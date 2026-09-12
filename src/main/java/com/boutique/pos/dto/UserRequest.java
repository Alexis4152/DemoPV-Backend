package com.boutique.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Payload para crear o actualizar un {@code User} ({@code POST}/{@code PUT /api/users/**},
 * requiere acceso a la sección USERS). El usuario queda ligado a la tienda del actor que
 * lo crea, salvo que quien lo crea sea SUPER_ADMIN (ver {@link #tiendaId}). La
 * "eliminación" de usuarios ({@code DELETE /api/users/{id}}) es siempre borrado suave
 * ({@code isActive=false} + auditoría de quién/cuándo), nunca se borra la fila.
 */
@Data
public class UserRequest {
    // Máximo alineado a users.name VARCHAR(100) — sin este tope, un nombre más largo
    // pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;
    // Máximo alineado a users.email VARCHAR(150), mismo motivo que arriba.
    @Email
    @NotBlank
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String email;
    // Solo aplica en actualización, y es opcional (si no se envía, la contraseña actual no
    // cambia) — es la vía para que un ADMIN le resetee la contraseña a alguien manualmente.
    // Al CREAR un usuario este campo se ignora siempre: la contraseña inicial nunca la
    // captura quien lo da de alta, el backend genera una temporal y se la manda por correo
    // (ver UserService#create).
    //
    // NO lleva @Size aquí a propósito: el frontend siempre manda este campo como "" (string
    // vacío, nunca null) al crear un usuario, porque el input de contraseña ni se muestra en
    // ese formulario — un @Size(min=6) en el DTO SÍ falla contra "" (longitud 0, @Size solo
    // ignora null, no vacío), tronando la creación de cualquier usuario nuevo. La longitud
    // (6 a 72 caracteres, 72 por el truncado silencioso de BCrypt) se valida en
    // UserService#update, el único lugar donde este campo realmente se usa para algo.
    private String password;
    @NotNull
    private Long roleId;
    private Boolean isActive;
    // solo aplica si quien crea/edita es SUPER_ADMIN; para el resto se ignora y se usa su propia tienda
    private Long tiendaId;
    // Solo aplica si el rol elegido es SUPERVISOR y quien crea/edita es SUPER_ADMIN (para
    // cualquier otro caso se ignora) — reemplaza POR COMPLETO el conjunto de tiendas que
    // este Supervisor administra (ver Tienda#getSupervisor()/UserService#applySupervisedTiendas):
    // null = no tocar la asignación actual; lista vacía = quitarle todas.
    private List<Long> supervisedTiendaIds;
}
