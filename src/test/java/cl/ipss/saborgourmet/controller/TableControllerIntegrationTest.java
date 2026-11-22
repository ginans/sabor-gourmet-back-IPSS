package cl.ipss.saborgourmet.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.model.Reservation;
import cl.ipss.saborgourmet.model.ReservationStatus;
import cl.ipss.saborgourmet.repository.ReservationRepository;
import cl.ipss.saborgourmet.repository.json.JsonFileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
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
class TableControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonFileStorage storage;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void resetStorage() throws Exception {
        Files.deleteIfExists(storage.getStoragePath());
        storage.initialize();
    }

    @Test
    @DisplayName("SG-MES-01..SG-MES-05: Validation 400, create 201, duplicate number 409, list 200, and 404")
    void createListAndValidationFlow() throws Exception {
        mockMvc.perform(post(ApiConstants.TABLES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 0,
                                  "capacity": -1,
                                  "location": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.number").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists())
                .andExpect(jsonPath("$.fieldErrors.location").exists());

        mockMvc.perform(post(ApiConstants.TABLES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 5,
                                  "capacity": 4,
                                  "location": "Main Hall"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/tables/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.number").value(5))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post(ApiConstants.TABLES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 5,
                                  "capacity": 6,
                                  "location": "Terrace"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TABLE_NUMBER_ALREADY_EXISTS.getCode()));

        mockMvc.perform(get(ApiConstants.TABLES_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(ApiConstants.TABLES_PATH + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TABLE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("SG-MES-06..SG-MES-10: Update, capacity/deactivation guards, and delete referential integrity")
    void updateAndDeleteLifecycle() throws Exception {
        Long tableWithReservationId = createTableAndReturnId(1, 6, "Main Hall");
        Long freeTableId = createTableAndReturnId(2, 2, "Patio");

        mockMvc.perform(delete(ApiConstants.TABLES_PATH + "/" + freeTableId))
                .andExpect(status().isNoContent());

        Reservation activeReservation = reservationRepository.save(Reservation.builder()
                .clientId(1L)
                .tableId(tableWithReservationId)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(20, 0))
                .endTime(LocalTime.of(22, 0))
                .guestCount(5)
                .status(ReservationStatus.CONFIRMED)
                .build());

        mockMvc.perform(put(ApiConstants.TABLES_PATH + "/" + tableWithReservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 1,
                                  "capacity": 4,
                                  "location": "Main Hall",
                                  "active": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TABLE_CAPACITY_REDUCTION_CONFLICT.getCode()));

        mockMvc.perform(put(ApiConstants.TABLES_PATH + "/" + tableWithReservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 1,
                                  "capacity": 6,
                                  "location": "Main Hall",
                                  "active": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TABLE_DEACTIVATION_CONFLICT.getCode()));

        activeReservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(activeReservation);

        mockMvc.perform(put(ApiConstants.TABLES_PATH + "/" + tableWithReservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": 1,
                                  "capacity": 4,
                                  "location": "Terrace",
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(4))
                .andExpect(jsonPath("$.location").value("Terrace"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete(ApiConstants.TABLES_PATH + "/" + tableWithReservationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TABLE_HAS_RESERVATIONS.getCode()));
    }

    private Long createTableAndReturnId(int number, int capacity, String location) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstants.TABLES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "number": %d,
                                  "capacity": %d,
                                  "location": "%s"
                                }
                                """, number, capacity, location)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
