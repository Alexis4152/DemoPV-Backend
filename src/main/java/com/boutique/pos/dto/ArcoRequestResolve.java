package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload para resolver una solicitud ARCO ({@code PUT /api/admin/arco-requests/{id}/resolve}, SUPER_ADMIN). */
@Data
public class ArcoRequestResolve {

    @NotNull(message = "Elige si se completa o se rechaza")
    @Pattern(regexp = "COMPLETED|REJECTED", message = "Resolución inválida")
    private String status;

    @NotBlank(message = "Explica qué se hizo (queda como constancia)")
    @Size(max = 2000, message = "La nota no puede tener más de 2000 caracteres")
    private String notes;
}
