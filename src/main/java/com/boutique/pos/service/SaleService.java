package com.boutique.pos.service;

import com.boutique.pos.dto.ApartadoItemBrief;
import com.boutique.pos.dto.ItemOutcomeRequest;
import com.boutique.pos.dto.SaleCancelRequest;
import com.boutique.pos.dto.SaleItemRequest;
import com.boutique.pos.dto.SaleRefundRequest;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.*;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private final EntityLocks entityLocks;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SaleItemRepository saleItemRepository;
    private final CashCutRepository cashCutRepository;
    private final InventoryMovementRepository movementRepository;
    private final EmailService emailService;
    private final TenantScope tenantScope;
    private final TiendaRepository tiendaRepository;
    private final OfferService offerService;
    private final ApartadoRepository apartadoRepository;
    private final CashMovementService cashMovementService;
    private final CashBalanceCalculator calculator;
    private final StockReturnService stockReturnService;
    private final TicketPdfService ticketPdfService;
    private final MailConfigService mailConfigService;

    /**
     * Calcula el próximo folio de venta de una tienda (consecutivo propio, no global) bajo
     * bloqueo pesimista sobre su fila — así dos ventas simultáneas en la misma tienda
     * siempre quedan serializadas y nunca comparten folio, igual que
     * {@code ProductRepository#findByIdForUpdate} serializa el descuento de stock.
     *
     * @param tienda tienda de la venta (nunca null al momento de llamar: ver
     *               {@link #create} y {@link #completeFromApartado})
     * @return el siguiente folio, ya reservado (el contador de la tienda queda actualizado
     *         en la misma transacción)
     */
    private Long nextSaleFolio(Tienda tienda) {
        Tienda locked = tiendaRepository.findByIdForUpdate(tienda.getId()).map(entityLocks::fresh)
                .orElseThrow(() -> new IllegalStateException("Tienda no encontrada"));
        long next = locked.getLastSaleFolio() + 1;
        locked.setLastSaleFolio(next);
        tiendaRepository.save(locked);
        return next;
    }

    /**
     * Reparte una línea de venta con oferta vigente en hasta dos {@link SaleItem} (agrega
     * ambos a {@code items}) y regresa el importe BRUTO de la línea (antes del descuento
     * manual del cajero, para {@code grossSubtotal}).
     * <p>
     * La cantidad capturada ({@code qtyInt}) son SIEMPRE piezas físicas — nunca "número de
     * promociones" — así que primero se calcula cuántas promociones completas caben ahí
     * ({@code promosPosibles}), topadas por {@link Offer#getMaxPromosPerSale()} si tiene
     * límite. Lo que no forma parte de una promoción completa (porque sobra, o porque
     * excede el límite) se cobra a precio normal, exactamente igual que si no hubiera
     * oferta — nunca queda sin cobrarse.
     * <ul>
     *   <li>Línea 1 (siempre): piezas pagadas (las de las promociones + las normales) a
     *       precio normal, con el descuento manual del cajero aplicado ahí si vino alguno
     *       — el cajero decide en el frontend si lo aplica encima de la oferta o no, este
     *       método no pregunta nada, solo aplica lo que llegó en el request.</li>
     *   <li>Línea 2 (solo si hay piezas "gratis"): a $0.00, con el nombre del producto más
     *       la razón de la oferta entre paréntesis (ej. "Playera básica (2x1)") para que el
     *       ticket deje claro cuántas se llevó de regalo — ver EscPosTicketService, que
     *       imprime {@code productName} + "qty x unitPrice" de cada línea tal cual.</li>
     * </ul>
     *
     * @param sale venta en construcción (dueña de ambas líneas)
     * @param items lista acumuladora de líneas de la venta; se le agregan 1 o 2 nuevas
     * @param product producto de esta línea
     * @param qtyInt piezas físicas que se lleva el cliente (no cambia si hay oferta o no)
     * @param unitPrice precio normal del producto
     * @param discount descuento manual del cajero para esta línea (puede ser cero)
     * @param offer oferta vigente para este producto
     * @param tienda tienda de la venta, para validar el tope de descuento manual
     * @return importe bruto de la línea completa (piezas pagadas × precio normal; las
     *         piezas gratis no suman nada, ya están a $0.00)
     */
    private BigDecimal applyOffer(Sale sale, List<SaleItem> items, Product product, int qtyInt,
                                   BigDecimal unitPrice, BigDecimal discount, Offer offer, Tienda tienda) {
        int buy = offer.getBuyQuantity();
        int pay = offer.getPayQuantity();
        int promosPosibles = qtyInt / buy;
        int promosAplicados = offer.getMaxPromosPerSale() != null
                ? Math.min(promosPosibles, offer.getMaxPromosPerSale())
                : promosPosibles;
        int piezasGratis = promosAplicados * (buy - pay);
        int piezasPagadas = qtyInt - piezasGratis;

        BigDecimal paidLineGross = unitPrice.multiply(BigDecimal.valueOf(piezasPagadas));
        validateDiscountLimit(discount, paidLineGross, product.getName(), tienda);

        SaleItem paidItem = new SaleItem();
        paidItem.setSale(sale);
        paidItem.setProduct(product);
        paidItem.setProductName(product.getName());
        paidItem.setQuantity(BigDecimal.valueOf(piezasPagadas));
        paidItem.setUnitPrice(unitPrice);
        paidItem.setDiscount(discount);
        paidItem.setSubtotal(paidLineGross.subtract(discount));
        items.add(paidItem);

        if (piezasGratis > 0) {
            SaleItem freeItem = new SaleItem();
            freeItem.setSale(sale);
            freeItem.setProduct(product);
            freeItem.setProductName(product.getName() + " (" + buy + "x" + pay + ")");
            freeItem.setQuantity(BigDecimal.valueOf(piezasGratis));
            freeItem.setUnitPrice(BigDecimal.ZERO);
            freeItem.setDiscount(BigDecimal.ZERO);
            freeItem.setSubtotal(BigDecimal.ZERO);
            items.add(freeItem);
        }

        return paidLineGross;
    }

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
                               PaymentMethod paymentMethod, SaleStatus status, Boolean refunded,
                               Pageable pageable, User actor) {
        Long scope = tenantScope.scopeId(actor);
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        Page<Sale> page = saleRepository.search(scope, effectiveFrom, effectiveTo, customerName, paymentMethod, status, refunded, pageable);
        populatePendingBalances(page.getContent());
        return page;
    }

    /**
     * Llena {@link Sale#getPendingBalance()} de cada venta {@code kind=DEPOSIT} de la
     * lista, con el saldo que le falta al apartado del que nació ({@code apartado.total -
     * apartado.depositAmount}) — en un solo query por lote (no uno por fila), igual que
     * {@code ProductService#withPrimaryImages} para las fotos de producto.
     */
    private void populatePendingBalances(List<Sale> sales) {
        List<Long> apartadoIds = sales.stream()
                .filter(s -> s.getKind() == SaleKind.DEPOSIT && s.getApartadoId() != null)
                .map(Sale::getApartadoId)
                .distinct()
                .toList();
        if (apartadoIds.isEmpty()) return;
        Map<Long, Apartado> byId = apartadoRepository.findAllById(apartadoIds).stream()
                .collect(Collectors.toMap(Apartado::getId, a -> a));
        for (Sale s : sales) {
            if (s.getKind() != SaleKind.DEPOSIT || s.getApartadoId() == null) continue;
            Apartado a = byId.get(s.getApartadoId());
            if (a != null) s.setApartadoItems(a.getItems().stream().map(ApartadoItemBrief::of).toList());
            // Solo mientras el apartado sigue ACTIVE (esperando a que el cliente venga por
            // su pedido) — una vez COMPLETED el saldo ya se cobró (ver Sale kind=BALANCE),
            // y CANCELLED/EXPIRED ya no se va a cobrar; en ambos casos mostrar un "saldo
            // pendiente" sería engañoso.
            if (a != null && a.getStatus() == ApartadoStatus.ACTIVE
                    && a.getDepositAmount() != null && a.getTotal() != null) {
                s.setPendingBalance(a.getTotal().subtract(a.getDepositAmount()));
            }
        }
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
    /** El ticket digital (PDF) de una venta, el mismo que se manda por correo. */
    @Transactional(readOnly = true)
    public byte[] ticketPdf(Long id, User actor) {
        return ticketPdfService.generate(findById(id, actor));
    }

    /** Manda por correo el ticket digital de una venta ya registrada. */
    @Transactional(readOnly = true)
    public void sendTicket(Long id, String email, User actor) {
        if (!Boolean.TRUE.equals(mailConfigService.getEntity().getEnabled())) {
            throw new IllegalStateException("El envío de correos está desactivado; actívalo en Configuración de correo");
        }
        emailService.sendTicketEmail(findById(id, actor), email.trim());
    }

    public Sale findById(Long id, User actor) {
        Sale s = findById(id);
        populatePendingBalances(List.of(s));
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

        // La tienda de la venta es la del corte de caja abierto, no la del actor
        // directamente: para un SUPER_ADMIN (sin tienda propia) es la única fuente
        // confiable de "en qué tienda está actuando ahora mismo" — el corte ya quedó
        // fijado a esa tienda al abrirse (ver CashCutService#open), así que no puede
        // cambiar a media sesión aunque el SUPER_ADMIN cambie de tienda actuante después.
        Tienda tienda = openCut.getTienda();

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(req.getCustomerName());
        sale.setCustomerEmail(req.getCustomerEmail());
        sale.setPaymentMethod(req.getPaymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes(req.getNotes());
        sale.setTienda(tienda);
        sale.setFolio(nextSaleFolio(tienda));

        List<SaleItem> items = new ArrayList<>();
        // grossSubtotal: suma de unitPrice*qty de cada línea, SIN restar ningún descuento —
        // es lo que se muestra como "Subtotal" en el ticket, precisamente para que ahí
        // "Subtotal - Descuento + Impuestos = Total" cierre a simple vista. El descuento
        // por línea sí se resta en item.subtotal (lo que se imprime junto a cada producto),
        // pero no aquí, para no restarlo dos veces.
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountTotal = BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        for (SaleItemRequest ir : req.getItems()) {
            // Línea "por concepto" (ver SaleItemRequest): nada de catálogo/inventario de por
            // medio, así que se resuelve aparte y no entra al flujo normal de stock/oferta
            // de abajo — se puede mezclar libremente con líneas de catálogo en la misma venta.
            if (ir.getProductId() == null) {
                String conceptName = ir.getConceptName() != null ? ir.getConceptName().trim() : "";
                if (conceptName.isEmpty()) {
                    throw new IllegalArgumentException("Captura el nombre del producto para la línea por concepto");
                }
                BigDecimal conceptUnitPrice = ir.getUnitPrice();
                if (conceptUnitPrice == null || conceptUnitPrice.signum() <= 0) {
                    throw new IllegalArgumentException("Captura un precio mayor a 0 para \"" + conceptName + "\"");
                }
                BigDecimal conceptQty = ir.getQuantity();
                BigDecimal conceptDiscount = ir.getDiscount() != null ? ir.getDiscount() : BigDecimal.ZERO;
                BigDecimal conceptLineGross = conceptUnitPrice.multiply(conceptQty).setScale(2, RoundingMode.HALF_UP);
                // sale_items.subtotal es NUMERIC(12,2): cantidad × precio no debe pasar de ahí.
                if (conceptLineGross.compareTo(new BigDecimal("9999999999.99")) > 0) {
                    throw new IllegalArgumentException("El importe de \"" + conceptName + "\" es excesivamente grande — el máximo permitido es $9,999,999,999.99");
                }
                validateDiscountLimit(conceptDiscount, conceptLineGross, conceptName, tienda);

                SaleItem item = new SaleItem();
                item.setSale(sale);
                item.setProduct(null);
                item.setProductName(conceptName);
                item.setQuantity(conceptQty);
                item.setUnitPrice(conceptUnitPrice);
                item.setDiscount(conceptDiscount);
                item.setSubtotal(conceptLineGross.subtract(conceptDiscount));
                items.add(item);

                itemDiscountTotal = itemDiscountTotal.add(conceptDiscount);
                grossSubtotal = grossSubtotal.add(conceptLineGross);
                continue;
            }

            // findByIdForUpdate (no findById): toma el lock ANTES de leer el stock, para que
            // dos ventas concurrentes del mismo producto queden serializadas en vez de
            // ambas leer el mismo stock "disponible" y una perder su resta (condición de
            // carrera — hallazgo "Media" de la auditoría de código).
            Product product = productService.findByIdForUpdate(ir.getProductId(), actor);

            if (!product.getIsActive()) throw new IllegalStateException("Producto inactivo: " + product.getName());

            BigDecimal qty = ir.getQuantity();
            int qtyInt = qty.intValue();
            // "Por pedido" (ver Product#getIsMadeToOrder): no hay inventario real que
            // validar ni descontar — el producto se fabrica/consigue hasta que se pide.
            boolean madeToOrder = Boolean.TRUE.equals(product.getIsMadeToOrder());
            if (!madeToOrder && product.getStock() < qtyInt) {
                throw new IllegalStateException("Stock insuficiente para " + product.getName()
                        + " (disponible: " + product.getStock() + ")");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal discount = ir.getDiscount() != null ? ir.getDiscount() : BigDecimal.ZERO;

            // Oferta vigente para este producto ("compra X paga Y" — ver Offer#isCurrentlyActive):
            // si aplica, la línea se reparte en dos SaleItem (piezas pagadas a precio normal +
            // piezas "gratis" de la promoción a $0.00, ver applyOffer) en vez de una sola. El
            // stock SIEMPRE baja por qtyInt completo más abajo, sin importar si hubo oferta —
            // es lo que el cliente se lleva, no lo que paga (ver Offer, javadoc de la clase).
            Optional<Offer> activeOffer = Boolean.TRUE.equals(ir.getSkipOffer())
                    ? Optional.empty()
                    : offerService.findActiveOfferForProduct(product, now);
            BigDecimal lineGross;
            if (activeOffer.isPresent()) {
                lineGross = applyOffer(sale, items, product, qtyInt, unitPrice, discount, activeOffer.get(), tienda);
            } else {
                validateDiscountLimit(discount, unitPrice.multiply(qty), product.getName(), tienda);
                lineGross = unitPrice.multiply(qty);
                SaleItem item = new SaleItem();
                item.setSale(sale);
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setQuantity(qty);
                item.setUnitPrice(unitPrice);
                item.setDiscount(discount);
                item.setSubtotal(lineGross.subtract(discount));
                items.add(item);
            }
            itemDiscountTotal = itemDiscountTotal.add(discount);

            if (!madeToOrder) {
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
                mv.setSource(MovementSource.SALE);
                movementRepository.save(mv);
            }

            grossSubtotal = grossSubtotal.add(lineGross);
        }

        // El descuento total de la venta es la suma de los descuentos por línea más
        // cualquier descuento global adicional que venga en el request (ver el Javadoc de
        // SaleRequest.discount) — antes de este fix, el descuento por línea nunca se
        // reflejaba aquí, aunque sí se restaba correctamente en cada item.subtotal.
        BigDecimal globalDiscount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
        // El descuento global pasa por las mismas reglas que el de cada línea (no negativo,
        // no mayor a lo que queda por cobrar, dentro del límite de la tienda).
        BigDecimal remaining = grossSubtotal.subtract(itemDiscountTotal);
        if (globalDiscount.compareTo(remaining) > 0) {
            throw new IllegalStateException("El descuento general no puede ser mayor al total de la venta ("
                    + MONEY_FMT.format(remaining) + ")");
        }
        validateDiscountLimit(globalDiscount, remaining, "la venta", tienda);
        BigDecimal discount = itemDiscountTotal.add(globalDiscount);
        BigDecimal tax = req.getTax() != null ? req.getTax() : BigDecimal.ZERO;
        if (tax.signum() < 0) {
            throw new IllegalStateException("El impuesto no puede ser negativo");
        }
        BigDecimal total = grossSubtotal.subtract(discount).add(tax);
        // Última barrera: una venta en negativo restaría al efectivo esperado del corte.
        if (total.signum() < 0) {
            throw new IllegalStateException("El total de la venta no puede ser negativo");
        }

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

        sale.setSubtotal(grossSubtotal);
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
        return cancel(id, null, actor);
    }

    /**
     * Igual que {@link #cancel(Long, User)}, eligiendo por artículo qué pasó con el producto
     * ({@code req.items}: regresa / merma / no regresó; los que no vengan regresan). Si alguno no
     * regresa al stock vendible, {@code req.reason} es obligatorio.
     */
    @Transactional
    public Sale cancel(Long id, SaleCancelRequest req, User actor) {
        // Bloqueada y releída: dos cancelaciones simultáneas no deben pasar ambas.
        Sale sale = entityLocks.fresh(findById(id, actor));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalStateException("La venta ya está cancelada");
        }
        if (sale.getRefundedAt() != null) {
            throw new IllegalStateException("Esta venta ya se devolvió");
        }
        // Cancelar cambia la venta en SU corte: si ese corte ya se cerró (y se contó), eso
        // reescribiría un turno cerrado y el dinero saldría de otra caja sin registro — en
        // ese caso es una devolución (ver refund).
        if (sale.getCashCut() != null && sale.getCashCut().getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("Esta venta es de un turno ya cerrado: regístrala como devolución");
        }
        // Cancelar saca el dinero de la caja del turno de la venta: un cajero solo puede hacerlo
        // en su propio turno; en el de otro, solo un administrador o supervisor.
        if (sale.getCashCut() != null && sale.getCashCut().getUser() != null
                && !sale.getCashCut().getUser().getId().equals(actor.getId()) && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Esta venta es del turno de " + sale.getCashCut().getUser().getName()
                    + ": solo esa persona, un administrador o un supervisor pueden cancelarla");
        }
        // El anticipo está ligado a su apartado: cancelarlo aquí dejaría el apartado activo
        // como si estuviera pagado. Se cancela el apartado y ahí se decide qué pasa con él.
        if (sale.getKind() == SaleKind.DEPOSIT) {
            throw new IllegalStateException("Esta venta es " + (Boolean.TRUE.equals(sale.getInstallment()) ? "un abono" : "el anticipo")
                    + " del apartado #" + sale.getApartadoFolio() + ": no es reembolsable; se gestiona desde Apartados");
        }
        ensureCutCanReturn(sale, actor);
        // La venta final de un apartado: cancelarla deshace el cobro, no el apartado. Vuelve a
        // quedar activo (el cliente lo sigue teniendo reservado y se puede volver a cobrar o
        // cancelar desde Apartados) y el stock NO regresa: sigue apartado para él.
        Apartado apartado = apartadoRepository.findBySaleId(sale.getId()).map(entityLocks::fresh).orElse(null);
        if (apartado != null && apartado.getStatus() == ApartadoStatus.COMPLETED) {
            apartado.setStatus(ApartadoStatus.ACTIVE);
            apartado.setPendingDelivery(false);
            apartado.setCompletedBy(null);
            apartado.setSaleId(null);
            apartado.setSaleFolio(null);
            // Si su vigencia ya pasó mientras estuvo completado, se le da otra igual desde hoy:
            // si no, el vencimiento automático lo vencería en minutos.
            if (apartado.getExpiresAt() != null && apartado.getConfirmedAt() != null
                    && apartado.getExpiresAt().isBefore(LocalDateTime.now())) {
                java.time.Duration vigencia = java.time.Duration.between(apartado.getConfirmedAt(), apartado.getExpiresAt());
                apartado.setExpiresAt(LocalDateTime.now().plus(vigencia.isNegative() || vigencia.isZero()
                        ? java.time.Duration.ofDays(1) : vigencia));
            }
            apartadoRepository.save(apartado);
            return markCancelled(sale, actor);
        }
        String reason = req != null && req.getReason() != null && !req.getReason().isBlank() ? req.getReason().trim() : null;
        Map<Long, ItemReturnOutcome> outcomes = stockReturnService.resolve(
                req != null ? req.getItems() : null, stockItemIds(sale), reason, "reason");
        sale.setCancelReason(reason);
        return cancelInternal(sale, actor, outcomes, reason);
    }

    /** Ids de las líneas con inventario que regresar (con producto y que no sea por pedido). */
    private static List<Long> stockItemIds(Sale sale) {
        return sale.getItems().stream()
                .filter(i -> i.getProduct() != null && !Boolean.TRUE.equals(i.getProduct().getIsMadeToOrder()))
                .map(SaleItem::getId)
                .toList();
    }

    /**
     * Cancelar regresa el dinero desde la caja del turno de la venta: ese turno debe seguir
     * abierto (se bloquea su corte, así no se cierra a la mitad) y, en efectivo, tener ese
     * efectivo; si no, el esperado quedaría en negativo (p. ej. ya se retiró).
     */
    private void ensureCutCanReturn(Sale sale, User actor) {
        if (sale.getCashCut() == null) return;
        CashCut cut = cashCutRepository.findByIdForUpdate(sale.getCashCut().getId()).map(entityLocks::fresh)
                .orElseThrow(() -> new IllegalStateException("No se encontró el corte de la venta"));
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("Esta venta es de un turno ya cerrado: regístrala como devolución");
        }
        if (sale.getPaymentMethod() != PaymentMethod.CASH || sale.getTotal() == null || sale.getTotal().signum() <= 0) return;
        BigDecimal available = calculator.expectedCash(cut);
        if (sale.getTotal().compareTo(available) > 0) {
            // Con conteo ciego el mensaje no dice cuánto hay: revelaría el efectivo esperado.
            boolean blind = cut.getTienda() != null && Boolean.TRUE.equals(cut.getTienda().getBlindCashCount())
                    && !tenantScope.isAdminLevel(actor);
            throw new IllegalStateException(blind
                    ? "No hay suficiente efectivo en la caja de ese turno para regresar esta venta."
                    : "Solo hay " + MONEY_FMT.format(available.max(BigDecimal.ZERO)) + " en la caja de ese turno: no alcanza para regresar los "
                            + MONEY_FMT.format(sale.getTotal()) + " de esta venta.");
        }
    }

    private Sale cancelInternal(Sale sale, User actor, Map<Long, ItemReturnOutcome> outcomes, String reason) {
        String what = "cancelación de venta folio " + sale.getFolio();
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() == null) continue; // el producto pudo eliminarse desde entonces; no hay a quién devolverle stock
            // findByIdForUpdate: mismo motivo que en create() — dos cancelaciones (o una
            // cancelación y una venta nueva) sobre el mismo producto no deben pisarse.
            Product p = productService.findByIdForUpdate(item.getProduct().getId());
            if (Boolean.TRUE.equals(p.getIsMadeToOrder())) continue; // nunca se descontó stock real, nada que devolver
            ItemReturnOutcome outcome = outcomes.getOrDefault(item.getId(), ItemReturnOutcome.RESTOCK);
            item.setReturnOutcome(outcome);
            stockReturnService.apply(p, item.getQuantity().intValue(), outcome, actor != null ? actor : sale.getUser(),
                    MovementSource.SALE_CANCEL, "Cancelación de venta folio " + sale.getFolio(), what, reason);
        }
        return markCancelled(sale, actor);
    }

    private Sale markCancelled(Sale sale, User actor) {
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledBy(actor);
        sale.setCancelledAt(LocalDateTime.now());
        return saleRepository.save(sale);
    }

    /**
     * Devuelve una venta de un turno ya cerrado (para una del turno abierto se usa
     * {@link #cancel}). A diferencia de cancelar:
     * <ul>
     *   <li>La venta sigue {@code COMPLETED} y contando en su corte: ese turno ya se cerró y
     *       se contó, no se reescribe. Solo se marca como devuelta (quién, cuándo, motivo).</li>
     *   <li>En efectivo, el dinero sale de la caja de quien devuelve: se registra una salida
     *       {@link CashMovementType#REFUND} en SU corte abierto (obligatorio), limitada al
     *       efectivo que tenga. Con tarjeta/transferencia no sale nada de la caja (el reembolso
     *       va por el mismo medio); solo queda registrada.</li>
     *   <li>Por artículo se decide si el cliente lo entregó: los que sí regresan al inventario;
     *       los que no, dejan un movimiento {@link MovementSource#SALE_REFUND_NO_PRODUCT} (sin
     *       cambiar el stock) para que quede a la vista.</li>
     * </ul>
     * Siempre es la venta completa (no hay devoluciones parciales). Las ventas de apartados y
     * anticipos no se devuelven aquí: están ligadas a su apartado.
     *
     * @throws IllegalStateException si la venta ya está cancelada o devuelta, es de un turno
     *         abierto, es de un apartado/anticipo, o es en efectivo y el actor no tiene un
     *         corte abierto con suficiente efectivo
     */
    @Transactional
    public Sale refund(Long id, SaleRefundRequest req, User actor) {
        // Bloqueada y releída: dos devoluciones simultáneas sacarían el efectivo dos veces.
        Sale sale = entityLocks.fresh(findById(id, actor));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalStateException("La venta está cancelada, no hay nada que devolver");
        }
        if (sale.getRefundedAt() != null) {
            throw new IllegalStateException("Esta venta ya se devolvió");
        }
        if (sale.getKind() != SaleKind.REGULAR || apartadoRepository.findBySaleId(sale.getId()).isPresent()) {
            throw new IllegalStateException("Las ventas de apartados y anticipos no se devuelven desde aquí: devuelve el apartado desde Apartados");
        }
        if (sale.getCashCut() != null && sale.getCashCut().getStatus() == CashCutStatus.OPEN) {
            throw new IllegalStateException("Esta venta es de un turno que sigue abierto: cancélala en lugar de devolverla");
        }
        // Solo ventas de antes de validar descuentos/impuestos pueden tener total negativo.
        if (sale.getTotal() != null && sale.getTotal().signum() < 0) {
            throw new IllegalStateException("Esta venta tiene un total negativo: no se puede devolver");
        }
        ensureCanRefund(sale.getTienda(), sale.getTotal(), sale.getCreatedAt(), actor);
        String reason = req.getReason().trim();
        List<Long> eligible = stockItemIds(sale);
        // Compatibilidad: sin `items`, los de returnedItemIds regresan y los demás no regresaron.
        List<ItemOutcomeRequest> chosen = req.getItems();
        if (chosen == null) {
            java.util.Set<Long> returnedIds = req.getReturnedItemIds() != null
                    ? new java.util.HashSet<>(req.getReturnedItemIds()) : java.util.Set.of();
            for (Long itemId : returnedIds) {
                if (sale.getItems().stream().noneMatch(i -> itemId.equals(i.getId()))) {
                    throw new IllegalArgumentException("Ese artículo no pertenece a la venta");
                }
            }
            chosen = eligible.stream().map(itemId -> new ItemOutcomeRequest(itemId,
                    returnedIds.contains(itemId) ? ItemReturnOutcome.RESTOCK : ItemReturnOutcome.NOT_RETURNED)).toList();
        }
        Map<Long, ItemReturnOutcome> outcomes = stockReturnService.resolve(chosen, eligible, reason, "reason");

        // El efectivo primero: si la caja no alcanza, no se toca nada del inventario.
        Long refundCutId = null;
        if (sale.getPaymentMethod() == PaymentMethod.CASH && sale.getTotal().signum() > 0) {
            CashCut cut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                    .orElseThrow(() -> new IllegalStateException(
                            "Abre tu corte de caja: el efectivo de la devolución sale de tu caja"));
            cashMovementService.registerRefund(cut.getId(), sale.getTotal(),
                    refundConcept(sale), actor, sale.getId());
            refundCutId = cut.getId();
        }

        String folio = "venta folio " + sale.getFolio();
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() == null) continue; // por concepto o producto eliminado: no hay inventario
            Product p = productService.findByIdForUpdate(item.getProduct().getId());
            if (Boolean.TRUE.equals(p.getIsMadeToOrder())) continue; // nunca se descontó stock real
            ItemReturnOutcome outcome = outcomes.getOrDefault(item.getId(), ItemReturnOutcome.RESTOCK);
            item.setReturnOutcome(outcome);
            item.setReturnedToStock(outcome == ItemReturnOutcome.RESTOCK);
            stockReturnService.apply(p, item.getQuantity().intValue(), outcome, actor,
                    MovementSource.SALE_REFUND, "Devolución de " + folio, "devolución de " + folio, reason);
        }

        sale.setRefundedBy(actor);
        sale.setRefundedAt(LocalDateTime.now());
        sale.setRefundReason(reason);
        sale.setRefundCashCutId(refundCutId);
        return saleRepository.save(sale);
    }

    /**
     * Devuelve el dinero de UNA venta de un apartado que el cliente regresó después de
     * recogerlo (lo llama {@code ApartadoService#refund}). Siempre como devolución, aunque su
     * turno siga abierto: la venta sí ocurrió (el cliente se llevó el producto), así que no
     * se cancela; en efectivo el dinero sale de la caja de quien devuelve. El inventario lo
     * resuelve el apartado. Si la venta ya estaba cancelada o devuelta, no hace nada.
     */
    @Transactional
    public void refundForApartado(Long saleId, String concept, String reason, User actor) {
        refundForApartado(saleId, null, concept, reason, actor);
    }

    /**
     * Igual, pero devolviendo solo {@code amount} (la tienda retiene el resto, ver {@code
     * ApartadoService#retention}); null = todo.
     */
    @Transactional
    public void refundForApartado(Long saleId, BigDecimal amount, String concept, String reason, User actor) {
        Sale sale = saleRepository.findById(saleId).map(entityLocks::fresh)
                .orElseThrow(() -> new IllegalStateException("No se encontró la venta del apartado"));
        if (sale.getStatus() == SaleStatus.CANCELLED || sale.getRefundedAt() != null) return;
        BigDecimal refund = amount == null ? sale.getTotal() : amount;
        Long refundCutId = null;
        if (sale.getPaymentMethod() == PaymentMethod.CASH && refund.signum() > 0) {
            CashCut cut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                    .orElseThrow(() -> new IllegalStateException(
                            "Abre tu corte de caja: el efectivo de la devolución sale de tu caja"));
            cashMovementService.registerRefund(cut.getId(), refund, concept, actor, sale.getId());
            refundCutId = cut.getId();
        }
        if (refund.compareTo(sale.getTotal()) < 0) sale.setRefundAmount(refund);
        sale.setRefundedBy(actor);
        sale.setRefundedAt(LocalDateTime.now());
        String withReason = concept + (reason != null && !reason.isBlank() ? ": " + reason.trim() : "");
        sale.setRefundReason(withReason.length() > 300 ? withReason.substring(0, 300) : withReason);
        sale.setRefundCashCutId(refundCutId);
        saleRepository.save(sale);
    }

    /** Marca en las líneas de una venta qué pasó con cada producto (por producto). */
    @Transactional
    public void markItemOutcomes(Long saleId, Map<Long, ItemReturnOutcome> byProductId) {
        saleRepository.findById(saleId).ifPresent(sale -> {
            for (SaleItem item : sale.getItems()) {
                if (item.getProduct() == null) continue;
                ItemReturnOutcome o = byProductId.get(item.getProduct().getId());
                if (o == null) continue;
                item.setReturnOutcome(o);
                item.setReturnedToStock(o == ItemReturnOutcome.RESTOCK);
            }
            saleRepository.save(sale);
        });
    }

    /**
     * Le regresa al cliente el anticipo de un apartado que se está cancelando (lo llama
     * {@code ApartadoService#cancel} con "devolver anticipo"). El anticipo no tiene productos
     * (el stock lo regresa el apartado), así que solo se resuelve el dinero:
     * <ul>
     *   <li>Si quien cancela tiene su propio corte abierto y no es el del anticipo, el dinero
     *       sale de su caja como devolución ({@link CashMovementType#REFUND} si es efectivo).</li>
     *   <li>Si no, y el turno del anticipo sigue abierto, se cancela la venta en ese turno (el
     *       dinero sale de la caja que lo recibió; queda en sus ventas canceladas).</li>
     *   <li>Si el turno del anticipo ya cerró y quien cancela no tiene corte abierto: en
     *       efectivo no hay de dónde sacarlo; con tarjeta/transferencia solo se registra.</li>
     * </ul>
     * Si la venta ya estaba cancelada o devuelta, no hace nada.
     */
    @Transactional
    public void refundDeposit(Long saleId, Long apartadoFolio, String reason, User actor) {
        refundDeposit(saleId, null, apartadoFolio, reason, actor);
    }

    /**
     * Igual, pero devolviendo solo {@code amount} (la tienda retiene el resto); null = todo.
     * Una devolución parcial nunca cancela la venta (sí se cobró, aunque sea en parte): sale
     * de la caja de quien cancela, que necesita su corte abierto si es en efectivo.
     */
    @Transactional
    public void refundDeposit(Long saleId, BigDecimal amount, Long apartadoFolio, String reason, User actor) {
        Sale sale = saleRepository.findById(saleId).map(entityLocks::fresh)
                .orElseThrow(() -> new IllegalStateException("No se encontró la venta del anticipo"));
        if (sale.getStatus() == SaleStatus.CANCELLED || sale.getRefundedAt() != null) return;
        BigDecimal refund = amount == null ? sale.getTotal() : amount;
        boolean partial = refund.compareTo(sale.getTotal()) < 0;

        CashCut actorCut = actor == null ? null
                : cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN).orElse(null);
        CashCut saleCut = sale.getCashCut();
        boolean saleTurnOpen = saleCut != null && saleCut.getStatus() == CashCutStatus.OPEN;
        boolean ownTurn = actorCut != null && saleCut != null && actorCut.getId().equals(saleCut.getId());

        if (!partial && saleTurnOpen && (ownTurn || actorCut == null)) {
            ensureCutCanReturn(sale, actor);
            cancelInternal(sale, actor, Map.of(), null);
            return;
        }
        String text = "Devolución " + (Boolean.TRUE.equals(sale.getInstallment()) ? "abono" : "anticipo")
                + " apartado #" + apartadoFolio + " (venta folio " + sale.getFolio() + ")";
        String withReason = text + (reason != null && !reason.isBlank() ? ": " + reason.trim() : "");
        Long refundCutId = null;
        if (sale.getPaymentMethod() == PaymentMethod.CASH && refund.signum() > 0) {
            if (actorCut == null) {
                throw new IllegalStateException("Abre tu corte de caja: el efectivo del anticipo sale de tu caja");
            }
            cashMovementService.registerRefund(actorCut.getId(), refund, text, actor, sale.getId());
            refundCutId = actorCut.getId();
        }
        if (partial) sale.setRefundAmount(refund);
        sale.setRefundedBy(actor);
        sale.setRefundedAt(LocalDateTime.now());
        sale.setRefundReason(withReason.length() > 300 ? withReason.substring(0, 300) : withReason);
        sale.setRefundCashCutId(refundCutId);
        saleRepository.save(sale);
    }

    /**
     * Un cajero solo puede devolver hasta el monto y la antigüedad que la tienda configuró en
     * "Datos de la tienda" ({@link Tienda#getCashierRefundMaxAmount()}/{@code MaxDays}); por
     * encima, solo un administrador o supervisor. Sin configurar = sin límite.
     *
     * @param amount lo que se devuelve
     * @param soldAt cuándo se hizo la venta
     */
    public void ensureCanRefund(Tienda tienda, BigDecimal amount, LocalDateTime soldAt, User actor) {
        if (tienda == null || tenantScope.isAdminLevel(actor)) return;
        if (tienda.getCashierRefundMaxAmount() != null && amount != null
                && amount.compareTo(tienda.getCashierRefundMaxAmount()) > 0) {
            throw new IllegalStateException("Las devoluciones de más de " + MONEY_FMT.format(tienda.getCashierRefundMaxAmount())
                    + " las registra un administrador o supervisor");
        }
        if (tienda.getCashierRefundMaxDays() != null && soldAt != null
                && soldAt.toLocalDate().isBefore(LocalDate.now().minusDays(tienda.getCashierRefundMaxDays()))) {
            throw new IllegalStateException("Esta venta tiene más de " + tienda.getCashierRefundMaxDays()
                    + " días: la devolución la registra un administrador o supervisor");
        }
    }

    // Concepto del movimiento de caja: solo "Devolución venta folio 12". El motivo queda en la
    // venta (refundReason) y qué pasó con cada producto se ve en la subtabla del corte.
    private static String refundConcept(Sale sale) {
        return "Devolución venta folio " + sale.getFolio();
    }

    /**
     * Genera la {@link Sale} real de un {@link Apartado} {@code ACTIVE} que el cliente vino
     * a recoger y pagar — llamado por {@code ApartadoService#complete}, nunca directamente
     * desde un controller.
     * <p>
     * A diferencia de {@link #create}, NO valida ni descuenta stock ni registra {@link
     * InventoryMovement} por producto: el stock del apartado ya se descontó al
     * CONFIRMARLO ({@code ApartadoService#confirm}), así que volver a descontarlo aquí
     * sería descontarlo dos veces. Los precios/cantidades/descuentos de cada línea ya
     * quedaron fijos desde el apartado (el descuento, si lo hubo, ya se validó contra el
     * límite de apartados al confirmar — no el de venta física), así que aquí solo se
     * copian a {@link SaleItem} tal cual.
     * <p>
     * Igual que en {@link #create}, en efectivo es obligatorio {@code amountReceived} (al
     * menos el total) y el cambio siempre lo calcula el servidor.
     *
     * @param apartado apartado {@code ACTIVE} que se está completando, con sus {@link
     *                 com.boutique.pos.model.ApartadoItem} ya cargados
     * @param paymentMethod forma de pago con la que el cliente liquida al recogerlo
     * @param amountReceived con cuánto pagó el cliente; obligatorio si {@code paymentMethod == CASH}
     * @param customerEmailOverride correo capturado por el cajero al completar (opcional);
     *                 si viene con algo, se usa para esta venta en vez del correo que el
     *                 cliente haya dejado al solicitar el apartado (que puede no existir) —
     *                 no modifica el registro del apartado, solo el de la venta generada
     * @param actor cajero/admin que completa el apartado; debe tener un corte de caja
     *              propio abierto, igual que en cualquier venta; queda registrado como
     *              {@code user} de la venta
     * @return la venta ya registrada y persistida, con {@code notes} indicando de qué
     *         apartado proviene
     * @throws IllegalStateException si el actor no tiene un corte abierto, o si es en
     *         efectivo y el monto recibido falta o es menor al total
     */
    @Transactional
    public Sale completeFromApartado(Apartado apartado, PaymentMethod paymentMethod, BigDecimal amountReceived, String customerEmailOverride, User actor) {
        return completeFromApartado(apartado, paymentMethod, amountReceived, customerEmailOverride, actor, BigDecimal.ZERO);
    }

    /**
     * Igual, cuando el anticipo se cobró en este mismo pago: {@code amountReceived} es todo lo
     * que entregó el cliente (lo que dice el ticket) y {@code paidNow} la parte que ya se
     * registró como anticipo, que no cuenta para el cambio.
     */
    @Transactional
    public Sale completeFromApartado(Apartado apartado, PaymentMethod paymentMethod, BigDecimal amountReceived,
                                     String customerEmailOverride, User actor, BigDecimal paidNow) {
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de completar un apartado"));

        String effectiveEmail = (customerEmailOverride != null && !customerEmailOverride.isBlank())
                ? customerEmailOverride : apartado.getCustomerEmail();

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(apartado.getCustomerName());
        sale.setCustomerEmail(effectiveEmail);
        sale.setPaymentMethod(paymentMethod);
        sale.setStatus(SaleStatus.COMPLETED);
        boolean isDeposit = apartado.getOrigin() == ApartadoOrigin.DEPOSIT
                && apartado.getDepositAmount() != null && apartado.getDepositAmount().signum() > 0;
        sale.setNotes(isDeposit
                ? "Saldo del apartado #" + apartado.getFolio() + " — ya se había pagado "
                        + MONEY_FMT.format(apartado.getDepositAmount()) + " a cuenta"
                : "Generada desde el apartado #" + apartado.getFolio());
        sale.setTienda(apartado.getTienda());
        sale.setFolio(nextSaleFolio(apartado.getTienda()));
        sale.setKind(isDeposit ? SaleKind.BALANCE : SaleKind.REGULAR);
        // Ligada a su apartado también sin anticipo, para mostrarlo en Ventas.
        sale.setApartadoId(apartado.getId());
        sale.setApartadoFolio(apartado.getFolio());

        List<SaleItem> items = new ArrayList<>();
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountTotal = BigDecimal.ZERO;

        for (ApartadoItem ai : apartado.getItems()) {
            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(ai.getProduct());
            item.setProductName(ai.getProductName());
            item.setQuantity(ai.getQuantity());
            item.setUnitPrice(ai.getUnitPrice());
            item.setDiscount(ai.getDiscount());
            item.setSubtotal(ai.getSubtotal());
            items.add(item);

            grossSubtotal = grossSubtotal.add(ai.getUnitPrice().multiply(ai.getQuantity()));
            itemDiscountTotal = itemDiscountTotal.add(ai.getDiscount());
        }

        // El anticipo ya pagado va en su propio renglón, no como descuento:
        // Total = Subtotal − Descuento − Anticipo.
        BigDecimal depositApplied = isDeposit ? apartado.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal total = grossSubtotal.subtract(itemDiscountTotal).subtract(depositApplied);
        if (isDeposit) {
            sale.setDepositApplied(depositApplied);
            boolean withInstallments = saleRepository.findApartadoPayments(apartado.getId(), List.of(-1L)).stream()
                    .anyMatch(s -> Boolean.TRUE.equals(s.getInstallment()));
            sale.setDepositSaleFolio(withInstallments ? null : apartado.getDepositSaleFolio());
        }

        BigDecimal changeGiven = null;
        if (paymentMethod == PaymentMethod.CASH) {
            if (amountReceived == null) {
                throw new IllegalStateException("Debes indicar con cuánto pagó el cliente para cobrar en efectivo");
            }
            if (amountReceived.compareTo(total.add(paidNow)) < 0) {
                throw new IllegalStateException("El monto recibido es menor al saldo pendiente del apartado");
            }
            changeGiven = amountReceived.subtract(total).subtract(paidNow);
        } else {
            amountReceived = null;
        }

        sale.setSubtotal(grossSubtotal);
        sale.setDiscount(itemDiscountTotal);
        sale.setTax(BigDecimal.ZERO);
        sale.setTotal(total);
        sale.setAmountReceived(amountReceived);
        sale.setChangeGiven(changeGiven);
        sale.setItems(items);

        Sale saved = saleRepository.save(sale);

        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            emailService.sendTicketEmail(saved, effectiveEmail);
        }

        return saved;
    }

    /**
     * Registra el anticipo que un cliente deja al encargar un producto, como una
     * {@link Sale} {@code kind=DEPOSIT} más — llamado únicamente por {@code
     * ApartadoService#createWithDeposit}, nunca directamente desde un controller.
     * <p>
     * No tiene líneas de producto reales (el anticipo no es "una pieza vendida"): se
     * representa con un único {@link SaleItem} informativo (sin {@code product}, solo
     * {@code productName}) por el monto completo del anticipo, para que el ticket y el
     * historial dejen clarísimo de qué se trata. Cuenta en el corte de caja de quien lo
     * recibe, igual que cualquier otra venta — es dinero real que entró en ese momento.
     *
     * @param apartado apartado {@code DEPOSIT} recién creado (todavía sin guardar su
     *                 {@code depositSaleId}; eso lo hace el llamador con el resultado)
     * @param depositAmount cuánto dejó de anticipo
     * @param paymentMethod forma de pago del anticipo
     * @param amountReceived con cuánto pagó el cliente; obligatorio si es efectivo
     * @param actor cajero/admin que lo recibe; debe tener un corte de caja propio abierto;
     *              queda registrado como {@code user} de la venta
     * @return la venta de anticipo ya registrada y persistida
     * @throws IllegalStateException si el actor no tiene un corte abierto, o si es en
     *         efectivo y el monto recibido falta o es menor al anticipo
     */
    @Transactional
    public Sale createDeposit(Apartado apartado, BigDecimal depositAmount, PaymentMethod paymentMethod,
                               BigDecimal amountReceived, User actor) {
        return createDeposit(apartado, depositAmount, paymentMethod, amountReceived, actor, false);
    }

    /**
     * Igual que {@link #createDeposit(Apartado, BigDecimal, PaymentMethod, BigDecimal, User)};
     * con {@code installment = true} es un abono posterior al anticipo (misma venta
     * {@code DEPOSIT}, marcada {@link Sale#getInstallment()}).
     */
    @Transactional
    public Sale createDeposit(Apartado apartado, BigDecimal depositAmount, PaymentMethod paymentMethod,
                               BigDecimal amountReceived, User actor, boolean installment) {
        String what = installment ? "abono" : "anticipo";
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de recibir un " + what));

        BigDecimal changeGiven = null;
        if (paymentMethod == PaymentMethod.CASH) {
            if (amountReceived == null) {
                throw new FieldConflictException("amountReceived", "Debes indicar con cuánto pagó el cliente para cobrar en efectivo");
            }
            if (amountReceived.compareTo(depositAmount) < 0) {
                throw new FieldConflictException("amountReceived", "El monto recibido es menor al " + what);
            }
            changeGiven = amountReceived.subtract(depositAmount);
        } else {
            amountReceived = null;
        }

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(apartado.getCustomerName());
        sale.setCustomerEmail(apartado.getCustomerEmail());
        sale.setPaymentMethod(paymentMethod);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes((installment ? "Abono al apartado #" : "Anticipo del apartado #") + apartado.getFolio());
        sale.setTienda(apartado.getTienda());
        sale.setFolio(nextSaleFolio(apartado.getTienda()));
        sale.setKind(SaleKind.DEPOSIT);
        if (installment) sale.setInstallment(true);
        // Lo pagado a cuenta tras este pago: en el anticipo depositAmount ya es este monto;
        // en un abono todavía no lo incluye (lo suma ApartadoService#addInstallment después).
        BigDecimal paidAfter = installment ? apartado.getDepositAmount().add(depositAmount) : depositAmount;
        sale.setBalanceAfter(apartado.getTotal().subtract(paidAfter));
        sale.setApartadoId(apartado.getId());
        sale.setApartadoFolio(apartado.getFolio());

        SaleItem item = new SaleItem();
        item.setSale(sale);
        // Una sola línea con la leyenda, sin concatenar los productos (ver DepositLines).
        item.setProductName(DepositLines.label(sale));
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(depositAmount);
        item.setDiscount(BigDecimal.ZERO);
        item.setSubtotal(depositAmount);

        sale.setSubtotal(depositAmount);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTax(BigDecimal.ZERO);
        sale.setTotal(depositAmount);
        sale.setAmountReceived(amountReceived);
        sale.setChangeGiven(changeGiven);
        sale.setItems(List.of(item));

        return saleRepository.save(sale);
    }

    /**
     * Verifica que el descuento de una línea no exceda ninguno de los límites que el ADMIN
     * haya fijado para la tienda en "Datos de la tienda" ({@link Tienda#getMaxDiscountAmount()}/
     * {@link Tienda#getMaxDiscountPercent()}). Ambos son opcionales e independientes: si
     * están definidos, se rechaza el descuento que exceda CUALQUIERA de los dos.
     * <p>
     * Si el ADMIN no configuró NINGUNO de los dos límites, los descuentos quedan
     * deshabilitados por completo (no hay un límite "sin restricción" implícito): hay que
     * fijar al menos uno para poder aplicar descuentos. Esto evita que una tienda recién
     * creada, donde nadie configuró nada todavía, permita descontar sin ningún tope.
     * <p>
     * El POS ya hace esta misma validación en el navegador (ver {@code lineDiscount}/
     * {@code resolveDiscountCap} en {@code POS.jsx}) para dar feedback inmediato sin ida y
     * vuelta al servidor; esta es la validación real, para que nadie pueda saltarse el
     * límite llamando a la API directamente.
     *
     * @param discount monto de descuento (en pesos) que se intenta aplicar a la línea
     * @param lineGross importe bruto de la línea ({@code unitPrice * quantity}), usado para
     *                  traducir el límite en porcentaje a un monto comparable
     * @param productName nombre del producto, solo para el mensaje de error
     * @param tienda tienda del vendedor; si es null (SUPER_ADMIN sin tienda) no aplica límite
     * @throws IllegalStateException si el descuento es negativo, mayor al importe de la
     *         línea, excede el límite en monto o en porcentaje, o si la tienda no tiene
     *         ningún límite configurado
     */
    private void validateDiscountLimit(BigDecimal discount, BigDecimal lineGross, String productName, Tienda tienda) {
        // Estas dos van ANTES de cualquier salida temprana: un descuento negativo subiría el
        // precio, y uno mayor al importe dejaría la línea en negativo — y una venta negativa
        // en efectivo baja el efectivo esperado del corte (dinero que saldría sin descuadrar).
        if (discount.signum() < 0) {
            throw new IllegalStateException("El descuento de \"" + productName + "\" no puede ser negativo");
        }
        if (discount.compareTo(lineGross) > 0) {
            throw new IllegalStateException("El descuento de \"" + productName + "\" no puede ser mayor a su importe ("
                    + MONEY_FMT.format(lineGross) + ")");
        }
        if (discount.signum() == 0 || tienda == null) return;

        if (tienda.getMaxDiscountAmount() == null && tienda.getMaxDiscountPercent() == null) {
            throw new IllegalStateException("Los descuentos están deshabilitados: el administrador debe configurar "
                    + "un límite de descuento en \"Datos de la tienda\" antes de poder aplicar descuentos.");
        }

        if (tienda.getMaxDiscountAmount() != null && discount.compareTo(tienda.getMaxDiscountAmount()) > 0) {
            throw new IllegalStateException("Ese descuento no está permitido para \"" + productName
                    + "\", el monto máximo permitido es " + MONEY_FMT.format(tienda.getMaxDiscountAmount()));
        }
        if (tienda.getMaxDiscountPercent() != null && lineGross.signum() > 0) {
            BigDecimal maxFromPercent = lineGross.multiply(tienda.getMaxDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (discount.compareTo(maxFromPercent) > 0) {
                throw new IllegalStateException("Ese porcentaje de descuento no está permitido para \"" + productName
                        + "\", el porcentaje máximo permitido es " + tienda.getMaxDiscountPercent() + "%");
            }
        }
    }
}
