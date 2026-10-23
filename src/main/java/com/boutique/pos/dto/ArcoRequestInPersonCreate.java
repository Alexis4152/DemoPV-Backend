package com.boutique.pos.dto;

import com.boutique.pos.model.ArcoRequestType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para registrar una solicitud ARCO EN MOSTRADOR ({@code POST
 * /api/admin/arco-requests/in-person}, SUPER_ADMIN) — para alguien que el personal verificó
 * físicamente (identificación mostrada en persona), con o sin cuenta en el sistema. A
 * diferencia de los otros dos canales, aquí {@code requesterEmail} es OPCIONAL: si no
 * corresponde a ningún usuario (o no se da), la solicitud queda sin {@code requesterUser}
 * ligado, pero se registra igual — es el único canal donde eso es válido.
 */
@Data
public class ArcoRequestInPersonCreate {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String requesterName;

    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String requesterEmail;

    @Pattern(regexp = "[0-9]{10}", message = "El teléfono debe tener 10 dígitos")
    private String requesterPhone;

    @NotNull(message = "Elige qué tipo de solicitud quieres hacer")
    private ArcoRequestType type;

    @Size(max = 2000, message = "El detalle no puede tener más de 2000 caracteres")
    private String details;

    private Boolean representative;

    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String representativeName;

    /** Cómo se verificó su identidad en el mostrador (ej. "INE mostrada en persona"). */
    @NotBlank(message = "Explica cómo verificaste su identidad")
    @Size(max = 500, message = "La nota no puede tener más de 500 caracteres")
    private String identityNotes;
}
