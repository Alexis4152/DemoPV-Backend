package com.boutique.pos.repository;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Repositorio de {@link Apartado}. */
public interface ApartadoRepository extends JpaRepository<Apartado, Long> {

    /**
     * Búsqueda paginada de apartados con filtros combinables, acotada a la tienda del
     * actor. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas; {@code
     * status} y {@code q} nulos no filtran; {@code from}/{@code to} siempre llegan con un
     * valor real (nunca null, ver {@code ApartadoService#search}) por la misma limitación
     * de Postgres con parámetros timestamp nulos que ya documentan {@code
     * SaleRepository}/{@code CashCutRepository}.
     *
     * <p>{@code q} es una búsqueda de texto libre (parcial, sin distinguir mayúsculas)
     * contra nombre y teléfono del cliente, O el nombre de cualquier producto de sus
     * líneas — así un cajero puede buscar "Juan" o "Anillo" con el mismo campo, sin tener
     * que saber de antemano si lo que recuerda es el cliente o el producto. El de producto
     * usa un {@code EXISTS} (no un {@code JOIN}) a propósito: así no duplica filas de
     * {@code Apartado} cuando el apartado tiene varias líneas, y no complica la paginación
     * con {@code DISTINCT} + fetch join.</p>
     */
    @Query("SELECT a FROM Apartado a WHERE " +
           "(:tiendaId IS NULL OR a.tienda.id = :tiendaId) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND a.requestedAt BETWEEN :from AND :to " +
           "AND (:q IS NULL OR " +
           "     LOWER(a.customerName) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) " +
           "     OR LOWER(a.customerPhone) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) " +
           "     OR EXISTS (SELECT 1 FROM ApartadoItem i WHERE i.apartado = a AND LOWER(i.productName) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')))" +
           ") " +
           "ORDER BY a.requestedAt DESC")
    Page<Apartado> search(@Param("tiendaId") Long tiendaId,
                           @Param("status") ApartadoStatus status,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("q") String q,
                           Pageable pageable);

    /**
     * Cuenta los apartados {@code PENDING} de una tienda — alimenta el badge del sidebar
     * ("N apartados nuevos"). {@code tiendaId} nulo = todas las tiendas (SUPER_ADMIN).
     */
    @Query("SELECT COUNT(a) FROM Apartado a WHERE a.status = 'PENDING' " +
           "AND (:tiendaId IS NULL OR a.tienda.id = :tiendaId)")
    long countPending(@Param("tiendaId") Long tiendaId);

    /** Todos los apartados {@code ACTIVE} cuyo plazo ya venció — usado por {@code ApartadoExpiryJob}. */
    List<Apartado> findAllByStatusAndExpiresAtBefore(ApartadoStatus status, LocalDateTime now);

    /**
     * Piezas de cada producto (de los {@code productIds} dados) ya reclamadas por OTRAS
     * solicitudes {@code PENDING} (excluyendo {@code excludeApartadoId}) — estas todavía
     * NO descontaron stock real (eso solo pasa al confirmar), así que sin este dato el
     * stock crudo del producto parece más libre de lo que en realidad está si hay más de
     * una solicitud pendiente compitiendo por él. Usado por {@code
     * ApartadoService#populateAvailableStock} para la pantalla de confirmación.
     *
     * @return filas {@code [productId, piezasReclamadas]}
     */
    @Query(value = "SELECT ai.product_id, COALESCE(SUM(ai.quantity), 0) " +
                   "FROM apartado_items ai JOIN apartados a ON a.id = ai.apartado_id " +
                   "WHERE a.status = 'PENDING' AND a.id <> :excludeApartadoId AND ai.product_id IN (:productIds) " +
                   "GROUP BY ai.product_id",
           nativeQuery = true)
    List<Object[]> sumPendingQuantityByProductExcluding(@Param("excludeApartadoId") Long excludeApartadoId,
                                                          @Param("productIds") List<Long> productIds);
}
