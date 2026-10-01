package com.fuelfinder.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityTestController {

    @GetMapping("/test-protected")
    public String protectedEndpoint() {
        return "authenticated";
    }

    @GetMapping("/api-docs")
    public String apiDocumentation() {
        return "openapi";
    }
}
