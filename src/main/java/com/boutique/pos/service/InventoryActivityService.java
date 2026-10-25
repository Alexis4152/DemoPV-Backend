package com.boutique.pos.service;

import com.boutique.pos.dto.InventoryActivityDto;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pestaña "Movimientos" de Inventario: en una sola lista paginada, los movimientos de
 * stock ({@code inventory_movements}) y la bitácora de acciones sin stock ({@code
 * inventory_audit_logs}) de la tienda, del más reciente al más antiguo. Solo para
 * admin/supervisor/super admin.
 *
 * <p>Es SQL nativo con UNION ALL (JdbcTemplate) porque son dos tablas distintas que se
 * paginan juntas; los filtros opcionales se agregan solo si vienen, en vez del patrón
 * {@code (:x IS NULL OR ...)} que Postgres no sabe tipar con parámetros nulos.</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryActivityService {

    private static final int MAX_PAGE_SIZE = 100;

    // Columnas ordenables (clave del frontend -> expresión SQL). Lista blanca: la clave del
    // cliente nunca se concatena tal cual en el ORDER BY.
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "date", "x.created_at", "user", "x.user_name", "product", "x.product_name", "action", "x.action");

    // Movimientos anteriores al campo `source` que aún no se clasificaron (ver
    // MovementSourceBackfill) caen en una acción genérica según el tipo.
    private static final String UNION = """
            SELECT * FROM (
              SELECT 'MOVEMENT' AS kind, m.id, m.created_at, m.user_id, u.name AS user_name,
                     COALESCE(m.source, CASE WHEN m.type = 'SALE' THEN 'SALE'
                                             WHEN m.new_stock < m.previous_stock THEN 'MANUAL_OUT'
                                             ELSE 'MANUAL_IN' END) AS action,
                     p.id AS product_id, p.name AS product_name, p.barcode AS product_barcode, p.category_id,
                     (m.new_stock - m.previous_stock) AS quantity, m.previous_stock, m.new_stock, m.reason AS detail
              FROM inventory_movements m
              JOIN products p ON p.id = m.product_id
              JOIN users u ON u.id = m.user_id
              WHERE p.tienda_id = :tiendaId AND m.created_at BETWEEN :from AND :to
              UNION ALL
              SELECT 'AUDIT', a.id, a.created_at, a.user_id, u.name, a.action,
                     p.id, p.name, p.barcode, p.category_id,
                     NULL, NULL, NULL, a.detail
              FROM inventory_audit_logs a
              JOIN users u ON u.id = a.user_id
              LEFT JOIN products p ON p.id = a.product_id
              WHERE a.tienda_id = :tiendaId AND a.created_at BETWEEN :from AND :to
            ) x WHERE 1 = 1
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantScope tenantScope;

    /**
     * @param from       fecha inicial (inclusive)
     * @param to         fecha final (inclusive, hasta el final del día)
     * @param q          texto en nombre o código del producto, o null
     * @param categoryId categoría del producto, o null
     * @param userId     usuario que hizo la acción, o null
     * @param action     acción exacta (origen del movimiento o acción de bitácora), o null
     * @throws AccessDeniedException si el actor no es admin/supervisor/super admin
     */
    public PageResponse<InventoryActivityDto> search(User actor, LocalDate from, LocalDate to, String q, Long categoryId,
                                                     Long userId, String action, String sort, String dir, int page, int size) {
        Long tiendaId = requireTienda(actor);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNum = Math.max(page, 0);

        MapSqlParameterSource params = baseParams(tiendaId, from, to);
        StringBuilder where = new StringBuilder();
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(x.product_name) LIKE :q OR LOWER(x.product_barcode) LIKE :q)");
            params.addValue("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (categoryId != null) {
            where.append(" AND x.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (userId != null) {
            where.append(" AND x.user_id = :userId");
            params.addValue("userId", userId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND x.action = :action");
            params.addValue("action", action);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + UNION + where + ") c", params, Long.class);
        params.addValue("limit", pageSize).addValue("offset", (long) pageNum * pageSize);
        List<InventoryActivityDto> rows = jdbc.query(
                UNION + where + " ORDER BY " + orderBy(sort, dir) + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> InventoryActivityDto.builder()
                        .kind(rs.getString("kind"))
                        .id(rs.getLong("id"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .userId(rs.getLong("user_id"))
                        .userName(rs.getString("user_name"))
                        .action(rs.getString("action"))
                        .productId((Long) rs.getObject("product_id", Long.class))
                        .productName(rs.getString("product_name"))
                        .productBarcode(rs.getString("product_barcode"))
                        .quantity((Integer) rs.getObject("quantity", Integer.class))
                        .previousStock((Integer) rs.getObject("previous_stock", Integer.class))
                        .newStock((Integer) rs.getObject("new_stock", Integer.class))
                        .detail(rs.getString("detail"))
                        .build());

        long totalElements = total != null ? total : 0;
        return PageResponse.<InventoryActivityDto>builder()
                .content(rows)
                .page(pageNum)
                .size(pageSize)
                .totalElements(totalElements)
                .totalPages((int) ((totalElements + pageSize - 1) / pageSize))
                .build();
    }

    /** Usuarios con alguna acción en el rango, para el filtro "Usuario": {id, name}. */
    public List<Map<String, Object>> users(User actor, LocalDate from, LocalDate to) {
        Long tiendaId = requireTienda(actor);
        return jdbc.queryForList("SELECT DISTINCT x.user_id AS id, x.user_name AS name FROM (" + UNION + ") x ORDER BY x.user_name",
                baseParams(tiendaId, from, to));
    }

    /** ORDER BY de la columna elegida (o fecha descendente), con desempate estable. */
    private static String orderBy(String sort, String dir) {
        String column = sort != null ? SORT_COLUMNS.get(sort) : null;
        String tieBreak = "x.created_at DESC, x.kind, x.id DESC";
        if (column == null) return tieBreak;
        return column + ("desc".equalsIgnoreCase(dir) ? " DESC" : " ASC") + " NULLS LAST, " + tieBreak;
    }

    private Long requireTienda(User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede ver los movimientos de inventario");
        }
        Long tiendaId = tenantScope.scopeId(actor);
        if (tiendaId == null) throw new IllegalStateException("Elige una tienda para ver sus movimientos");
        return tiendaId;
    }

    private static MapSqlParameterSource baseParams(Long tiendaId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("tiendaId", tiendaId)
                .addValue("from", Timestamp.valueOf(from.atStartOfDay()))
                .addValue("to", Timestamp.valueOf(to.plusDays(1).atStartOfDay().minusNanos(1000)));
    }
}
