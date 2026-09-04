package com.boutique.pos.service;

import com.boutique.pos.dto.SaleItemRequest;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.*;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro y consulta de ventas (punto de venta), y cancelación de ventas ya registradas.
 *
 * <p>Una venta ({@link Sale}) siempre queda atada al corte de caja ({@link CashCut})
 * ABIERTO del propio vendedor/cajero que la registra — cada usuario tiene el suyo, no
 * existe "el corte de la tienda" (varios pueden estar abiertos a la vez en la misma
 * tienda). Al crear una venta se valida stock disponible, se arma cada {@link SaleItem},
 * se descuenta el stock del producto y se registra su {@link InventoryMovement}
 * correspondiente (tipo {@code SALE}). Cancelar una venta revierte el stock (con su
 * propio movimiento de tipo {@code IN}) pero nunca borra la fila: se marca como
 * {@code CANCELLED} y se conserva quién y cuándo la canceló.</p>
 */
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SaleItemRepository saleItemRepository;
    private final CashCutRepository cashCutRepository;
    private final InventoryMovementRepository movementRepository;
    private final EmailService emailService;
    private final TenantScope tenantScope;

    // BETWEEN siempre necesita las dos fechas: cuando el filtro viene vacío, Postgres no
    // logra inferir el tipo de un parámetro timestamp nulo (ni con CAST), así que en vez
    // de mandar null se usa un rango que cubre todo el historial.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /**
     * Búsqueda paginada de ventas con filtros combinables, acotada a la tienda del actor.
     *
     * @param from fecha/hora mínima (inclusiva); si es null se usa un límite inferior
     *             muy antiguo para evitar pasar null al BETWEEN de la consulta
     * @param to fecha/hora máxima (inclusiva); si es null se usa un límite superior muy
     *           lejano, por la misma razón
     * @param customerName filtro por nombre de cliente (parcial), o null
     * @param paymentMethod filtro por método de pago, o null
     * @param status filtro por estado (completada/cancelada), o null
     * @param pageable paginación y orden solicitados
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return página de ventas que cumplen los filtros
     */
    public Page<Sale> findAll(LocalDateTime from, LocalDateTime to, String customerName,
                               PaymentMethod paymentMethod, SaleStatus status, Pageable pageable, User actor) {
        Long scope = tenantScope.scopeId(actor);
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return saleRepository.search(scope, effectiveFrom, effectiveTo, customerName, paymentMethod, status, pageable);
    }

    /**
     * Busca una venta por id sin validar a qué tienda pertenece. Uso interno; para
     * flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id de la venta
     * @return la venta encontrada
     * @throws IllegalArgumentException si no existe
     */
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + id));
    }

    /**
     * Busca una venta por id validando que pertenezca a la tienda del actor.
     *
     * @param id id de la venta
     * @param actor usuario que realiza la consulta
     * @return la venta encontrada, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Sale findById(Long id, User actor) {
        Sale s = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (s.getTienda() == null || !scope.equals(s.getTienda().getId()))) {
            throw new IllegalArgumentException("Venta no encontrada: " + id);
        }
        return s;
    }

    /**
     * Registra una venta nueva: valida stock, arma cada línea de venta, descuenta el
     * inventario y ata la venta al corte de caja abierto del actor.
     *
     * <p>Reglas de negocio clave:</p>
     * <ul>
     *   <li>El actor debe tener un corte de caja propio en estado {@code OPEN}; la venta
     *   se ata a ese corte (no existe un "corte de la tienda" compartido).</li>
     *   <li>Cada producto debe estar activo y tener stock suficiente; si no, se aborta
     *   toda la venta (la operación es transaccional).</li>
     *   <li>Por cada línea se descuenta el stock del producto y se registra un
     *   {@link InventoryMovement} de tipo {@code SALE} con el stock anterior y nuevo.</li>
     *   <li>El total se calcula como {@code subtotal - descuento + impuestos}.</li>
     *   <li>Si el request trae un correo de cliente, se dispara el envío asíncrono del
     *   ticket en PDF vía {@link EmailService#sendTicketEmail}.</li>
     * </ul>
     *
     * @param req datos de la venta: líneas, método de pago, descuento/impuestos
     *            globales, datos opcionales de cliente
     * @param actor usuario (cajero/vendedor) que registra la venta; debe tener un corte
     *              de caja propio abierto; queda registrado como {@code user} de la venta
     * @return la venta ya registrada y persistida
     * @throws IllegalStateException si el actor no tiene un corte abierto, si algún
     *         producto está inactivo o no tiene stock suficiente
     * @throws IllegalArgumentException si algún producto de las líneas no existe o no
     *         pertenece a la tienda del actor
     */
    @Transactional
    public Sale create(SaleRequest req, User actor) {
        // cada cajero/vendedor puede tener su propio corte abierto en simultáneo con
        // los de sus compañeros de tienda, así que la venta se pega al SUYO, no a
        // "el" corte abierto de la tienda (ya no existe tal cosa).
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de registrar ventas"));

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(req.getCustomerName());
        sale.setCustomerEmail(req.getCustomerEmail());
        sale.setPaymentMethod(req.getPaymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes(req.getNotes());
        sale.setTienda(actor.getTienda());

        List<SaleItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest ir : req.getItems()) {
            Product product = productService.findById(ir.getProductId(), actor);

            if (!product.getIsActive()) throw new IllegalStateException("Producto inactivo: " + product.getName());

            BigDecimal qty = ir.getQuantity();
            int qtyInt = qty.intValue();
            if (product.getStock() < qtyInt) {
                throw new IllegalStateException("Stock insuficiente para " + product.getName()
                        + " (disponible: " + product.getStock() + ")");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal discount = ir.getDiscount() != null ? ir.getDiscount() : BigDecimal.ZERO;
            BigDecimal itemSubtotal = unitPrice.multiply(qty).subtract(discount);

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(qty);
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);
            item.setSubtotal(itemSubtotal);
            items.add(item);

            int previous = product.getStock();
            product.setStock(previous - qtyInt);
            productRepository.save(product);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(product);
            mv.setUser(actor);
            mv.setType(MovementType.SALE);
            mv.setQuantity(qtyInt);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous - qtyInt);
            mv.setReason("Venta");
            movementRepository.save(mv);

            subtotal = subtotal.add(itemSubtotal);
        }

        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
        BigDecimal tax = req.getTax() != null ? req.getTax() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(tax);

        // En efectivo, el cajero debe capturar con cuánto pagó el cliente para poder
        // calcular el cambio a entregar; en tarjeta/transferencia no aplica y se ignora
        // cualquier valor que llegue en el request. El cambio siempre lo calcula el
        // servidor (nunca se confía en un "changeGiven" enviado por el cliente).
        BigDecimal amountReceived = null;
        BigDecimal changeGiven = null;
        if (req.getPaymentMethod() == PaymentMethod.CASH) {
            if (req.getAmountReceived() == null) {
                throw new IllegalStateException("Debes indicar con cuánto pagó el cliente para cobrar en efectivo");
            }
            if (req.getAmountReceived().compareTo(total) < 0) {
                throw new IllegalStateException("El monto recibido es menor al total de la venta");
            }
            amountReceived = req.getAmountReceived();
            changeGiven = amountReceived.subtract(total);
        }

        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTax(tax);
        sale.setTotal(total);
        sale.setAmountReceived(amountReceived);
        sale.setChangeGiven(changeGiven);
        sale.setItems(items);

        Sale saved = saleRepository.save(sale);

        if (req.getCustomerEmail() != null && !req.getCustomerEmail().isBlank()) {
            emailService.sendTicketEmail(saved, req.getCustomerEmail());
        }

        return saved;
    }

    /**
     * Cancela una venta ya registrada: revierte el stock de cada producto vendido y
     * marca la venta como cancelada.
     *
     * <p>Por cada línea de la venta se devuelve la cantidad al stock del producto y se
     * registra un {@link InventoryMovement} de tipo {@code IN} explicando que es una
     * reversión ("Cancelación de venta #id"). La venta nunca se borra: se marca
     * {@code CANCELLED} y se conserva quién y cuándo la canceló ({@code cancelledBy}/
     * {@code cancelledAt}), preservando el historial completo para auditoría y para los
     * reportes de corte de caja (que cuentan las canceladas aparte).</p>
     *
     * @param id id de la venta a cancelar
     * @param actor usuario que cancela; debe tener acceso a la tienda de la venta; queda
     *              registrado como {@code cancelledBy}
     * @return la venta ya marcada como cancelada
     * @throws IllegalStateException si la venta ya estaba cancelada
     * @throws IllegalArgumentException si la venta no existe o no pertenece a la tienda del actor
     */
    @Transactional
    public Sale cancel(Long id, User actor) {
        Sale sale = findById(id, actor);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalStateException("La venta ya está cancelada");
        }
        for (SaleItem item : sale.getItems()) {
            Product p = item.getProduct();
            int qty = item.getQuantity().intValue();
            int previous = p.getStock();
            p.setStock(previous + qty);
            productRepository.save(p);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(p);
            mv.setUser(sale.getUser());
            mv.setType(MovementType.IN);
            mv.setQuantity(qty);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous + qty);
            mv.setReason("Cancelación de venta #" + id);
            movementRepository.save(mv);
        }
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledBy(actor);
        sale.setCancelledAt(LocalDateTime.now());
        return saleRepository.save(sale);
    }
}
