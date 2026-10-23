package com.boutique.pos.dto;

import com.boutique.pos.model.ArcoRequestType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para levantar una solicitud ARCO, por cualquiera de los dos canales que la
 * llenan a mano:
 * <ul>
 *   <li>Público ({@code POST /api/public/arco-requests}, sin sesión — ver {@code
 *   PublicArcoController}/{@code ArcoRequestService#createPublic}): {@code requesterName}/
 *   {@code requesterEmail} son obligatorios y se validan contra {@code users.email}; queda
 *   {@code PENDING_EMAIL} hasta que confirma su correo por el link que se le manda.</li>
 *   <li>Cuenta propia ({@code POST /api/arco-requests}, con sesión — ver {@code
 *   ArcoRequestController}/{@code ArcoRequestService#createOwn}): {@code requesterName}/
 *   {@code requesterEmail} se IGNORAN (se usan los de la cuenta en sesión); si {@link
 *   com.boutique.pos.model.ArcoRequestType#needsStrongIdentity()}, {@code password} es
 *   obligatoria (vuelve a confirmar que de verdad es quien dice ser).</li>
 * </ul>
 */
@Data
public class ArcoRequestCreate {

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

    /** Si quien llena el formulario actúa a nombre de alguien más (ej. un familiar). */
    private Boolean representative;

    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String representativeName;

    /** Obligatoria solo si {@link #getType()} necesita identidad fuerte Y hay sesión (canal cuenta propia). */
    private String password;

    @AssertTrue(message = "Debes aceptar el aviso de privacidad")
    private boolean acceptPrivacyNotice;
}
