package com.boutique.pos.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Pasar piezas de materia prima al inventario vendible: a un producto que ya existe
 * ({@code productId}) o a uno nuevo ({@code newProductName}, {@code newProductPrice},
 * {@code newProductCategoryId} y, opcional, {@code newProductBarcode}).
 */
@Data
public class RawMaterialTransferRequest {
    @NotNull(message = "Indica cuántas piezas")
    @Positive(message = "La cantidad debe ser mayor a cero")
    private Integer quantity;

    private Long productId;

    @Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
    private String newProductName;
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El precio no puede ser mayor a 9,999,999,999.99")
    private BigDecimal newProductPrice;
    private Long newProductCategoryId;
    @Size(max = 100, message = "El código de barras no puede tener más de 100 caracteres")
    private String newProductBarcode;

    @Size(max = 255, message = "La nota no puede tener más de 255 caracteres")
    private String reason;
}
