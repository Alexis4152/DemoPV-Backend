package com.boutique.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String name; // Tienda.name — se edita desde la misma pantalla

    // Los límites de abajo son los mismos VARCHAR(N) reales de TiendaInfo (ver esa entidad);
    // redesSociales/notasAdicionales son TEXT (sin límite de columna) — su tope es solo de
    // aplicación, igual que products.description en ProductRequest.
    @Size(max = 20, message = "El RFC no puede tener más de 20 caracteres")
    private String rfc;
    @Size(max = 200, message = "La calle no puede tener más de 200 caracteres")
    private String calle;
    @Size(max = 150, message = "La colonia no puede tener más de 150 caracteres")
    private String colonia;
    @Size(max = 10, message = "El código postal no puede tener más de 10 caracteres")
    private String codigoPostal;
    @Size(max = 150, message = "La localidad no puede tener más de 150 caracteres")
    private String localidad;
    @Size(max = 100, message = "El estado no puede tener más de 100 caracteres")
    private String estado;
    @Size(max = 200, message = "La razón social no puede tener más de 200 caracteres")
    private String razonSocial;
    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    private String telefono;
    @Size(max = 200, message = "La página web no puede tener más de 200 caracteres")
    private String paginaWeb;
    @Size(max = 500, message = "Las redes sociales no pueden tener más de 500 caracteres")
    private String redesSociales;
    @Size(max = 500, message = "Los otros datos no pueden tener más de 500 caracteres")
    private String notasAdicionales;

    // Tienda.contactEmail — se usa como "Responder a" en los tickets/avisos que salen de
    // esta tienda (ver EmailService). Vacío/null = sin correo propio configurado.
    @Email(message = "El correo de contacto no es válido")
    @Size(max = 150, message = "El correo de contacto no puede tener más de 150 caracteres")
    private String contactEmail;

    // Límites de descuento por línea de venta (Tienda.maxDiscountAmount/maxDiscountPercent
    // — se editan desde esta misma pantalla). Opcionales: null quita el límite en ese criterio.
    @DecimalMin(value = "0", message = "El monto máximo de descuento no puede ser negativo")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "El porcentaje máximo de descuento no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje máximo de descuento no puede ser mayor a 100")
    private BigDecimal maxDiscountPercent;

    // ── Apartados (Tienda.apartadosEnabled/publicSlug/maxApartadoDiscount*/defaultApartadoHours
    // — se editan desde esta misma pantalla). Mismo patrón que los de venta física de arriba.
    private Boolean apartadosEnabled;

    // Tienda.publicSlug es VARCHAR(80) único (ver esa entidad).
    @Size(max = 80, message = "El enlace no puede tener más de 80 caracteres")
    private String publicSlug;

    @DecimalMin(value = "0", message = "El monto máximo de descuento de apartado no puede ser negativo")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal maxApartadoDiscountAmount;

    @DecimalMin(value = "0", message = "El porcentaje máximo de descuento de apartado no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje máximo de descuento de apartado no puede ser mayor a 100")
    private BigDecimal maxApartadoDiscountPercent;

    // Tienda.defaultApartadoHours es Integer, acotado a un día (24h) por regla de negocio:
    // un apartado no debería durar más que eso. El mínimo de 1 antes solo lo imponía el
    // `min` nativo del input en el frontend.
    @Min(value = 1, message = "Las horas que dura un apartado deben ser al menos 1")
    @Max(value = 24, message = "Las horas que dura un apartado no pueden ser más de 24")
    private Integer defaultApartadoHours;

    // Meta de venta diaria (Tienda.dailySalesGoal — se edita desde esta misma pantalla).
    // Opcional: null quita la meta (el Dashboard deja de mostrar el % de avance).
    @DecimalMin(value = "0", message = "La meta de venta diaria no puede ser negativa")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal dailySalesGoal;

    // Segundos entre cada actualización automática de Apartados y el Dashboard
    // (Tienda.pollingIntervalSeconds — se edita desde esta misma pantalla). Acotado para
    // evitar tanto saturar la API (muy bajo) como que se sienta "muerto" (muy alto).
    @Min(value = 5, message = "El intervalo de actualización no puede ser menor a 5 segundos")
    @Max(value = 300, message = "El intervalo de actualización no puede ser mayor a 300 segundos")
    private Integer pollingIntervalSeconds;
}
