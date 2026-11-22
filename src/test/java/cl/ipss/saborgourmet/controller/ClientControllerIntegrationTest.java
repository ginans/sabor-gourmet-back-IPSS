package cl.ipss.saborgourmet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.exception.ErrorCode;
import cl.ipss.saborgourmet.model.Client;
import cl.ipss.saborgourmet.model.ClientStatus;
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
class ClientControllerIntegrationTest {

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
    @DisplayName("SG-CLI-01 / SG-CLI-02 / SG-CLI-03: Create without PII echo, validation 400, and duplicate email 409")
    void createValidationAndDuplicateEmailFlow() throws Exception {
        mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "invalid-email",
                                  "phone": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " a ",
                                  "email": "valid@correo.cl",
                                  "phone": "-------"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()));

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").exists());

        mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ana Perez",
                                  "email": "ANA.PEREZ@correo.cl",
                                  "phone": "+56 9 1234-5678"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/clients/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.message").value(ApiConstants.MSG_CLIENT_CREATED))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist());

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.perez@correo.cl"))
                .andExpect(jsonPath("$.phone").value("+56912345678"))
                .andExpect(jsonPath("$.status").value(ClientStatus.ACTIVE.name()));

        mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ana P.",
                                  "email": "ana.perez@CORREO.CL",
                                  "phone": "+56 9 9999-8888"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("BUSINESS"))
                .andExpect(jsonPath("$.code").value(ErrorCode.CLIENT_EMAIL_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("SG-CLI-04..SG-CLI-12: CRUD, privacy-preserving update, ban, physical delete, and soft-delete anonymization")
    void fullClientLifecycleEndpoints() throws Exception {
        Long clientWithoutHistoryId = createClientAndReturnId("Luis Soto", "luis@correo.cl", "+56911112222");
        Long clientWithHistoryId = createClientAndReturnId("Maria Silva", "maria@correo.cl", "+56933334444");

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CLIENT_NOT_FOUND.getCode()));

        mockMvc.perform(put(ApiConstants.CLIENTS_PATH + "/" + clientWithoutHistoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Luis Alberto Soto",
                                  "email": "luis@correo.cl",
                                  "phone": "+56 9 5555-6666"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientWithoutHistoryId))
                .andExpect(jsonPath("$.message").value(ApiConstants.MSG_CLIENT_UPDATED))
                .andExpect(jsonPath("$.updatedFields[0]").value(Client.Fields.name))
                .andExpect(jsonPath("$.updatedFields[1]").value(Client.Fields.phone))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(patch(ApiConstants.CLIENTS_PATH + "/" + clientWithoutHistoryId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "BANNED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientWithoutHistoryId))
                .andExpect(jsonPath("$.message").value(ApiConstants.MSG_CLIENT_STATUS_UPDATED))
                .andExpect(jsonPath("$.updatedFields[0]").value(Client.Fields.status));

        mockMvc.perform(delete(ApiConstants.CLIENTS_PATH + "/" + clientWithoutHistoryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/" + clientWithoutHistoryId))
                .andExpect(status().isNotFound());

        Reservation activeReservation = reservationRepository.save(Reservation.builder()
                .clientId(clientWithHistoryId)
                .tableId(1L)
                .date(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(20, 0))
                .endTime(LocalTime.of(22, 0))
                .guestCount(4)
                .status(ReservationStatus.PENDING)
                .build());

        mockMvc.perform(delete(ApiConstants.CLIENTS_PATH + "/" + clientWithHistoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CLIENT_HAS_ACTIVE_RESERVATIONS.getCode()));

        activeReservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(activeReservation);

        mockMvc.perform(delete(ApiConstants.CLIENTS_PATH + "/" + clientWithHistoryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(ApiConstants.CLIENTS_PATH + "/" + clientWithHistoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ClientStatus.DELETED.name()))
                .andExpect(jsonPath("$.name").value(ApiConstants.ANONYMIZED_CLIENT_NAME))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(patch(ApiConstants.CLIENTS_PATH + "/" + clientWithHistoryId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACTIVE" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CLIENT_DELETED_IMMUTABLE.getCode()));

        assertThat(reservationRepository.findByClientId(clientWithHistoryId)).hasSize(1);
    }

    private Long createClientAndReturnId(String name, String email, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstants.CLIENTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "name": "%s",
                                  "email": "%s",
                                  "phone": "%s"
                                }
                                """, name, email, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
