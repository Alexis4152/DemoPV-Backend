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

    // ── Fragmentos compartidos de searchActiveWithSales/countActiveWithSales ───────────
    // Deben ser constantes en tiempo de compilación (Java las concatena en una sola
    // constante) porque el valor de @Query tiene que serlo; evita repetir el WHERE/JOIN
    // completo dos veces por separado y que se desincronicen entre sí.
    String SALES_STATS_JOIN =
            "LEFT JOIN (" +
            "  SELECT si.product_id AS pid, SUM(CASE WHEN s.status = 'COMPLETED' THEN si.quantity ELSE 0 END) AS qty " +
            "  FROM sale_items si JOIN sales s ON s.id = si.sale_id " +
            "  GROUP BY si.product_id" +
            ") stats ON stats.pid = p.id ";
    String SEARCH_WITH_SALES_WHERE =
            "WHERE p.is_active = true " +
            "AND (:tiendaId IS NULL OR p.tienda_id = :tiendaId) " +
            "AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')) " +
            "     OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%'))) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:lowStock IS NULL OR (:lowStock = true AND p.stock <= p.min_stock)) " +
            "AND (:soldFilter IS NULL " +
            "     OR (CAST(:soldFilter AS text) = 'NEVER_SOLD' AND COALESCE(stats.qty, 0) = 0) " +
            "     OR (CAST(:soldFilter AS text) = 'TOP_SELLERS' AND COALESCE(stats.qty, 0) > 0)) ";

    /**
     * Búsqueda paginada de productos activos con los mismos filtros que {@link
     * #searchActive} (texto, categoría, stock bajo), más un filtro opcional por historial
     * de ventas ({@code soldFilter}: {@code "NEVER_SOLD"} = nunca vendido, {@code
     * "TOP_SELLERS"} = con al menos una venta, ordenado de más a menos vendido; cualquier
     * otro valor —incluido {@code null}— no filtra por ventas).
     *
     * <p>A diferencia de {@link #searchActive} (JPQL sobre la entidad), este es un query
     * nativo con un {@code LEFT JOIN} a una subconsulta agregada de {@code sale_items}
     * (igual idea que {@link #totalSoldByProduct}, pero aquí SÍ hace falta paginar y
     * ordenar sobre ese total, no solo listarlo). Requiere su propio {@code countQuery}
     * porque Spring Data no puede derivar automáticamente el conteo de un query nativo.
     * Pensado para Inventario, que pagina en el servidor en vez de traer el catálogo
     * completo (ver también {@link #findAllActive}, que sigue existiendo para lo poco que
     * todavía lo use).</p>
     *
     * @param soldFilter {@code "NEVER_SOLD"}, {@code "TOP_SELLERS"}, o {@code null}/otro
     *                   valor para no filtrar por ventas
     */
    @Query(value = "SELECT p.* FROM products p " + SALES_STATS_JOIN + SEARCH_WITH_SALES_WHERE +
                   "ORDER BY CASE WHEN CAST(:soldFilter AS text) = 'TOP_SELLERS' THEN COALESCE(stats.qty, 0) END DESC NULLS LAST, " +
                   "p.name ASC",
           countQuery = "SELECT count(*) FROM products p " + SALES_STATS_JOIN + SEARCH_WITH_SALES_WHERE,
           nativeQuery = true)
    Page<Product> searchActiveWithSales(@Param("q") String q,
                                         @Param("categoryId") Long categoryId,
                                         @Param("lowStock") Boolean lowStock,
                                         @Param("soldFilter") String soldFilter,
                                         @Param("tiendaId") Long tiendaId,
                                         Pageable pageable);

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

    /**
     * Total de unidades vendidas (histórico completo, en ventas {@code COMPLETED}, sin
     * acotar por fecha) de cada producto activo de la tienda, devolviendo
     * {@code [productId, cantidadVendida]} — incluye TODOS los productos activos, incluso
     * los que nunca se han vendido (con {@code cantidadVendida = 0}), gracias al
     * {@code LEFT JOIN}: es justo lo que hace falta para poder filtrar "sin ventas" o
     * "más vendidos" en Inventario, a diferencia de {@code SaleItemRepository.topProductsBetween},
     * que solo devuelve productos que SÍ tienen al menos una venta en el rango.
     *
     * <p>Es un query nativo (no JPQL) porque agrupa directamente sobre las tablas
     * {@code sale_items}/{@code sales}, igual que los reportes de {@code SaleItemRepository}.
     * El estado de la venta se valida dentro del {@code CASE} (no en el {@code JOIN} ni en
     * un {@code WHERE}) para no perder los productos sin ninguna venta: un {@code WHERE
     * s.status = 'COMPLETED'} convertiría el {@code LEFT JOIN} en un inner join de facto,
     * ya que {@code NULL = 'COMPLETED'} nunca es verdadero.</p>
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra
     * por tienda).</p>
     */
    @Query(value = "SELECT p.id, COALESCE(SUM(CASE WHEN s.status = 'COMPLETED' THEN si.quantity ELSE 0 END), 0) " +
                   "FROM products p " +
                   "LEFT JOIN sale_items si ON si.product_id = p.id " +
                   "LEFT JOIN sales s ON s.id = si.sale_id " +
                   "WHERE p.is_active = true AND (:tiendaId IS NULL OR p.tienda_id = :tiendaId) " +
                   "GROUP BY p.id",
           nativeQuery = true)
    List<Object[]> totalSoldByProduct(@Param("tiendaId") Long tiendaId);

    /**
     * Piezas actualmente descontadas del stock por apartados {@code ACTIVE} (ya
     * confirmados por el cajero/admin) de cada producto activo de la tienda, devolviendo
     * {@code [productId, cantidadApartada]} — incluye TODOS los productos activos, incluso
     * los que no tienen ningún apartado {@code ACTIVE} (con {@code cantidadApartada = 0}),
     * mismo motivo del {@code LEFT JOIN} que en {@link #totalSoldByProduct}. Se excluye a
     * propósito {@code PENDING}: todavía NO descuenta stock (ver {@code ApartadoService}),
     * así que contarlo aquí mostraría piezas "apartadas" que en realidad siguen completas
     * en el inventario — confuso para quien lee la columna de Inventario. También se
     * excluyen {@code COMPLETED}/{@code CANCELLED}/{@code EXPIRED}: esos ya no tienen nada
     * pendiente de entregar (el primero ya se vendió, los otros ya liberaron el producto).
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra
     * por tienda).</p>
     */
    @Query(value = "SELECT p.id, COALESCE(SUM(CASE WHEN a.status = 'ACTIVE' THEN ai.quantity ELSE 0 END), 0) " +
                   "FROM products p " +
                   "LEFT JOIN apartado_items ai ON ai.product_id = p.id " +
                   "LEFT JOIN apartados a ON a.id = ai.apartado_id " +
                   "WHERE p.is_active = true AND (:tiendaId IS NULL OR p.tienda_id = :tiendaId) " +
                   "GROUP BY p.id",
           nativeQuery = true)
    List<Object[]> totalReservedByProduct(@Param("tiendaId") Long tiendaId);

    /**
     * Catálogo público de apartados de una tienda: productos activos, marcados como
     * {@code isReservable}, con stock disponible, de la tienda dada — usado por {@code
     * PublicController}, sin autenticación, así que {@code tiendaId} SIEMPRE viene de un
     * slug ya resuelto (nunca de una sesión). {@code categoryId} y {@code q} (búsqueda por
     * nombre, coincidencia parcial sin distinguir mayúsculas — mismo patrón LIKE que {@link
     * #searchActive}) son ambos opcionales y combinables.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isReservable = true AND p.stock > 0 " +
           "AND p.tienda.id = :tiendaId " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%'))) " +
           "ORDER BY p.name ASC")
    Page<Product> findPublicCatalog(@Param("tiendaId") Long tiendaId,
                                     @Param("categoryId") Long categoryId,
                                     @Param("q") String q,
                                     Pageable pageable);
}
