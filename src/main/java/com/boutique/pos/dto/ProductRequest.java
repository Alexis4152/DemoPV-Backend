package com.boutique.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para crear o actualizar un {@code Product} del catálogo de la tienda del actor
 * ({@code POST}/{@code PUT /api/products/**}, restringido a ADMIN). Para ajustes puntuales
 * de existencias (entradas/salidas de stock) se usa {@link InventoryAdjustRequest} en su
 * propio endpoint, no este DTO.
 */
@Data
public class ProductRequest {
    // Máximo alineado a products.name VARCHAR(200) — sin este tope, un nombre más largo
    // pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
    private String name;
    // products.description es TEXT (sin límite de columna) — este tope NO es por la base
    // de datos, sino de higiene de la app: sin él, nada impide pegar un documento entero
    // aquí, lo que infla el payload de cualquier pantalla que liste productos con su
    // descripción (buscador del POS, tabla de Inventario). 500 caracteres alcanza de sobra
    // para una descripción real de producto en un POS.
    @Size(max = 500, message = "La descripción no puede tener más de 500 caracteres")
    private String description;
    // Máximo alineado a products.barcode VARCHAR(100).
    @Size(max = 100, message = "El código de barras no puede tener más de 100 caracteres")
    private String barcode;
    // Máximo alineado a products.price NUMERIC(12,2) — 10 dígitos enteros + 2 decimales;
    // sin este tope, un valor mayor pasa la validación pero truena al guardar con un
    // error crudo de "numeric field overflow" de PostgreSQL.
    @NotNull @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El precio no puede ser mayor a 9,999,999,999.99")
    private BigDecimal price;
    // Mismo límite que price — products.cost es NUMERIC(12,2) también.
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El costo no puede ser mayor a 9,999,999,999.99")
    private BigDecimal cost;
    // Stock inicial/absoluto del producto (no un delta); los ajustes posteriores de
    // inventario se hacen vía InventoryAdjustRequest, no reenviando este campo. Máximo
    // alineado al límite real de un INTEGER de Postgres (products.stock).
    @PositiveOrZero
    @Max(value = 2147483647, message = "El stock no puede ser mayor a 2,147,483,647")
    private Integer stock;
    // Umbral usado para marcar el producto como "stock bajo" en el buscador y en el
    // widget del Dashboard. Mismo límite de INTEGER que stock.
    @PositiveOrZero
    @Max(value = 2147483647, message = "El stock mínimo no puede ser mayor a 2,147,483,647")
    private Integer minStock;
    // Máximo alineado a products.unit VARCHAR(20).
    @Size(max = 20, message = "La unidad no puede tener más de 20 caracteres")
    private String unit;
    private Long categoryId;
    // Si debe salir en la tienda pública de apartados (ver PublicController). Opcional:
    // null se trata igual que false (no exhibirlo) — ver ProductService.
    private Boolean isReservable;

    // Descuento promocional PÚBLICO para apartados (precio tachado + con descuento en la
    // tienda pública) — distinto del límite privado que el cajero aplica al confirmar.
    // Opcional: null/0 = sin oferta. Validado contra el límite de la tienda al guardar.
    @DecimalMin(value = "0", message = "El descuento de apartado no puede ser negativo")
    @DecimalMax(value = "100", message = "El descuento de apartado no puede ser mayor a 100")
    private BigDecimal apartadoDiscountPercent;
}
