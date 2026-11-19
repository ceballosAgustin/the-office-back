package com.aceballos.the_office_back.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflictException(ResourceConflictException ex) {
        log.warn("Conflicto de estado o recurso: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler({ BusinessRuleException.class, IllegalArgumentException.class })
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex) {
        log.warn("Petición inválida o regla de negocio no cumplida: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponse> handleInvalidFormatExceptions(Exception ex) {
        log.warn("Formato de petición o parámetro inválido: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("El formato de los datos enviados o el valor de un parámetro no es válido")
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("No tienes permisos suficientes para realizar esta operación")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            // Los errores a nivel de objeto (no de campo) se informan bajo el nombre del objeto
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Error de validación en parámetros: {}", errors);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Error de validación en los campos enviados")
                .fieldErrors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "field";
            errors.put(fieldName, violation.getMessage());
        }

        log.warn("Error de validación de restricciones: {}", errors);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Error de validación en los campos enviados")
                .fieldErrors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Intento de autenticación fallido: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Credenciales inválidas")
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MissingServletRequestPartException.class })
    public ResponseEntity<ErrorResponse> handleMissingRequestData(Exception ex) {
        log.warn("Falta un parámetro o parte obligatoria de la petición: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "Falta un parámetro o archivo obligatorio en la petición");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortProperty(PropertyReferenceException ex) {
        log.warn("Propiedad de ordenamiento inválida: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "El criterio de ordenamiento indicado no es válido");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Método HTTP no soportado: {}", ex.getMessage());
        ResponseEntity<ErrorResponse> response = buildError(HttpStatus.METHOD_NOT_ALLOWED,
                "El método HTTP utilizado no está permitido para este recurso");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Tipo de contenido no soportado: {}", ex.getMessage());
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido enviado no es soportado");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "El recurso solicitado no existe");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Tamaño máximo de subida excedido: {}", ex.getMessage());
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo enviado supera el tamaño máximo permitido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return buildError(HttpStatus.CONFLICT,
                "La operación no pudo completarse porque entra en conflicto con datos existentes");
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();
        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        log.error("Error no controlado en el servidor: ", ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Ocurrió un error interno en el servidor. Por favor, intenta de nuevo más tarde.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
