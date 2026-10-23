package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación completa de una solicitud ARCO — usada tanto para la lista/detalle de
 * administración (SUPER_ADMIN) como para "Mis solicitudes" (cada quien ve las suyas) y el
 * portal público de seguimiento por link (sin exponer nada que no debería ver el propio
 * titular: no hay diferencia de campos entre vistas, todo lo de aquí ya es sobre la propia
 * solicitud de quien la consulta).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ArcoRequestView {
    private Long id;
    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;
    /** Nombre de {@code ArcoRequestType} (ACCESS/RECTIFICATION/CANCELLATION/OPPOSITION/REVOCATION/LIMITATION). */
    private String type;
    /** Nombre de {@code ArcoRequestStatus}. */
    private String status;
    /** Nombre de {@code ArcoRequestChannel}. */
    private String channel;
    private String details;
    private Boolean representative;
    private String representativeName;

    private LocalDateTime createdAt;
    private LocalDateTime receivedAt;
    private LocalDateTime emailVerifiedAt;
    private LocalDate dueDate;

    private LocalDateTime identityVerifiedAt;
    /** Nombre de {@code ArcoIdentityMethod}, o null si no se ha verificado. */
    private String identityMethod;
    private String identityNotes;

    private String infoRequest;
    private LocalDate infoDue;
    private String infoResponse;

    private Boolean extended;
    private String extensionReason;

    /** Nombre de {@code ArcoOutcome}, o null mientras no se responde. */
    private String outcome;
    private String legalBasis;
    private String resolutionNotes;
    private LocalDateTime resolvedAt;
    private String resolvedByName;

    private LocalDate executionDue;
    private LocalDateTime executedAt;
    private String executedByName;
    private String executionSummary;

    private String createdByName;
}
