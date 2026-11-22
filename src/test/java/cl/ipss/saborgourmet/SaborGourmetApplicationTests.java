package cl.ipss.saborgourmet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ipss.saborgourmet.config.ApiConstants;
import cl.ipss.saborgourmet.config.ReservationProperties;
import cl.ipss.saborgourmet.config.StorageProperties;
import cl.ipss.saborgourmet.exception.ErrorCode;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaborGourmetApplicationTests {

    @Autowired
    private ReservationProperties reservationProperties;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Application context loads externalized storage and reservation properties")
    void contextLoadsConfigurationProperties() {
        assertThat(storageProperties.filePath()).isEqualTo("target/test-data/data.json");
        assertThat(reservationProperties.turnDurationMinutes()).isEqualTo(120);
        assertThat(reservationProperties.openingTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(reservationProperties.closingTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(reservationProperties.minOccupancyPercentage()).isEqualTo(75);
        assertThat(reservationProperties.cancellationNoticeHours()).isEqualTo(24);
        assertThat(reservationProperties.maxLateCancellations()).isEqualTo(3);
    }

    @Test
    @DisplayName("Minimum occupancy calculation applies 75% rounded down with lower bound of 1")
    void calculateMinimumGuestsFollowsBusinessTable() {
        assertThat(reservationProperties.calculateMinimumGuests(2)).isEqualTo(1);
        assertThat(reservationProperties.calculateMinimumGuests(4)).isEqualTo(3);
        assertThat(reservationProperties.calculateMinimumGuests(6)).isEqualTo(4);
        assertThat(reservationProperties.calculateMinimumGuests(7)).isEqualTo(5);
        assertThat(reservationProperties.calculateMinimumGuests(8)).isEqualTo(6);
    }

    @Test
    @DisplayName("OpenAPI specification endpoint is available on startup")
    void openApiEndpointRespondsSuccessfully() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(ApiConstants.OPENAPI_TITLE));
    }

    @Test
    @DisplayName("Unknown endpoint returns standardized JSON 404 with cataloged ErrorCode")
    void unknownEndpointReturnsStandardizedError() throws Exception {
        mockMvc.perform(get("/api/v1/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.category").value(ErrorCode.ENDPOINT_NOT_FOUND.getCategory().name()))
                .andExpect(jsonPath("$.code").value(ErrorCode.ENDPOINT_NOT_FOUND.getCode()));
    }
}
