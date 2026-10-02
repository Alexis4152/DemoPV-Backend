package com.boutique.pos.repository;

import com.boutique.pos.model.RefreshToken;
import com.boutique.pos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repositorio de {@link RefreshToken}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** Busca un refresh token por su valor (el que viaja en la cookie httpOnly). */
    Optional<RefreshToken> findByToken(String token);

    // Se invoca al cambiar contraseña (AuthService#changePassword/#resetPassword) para
    // cerrar sesión en todos los dispositivos — no borra los tokens, solo los marca
    // revocados, por si se quisiera auditar cuántas sesiones había activas.
    /** Marca como revocados todos los refresh tokens sin revocar de un usuario. */
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
