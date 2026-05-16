package com.abc.claims.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = "claims.data.xlsx-path=./SamplePlanAndTransactionData_1.xlsx")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("anonymous → /api/v1/claims is rejected with 401")
    @WithAnonymousUser
    void anonymous_api_call_is_401() throws Exception {
        mockMvc().perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous → / is rejected with 401")
    @WithAnonymousUser
    void anonymous_gui_is_401() throws Exception {
        mockMvc().perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticated PROCESSOR can reach the GUI index page")
    @WithMockUser(username = "processor", roles = {"PROCESSOR"})
    void processor_can_reach_gui() throws Exception {
        mockMvc().perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("authenticated user without PROCESSOR role is forbidden")
    @WithMockUser(username = "guest", roles = {"GUEST"})
    void wrong_role_is_forbidden() throws Exception {
        mockMvc().perform(get("/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/actuator/health is public")
    @WithAnonymousUser
    void actuator_health_is_public() throws Exception {
        mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/prometheus is public (Prometheus scraper has no creds)")
    @WithAnonymousUser
    void actuator_prometheus_is_public() throws Exception {
        mockMvc().perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/v3/api-docs is public so Swagger UI works without auth")
    @WithAnonymousUser
    void openapi_docs_are_public() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/env requires ADMIN")
    @WithMockUser(username = "processor", roles = {"PROCESSOR"})
    void actuator_env_requires_admin() throws Exception {
        mockMvc().perform(get("/actuator/env"))
                .andExpect(status().isForbidden());
    }
}
