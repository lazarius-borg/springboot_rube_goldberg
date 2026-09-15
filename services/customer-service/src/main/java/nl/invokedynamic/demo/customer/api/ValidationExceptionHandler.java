package nl.invokedynamic.demo.customer.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.*;

@RestControllerAdvice
public class ValidationExceptionHandler {

    private static final URI VALIDATION_ERROR_TYPE = URI.create("https://example.invalid/problems/validation-error");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more request fields failed validation");
        pd.setType(VALIDATION_ERROR_TYPE);
        pd.setTitle("Validation Failed");

        List<Map<String, String>> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "name", err.getField(),
                        "reason", Objects.requireNonNullElse(err.getDefaultMessage(), "Invalid value")
                ))
                .toList();

        pd.setProperty("invalidParams", invalidParams);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more request parameters failed validation");
        pd.setType(VALIDATION_ERROR_TYPE);
        pd.setTitle("Validation Failed");

        List<Map<String, String>> invalidParams = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    String paramName = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream().map(err -> {
                        String field = err instanceof FieldError fe ? fe.getField() : (paramName != null ? paramName : "parameter");
                        return Map.of(
                                "name", field,
                                "reason", Objects.requireNonNullElse(err.getDefaultMessage(), "Invalid parameter value")
                        );
                    });
                })
                .toList();

        pd.setProperty("invalidParams", invalidParams);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more constraints were violated");
        pd.setType(VALIDATION_ERROR_TYPE);
        pd.setTitle("Validation Failed");

        List<Map<String, String>> invalidParams = ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                        "name", cv.getPropertyPath().toString(),
                        "reason", cv.getMessage()
                ))
                .toList();

        pd.setProperty("invalidParams", invalidParams);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
        pd.setType(VALIDATION_ERROR_TYPE);
        pd.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(pd);
    }
}
