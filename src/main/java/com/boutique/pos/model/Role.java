package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = {"tienda_id", "name"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    // null = rol de plataforma (SUPER_ADMIN); todo lo demás pertenece a una sola tienda
    // y no se comparte ni se ve afectado por cambios en otras tiendas.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_sections", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    @Builder.Default
    private Set<AppSection> sections = new HashSet<>();
}
