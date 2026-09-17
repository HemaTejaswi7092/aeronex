package com.aeronex.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void livenessProbeReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void prometheusEndpointExposesMetricsInExpositionFormat() throws Exception {
        // Ensure at least one HTTP request has been recorded before asserting on http.server.requests.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("http_server_requests_seconds")))
                .andExpect(content().string(containsString("aeronex_outbox_backlog_size")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void metricsEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    void prometheusEndpointWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void prometheusEndpointWithNonAdminRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    private static final String[] SENSITIVE_ENDPOINTS = {
            "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/mappings",
            "/actuator/heapdump", "/actuator/threaddump", "/actuator/shutdown"
    };

    @Test
    void sensitiveEndpointsRejectUnauthenticatedRequests() throws Exception {
        // The security filter chain intercepts these paths before Spring MVC would
        // otherwise report "no handler" — anonymous callers see 401, not 404. Still
        // provably inaccessible, just denied one layer earlier than before.
        for (String endpoint : SENSITIVE_ENDPOINTS) {
            mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sensitiveEndpointsAreDeniedEvenToAdmin() throws Exception {
        // The catch-all authorizeHttpRequests rule is an unconditional denyAll, not a
        // role check, so even the most privileged role never reaches Spring MVC to
        // discover these endpoints aren't registered — every authenticated caller,
        // regardless of role, is denied at the security layer with 403.
        for (String endpoint : SENSITIVE_ENDPOINTS) {
            mockMvc.perform(get(endpoint)).andExpect(status().isForbidden());
        }
    }
}
