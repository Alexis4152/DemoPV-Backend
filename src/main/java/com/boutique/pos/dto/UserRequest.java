package com.boutique.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para crear o actualizar un {@code User} ({@code POST}/{@code PUT /api/users/**},
 * requiere acceso a la sección USERS). El usuario queda ligado a la tienda del actor que
 * lo crea, salvo que quien lo crea sea SUPER_ADMIN (ver {@link #tiendaId}). La
 * "eliminación" de usuarios ({@code DELETE /api/users/{id}}) es siempre borrado suave
 * ({@code isActive=false} + auditoría de quién/cuándo), nunca se borra la fila.
 */
@Data
public class UserRequest {
    @NotBlank
    private String name;
    @Email @NotBlank
    private String email;
    // Opcional en actualización (si no se envía, la contraseña actual no cambia);
    // requerida en la práctica al crear un usuario nuevo.
    private String password;
    @NotNull
    private Long roleId;
    private Boolean isActive;
    // solo aplica si quien crea/edita es SUPER_ADMIN; para el resto se ignora y se usa su propia tienda
    private Long tiendaId;
}
