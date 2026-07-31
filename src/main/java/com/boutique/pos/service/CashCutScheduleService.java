package com.boutique.pos.service;

import com.boutique.pos.dto.CashCutScheduleRequest;
import com.boutique.pos.model.CashCutSchedule;
import com.boutique.pos.repository.CashCutScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashCutScheduleService {

    private final CashCutScheduleRepository scheduleRepository;

    public CashCutSchedule get() {
        return scheduleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("No se ha configurado el horario de cierre"));
    }

    public CashCutSchedule update(CashCutScheduleRequest req) {
        CashCutSchedule schedule = get();
        schedule.setCloseHour(req.getCloseHour());
        schedule.setCloseMinute(req.getCloseMinute());
        schedule.setEnabled(req.getEnabled());
        return scheduleRepository.save(schedule);
    }
}
