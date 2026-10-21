package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Fila de la lista de solicitudes ARCO, para la pantalla de administración (SUPER_ADMIN). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ArcoRequestView {
    private Long id;
    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;
    /** Nombre de {@code ArcoRequestType} (ACCESS/RECTIFICATION/CANCELLATION/OPPOSITION). */
    private String type;
    /** Nombre de {@code ArcoRequestStatus} (PENDING/COMPLETED/REJECTED). */
    private String status;
    private String details;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolvedByName;
}
