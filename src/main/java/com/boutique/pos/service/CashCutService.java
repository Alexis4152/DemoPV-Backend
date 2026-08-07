package com.boutique.pos.service;

import com.boutique.pos.dto.CashCutRequest;
import com.boutique.pos.dto.CashCutSummary;
import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.CashCutRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashCutService {

    private static final String ADMIN = "ADMIN";

    private final CashCutRepository cashCutRepository;
    private final SaleRepository saleRepository;
    private final TenantScope tenantScope;

    // "Mi" corte abierto ahora mismo — cada cajero/vendedor puede tener el suyo propio
    // abierto en simultáneo con los de sus compañeros de la misma tienda.
    public Optional<CashCut> findOpen(User actor) {
        return cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN);
    }

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService.findAll), así que en vez de
    // null se manda un rango que cubre todo el historial cuando no se filtra por fecha.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    public Page<CashCut> findAll(LocalDateTime from, LocalDateTime to, CashCutStatus status, Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return cashCutRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, status, pageable);
    }

    // El corte propio del día de hoy, abierto o ya cerrado — a diferencia de findOpen(),
    // esto no depende de que siga OPEN, así que sigue disponible justo después de cerrarlo.
    public Optional<CashCut> findMineToday(User actor) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return cashCutRepository.findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                actor.getId(), startOfDay, endOfDay);
    }

    public CashCut findById(Long id, User actor) {
        CashCut cut = findById(id);
        if (!canView(cut, actor)) {
            throw new IllegalArgumentException("Corte no encontrado: " + id);
        }
        return cut;
    }

    private CashCut findById(Long id) {
        return cashCutRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
    }

    // ADMIN ve cualquier corte de su tienda (para supervisar a todos sus cajeros a la vez);
    // SUPER_ADMIN ve todo; el resto solo ve el suyo propio del día de hoy.
    private boolean canView(CashCut cut, User actor) {
        if (tenantScope.isSuperAdmin(actor)) return true;
        if (ADMIN.equals(actor.getRole().getName())) {
            return actor.getTienda() == null ? cut.getTienda() == null
                    : actor.getTienda().getId().equals(cut.getTienda() != null ? cut.getTienda().getId() : null);
        }
        return cut.getUser().getId().equals(actor.getId())
                && cut.getOpenedAt().toLocalDate().equals(LocalDate.now());
    }

    // Cada cajero/vendedor abre y cierra el suyo, uno por día, con su propio fondo inicial —
    // ya no importa si alguien más de la misma tienda tiene el suyo abierto al mismo tiempo.
    @Transactional
    public CashCut open(CashCutRequest req, User actor) {
        if (!ADMIN.equals(actor.getRole().getName())) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            if (cashCutRepository.existsByUserIdAndOpenedAtBetween(actor.getId(), startOfDay, endOfDay)) {
                throw new IllegalStateException(
                        "Ya abriste un corte de caja hoy. Solo un administrador puede abrir varios cortes el mismo día.");
            }
        }
        CashCut cut = new CashCut();
        cut.setUser(actor);
        cut.setTienda(actor.getTienda());
        cut.setOpeningAmount(req.getAmount());
        cut.setStatus(CashCutStatus.OPEN);
        cut.setNotes(req.getNotes());
        return cashCutRepository.save(cut);
    }

    public CashCutSummary summary(Long id, User actor) {
        CashCut cut = findById(id, actor);
        List<Sale> sales = saleRepository.findByCashCutId(id);
        SalesTotals totals = sumSales(sales);
        return new CashCutSummary(cut.getOpeningAmount(), totals.cashSales, totals.cardSales, totals.transferSales,
                totals.totalSales, totals.transactionCount, totals.cancelledCount, totals.cancelledTotal);
    }

    // El aviso por correo NO se manda aquí: solo lo dispara el job programado
    // (CashCutAutoCloseJob), que junta en un solo reporte por tienda TODOS los
    // cortes del día — los cerrados a mano en cualquier momento y los que él mismo
    // cierra al llegar la hora configurada.
    @Transactional
    public CashCut close(Long id, CashCutRequest req, User actor) {
        CashCut cut = findById(id, actor);
        ensureOpen(cut);
        BigDecimal expenses = req.getExpenses() != null ? req.getExpenses() : BigDecimal.ZERO;
        return closeInternal(cut, expenses, req.getNotes(), actor);
    }

    // Usado por el job programado de cierre automático: mismo cálculo de totales que un
    // cierre manual, pero sin gastos capturados (nadie estuvo ahí para escribirlos) y con
    // una nota que deja claro que no lo cerró una persona. closedBy queda null a propósito
    // para que quede registrado que lo cerró el sistema, no un usuario.
    @Transactional
    public CashCut autoClose(Long id) {
        CashCut cut = findById(id);
        ensureOpen(cut);
        return closeInternal(cut, BigDecimal.ZERO, "Cerrado automáticamente por el sistema (corte del día).", null);
    }

    private void ensureOpen(CashCut cut) {
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }
    }

    private CashCut closeInternal(CashCut cut, BigDecimal expenses, String notes, User closedBy) {
        List<Sale> sales = saleRepository.findByCashCutId(cut.getId());
        SalesTotals totals = sumSales(sales);
        BigDecimal closingAmount = cut.getOpeningAmount().add(totals.cashSales).subtract(expenses);

        cut.setExpenses(expenses);
        cut.setClosingAmount(closingAmount);
        cut.setTotalSales(totals.totalSales);
        cut.setCashSales(totals.cashSales);
        cut.setCardSales(totals.cardSales);
        cut.setTransferSales(totals.transferSales);
        cut.setTotalTransactions(totals.transactionCount);
        cut.setCancelledCount(totals.cancelledCount);
        cut.setCancelledTotal(totals.cancelledTotal);
        cut.setStatus(CashCutStatus.CLOSED);
        cut.setClosedAt(LocalDateTime.now());
        cut.setClosedBy(closedBy);
        if (notes != null) cut.setNotes(notes);
        return cashCutRepository.save(cut);
    }

    private SalesTotals sumSales(List<Sale> sales) {
        SalesTotals totals = new SalesTotals();

        for (Sale s : sales) {
            if (s.getStatus() == SaleStatus.CANCELLED) {
                totals.cancelledCount++;
                totals.cancelledTotal = totals.cancelledTotal.add(s.getTotal());
                continue;
            }
            totals.transactionCount++;
            totals.totalSales = totals.totalSales.add(s.getTotal());
            if (s.getPaymentMethod() == PaymentMethod.CASH) totals.cashSales = totals.cashSales.add(s.getTotal());
            else if (s.getPaymentMethod() == PaymentMethod.CARD) totals.cardSales = totals.cardSales.add(s.getTotal());
            else if (s.getPaymentMethod() == PaymentMethod.TRANSFER) totals.transferSales = totals.transferSales.add(s.getTotal());
        }
        return totals;
    }

    private static class SalesTotals {
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal transferSales = BigDecimal.ZERO;
        int transactionCount = 0;
        int cancelledCount = 0;
        BigDecimal cancelledTotal = BigDecimal.ZERO;
    }
}
