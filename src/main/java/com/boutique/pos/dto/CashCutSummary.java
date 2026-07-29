package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CashCutSummary {
    private BigDecimal openingAmount;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal transferSales;
    private BigDecimal totalSales;
    private int totalTransactions;
    private int cancelledCount;
    private BigDecimal cancelledTotal;
}
