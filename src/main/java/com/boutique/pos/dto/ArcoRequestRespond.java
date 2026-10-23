package com.boutique.pos.dto;

import com.boutique.pos.model.ArcoOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para responder legalmente una solicitud ARCO ({@code POST /{id}/respond},
 * SUPER_ADMIN) — decide si procede, procede parcialmente o es improcedente. {@code
 * legalBasis} es obligatorio salvo que {@code outcome} sea {@code PROCEDENTE} (ver
 * validación en {@code ArcoRequestService#respond}, ya que depende de otro campo).
 */
@Data
public class ArcoRequestRespond {

    @NotNull(message = "Elige el resultado")
    private ArcoOutcome outcome;

    @Size(max = 2000, message = "No puede tener más de 2000 caracteres")
    private String legalBasis;

    @NotBlank(message = "Explica tu respuesta (queda como constancia)")
    @Size(max = 2000, message = "No puede tener más de 2000 caracteres")
    private String notes;
}
