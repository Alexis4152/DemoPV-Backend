package com.boutique.pos.dto;

import com.boutique.pos.model.ArcoIdentityMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload para marcar verificada la identidad de quien pide ({@code POST /{id}/verify-identity}, SUPER_ADMIN). */
@Data
public class ArcoRequestVerifyIdentity {

    @NotNull(message = "Elige cómo verificaste su identidad")
    private ArcoIdentityMethod method;

    @NotBlank(message = "Explica cómo la verificaste")
    @Size(max = 500, message = "No puede tener más de 500 caracteres")
    private String notes;
}
