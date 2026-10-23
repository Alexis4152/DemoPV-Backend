package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload para pedirle más información al titular ({@code POST /{id}/request-info}, SUPER_ADMIN). */
@Data
public class ArcoRequestInfoRequest {

    @NotBlank(message = "Explica qué información necesitas")
    @Size(max = 1000, message = "No puede tener más de 1000 caracteres")
    private String infoRequest;
}
