package com.boutique.pos.service;

import com.boutique.pos.dto.ApartadoCompleteRequest;
import com.boutique.pos.dto.ApartadoConfirmRequest;
import com.boutique.pos.dto.ApartadoItemRequest;
import com.boutique.pos.dto.ApartadoRequest;
import com.boutique.pos.dto.PublicApartadoDto;
import com.boutique.pos.dto.PublicCategoryDto;
import com.boutique.pos.dto.PublicProductDto;
import com.boutique.pos.dto.PublicTiendaDto;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.ApartadoRepository;
import com.boutique.pos.repository.CategoryRepository;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.ProductImageRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ciclo de vida completo de un {@link Apartado}: solicitud pública, confirmación,
 * finalización (venta real) y cancelación. Ver el Javadoc de {@link Apartado} para el
 * diagrama de estados.
 *
 * <p>La solicitud pública ({@link #createPublic}) es la única operación de este servicio
 * que NO recibe un {@code actor} autenticado — todo lo demás (confirmar, completar,
 * cancelar, listar) sí, y se acota a la tienda del actor con {@link TenantScope} igual
 * que el resto de los services de negocio.</p>
 */
@Service
@RequiredArgsConstructor
public class ApartadoService {

    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    // BETWEEN siempre necesita las dos fechas: cuando el filtro viene vacío, Postgres no
    // logra inferir el tipo de un parámetro timestamp nulo (mismo motivo que en
    // SaleService), así que en vez de mandar null se usa un rango que cubre todo el historial.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final ApartadoRepository apartadoRepository;
    private final TiendaRepository tiendaRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final SaleService saleService;
    private final EmailService emailService;
    private final TenantScope tenantScope;

    /**
     * Crea una SOLICITUD de apartado desde la tienda pública ({@code /apartar/{slug}}, sin
     * autenticación). No descuenta stock todavía (queda {@code PENDING}) — solo valida que
     * la tienda exista, tenga los apartados habilitados, y que cada producto exista, esté
     * activo, sea reservable y tenga en este momento stock suficiente (una validación de
     * sentido común, no una reserva real: entre esta solicitud y su confirmación el stock
     * puede cambiar, y quien confirme primero se lo queda — ver {@link #confirm}).
     * <p>
     * Al crearse, avisa por correo a todos los administradores de la tienda (best effort,
     * asíncrono): es el único momento del ciclo de vida en el que alguien tiene que actuar.
     *
     * @param slug slug público de la tienda (de la URL)
     * @param req datos de contacto del cliente y productos solicitados
     * @return el apartado recién creado, en estado {@code PENDING}
     * @throws IllegalArgumentException si la tienda no existe, no tiene apartados
     *         habilitados, o algún producto no existe/no es de esa tienda/no es reservable
     * @throws IllegalStateException si algún producto no tiene stock suficiente ahora mismo
     */
    @Transactional
    public Apartado createPublic(String slug, ApartadoRequest req) {
        Tienda tienda = findPublicTienda(slug);

        Apartado apartado = new Apartado();
        apartado.setTienda(tienda);
        apartado.setCustomerName(req.getCustomerName());
        apartado.setCustomerPhone(req.getCustomerPhone());
        apartado.setCustomerEmail(req.getCustomerEmail());
        apartado.setNotes(req.getNotes());
        apartado.setStatus(ApartadoStatus.PENDING);

        List<ApartadoItem> items = new ArrayList<>();
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (ApartadoItemRequest ir : req.getItems()) {
            Product product = productRepository.findById(ir.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + ir.getProductId()));
            if (!product.getTienda().getId().equals(tienda.getId()) || !Boolean.TRUE.equals(product.getIsActive())
                    || !Boolean.TRUE.equals(product.getIsReservable())) {
                throw new IllegalArgumentException("\"" + product.getName() + "\" ya no está disponible para apartar");
            }
            if (BigDecimal.valueOf(product.getStock()).compareTo(ir.getQuantity()) < 0) {
                throw new IllegalStateException("No hay suficiente stock de \"" + product.getName() + "\" en este momento");
            }

            // La oferta pública del producto (Product.apartadoDiscountPercent, si tiene) se
            // aplica AQUÍ, automáticamente — es la misma que el cliente ya vio en el
            // catálogo, nunca algo que él elige. unitPrice se guarda al precio de LISTA
            // (no ya descontado) y el descuento queda explícito en `discount`, igual que en
            // una Sale normal, para que quede claro de dónde salió el precio final.
            BigDecimal lineGross = product.getPrice().multiply(ir.getQuantity());
            BigDecimal lineDiscount = lineGross.subtract(finalPrice(lineGross, product.getApartadoDiscountPercent()));

            ApartadoItem item = new ApartadoItem();
            item.setApartado(apartado);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(ir.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setDiscount(lineDiscount);
            item.setSubtotal(lineGross.subtract(lineDiscount));
            items.add(item);
            grossSubtotal = grossSubtotal.add(lineGross);
            totalDiscount = totalDiscount.add(lineDiscount);
        }

        apartado.setItems(items);
        apartado.setSubtotal(grossSubtotal);
        apartado.setDiscount(totalDiscount);
        apartado.setTotal(grossSubtotal.subtract(totalDiscount));

        Apartado saved = apartadoRepository.save(apartado);
        notifyAdmins(saved);
        return saved;
    }

    /**
     * Mapea un {@link Apartado} a lo que sí es seguro devolverle al cliente público (ver
     * {@link PublicApartadoDto}) — nunca la entidad completa, que arrastra la {@link
     * Tienda} anidada (límites de descuento, slug, usuarios) y cada {@link Product} con su
     * costo y auditoría.
     */
    public PublicApartadoDto toPublicDto(Apartado apartado) {
        return PublicApartadoDto.builder()
                .id(apartado.getId())
                .status(apartado.getStatus().name())
                .requestedAt(apartado.getRequestedAt())
                .subtotal(apartado.getSubtotal())
                .discount(apartado.getDiscount())
                .total(apartado.getTotal())
                .items(apartado.getItems().stream()
                        .map(i -> PublicApartadoDto.Item.builder()
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .subtotal(i.getSubtotal())
                                .build())
                        .toList())
                .build();
    }

    /**
     * Resuelve la tienda pública de apartados por su slug. A propósito NO usa {@link
     * TenantScope} (no hay actor autenticado en esta ruta) — el slug ES el mecanismo de
     * aislamiento entre tiendas de dueños distintos en esta superficie pública.
     *
     * @param slug slug de la URL pública
     * @return la tienda activa con ese slug y apartados habilitados
     * @throws IllegalArgumentException si no existe una tienda activa con ese slug, o si
     *         esa tienda no tiene los apartados habilitados
     */
    public Tienda findPublicTienda(String slug) {
        Tienda tienda = tiendaRepository.findByPublicSlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada"));
        if (!Boolean.TRUE.equals(tienda.getApartadosEnabled())) {
            throw new IllegalArgumentException("Tienda no encontrada");
        }
        return tienda;
    }

    /**
     * Datos de una tienda para el encabezado de su vitrina pública (nombre/logo/color) —
     * nunca la entidad {@link Tienda} completa, para no filtrar límites de descuento ni
     * datos de auditoría por esta vía sin autenticación.
     */
    public PublicTiendaDto publicTienda(String slug) {
        Tienda tienda = findPublicTienda(slug);
        return PublicTiendaDto.builder()
                .name(tienda.getName())
                .logoPath(tienda.getLogoPath())
                .primaryColor(tienda.getPrimaryColor())
                .build();
    }

    /** Categorías de una tienda, para el filtro de su vitrina pública. */
    public List<PublicCategoryDto> publicCategories(String slug) {
        Tienda tienda = findPublicTienda(slug);
        return categoryRepository.findAllForTienda(tienda.getId()).stream()
                .map(c -> PublicCategoryDto.builder().id(c.getId()).name(c.getName()).build())
                .toList();
    }

    /**
     * Catálogo público paginado de productos reservables de una tienda (ver {@link
     * ProductRepository#findPublicCatalog}), ya mapeado a {@link PublicProductDto} con sus
     * fotos incluidas (batch-cargadas en un solo query para toda la página, no una por
     * producto).
     *
     * @param slug slug de la tienda
     * @param categoryId filtro opcional por categoría
     * @param q texto de búsqueda opcional por nombre (coincidencia parcial)
     * @param pageable paginación solicitada
     * @return página de productos activos, reservables y con stock, de esa tienda
     */
    public Page<PublicProductDto> publicCatalog(String slug, Long categoryId, String q, Pageable pageable) {
        Tienda tienda = findPublicTienda(slug);
        Page<Product> page = productRepository.findPublicCatalog(tienda.getId(), categoryId, q, pageable);

        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, List<String>> imagesByProduct = ids.isEmpty() ? Map.of()
                : productImageRepository.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(ids).stream()
                        .collect(Collectors.groupingBy(
                                img -> img.getProduct().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(ProductImage::getPath, Collectors.toList())));

        return page.map(p -> PublicProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .discountPercent(p.getApartadoDiscountPercent())
                .finalPrice(finalPrice(p.getPrice(), p.getApartadoDiscountPercent()))
                .unit(p.getUnit())
                .stock(p.getStock())
                .images(imagesByProduct.getOrDefault(p.getId(), List.of()))
                .build());
    }

    /**
     * Aplica el descuento promocional PÚBLICO de un producto ({@link
     * Product#getApartadoDiscountPercent()}) a su precio de lista — usado tanto para
     * mostrarlo en el catálogo público ({@link #publicCatalog}) como para calcularlo de
     * verdad al crear el apartado ({@link #createPublic}), así nunca se desincroniza lo
     * que el cliente ve de lo que en realidad se le cobra.
     *
     * @param price precio de lista del producto
     * @param discountPercent porcentaje de descuento (0-100), o null/0 para "sin oferta"
     * @return {@code price} sin cambios si no hay descuento, o ya con el descuento restado
     */
    private BigDecimal finalPrice(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.signum() <= 0 || price == null) return price;
        BigDecimal discount = price.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return price.subtract(discount);
    }

    /**
     * Búsqueda paginada de apartados de la tienda del actor, con filtros combinables por
     * estado, rango de fechas de solicitud y texto libre (cliente o producto).
     *
     * @param status filtro por estado, o null para no filtrar
     * @param from fecha mínima de solicitud (inclusiva), o null para no acotar por abajo
     * @param to fecha máxima de solicitud (inclusiva), o null para no acotar por arriba
     * @param q texto de búsqueda opcional: coincide con el nombre/teléfono del cliente, o
     *          el nombre de cualquier producto en las líneas del apartado (ver {@link
     *          ApartadoRepository#search})
     * @param pageable paginación solicitada
     * @param actor usuario que consulta; acota el resultado a su tienda
     */
    public Page<Apartado> search(ApartadoStatus status, LocalDate from, LocalDate to, String q, Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from.atStartOfDay() : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to.plusDays(1).atStartOfDay() : MAX_DATE;
        return apartadoRepository.search(tenantScope.scopeId(actor), status, effectiveFrom, effectiveTo, q, pageable);
    }

    /** Cuántos apartados {@code PENDING} tiene la tienda del actor — alimenta el badge del sidebar. */
    public long pendingCount(User actor) {
        return apartadoRepository.countPending(tenantScope.scopeId(actor));
    }

    /**
     * Busca un apartado por id, validando que pertenezca a la tienda del actor.
     *
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Apartado findById(Long id, User actor) {
        Apartado a = apartadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Apartado no encontrado: " + id));
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (a.getTienda() == null || !scope.equals(a.getTienda().getId()))) {
            throw new IllegalArgumentException("Apartado no encontrado: " + id);
        }
        return a;
    }

    /**
     * Confirma un apartado {@code PENDING}: aquí es donde de verdad se descuenta el stock
     * (con su {@link InventoryMovement} de tipo {@code OUT}, motivo "Apartado #id
     * confirmado") y empieza a correr el plazo hacia {@link Apartado#getExpiresAt()}.
     * <p>
     * Vuelve a validar el stock de cada producto EN ESTE MOMENTO (pudo cambiar desde que
     * se solicitó) y, si el cajero capturó descuentos, los valida contra el límite de
     * apartados de la tienda ({@link Tienda#getMaxApartadoDiscountAmount()}/{@link
     * Tienda#getMaxApartadoDiscountPercent()} — un límite SEPARADO del de venta física).
     *
     * @param id id del apartado a confirmar
     * @param req horas de vigencia (o null para usar el default de la tienda) y
     *            descuentos opcionales por línea
     * @param actor cajero/admin que confirma; queda registrado como {@code confirmedBy}
     * @return el apartado ya {@code ACTIVE}
     * @throws IllegalStateException si no está {@code PENDING}, si algún producto ya no
     *         tiene stock suficiente, o si algún descuento excede el límite configurado
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    @Transactional
    public Apartado confirm(Long id, ApartadoConfirmRequest req, User actor) {
        Apartado apartado = findById(id, actor);
        if (apartado.getStatus() != ApartadoStatus.PENDING) {
            throw new IllegalStateException("Este apartado ya fue confirmado, completado o cancelado");
        }

        Map<Long, BigDecimal> discountsByItemId = (req != null && req.getItems() != null)
                ? req.getItems().stream()
                        .filter(i -> i.getItemId() != null)
                        .collect(Collectors.toMap(
                                ApartadoConfirmRequest.ApartadoConfirmItemRequest::getItemId,
                                i -> i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO))
                : Map.of();

        Tienda tienda = apartado.getTienda();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (ApartadoItem item : apartado.getItems()) {
            Product product = item.getProduct();
            if (product == null) {
                throw new IllegalStateException("\"" + item.getProductName() + "\" ya no existe en el catálogo");
            }
            if (BigDecimal.valueOf(product.getStock()).compareTo(item.getQuantity()) < 0) {
                throw new IllegalStateException("No hay suficiente stock de \"" + product.getName()
                        + "\" para confirmar este apartado (disponible: " + product.getStock() + ")");
            }

            BigDecimal lineGross = item.getUnitPrice().multiply(item.getQuantity());
            BigDecimal discount = discountsByItemId.getOrDefault(item.getId(), BigDecimal.ZERO);
            validateApartadoDiscountLimit(discount, lineGross, product.getName(), tienda);
            item.setDiscount(discount);
            item.setSubtotal(lineGross.subtract(discount));
            subtotal = subtotal.add(lineGross);
            totalDiscount = totalDiscount.add(discount);

            int qtyInt = item.getQuantity().intValue();
            int previous = product.getStock();
            product.setStock(previous - qtyInt);
            productRepository.save(product);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(product);
            mv.setUser(actor);
            mv.setType(MovementType.OUT);
            mv.setQuantity(qtyInt);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous - qtyInt);
            mv.setReason("Apartado #" + apartado.getId() + " confirmado");
            movementRepository.save(mv);
        }

        int durationHours = req != null && req.getDurationHours() != null
                ? req.getDurationHours()
                : tienda.getDefaultApartadoHours();

        apartado.setSubtotal(subtotal);
        apartado.setDiscount(totalDiscount);
        apartado.setTotal(subtotal.subtract(totalDiscount));
        apartado.setDurationHours(durationHours);
        apartado.setConfirmedAt(LocalDateTime.now());
        apartado.setExpiresAt(apartado.getConfirmedAt().plusHours(durationHours));
        apartado.setConfirmedBy(actor);
        apartado.setStatus(ApartadoStatus.ACTIVE);
        return apartadoRepository.save(apartado);
    }

    /**
     * Completa un apartado {@code ACTIVE}: el cliente vino a recoger y pagar. Genera la
     * venta real vía {@link SaleService#completeFromApartado} (sin volver a descontar
     * stock, ya se descontó al confirmar) y marca el apartado como {@code COMPLETED}.
     *
     * @param id id del apartado a completar
     * @param req forma de pago y, si es efectivo, con cuánto pagó
     * @param actor cajero/admin que lo completa; debe tener un corte de caja propio
     *              abierto; queda registrado como {@code completedBy}
     * @return el apartado ya {@code COMPLETED}, con {@link Apartado#getSaleId()} apuntando
     *         a la venta generada
     * @throws IllegalStateException si no está {@code ACTIVE}, o si falla el cobro (ver
     *         {@link SaleService#completeFromApartado})
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    @Transactional
    public Apartado complete(Long id, ApartadoCompleteRequest req, User actor) {
        Apartado apartado = findById(id, actor);
        if (apartado.getStatus() != ApartadoStatus.ACTIVE) {
            throw new IllegalStateException("Solo se puede completar un apartado confirmado (activo)");
        }
        Sale sale = saleService.completeFromApartado(apartado, req.getPaymentMethod(), req.getAmountReceived(), req.getCustomerEmail(), actor);
        apartado.setStatus(ApartadoStatus.COMPLETED);
        apartado.setCompletedBy(actor);
        apartado.setSaleId(sale.getId());
        return apartadoRepository.save(apartado);
    }

    /**
     * Cancela un apartado {@code PENDING} o {@code ACTIVE}. Si ya estaba {@code ACTIVE}
     * (el stock ya se había descontado al confirmar), lo restituye con su propio {@link
     * InventoryMovement} de tipo {@code IN}.
     * <p>
     * {@code reason} es opcional (un cajero puede cancelar sin dar motivo) — se guarda en
     * {@link Apartado#getCancelReason()} para el historial y, si el cliente dejó correo al
     * solicitar el apartado, se le manda un aviso con ese motivo (best effort, ver {@link
     * EmailService#sendApartadoCancelledEmail}); sin correo del cliente, simplemente no hay
     * a quién avisarle.
     *
     * @param id id del apartado a cancelar
     * @param reason motivo para el cliente, opcional
     * @param actor cajero/admin que cancela; queda registrado como {@code cancelledBy}
     * @return el apartado ya {@code CANCELLED}
     * @throws IllegalStateException si ya estaba completado, cancelado o vencido
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    @Transactional
    public Apartado cancel(Long id, String reason, User actor) {
        Apartado apartado = findById(id, actor);
        if (apartado.getStatus() != ApartadoStatus.PENDING && apartado.getStatus() != ApartadoStatus.ACTIVE) {
            throw new IllegalStateException("Este apartado ya no se puede cancelar");
        }
        if (apartado.getStatus() == ApartadoStatus.ACTIVE) {
            restoreStock(apartado, "cancelado", actor);
        }
        apartado.setStatus(ApartadoStatus.CANCELLED);
        apartado.setCancelledBy(actor);
        apartado.setCancelledAt(LocalDateTime.now());
        apartado.setCancelReason(reason);
        Apartado saved = apartadoRepository.save(apartado);
        if (apartado.getCustomerEmail() != null && !apartado.getCustomerEmail().isBlank()) {
            emailService.sendApartadoCancelledEmail(apartado, reason, apartado.getCustomerEmail());
        }
        return saved;
    }

    /**
     * Restituye el stock de cada línea de un apartado {@code ACTIVE} (cancelación o
     * vencimiento), con un {@link InventoryMovement} de tipo {@code IN} por línea.
     *
     * @param apartado apartado {@code ACTIVE} cuyo stock se restituye
     * @param motivo texto corto para el {@code reason} del movimiento (ej. "cancelado", "vencido")
     * @param user usuario a registrar en el movimiento — el que cancela, o (si lo dispara
     *             el job de vencimiento, sin actor humano) quien lo había confirmado
     */
    private void restoreStock(Apartado apartado, String motivo, User user) {
        for (ApartadoItem item : apartado.getItems()) {
            Product product = item.getProduct();
            if (product == null) continue; // el producto pudo eliminarse desde entonces; no hay a quién devolverle stock
            int qty = item.getQuantity().intValue();
            int previous = product.getStock();
            product.setStock(previous + qty);
            productRepository.save(product);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(product);
            mv.setUser(user);
            mv.setType(MovementType.IN);
            mv.setQuantity(qty);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous + qty);
            mv.setReason("Apartado #" + apartado.getId() + " " + motivo);
            movementRepository.save(mv);
        }
    }

    /**
     * Marca como {@code EXPIRED} todo apartado {@code ACTIVE} cuyo {@link
     * Apartado#getExpiresAt()} ya se cumplió, restituyendo su stock — llamado únicamente
     * por {@code ApartadoExpiryJob}.
     *
     * @return cuántos apartados se vencieron en esta corrida
     */
    @Transactional
    public int expireOverdue() {
        List<Apartado> overdue = apartadoRepository.findAllByStatusAndExpiresAtBefore(ApartadoStatus.ACTIVE, LocalDateTime.now());
        for (Apartado apartado : overdue) {
            restoreStock(apartado, "vencido", apartado.getConfirmedBy());
            apartado.setStatus(ApartadoStatus.EXPIRED);
            apartadoRepository.save(apartado);
        }
        return overdue.size();
    }

    /**
     * Avisa por correo a todos los administradores de la tienda que hay un apartado nuevo
     * por revisar. Best effort: una tienda sin administradores registrados no revienta la
     * creación del apartado, simplemente no hay a quién avisarle.
     */
    private void notifyAdmins(Apartado apartado) {
        List<User> admins = userRepository.findAdminsByTiendaId(apartado.getTienda().getId());
        for (User admin : admins) {
            emailService.sendApartadoRequestEmail(apartado, admin.getEmail());
        }
    }

    /**
     * Igual que {@code SaleService.validateDiscountLimit}, pero contra el límite específico
     * de apartados de la tienda ({@link Tienda#getMaxApartadoDiscountAmount()}/{@link
     * Tienda#getMaxApartadoDiscountPercent()}) — a propósito un límite separado del de
     * venta física, para que una tienda pueda incentivar apartados con condiciones
     * distintas a las de mostrador. Sin descuento (0 o negativo) no valida nada.
     *
     * @throws IllegalStateException si la tienda no configuró ningún límite de apartados,
     *         o si el descuento excede el que sí configuró
     */
    private void validateApartadoDiscountLimit(BigDecimal discount, BigDecimal lineGross, String productName, Tienda tienda) {
        if (discount == null || discount.signum() <= 0) return;

        if (tienda.getMaxApartadoDiscountAmount() == null && tienda.getMaxApartadoDiscountPercent() == null) {
            throw new IllegalStateException("Los descuentos de apartado están deshabilitados: configura un límite "
                    + "en \"Datos de la tienda\" antes de poder aplicar descuentos al confirmar.");
        }
        if (tienda.getMaxApartadoDiscountAmount() != null && discount.compareTo(tienda.getMaxApartadoDiscountAmount()) > 0) {
            throw new IllegalStateException("Ese descuento no está permitido para \"" + productName
                    + "\", el monto máximo permitido es " + MONEY_FMT.format(tienda.getMaxApartadoDiscountAmount()));
        }
        if (tienda.getMaxApartadoDiscountPercent() != null && lineGross.signum() > 0) {
            BigDecimal maxFromPercent = lineGross.multiply(tienda.getMaxApartadoDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (discount.compareTo(maxFromPercent) > 0) {
                throw new IllegalStateException("Ese porcentaje de descuento no está permitido para \"" + productName
                        + "\", el porcentaje máximo permitido es " + tienda.getMaxApartadoDiscountPercent() + "%");
            }
        }
    }
}
