package cl.ipss.saborgourmet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.model.ClientStatus;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.repository.json.JsonFileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonFileStorage storage;

    private LocalDate futureDate;

    @BeforeEach
    void resetStorage() throws Exception {
        Files.deleteIfExists(storage.getStoragePath());
        storage.initialize();
        futureDate = LocalDate.now().plusDays(10);
    }

    @Test
    @DisplayName("SG-RES-01..SG-RES-19: Full reservation lifecycle, multi-table booking, availability release, and filtering")
    void fullReservationLifecycleAndAvailabilityRelease() throws Exception {
        Long clientId = createClient("Ana Perez", "ana@correo.cl", "+56912345678");
        Long table1Id = createTable(1, 4, "Main Hall");
        Long table2Id = createTable(2, 4, "Terrace");
        createTable(3, 6, "VIP");

        mockMvc.perform(get(ApiConstants.TABLES_PATH + "/availability")
                        .param("date", futureDate.toString())
                        .param("time", "20:00")
                        .param("guests", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(2))
                .andExpect(jsonPath("$.endTime").value("22:00:00"));

        Long reservation1Id = createReservation(clientId, table1Id, futureDate, "20:00", 3);

        mockMvc.perform(get(ApiConstants.TABLES_PATH + "/availability")
                        .param("date", futureDate.toString())
                        .param("time", "20:00")
                        .param("guests", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(1))
                .andExpect(jsonPath("$.availableTables[0].id").value(table2Id));

        Long reservation2Id = createReservation(clientId, table2Id, futureDate, "20:00", 4);
        assertThat(reservation2Id).isNotEqualTo(reservation1Id);

        mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(clientId, table1Id, futureDate, "20:30", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESERVATION_TIME_OVERLAP.getCode()));

        mockMvc.perform(put(ApiConstants.RESERVATIONS_PATH + "/" + reservation1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(clientId, table1Id, futureDate, "19:30", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("19:30:00"))
                .andExpect(jsonPath("$.endTime").value("21:30:00"));

        mockMvc.perform(patch(ApiConstants.RESERVATIONS_PATH + "/" + reservation1Id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReservationStatus.CONFIRMED.name()));

        mockMvc.perform(get(ApiConstants.RESERVATIONS_PATH)
                        .param("date", futureDate.toString())
                        .param("status", ReservationStatus.CONFIRMED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reservation1Id));

        mockMvc.perform(patch(ApiConstants.RESERVATIONS_PATH + "/" + reservation1Id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReservationStatus.CANCELLED.name()));

        mockMvc.perform(get(ApiConstants.TABLES_PATH + "/availability")
                        .param("date", futureDate.toString())
                        .param("time", "20:00")
                        .param("guests", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(1))
                .andExpect(jsonPath("$.availableTables[0].id").value(table1Id));

        mockMvc.perform(get(ApiConstants.RESERVATIONS_PATH + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESERVATION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("SG-PER-04: Two concurrent booking requests for the same table and slot yield one 201 and one 409")
    void concurrentBookingsProduceExactlyOneCreatedAndOneConflict() throws Exception {
        Long client1Id = createClient("Ana Perez", "ana@correo.cl", "+56911111111");
        Long client2Id = createClient("Luis Soto", "luis@correo.cl", "+56922222222");
        Long tableId = createTable(1, 4, "Main Hall");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);

        try {
            Callable<Integer> request1 = () -> {
                ready.countDown();
                fire.await();
                return mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bookingPayload(client1Id, tableId, futureDate, "20:00", 4)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Callable<Integer> request2 = () -> {
                ready.countDown();
                fire.await();
                return mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bookingPayload(client2Id, tableId, futureDate, "20:00", 3)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };

            Future<Integer> future1 = executor.submit(request1);
            Future<Integer> future2 = executor.submit(request2);
            ready.await();
            fire.countDown();

            List<Integer> statuses = List.of(future1.get(), future2.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get(ApiConstants.RESERVATIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("SG-RES-20: Third late cancellation (<24h) bans client and blocks subsequent reservations")
    void threeLateCancellationsBanClientEndToEnd() throws Exception {
        Long clientId = createClient("Pedro Late", "pedro@correo.cl", "+56977777777");
        Long tableId = createTable(1, 4, "Main Hall");

        LocalDateTime snapshotNow = LocalDateTime.now();
        boolean useTodayEvening = snapshotNow.toLocalTime().isBefore(LocalTime.of(20, 0));
        LocalDate within24hDate = useTodayEvening ? snapshotNow.toLocalDate() : snapshotNow.toLocalDate().plusDays(1);
        String within24hTime = useTodayEvening ? "21:00" : "12:00";

        for (int i = 0; i < 3; i++) {
            Long resId = createReservation(clientId, tableId, within24hDate, within24hTime, 3);
            mockMvc.perform(patch(ApiConstants.RESERVATIONS_PATH + "/" + resId + "/cancel"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/" + clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ClientStatus.BANNED.name()))
                .andExpect(jsonPath("$.lateCancellations").value(3));

        mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(clientId, tableId, futureDate, "20:00", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CLIENT_NOT_ACTIVE.getCode()));
    }

    @Test
    @DisplayName("Regression: Availability query error strips method name prefix and negative IDs return 400")
    void constraintViolationsStripInternalMethodNameAndRejectNegativeIds() throws Exception {
        mockMvc.perform(get(ApiConstants.TABLES_PATH + "/availability")
                        .param("date", futureDate.toString())
                        .param("time", "20:00")
                        .param("guests", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.guests").exists())
                .andExpect(jsonPath("$.fieldErrors['checkAvailability.guests']").doesNotExist());

        mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(-1L, -2L, futureDate, "20:00", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.clientId").exists())
                .andExpect(jsonPath("$.fieldErrors.tableId").exists());
    }

    private Long createClient(String name, String email, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                { "name": "%s", "email": "%s", "phone": "%s" }
                                """, name, email, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private Long createTable(int number, int capacity, String location) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstants.TABLES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                { "number": %d, "capacity": %d, "location": "%s" }
                                """, number, capacity, location)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private Long createReservation(Long clientId, Long tableId, LocalDate date, String startTime, int guests)
            throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstants.RESERVATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(clientId, tableId, date, startTime, guests)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private String bookingPayload(Long clientId, Long tableId, LocalDate date, String startTime, int guests) {
        return String.format("""
                {
                  "clientId": %d,
                  "tableId": %d,
                  "date": "%s",
                  "startTime": "%s",
                  "guestCount": %d
                }
                """, clientId, tableId, date, startTime, guests);
    }
}
