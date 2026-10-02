package com.boutique.pos.repository;

import com.boutique.pos.model.RefreshToken;
import com.boutique.pos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link RefreshToken}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** Busca un refresh token por su valor (el que viaja en la cookie httpOnly). */
    Optional<RefreshToken> findByToken(String token);

    // Usado por AuthService#login para rechazar un segundo inicio de sesión mientras el
    // usuario ya tiene uno vigente en otro dispositivo/navegador (ver esa validación para
    // el porqué: evita que dos cajeros operen sin querer con la misma cuenta a la vez).
    /** {@code true} si el usuario tiene al menos un refresh token sin revocar y aún vigente. */
    boolean existsByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);

    // Batch (un solo query) para no hacer un existsBy... por cada fila al listar usuarios
    // (ver UserService#findAll) — marca "Sesión activa" en la tabla de Usuarios.
    /** Ids, de entre los dados, que tienen al menos un refresh token sin revocar y vigente. */
    @Query("SELECT DISTINCT t.user.id FROM RefreshToken t WHERE t.user.id IN :userIds AND t.revoked = false AND t.expiresAt > :now")
    List<Long> findUserIdsWithActiveSession(@Param("userIds") List<Long> userIds, @Param("now") LocalDateTime now);

    // Se invoca al cambiar contraseña (AuthService#changePassword/#resetPassword) para
    // cerrar sesión en todos los dispositivos, y al hacer login un rol exento del bloqueo
    // de sesión única (ver AuthService#login) — este último NO corre dentro de una
    // transacción (a propósito, ver el comentario de ese método), así que @Transactional
    // va aquí mismo: una consulta @Modifying siempre necesita una, y así queda garantizada
    // sin importar si quien llama ya tenía una abierta o no (se une a la existente si la
    // hay, o crea una nueva — mismo default de siempre, REQUIRED).
    /** Marca como revocados todos los refresh tokens sin revocar de un usuario. */
    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user = :user AND t.revoked = false")
    void revokeAllForUser(@Param("user") User user);

    // Purga real (borrado de fila), a diferencia de revokeAllForUser: un token revocado o
    // vencido ya no sirve para nada, ni siquiera para auditoría (no hay pantalla que lea
    // esta tabla), así que no hay razón para conservarlo — ver TokenPurgeJob.
    /** Borra los refresh tokens ya vencidos (expiresAt en el pasado). */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
