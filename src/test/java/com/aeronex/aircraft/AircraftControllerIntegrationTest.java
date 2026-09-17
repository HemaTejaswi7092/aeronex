package com.aeronex.aircraft;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
