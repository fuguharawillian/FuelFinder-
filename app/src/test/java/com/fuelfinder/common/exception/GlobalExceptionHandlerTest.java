package com.fuelfinder.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void mapsMissingResourcesToNotFound() {
        ProblemDetail problem = exceptionHandler.handleNotFound(
                new ResourceNotFoundException("Posto não encontrado"));

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Posto não encontrado", problem.getDetail());
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    void mapsDuplicatesToConflict() {
        ProblemDetail problem = exceptionHandler.handleDuplicate(
                new DuplicateResourceException("E-mail já cadastrado"));

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Recurso duplicado", problem.getTitle());
    }

    @Test
    void mapsBusinessRulesToUnprocessableEntity() {
        ProblemDetail problem = exceptionHandler.handleBusiness(
                new BusinessException("Regra de negócio inválida"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
        assertEquals("Regra de negócio inválida", problem.getDetail());
    }

    @Test
    void mapsExternalServiceFailuresToBadGateway() {
        ProblemDetail problem = exceptionHandler.handleExternalService(
                new ExternalServiceException("Serviço externo indisponível", new IllegalStateException()));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
        assertEquals("Serviço externo indisponível", problem.getDetail());
    }

    @Test
    void mapsValidationErrorsToBadRequestGroupedByField() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "E-mail obrigatório"));
        bindingResult.addError(new FieldError("request", "email", "E-mail inválido"));
        bindingResult.addError(new FieldError("request", "name", "Nome obrigatório"));
        Method validationMethod = ValidationTarget.class.getDeclaredMethod("validate");

        ProblemDetail problem = exceptionHandler.handleValidationErrors(
                new MethodArgumentNotValidException(
                        new org.springframework.core.MethodParameter(validationMethod, -1),
                        bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals(
                Map.of(
                        "email", List.of("E-mail obrigatório", "E-mail inválido"),
                        "name", List.of("Nome obrigatório")),
                problem.getProperties().get("invalidFields"));
    }

    private static class ValidationTarget {
        void validate() {
        }
    }
}
