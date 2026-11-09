package com.boutique.pos.service;

import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Manda el reporte diario de inventario de una tienda a la hora que configuró
 * ({@link Tienda#getInventoryReportTime()}), al correo de su supervisor. Lo llama cada minuto
 * {@code InventoryReportJob}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReportService {

    /** Margen tras la hora configurada: si el servidor estuvo abajo ese minuto, todavía se manda. */
    private static final long WINDOW_MINUTES = 10;

    private final TiendaRepository tiendaRepository;
    private final UserRepository userRepository;
    private final InventoryReportExcelService excelService;
    private final EmailService emailService;
    private final CashReportRecipientService recipientService;

    /** Si a la tienda ya le toca y hoy no se le ha mandado, genera el Excel y lo envía. */
    @Transactional
    public void sendIfDue(Long tiendaId, LocalDateTime now) {
        Tienda tienda = tiendaRepository.findById(tiendaId).orElse(null);
        if (tienda == null || !isDue(tienda, now)) return;

        Set<String> to = recipients(tienda);
        if (to.isEmpty()) {
            log.warn("La tienda {} no tiene a quién mandarle el reporte de inventario", tienda.getName());
            return;
        }
        byte[] excel = excelService.build(tienda);
        tienda.setLastInventoryReportDate(now.toLocalDate());
        tiendaRepository.save(tienda);
        for (String email : to) emailService.sendInventoryReportEmail(tienda, excel, email);
        log.info("Reporte de inventario de {} enviado a {} destinatario(s)", tienda.getName(), to.size());
    }

    private boolean isDue(Tienda tienda, LocalDateTime now) {
        String time = tienda.getInventoryReportTime();
        if (time == null || now.toLocalDate().equals(tienda.getLastInventoryReportDate())) return false;
        long minutes = ChronoUnit.MINUTES.between(LocalTime.parse(time), now.toLocalTime());
        return minutes >= 0 && minutes < WINDOW_MINUTES;
    }

    /** El supervisor de la tienda; sin uno activo con correo, los destinatarios del corte. */
    private Set<String> recipients(Tienda tienda) {
        return userRepository.findSupervisorOfTienda(tienda.getId())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && u.getEmail() != null)
                .map(User::getEmail).map(Set::of)
                .orElseGet(() -> recipientService.resolveEmails(tienda));
    }
}
