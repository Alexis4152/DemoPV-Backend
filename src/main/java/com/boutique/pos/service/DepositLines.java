package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoItem;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleKind;
import com.boutique.pos.repository.ApartadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La venta de un anticipo o abono tiene una sola línea: el pago a cuenta, no productos. Su
 * nombre es la leyenda "Anticipo del apartado #N" / "Abono al apartado #N" (nunca la lista de
 * productos concatenada); los productos son los de su {@link Apartado}, solo informativos.
 */
@Component
@RequiredArgsConstructor
public class DepositLines {

    private final ApartadoRepository apartadoRepository;

    public static boolean isDeposit(Sale sale) {
        return sale.getKind() == SaleKind.DEPOSIT;
    }

    /**
     * "Anticipo del apartado #N" o "Abono al apartado #N". Se calcula de la venta, no del nombre
     * guardado: las ventas anteriores guardaron "Anticipo — producto, producto, …".
     */
    public static String label(Sale sale) {
        boolean installment = Boolean.TRUE.equals(sale.getInstallment());
        String folio = sale.getApartadoFolio() != null ? " #" + sale.getApartadoFolio() : "";
        return (installment ? "Abono al apartado" : "Anticipo del apartado") + folio;
    }

    /** Nombre de una línea para mostrar: la leyenda si es de un anticipo/abono, si no su producto. */
    public static String itemName(Sale sale, String productName) {
        return isDeposit(sale) ? label(sale) : productName;
    }

    /** Productos del apartado de un anticipo/abono; vacío si la venta no es de uno. */
    public List<ApartadoItem> apartadoItems(Sale sale) {
        if (!isDeposit(sale) || sale.getApartadoId() == null) return List.of();
        return apartadoRepository.findById(sale.getApartadoId()).map(Apartado::getItems).orElse(List.of());
    }

    /** Los apartados de varias ventas de anticipo/abono, en un solo query, por id. */
    public Map<Long, Apartado> apartadosOf(Collection<Sale> sales) {
        List<Long> ids = sales.stream().filter(DepositLines::isDeposit).map(Sale::getApartadoId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return apartadoRepository.findAllById(ids).stream().collect(Collectors.toMap(Apartado::getId, Function.identity()));
    }
}
