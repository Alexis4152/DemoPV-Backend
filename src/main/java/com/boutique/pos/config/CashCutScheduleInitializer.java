package com.boutique.pos.config;

import com.boutique.pos.model.CashCutSchedule;
import com.boutique.pos.repository.CashCutScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Siembra la fila única (id=1) del horario de cierre automático de cortes de caja si no
// existe todavía. Queda deshabilitada (enabled=false) por default a propósito: el
// SUPER_ADMIN debe entrar y elegir la hora + activarla, no queremos que empiece a cerrar
// cortes reales solo porque alguien nunca la configuró.
@Component
@RequiredArgsConstructor
@Slf4j
public class CashCutScheduleInitializer implements CommandLineRunner {

    private final CashCutScheduleRepository scheduleRepository;

    @Override
    public void run(String... args) {
        if (scheduleRepository.existsById(1L)) return;
        CashCutSchedule schedule = CashCutSchedule.builder()
                .id(1L)
                .closeHour(23)
                .closeMinute(0)
                .enabled(false)
                .build();
        scheduleRepository.save(schedule);
        log.info("Horario de cierre automático de cortes sembrado (23:00, deshabilitado)");
    }
}
