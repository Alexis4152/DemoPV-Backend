package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Payload público para solicitar un apartado ({@code POST /api/public/tiendas/{slug}/apartados},
 * sin autenticación). Solo pide lo mínimo para poder contactar al cliente — sin cuenta,
 * sin login, sin capturar ningún descuento (eso lo decide el cajero al confirmar, nunca
 * el cliente público — ver {@link ApartadoConfirmRequest}).
 * <p>
 * Sin autenticación de por medio, este es el único DTO de todo el sistema que cualquiera en
 * internet puede mandar directo — de ahí que estos límites importen más que en cualquier
 * otro formulario (nunca hay un cajero revisando antes de que llegue a la base de datos).
 */
@Data
public class ApartadoRequest {
    // Máximo alineado a apartados.customer_name VARCHAR(150).
    @NotBlank
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String customerName;

    // Máximo alineado a apartados.customer_phone VARCHAR(30). Texto libre a propósito (las
    // notas van en `notes`, no aquí) pero debe verse como un teléfono real: solo dígitos y
    // separadores típicos, con al menos 7 dígitos — mismo patrón que PublicApartar.jsx.
    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    @Pattern(regexp = "^(?=(?:.*\\d){7,})[0-9+\\-\\s()]+$",
            message = "El teléfono solo puede tener números, espacios, +, - y paréntesis, con al menos 7 dígitos")
    private String customerPhone;

    // Opcional. Máximo alineado a apartados.customer_email VARCHAR(150).
    @Email
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String customerEmail;

    // apartados.notes es TEXT (sin límite de columna) — este tope es de higiene de la app.
    // 100 y no 500 a propósito: es una nota corta del cliente, no una descripción larga.
    @Size(max = 100, message = "Las notas no pueden tener más de 100 caracteres")
    private String notes;

    @NotEmpty(message = "Agrega al menos un producto")
    @Valid
    private List<ApartadoItemRequest> items;
}
