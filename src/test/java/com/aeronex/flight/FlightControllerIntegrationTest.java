package com.aeronex.flight;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "OPS")
class FlightControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    private Airport jfk;
    private Airport lax;
    private Aircraft activeAircraft;
    private Aircraft outOfServiceAircraft;
    private Aircraft maintenanceAircraft;

    @BeforeEach
    void setUp() {
        jfk = airportRepository.saveAndFlush(new Airport("JFK", null, "John F. Kennedy International Airport",
                "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925")));
        lax = airportRepository.saveAndFlush(new Airport("LAX", null, "Los Angeles International Airport",
                "Los Angeles", "United States", "America/Los_Angeles",
                new BigDecimal("33.941589"), new BigDecimal("-118.408530")));
        activeAircraft = aircraftRepository.saveAndFlush(
                new Aircraft("N12345", "Boeing", "737-800", 189, AircraftStatus.ACTIVE));
        outOfServiceAircraft = aircraftRepository.saveAndFlush(
                new Aircraft("N99999", "Boeing", "747-400", 400, AircraftStatus.OUT_OF_SERVICE));
        maintenanceAircraft = aircraftRepository.saveAndFlush(
                new Aircraft("N88888", "Airbus", "A320", 150, AircraftStatus.MAINTENANCE));
    }

    @Test
    void createAndRetrieveFlight() throws Exception {
        String json = """
                {
                  "flightNumber": "aa100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "aircraftId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-07:00"
                }
                """.formatted(jfk.getId(), lax.getId(), activeAircraft.getId());

        String responseBody = mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flightNumber").value("AA100"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.originAirport.iataCode").value("JFK"))
                .andExpect(jsonPath("$.destinationAirport.iataCode").value("LAX"))
                .andExpect(jsonPath("$.aircraft.registrationNumber").value("N12345"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(responseBody);
        String id = node.get("id").asText();

        mockMvc.perform(get("/api/flights/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("AA100"));

        mockMvc.perform(get("/api/flights/number/aa100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/flights/airport/jfk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createWithSameOriginAndDestinationReturnsBadRequest() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(jfk.getId(), jfk.getId());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithArrivalBeforeDepartureReturnsBadRequest() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "scheduledDepartureTime": "2026-06-01T13:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T10:00:00-04:00"
                }
                """.formatted(jfk.getId(), lax.getId());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithOutOfServiceAircraftReturnsConflict() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "aircraftId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(jfk.getId(), lax.getId(), outOfServiceAircraft.getId());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithAircraftInMaintenanceReturnsConflict() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "aircraftId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(jfk.getId(), lax.getId(), maintenanceAircraft.getId());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithNonexistentOriginAirportReturnsNotFound() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(UUID.randomUUID(), lax.getId());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithNonexistentAircraftReturnsNotFound() throws Exception {
        String json = """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "aircraftId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(jfk.getId(), lax.getId(), UUID.randomUUID());

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/flights/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String validFlightJson() {
        return """
                {
                  "flightNumber": "AA100",
                  "originAirportId": "%s",
                  "destinationAirportId": "%s",
                  "scheduledDepartureTime": "2026-06-01T10:00:00-04:00",
                  "scheduledArrivalTime": "2026-06-01T13:00:00-04:00"
                }
                """.formatted(jfk.getId(), lax.getId());
    }

    private UUID createFlight() throws Exception {
        String responseBody = mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFlightJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());
    }

    @Test
    @WithAnonymousUser
    void createWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(validFlightJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void createWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(validFlightJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWithAdminRoleSucceeds() throws Exception {
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(validFlightJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void updateStatusWithValidTransitionSucceeds() throws Exception {
        UUID id = createFlight();

        mockMvc.perform(patch("/api/flights/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\",\"reason\":\"Gate ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOARDING"));
    }

    @Test
    void updateStatusWithIllegalTransitionReturnsConflict() throws Exception {
        UUID id = createFlight();

        mockMvc.perform(patch("/api/flights/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARRIVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatusWithMissingStatusReturnsBadRequest() throws Exception {
        UUID id = createFlight();

        mockMvc.perform(patch("/api/flights/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatusForUnknownFlightReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/flights/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void updateStatusWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/flights/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void updateStatusWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/flights/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatusWithAdminRoleSucceeds() throws Exception {
        UUID id = createFlight();

        mockMvc.perform(patch("/api/flights/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void statusHistoryIncludesCreationSeedAndSubsequentTransitionInChronologicalOrder() throws Exception {
        UUID id = createFlight();

        mockMvc.perform(patch("/api/flights/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOARDING\",\"reason\":\"Gate ready\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/flights/" + id + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].previousStatus").value(nullValue()))
                .andExpect(jsonPath("$[0].newStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].source").value("CREATED"))
                .andExpect(jsonPath("$[1].previousStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$[1].newStatus").value("BOARDING"))
                .andExpect(jsonPath("$[1].source").value("OPERATOR"))
                .andExpect(jsonPath("$[1].reason").value("Gate ready"));
    }

    @Test
    void statusHistoryForUnknownFlightReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/flights/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void statusHistoryWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/flights/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void statusHistoryWithViewerRoleSucceeds() throws Exception {
        // Read access follows the same convention as the other flight GET endpoints:
        // any authenticated role may read, only writes are ADMIN/OPS-gated.
        mockMvc.perform(get("/api/flights/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isNotFound());
    }
}
