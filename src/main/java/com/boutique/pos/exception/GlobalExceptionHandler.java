package com.boutique.pos.exception;

import com.boutique.pos.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // `message` se mantiene como los mensajes unidos con ", " (mismo formato de siempre,
    // no rompe ninguna pantalla que solo lea ese campo); `data` ahora trae un mapa
    // "nombre de campo del DTO" -> mensaje, para que un formulario pueda mostrar cada
    // error justo debajo de su input correspondiente (ver Inventory.jsx#handleSave) en vez
    // de un bloque genérico. Si un campo tiene más de una violación, se queda con la
    // primera (LinkedHashMap#putIfAbsent) — mostrar dos mensajes en el mismo campo a la vez
    // no aporta, y así se preserva el orden en que Spring las reportó.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String msg = String.join(", ", fieldErrors.values());
        return ResponseEntity.badRequest().body(
                ApiResponse.<Map<String, String>>builder().success(false).message(msg).data(fieldErrors).build());
    }

    // Mismo formato { campo: mensaje } que handleValidation de arriba, pero para errores
    // de negocio que no vienen de @Valid (ej. "ese código de barras ya lo usa otro
    // producto" — ver ProductService#validateBarcodeUnique). Así el frontend los muestra
    // debajo de su input correspondiente con el mismo código, sin distinguir el origen.
    @ExceptionHandler(FieldConflictException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleFieldConflict(FieldConflictException ex) {
        return ResponseEntity.badRequest().body(
                ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .data(Map.of(ex.getField(), ex.getMessage()))
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCreds(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Credenciales inválidas"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Acceso denegado"));
    }

    // Se dispara ANTES de llegar al controller (y por lo tanto antes de que
    // TiendaLogoService/ProductImageService puedan dar su propio mensaje con el límite
    // exacto de cada uno) solo si el archivo supera el techo global de
    // spring.servlet.multipart.max-file-size — un caso ya bastante extremo dado el margen
    // que ese techo deja sobre el límite real de cada servicio (ver application.properties).
    // Sin este handler, Spring devuelve una página de error genérica en vez del mismo
    // formato {success, message} que espera el frontend.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("El archivo es demasiado pesado"));
    }

    // Cualquier RuntimeException no cubierta por un handler más específico de arriba —
    // antes se le mandaba al cliente el mensaje crudo sin dejar rastro en el log del
    // servidor, así que un 500 real (bug, no un error de negocio esperado) no dejaba forma
    // de diagnosticarlo después del hecho.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ex.getMessage()));
    }
}
