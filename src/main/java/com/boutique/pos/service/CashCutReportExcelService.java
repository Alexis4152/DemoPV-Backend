package com.boutique.pos.service;

import com.boutique.pos.dto.CashCutSummary;
import com.boutique.pos.repository.CashMovementRepository;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Arma el Excel (5 hojas: Cómo se calcula / Resumen / Ventas / Productos / Productos apartados) que se manda al admin de una tienda
// cada vez que se cierra uno o más cortes (a mano o por el job automático).
/**
 * Genera el reporte Excel (.xlsx, vía Apache POI) del cierre de caja diario de una
 * tienda, consumido por {@link CashCutReportNotifier} para adjuntarlo al correo que
 * reciben los administradores.
 *
 * <p>El libro tiene cinco hojas (la primera explica cómo se calcula el Resumen):</p>
 * <ul>
 *   <li><b>Resumen</b>: una columna por cada corte cerrado y otra con el total, con los
 *   bloques Ventas, Devoluciones, Utilidad y Efectivo en caja.</li>
 *   <li><b>Ventas</b>: totales por método de pago y el detalle de cada venta completada
 *   (se excluyen las canceladas), agrupadas por cajero, incluyendo el descuento total de
 *   cada venta, y al final las devoluciones hechas en el reporte de ventas de días anteriores.</li>
 *   <li><b>Productos</b>: el detalle línea por línea de cada producto vendido, con
 *   categoría, piezas, descuento aplicado a esa línea y subtotal — incluye una columna
 *   TIPO que marca en ámbar las líneas "por concepto" (no inventariadas, ver POS.jsx
 *   "📋 Por concepto"), para que no se confundan con ventas de catálogo real. Las ventas de un
 *   apartado (anticipo, abono, liquidación) no llevan filas aquí.</li>
 *   <li><b>Productos apartados</b>: un bloque por cada apartado hecho en el reporte, una sola
 *   vez (se toma de su anticipo, que es cuando bajan del inventario; los abonos no lo repiten),
 *   con su anticipo y, si se devolvió o canceló, lo que se regresó en negativo y lo retenido.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashCutReportExcelService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final SaleRepository saleRepository;
    private final DepositLines depositLines;
    private final CashBalanceCalculator calculator;
    private final CashMovementRepository movementRepository;

    /**
     * Construye el libro de Excel completo (Cómo se calcula, Resumen, Ventas, Productos, Productos apartados) para el
     * conjunto de cortes cerrados de una tienda.
     *
     * <p>Las ventas de cada corte se agrupan por cajero (el {@link User} dueño del
     * corte), incluyendo solo las ventas en estado {@code COMPLETED} — las canceladas no
     * entran a los totales ni al detalle.</p>
     *
     * @param tienda tienda a la que pertenece el reporte
     * @param cuts cortes cerrados a incluir en el reporte (de un mismo día, típicamente)
     * @param refundsSince desde cuándo buscar devoluciones con tarjeta o transferencia de ventas
     *                     de otros reportes (el fin de día anterior de la caja); null = no buscar
     * @return el archivo .xlsx generado, en bytes
     * @throws RuntimeException si Apache POI falla al construir o escribir el libro
     */
    public byte[] build(Tienda tienda, List<CashCut> cuts, LocalDateTime refundsSince) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle concept = conceptStyle(wb);

            buildComoSeCalcula(wb.createSheet("Cómo se calcula"), header, wb);
            Sheet resumen = wb.createSheet("Resumen");

            // las ventas y sus productos, agrupadas por cajero (una lista por cada corte que le pertenece)
            Map<User, List<Sale>> salesByCajero = new LinkedHashMap<>();
            for (CashCut cut : cuts) {
                List<Sale> sales = saleRepository.findByCashCutId(cut.getId()).stream()
                        .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                        .toList();
                salesByCajero.computeIfAbsent(cut.getUser(), k -> new ArrayList<>()).addAll(sales);
            }

            List<Sale> otherRefunds = refundsOfOtherSales(cuts, salesByCajero, refundsSince);
            buildResumen(resumen, resumenStyles(wb), tienda, cuts);
            buildVentas(wb.createSheet("Ventas"), header, bold, money, salesByCajero, otherRefunds);
            buildProductos(wb.createSheet("Productos"), header, bold, money, concept, tienda, salesByCajero);
            buildProductosApartados(wb.createSheet("Productos apartados"), header, bold, money, tienda, salesByCajero);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el reporte de cierre en Excel", e);
        }
    }

    /** Construye la hoja "Cómo se calcula": qué significa cada dato de la hoja Resumen. */
    private void buildComoSeCalcula(Sheet sheet, CellStyle header, Workbook wb) {
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        int r = 0;
        header(sheet, r++, header, "BLOQUE", "DATO", "CÓMO SE CALCULA");
        String[][] rows = {
                {"Columnas", "Turnos", "Cada turno del reporte es una columna, con su cajero y su horario."},
                {"Columnas", "TOTAL DEL DÍA", "Suma de los turnos, excepto en Efectivo en caja: Fondo inicial es el del primer turno; Efectivo esperado y Contado son los del último, porque lo que deja un turno es el fondo del siguiente."},
                {"Ventas", "Efectivo", "Lo cobrado en efectivo en el turno."},
                {"Ventas", "Tarjeta", "Lo cobrado con tarjeta en el turno."},
                {"Ventas", "Transferencia", "Lo cobrado por transferencia en el turno."},
                {"Ventas", "Total ventas", "Efectivo + Tarjeta + Transferencia. Incluye anticipos, abonos y liquidaciones de apartados. No cuenta las ventas canceladas ni resta las devoluciones."},
                {"Devoluciones", "Efectivo", "Efectivo devuelto a clientes en el turno, aunque la venta sea de otro día. Sale de la caja."},
                {"Devoluciones", "Tarjeta", "Lo devuelto con tarjeta en el turno, aunque la venta sea de otro día. No sale de la caja, así que no cambia el Efectivo esperado."},
                {"Devoluciones", "Transferencia", "Lo devuelto por transferencia en el turno, aunque la venta sea de otro día. No sale de la caja, así que no cambia el Efectivo esperado."},
                {"Devoluciones", "Total devoluciones", "Efectivo + Tarjeta + Transferencia."},
                {"Utilidad", "Gastos", "Efectivo que se pagó de la caja en gastos, incluidos los registrados antes de abrir el turno."},
                {"Utilidad", "Utilidad total", "Total ventas − Total devoluciones − Gastos."},
                {"Utilidad", "Utilidad en efectivo", "Ventas en efectivo − Devoluciones en efectivo − Gastos. No incluye tarjeta ni transferencia."},
                {"Efectivo en caja", "Fondo inicial", "Efectivo con el que abrió el turno."},
                {"Efectivo en caja", "+ Ventas en efectivo", "Lo cobrado en efectivo en el turno."},
                {"Efectivo en caja", "+ Entradas", "Efectivo que entró sin ser venta: aportes, entradas de cambio, pagos de faltantes y préstamos recibidos de otras cajas."},
                {"Efectivo en caja", "− Gastos", "Gastos pagados durante el turno. Los registrados antes de abrir ya no están en el Fondo inicial."},
                {"Efectivo en caja", "− Retiros", "Efectivo que se sacó de la caja (entregado al dueño, depósito bancario o caja fuerte)."},
                {"Efectivo en caja", "− Reembolsos", "Efectivo pagado de la caja para reembolsar un aporte."},
                {"Efectivo en caja", "− Préstamos a otras cajas", "Efectivo prestado a otra caja."},
                {"Efectivo en caja", "− Devoluciones en efectivo", "Efectivo devuelto a clientes en el turno."},
                {"Efectivo en caja", "Efectivo esperado", "Fondo inicial + Ventas en efectivo + Entradas − Gastos − Retiros − Reembolsos − Préstamos a otras cajas − Devoluciones en efectivo."},
                {"Efectivo en caja", "Contado", "Efectivo que se contó al cerrar el turno."},
                {"Efectivo en caja", "Diferencia", "Contado − Efectivo esperado. Negativo = faltó dinero; positivo = sobró."},
                {"Antes de abrir el turno", "Movimientos", "Gastos, retiros y entradas registrados con la caja cerrada. Ya están descontados del Fondo inicial, así que no cambian el Efectivo esperado."},
        };
        for (String[] data : rows) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < data.length; c++) {
                row.createCell(c).setCellValue(data[c]);
                cellStyle(row, c, wrap);
            }
        }
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 95 * 256);
    }

    /**
     * Construye la hoja "Resumen": los conceptos hacia abajo en cuatro bloques (Ventas,
     * Devoluciones, Utilidad, Efectivo en caja), una columna por corte y al final el total. Las
     * devoluciones con tarjeta o transferencia no salen de la caja (no tocan el efectivo
     * esperado), pero sí restan de la utilidad porque la venta sí la subió.
     *
     * @param sheet hoja de Excel a llenar
     * @param st estilos de la hoja
     * @param tienda tienda del reporte, para el título
     * @param cuts cortes cerrados a listar, del más antiguo al más reciente
     */
    private void buildResumen(Sheet sheet, ResumenStyles st, Tienda tienda, List<CashCut> cuts) {
        Map<Long, CashCutSummary> summaries = new HashMap<>();
        for (CashCut cut : cuts) summaries.put(cut.getId(), calculator.summarize(cut));
        Function<CashCut, BigDecimal> card = c -> nz(summaries.get(c.getId()).getCardRefunds());
        Function<CashCut, BigDecimal> transfer = c -> nz(summaries.get(c.getId()).getTransferRefunds());
        Function<CashCut, BigDecimal> refunds = c -> nz(c.getRefundsTotal()).add(card.apply(c)).add(transfer.apply(c));
        Map<Long, List<CashMovement>> beforeOpening = new HashMap<>();
        for (CashCut cut : cuts) beforeOpening.put(cut.getId(), movementRepository.findByReceivedByCutIdOrderByCreatedAtAsc(cut.getId()));
        // Un gasto antes de abrir sí baja la utilidad, aunque ya no pase por el efectivo del turno.
        Function<CashCut, BigDecimal> expenses = c -> beforeOpening.get(c.getId()).stream()
                .filter(m -> m.getType() == CashMovementType.EXPENSE).map(CashMovement::getAmount)
                .reduce(nz(c.getExpenses()), BigDecimal::add);

        int r = 0;
        Row title = sheet.createRow(r++);
        title.createCell(0).setCellValue("CORTE DE CAJA");
        cellStyle(title, 0, st.title());
        List<String> registers = cuts.stream().map(this::registerName).distinct().toList();
        row(sheet, r++, st.label(), "Tienda", tienda.getName());
        row(sheet, r++, st.label(), "Caja", String.join(", ", registers));
        row(sheet, r++, st.label(), "Día", reportDay(cuts));
        r++;

        Row head = sheet.createRow(r++);
        head.setHeightInPoints(48);
        head.createCell(0).setCellValue("CONCEPTO");
        cellStyle(head, 0, st.headLeft());
        for (int i = 0; i < cuts.size(); i++) {
            CashCut cut = cuts.get(i);
            String caja = registers.size() > 1 ? registerName(cut) + " · " : "";
            head.createCell(i + 1).setCellValue(caja + "TURNO " + (i + 1) + "\n" + cut.getUser().getName() + "\n" + turnHours(cut));
            cellStyle(head, i + 1, st.headRight());
        }
        int totalCol = cuts.size() + 1;
        head.createCell(totalCol).setCellValue("TOTAL DEL DÍA");
        cellStyle(head, totalCol, st.headRight());
        sheet.createFreezePane(1, r);

        r = section(sheet, r, st, "VENTAS", totalCol);
        r = line(sheet, r, st, false, "Efectivo", cuts, CashCut::getCashSales);
        r = line(sheet, r, st, false, "Tarjeta", cuts, CashCut::getCardSales);
        r = line(sheet, r, st, false, "Transferencia", cuts, CashCut::getTransferSales);
        r = line(sheet, r, st, true, "Total ventas", cuts, CashCut::getTotalSales) + 1;

        r = section(sheet, r, st, "DEVOLUCIONES", totalCol);
        r = line(sheet, r, st, false, "Efectivo", cuts, CashCut::getRefundsTotal);
        r = line(sheet, r, st, false, "Tarjeta", cuts, card);
        r = line(sheet, r, st, false, "Transferencia", cuts, transfer);
        r = line(sheet, r, st, true, "Total devoluciones", cuts, refunds) + 1;

        r = section(sheet, r, st, "UTILIDAD", totalCol);
        r = line(sheet, r, st, false, "Gastos", cuts, expenses);
        r = line(sheet, r, st, true, "Utilidad total", cuts,
                c -> nz(c.getTotalSales()).subtract(refunds.apply(c)).subtract(expenses.apply(c)));
        r = line(sheet, r, st, true, "Utilidad en efectivo", cuts,
                c -> nz(c.getCashSales()).subtract(nz(c.getRefundsTotal())).subtract(expenses.apply(c))) + 1;

        // Lo que deja un turno es el fondo del siguiente: en el total cuenta el primero y el
        // último de cada caja (los cortes sin caja no se encadenan: cada uno va por su lado).
        Map<Object, CashCut> firstOfRegister = new LinkedHashMap<>();
        Map<Object, CashCut> lastOfRegister = new LinkedHashMap<>();
        for (CashCut cut : cuts) {
            Object key = cut.getCashRegister() != null ? cut.getCashRegister().getId() : "corte-" + cut.getId();
            firstOfRegister.putIfAbsent(key, cut);
            lastOfRegister.put(key, cut);
        }

        r = section(sheet, r, st, "EFECTIVO EN CAJA", totalCol);
        r = line(sheet, r, st, false, "Fondo inicial", cuts, CashCut::getOpeningAmount, sum(firstOfRegister.values(), CashCut::getOpeningAmount));
        r = line(sheet, r, st, false, "+ Ventas en efectivo", cuts, CashCut::getCashSales);
        r = line(sheet, r, st, false, "+ Entradas", cuts, CashCut::getEntriesTotal);
        r = line(sheet, r, st, false, "− Gastos", cuts, c -> nz(c.getExpenses()).negate());
        r = line(sheet, r, st, false, "− Retiros", cuts, c -> nz(c.getWithdrawalsTotal()).negate());
        r = line(sheet, r, st, false, "− Reembolsos", cuts, c -> nz(c.getReimbursementsTotal()).negate());
        r = line(sheet, r, st, false, "− Préstamos a otras cajas", cuts, c -> nz(c.getLoansOutTotal()).negate());
        r = line(sheet, r, st, false, "− Devoluciones en efectivo", cuts, c -> nz(c.getRefundsTotal()).negate());
        r = line(sheet, r, st, true, "Efectivo esperado", cuts, CashCut::getClosingAmount, sum(lastOfRegister.values(), CashCut::getClosingAmount));

        Row counted = sheet.createRow(r++);
        counted.createCell(0).setCellValue("Contado");
        for (int i = 0; i < cuts.size(); i++) countedCell(counted, i + 1, st.money(), cuts.get(i).getCountedAmount());
        boolean allCounted = lastOfRegister.values().stream().allMatch(c -> c.getCountedAmount() != null);
        countedCell(counted, totalCol, st.moneyBold(), allCounted ? sum(lastOfRegister.values(), CashCut::getCountedAmount) : null);
        r = line(sheet, r, st, true, "Diferencia", cuts, CashCut::getCashDifference) + 1;

        if (beforeOpening.values().stream().anyMatch(list -> !list.isEmpty())) {
            r = section(sheet, r, st, "ANTES DE ABRIR EL TURNO", totalCol);
            for (CashCut cut : cuts) {
                for (CashMovement m : beforeOpening.get(cut.getId())) {
                    BigDecimal signed = m.getType().isOutflow() ? m.getAmount().negate() : m.getAmount();
                    r = line(sheet, r, st, false, beforeOpeningLabel(m), cuts, c -> c == cut ? signed : BigDecimal.ZERO);
                    cellStyle(sheet.getRow(r - 1), 0, st.wrap());
                }
            }
        }

        sheet.setColumnWidth(0, 32 * 256);
        for (int c = 1; c <= totalCol; c++) sheet.setColumnWidth(c, 30 * 256);
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 1);
    }

    /** Qué fue el movimiento, a dónde fue, quién y a qué hora. */
    private String beforeOpeningLabel(CashMovement m) {
        String what = switch (m.getType()) {
            case WITHDRAWAL -> "Retiro · " + m.getDestination().describe(m.getDestinationOther());
            case EXPENSE -> "Gasto · " + m.getConcept();
            default -> "Entrada" + (m.getConcept() != null ? " · " + m.getConcept() : "");
        };
        String person = m.getPerson() != null ? m.getPerson().getName() : m.getRegisteredBy().getName();
        LocalDateTime when = m.getOccurredAt() != null ? m.getOccurredAt() : m.getCreatedAt();
        return what + " · " + person + " · " + when.format(HOUR);
    }

    /** Título de un bloque del Resumen, con su franja a todo lo ancho. */
    private int section(Sheet sheet, int r, ResumenStyles st, String label, int totalCol) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        for (int c = 0; c <= totalCol; c++) cellStyle(row, c, st.section());
        return r + 1;
    }

    /** Renglón del Resumen cuyo total es la suma de los turnos. */
    private int line(Sheet sheet, int r, ResumenStyles st, boolean strong, String label,
                     List<CashCut> cuts, Function<CashCut, BigDecimal> value) {
        return line(sheet, r, st, strong, label, cuts, value, sum(cuts, value));
    }

    /** Renglón del Resumen: la etiqueta, el valor de cada turno y el total. @return el siguiente renglón */
    private int line(Sheet sheet, int r, ResumenStyles st, boolean strong, String label,
                     List<CashCut> cuts, Function<CashCut, BigDecimal> value, BigDecimal total) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        if (strong) cellStyle(row, 0, st.strongLabel());
        for (int i = 0; i < cuts.size(); i++) {
            moneyCell(row, i + 1, strong ? st.strongMoney() : st.money(), value.apply(cuts.get(i)));
        }
        moneyCell(row, cuts.size() + 1, strong ? st.strongMoney() : st.moneyBold(), total);
        return r + 1;
    }

    private BigDecimal sum(Collection<CashCut> cuts, Function<CashCut, BigDecimal> value) {
        return cuts.stream().map(value).map(this::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void countedCell(Row row, int col, CellStyle style, BigDecimal amount) {
        if (amount != null) moneyCell(row, col, style, amount);
        else row.createCell(col).setCellValue("No contado");
    }

    private String registerName(CashCut cut) {
        return cut.getCashRegister() != null ? cut.getCashRegister().getName() : "—";
    }

    /** El día del reporte, o el rango si sus turnos abarcan más de uno. */
    private String reportDay(List<CashCut> cuts) {
        LocalDate from = cuts.get(0).getOpenedAt().toLocalDate();
        LocalDateTime closed = cuts.get(cuts.size() - 1).getClosedAt();
        LocalDate to = closed != null ? closed.toLocalDate() : from;
        return from.equals(to) ? from.format(DAY) : from.format(DAY) + " a " + to.format(DAY);
    }

    /** Horario del turno; con fechas si no abrió y cerró el mismo día. */
    private String turnHours(CashCut cut) {
        LocalDateTime opened = cut.getOpenedAt(), closed = cut.getClosedAt();
        if (closed == null) return opened.format(FMT);
        return opened.toLocalDate().equals(closed.toLocalDate())
                ? opened.format(HOUR) + " a " + closed.format(HOUR)
                : opened.format(FMT) + " a " + closed.format(FMT);
    }

    /**
     * Construye la hoja "Ventas": primero un mini-resumen de totales por método de pago
     * (efectivo/tarjeta/transferencia), alineado con la columna CONCEPTO para que la primera
     * columna quede angosta, y luego el detalle fila por fila de cada venta
     * completada, agrupadas por cajero. Las devoluciones de ventas anteriores van en la misma
     * tabla, sin DESCUENTO ni TOTAL porque esa venta ya se contó el día que se hizo: así cada
     * columna suma lo mismo que su renglón del mini-resumen.
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas
     * @param money estilo de formato de moneda
     * @param salesByCajero ventas completadas de todos los cortes, agrupadas por cajero
     * @param otherRefunds ventas de otros reportes devueltas en este
     */
    private void buildVentas(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                              Map<User, List<Sale>> salesByCajero, List<Sale> otherRefunds) {
        int r = 0;

        Map<PaymentMethod, long[]> counts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> totals = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod pm : PaymentMethod.values()) {
            counts.put(pm, new long[]{0});
            totals.put(pm, BigDecimal.ZERO);
        }
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal grandRefunded = BigDecimal.ZERO;
        for (List<Sale> sales : salesByCajero.values()) {
            for (Sale s : sales) {
                counts.get(s.getPaymentMethod())[0]++;
                totals.put(s.getPaymentMethod(), totals.get(s.getPaymentMethod()).add(s.getTotal()));
                grandTotal = grandTotal.add(s.getTotal());
                grandRefunded = grandRefunded.add(refundedOf(s));
            }
        }
        for (Sale s : otherRefunds) grandRefunded = grandRefunded.add(refundedOf(s));
        r += 2;
        for (PaymentMethod pm : PaymentMethod.values()) {
            Row row = sheet.createRow(r++);
            row.createCell(2).setCellValue(spanishMethod(pm));
            row.createCell(3).setCellValue(counts.get(pm)[0]);
            moneyCell(row, 4, money, totals.get(pm));
        }
        List<Sale> refunded = Stream.concat(salesByCajero.values().stream().flatMap(List::stream), otherRefunds.stream())
                .filter(s -> refundedOf(s).signum() > 0).toList();
        refundRow(sheet, r++, money, "DEVOLUCIONES EN EFECTIVO", PaymentMethod.CASH, refunded);
        refundRow(sheet, r++, money, "DEVOLUCIONES EN TARJETA", PaymentMethod.CARD, refunded);
        refundRow(sheet, r++, money, "DEVOLUCIONES EN TRANSFERENCIA", PaymentMethod.TRANSFER, refunded);
        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(2).setCellValue("TOTAL");
        cellStyle(totalRow, 2, bold);
        moneyCell(totalRow, 4, money, grandTotal);
        Row refundedRow = sheet.createRow(r++);
        refundedRow.createCell(2).setCellValue("DEVUELTO");
        moneyCell(refundedRow, 4, money, grandRefunded.negate());
        Row netRow = sheet.createRow(r++);
        netRow.createCell(2).setCellValue("NETO");
        cellStyle(netRow, 2, bold);
        moneyCell(netRow, 4, money, grandTotal.subtract(grandRefunded));
        r += 2;

        header(sheet, r++, header, "FOLIO_VENTA", "FOLIO_APARTADO", "CONCEPTO", "FECHA VENTA", "FECHA DEVOLUCION", "CLIENTE", "METODO", "VENDEDOR", "DESCUENTO", "TOTAL", "DEVUELTO", "NETO");
        for (Map.Entry<User, List<Sale>> entry : salesByCajero.entrySet()) {
            for (Sale s : entry.getValue()) {
                Row row = ventaRow(sheet, r++, money, s, entry.getKey().getName());
                moneyCell(row, 8, money, s.getDiscount());
                moneyCell(row, 9, money, s.getTotal());
                moneyCell(row, 11, money, s.getTotal().subtract(refundedOf(s)));
            }
        }
        for (Sale s : otherRefunds) {
            Row row = ventaRow(sheet, r++, money, s, s.getUser().getName());
            moneyCell(row, 11, money, refundedOf(s).negate());
        }
        autosize(sheet, 12);
    }

    /** Renglón del mini-resumen de Ventas: cuántas devoluciones hubo con un método de pago y cuánto sumaron. */
    private void refundRow(Sheet sheet, int r, CellStyle money, String label, PaymentMethod method, List<Sale> refunded) {
        List<Sale> sales = refunded.stream().filter(s -> s.getPaymentMethod() == method).toList();
        Row row = sheet.createRow(r);
        row.createCell(2).setCellValue(label);
        row.createCell(3).setCellValue(sales.size());
        moneyCell(row, 4, money, sales.stream().map(this::refundedOf).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Fila de la hoja Ventas con los datos de la venta y lo devuelto; los importes los pone quien llama. */
    private Row ventaRow(Sheet sheet, int r, CellStyle money, Sale s, String vendedor) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(s.getFolio());
        apartadoCell(row, 1, s);
        row.createCell(2).setCellValue(saleConcept(s));
        row.createCell(3).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "—");
        if (s.getRefundedAt() != null) row.createCell(4).setCellValue(s.getRefundedAt().format(FMT));
        row.createCell(5).setCellValue(s.getCustomerName() != null && !s.getCustomerName().isBlank() ? s.getCustomerName() : "—");
        row.createCell(6).setCellValue(spanishMethod(s.getPaymentMethod()));
        row.createCell(7).setCellValue(vendedor);
        moneyCell(row, 10, money, refundedOf(s).negate());
        return row;
    }

    /**
     * Construye la hoja "Productos": una fila por cada línea de producto vendida (no por
     * venta), con su categoría, cantidad y subtotal, más el gran total al final.
     * <p>
     * La columna TIPO distingue las líneas "por concepto" (ver {@code SaleItemRequest} /
     * POS.jsx "📋 Por concepto") de las de catálogo normal: {@code item.getProduct() ==
     * null} marca algo que el cajero vendió a mano (ej. "2 jitomates") y que nunca estuvo
     * en el inventario — sin esta columna, quien revisa el Excel no tiene forma de saber
     * que esas piezas no van a cuadrar contra existencias.
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas
     * @param money estilo de formato de moneda
     * @param concept estilo distintivo (relleno ámbar) para las líneas "por concepto"
     * @param tienda tienda del reporte, repetida en cada fila
     * @param salesByCajero ventas completadas de todos los cortes, agrupadas por cajero
     */
    private void buildProductos(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money, CellStyle concept,
                                 Tienda tienda, Map<User, List<Sale>> salesByCajero) {
        int r = 0;
        header(sheet, r++, header, "FOLIO_VENTA", "FECHA", "PRODUCTO", "TIPO", "CATEGORIA", "PIEZAS VENDIDAS", "VENDEDOR", "TIENDA", "DESCUENTO", "SUBTOTAL");

        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;
        for (Map.Entry<User, List<Sale>> entry : salesByCajero.entrySet()) {
            for (Sale s : entry.getValue()) {
                // Lo de un apartado (anticipo, abono, liquidación) va en "Productos apartados".
                if (s.getApartadoId() != null) continue;
                for (SaleItem item : s.getItems()) {
                    boolean isConcept = item.getProduct() == null;
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(s.getFolio());
                    row.createCell(1).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "—");
                    row.createCell(2).setCellValue(item.getProductName());
                    Cell tipoCell = row.createCell(3);
                    tipoCell.setCellValue(isConcept ? "POR CONCEPTO (no inventariado)" : "Catálogo");
                    if (isConcept) tipoCell.setCellStyle(concept);
                    row.createCell(4).setCellValue(!isConcept && item.getProduct().getCategory() != null
                            ? item.getProduct().getCategory().getName() : "—");
                    row.createCell(5).setCellValue(item.getQuantity().doubleValue());
                    row.createCell(6).setCellValue(entry.getKey().getName());
                    row.createCell(7).setCellValue(tienda.getName());
                    moneyCell(row, 8, money, item.getDiscount());
                    moneyCell(row, 9, money, item.getSubtotal());
                    grandTotal = grandTotal.add(item.getSubtotal());
                    grandDiscount = grandDiscount.add(nz(item.getDiscount()));
                }
            }
        }
        Row totalRow = sheet.createRow(r);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 8, money, grandDiscount);
        moneyCell(totalRow, 9, money, grandTotal);

        autosize(sheet, 10);
    }

    /**
     * Construye la hoja "Productos apartados": un bloque por cada apartado hecho en el reporte.
     * Se recorren solo los anticipos porque cada apartado tiene uno y es cuando sus productos
     * bajan del inventario; así ningún apartado se repite por sus abonos, y los que se liquidan
     * hoy pero se hicieron otro día no aparecen.
     *
     * <p>El TOTAL es lo que la tienda conserva de esos apartados: el valor de lo que sigue apartado
     * o entregado, más lo retenido de los devueltos o cancelados (su valor menos lo que no se quedó
     * la tienda). El anticipo es informativo y no suma.</p>
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas (títulos y totales)
     * @param money estilo de formato de moneda
     * @param tienda tienda del reporte, repetida en cada fila
     * @param salesByCajero ventas completadas de todos los cortes, agrupadas por cajero
     */
    private void buildProductosApartados(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                                          Tienda tienda, Map<User, List<Sale>> salesByCajero) {
        int r = 0;
        List<Sale> allSales = salesByCajero.values().stream().flatMap(List::stream).toList();
        Map<Long, Apartado> apartados = depositLines.apartadosOf(allSales);
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Map.Entry<User, List<Sale>> entry : salesByCajero.entrySet()) {
            for (Sale s : entry.getValue()) {
                if (!DepositLines.isDeposit(s) || Boolean.TRUE.equals(s.getInstallment())) continue;
                Apartado apartado = apartados.get(s.getApartadoId());
                if (apartado == null) continue;

                Row title = sheet.createRow(r++);
                title.createCell(1).setCellValue("Apartado #" + apartado.getFolio());
                cellStyle(title, 1, bold);
                title.createCell(8).setCellValue(apartadoEstado(apartado));
                cellStyle(title, 8, bold);
                header(sheet, r++, header, "", "FECHA", "CLIENTE", "PRODUCTO", "TIPO", "CATEGORIA", "PIEZAS", "VENDEDOR", "TIENDA", "PRECIO", "SUBTOTAL");

                BigDecimal apartadoTotal = BigDecimal.ZERO;
                for (ApartadoItem ai : apartado.getItems()) {
                    Product product = ai.getProduct();
                    Row row = sheet.createRow(r++);
                    row.createCell(1).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "—");
                    row.createCell(2).setCellValue(apartado.getCustomerName() != null && !apartado.getCustomerName().isBlank() ? apartado.getCustomerName() : "—");
                    row.createCell(3).setCellValue(ai.getProductName());
                    row.createCell(4).setCellValue(product == null ? "Por concepto"
                            : Boolean.TRUE.equals(ai.getOnOrder()) ? "Sobre pedido"
                            : Boolean.TRUE.equals(product.getIsMadeToOrder()) ? "Por pedido" : "Catálogo");
                    row.createCell(5).setCellValue(product != null && product.getCategory() != null ? product.getCategory().getName() : "—");
                    row.createCell(6).setCellValue(ai.getQuantity().doubleValue());
                    row.createCell(7).setCellValue(entry.getKey().getName());
                    row.createCell(8).setCellValue(tienda.getName());
                    moneyCell(row, 9, money, ai.getUnitPrice());
                    moneyCell(row, 10, money, ai.getSubtotal());
                    apartadoTotal = apartadoTotal.add(ai.getSubtotal());
                }

                apartadoLine(sheet, r++, bold, money, "ANTICIPO DE", s.getTotal());
                boolean undone = apartado.getRefundedAt() != null || apartado.getStatus() == ApartadoStatus.CANCELLED;
                if (undone) {
                    BigDecimal retained = nz(apartado.getRetainedAmount());
                    apartadoLine(sheet, r++, bold, money, apartado.getRefundedAt() != null ? "DEVUELTO" : "CANCELADO", retained.subtract(apartadoTotal));
                    if (retained.signum() > 0) apartadoLine(sheet, r++, bold, money, "RETENIDO", retained);
                    grandTotal = grandTotal.add(retained);
                } else {
                    grandTotal = grandTotal.add(apartadoTotal);
                }
                r++;
            }
        }
        Row totalRow = sheet.createRow(r);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 10, money, grandTotal);

        autosize(sheet, 11);
    }

    /** Línea al pie del bloque de un apartado: etiqueta en la columna PRECIO, monto en SUBTOTAL. */
    private void apartadoLine(Sheet sheet, int r, CellStyle bold, CellStyle money, String label, BigDecimal amount) {
        Row row = sheet.createRow(r);
        row.createCell(9).setCellValue(label);
        cellStyle(row, 9, bold);
        moneyCell(row, 10, money, amount);
    }

    /**
     * Traduce el método de pago al español (en mayúsculas) para las hojas del reporte.
     *
     * @param pm método de pago
     * @return texto en español
     */
    private String spanishMethod(PaymentMethod pm) {
        return switch (pm) {
            case CASH -> "EFECTIVO";
            case CARD -> "TARJETA";
            case TRANSFER -> "TRANSFERENCIAS";
        };
    }

    /** Estado actual del apartado, en español; uno devuelto sigue COMPLETED, marcado con su fecha. */
    private String apartadoEstado(Apartado apartado) {
        if (apartado.getRefundedAt() != null) return "Devuelto";
        return switch (apartado.getStatus()) {
            case PENDING -> "Pendiente";
            case ACTIVE -> "Activo";
            case COMPLETED -> "Liquidado";
            case CANCELLED -> "Cancelado";
            case EXPIRED -> "Vencido";
        };
    }

    /** Folio del apartado de la venta (anticipo, abono o liquidación), o vacío si no es de uno. */
    private void apartadoCell(Row row, int col, Sale s) {
        Cell cell = row.createCell(col);
        if (s.getApartadoFolio() != null) cell.setCellValue(s.getApartadoFolio());
    }

    /**
     * Ventas de otros reportes devueltas en este: en efectivo, las que salieron de estos cortes;
     * con tarjeta o transferencia no pasan por una caja, así que se toman las de ventas de esta
     * caja devueltas desde su fin de día anterior (cada una cae en un solo reporte).
     */
    private List<Sale> refundsOfOtherSales(List<CashCut> cuts, Map<User, List<Sale>> salesByCajero, LocalDateTime since) {
        Set<Long> listed = salesByCajero.values().stream().flatMap(List::stream).map(Sale::getId).collect(Collectors.toSet());
        List<Sale> refunds = new ArrayList<>(saleRepository.findByRefundCashCutIdIn(cuts.stream().map(CashCut::getId).toList()));
        CashRegister register = cuts.get(0).getCashRegister();
        if (register != null && since != null) refunds.addAll(saleRepository.findNonCashRefundedSince(register.getId(), since));
        return refunds.stream().filter(s -> s.getStatus() == SaleStatus.COMPLETED && !listed.contains(s.getId())).toList();
    }

    /** Concepto de una venta en la hoja Ventas. */
    private String saleConcept(Sale s) {
        return DepositLines.isDeposit(s) ? DepositLines.label(s)
                : s.getApartadoFolio() != null ? "Liquidación del apartado #" + s.getApartadoFolio() : "Venta";
    }

    /** Lo que se le devolvió al cliente de una venta: parcial si se guardó el monto, si no todo. */
    private BigDecimal refundedOf(Sale s) {
        if (s.getRefundedAt() == null) return BigDecimal.ZERO;
        return s.getRefundAmount() != null ? s.getRefundAmount() : s.getTotal();
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void row(Sheet sheet, int r, CellStyle style, String label, String value) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        cellStyle(row, 0, style);
        row.createCell(1).setCellValue(value);
    }

    private void header(Sheet sheet, int r, CellStyle style, String... labels) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private void moneyCell(Row row, int col, CellStyle style, BigDecimal value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private void cellStyle(Row row, int col, CellStyle style) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellStyle(style);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle boldStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        return s;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }

    /** Estilos de la hoja Resumen. */
    private record ResumenStyles(CellStyle title, CellStyle label, CellStyle headLeft, CellStyle headRight, CellStyle section,
                                 CellStyle money, CellStyle moneyBold, CellStyle strongLabel, CellStyle strongMoney, CellStyle wrap) {
    }

    private ResumenStyles resumenStyles(XSSFWorkbook wb) {
        short format = wb.createDataFormat().getFormat("$#,##0.00;[Red]-$#,##0.00");
        Font normalBold = wb.createFont();
        normalBold.setBold(true);
        Font big = wb.createFont();
        big.setBold(true);
        big.setFontHeightInPoints((short) 14);
        Font white = wb.createFont();
        white.setBold(true);
        white.setColor(IndexedColors.WHITE.getIndex());

        CellStyle title = wb.createCellStyle();
        title.setFont(big);
        CellStyle label = wb.createCellStyle();
        label.setFont(normalBold);

        XSSFCellStyle headLeft = filled(wb, 0x37, 0x41, 0x51);
        headLeft.setFont(white);
        headLeft.setVerticalAlignment(VerticalAlignment.CENTER);
        headLeft.setWrapText(true);
        XSSFCellStyle headRight = filled(wb, 0x37, 0x41, 0x51);
        headRight.setFont(white);
        headRight.setVerticalAlignment(VerticalAlignment.CENTER);
        headRight.setAlignment(HorizontalAlignment.RIGHT);
        headRight.setWrapText(true);

        XSSFCellStyle section = filled(wb, 0xE5, 0xE7, 0xEB);
        section.setFont(normalBold);

        CellStyle money = wb.createCellStyle();
        money.setDataFormat(format);
        CellStyle moneyBold = wb.createCellStyle();
        moneyBold.setDataFormat(format);
        moneyBold.setFont(normalBold);

        XSSFCellStyle strongLabel = filled(wb, 0xF3, 0xF4, 0xF6);
        strongLabel.setFont(normalBold);
        strongLabel.setBorderTop(BorderStyle.THIN);
        XSSFCellStyle strongMoney = filled(wb, 0xF3, 0xF4, 0xF6);
        strongMoney.setFont(normalBold);
        strongMoney.setBorderTop(BorderStyle.THIN);
        strongMoney.setDataFormat(format);
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        return new ResumenStyles(title, label, headLeft, headRight, section, money, moneyBold, strongLabel, strongMoney, wrap);
    }

    private XSSFCellStyle filled(XSSFWorkbook wb, int red, int green, int blue) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) red, (byte) green, (byte) blue}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    /** Relleno ámbar + negritas para la columna TIPO de una línea "por concepto" (hoja Productos). */
    private CellStyle conceptStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
