package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
