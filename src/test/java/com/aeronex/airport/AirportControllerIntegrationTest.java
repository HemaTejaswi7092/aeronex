package com.aeronex.airport;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AirportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_AIRPORT_JSON = """
            {
              "iataCode": "jfk",
              "icaoCode": "kjfk",
              "name": "John F. Kennedy International Airport",
              "city": "New York",
              "country": "United States",
              "timezone": "America/New_York",
              "latitude": 40.639751,
              "longitude": -73.778925
            }
            """;

    @Test
    void createAndRetrieveAirport() throws Exception {
        String responseBody = mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRPORT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iataCode").value("JFK"))
                .andExpect(jsonPath("$.icaoCode").value("KJFK"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(responseBody);
        String id = node.get("id").asText();

        mockMvc.perform(get("/api/airports/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iataCode").value("JFK"));

        mockMvc.perform(get("/api/airports/iata/jfk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createDuplicateIataCodeReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AIRPORT_JSON))
                .andExpect(status().isCreated());

        String duplicateJson = VALID_AIRPORT_JSON.replace("kjfk", "klax");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createWithInvalidIataCodeReturnsBadRequestWithFieldErrors() throws Exception {
        String invalidJson = VALID_AIRPORT_JSON.replace("\"jfk\"", "\"toolong\"");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.iataCode").exists());
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/airports/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIataCodeReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/airports/iata/zzz"))
                .andExpect(status().isNotFound());
    }
}
