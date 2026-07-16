package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryAdjustRequest {
    @NotNull
    private Integer quantity;
    private String reason;
}
