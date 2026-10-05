package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Categoría tal como se expone en la tienda pública de apartados (solo para filtrar). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicCategoryDto {
    private Long id;
    private String name;
}
