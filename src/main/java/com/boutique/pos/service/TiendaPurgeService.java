package com.boutique.pos.service;

import com.boutique.pos.model.CashCutSchedule;
import com.boutique.pos.model.MailConfig;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.ApartadoRepository;
import com.boutique.pos.repository.ArcoRequestRepository;
import com.boutique.pos.repository.CashCutRepository;
import com.boutique.pos.repository.CashCutScheduleRepository;
import com.boutique.pos.repository.CashMovementRepository;
import com.boutique.pos.repository.CashRegisterRepository;
import com.boutique.pos.repository.CashReportRecipientRepository;
import com.boutique.pos.repository.CategoryRepository;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.MailConfigRepository;
import com.boutique.pos.repository.OfferRepository;
import com.boutique.pos.repository.PasswordResetTokenRepository;
import com.boutique.pos.repository.ProductImageRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.repository.RawMaterialMovementRepository;
import com.boutique.pos.repository.RawMaterialRepository;
import com.boutique.pos.repository.ReimbursementRepository;
import com.boutique.pos.repository.RefreshTokenRepository;
import com.boutique.pos.repository.RoleRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.repository.ShortagePaymentRepository;
import com.boutique.pos.repository.ShortageRepository;
import com.boutique.pos.repository.TiendaInfoRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrado FÍSICO y total de una {@link Tienda}: a diferencia de {@link TiendaService#deactivate},
 * que solo la marca {@code isActive=false} sin tocar ningún dato, este servicio elimina de
 * verdad la tienda y absolutamente todo lo que cuelga de ella (usuarios, roles, productos,
 * categorías, ventas, apartados, cortes de caja, cajas, materia prima, faltantes, reembolsos...)
 * — más de 20 tablas distintas. Es irreversible: no hay respaldo ni "deshacer".
 * <p>
 * Solo {@code SUPER_ADMIN} puede invocarlo (ver {@code TiendaController}), con doble
 * confirmación obligatoria del lado del frontend (escribir el nombre exacto de la tienda).
 * <p>
 * Como no existe {@code ON DELETE CASCADE} a nivel de base de datos para la mayoría de estas
 * relaciones (salvo {@code sale_items}/{@code apartado_items}, que sí lo tienen y caen solos
 * con su {@code Sale}/{@code Apartado}), el orden de borrado de abajo es exactamente el que
 * evita violar una llave foránea: primero se rompen los dos ciclos de auto-referencia
 * ({@code Tienda}/{@code Role}/{@code User} apuntándose entre sí, y {@code CashRegister}
 * ↔{@code CashCut}), luego se borra de las hojas del árbol de dependencias hacia la raíz.
 * <p>
 * <b>Limitación conocida:</b> no borra los archivos físicos en {@code /uploads} (logo de la
 * tienda, fotos de producto, comprobantes de caja) — solo las filas de la base de datos que
 * los referencian. Quedan huérfanos en disco; es una limpieza de almacenamiento pendiente,
 * no un problema de integridad de datos.
 */
@Service
@RequiredArgsConstructor
public class TiendaPurgeService {

    private final TiendaRepository tiendaRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ArcoRequestRepository arcoRequestRepository;
    private final CashCutScheduleRepository cashCutScheduleRepository;
    private final MailConfigRepository mailConfigRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final RawMaterialMovementRepository rawMaterialMovementRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final ShortagePaymentRepository shortagePaymentRepository;
    private final ShortageRepository shortageRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CashCutRepository cashCutRepository;
    private final CashMovementRepository cashMovementRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final SaleRepository saleRepository;
    private final ApartadoRepository apartadoRepository;
    private final CashReportRecipientRepository cashReportRecipientRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OfferRepository offerRepository;
    private final TiendaInfoRepository tiendaInfoRepository;
    private final TenantScope tenantScope;

    /**
     * Borra permanentemente una tienda y todo lo relacionado con ella.
     *
     * @param id            id de la tienda a borrar
     * @param confirmedName nombre que el SUPER_ADMIN escribió para confirmar; debe coincidir
     *                      exactamente (sensible a mayúsculas) con el nombre real de la
     *                      tienda — segunda confirmación obligatoria, además del diálogo del
     *                      frontend, para que un borrado nunca ocurra por un id equivocado
     * @param actor         usuario que ejecuta el borrado; debe ser SUPER_ADMIN
     * @throws AccessDeniedException   si el actor no es SUPER_ADMIN
     * @throws IllegalArgumentException si la tienda no existe o el nombre no coincide
     */
    @Transactional
    public void purge(Long id, String confirmedName, User actor) {
        if (!tenantScope.isSuperAdmin(actor)) {
            throw new AccessDeniedException("Solo un SUPER_ADMIN puede borrar una tienda de forma permanente");
        }
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + id));
        if (confirmedName == null || !confirmedName.equals(tienda.getName())) {
            throw new IllegalArgumentException("El nombre escrito no coincide con el de la tienda — nada se borró");
        }

        // 1) Romper los ciclos de auto-referencia antes de tocar nada más.
        tiendaRepository.clearSelfRefs(id);
        roleRepository.clearUserRefsByTiendaId(id);
        userRepository.clearSelfRefsByTiendaId(id);

        // 2) Desvincular (sin borrar) lo que es de PLATAFORMA, no de esta tienda.
        arcoRequestRepository.clearRequesterUserByTiendaId(id);
        arcoRequestRepository.clearResolvedByByTiendaId(id);
        arcoRequestRepository.clearIdentityVerifiedByByTiendaId(id);
        arcoRequestRepository.clearExecutedByByTiendaId(id);
        arcoRequestRepository.clearCreatedByByTiendaId(id);
        clearPlatformSingletonRefs(id);

        // 3) Sesiones/tokens de los usuarios de esta tienda (FK NOT NULL: se borran, no se desvinculan).
        refreshTokenRepository.deleteByUserTiendaId(id);
        passwordResetTokenRepository.deleteByUserTiendaId(id);

        // 4) Catálogo e inventario.
        productImageRepository.deleteByProductTiendaId(id);
        inventoryMovementRepository.deleteByProductTiendaId(id);

        // 5) Materia prima.
        rawMaterialMovementRepository.deleteByRawMaterialTiendaId(id);
        rawMaterialRepository.deleteByTiendaId(id);

        // 6) Faltantes (antes de cortes/cajas, que referencian).
        shortagePaymentRepository.deleteByShortageTiendaId(id);
        shortageRepository.deleteByTiendaId(id);

        // 7) Romper el ciclo CashRegister↔CashCut antes de poder borrar cualquiera de los dos.
        cashRegisterRepository.clearLastCutByTiendaId(id);
        cashCutRepository.clearPreviousCutByTiendaId(id);

        // 8) Movimientos de caja y reembolsos (antes de cortes, que referencian).
        cashMovementRepository.deleteByTiendaId(id);
        reimbursementRepository.deleteByTiendaId(id);

        // 9) Ventas y apartados (sus líneas caen solas vía ON DELETE CASCADE) — antes de
        //    cortes de caja, ya que Sale.cashCut los referencia.
        saleRepository.deleteByTiendaId(id);
        apartadoRepository.deleteByTiendaId(id);

        // 10) Cortes y cajas.
        cashCutRepository.deleteByTiendaId(id);
        cashRegisterRepository.deleteByTiendaId(id);
        cashReportRecipientRepository.deleteByTiendaId(id);

        // 10.5) Ofertas — la tabla puente offer_products referencia tanto offers como
        // products, así que debe vaciarse antes de borrar cualquiera de las dos (igual
        // razón que el resto de este orden: evitar violar la FK).
        offerRepository.deleteOfferProductLinksByTiendaId(id);
        offerRepository.deleteByTiendaId(id);

        // 11) Catálogo.
        productRepository.deleteByTiendaId(id);
        categoryRepository.deleteByTiendaId(id);
        tiendaInfoRepository.deleteByTiendaId(id);

        // 12) Identidad — usuarios antes que roles (User.role_id los referencia).
        userRepository.deleteByTiendaId(id);
        roleRepository.deleteByTiendaId(id);

        // 13) La tienda misma.
        tiendaRepository.delete(tienda);
    }

    /**
     * {@link CashCutSchedule} y {@link MailConfig} son filas únicas de configuración global
     * (no por tienda): si su {@code updatedBy} resulta ser un usuario de la tienda que se
     * está borrando, solo se desvincula la referencia — la fila de configuración en sí no
     * se toca.
     */
    private void clearPlatformSingletonRefs(Long tiendaId) {
        cashCutScheduleRepository.findAll().forEach(schedule -> {
            User updatedBy = schedule.getUpdatedBy();
            if (updatedBy != null && updatedBy.getTienda() != null && tiendaId.equals(updatedBy.getTienda().getId())) {
                schedule.setUpdatedBy(null);
                cashCutScheduleRepository.save(schedule);
            }
        });
        mailConfigRepository.findAll().forEach(config -> {
            User updatedBy = config.getUpdatedBy();
            if (updatedBy != null && updatedBy.getTienda() != null && tiendaId.equals(updatedBy.getTienda().getId())) {
                config.setUpdatedBy(null);
                mailConfigRepository.save(config);
            }
        });
    }
}
