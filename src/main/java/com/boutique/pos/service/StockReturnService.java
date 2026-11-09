package com.boutique.pos.service;

import com.boutique.pos.dto.ItemOutcomeRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.InventoryMovement;
import com.boutique.pos.model.ItemReturnOutcome;
import com.boutique.pos.model.MovementSource;
import com.boutique.pos.model.MovementType;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regresa (o no) al inventario un producto que había salido, según lo que pasó físicamente
 * con él ({@link ItemReturnOutcome}). Lo usan los tres flujos donde un producto puede volver:
 * cancelar una venta, devolverla y cancelar un apartado — así los tres dejan el mismo rastro
 * en Movimientos de inventario:
 * <ul>
 *   <li>{@code RESTOCK}: entrada con el origen del flujo (stock + qty).</li>
 *   <li>{@code WASTE}: la misma entrada y enseguida una salida {@link MovementSource#MERMA}
 *       (el stock vendible no cambia y la merma queda contada con su cantidad).</li>
 *   <li>{@code NOT_RETURNED}: un movimiento {@link MovementSource#SALE_REFUND_NO_PRODUCT} sin
 *       cambio de stock, para que quede a la vista.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StockReturnService {

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    /**
     * Lee lo que se eligió por artículo. Solo cuentan los artículos con inventario
     * ({@code eligibleIds}); los que no vengan quedan como {@code RESTOCK}. Si algún producto
     * no regresa al stock vendible, el motivo es obligatorio.
     *
     * @param noteField campo del formulario al que se liga el error de motivo faltante
     */
    public Map<Long, ItemReturnOutcome> resolve(List<ItemOutcomeRequest> items, Collection<Long> eligibleIds,
                                                String note, String noteField) {
        Map<Long, ItemReturnOutcome> out = new HashMap<>();
        if (items != null) {
            for (ItemOutcomeRequest i : items) {
                if (i.getItemId() == null || i.getOutcome() == null) continue;
                if (!eligibleIds.contains(i.getItemId())) {
                    throw new IllegalArgumentException("Ese artículo no pertenece a esta operación");
                }
                out.put(i.getItemId(), i.getOutcome());
            }
        }
        boolean anyNotRestocked = out.values().stream().anyMatch(o -> o != ItemReturnOutcome.RESTOCK);
        if (anyNotRestocked && (note == null || note.isBlank())) {
            throw new FieldConflictException(noteField, "Escribe el motivo: hay productos que no regresan al inventario");
        }
        return out;
    }

    /**
     * Aplica el resultado a UN producto (ya bloqueado con findByIdForUpdate por quien llama).
     *
     * @param restockSource origen del movimiento de entrada (SALE_CANCEL, SALE_REFUND, APARTADO_RETURN)
     * @param restockReason texto de la entrada, p. ej. "Cancelación de venta folio 5"
     * @param what          de dónde viene, para merma/no regresó, p. ej. "cancelación de venta folio 5"
     * @param note          motivo que escribió quien lo hizo (obligatorio si no es RESTOCK)
     */
    public void apply(Product p, int qty, ItemReturnOutcome outcome, User user,
                      MovementSource restockSource, String restockReason, String what, String note) {
        if (outcome == null) outcome = ItemReturnOutcome.RESTOCK;
        String suffix = note != null && !note.isBlank() ? ": " + note.trim() : "";
        if (outcome == ItemReturnOutcome.NOT_RETURNED) {
            int stock = p.getStock();
            save(p, user, MovementType.ADJUSTMENT, qty, stock, stock,
                    "Producto no regresó (" + what + ")" + suffix, MovementSource.SALE_REFUND_NO_PRODUCT);
            return;
        }
        int previous = p.getStock();
        p.setStock(previous + qty);
        save(p, user, MovementType.IN, qty, previous, previous + qty,
                restockReason + (outcome == ItemReturnOutcome.WASTE ? " (regresó, pero no sirve)" : ""), restockSource);
        if (outcome == ItemReturnOutcome.WASTE) {
            p.setStock(previous);
            save(p, user, MovementType.OUT, qty, previous + qty, previous,
                    "Merma – " + what + suffix, MovementSource.MERMA);
        }
        productRepository.save(p);
    }

    private void save(Product p, User user, MovementType type, int qty, int previous, int next,
                      String reason, MovementSource source) {
        InventoryMovement mv = new InventoryMovement();
        mv.setProduct(p);
        mv.setUser(user);
        mv.setType(type);
        mv.setQuantity(qty);
        mv.setPreviousStock(previous);
        mv.setNewStock(next);
        mv.setReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
        mv.setSource(source);
        movementRepository.save(mv);
    }
}
