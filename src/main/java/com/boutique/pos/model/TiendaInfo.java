package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Datos fiscales/de contacto de la tienda (RFC, dirección, etc.) — separados de la
// entidad Tienda para no cargar esos campos en cada lugar que ya serializa Tienda
// (login, ventas, cortes...). Se piden solo desde la pantalla de "Datos de la tienda".
@Entity
@Table(name = "tienda_info", uniqueConstraints = @UniqueConstraint(columnNames = "tienda_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TiendaInfo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(length = 20)
    private String rfc;

    // Dirección separada por campo (no un solo texto libre) para que el ticket
    // la pueda imprimir ordenada, un renglón por dato: calle, colonia, C.P., localidad, estado.
    @Column(length = 200)
    private String calle;

    @Column(length = 150)
    private String colonia;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 150)
    private String localidad;

    @Column(length = 100)
    private String estado;

    @Column(length = 200)
    private String razonSocial;

    @Column(length = 30)
    private String telefono;

    @Column(length = 200)
    private String paginaWeb;

    @Column(columnDefinition = "TEXT")
    private String redesSociales;

    // campo libre para lo que no entra en las demás columnas
    @Column(columnDefinition = "TEXT")
    private String notasAdicionales;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
