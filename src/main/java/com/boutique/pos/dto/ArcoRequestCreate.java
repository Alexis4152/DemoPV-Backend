package com.boutique.pos.dto;

import com.boutique.pos.model.ArcoRequestType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para levantar una solicitud ARCO desde el formulario público ({@code POST
 * /api/public/arco-requests}, sin sesión — ver {@code PublicArcoController}). No pide
 * iniciar sesión para LLENAR el formulario, pero {@code requesterEmail} se valida contra
 * {@code users.email} al mandarlo (ver {@code ArcoRequestService#create}): solo puede
 * levantar una solicitud quien ya tiene cuenta en el sistema. El seguimiento después sí
 * exige esa sesión real (ver {@code ArcoRequestController#mine}, "Mis solicitudes").
 */
@Data
public class ArcoRequestCreate {

    @NotBlank(message = "Tu nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String requesterName;

    @NotBlank(message = "Tu correo es obligatorio")
    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String requesterEmail;

    @Pattern(regexp = "[0-9]{10}", message = "El teléfono debe tener 10 dígitos")
    private String requesterPhone;

    @NotNull(message = "Elige qué tipo de solicitud quieres hacer")
    private ArcoRequestType type;

    @Size(max = 2000, message = "El detalle no puede tener más de 2000 caracteres")
    private String details;
}
