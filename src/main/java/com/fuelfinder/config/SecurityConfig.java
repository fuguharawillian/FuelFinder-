package com.fuelfinder.config;

import com.fuelfinder.modules.auth.service.JwtAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@EnableMethodSecurity

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OriginValidationFilter originValidationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OriginValidationFilter originValidationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.originValidationFilter = originValidationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/admin/*.html",
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs",
                                "/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/stations/**", "/fuel-prices/compare")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(originValidationFilter, JwtAuthenticationFilter.class)
                .build();
    }
}
