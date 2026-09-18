package com.aeronex.aircraft;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class AircraftControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_AIRCRAFT_JSON = """
            {
              "registrationNumber": "n12345",
              "manufacturer": "Boeing",
              "model": "737-800",
              "seatCapacity": 189,
              "status": "ACTIVE"
            }
            """;

    private UUID createAircraft() throws Exception {
        String responseBody = mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());
    }

    @Test
    void createAndRetrieveAircraft() throws Exception {
        String responseBody = mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("N12345"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(responseBody);
        String id = node.get("id").asText();

        mockMvc.perform(get("/api/aircraft/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("N12345"));

        mockMvc.perform(get("/api/aircraft/registration/n12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/aircraft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createWithoutStatusDefaultsToActive() throws Exception {
        String jsonWithoutStatus = """
                {
                  "registrationNumber": "n54321",
                  "manufacturer": "Airbus",
                  "model": "A320",
                  "seatCapacity": 150
                }
                """;

        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutStatus))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createDuplicateRegistrationNumberReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createWithInvalidSeatCapacityReturnsBadRequestWithFieldErrors() throws Exception {
        String invalidJson = VALID_AIRCRAFT_JSON.replace("189", "0");

        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.seatCapacity").exists());
    }

    @Test
    void createWithBlankRegistrationNumberReturnsBadRequestWithFieldErrors() throws Exception {
        String invalidJson = VALID_AIRCRAFT_JSON.replace("\"n12345\"", "\"\"");

        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.registrationNumber").exists());
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/aircraft/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByRegistrationNumberReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/aircraft/registration/zzzzz"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void createWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void createWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPS")
    void createWithOpsRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/aircraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRCRAFT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusWithValidTransitionSucceeds() throws Exception {
        UUID id = createAircraft();

        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\",\"reason\":\"Scheduled check\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void updateStatusWithIllegalTransitionReturnsConflict() throws Exception {
        UUID id = createAircraft();

        // ACTIVE -> ACTIVE is not a legal transition (no self-transitions).
        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatusMaintenanceToActiveDirectlyReturnsConflict() throws Exception {
        UUID id = createAircraft();

        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk());

        // MAINTENANCE has no direct path back to ACTIVE, by explicit design.
        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatusWithMissingStatusReturnsBadRequest() throws Exception {
        UUID id = createAircraft();

        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatusForUnknownAircraftReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/aircraft/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void updateStatusWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/aircraft/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void updateStatusWithViewerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/aircraft/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPS")
    void updateStatusWithOpsRoleReturnsForbidden() throws Exception {
        // Unlike Flight/Disruption, Aircraft writes are ADMIN-only -- OPS must be
        // forbidden here even though it's allowed on the equivalent Flight endpoint.
        mockMvc.perform(patch("/api/aircraft/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusHistoryIncludesCreationSeedAndSubsequentTransitionInChronologicalOrder() throws Exception {
        UUID id = createAircraft();

        mockMvc.perform(patch("/api/aircraft/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\",\"reason\":\"Scheduled check\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/aircraft/" + id + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].previousStatus").value(nullValue()))
                .andExpect(jsonPath("$[0].newStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].source").value("CREATED"))
                .andExpect(jsonPath("$[1].previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].newStatus").value("MAINTENANCE"))
                .andExpect(jsonPath("$[1].source").value("OPERATOR"))
                .andExpect(jsonPath("$[1].reason").value("Scheduled check"));
    }

    @Test
    void statusHistoryForUnknownAircraftReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/aircraft/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void statusHistoryWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/aircraft/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void statusHistoryWithViewerRoleSucceeds() throws Exception {
        // Read access follows the same convention as the other Aircraft GET
        // endpoints: any authenticated role may read, only writes are ADMIN-gated.
        mockMvc.perform(get("/api/aircraft/" + UUID.randomUUID() + "/status-history"))
                .andExpect(status().isNotFound());
    }
}
