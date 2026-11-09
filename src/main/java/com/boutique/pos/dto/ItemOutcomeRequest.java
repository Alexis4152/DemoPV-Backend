package com.boutique.pos.dto;

import com.boutique.pos.model.ItemReturnOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Qué pasó con UN artículo (línea de venta o de apartado) al cancelar o devolver. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemOutcomeRequest {
    @NotNull(message = "Falta el artículo")
    private Long itemId;
    @NotNull(message = "Elige qué pasó con el producto")
    private ItemReturnOutcome outcome;
}
