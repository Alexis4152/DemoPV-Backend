package com.boutique.pos.exception;

import lombok.Getter;

/**
 * Error de negocio (no de {@code @Valid}) asociado a un campo específico del formulario —
 * ej. "ese código de barras ya lo usa otro producto" ({@code ProductService}). Se mapea a
 * la misma forma que {@link GlobalExceptionHandler#handleValidation} ({@code data: {campo:
 * mensaje}}), así el frontend puede mostrarlo justo debajo de su input con el mismo código
 * que ya usa para los errores de validación, sin necesitar un caso especial por mensaje.
 */
@Getter
public class FieldConflictException extends RuntimeException {
    private final String field;

    public FieldConflictException(String field, String message) {
        super(message);
        this.field = field;
    }
}
