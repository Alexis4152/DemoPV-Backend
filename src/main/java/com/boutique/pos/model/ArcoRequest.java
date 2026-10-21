package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Solicitud de ejercicio de derechos ARCO (Acceso, Rectificación, Cancelación, Oposición —
 * ver {@link ArcoRequestType}) sobre datos personales que el sistema guarda de alguien,
 * sea cliente de una tienda (nombre/teléfono/correo capturados en una venta o un apartado)
 * o personal con cuenta en el sistema.
 * <p>
 * A propósito NO está ligada a ninguna {@link Tienda}: es una obligación de la plataforma
 * completa (Nexora Systems), no de una tienda en particular — por eso solo SUPER_ADMIN
 * puede verlas/atenderlas (ver {@code AdminArcoRequestController}), igual que {@link
 * MailConfig}.
 * <p>
 * Solo puede levantarla alguien que YA tiene cuenta en el sistema ({@link User}): el
 * formulario público sigue sin pedir sesión para llenarlo, pero el correo que se escribe
 * ahí se valida contra {@code users.email} al mandarlo (ver {@code ArcoRequestService
 * #create}) — si no corresponde a ningún usuario, se rechaza. {@link #requesterUser} queda
 * ligado a esa cuenta encontrada, que es lo que permite el seguimiento "Mis solicitudes"
 * (ver {@code ArcoRequestController#mine}): inicia sesión con su correo/contraseña reales,
 * nunca con el folio de la solicitud ni con el correo a secas (eso sería fácil de adivinar
 * o de teclear a nombre de alguien más).
 * <p>
 * A diferencia de otros sistemas que anonimizan datos automáticamente al resolver una
 * solicitud de Cancelación, aquí la resolución es manual: el SUPER_ADMIN la marca
 * COMPLETED/REJECTED con una nota explicando qué se hizo, pero el borrado/corrección real
 * de los datos (ej. en una venta o un apartado ya facturado) se hace fuera de este flujo,
 * a propósito — son registros con obligaciones fiscales/contables, no algo para automatizar
 * sin criterio humano caso por caso.
 */
@Entity
@Table(name = "arco_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ArcoRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_name", nullable = false, length = 150)
    private String requesterName;

    @Column(name = "requester_email", nullable = false, length = 150)
    private String requesterEmail;

    @Column(name = "requester_phone", length = 30)
    private String requesterPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArcoRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ArcoRequestStatus status = ArcoRequestStatus.PENDING;

    /** Lo que la persona escribió explicando su solicitud (opcional). */
    @Column(columnDefinition = "TEXT")
    private String details;

    /** Lo que el SUPER_ADMIN escribió al resolverla — obligatorio para poder resolver. */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    /** La cuenta encontrada por {@code requesterEmail} al momento de crearse — ver el
     *  JavaDoc de la clase. Alimenta "Mis solicitudes"; no se vuelve a verificar después
     *  (si la cuenta cambia de correo más tarde, esta solicitud se queda como constancia
     *  de cómo era en ese momento). */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requesterUser;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;
}
