package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Token de un solo uso para el flujo de "olvidé mi contraseña" (ver {@code AuthService}).
 * <p>
 * Vive un tiempo corto ({@code expiresAt}, 30 minutos desde que se genera) y se marca
 * {@code used=true} en cuanto se usa para cambiar la contraseña, para que no pueda
 * reutilizarse. Al pedir un nuevo link, cualquier token anterior sin usar del mismo
 * usuario se invalida (ver {@code PasswordResetTokenRepository#invalidateAllForUser}),
 * así que solo el correo más reciente enviado funciona.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PasswordResetToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
