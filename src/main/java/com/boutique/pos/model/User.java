package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Usuario del sistema: puede ser un usuario de plataforma (SUPER_ADMIN, {@code tienda} null,
 * sin restricción de tenant) o un usuario perteneciente a una {@link Tienda} específica
 * (dueño, ADMIN, cajero/vendedor, etc., según su {@link Role}).
 * <p>
 * Implementa {@link UserDetails} para integrarse directamente con Spring Security: la
 * autenticación JWT se basa en {@code email} como username, y la autoridad otorgada
 * (ver {@link #getAuthorities()}) se deriva del nombre del {@link Role} asignado.
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt});
 * un usuario inactivo no puede autenticarse (ver {@link #isEnabled()}).
 */
@Entity
@Table(name = "users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User implements UserDetails {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    // nullable so Hibernate can ADD COLUMN on the existing non-empty table;
    // RoleDataInitializer backfills it and the service layer enforces it going forward.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // LAZY (a diferencia del resto de relaciones de esta clase) porque User se referencia
    // a sí mismo aquí — en EAGER, Hibernate encadena el join User→createdBy→createdBy→...
    // sin límite y Postgres truena con "límite de profundidad de stack alcanzado". Por el
    // mismo motivo, JsonIgnoreProperties evita que Jackson expanda role/tienda/los propios
    // createdBy-updatedBy-deletedBy de este User anidado al serializar (User→role→tienda→
    // updatedBy(User)→role→... sería un ciclo infinito).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_user_id")
    private User deletedBy;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── UserDetails ──────────────────────────────────────────────

    /**
     * Expone el {@link Role} del usuario como una única {@link GrantedAuthority} de Spring
     * Security, con el prefijo {@code "ROLE_"} seguido del nombre del rol (ej. "ROLE_ADMIN").
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getName()));
    }

    /** El username de Spring Security para este usuario es su correo electrónico. */
    @Override public String getUsername()              { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    /** Un usuario dado de baja (borrado suave, {@code isActive=false}) no puede autenticarse. */
    @Override public boolean isEnabled()               { return Boolean.TRUE.equals(isActive); }
}
