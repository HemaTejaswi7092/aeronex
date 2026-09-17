package com.aeronex.disruption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "OPS")
class DisruptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRepository flightRepository;

    private Flight scheduledFlight;
    private Flight arrivedFlight;
    private Flight cancelledFlight;

    @BeforeEach
    void setUp() {
        Airport jfk = airportRepository.saveAndFlush(new Airport("JFK", null, "John F. Kennedy International Airport",
                "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925")));
        Airport lax = airportRepository.saveAndFlush(new Airport("LAX", null, "Los Angeles International Airport",
                "Los Angeles", "United States", "America/Los_Angeles",
                new BigDecimal("33.941589"), new BigDecimal("-118.408530")));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");

        scheduledFlight = flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                departure, arrival, null, null, FlightStatus.SCHEDULED));
        arrivedFlight = flightRepository.saveAndFlush(new Flight("AA200", jfk, lax, null,
                departure, arrival, departure, arrival, FlightStatus.ARRIVED));
        cancelledFlight = flightRepository.saveAndFlush(new Flight("AA300", jfk, lax, null,
                departure, arrival, null, null, FlightStatus.CANCELLED));
    }

    private String validDisruptionJson(UUID flightId) {
        return """
                {
                  "flightId": "%s",
                  "type": "WEATHER",
                  "severity": "MEDIUM",
                  "description": "Heavy snow at origin airport",
                  "estimatedDelayMinutes": 45
                }
                """.formatted(flightId);
    }

    @Test
    void createAndRetrieveDisruption() throws Exception {
        String responseBody = mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(scheduledFlight.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.type").value("WEATHER"))
                .andExpect(jsonPath("$.flight.flightNumber").value("AA100"))
                .andExpect(jsonPath("$.flight.originIataCode").value("JFK"))
                .andExpect(jsonPath("$.flight.destinationIataCode").value("LAX"))
                .andExpect(jsonPath("$.flight.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.reportedAt").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(responseBody);
        String id = node.get("id").asText();

        mockMvc.perform(get("/api/disruptions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/disruptions/flight/" + scheduledFlight.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/disruptions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/disruptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void resolveDisruptionSetsStatusAndRemovesFromActive() throws Exception {
        String responseBody = mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(scheduledFlight.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createdNode = objectMapper.readTree(responseBody);
        String id = createdNode.get("id").asText();
        String createdUpdatedAt = createdNode.get("updatedAt").asText();

        String resolvedBody = mockMvc.perform(patch("/api/disruptions/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andReturn().getResponse().getContentAsString();

        // updatedAt must reflect the resolve itself, not a stale pre-flush snapshot
        // from before the UPDATE statement actually ran.
        String resolvedUpdatedAt = objectMapper.readTree(resolvedBody).get("updatedAt").asText();
        assertThat(resolvedUpdatedAt).isNotEqualTo(createdUpdatedAt);

        mockMvc.perform(get("/api/disruptions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createForArrivedFlightReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(arrivedFlight.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void createForCancelledFlightReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(cancelledFlight.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithNonexistentFlightReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithBlankDescriptionReturnsBadRequestWithFieldErrors() throws Exception {
        String json = validDisruptionJson(scheduledFlight.getId()).replace("Heavy snow at origin airport", "");

        mockMvc.perform(post("/api/disruptions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void createWithNegativeEstimatedDelayReturnsBadRequestWithFieldErrors() throws Exception {
        String json = validDisruptionJson(scheduledFlight.getId())
                .replace("\"estimatedDelayMinutes\": 45", "\"estimatedDelayMinutes\": -10");

        mockMvc.perform(post("/api/disruptions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.estimatedDelayMinutes").exists());
    }

    @Test
    void createWithResolvedAtButStatusNotResolvedReturnsBadRequest() throws Exception {
        String json = """
                {
                  "flightId": "%s",
                  "type": "WEATHER",
                  "severity": "MEDIUM",
                  "description": "Heavy snow at origin airport",
                  "status": "OPEN",
                  "resolvedAt": "2026-06-01T09:00:00Z"
                }
                """.formatted(scheduledFlight.getId());

        mockMvc.perform(post("/api/disruptions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveNonexistentDisruptionReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/disruptions/" + UUID.randomUUID() + "/resolve"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void createWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(scheduledFlight.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void createWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(scheduledFlight.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void resolveWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/disruptions/" + UUID.randomUUID() + "/resolve"))
                .andExpect(status().isForbidden());
    }
}
