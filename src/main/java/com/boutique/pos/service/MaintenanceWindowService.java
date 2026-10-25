package com.boutique.pos.service;

import com.boutique.pos.dto.MaintenanceWindowCreate;
import com.boutique.pos.dto.MaintenanceWindowExtend;
import com.boutique.pos.dto.MaintenanceWindowView;
import com.boutique.pos.exception.MaintenanceModeException;
import com.boutique.pos.model.MaintenanceWindow;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.MaintenanceWindowRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Administra las ventanas de mantenimiento de la plataforma — de alcance global, visibles
 * en el Login de TODAS las tiendas (ver {@code PublicMaintenanceController}), no algo por
 * tienda. Exclusivo de SUPER_ADMIN.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceWindowService {

    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final TenantScope tenantScope;

    /** La ventana relevante ahora mismo (en curso o la próxima programada), si hay alguna. */
    // Caché corto de "¿hay una ventana activa ahora?": MaintenanceInterceptor lo pregunta en
    // CADA petición, no se quiere una consulta a la base por request. Se invalida al
    // programar/extender/cancelar, así que un cambio hecho aquí se aplica de inmediato.
    private static final long ACTIVE_CACHE_MS = 15_000;
    private volatile Optional<MaintenanceWindow> cachedActive = Optional.empty();
    private volatile long cachedAtMs = 0;

    /**
     * Ventana de mantenimiento en curso en este momento (empezó y no ha terminado), si la hay.
     */
    public Optional<MaintenanceWindow> activeNow() {
        long nowMs = System.currentTimeMillis();
        if (nowMs - cachedAtMs > ACTIVE_CACHE_MS) {
            LocalDateTime now = LocalDateTime.now();
            cachedActive = maintenanceWindowRepository.findFirstByEndAtAfterOrderByStartAtAsc(now)
                    .filter(w -> !now.isBefore(w.getStartAt()));
            cachedAtMs = nowMs;
        }
        // Se re-valida contra la hora actual: la ventana pudo terminar dentro del caché.
        LocalDateTime now = LocalDateTime.now();
        return cachedActive.filter(w -> now.isBefore(w.getEndAt()) && !now.isBefore(w.getStartAt()));
    }

    /**
     * Durante una ventana activa, solo el SUPER_ADMIN puede iniciar sesión y operar (para
     * hacer pruebas); a cualquier otro usuario se le corta con {@link MaintenanceModeException}.
     */
    public void assertCanOperate(User user) {
        if (user == null) return;
        boolean superAdmin = user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getName());
        if (superAdmin) return;
        activeNow().ifPresent(w -> { throw new MaintenanceModeException(w.getEndAt()); });
    }

    private void invalidateCache() {
        cachedAtMs = 0;
    }

    public Optional<MaintenanceWindowView> current() {
        return maintenanceWindowRepository.findFirstByEndAtAfterOrderByStartAtAsc(LocalDateTime.now()).map(this::toView);
    }

    /**
     * Programa una ventana nueva.
     *
     * @throws IllegalArgumentException si la hora de fin no es posterior a la de inicio
     */
    @Transactional
    public MaintenanceWindowView schedule(MaintenanceWindowCreate req, User actor) {
        requireSuperAdmin(actor);
        if (!req.getEndAt().isAfter(req.getStartAt())) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
        }
        MaintenanceWindow w = MaintenanceWindow.builder()
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .createdBy(actor)
                .build();
        MaintenanceWindow saved = maintenanceWindowRepository.save(w);
        invalidateCache();
        return toView(saved);
    }

    /**
     * Prolonga una ventana ya en curso (o todavía programada) a una hora de fin posterior a
     * la actual — es la única edición permitida una vez creada.
     *
     * @throws IllegalArgumentException si la ventana ya terminó, o la nueva hora de fin no
     *         es posterior a la actual
     */
    @Transactional
    public MaintenanceWindowView extend(Long id, MaintenanceWindowExtend req, User actor) {
        requireSuperAdmin(actor);
        MaintenanceWindow w = maintenanceWindowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ventana no encontrada: " + id));
        if (!w.getEndAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Esta ventana ya terminó, no se puede prolongar");
        }
        if (!req.getEndAt().isAfter(w.getEndAt())) {
            throw new IllegalArgumentException("La nueva hora de fin debe ser posterior a la actual");
        }
        w.setEndAt(req.getEndAt());
        w.setUpdatedBy(actor);
        MaintenanceWindow saved = maintenanceWindowRepository.save(w);
        invalidateCache();
        return toView(saved);
    }

    /**
     * Cancela una ventana que TODAVÍA NO EMPIEZA (para corregir una programada por error).
     * Una vez en curso ya no se puede cancelar desde aquí, solo prolongar — si de verdad
     * terminó antes de lo previsto, lo más simple es prolongarla lo mínimo y dejar que
     * cierre sola.
     *
     * @throws IllegalArgumentException si la ventana no existe o ya empezó
     */
    @Transactional
    public void cancel(Long id, User actor) {
        requireSuperAdmin(actor);
        MaintenanceWindow w = maintenanceWindowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ventana no encontrada: " + id));
        if (!w.getStartAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Esta ventana ya empezó, no se puede cancelar");
        }
        maintenanceWindowRepository.delete(w);
        invalidateCache();
    }

    private void requireSuperAdmin(User actor) {
        if (!tenantScope.isSuperAdmin(actor)) {
            throw new AccessDeniedException("Solo un SUPER_ADMIN puede administrar ventanas de mantenimiento");
        }
    }

    private MaintenanceWindowView toView(MaintenanceWindow w) {
        LocalDateTime now = LocalDateTime.now();
        boolean active = !now.isBefore(w.getStartAt()) && now.isBefore(w.getEndAt());
        return MaintenanceWindowView.builder()
                .id(w.getId())
                .startAt(w.getStartAt())
                .endAt(w.getEndAt())
                .phase(active ? "ACTIVE" : "SCHEDULED")
                .build();
    }
}
