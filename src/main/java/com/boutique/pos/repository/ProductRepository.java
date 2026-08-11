package com.boutique.pos.repository;

import com.boutique.pos.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio de {@link Product}.
 *
 * <p>El borrado de un producto es siempre suave (campo {@code isActive=false}), por lo que
 * todas las consultas filtran explícitamente {@code isActive = true} para no mostrar
 * productos eliminados.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Lista los productos activos, ordenados alfabéticamente.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda);
     * cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId) ORDER BY p.name ASC")
    List<Product> findAllActive(@Param("tiendaId") Long tiendaId);

    /**
     * Búsqueda paginada de productos activos con filtros combinables.
     *
     * <p>{@code q} busca coincidencia parcial (insensible a mayúsculas) en nombre o código de
     * barras; el {@code CAST(:q AS string)} evita un bug de Postgres al inferir el tipo de un
     * parámetro de texto nulo. {@code categoryId} y {@code lowStock} son opcionales (nulo = sin
     * filtrar); {@code lowStock = true} limita el resultado a productos cuyo stock ya llegó o
     * bajó del mínimo configurado. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas las
     * tiendas; cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:lowStock IS NULL OR (:lowStock = true AND p.stock <= p.minStock)) " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId)")
    Page<Product> searchActive(@Param("q") String q,
                                @Param("categoryId") Long categoryId,
                                @Param("lowStock") Boolean lowStock,
                                @Param("tiendaId") Long tiendaId,
                                Pageable pageable);

    /**
     * Lista los productos activos cuyo stock actual es menor o igual a su stock mínimo,
     * ordenados de menor a mayor stock. Sin filtro de tienda: recorre todas las tiendas.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock ORDER BY p.stock ASC")
    List<Product> findLowStock();
}
