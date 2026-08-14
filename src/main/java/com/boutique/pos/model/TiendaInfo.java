package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Datos fiscales y de contacto de una {@link Tienda} (RFC, razón social, dirección,
 * teléfono, redes sociales, etc.), usados principalmente para imprimirse en el ticket
 * PDF de las ventas.
 * <p>
 * Relación uno a uno con {@link Tienda} ({@code tienda_id} es único); es un registro
 * opcional que se captura desde la pantalla de "Datos de la tienda", no en el alta inicial.
 */
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

    /** Razón social de la tienda para efectos fiscales/facturación. */
    @Column(length = 200)
    private String razonSocial;

    @Column(length = 30)
    private String telefono;

    @Column(length = 200)
    private String paginaWeb;

    /** Enlaces o menciones a redes sociales de la tienda, en texto libre. */
    @Column(columnDefinition = "TEXT")
    private String redesSociales;

    // campo libre para lo que no entra en las demás columnas
    @Column(columnDefinition = "TEXT")
    private String notasAdicionales;

    // LAZY: evita cargar en cadena Role/Tienda/etc. de ese usuario (mismo motivo que en las demás entidades)
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
