package com.boutique.pos.config;

import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.CashCutSchedule;
import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.repository.CashCutRepository;
import com.boutique.pos.repository.CashCutScheduleRepository;
import com.boutique.pos.service.CashCutReportNotifier;
import com.boutique.pos.service.CashCutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Revisa cada minuto si ya es la hora configurada en cash_cut_schedule y, de ser así,
// cierra TODOS los cortes que sigan abiertos (de cualquier tienda) y manda UN solo reporte
// por tienda con TODOS los cortes del día — los que ya se habían cerrado a mano en cualquier
// momento del día y los que este job cierra justo ahora. La hora es global para todo el sistema.
// Los cierres manuales (CashCutService.close) NO mandan correo por su cuenta.
@Component
@RequiredArgsConstructor
@Slf4j
public class CashCutAutoCloseJob {

    private final CashCutScheduleRepository scheduleRepository;
    private final CashCutRepository cashCutRepository;
    private final CashCutService cashCutService;
    private final CashCutReportNotifier reportNotifier;

    // en memoria: si la app se reinicia justo en el minuto exacto, en el peor caso
    // corre dos veces ese día — aceptable, cerrar un corte ya cerrado simplemente no hace nada.
    private LocalDate lastRunDate;

    @Scheduled(fixedRate = 60_000)
    public void checkAndRun() {
        CashCutSchedule schedule = scheduleRepository.findById(1L).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())) return;

        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() != schedule.getCloseHour() || now.getMinute() != schedule.getCloseMinute()) return;
        if (now.toLocalDate().equals(lastRunDate)) return;
        lastRunDate = now.toLocalDate();

        closeAllOpenCuts();
        sendDailyReport();
    }

    private void closeAllOpenCuts() {
        List<CashCut> openCuts = cashCutRepository.findAllByStatus(CashCutStatus.OPEN);
        if (openCuts.isEmpty()) return;

        log.info("Cierre automático de cortes: {} corte(s) abierto(s) por cerrar", openCuts.size());
        for (CashCut cut : openCuts) {
            try {
                cashCutService.autoClose(cut.getId());
            } catch (Exception e) {
                log.error("No se pudo autocerrar el corte #{}: {}", cut.getId(), e.getMessage());
            }
        }
    }

    private void sendDailyReport() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<CashCut> closedToday = cashCutRepository.findAllByStatusAndOpenedAtBetween(
                CashCutStatus.CLOSED, startOfDay, endOfDay);
        reportNotifier.notify(closedToday);
    }
}
