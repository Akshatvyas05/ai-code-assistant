package com.akshat.ai_code_assistant.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ProblemDetail problem(HttpStatus status, String title, String detail){
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }

    @ExceptionHandler
    public ProblemDetail handleInvalidCredentials(InvalidCredentialException ex){
        ProblemDetail problemDetail = problem(HttpStatus.UNAUTHORIZED, "Authentication Failed", ex.getMessage());
        problemDetail.setType(URI.create("https://api.example.com/errors/unauthorized"));
        return problemDetail;
    }

    @ExceptionHandler
    public ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException ex){
        ProblemDetail problemDetail = problem(HttpStatus.CONFLICT, "Email already in use", ex.getMessage());
        problemDetail.setType(URI.create("https://api.example.com/errors/conflict"));
        return problemDetail;
    }

    // Covers the race where two registrations pass the existsByEmail check together
    @ExceptionHandler
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex){
        return handleEmailAlreadyExists(new EmailAlreadyExistsException("Email is already in use"));
    }

    @ExceptionHandler
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex){
        return problem(HttpStatus.BAD_REQUEST, "Invalid input", "Request body is missing or malformed");
    }

    @ExceptionHandler
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex){
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, "Invalid input", "Validation failed for one or more fields");
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }
}
