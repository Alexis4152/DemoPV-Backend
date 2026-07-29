package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TiendaRequest {
    @NotBlank
    private String name;
}
