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

import com.aeronex.aircraft.Aircraft;
import com.aeronex.aircraft.AircraftRepository;
import com.aeronex.aircraft.AircraftStatus;
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

    @Autowired
    private AircraftRepository aircraftRepository;

    private Flight scheduledFlight;
    private Flight arrivedFlight;
    private Flight cancelledFlight;
    private Flight flightWithAircraft;
    private Aircraft assignedAircraft;

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

        assignedAircraft = aircraftRepository.saveAndFlush(
                new Aircraft("N77777", "Boeing", "737-800", 189, AircraftStatus.ACTIVE));
        flightWithAircraft = flightRepository.saveAndFlush(new Flight("AA400", jfk, lax, assignedAircraft,
                departure, arrival, null, null, FlightStatus.SCHEDULED));
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

    private String disruptionJsonWithSeverity(UUID flightId, String severity) {
        return """
                {
                  "flightId": "%s",
                  "type": "MECHANICAL",
                  "severity": "%s",
                  "description": "Engine inspection required"
                }
                """.formatted(flightId, severity);
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

    @Test
    void createWithHighSeverityAutoDelaysFlight() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(scheduledFlight.getId(), "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flight.status").value("DELAYED"));

        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"));

        // scheduledFlight is inserted directly via the repository in setUp(), bypassing
        // FlightService.create(), so it has no CREATED seed row — this HIGH-severity
        // disruption's automatic transition is the only history entry.
        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].source").value("DISRUPTION_AUTO"))
                .andExpect(jsonPath("$[0].previousStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].newStatus").value("DELAYED"));
    }

    @Test
    void createWithMediumSeverityDoesNotChangeFlightStatus() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(scheduledFlight.getId(), "MEDIUM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flight.status").value("SCHEDULED"));

        // No transition at all for MEDIUM severity, so no history row is created.
        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void resolvingAutoDelayedDisruptionDoesNotRestoreFlightStatus() throws Exception {
        String responseBody = mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(scheduledFlight.getId(), "CRITICAL")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(patch("/api/disruptions/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"));

        // Resolution must not add a history entry — only the one automatic
        // disruption-driven transition from before, unchanged by resolve().
        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void secondHighSeverityDisruptionOnAlreadyDelayedFlightAddsNoExtraHistory() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(scheduledFlight.getId(), "HIGH")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(scheduledFlight.getId(), "CRITICAL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flight.status").value("DELAYED"));

        // Two disruptions were recorded, but the flight only ever transitioned once.
        mockMvc.perform(get("/api/disruptions/flight/" + scheduledFlight.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/flights/" + scheduledFlight.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createWithMechanicalDisruptionTransitionsAircraftToMaintenanceRegardlessOfSeverity() throws Exception {
        // LOW severity: proves the aircraft rule is severity-independent, unlike the
        // flight-delay rule (which requires HIGH/CRITICAL).
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(flightWithAircraft.getId(), "LOW")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        // assignedAircraft is inserted directly via the repository in setUp(),
        // bypassing AircraftService.create(), so it has no CREATED seed row.
        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].source").value("DISRUPTION_AUTO"))
                .andExpect(jsonPath("$[0].previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].newStatus").value("MAINTENANCE"));

        // LOW severity must still not delay the flight -- the two rules are independent.
        mockMvc.perform(get("/api/flights/" + flightWithAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void createWithNonMechanicalDisruptionDoesNotChangeAircraftStatus() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDisruptionJson(flightWithAircraft.getId()))) // WEATHER type
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void repeatedMechanicalDisruptionsOnSameAircraftAddNoExtraHistoryAfterMaintenance() throws Exception {
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(flightWithAircraft.getId(), "HIGH")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(flightWithAircraft.getId(), "CRITICAL")))
                .andExpect(status().isCreated());

        // Two MECHANICAL disruptions were recorded, but the aircraft only ever
        // transitioned once -- idempotent, not a duplicate history row or error.
        mockMvc.perform(get("/api/disruptions/flight/" + flightWithAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId() + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void mechanicalDisruptionCanDelayFlightAndTransitionAircraftFromTheSameDisruption() throws Exception {
        // MECHANICAL + HIGH: eligible for both the flight-delay rule and the
        // aircraft-maintenance rule at once -- they are independent, not coupled,
        // and both fire from the same disruption.
        mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(flightWithAircraft.getId(), "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flight.status").value("DELAYED"));

        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void resolvingMechanicalDisruptionDoesNotRestoreAircraftStatus() throws Exception {
        String responseBody = mockMvc.perform(post("/api/disruptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disruptionJsonWithSeverity(flightWithAircraft.getId(), "HIGH")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(patch("/api/disruptions/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/aircraft/" + assignedAircraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }
}
