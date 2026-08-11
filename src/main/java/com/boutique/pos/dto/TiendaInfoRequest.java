package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
