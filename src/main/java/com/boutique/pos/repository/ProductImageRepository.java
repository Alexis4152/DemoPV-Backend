package com.boutique.pos.repository;

import com.boutique.pos.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio de {@link ProductImage}. */
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Fotos de un producto, ordenadas: portada primero, luego por {@code sortOrder}. */
    List<ProductImage> findByProductIdOrderByIsPrimaryDescSortOrderAsc(Long productId);

    /** Todas las fotos de un producto, sin ordenar — para borrarlas (ej. al desactivar el producto). */
    List<ProductImage> findByProductId(Long productId);

    /**
     * Fotos de varios productos a la vez (portada primero), para armar el catálogo
     * público sin un query por producto (N+1) — se agrupan por productId en el service.
     */
    List<ProductImage> findByProductIdInOrderByIsPrimaryDescSortOrderAsc(List<Long> productIds);
}
