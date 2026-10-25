package com.boutique.pos.service;

import com.boutique.pos.model.InventoryAuditAction;
import com.boutique.pos.model.InventoryAuditLog;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.InventoryAuditLogRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Registra en la bitácora de Inventario ({@link InventoryAuditLog}) las acciones que no
 * mueven stock. Si no hay tienda o usuario que registrar, la acción se omite (solo queda
 * en el log del servidor) en vez de tumbar la operación que lo llama.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAuditService {

    private final InventoryAuditLogRepository repository;
    private final TenantScope tenantScope;

    /** Acción sobre un producto (la tienda es la del producto). */
    public void log(User actor, InventoryAuditAction action, Product product, String detail) {
        Long tiendaId = product.getTienda() != null ? product.getTienda().getId() : null;
        save(actor, tiendaId, action, product, detail);
    }

    /** Acción sobre la tienda en general (carga masiva, descargas...): la tienda del actor. */
    public void log(User actor, InventoryAuditAction action, String detail) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        save(actor, tienda != null ? tienda.getId() : null, action, null, detail);
    }

    private void save(User actor, Long tiendaId, InventoryAuditAction action, Product product, String detail) {
        if (tiendaId == null || actor == null) {
            log.warn("Bitácora de inventario: se omite {} sin tienda o usuario ({})", action, detail);
            return;
        }
        // Sin try/catch a propósito: se guarda en la misma transacción que la operación, y un
        // error de base de datos la marca para rollback aunque se atrape aquí — atraparlo solo
        // escondía la causa real detrás de un "Transaction silently rolled back". Las
        // restricciones de las columnas enum se mantienen al día en
        // InventoryEnumConstraintMigration para que esto no falle al agregar acciones nuevas.
        repository.save(InventoryAuditLog.builder()
                .tiendaId(tiendaId)
                .user(actor)
                .action(action)
                .product(product)
                .detail(detail)
                .build());
    }
}
