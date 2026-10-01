package com.fuelfinder.modules.review.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ReviewControllerTest {

    @Test
    void rejectsDeletionWhenAuthenticatedPrincipalHasNoRecognizedRole() {
        ReviewController controller = new ReviewController(mock(ReviewService.class));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OTHER")));

        assertThrows(IllegalStateException.class, () ->
                controller.delete(UUID.randomUUID(),
                        (AuthPrincipal) authentication.getPrincipal(),
                        authentication));
    }
}
