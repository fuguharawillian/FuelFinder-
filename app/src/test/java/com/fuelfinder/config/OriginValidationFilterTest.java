package com.fuelfinder.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginValidationFilterTest {

    private final TestableOriginValidationFilter filter =
            new TestableOriginValidationFilter(
                    "http://localhost:8080,http://127.0.0.1:8080,"
                            + "http://localhost:3000,http://127.0.0.1:3000,"
                            + "http://localhost:5500,http://127.0.0.1:5500,"
                            + "http://localhost:8081,http://127.0.0.1:8081");

    @Test
    void filtersOnlyPostRefreshAndLogoutRequests() {
        assertTrue(filter.shouldSkip(request("GET", "/auth/refresh")));
        assertTrue(filter.shouldSkip(request("POST", "/auth/login")));
        assertFalse(filter.shouldSkip(request("POST", "/auth/refresh")));
        assertFalse(filter.shouldSkip(request("POST", "/auth/logout")));
    }

    @Test
    void rejectsMissingOrUnlistedOriginsAndContinuesForAllowedOrigins() throws Exception {
        MockHttpServletRequest missingOrigin = request("POST", "/auth/refresh");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilterInternal(missingOrigin, missingResponse, new MockFilterChain());
        assertEquals(403, missingResponse.getStatus());

        MockHttpServletRequest forbiddenOrigin = request("POST", "/auth/logout");
        forbiddenOrigin.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse forbiddenResponse = new MockHttpServletResponse();
        filter.doFilterInternal(forbiddenOrigin, forbiddenResponse, new MockFilterChain());
        assertEquals(403, forbiddenResponse.getStatus());

        MockHttpServletRequest allowedOrigin = request("POST", "/auth/refresh");
        allowedOrigin.addHeader("Origin", "http://localhost:5500");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(allowedOrigin, allowedResponse, chain);
        assertNotNull(chain.getRequest());
        assertEquals(200, allowedResponse.getStatus());

        MockHttpServletRequest sameOrigin = request("POST", "/auth/refresh");
        sameOrigin.addHeader("Origin", "http://localhost:8080");
        MockHttpServletResponse sameOriginResponse = new MockHttpServletResponse();
        filter.doFilterInternal(sameOrigin, sameOriginResponse, new MockFilterChain());
        assertEquals(200, sameOriginResponse.getStatus());

        MockHttpServletRequest loopbackOrigin = request("POST", "/auth/refresh");
        loopbackOrigin.addHeader("Origin", "http://127.0.0.1:8081");
        MockHttpServletResponse loopbackResponse = new MockHttpServletResponse();
        MockFilterChain loopbackChain = new MockFilterChain();
        filter.doFilterInternal(loopbackOrigin, loopbackResponse, loopbackChain);
        assertNotNull(loopbackChain.getRequest());
        assertEquals(200, loopbackResponse.getStatus());
    }

    @Test
    void rejectsRequestsWhenOriginAllowlistContainsOnlyBlankEntries() throws Exception {
        TestableOriginValidationFilter emptyAllowlist = new TestableOriginValidationFilter(" , ");
        MockHttpServletRequest request = request("POST", "/auth/refresh");
        request.addHeader("Origin", "http://localhost:3000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        emptyAllowlist.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setServletPath(path);
        return request;
    }

    private static class TestableOriginValidationFilter extends OriginValidationFilter {

        TestableOriginValidationFilter(String allowedOrigins) {
            super(allowedOrigins);
        }

        boolean shouldSkip(MockHttpServletRequest request) {
            return shouldNotFilter(request);
        }

        void doFilterInternal(
                MockHttpServletRequest request,
                MockHttpServletResponse response,
                FilterChain filterChain) throws Exception {
            super.doFilterInternal(request, response, filterChain);
        }
    }
}
