package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tienda (sucursal/negocio) del sistema multi-tenant: la unidad de aislamiento a la que
 * pertenecen usuarios, roles, productos, categorías, ventas y cortes de caja.
 * <p>
 * Salvo el usuario de plataforma SUPER_ADMIN (que no pertenece a ninguna tienda y ve todo,
 * ver {@code TenantScope}), cada dato de negocio queda acotado a su {@code tienda}, y las
 * consultas se filtran por {@code tienda.id} para garantizar el aislamiento entre negocios.
 * <p>
 * {@code primaryColor} y {@code logoPath} permiten personalizar la apariencia del portal
 * para esta tienda (color de marca y logo); los datos fiscales/de contacto usados en el
 * ticket impreso viven aparte, en {@link TiendaInfo}.
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt}).
 */
@Entity
@Table(name = "tiendas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tienda {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // color de marca elegido por el ADMIN de esta tienda (hex, ej. "#155dea");
    // null = usa el azul Nexora por default en el frontend
    @Column(length = 7)
    private String primaryColor;

    // ruta pública del logo subido por el ADMIN de esta tienda (ej. "/uploads/logos/tienda-1-xxx.png");
    // null = usa el logo de Nexora por default en el frontend
    @Column(length = 255)
    private String logoPath;

    // Límites de descuento por línea de venta que el ADMIN fija para su tienda (pantalla
    // "Datos de la tienda"), para que un cajero no pueda dejar un producto prácticamente
    // gratis. Ambos son opcionales e independientes: si están definidos, un descuento se
    // rechaza (en el POS y de nuevo en el backend al registrar la venta) si excede
    // CUALQUIERA de los dos, el que sea más restrictivo para esa línea — no es necesario
    // definir los dos a la vez. null = sin límite en ese criterio.
    @Column(precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxDiscountPercent;

    // ── Apartados (reservas) ─────────────────────────────────────────────────
    // Mismo par que arriba (maxDiscountAmount/Percent) pero para apartados, a propósito
    // separado: una tienda puede querer condiciones de descuento distintas para incentivar
    // apartados que las que usa en venta física. Igual de opcionales/independientes.
    @Column(precision = 12, scale = 2)
    private BigDecimal maxApartadoDiscountAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxApartadoDiscountPercent;

    // Apagado por default a propósito: una tienda recién creada, o que nunca configuró
    // esto, no debe exponer su catálogo públicamente sin que el ADMIN lo decida.
    @Column(nullable = false)
    @Builder.Default
    private Boolean apartadosEnabled = false;

    // Identificador único y amigable en la URL pública (ej. "/apartar/mi-tienda"), editable
    // por el ADMIN en "Datos de la tienda". Es lo único que distingue de qué tienda es el
    // catálogo cuando distintas tiendas de distintos dueños comparten el mismo frontend —
    // no hay login de cliente, todo se resuelve por este slug (ver PublicController).
    @Column(unique = true, length = 80)
    private String publicSlug;

    // Cuántas horas dura un apartado a partir de que se CONFIRMA (no desde que se solicita
    // — mientras está PENDING no corre ningún conteo). El cajero puede capturar un valor
    // distinto al confirmar uno en particular; este es solo el default sugerido.
    @Column(nullable = false)
    @Builder.Default
    private Integer defaultApartadoHours = 24;

    // Usuario con rol SUPERVISOR ("Supervisor de tiendas") a cargo de esta tienda, o null si
    // no tiene ninguno asignado todavía. Es la relación INVERSA a User.tienda: mientras un
    // usuario normal cuelga de una sola tienda, un Supervisor no tiene tienda propia (igual
    // que SUPER_ADMIN) y en cambio puede tener VARIAS tiendas apuntándolo a él aquí — de ahí
    // que la relación viva de este lado (Tienda→supervisor) y no al revés. Ver TenantScope,
    // que es quien realmente decide qué puede ver/hacer un Supervisor con esto.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;
    // Meta de venta diaria que el ADMIN fija para su tienda (pantalla "Datos de la
    // tienda"), usada por el Dashboard para mostrar el % de avance del día contra esta
    // meta. Opcional: null = sin meta definida (el Dashboard solo muestra la venta del
    // día, sin porcentaje).
    @Column(precision = 12, scale = 2)
    private BigDecimal dailySalesGoal;

    // Segundos entre cada actualización automática (polling) de Apartados y el Dashboard
    // — así ambos módulos se refrescan solos sin que alguien tenga que recargar la
    // página o volver a entrar. Ajustable por el ADMIN en "Datos de la tienda"; 20s de
    // default, un punto medio razonable entre "se siente vivo" y no saturar la API.
    @Column(nullable = false)
    @Builder.Default
    private Integer pollingIntervalSeconds = 20;

    // Correo de contacto de ESTA tienda, editable por su ADMIN (o SUPERVISOR/SUPER_ADMIN)
    // en "Datos de la tienda" — es lo único de la configuración de correo que un cliente
    // puede tocar. Nunca se usa como remitente real (Gmail no deja mandar con un "De:"
    // distinto a la cuenta autenticada en MailConfig): EmailService lo pone como
    // "Responder a", así que si el comprador contesta un ticket, le llega a esta tienda y
    // no a la cuenta de correo centralizada de la plataforma. null = sin correo propio
    // configurado, los correos de esta tienda no llevan "Responder a".
    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    // LAZY: evita el ciclo User→Tienda(EAGER)→createdBy(User)→Tienda→... (User carga su Tienda en EAGER).
    // JsonIgnoreProperties evita además que, al serializar, Jackson expanda role/tienda de
    // este User anidado — sin esto, Tienda.updatedBy→role→tienda→updatedBy→... es un ciclo
    // infinito real (lo que rompía /api/auth/login y /api/auth/me en producción).
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
}
