package com.boutique.pos.controller;

import com.boutique.pos.dto.ApartadoLookupRequest;
import com.boutique.pos.dto.ApartadoPhoneLookupRequest;
import com.boutique.pos.dto.ApartadoRequest;
import com.boutique.pos.dto.ApartadoSelfCancelRequest;
import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.PublicApartadoDto;
import com.boutique.pos.dto.PublicCategoryDto;
import com.boutique.pos.dto.PublicProductDto;
import com.boutique.pos.dto.PublicTiendaDto;
import com.boutique.pos.model.Apartado;
import com.boutique.pos.service.ApartadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vitrina pública de apartados, expuesta bajo {@code /api/public/**} — SIN autenticación
 * (ver {@code SecurityConfig}, permite todo bajo este prefijo). Es la única superficie de
 * la aplicación que un cliente final visita sin cuenta ni login; el aislamiento entre
 * tiendas de dueños distintos lo da el {@code slug} de la URL, no una sesión.
 * <p>
 * Nunca expone entidades completas (ver {@link PublicTiendaDto}/{@link PublicProductDto}):
 * ningún dato interno (costos, límites de descuento, auditoría) debe poder verse desde
 * aquí. Los descuentos NUNCA los captura el cliente público — solo lee catálogo y manda
 * su solicitud de apartado; el cajero decide el descuento al confirmarlo.
 */
@RestController
@RequestMapping("/api/public/tiendas/{slug}")
@RequiredArgsConstructor
public class PublicController {

    private final ApartadoService apartadoService;

    /** Nombre/logo/color de la tienda, para el encabezado de su vitrina. */
    @GetMapping
    public ResponseEntity<ApiResponse<PublicTiendaDto>> tienda(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.publicTienda(slug), null));
    }

    /** Categorías de la tienda, para el filtro de la vitrina. */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<PublicCategoryDto>>> categories(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.publicCategories(slug), null));
    }

    /** Catálogo paginado de productos reservables de la tienda, con filtros opcionales por categoría y búsqueda por nombre. */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<PublicProductDto>>> products(
            @PathVariable String slug,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PublicProductDto> result = apartadoService.publicCatalog(slug, categoryId, q, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Reconsulta un conjunto puntual de productos por id (sin paginar) — usado por la
     * vitrina pública para revalidar un carrito de apartado restaurado desde {@code
     * localStorage} contra el stock/precio/oferta actuales al cargar la página, no para
     * navegar el catálogo normal (eso es {@link #products}). Un id que ya no existe, se
     * desactivó o dejó de ser reservable simplemente no aparece en la respuesta.
     */
    @GetMapping("/products/by-ids")
    public ResponseEntity<ApiResponse<List<PublicProductDto>>> productsByIds(
            @PathVariable String slug,
            @RequestParam List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.publicProductsByIds(slug, ids), null));
    }

    /**
     * Solicita un apartado. Queda {@code PENDING} (sin descontar stock todavía) hasta que
     * un cajero/admin de la tienda lo confirme — ver {@link ApartadoService#createPublic}.
     */
    @PostMapping("/apartados")
    public ResponseEntity<ApiResponse<PublicApartadoDto>> crearApartado(
            @PathVariable String slug,
            @Valid @RequestBody ApartadoRequest req) {
        Apartado apartado = apartadoService.createPublic(slug, req);
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.toPublicDto(apartado),
                "¡Listo! Tu apartado quedó registrado, en breve la tienda te confirmará."));
    }

    /**
     * Consulta el estado de un apartado ya hecho, por folio (su id) + teléfono — para que un
     * cliente sin cuenta pueda darle seguimiento sin tener que llamar a la tienda (ver
     * {@link ApartadoService#publicApartadoLookup}). Va por {@code POST} y no {@code GET}
     * con query params a propósito: aunque el teléfono no es un dato ultra sensible, lleva
     * el mismo criterio que una credencial — mejor no dejarlo en la URL (logs de servidor/
     * proxy) si hay forma fácil de evitarlo.
     */
    @PostMapping("/apartados/lookup")
    public ResponseEntity<ApiResponse<PublicApartadoDto>> lookupApartado(
            @PathVariable String slug,
            @Valid @RequestBody ApartadoLookupRequest req) {
        PublicApartadoDto dto = apartadoService.publicApartadoLookup(slug, req.getId(), req.getPhone());
        return ResponseEntity.ok(ApiResponse.ok(dto, null));
    }

    /**
     * "¿No tienes tu folio?" — lista los apartados recientes de esta tienda que coincidan
     * con un teléfono (ver {@link ApartadoService#publicApartadoLookupByPhone} para el
     * criterio de coincidencia y la nota sobre por qué esta consulta es, a propósito,
     * menos estricta que {@link #lookupApartado}).
     */
    @PostMapping("/apartados/lookup-by-phone")
    public ResponseEntity<ApiResponse<List<PublicApartadoDto>>> lookupApartadosByPhone(
            @PathVariable String slug,
            @Valid @RequestBody ApartadoPhoneLookupRequest req) {
        List<PublicApartadoDto> results = apartadoService.publicApartadoLookupByPhone(slug, req.getPhone());
        return ResponseEntity.ok(ApiResponse.ok(results, null));
    }

    /**
     * El cliente cancela su propio apartado, sin hablarle a la tienda — mismo folio+
     * teléfono que {@link #lookupApartado} para verificar que de verdad es suyo, revalidado
     * aquí desde cero (nunca se confía en que el frontend ya lo validó al consultarlo antes
     * de mostrar el botón). También es la base de "editar" en la vitrina: cancela este y el
     * frontend manda al cliente a apartar de nuevo con los mismos productos precargados —
     * ver {@link ApartadoService#publicApartadoCancel}.
     */
    @PostMapping("/apartados/self-cancel")
    public ResponseEntity<ApiResponse<PublicApartadoDto>> selfCancelApartado(
            @PathVariable String slug,
            @Valid @RequestBody ApartadoSelfCancelRequest req) {
        PublicApartadoDto dto = apartadoService.publicApartadoCancel(slug, req.getId(), req.getPhone(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Tu apartado fue cancelado."));
    }
}
