package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Configuración SMTP única y global con la que el backend manda TODOS los correos del
 * sistema (tickets de venta, reportes de cierre de caja, avisos de apartados, recuperación
 * de contraseña, alta de usuarios) — antes vivía fija en application.properties/variables de
 * entorno (una sola cuenta Gmail para todo el sistema), ahora es una fila editable en
 * caliente desde {@code /api/admin/mail-config}, sin necesitar un redeploy para rotar la
 * contraseña o cambiar de cuenta.
 * <p>
 * Solo debe existir una fila (la siembra {@code MailConfigDataInitializer} al primer
 * arranque, tomando los valores que hasta entonces vivían en application.properties). Solo
 * un SUPER_ADMIN puede leerla/editarla — es infraestructura de la plataforma, no algo por
 * tienda. El correo que SÍ puede editar cada tienda es {@link Tienda#getContactEmail()},
 * usado únicamente como "Responder a" en los correos de esa tienda: Gmail no permite mandar
 * con un remitente real distinto a la cuenta aquí autenticada.
 */
@Entity
@Table(name = "mail_config")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MailConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "smtp_host", nullable = false, length = 150)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 150)
    private String smtpUsername;

    @Column(name = "smtp_password", length = 255)
    private String smtpPassword;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
