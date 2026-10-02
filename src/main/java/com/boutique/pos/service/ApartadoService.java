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
import com.boutique.pos.repository.TiendaInfoRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final TiendaInfoRepository tiendaInfoRepository;
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
     * Al crearse, avisa por correo a todo el personal de la tienda con acceso a Apartados
     * (best effort, asíncrono, ver {@link #staffToNotify}) — es el momento en que alguien
     * tiene que revisarlo y decidir si lo confirma o lo cancela.
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
        for (User staff : staffToNotify(saved, null)) {
            emailService.sendApartadoRequestEmail(saved, staff.getEmail());
        }
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
                .confirmedAt(apartado.getConfirmedAt())
                .expiresAt(apartado.getExpiresAt())
                .cancelledAt(apartado.getCancelledAt())
                .cancelReason(apartado.getCancelReason())
                .subtotal(apartado.getSubtotal())
                .discount(apartado.getDiscount())
                .total(apartado.getTotal())
                .items(apartado.getItems().stream()
                        .map(i -> PublicApartadoDto.Item.builder()
                                .productId(i.getProduct() != null ? i.getProduct().getId() : null)
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .subtotal(i.getSubtotal())
                                .build())
                        .toList())
                .build();
    }

    /**
     * Mensaje genérico usado tanto cuando el folio no existe/no es de esta tienda como
     * cuando el teléfono no coincide — a propósito EL MISMO en los dos casos (ver {@link
     * #publicApartadoLookup}), para no dejarle saber a quien prueba folios al azar cuál de
     * las dos cosas falló (eso le ayudaría a enumerar folios ajenos válidos).
     */
    private static final String LOOKUP_NOT_FOUND_MSG = "No encontramos un apartado con ese folio y teléfono en esta tienda";

    /**
     * Consulta pública de un apartado por folio (su id) + teléfono — la única forma de que
     * un cliente sin cuenta le dé seguimiento a su solicitud sin tener que llamar a la
     * tienda (ver {@code PublicController#lookupApartado}). El folio por sí solo no basta:
     * debe coincidir también el teléfono que dejó al solicitarlo, comparado de forma
     * tolerante a formato (ver {@link #phonesMatch}) porque puede haberlo tecleado distinto
     * la primera vez (con/sin lada, espacios, guiones) a como lo escribe ahora.
     *
     * @param slug  slug de la tienda
     * @param id    folio del apartado (su id)
     * @param phone teléfono tecleado para verificar
     * @return el apartado, mapeado igual que al crearlo (con su estado actual)
     * @throws IllegalArgumentException si no hay un apartado con ese folio en esa tienda, o
     *         si el teléfono no coincide con el que se dejó al solicitarlo
     */
    public PublicApartadoDto publicApartadoLookup(String slug, Long id, String phone) {
        return toPublicDto(resolveAndVerifyPublicApartado(slug, id, phone));
    }

    /**
     * Resuelve un apartado por folio + teléfono, tal como {@link #publicApartadoLookup}
     * (mismo mensaje genérico si el folio no existe/no es de esa tienda o si el teléfono no
     * coincide) — factorizado aparte porque lo comparte con {@link
     * #publicApartadoCancel}, que necesita el MISMO nivel de verificación antes de dejar
     * mutar algo, no solo consultarlo.
     */
    private Apartado resolveAndVerifyPublicApartado(String slug, Long id, String phone) {
        Tienda tienda = findPublicTienda(slug);
        Apartado apartado = apartadoRepository.findById(id)
                .filter(a -> a.getTienda().getId().equals(tienda.getId()))
                .orElseThrow(() -> new IllegalArgumentException(LOOKUP_NOT_FOUND_MSG));
        if (!phonesMatch(apartado.getCustomerPhone(), phone)) {
            throw new IllegalArgumentException(LOOKUP_NOT_FOUND_MSG);
        }
        return apartado;
    }

    /**
     * Cancela un apartado desde la vitrina pública — el cliente mismo, sin hablarle a la
     * tienda (ver {@code PublicController#selfCancelApartado}). Mismo folio+teléfono que
     * {@link #publicApartadoLookup} para verificar que de verdad es suyo (nunca basta con
     * haberlo visto en el frontend: esta llamada revalida todo desde cero en el servidor),
     * y de ahí en adelante usa exactamente la misma lógica que la cancelación de un cajero/
     * admin ({@link #doCancel}) — mismo restituir stock si ya estaba {@code ACTIVE}, mismos
     * avisos. Queda registrado como cancelado SIN {@code cancelledBy} (null = lo canceló el
     * cliente, no personal — útil para quien revise el historial después).
     * <p>
     * También es la base de "editar" en la vitrina: el frontend llama esto para liberar el
     * apartado actual y de inmediato deja al cliente mandar uno nuevo (con los mismos
     * productos precargados) desde el flujo normal de {@link #createPublic} — así "editar"
     * reutiliza TODA la lógica ya probada de cancelar + crear, en vez de inventar una
     * mutación nueva de "cambiar las líneas de un apartado existente".
     *
     * @param slug   slug de la tienda
     * @param id     folio del apartado a cancelar
     * @param phone  teléfono a verificar contra el que se dejó al solicitarlo
     * @param reason motivo opcional que el cliente quiera dejar
     * @return el apartado ya {@code CANCELLED}
     * @throws IllegalArgumentException si no hay un apartado con ese folio en esa tienda, o
     *         si el teléfono no coincide
     * @throws IllegalStateException si ya estaba completado, cancelado o vencido
     */
    @Transactional
    public PublicApartadoDto publicApartadoCancel(String slug, Long id, String phone, String reason) {
        Apartado apartado = resolveAndVerifyPublicApartado(slug, id, phone);
        return toPublicDto(doCancel(apartado, reason, null));
    }

    /**
     * Candidatos recientes a revisar en {@link #publicApartadoLookupByPhone} — bastante
     * generoso (200) porque el filtro real pasa en Java sobre texto libre, no en SQL.
     */
    private static final int PHONE_LOOKUP_CANDIDATE_LIMIT = 200;

    /** Cuántos apartados como máximo se le muestran al cliente en {@link #publicApartadoLookupByPhone}. */
    private static final int PHONE_LOOKUP_RESULT_LIMIT = 10;

    /**
     * "¿No tienes tu folio?" — busca los apartados de esta tienda que coincidan con un
     * teléfono, sin necesitar el folio (ver {@link #publicApartadoLookup}, que sí lo
     * exige). A propósito es MENOS estricto que esa otra consulta: cualquiera que sepa el
     * teléfono de alguien puede ver su historial de apartados en esta tienda (nombre no se
     * pide), no solo uno puntual — se decidió así explícitamente porque no es información
     * sensible (no hay pagos ni datos financieros de por medio) y perder el folio es un
     * caso real y frecuente. Si se vuelve un problema real, lo primero a ajustar sería
     * pedir también el nombre exacto para filtrar.
     *
     * @param slug  slug de la tienda
     * @param phone teléfono a buscar (tolerante a formato, ver {@link #phonesMatch})
     * @return hasta {@value #PHONE_LOOKUP_RESULT_LIMIT} apartados que coinciden, más
     *         recientes primero; vacío si no hay ninguno o el teléfono es demasiado corto
     */
    public List<PublicApartadoDto> publicApartadoLookupByPhone(String slug, String phone) {
        Tienda tienda = findPublicTienda(slug);
        if (phone == null || phone.replaceAll("\\D", "").length() < 7) return List.of();
        List<Apartado> candidates = apartadoRepository.findByTiendaIdOrderByRequestedAtDesc(
                tienda.getId(), PageRequest.of(0, PHONE_LOOKUP_CANDIDATE_LIMIT));
        return candidates.stream()
                .filter(a -> phonesMatch(a.getCustomerPhone(), phone))
                .limit(PHONE_LOOKUP_RESULT_LIMIT)
                .map(this::toPublicDto)
                .toList();
    }

    /**
     * Compara dos teléfonos de forma tolerante a formato: se queda solo con los dígitos de
     * cada uno y compara los últimos 10 (largo de un número mexicano sin lada de país), así
     * "55 1234 5678", "5512345678" y "+52 55 1234 5678" se consideran el mismo número.
     * Exige al menos 7 dígitos en cada uno (mismo mínimo que ya valida {@code
     * ApartadoRequest#customerPhone}) para no dar un "match" con una entrada casi vacía.
     */
    private boolean phonesMatch(String stored, String typed) {
        String a = stored == null ? "" : stored.replaceAll("\\D", "");
        String b = typed == null ? "" : typed.replaceAll("\\D", "");
        if (a.length() < 7 || b.length() < 7) return false;
        String suffixA = a.substring(Math.max(0, a.length() - 10));
        String suffixB = b.substring(Math.max(0, b.length() - 10));
        return suffixA.equals(suffixB);
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
        TiendaInfo info = tiendaInfoRepository.findByTiendaId(tienda.getId()).orElse(null);
        return PublicTiendaDto.builder()
                .name(tienda.getName())
                .logoPath(tienda.getLogoPath())
                .primaryColor(tienda.getPrimaryColor())
                .defaultApartadoHours(tienda.getDefaultApartadoHours())
                .direccion(buildAddress(info))
                .telefono(info != null ? info.getTelefono() : null)
                .horario(info != null ? info.getHorario() : null)
                .paginaWeb(info != null ? info.getPaginaWeb() : null)
                .redesSociales(info != null ? info.getRedesSociales() : null)
                .build();
    }

    /**
     * Calle, colonia, C.P., localidad y estado de {@link TiendaInfo} unidos en una sola
     * línea separada por comas, saltándose los que vengan vacíos — mismo orden que ya usa
     * el ticket PDF ({@code TicketPdfService#addAddressLines}), pero en una sola línea
     * (ahí va una por renglón) porque aquí es para un recuadro compacto de la vitrina
     * pública, no para el encabezado de un ticket.
     *
     * @return la dirección armada, o null si la tienda no tiene {@link TiendaInfo} o
     *         ninguno de esos campos capturado
     */
    private String buildAddress(TiendaInfo info) {
        if (info == null) return null;
        String cp = info.getCodigoPostal() != null && !info.getCodigoPostal().isBlank()
                ? "C.P. " + info.getCodigoPostal() : null;
        String joined = Stream.of(info.getCalle(), info.getColonia(), cp, info.getLocalidad(), info.getEstado())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
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
     * Reconsulta un conjunto puntual de productos por id, sin paginar — usado para
     * revalidar un carrito de apartado restaurado desde {@code localStorage} en la vitrina
     * pública (ver {@code PublicController#productsByIds}) contra el stock/precio/oferta
     * ACTUALES, no para navegar el catálogo normal (eso es {@link #publicCatalog}). Un id
     * que ya no existe, se desactivó o dejó de ser reservable simplemente no aparece en el
     * resultado — el frontend interpreta su ausencia como "ya no disponible" y lo quita del
     * carrito con un aviso.
     *
     * @param slug slug de la tienda
     * @param ids  ids de producto a reconsultar
     * @return los productos de esa lista que siguen activos y reservables en esa tienda
     */
    public List<PublicProductDto> publicProductsByIds(String slug, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Tienda tienda = findPublicTienda(slug);
        List<Product> products = productRepository.findPublicByIds(tienda.getId(), ids);

        List<Long> foundIds = products.stream().map(Product::getId).toList();
        Map<Long, List<String>> imagesByProduct = foundIds.isEmpty() ? Map.of()
                : productImageRepository.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(foundIds).stream()
                        .collect(Collectors.groupingBy(
                                img -> img.getProduct().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(ProductImage::getPath, Collectors.toList())));

        return products.stream().map(p -> PublicProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .discountPercent(p.getApartadoDiscountPercent())
                .finalPrice(finalPrice(p.getPrice(), p.getApartadoDiscountPercent()))
                .unit(p.getUnit())
                .stock(p.getStock())
                .images(imagesByProduct.getOrDefault(p.getId(), List.of()))
                .build()).toList();
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
        // Solo tiene sentido mientras sigue PENDING: es antes de confirmar cuando el
        // cajero necesita saber qué tan libre está de verdad el stock (ver el método).
        if (a.getStatus() == ApartadoStatus.PENDING) {
            populateAvailableStock(a);
        }
        return a;
    }

    /**
     * Calcula, por línea, {@link ApartadoItem#getAvailableStock()}: el stock actual del
     * producto menos lo que ya reclaman OTRAS solicitudes {@code PENDING} del mismo
     * producto ({@link ApartadoRepository#sumPendingQuantityByProductExcluding}) — sin
     * esto, el cajero vería el stock crudo (todavía no descontado para NINGÚN PENDING) y
     * podría pensar que hay más disponible de lo que en realidad queda si hay más de una
     * solicitud compitiendo por el mismo producto.
     *
     * @param apartado apartado {@code PENDING} cuyas líneas se anotan in-place
     */
    private void populateAvailableStock(Apartado apartado) {
        List<Long> productIds = apartado.getItems().stream()
                .map(ApartadoItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return;

        Map<Long, BigDecimal> pendingByProduct = apartadoRepository
                .sumPendingQuantityByProductExcluding(apartado.getId(), productIds).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> (BigDecimal) r[1]));

        for (ApartadoItem item : apartado.getItems()) {
            Product product = item.getProduct();
            if (product == null) continue;
            BigDecimal claimedByOthers = pendingByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);
            item.setAvailableStock(Math.max(product.getStock() - claimedByOthers.intValue(), 0));
        }
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
     * <p>
     * Al confirmarse, avisa por correo al resto del personal con acceso a Apartados (ver
     * {@link #staffToNotify}, excluye a quien confirmó) y, si el cliente dejó correo al
     * solicitarlo, también a él (con la fecha límite para recogerlo).
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
            if (item.getProduct() == null) {
                throw new IllegalStateException("\"" + item.getProductName() + "\" ya no existe en el catálogo");
            }
            // findByIdForUpdate (no la referencia ya cargada del item): toma el lock ANTES
            // de leer el stock, para que dos confirmaciones (o una confirmación y una venta)
            // concurrentes sobre el mismo producto queden serializadas en vez de ambas
            // partir del mismo stock leído (condición de carrera — hallazgo "Media" de la
            // auditoría de código).
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalStateException("\"" + item.getProductName() + "\" ya no existe en el catálogo"));
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
        Apartado saved = apartadoRepository.save(apartado);

        for (User staff : staffToNotify(saved, actor)) {
            emailService.sendApartadoConfirmedStaffEmail(saved, staff.getEmail());
        }
        if (saved.getCustomerEmail() != null && !saved.getCustomerEmail().isBlank()) {
            emailService.sendApartadoConfirmedCustomerEmail(saved, saved.getCustomerEmail());
        }
        return saved;
    }

    /**
     * Completa un apartado {@code ACTIVE}: el cliente vino a recoger y pagar. Genera la
     * venta real vía {@link SaleService#completeFromApartado} (sin volver a descontar
     * stock, ya se descontó al confirmar) y marca el apartado como {@code COMPLETED}.
     * Avisa por correo al resto del personal con acceso a Apartados (excluye a quien lo
     * completó) — el cliente no recibe un aviso aparte porque ya le llega su ticket.
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
        Apartado saved = apartadoRepository.save(apartado);

        // Al cliente ya le llega su ticket aparte (SaleService#completeFromApartado ->
        // EmailService#sendTicketEmail), no hace falta duplicarlo aquí — solo al personal.
        for (User staff : staffToNotify(saved, actor)) {
            emailService.sendApartadoCompletedStaffEmail(saved, staff.getEmail());
        }
        return saved;
    }

    /**
     * Quita UNA línea de un apartado {@code PENDING} — pensado para cuando, entre que se
     * solicitó y se revisa, el producto se vendió por otro lado y ya no queda ninguna
     * pieza ({@link ApartadoItem#getAvailableStock()} en 0): en vez de tener que cancelar
     * el apartado completo, el cajero quita solo esa línea y confirma el resto. No toca
     * stock (un {@code PENDING} nunca lo descontó) ni manda avisos automáticos — el
     * frontend le recuerda al cajero contactar al cliente él mismo.
     *
     * @param apartadoId id del apartado
     * @param itemId id de la línea a quitar
     * @param actor cajero/admin que quita la línea
     * @return el apartado ya sin esa línea, con sus totales recalculados
     * @throws IllegalArgumentException si el apartado o la línea no existen (o no
     *         pertenecen a la tienda del actor / a ese apartado)
     * @throws IllegalStateException si el apartado ya no está {@code PENDING}, o si es la
     *         única línea que le queda (para eso está cancelar el apartado completo)
     */
    @Transactional
    public Apartado removeItem(Long apartadoId, Long itemId, User actor) {
        Apartado apartado = findById(apartadoId, actor);
        if (apartado.getStatus() != ApartadoStatus.PENDING) {
            throw new IllegalStateException("Solo se pueden quitar productos de un apartado pendiente");
        }
        if (apartado.getItems().size() <= 1) {
            throw new IllegalStateException("No puedes quitar el único producto de este apartado; cancélalo completo si ya no aplica");
        }
        ApartadoItem toRemove = apartado.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado en este apartado: " + itemId));

        apartado.getItems().remove(toRemove);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (ApartadoItem item : apartado.getItems()) {
            subtotal = subtotal.add(item.getUnitPrice().multiply(item.getQuantity()));
            discount = discount.add(item.getDiscount());
        }
        apartado.setSubtotal(subtotal);
        apartado.setDiscount(discount);
        apartado.setTotal(subtotal.subtract(discount));

        return apartadoRepository.save(apartado);
    }

    /**
     * Cancela un apartado {@code PENDING} o {@code ACTIVE}. Si ya estaba {@code ACTIVE}
     * (el stock ya se había descontado al confirmar), lo restituye con su propio {@link
     * InventoryMovement} de tipo {@code IN}.
     * <p>
     * {@code reason} es opcional (un cajero puede cancelar sin dar motivo) — se guarda en
     * {@link Apartado#getCancelReason()} para el historial. Avisa por correo al resto del
     * personal con acceso a Apartados SIEMPRE (excluye a quien canceló, ver {@link
     * #staffToNotify}) y, si el cliente dejó correo al solicitarlo, también a él con ese
     * motivo (best effort, ver {@link EmailService#sendApartadoCancelledEmail}); sin
     * correo del cliente, simplemente no hay cómo avisarle a él, pero el personal se
     * entera de todas formas.
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
        return doCancel(apartado, reason, actor);
    }

    /**
     * Lógica real de cancelar, compartida por {@link #cancel} (cajero/admin autenticado) y
     * {@link #publicApartadoCancel} (el cliente mismo, desde la vitrina pública) — cada uno
     * resuelve y AUTORIZA el {@link Apartado} a su manera (tienda del actor vía {@link
     * TenantScope}, o folio+teléfono) antes de llegar aquí; este método ya no vuelve a
     * decidir quién puede tocar qué, solo ejecuta el cambio de estado.
     *
     * @param apartado apartado ya resuelto y autorizado
     * @param reason   motivo opcional (cajero/admin o el propio cliente)
     * @param actor    quien cancela; {@code null} si lo hizo el cliente desde la vitrina
     *                 pública (sin actor humano del lado del personal) — afecta a quién se
     *                 le avisa ({@link #staffToNotify}) y queda registrado así en {@link
     *                 Apartado#getCancelledBy()}, para distinguir después "lo canceló el
     *                 cliente" de "lo canceló alguien del personal"
     * @throws IllegalStateException si ya estaba completado, cancelado o vencido
     */
    private Apartado doCancel(Apartado apartado, String reason, User actor) {
        if (apartado.getStatus() != ApartadoStatus.PENDING && apartado.getStatus() != ApartadoStatus.ACTIVE) {
            throw new IllegalStateException("Este apartado ya no se puede cancelar");
        }
        if (apartado.getStatus() == ApartadoStatus.ACTIVE) {
            // InventoryMovement.user es NOT NULL — sin actor humano (cancelación desde la
            // vitrina pública), se le atribuye el movimiento a quien lo había confirmado,
            // mismo criterio que ya usa expireOverdue() para su propio restoreStock.
            restoreStock(apartado, "cancelado", actor != null ? actor : apartado.getConfirmedBy());
        }
        apartado.setStatus(ApartadoStatus.CANCELLED);
        apartado.setCancelledBy(actor);
        apartado.setCancelledAt(LocalDateTime.now());
        apartado.setCancelReason(reason);
        Apartado saved = apartadoRepository.save(apartado);

        for (User staff : staffToNotify(saved, actor)) {
            emailService.sendApartadoCancelledStaffEmail(saved, reason, staff.getEmail());
        }
        if (saved.getCustomerEmail() != null && !saved.getCustomerEmail().isBlank()) {
            emailService.sendApartadoCancelledEmail(saved, reason, saved.getCustomerEmail());
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
            if (item.getProduct() == null) continue; // el producto pudo eliminarse desde entonces; no hay a quién devolverle stock
            // findByIdForUpdate: mismo motivo que en confirm() — dos operaciones
            // concurrentes sobre el mismo producto no deben pisarse.
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product == null) continue;
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
     * por {@code ApartadoExpiryJob}. Sin actor humano (lo dispara el job programado), así
     * que avisa a TODO el personal con acceso a Apartados (nadie que excluir) y, si el
     * cliente dejó correo al solicitarlo, también a él.
     *
     * @return cuántos apartados se vencieron en esta corrida
     */
    @Transactional
    public int expireOverdue() {
        List<Apartado> overdue = apartadoRepository.findAllByStatusAndExpiresAtBefore(ApartadoStatus.ACTIVE, LocalDateTime.now());
        for (Apartado apartado : overdue) {
            restoreStock(apartado, "vencido", apartado.getConfirmedBy());
            apartado.setStatus(ApartadoStatus.EXPIRED);
            Apartado saved = apartadoRepository.save(apartado);

            for (User staff : staffToNotify(saved, null)) {
                emailService.sendApartadoExpiredStaffEmail(saved, staff.getEmail());
            }
            if (saved.getCustomerEmail() != null && !saved.getCustomerEmail().isBlank()) {
                emailService.sendApartadoExpiredCustomerEmail(saved, saved.getCustomerEmail());
            }
        }
        return overdue.size();
    }

    /**
     * Personal activo de la tienda del apartado con acceso a la sección {@code APARTADOS}
     * (cualquier rol, no solo ADMIN — un cajero o vendedor con ese acceso también debe
     * enterarse), para los avisos de cambio de estado. Best effort: una tienda sin nadie
     * con ese acceso no revienta la operación, simplemente no hay a quién avisarle.
     *
     * @param apartado apartado cuyo cambio de estado se va a avisar
     * @param actor quien hizo la acción (confirmar/completar/cancelar), para EXCLUIRLO de
     *              su propio aviso — ya sabe lo que acaba de hacer; {@code null} si no hay
     *              actor humano (creación pública, o el job de vencimiento), en cuyo caso
     *              se incluye a todos sin excepción
     * @return el personal a notificar
     */
    private List<User> staffToNotify(Apartado apartado, User actor) {
        List<User> staff = userRepository.findActiveByTiendaIdAndSection(apartado.getTienda().getId(), AppSection.APARTADOS);
        if (actor == null) return staff;
        return staff.stream().filter(u -> !u.getId().equals(actor.getId())).toList();
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
