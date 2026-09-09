package com.boutique.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // Tienda.contactEmail — se usa como "Responder a" en los tickets/avisos que salen de
    // esta tienda (ver EmailService). Vacío/null = sin correo propio configurado.
    @Email(message = "El correo de contacto no es válido")
    private String contactEmail;

    // Límites de descuento por línea de venta (Tienda.maxDiscountAmount/maxDiscountPercent
    // — se editan desde esta misma pantalla). Opcionales: null quita el límite en ese criterio.
    @DecimalMin(value = "0", message = "El monto máximo de descuento no puede ser negativo")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "El porcentaje máximo de descuento no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje máximo de descuento no puede ser mayor a 100")
    private BigDecimal maxDiscountPercent;

    // ── Apartados (Tienda.apartadosEnabled/publicSlug/maxApartadoDiscount*/defaultApartadoHours
    // — se editan desde esta misma pantalla). Mismo patrón que los de venta física de arriba.
    private Boolean apartadosEnabled;

    private String publicSlug;

    @DecimalMin(value = "0", message = "El monto máximo de descuento de apartado no puede ser negativo")
    private BigDecimal maxApartadoDiscountAmount;

    @DecimalMin(value = "0", message = "El porcentaje máximo de descuento de apartado no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje máximo de descuento de apartado no puede ser mayor a 100")
    private BigDecimal maxApartadoDiscountPercent;

    private Integer defaultApartadoHours;

    // Meta de venta diaria (Tienda.dailySalesGoal — se edita desde esta misma pantalla).
    // Opcional: null quita la meta (el Dashboard deja de mostrar el % de avance).
    @DecimalMin(value = "0", message = "La meta de venta diaria no puede ser negativa")
    private BigDecimal dailySalesGoal;

    // Segundos entre cada actualización automática de Apartados y el Dashboard
    // (Tienda.pollingIntervalSeconds — se edita desde esta misma pantalla). Acotado para
    // evitar tanto saturar la API (muy bajo) como que se sienta "muerto" (muy alto).
    @Min(value = 5, message = "El intervalo de actualización no puede ser menor a 5 segundos")
    @Max(value = 300, message = "El intervalo de actualización no puede ser mayor a 300 segundos")
    private Integer pollingIntervalSeconds;
}
