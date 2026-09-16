package com.boutique.pos.payment.infrastructure.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Información del cliente que realiza la compra")
public record CustomerRequest(
        @Schema(description = "Nombre(s) del cliente", example = "Juan")
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 100)
        String name,

        @Schema(description = "Apellido(s) del cliente", example = "Pérez")
        @NotBlank(message = "El apellido del cliente es obligatorio")
        @Size(max = 100)
        String lastName,

        @Schema(description = "Correo electrónico del cliente", example = "juan.perez@example.com")
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo electrónico es inválido")
        String email,

        @Schema(description = "Número telefónico del cliente (10 dígitos)", example = "5512345678")
        @NotBlank(message = "El número telefónico es obligatorio")
        @Size(min = 10, max = 20, message = "El teléfono debe contener entre 10 y 20 caracteres")
        String phoneNumber
) {}
