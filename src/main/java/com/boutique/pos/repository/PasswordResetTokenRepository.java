package com.boutique.pos.repository;

import com.boutique.pos.model.PasswordResetToken;
import com.boutique.pos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositorio de {@link PasswordResetToken}.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    /** Busca un token por su valor (el que viaja en el link del correo). */
    Optional<PasswordResetToken> findByToken(String token);

    // Se invocan al pedir un link nuevo, para que solo el más reciente sirva (ver
    // AuthService#forgotPassword) — no borra los anteriores, solo los marca usados,
    // por si se quisiera auditar cuántos links se pidieron.
    /** Marca como usados todos los tokens sin usar de un usuario (invalidación al pedir uno nuevo). */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);
}
