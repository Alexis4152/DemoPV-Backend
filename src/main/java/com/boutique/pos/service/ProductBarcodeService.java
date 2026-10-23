package com.boutique.pos.service;

import com.boutique.pos.model.Product;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Códigos de producto generados por el sistema. Formato: 8 dígitos, los 3 primeros son el
 * id de la tienda y los 5 siguientes el consecutivo de esa tienda (tienda 2, producto 1 →
 * {@code 00200001}). Cada tienda lleva su propio consecutivo ({@link
 * Tienda#getBarcodeSequence()}) y empieza en 1; como el prefijo es la tienda, el mismo
 * consecutivo en dos tiendas nunca da el mismo código, y al escanear se distingue de qué
 * tienda es.
 *
 * <p>Un código generado queda marcado ({@link Product#getBarcodeGenerated()}) y ya no se
 * puede editar (ver {@code ProductService#update}).</p>
 */
@Service
@RequiredArgsConstructor
public class ProductBarcodeService {

    static final long MAX_TIENDA_ID = 999;
    static final int MAX_SEQUENCE = 99_999;

    private final ProductRepository productRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    /**
     * Toma el siguiente consecutivo libre de la tienda y lo regresa ya formateado. Bloquea
     * la fila de la tienda hasta que termine la transacción, así que dos altas simultáneas
     * nunca obtienen el mismo número. Si un número ya lo usa algún producto (por ejemplo,
     * uno capturado a mano con ese mismo formato), se lo salta.
     *
     * <p>Debe llamarse dentro de una transacción activa.</p>
     *
     * @param tiendaId tienda dueña del producto
     * @return el código nuevo, ej. {@code "00200001"}
     * @throws IllegalStateException si el id de la tienda no cabe en 3 dígitos o la tienda
     *         ya usó todos sus consecutivos
     */
    public String nextCode(Long tiendaId) {
        if (tiendaId > MAX_TIENDA_ID) {
            throw new IllegalStateException("Esta tienda no puede generar códigos automáticos (número de tienda mayor a 999)");
        }
        Tienda tienda = tiendaRepository.findByIdForUpdate(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
        int seq = tienda.getBarcodeSequence() != null ? tienda.getBarcodeSequence() : 0;
        String code;
        do {
            seq++;
            if (seq > MAX_SEQUENCE) {
                throw new IllegalStateException("La tienda ya usó los " + MAX_SEQUENCE + " códigos automáticos disponibles");
            }
            code = format(tiendaId, seq);
        } while (productRepository.existsByTiendaIdAndBarcode(tiendaId, code));
        tienda.setBarcodeSequence(seq);
        tiendaRepository.save(tienda);
        return code;
    }

    /** Código con el formato de 8 dígitos: tienda (3) + consecutivo (5). */
    static String format(long tiendaId, int seq) {
        return String.format("%03d%05d", tiendaId, seq);
    }

    /**
     * Cuántos productos activos de la tienda del actor no tienen código todavía.
     *
     * @param actor usuario que consulta
     * @return número de productos sin código (0 si no tiene tienda elegida)
     */
    public long countMissing(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        return tienda == null ? 0 : productRepository.countActiveWithoutBarcode(tienda.getId());
    }

    /**
     * Asigna un código a cada producto activo de la tienda del actor que no tenga, en orden
     * alfabético por nombre, siguiendo el consecutivo de la tienda.
     *
     * @param actor usuario que ejecuta la generación; queda como {@code updatedBy}
     * @return cuántos productos recibieron código
     * @throws IllegalStateException si el actor no tiene una tienda elegida
     */
    @Transactional
    public int generateMissing(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para poder generar códigos");

        List<Product> pending = productRepository.findActiveWithoutBarcode(tienda.getId());
        for (Product p : pending) {
            p.setBarcode(nextCode(tienda.getId()));
            p.setBarcodeGenerated(true);
            p.setUpdatedBy(actor);
            productRepository.save(p);
        }
        return pending.size();
    }
}
