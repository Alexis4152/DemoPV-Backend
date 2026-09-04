package com.boutique.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para actualizar los datos fiscales y de contacto de una tienda
 * ({@code PUT /api/tiendas/{id}/info}, accesible al ADMIN de esa tienda o al SUPER_ADMIN).
 * Estos datos alimentan directamente el encabezado/pie del ticket de venta en PDF
 * (razón social, RFC, dirección desglosada, contacto), por lo que se guardan en la
 * entidad {@code TiendaInfo} asociada a la {@code Tienda}, separada de su información
 * básica de cuenta.
 */
@Data
public class TiendaInfoRequest {
    @NotBlank
    private String name; // Tienda.name — se edita desde la misma pantalla

    private String rfc;
    private String calle;
    private String colonia;
    private String codigoPostal;
    private String localidad;
    private String estado;
    private String razonSocial;
    private String telefono;
    private String paginaWeb;
    private String redesSociales;
    private String notasAdicionales;

    // Límites de descuento por línea de venta (Tienda.maxDiscountAmount/maxDiscountPercent
    // — se editan desde esta misma pantalla). Opcionales: null quita el límite en ese criterio.
    @DecimalMin(value = "0", message = "El monto máximo de descuento no puede ser negativo")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "El porcentaje máximo de descuento no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje máximo de descuento no puede ser mayor a 100")
    private BigDecimal maxDiscountPercent;
}
