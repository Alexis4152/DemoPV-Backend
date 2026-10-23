package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.boutique.pos.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Solicitud de ejercicio de derechos ARCO (Acceso, Rectificación, Cancelación, Oposición,
 * Revocación, Limitación — ver {@link ArcoRequestType}) sobre datos personales que el
 * sistema guarda de alguien, sea cliente de una tienda (nombre/teléfono/correo capturados
 * en una venta o un apartado) o personal con cuenta en el sistema.
 * <p>
 * A propósito NO está ligada a ninguna {@link Tienda}: es una obligación de la plataforma
 * completa (Nexora Systems), no de una tienda en particular — por eso solo SUPER_ADMIN
 * puede verlas/atenderlas (ver {@code AdminArcoRequestController}), igual que {@link
 * MailConfig}.
 * <p>
 * Solo puede levantarla alguien que YA tiene cuenta en el sistema ({@link User}) por los
 * canales {@link ArcoRequestChannel#WEB}/{@link ArcoRequestChannel#ACCOUNT} (el correo se
 * valida contra {@code users.email} al crearla — ver {@code ArcoRequestService#createPublic}/
 * {@code #createOwn} — si no corresponde a ningún usuario, se rechaza con un mensaje claro);
 * el canal {@link ArcoRequestChannel#IN_PERSON} es la única excepción, para alguien que el
 * personal verificó físicamente aunque no tenga cuenta.
 * <p>
 * Ciclo de vida completo en {@link ArcoRequestStatus}. El canal {@code WEB} exige confirmar
 * el correo (link de seguimiento, {@code trackingTokenHash}) antes de que corra el plazo
 * legal — así nadie puede levantar una solicitud a nombre de un correo que no controla, aun
 * sabiendo que esa cuenta existe. El plazo ({@code dueDate}) son 20 días hábiles desde que
 * se recibe formalmente ({@code receivedAt}), con una única extensión posible de otros 20
 * (ver {@code BusinessCalendar}, que por ahora solo excluye sábados/domingos, sin calendario
 * de días festivos).
 * <p>
 * A diferencia de otros sistemas que anonimizan datos automáticamente al resolver una
 * solicitud de Cancelación, aquí la resolución/ejecución de Rectificación/Oposición/
 * Revocación/Limitación es MANUAL: el SUPER_ADMIN describe en {@code executionSummary} qué
 * hizo — son registros con obligaciones fiscales/contables (ventas, apartados), no algo para
 * automatizar sin criterio humano caso por caso. Solo Acceso (genera un reporte de datos en
 * PDF) y Cancelación (desactiva la cuenta del titular, si tiene) tienen una acción
 * automática real al ejecutarse — ver {@code ArcoRequestService#execute}.
 */
@Entity
@Table(name = "arco_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ArcoRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_name", nullable = false, length = 150)
    private String requesterName;

    // nullable: el canal IN_PERSON permite registrar a alguien sin cuenta y sin correo,
    // verificado físicamente por el personal (ver ArcoRequestChannel#IN_PERSON).
    @Column(name = "requester_email", length = 150)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'WEB'")
    @Builder.Default
    private ArcoRequestChannel channel = ArcoRequestChannel.WEB;

    /** Lo que la persona escribió explicando su solicitud (opcional). */
    @Column(columnDefinition = "TEXT")
    private String details;

    // ── Representante (alguien actuando a nombre de otra persona) ──────────────────────
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean representative = false;

    @Column(length = 150)
    private String representativeName;

    /** Quedó aceptado el aviso de privacidad al momento de levantarla (auditoría, no legal por sí sola). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean privacyNoticeAccepted = false;

    // ── Seguimiento por link (solo canal WEB) ───────────────────────────────────────────
    /** SHA-256 del token del link de seguimiento, para buscarlo — nunca se guarda el token en claro aquí. */
    @Column(length = 64, unique = true)
    private String trackingTokenHash;

    /** Copia cifrada (AES-256-GCM, ver {@link EncryptedStringConverter}) del token en claro, por si hace falta reenviar el link. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String trackingTokenEnc;

    // ── Recepción formal (arranca el plazo legal) ───────────────────────────────────────
    /** Cuándo se recibió FORMALMENTE: al confirmar correo (WEB) o de inmediato (ACCOUNT/IN_PERSON). Null mientras sigue {@code PENDING_EMAIL}. */
    private LocalDateTime receivedAt;

    private LocalDateTime emailVerifiedAt;

    /** 20 días hábiles desde {@code receivedAt} (o +20 más si {@code extended}). Null hasta que se recibe formalmente. */
    private LocalDate dueDate;

    // ── Verificación de identidad ────────────────────────────────────────────────────────
    private LocalDateTime identityVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ArcoIdentityMethod identityMethod;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_verified_by_user_id")
    private User identityVerifiedBy;

    @Column(columnDefinition = "TEXT")
    private String identityNotes;

    // ── Solicitud de más información al titular ─────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String infoRequest;

    /** 10 días hábiles desde que se pidió — si no responde a tiempo, pasa a {@code NOT_FILED}. */
    private LocalDate infoDue;

    @Column(columnDefinition = "TEXT")
    private String infoResponse;

    // ── Extensión del plazo (una sola vez) ──────────────────────────────────────────────
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean extended = false;

    @Column(columnDefinition = "TEXT")
    private String extensionReason;

    // ── Respuesta legal ──────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ArcoOutcome outcome;

    /** Obligatorio cuando {@code outcome} es PARCIAL o IMPROCEDENTE — por qué. */
    @Column(columnDefinition = "TEXT")
    private String legalBasis;

    /** Lo que el SUPER_ADMIN escribió al responder — obligatorio para poder responder. */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /** Cuándo se respondió (outcome ya decidido) — null mientras sigue pendiente. */
    private LocalDateTime resolvedAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    // ── Ejecución (solo si quedó APPROVED) ──────────────────────────────────────────────
    /** 15 días hábiles desde que se aprobó. */
    private LocalDate executionDue;

    private LocalDateTime executedAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by_user_id")
    private User executedBy;

    @Column(columnDefinition = "TEXT")
    private String executionSummary;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** La cuenta encontrada por {@code requesterEmail} al momento de crearse (null en algunas
     *  solicitudes {@code IN_PERSON} sin cuenta) — ver el JavaDoc de la clase. Alimenta "Mis
     *  solicitudes"; no se vuelve a verificar después. */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requesterUser;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
}
