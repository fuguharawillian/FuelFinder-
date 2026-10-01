package com.fuelfinder.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fuelfinder.modules.auth.service.JwtAuthenticationFilter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

@WebMvcTest(controllers = SecurityTestController.class)
@Import({SecurityConfig.class, WebConfig.class})
class SecurityConfigTest {

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void allowTestRequestToContinueThroughMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(
                    invocation.getArgument(0),
                    invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(ServletRequest.class),
                any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void requiresAuthenticationForNonDocumentationEndpoints() throws Exception {
        mockMvc.perform(get("/test-protected").accept("application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsOpenApiDocumentationEndpoints() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsAuthenticatedRequestsToProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/test-protected").with(user("driver")))
                .andExpect(status().isOk());
    }

    @Test
    void addsCorsHeadersForAllowedLocalFrontendOrigins() throws Exception {
        mockMvc.perform(options("/test-protected")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void doesNotAllowUnlistedCorsOrigins() throws Exception {
        mockMvc.perform(options("/test-protected")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
