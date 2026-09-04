package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.EscPosTicketService;
import com.boutique.pos.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controlador de ventas, expuesto bajo {@code /api/sales}.
 * <p>
 * El listado y la consulta de ventas viven bajo la sección {@code SALES},
 * mientras que el registro de una nueva venta se hace desde el flujo del
 * Punto de Venta ({@code POS}). La cancelación de una venta ya registrada se
 * restringe al rol ADMIN por su impacto en inventario y caja. Todas las
 * operaciones están acotadas a la tienda del usuario autenticado.
 */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final EscPosTicketService escPosTicketService;

    /**
     * Arma el ticket de una venta ya registrada en formato ESC/POS, listo para que el
     * navegador se lo pase directo a un puente local de impresión (ej. QZ Tray) que hable
     * con la impresora térmica USB de la caja. El backend nunca toca la impresora — corre
     * en la nube, sin acceso al hardware local de cada tienda; solo arma el contenido, como
     * una cadena hexadecimal (ver {@link EscPosTicketService} para el porqué del formato).
     * Incluye el comando de apertura del cajón al final si la venta fue en efectivo (ver
     * {@link EscPosTicketService#build}). Requiere acceso a {@code SALES} o {@code POS}.
     *
     * @param id    id de la venta a imprimir
     * @param actor usuario autenticado; la venta debe pertenecer a su tienda
     */
    @GetMapping("/{id}/ticket-escpos")
    @PreAuthorize("@sectionAccess.checkAny('SALES', 'POS')")
    public ResponseEntity<ApiResponse<String>> ticketEscPos(@PathVariable Long id,
                                                              @AuthenticationPrincipal User actor) {
        Sale sale = saleService.findById(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(escPosTicketService.build(sale), null));
    }

    /**
     * Lista paginada de ventas, con filtros opcionales por rango de fechas,
     * nombre de cliente, método de pago y estado. Requiere acceso a la sección
     * {@code SALES}.
     *
     * @param from          fecha/hora inicial del rango (opcional)
     * @param to            fecha/hora final del rango (opcional)
     * @param customerName  nombre de cliente a filtrar (opcional)
     * @param paymentMethod método de pago a filtrar (opcional)
     * @param status        estado de la venta a filtrar (opcional)
     * @param page          número de página (base 0, por defecto 0)
     * @param size          tamaño de página (por defecto 20)
     * @param actor         usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<PageResponse<Sale>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Sale> result = saleService.findAll(from, to, customerName, paymentMethod, status, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Obtiene el detalle de una venta por su id, dentro de la tienda del
     * usuario autenticado. Requiere acceso a la sección {@code SALES}.
     *
     * @param id    identificador de la venta
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<Sale>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.findById(id, actor), null));
    }

    /**
     * Registra una nueva venta (ticket) desde el flujo del Punto de Venta,
     * descontando el stock de los productos vendidos. Requiere acceso a la
     * sección {@code POS}.
     *
     * @param req   datos de la venta (productos, cantidades, método de pago, etc.)
     * @param actor usuario autenticado que realiza la venta
     */
    @PostMapping
    @PreAuthorize("@sectionAccess.check('POS')")
    public ResponseEntity<ApiResponse<Sale>> create(@Valid @RequestBody SaleRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.create(req, actor), "Venta registrada"));
    }

    /**
     * Cancela una venta ya registrada. Solo disponible para el rol ADMIN, dado
     * su impacto en el inventario y en los totales de caja.
     *
     * @param id    identificador de la venta a cancelar
     * @param actor usuario autenticado que realiza la cancelación
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Sale>> cancel(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.cancel(id, actor), "Venta cancelada"));
    }
}
