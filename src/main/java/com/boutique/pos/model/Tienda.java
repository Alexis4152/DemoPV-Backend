package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @CreationTimestamp
    private LocalDateTime createdAt;
}
