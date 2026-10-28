package com.boutique.pos.service;

import com.boutique.pos.dto.OfferRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.Offer;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.OfferRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CRUD de ofertas ("compra X paga Y": 2x1, 3x2, ...), con aislamiento por tienda
 * (multi-tenancy) — mismo patrón que {@link CategoryService}.
 *
 * <p>La aplicación de la oferta AL VENDER (cuánto se cobra, cuánto baja del stock) vive en
 * {@link SaleService}, no aquí: esta clase solo administra el catálogo de ofertas y resuelve
 * cuál está vigente para un producto dado.</p>
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final TenantScope tenantScope;

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    public List<Offer> findAll(User actor) {
        return offerRepository.findAllForTienda(tenantScope.scopeId(actor));
    }

    public Page<Offer> search(LocalDateTime from, LocalDateTime to, String name, Boolean isActive,
                               User actor, Pageable pageable) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return offerRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, name, isActive, pageable);
    }

    public Offer findById(Long id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada: " + id));
    }

    public Offer findById(Long id, User actor) {
        Offer o = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (o.getTienda() == null || !scope.equals(o.getTienda().getId()))) {
            throw new IllegalArgumentException("Oferta no encontrada: " + id);
        }
        return o;
    }

    /**
     * Resuelve la oferta vigente AHORA para un producto, si hay alguna — usada por
     * {@link SaleService} al vender.
     *
     * <p>Si un producto llegara a tener más de una oferta activa a la vez (el CRUD no lo
     * impide, aunque en la práctica normalmente sea una sola), se toma la que más piezas
     * de regalo da por promoción ({@code buyQuantity - payQuantity}) — la más conveniente
     * para el cliente, para no dejar la elección al azar del orden en que vinieron de la
     * base de datos.</p>
     *
     * @param product producto a vender
     * @param now momento a evaluar (normalmente {@code LocalDateTime.now()}, inyectado para
     *            que toda una venta con varias líneas evalúe el mismo instante)
     * @return la oferta vigente más conveniente, o vacío si ninguna aplica ahora mismo
     */
    public Optional<Offer> findActiveOfferForProduct(Product product, LocalDateTime now) {
        return offerRepository.findActiveByProductId(product.getId()).stream()
                .filter(o -> o.isCurrentlyActive(now))
                .max(Comparator.comparingInt(o -> o.getBuyQuantity() - o.getPayQuantity()));
    }

    @Transactional
    public Offer create(OfferRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null && tenantScope.isPlatformActor(actor)) {
            throw new IllegalStateException("Elige una tienda para poder crear una oferta");
        }
        validatePayLessThanBuy(req);
        Offer offer = new Offer();
        offer.setTienda(tienda);
        offer.setCreatedBy(actor);
        applyRequest(offer, req, tienda.getId());
        return offerRepository.save(offer);
    }

    @Transactional
    public Offer update(Long id, OfferRequest req, User actor) {
        Offer offer = findById(id, actor);
        validatePayLessThanBuy(req);
        Long tiendaId = offer.getTienda() != null ? offer.getTienda().getId() : null;
        applyRequest(offer, req, tiendaId);
        offer.setUpdatedBy(actor);
        return offerRepository.save(offer);
    }

    private void applyRequest(Offer offer, OfferRequest req, Long tiendaId) {
        offer.setName(req.getName());
        offer.setBuyQuantity(req.getBuyQuantity());
        offer.setPayQuantity(req.getPayQuantity());
        offer.setMaxPromosPerSale(req.getMaxPromosPerSale());
        offer.setStartsAt(req.getStartsAt());
        offer.setEndsAt(req.getEndsAt());
        if (req.getIsActive() != null) offer.setIsActive(req.getIsActive());
        offer.setProducts(resolveProducts(req.getProductIds(), tiendaId));
    }

    private void validatePayLessThanBuy(OfferRequest req) {
        if (!req.isPayLessThanBuy()) {
            throw new FieldConflictException("payQuantity",
                    "Lo que se paga (" + req.getPayQuantity() + ") debe ser menor a lo que se lleva ("
                            + req.getBuyQuantity() + ") — si no, no es una oferta");
        }
        // "Empieza" puede ser pasada; "Termina" no (con 1 min de tolerancia por el desfase
        // entre lo que el usuario ve en su reloj y el instante en que llega al servidor).
        if (req.getEndsAt() != null && req.getEndsAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new FieldConflictException("endsAt", "No puede ser anterior a la fecha y hora actual");
        }
        if (req.getStartsAt() != null && req.getEndsAt() != null && !req.getEndsAt().isAfter(req.getStartsAt())) {
            throw new FieldConflictException("endsAt", "Debe terminar después de que empieza");
        }
    }

    // Valida que cada producto elegido exista y pertenezca a la MISMA tienda de la oferta —
    // sin esto, un SUPER_ADMIN actuando sobre la tienda A podría colar por id un producto
    // de la tienda B (mismo tipo de descuido que causó el bug de roles cruzados en
    // UserService — ver su reconcileRoleForTienda). Nunca se confía en que el id venga
    // limpio del frontend.
    private List<Product> resolveProducts(List<Long> productIds, Long tiendaId) {
        List<Product> found = productRepository.findAllById(productIds);
        if (found.size() != productIds.size()) {
            throw new IllegalArgumentException("Uno o más productos elegidos no existen");
        }
        for (Product p : found) {
            Long productTiendaId = p.getTienda() != null ? p.getTienda().getId() : null;
            if (!tiendaId.equals(productTiendaId)) {
                throw new IllegalArgumentException(
                        "El producto \"" + p.getName() + "\" no pertenece a esta tienda");
            }
        }
        return found;
    }

    public void delete(Long id, User actor) {
        Offer offer = findById(id, actor);
        offer.setIsActive(false);
        offer.setDeletedBy(actor);
        offer.setDeletedAt(LocalDateTime.now());
        offerRepository.save(offer);
    }

    /** Revierte {@link #delete}: la oferta vuelve a aplicar en el POS (según su vigencia). */
    public Offer activate(Long id, User actor) {
        Offer offer = findById(id, actor);
        offer.setIsActive(true);
        offer.setDeletedBy(null);
        offer.setDeletedAt(null);
        offer.setUpdatedBy(actor);
        return offerRepository.save(offer);
    }
}
