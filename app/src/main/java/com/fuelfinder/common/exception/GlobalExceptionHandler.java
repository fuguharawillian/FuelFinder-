package com.fuelfinder.common.exception;

import com.fuelfinder.modules.auth.service.AccountForbiddenException;
import com.fuelfinder.modules.auth.service.InvalidCredentialsException;
import com.fuelfinder.modules.auth.service.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "Recurso não encontrado",
                "not-found",
                exception.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException exception) {
        return createProblem(
                HttpStatus.CONFLICT,
                "Recurso duplicado",
                "conflict",
                exception.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException exception) {
        return createProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Erro de regra de negócio",
                "business",
                exception.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException exception) {
        return createProblem(
                HttpStatus.BAD_GATEWAY,
                "Serviço externo indisponível",
                "external-service",
                exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return createProblem(
                HttpStatus.UNAUTHORIZED,
                "Autenticação inválida",
                "unauthorized",
                exception.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException exception) {
        return createProblem(
                HttpStatus.UNAUTHORIZED,
                "Token inválido",
                "unauthorized",
                exception.getMessage());
    }

    @ExceptionHandler(AccountForbiddenException.class)
    public ProblemDetail handleForbidden(AccountForbiddenException exception) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "Acesso negado",
                "forbidden",
                exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException exception) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Erro de validação",
                "validation",
                "Falha na validação dos campos de entrada.");

        Map<String, List<String>> invalidFields = new TreeMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            invalidFields.computeIfAbsent(error.getField(), ignored -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }
        problem.setProperty("invalidFields", invalidFields);
        return problem;
    }

    private ProblemDetail createProblem(
            HttpStatus status,
            String title,
            String errorType,
            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://fuelfinder.com/errors/" + errorType));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
