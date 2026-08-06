package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TiendaThemeRequest {
    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un hex válido, ej. #155dea")
    private String primaryColor;
}
