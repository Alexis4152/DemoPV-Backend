package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una fila de la pestaña "Movimientos" de Inventario: un movimiento de stock ({@code kind}
 * MOVEMENT, acción = su origen) o una acción de la bitácora ({@code kind} AUDIT, acción =
 * {@code InventoryAuditAction}). Los campos de stock solo vienen en los MOVEMENT.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryActivityDto {
    private String kind;
    private Long id;
    private LocalDateTime createdAt;
    private Long userId;
    private String userName;
    private String action;
    private Long productId;
    private String productName;
    private String productBarcode;
    /** Cambio de piezas con signo (+ entrada, − salida). */
    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private String detail;
}
