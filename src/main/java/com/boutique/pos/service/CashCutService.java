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

/**
 * Administra el ciclo de vida de los cortes de caja ({@link CashCut}): apertura,
 * consulta, resumen y cierre (manual o automático).
 *
 * <p>Cada cajero/vendedor abre y cierra su propio corte — con su propio fondo inicial —
 * y puede haber varios cortes abiertos a la vez dentro de la misma tienda, uno por cada
 * usuario que esté trabajando. Un usuario normal solo puede tener un corte abierto por
 * día; solo un ADMIN puede abrir varios el mismo día. El cierre calcula los totales de
 * ventas (por método de pago), transacciones y cancelaciones a partir de las ventas
 * atadas a ese corte, y puede hacerse a mano ({@link #close}, con gastos capturados) o
 * automáticamente ({@link #autoClose}, disparado por el job programado, sin gastos y con
 * {@code closedBy} en null para dejar constancia de que lo cerró el sistema). El correo
 * con el reporte de cierre NO se envía desde aquí: lo dispara el job una sola vez al día
 * por tienda, agrupando todos los cortes cerrados ese día (ver {@link
 * CashCutReportNotifier}).</p>
 */
@Service
@RequiredArgsConstructor
public class CashCutService {

    private static final String ADMIN = "ADMIN";

    private final CashCutRepository cashCutRepository;
    private final SaleRepository saleRepository;
    private final TenantScope tenantScope;

    // "Mi" corte abierto ahora mismo — cada cajero/vendedor puede tener el suyo propio
    // abierto en simultáneo con los de sus compañeros de la misma tienda.
    /**
     * Obtiene el corte de caja actualmente abierto del actor, si tiene uno.
     *
     * @param actor usuario cuyo corte abierto se busca
     * @return el corte abierto del actor, o vacío si no tiene ninguno abierto
     */
    public Optional<CashCut> findOpen(User actor) {
        return cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN);
    }

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService.findAll), así que en vez de
    // null se manda un rango que cubre todo el historial cuando no se filtra por fecha.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /**
     * Búsqueda paginada de cortes de caja con filtros combinables, acotada a la tienda
     * del actor.
     *
     * @param from fecha/hora mínima de apertura (inclusiva); si es null se usa un límite
     *             inferior muy antiguo para evitar pasar null al BETWEEN de la consulta
     * @param to fecha/hora máxima de apertura (inclusiva); si es null se usa un límite
     *           superior muy lejano, por la misma razón
     * @param status filtro por estado (abierto/cerrado), o null
     * @param userId filtra al cajero dueño del corte, o null para no filtrar
     * @param pageable paginación y orden solicitados
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return página de cortes que cumplen los filtros
     */
    public Page<CashCut> findAll(LocalDateTime from, LocalDateTime to, CashCutStatus status, Long userId, Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return cashCutRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, status, userId, pageable);
    }

    /**
     * Cajeros distintos con al menos un corte de caja en la tienda del actor, para poblar
     * el filtro "Cajero" del historial (ver {@link CashCutRepository#findDistinctCashiers}).
     *
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return lista de {@code [id, nombre]} de cada cajero, ordenada por nombre
     */
    public List<Object[]> listCashiers(User actor) {
        return cashCutRepository.findDistinctCashiers(tenantScope.scopeId(actor));
    }

    // El corte propio del día de hoy, abierto o ya cerrado — a diferencia de findOpen(),
    // esto no depende de que siga OPEN, así que sigue disponible justo después de cerrarlo.
    /**
     * Obtiene el corte de caja propio del actor abierto hoy, sin importar si ya se cerró.
     *
     * @param actor usuario cuyo corte del día se busca
     * @return el corte del día del actor (abierto o cerrado), o vacío si no abrió ninguno hoy
     */
    public Optional<CashCut> findMineToday(User actor) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return cashCutRepository.findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                actor.getId(), startOfDay, endOfDay);
    }

    /**
     * Busca un corte de caja por id, validando que el actor tenga permiso para verlo
     * (ver {@link #canView(CashCut, User)}).
     *
     * @param id id del corte
     * @param actor usuario que consulta
     * @return el corte encontrado
     * @throws IllegalArgumentException si no existe o el actor no tiene permiso para verlo
     */
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
    /**
     * Determina si el actor tiene permiso para ver un corte de caja en particular, según
     * la regla descrita en el comentario anterior.
     *
     * @param cut corte a validar
     * @param actor usuario que intenta verlo
     * @return true si el actor puede ver ese corte
     */
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
    /**
     * Abre un corte de caja nuevo para el actor, con su fondo inicial.
     *
     * <p>Un usuario que no sea ADMIN solo puede abrir un corte por día; un ADMIN puede
     * abrir varios el mismo día (por ejemplo, para cubrir turnos o corregir un cierre
     * anterior).</p>
     *
     * @param req datos de apertura: monto del fondo inicial y notas opcionales
     * @param actor usuario que abre el corte; queda como dueño del corte y determina su tienda
     * @return el corte recién abierto, en estado {@code OPEN}
     * @throws IllegalStateException si el actor no es ADMIN y ya abrió un corte hoy
     */
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

    /**
     * Calcula el resumen en vivo de un corte (fondo inicial, ventas por método de pago,
     * total de ventas, transacciones y cancelaciones), a partir de las ventas atadas a
     * ese corte en este momento — sirve tanto para un corte todavía abierto (vista previa
     * antes de cerrar) como para uno ya cerrado.
     *
     * @param id id del corte
     * @param actor usuario que consulta; debe tener permiso para ver ese corte
     * @return resumen calculado del corte
     * @throws IllegalArgumentException si el corte no existe o el actor no tiene permiso para verlo
     */
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
    /**
     * Cierra manualmente un corte de caja: calcula sus totales y descuenta los gastos
     * capturados por el cajero al cerrar.
     *
     * <p>El correo con el reporte de cierre NO se envía aquí (ver nota de clase): solo
     * lo dispara el job programado, que agrupa este cierre con los demás del día de la
     * misma tienda.</p>
     *
     * @param id id del corte a cerrar
     * @param req gastos capturados al cierre y notas opcionales
     * @param actor usuario que cierra; debe tener permiso para ver/cerrar ese corte;
     *              queda registrado como {@code closedBy}
     * @return el corte ya cerrado, con sus totales calculados
     * @throws IllegalStateException si el corte ya estaba cerrado
     * @throws IllegalArgumentException si el corte no existe o el actor no tiene permiso
     */
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
    /**
     * Cierra automáticamente un corte que sigue abierto al llegar la hora configurada,
     * llamado exclusivamente por {@code CashCutAutoCloseJob}.
     *
     * <p>Usa el mismo cálculo de totales que un cierre manual, pero sin gastos
     * capturados (no hubo un cajero presente para registrarlos) y con {@code closedBy}
     * en null a propósito, para que quede registrado que lo cerró el sistema y no una
     * persona (el frontend lo muestra como "Sistema").</p>
     *
     * @param id id del corte a cerrar automáticamente
     * @return el corte ya cerrado
     * @throws IllegalStateException si el corte ya estaba cerrado
     * @throws IllegalArgumentException si el corte no existe
     */
    @Transactional
    public CashCut autoClose(Long id) {
        CashCut cut = findById(id);
        ensureOpen(cut);
        return closeInternal(cut, BigDecimal.ZERO, "Cerrado automáticamente por el sistema (corte del día).", null);
    }

    /**
     * Valida que un corte siga abierto antes de intentar cerrarlo.
     *
     * @param cut corte a validar
     * @throws IllegalStateException si el corte ya está cerrado
     */
    private void ensureOpen(CashCut cut) {
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }
    }

    /**
     * Lógica común de cierre compartida por {@link #close} y {@link #autoClose}: calcula
     * los totales de ventas del corte, obtiene el fondo final ({@code fondo inicial +
     * ventas en efectivo - gastos} — las ventas con tarjeta/transferencia no mueven el
     * efectivo en caja) y persiste el corte ya cerrado.
     *
     * @param cut corte a cerrar (debe estar {@code OPEN})
     * @param expenses gastos a descontar del fondo final ({@link BigDecimal#ZERO} en cierre automático)
     * @param notes notas a guardar; si es null se conservan las notas existentes del corte
     * @param closedBy usuario que cierra el corte, o null si lo cerró el sistema (cierre automático)
     * @return el corte ya cerrado y persistido
     */
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

    /**
     * Suma los totales de un conjunto de ventas de un corte, separando las ventas
     * completadas (contadas y sumadas por método de pago) de las canceladas (contadas y
     * sumadas aparte, sin afectar el total de ventas del corte).
     *
     * @param sales ventas del corte a sumar
     * @return totales agregados por método de pago, transacciones y cancelaciones
     */
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
