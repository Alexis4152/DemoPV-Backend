package com.boutique.pos.repository;

import com.boutique.pos.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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
     * Busca un producto activo por su código de barras EXACTO, dentro de una tienda.
     *
     * <p>A diferencia de {@link #searchActive} (coincidencia parcial, pensada para que un
     * humano teclee), esta consulta es para los flujos de lector de código de barras: el
     * escáner manda el código completo de una sola vez, y una coincidencia parcial podría
     * ser ambigua si un código es substring de otro. {@code tiendaId} nulo indica
     * SUPER_ADMIN (sin filtrar); cualquier otro valor restringe la búsqueda a esa tienda.</p>
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.barcode = :barcode " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId)")
    Optional<Product> findByBarcodeExact(@Param("barcode") String barcode, @Param("tiendaId") Long tiendaId);

    /**
     * Lista los productos activos cuyo stock actual es menor o igual a su stock mínimo,
     * ordenados de menor a mayor stock. Sin filtro de tienda: recorre todas las tiendas.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock ORDER BY p.stock ASC")
    List<Product> findLowStock();
}
