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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashCutService {

    private final CashCutRepository cashCutRepository;
    private final SaleRepository saleRepository;

    public Optional<CashCut> findOpen() {
        return cashCutRepository.findFirstByStatus(CashCutStatus.OPEN);
    }

    public Page<CashCut> findAll(Pageable pageable) {
        return cashCutRepository.findAllByOrderByOpenedAtDesc(pageable);
    }

    public CashCut findById(Long id) {
        return cashCutRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
    }

    @Transactional
    public CashCut open(CashCutRequest req, User actor) {
        if (cashCutRepository.findFirstByStatus(CashCutStatus.OPEN).isPresent()) {
            throw new IllegalStateException("Ya hay un corte de caja abierto");
        }
        CashCut cut = new CashCut();
        cut.setUser(actor);
        cut.setOpeningAmount(req.getAmount());
        cut.setStatus(CashCutStatus.OPEN);
        cut.setNotes(req.getNotes());
        return cashCutRepository.save(cut);
    }

    public CashCutSummary summary(Long id) {
        CashCut cut = findById(id);
        List<Sale> sales = saleRepository.findByCashCutId(id);
        SalesTotals totals = sumSales(sales);
        return new CashCutSummary(cut.getOpeningAmount(), totals.cashSales, totals.cardSales, totals.transferSales,
                totals.totalSales, totals.transactionCount, totals.cancelledCount, totals.cancelledTotal);
    }

    @Transactional
    public CashCut close(Long id, CashCutRequest req, User actor) {
        CashCut cut = findById(id);
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }

        List<Sale> sales = saleRepository.findByCashCutId(id);
        SalesTotals totals = sumSales(sales);

        BigDecimal expenses = req.getExpenses() != null ? req.getExpenses() : BigDecimal.ZERO;
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
        cut.setNotes(req.getNotes() != null ? req.getNotes() : cut.getNotes());
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
