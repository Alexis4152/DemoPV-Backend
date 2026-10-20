package com.boutique.pos.service;

import com.boutique.pos.dto.ArcoRequestCreate;
import com.boutique.pos.dto.ArcoRequestResolve;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.ArcoRequest;
import com.boutique.pos.model.ArcoRequestStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.ArcoRequestRepository;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solicitudes de derechos ARCO (Acceso, Rectificación, Cancelación, Oposición) sobre datos
 * personales — plataforma completa, sin tienda (ver {@link ArcoRequest}). El formulario
 * público no pide iniciar sesión, pero solo se acepta la solicitud si el correo
 * corresponde a una cuenta real del sistema (ver {@link #create}); solo SUPER_ADMIN puede
 * verlas todas y atenderlas (ver {@code AdminArcoRequestController}) — cada quien puede ver
 * las suyas desde "Mis solicitudes" (ver {@link #listMine}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArcoRequestService {

    private final ArcoRequestRepository repository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Registra una solicitud nueva (queda {@code PENDING}) y avisa por correo a todos los
     * SUPER_ADMIN activos — ninguno queda sin enterarse por no ser "el" responsable.
     *
     * <p>{@code req.requesterEmail} debe corresponder a una cuenta real ({@link User}) del
     * sistema — se busca por {@link UserRepository#findByEmail}, sin importar si está
     * activa o no (una cuenta desactivada también puede querer ejercer sus derechos ARCO
     * sobre lo que el sistema todavía guarda de ella). Queda ligada en {@link
     * ArcoRequest#getRequesterUser()}, lo que habilita el seguimiento ("Mis solicitudes",
     * ver {@link #listMine}) iniciando sesión con esa cuenta.</p>
     *
     * @param req datos de la solicitud, capturados por quien la hace
     * @throws IllegalArgumentException si {@code requesterEmail} no corresponde a ningún usuario
     */
    @Transactional
    public void create(ArcoRequestCreate req) {
        String email = req.getRequesterEmail().trim();
        User requesterUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ese correo no corresponde a ningún usuario registrado en el sistema."));

        ArcoRequest entity = ArcoRequest.builder()
                .requesterName(req.getRequesterName().trim())
                .requesterEmail(email)
                .requesterPhone(blankToNull(req.getRequesterPhone()))
                .type(req.getType())
                .details(blankToNull(req.getDetails()))
                .requesterUser(requesterUser)
                .build();
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} ({}) registrada de {}", saved.getId(), saved.getType(), saved.getRequesterEmail());

        List<User> admins = userRepository.findActiveSuperAdmins();
        if (admins.isEmpty()) {
            log.warn("Solicitud ARCO {} registrada pero no hay ningún SUPER_ADMIN activo a quien avisar", saved.getId());
            return;
        }
        String adminLink = frontendUrl + "/arco-requests";
        for (User admin : admins) {
            emailService.sendArcoRequestEmail(saved, adminLink, admin.getEmail());
        }
    }

    /**
     * Lista las solicitudes, pendientes primero — ver {@link ArcoRequestRepository#search}.
     *
     * @param status si no es nulo, acota a ese estado únicamente
     */
    @Transactional(readOnly = true)
    public List<ArcoRequestView> list(ArcoRequestStatus status) {
        return repository.search(status).stream().map(this::toView).toList();
    }

    /** Cuántas solicitudes siguen sin atender — para el badge del menú. */
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByStatus(ArcoRequestStatus.PENDING);
    }

    /**
     * Las solicitudes levantadas por el usuario en sesión — "Mis solicitudes", abierto a
     * cualquier rol (cada quien ve únicamente las suyas, nunca las de alguien más).
     *
     * @param actor usuario autenticado; se filtra por {@link ArcoRequest#getRequesterUser()}
     */
    @Transactional(readOnly = true)
    public List<ArcoRequestView> listMine(User actor) {
        return repository.findByRequesterUserIdOrderByCreatedAtDesc(actor.getId()).stream().map(this::toView).toList();
    }

    /**
     * Resuelve una solicitud pendiente, marcándola {@code COMPLETED} o {@code REJECTED}
     * con una nota explicando qué se hizo (obligatoria, queda como constancia). Una
     * solicitud ya resuelta no se puede volver a resolver — si hace falta corregir algo,
     * es un caso manual fuera de este flujo.
     *
     * @throws IllegalArgumentException si no existe
     * @throws IllegalStateException si ya estaba resuelta
     */
    @Transactional
    public ArcoRequestView resolve(Long id, ArcoRequestResolve req, User actor) {
        ArcoRequest entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + id));
        if (entity.getStatus() != ArcoRequestStatus.PENDING) {
            throw new IllegalStateException("Esta solicitud ya fue resuelta");
        }
        entity.setStatus(ArcoRequestStatus.valueOf(req.getStatus()));
        entity.setResolutionNotes(req.getNotes().trim());
        entity.setResolvedBy(actor);
        entity.setResolvedAt(LocalDateTime.now());
        return toView(repository.save(entity));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ArcoRequestView toView(ArcoRequest a) {
        return ArcoRequestView.builder()
                .id(a.getId())
                .requesterName(a.getRequesterName())
                .requesterEmail(a.getRequesterEmail())
                .requesterPhone(a.getRequesterPhone())
                .type(a.getType().name())
                .status(a.getStatus().name())
                .details(a.getDetails())
                .resolutionNotes(a.getResolutionNotes())
                .createdAt(a.getCreatedAt())
                .resolvedAt(a.getResolvedAt())
                .resolvedByName(a.getResolvedBy() != null ? a.getResolvedBy().getName() : null)
                .build();
    }
}
