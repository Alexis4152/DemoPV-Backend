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
    // Solo aplica en actualización, y es opcional (si no se envía, la contraseña actual no
    // cambia) — es la vía para que un ADMIN le resetee la contraseña a alguien manualmente.
    // Al CREAR un usuario este campo se ignora siempre: la contraseña inicial nunca la
    // captura quien lo da de alta, el backend genera una temporal y se la manda por correo
    // (ver UserService#create).
    private String password;
    @NotNull
    private Long roleId;
    private Boolean isActive;
    // solo aplica si quien crea/edita es SUPER_ADMIN; para el resto se ignora y se usa su propia tienda
    private Long tiendaId;
}
