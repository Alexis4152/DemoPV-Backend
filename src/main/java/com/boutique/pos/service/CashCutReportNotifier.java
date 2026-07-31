package com.boutique.pos.service;

import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Llamado únicamente por el job programado (CashCutAutoCloseJob) una vez al día: agrupa
// TODOS los cortes cerrados del día por tienda y manda UN correo con Excel adjunto por
// cada tienda afectada, a su(s) admin(es). Se agrupa por el id de la tienda (no por el
// objeto Tienda) porque cada corte se cargó en su propia transacción y Tienda no tiene
// equals/hashCode propios — agrupar por objeto rompería el agrupado silenciosamente.
@Service
@RequiredArgsConstructor
@Slf4j
public class CashCutReportNotifier {

    private final CashCutReportExcelService excelService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public void notify(List<CashCut> closedCuts) {
        if (closedCuts.isEmpty()) return;

        Map<Long, List<CashCut>> byTiendaId = new LinkedHashMap<>();
        for (CashCut cut : closedCuts) {
            if (cut.getTienda() == null) continue;
            byTiendaId.computeIfAbsent(cut.getTienda().getId(), k -> new java.util.ArrayList<>()).add(cut);
        }

        for (Map.Entry<Long, List<CashCut>> entry : byTiendaId.entrySet()) {
            List<CashCut> cuts = entry.getValue();
            Tienda tienda = cuts.get(0).getTienda();
            List<User> admins = userRepository.findAdminsByTiendaId(tienda.getId());
            if (admins.isEmpty()) continue;

            byte[] excel;
            try {
                excel = excelService.build(tienda, cuts);
            } catch (RuntimeException e) {
                log.error("No se pudo generar el reporte de cierre para {}: {}", tienda.getName(), e.getMessage());
                continue;
            }

            for (User admin : admins) {
                emailService.sendCashCutReportEmail(tienda, cuts.size(), excel, admin.getEmail());
            }
        }
    }
}
