package com.restorant.exception;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<?> business(ApiException e) { return error(e.getStatus(), e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldErrors().getFirst();
        return error(400, field.getField() + ": " + field.getDefaultMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) { return error(400, e instanceof IllegalArgumentException ? e.getMessage() : "Invalid request fields or date/time format"); }
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<?> state(IllegalStateException e) { return error(409, e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate() { return error(409, "That email or table number is already in use, or the change conflicts with existing records"); }
    private ResponseEntity<?> error(int code, String message) { return ResponseEntity.status(code).body(Map.of("message", message)); }
}
