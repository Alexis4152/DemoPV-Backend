package com.boutique.pos.service;

import com.boutique.pos.dto.CashCutSummary;
import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.CashMovement;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.repository.CashMovementRepository;
import com.boutique.pos.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calcula los totales de un corte a partir de sus ventas y sus movimientos de efectivo, y
 * de ahí el efectivo esperado en caja. Compartido por {@link CashCutService} (resumen y
 * cierre), {@link CashMovementService} (tope de las salidas) y {@link CashRegisterService}
 * (efectivo esperado de las cajas en uso), para que los tres usen exactamente la misma fórmula:
 *
 * <pre>esperado = fondo inicial + ventas en efectivo + entradas − gastos − retiros − reembolsos</pre>
 *
 * Las ventas con tarjeta/transferencia no son dinero físico: se reportan pero no suman.
 */
@Component
@RequiredArgsConstructor
public class CashBalanceCalculator {

    private final SaleRepository saleRepository;
    private final CashMovementRepository movementRepository;

    /** Resumen completo (ventas por método, cancelaciones, movimientos y efectivo esperado). */
    public CashCutSummary summarize(CashCut cut) {
        BigDecimal cash = BigDecimal.ZERO, card = BigDecimal.ZERO, transfer = BigDecimal.ZERO, total = BigDecimal.ZERO;
        BigDecimal cancelledTotal = BigDecimal.ZERO;
        int transactions = 0, cancelledCount = 0;

        List<Sale> sales = saleRepository.findByCashCutId(cut.getId());
        for (Sale s : sales) {
            if (s.getStatus() == SaleStatus.CANCELLED) {
                cancelledCount++;
                cancelledTotal = cancelledTotal.add(s.getTotal());
                continue;
            }
            transactions++;
            total = total.add(s.getTotal());
            if (s.getPaymentMethod() == PaymentMethod.CASH) cash = cash.add(s.getTotal());
            else if (s.getPaymentMethod() == PaymentMethod.CARD) card = card.add(s.getTotal());
            else if (s.getPaymentMethod() == PaymentMethod.TRANSFER) transfer = transfer.add(s.getTotal());
        }

        BigDecimal entries = BigDecimal.ZERO, expenses = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO, reimbursements = BigDecimal.ZERO;
        for (CashMovement m : movementRepository.findByCashCut_IdOrderByCreatedAtAsc(cut.getId())) {
            switch (m.getType()) {
                case CONTRIBUTION, FLOAT -> entries = entries.add(m.getAmount());
                case EXPENSE -> expenses = expenses.add(m.getAmount());
                case WITHDRAWAL -> withdrawals = withdrawals.add(m.getAmount());
                case REIMBURSEMENT -> reimbursements = reimbursements.add(m.getAmount());
            }
        }

        BigDecimal opening = cut.getOpeningAmount() != null ? cut.getOpeningAmount() : BigDecimal.ZERO;
        BigDecimal expected = opening.add(cash).add(entries).subtract(expenses).subtract(withdrawals).subtract(reimbursements);

        return CashCutSummary.builder()
                .openingAmount(opening)
                .cashSales(cash).cardSales(card).transferSales(transfer).totalSales(total)
                .totalTransactions(transactions)
                .cancelledCount(cancelledCount).cancelledTotal(cancelledTotal)
                .entriesTotal(entries).expensesTotal(expenses).withdrawalsTotal(withdrawals).reimbursementsTotal(reimbursements)
                .expectedCash(expected)
                .build();
    }

    /** Solo el efectivo esperado en caja ahora mismo. */
    public BigDecimal expectedCash(CashCut cut) {
        return summarize(cut).getExpectedCash();
    }
}
