package com.boutique.pos.service;

import com.boutique.pos.dto.ArcoRequestCreate;
import com.boutique.pos.dto.ArcoRequestExecute;
import com.boutique.pos.dto.ArcoRequestExtend;
import com.boutique.pos.dto.ArcoRequestInPersonCreate;
import com.boutique.pos.dto.ArcoRequestInfoAnswer;
import com.boutique.pos.dto.ArcoRequestInfoRequest;
import com.boutique.pos.dto.ArcoRequestRespond;
import com.boutique.pos.dto.ArcoRequestVerifyIdentity;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ArcoIdentityMethod;
import com.boutique.pos.model.ArcoOutcome;
import com.boutique.pos.model.ArcoRequest;
import com.boutique.pos.model.ArcoRequestChannel;
import com.boutique.pos.model.ArcoRequestStatus;
import com.boutique.pos.model.ArcoRequestType;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.ApartadoRepository;
import com.boutique.pos.repository.ArcoRequestRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Ciclo de vida completo de una solicitud de derechos ARCO (Acceso, Rectificación,
 * Cancelación, Oposición, Revocación, Limitación) — plataforma completa, sin tienda (ver
 * {@link ArcoRequest}). Tres formas de levantarla ({@link #createPublic}/{@link
 * #createOwn}/{@link #createInPerson}), seguidas de un flujo de atención por SUPER_ADMIN:
 * verificar identidad, pedir información si hace falta, responder (resultado legal) y
 * ejecutar — ver el Javadoc de {@link ArcoRequestStatus} para el diagrama de estados
 * completo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArcoRequestService {

    private final ArcoRequestRepository repository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final ApartadoRepository apartadoRepository;
    private final EmailService emailService;
    private final ArcoConstanciaPdfService constanciaPdfService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private static final int RESPONSE_BUSINESS_DAYS = 20;
    private static final int INFO_RESPONSE_BUSINESS_DAYS = 10;
    private static final int EXECUTION_BUSINESS_DAYS = 15;

    // ── Alta ─────────────────────────────────────────────────────────────────────────────

    /**
     * Levanta una solicitud desde el formulario público, sin sesión (canal {@code WEB}).
     * {@code req.requesterEmail} debe corresponder a una cuenta real ({@link User}) del
     * sistema. Queda {@code PENDING_EMAIL}: no le avisa a ningún SUPER_ADMIN todavía — eso
     * pasa hasta que confirma su correo por el link que se le manda (ver {@link
     * #trackByToken}), para que nadie pueda levantar una a nombre de un correo que no
     * controla, aun sabiendo que esa cuenta existe.
     *
     * @throws IllegalArgumentException si falta el nombre/correo, si el correo no
     *         corresponde a ningún usuario, o si falta el nombre del representante cuando
     *         {@code representative=true}
     */
    @Transactional
    public void createPublic(ArcoRequestCreate req) {
        String name = requireText(req.getRequesterName(), "Tu nombre es obligatorio");
        String email = requireText(req.getRequesterEmail(), "Tu correo es obligatorio").trim();
        User requesterUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ese correo no corresponde a ningún usuario registrado en el sistema."));
        validateRepresentative(req.getRepresentative(), req.getRepresentativeName());

        String rawToken = generateToken();
        ArcoRequest entity = ArcoRequest.builder()
                .requesterName(name)
                .requesterEmail(email)
                .requesterPhone(blankToNull(req.getRequesterPhone()))
                .type(req.getType())
                .details(blankToNull(req.getDetails()))
                .representative(Boolean.TRUE.equals(req.getRepresentative()))
                .representativeName(blankToNull(req.getRepresentativeName()))
                .privacyNoticeAccepted(req.isAcceptPrivacyNotice())
                .channel(ArcoRequestChannel.WEB)
                .status(ArcoRequestStatus.PENDING_EMAIL)
                .trackingTokenHash(hash(rawToken))
                .trackingTokenEnc(rawToken)
                .requesterUser(requesterUser)
                .build();
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} ({}) creada vía WEB, pendiente de confirmar correo", saved.getId(), saved.getType());
        emailService.sendArcoConfirmEmail(saved, trackingUrl(rawToken), email);
    }

    /**
     * Levanta una solicitud desde la cuenta ya autenticada del propio titular (canal {@code
     * ACCOUNT}, "Mis solicitudes ARCO") — nombre/correo se toman de la sesión, nunca del
     * body. Si {@link ArcoRequestType#needsStrongIdentity()}, exige volver a confirmar la
     * contraseña de la cuenta. No necesita confirmar correo (la sesión ya es prueba de eso):
     * queda {@code PENDING} directamente y corre el plazo desde ya.
     *
     * @throws IllegalArgumentException si falta la contraseña (o es incorrecta) para un tipo
     *         de identidad fuerte, o si falta el nombre del representante
     */
    @Transactional
    public void createOwn(ArcoRequestCreate req, User actor) {
        validateRepresentative(req.getRepresentative(), req.getRepresentativeName());
        boolean strong = req.getType().needsStrongIdentity();
        if (strong && (req.getPassword() == null || req.getPassword().isBlank()
                || !passwordEncoder.matches(req.getPassword(), actor.getPassword()))) {
            throw new IllegalArgumentException("Tu contraseña no es correcta");
        }

        LocalDateTime now = LocalDateTime.now();
        String rawToken = generateToken();
        ArcoRequest entity = ArcoRequest.builder()
                .requesterName(actor.getName())
                .requesterEmail(actor.getEmail())
                .requesterPhone(blankToNull(req.getRequesterPhone()))
                .type(req.getType())
                .details(blankToNull(req.getDetails()))
                .representative(Boolean.TRUE.equals(req.getRepresentative()))
                .representativeName(blankToNull(req.getRepresentativeName()))
                .privacyNoticeAccepted(req.isAcceptPrivacyNotice())
                .channel(ArcoRequestChannel.ACCOUNT)
                .status(ArcoRequestStatus.PENDING)
                .receivedAt(now)
                .dueDate(businessDaysFrom(now.toLocalDate(), RESPONSE_BUSINESS_DAYS))
                .trackingTokenHash(hash(rawToken))
                .trackingTokenEnc(rawToken)
                .requesterUser(actor)
                .build();
        if (strong) {
            entity.setIdentityVerifiedAt(now);
            entity.setIdentityMethod(ArcoIdentityMethod.PASSWORD);
            entity.setIdentityNotes("Confirmó su contraseña al levantar la solicitud desde su cuenta.");
        }
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} ({}) creada vía ACCOUNT por {}", saved.getId(), saved.getType(), actor.getEmail());
        notifyAdminsNewRequest(saved);
    }

    /**
     * Registra una solicitud EN MOSTRADOR (canal {@code IN_PERSON}, SUPER_ADMIN) para
     * alguien que el personal verificó físicamente — con o sin cuenta en el sistema. Queda
     * {@code PENDING} e identidad ya verificada de inmediato.
     *
     * @throws IllegalArgumentException si falta el nombre del representante cuando
     *         {@code representative=true}
     */
    @Transactional
    public ArcoRequestView createInPerson(ArcoRequestInPersonCreate req, User actor) {
        validateRepresentative(req.getRepresentative(), req.getRepresentativeName());
        User requesterUser = null;
        String email = blankToNull(req.getRequesterEmail());
        if (email != null) {
            requesterUser = userRepository.findByEmail(email.trim()).orElse(null);
        }

        LocalDateTime now = LocalDateTime.now();
        String rawToken = generateToken();
        ArcoRequest entity = ArcoRequest.builder()
                .requesterName(req.getRequesterName().trim())
                .requesterEmail(email)
                .requesterPhone(blankToNull(req.getRequesterPhone()))
                .type(req.getType())
                .details(blankToNull(req.getDetails()))
                .representative(Boolean.TRUE.equals(req.getRepresentative()))
                .representativeName(blankToNull(req.getRepresentativeName()))
                .privacyNoticeAccepted(true)
                .channel(ArcoRequestChannel.IN_PERSON)
                .status(ArcoRequestStatus.PENDING)
                .receivedAt(now)
                .dueDate(businessDaysFrom(now.toLocalDate(), RESPONSE_BUSINESS_DAYS))
                .trackingTokenHash(hash(rawToken))
                .trackingTokenEnc(rawToken)
                .requesterUser(requesterUser)
                .identityVerifiedAt(now)
                .identityMethod(ArcoIdentityMethod.IN_PERSON)
                .identityVerifiedBy(actor)
                .identityNotes(req.getIdentityNotes().trim())
                .createdBy(actor)
                .build();
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} ({}) registrada en mostrador por {}", saved.getId(), saved.getType(), actor.getEmail());
        notifyAdminsNewRequest(saved);
        return toView(saved);
    }

    // ── Seguimiento público por link ────────────────────────────────────────────────────

    /**
     * Resuelve una solicitud por su token de seguimiento. Si todavía está {@code
     * PENDING_EMAIL} (canal WEB sin confirmar), visitar el link ES la confirmación: marca el
     * correo verificado, arranca el plazo legal y recién ahí avisa a los SUPER_ADMIN.
     *
     * @throws IllegalArgumentException si el token no corresponde a ninguna solicitud
     */
    @Transactional
    public ArcoRequestView trackByToken(String rawToken) {
        ArcoRequest entity = repository.findByTrackingTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Link inválido o vencido"));
        if (entity.getStatus() == ArcoRequestStatus.PENDING_EMAIL) {
            LocalDateTime now = LocalDateTime.now();
            entity.setEmailVerifiedAt(now);
            entity.setReceivedAt(now);
            entity.setDueDate(businessDaysFrom(now.toLocalDate(), RESPONSE_BUSINESS_DAYS));
            entity.setStatus(ArcoRequestStatus.PENDING);
            entity = repository.save(entity);
            log.info("Solicitud ARCO {} confirmó su correo, arranca el plazo legal", entity.getId());
            notifyAdminsNewRequest(entity);
        }
        return toView(entity);
    }

    /**
     * El titular responde, por el link de seguimiento (sin sesión), lo que se le pidió en
     * {@link #requestInfo}.
     *
     * @throws IllegalArgumentException si el token no corresponde a ninguna solicitud
     * @throws IllegalStateException si no está esperando información
     */
    @Transactional
    public ArcoRequestView answerInfoByToken(String rawToken, ArcoRequestInfoAnswer req) {
        ArcoRequest entity = repository.findByTrackingTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Link inválido o vencido"));
        return answerInfo(entity, req);
    }

    /**
     * El titular (ya autenticado, dueño de la solicitud) responde lo que se le pidió —
     * mismo efecto que {@link #answerInfoByToken}, para quien prefiere hacerlo desde "Mis
     * solicitudes" en vez del link del correo.
     *
     * @throws IllegalArgumentException si no existe o no es suya
     * @throws IllegalStateException si no está esperando información
     */
    @Transactional
    public ArcoRequestView answerInfoMine(Long id, ArcoRequestInfoAnswer req, User actor) {
        ArcoRequest entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + id));
        if (entity.getRequesterUser() == null || !entity.getRequesterUser().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Esta solicitud no es tuya");
        }
        return answerInfo(entity, req);
    }

    private ArcoRequestView answerInfo(ArcoRequest entity, ArcoRequestInfoAnswer req) {
        if (entity.getStatus() != ArcoRequestStatus.INFO_REQUESTED) {
            throw new IllegalStateException("Esta solicitud no está esperando información");
        }
        entity.setInfoResponse(req.getAnswer().trim());
        entity.setStatus(ArcoRequestStatus.PENDING);
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} recibió la información pedida", saved.getId());
        for (User admin : userRepository.findActiveSuperAdmins()) {
            emailService.sendArcoInfoAnsweredEmail(saved, adminLink(), admin.getEmail());
        }
        return toView(saved);
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────

    /** Lista las solicitudes, abiertas primero — ver {@link ArcoRequestRepository#search}. */
    @Transactional(readOnly = true)
    public List<ArcoRequestView> list(ArcoRequestStatus status) {
        return repository.search(status).stream().map(this::toView).toList();
    }

    /** Cuántas solicitudes siguen sin cerrar — para el badge del menú. */
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countOpen();
    }

    /** Las solicitudes levantadas por el usuario en sesión — "Mis solicitudes", abierto a cualquier rol. */
    @Transactional(readOnly = true)
    public List<ArcoRequestView> listMine(User actor) {
        return repository.findByRequesterUserIdOrderByCreatedAtDesc(actor.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ArcoRequestView getById(Long id) {
        return toView(findOrThrow(id));
    }

    /** PDF de constancia de una solicitud, con su estado actual. */
    @Transactional(readOnly = true)
    public byte[] constanciaPdf(Long id) {
        return constanciaPdfService.generate(findOrThrow(id));
    }

    // ── Atención (SUPER_ADMIN) ──────────────────────────────────────────────────────────

    /** Marca verificada la identidad de quien pide — se puede repetir/corregir mientras no se haya respondido. */
    @Transactional
    public ArcoRequestView verifyIdentity(Long id, ArcoRequestVerifyIdentity req, User actor) {
        ArcoRequest entity = findOrThrow(id);
        if (entity.getStatus() == ArcoRequestStatus.COMPLETED || entity.getStatus() == ArcoRequestStatus.REJECTED
                || entity.getStatus() == ArcoRequestStatus.NOT_FILED) {
            throw new IllegalStateException("Esta solicitud ya está cerrada");
        }
        entity.setIdentityVerifiedAt(LocalDateTime.now());
        entity.setIdentityMethod(req.getMethod());
        entity.setIdentityNotes(req.getNotes().trim());
        entity.setIdentityVerifiedBy(actor);
        return toView(repository.save(entity));
    }

    /** Pide más información al titular antes de poder responder. */
    @Transactional
    public ArcoRequestView requestInfo(Long id, ArcoRequestInfoRequest req, User actor) {
        ArcoRequest entity = findOrThrow(id);
        if (entity.getStatus() != ArcoRequestStatus.PENDING) {
            throw new IllegalStateException("Solo se puede pedir información a una solicitud pendiente");
        }
        entity.setInfoRequest(req.getInfoRequest().trim());
        entity.setInfoDue(businessDaysFrom(LocalDate.now(), INFO_RESPONSE_BUSINESS_DAYS));
        entity.setStatus(ArcoRequestStatus.INFO_REQUESTED);
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} pasa a INFO_REQUESTED, responde antes del {}", saved.getId(), saved.getInfoDue());
        String url = saved.getTrackingTokenEnc() != null ? trackingUrl(saved.getTrackingTokenEnc()) : null;
        emailService.sendArcoInfoRequestedEmail(saved, url, saved.getRequesterEmail());
        return toView(saved);
    }

    /** Extiende el plazo legal una sola vez (otros 20 días hábiles). */
    @Transactional
    public ArcoRequestView extend(Long id, ArcoRequestExtend req, User actor) {
        ArcoRequest entity = findOrThrow(id);
        if (Boolean.TRUE.equals(entity.getExtended())) {
            throw new IllegalStateException("Esta solicitud ya se extendió una vez — no se puede extender de nuevo");
        }
        if (entity.getDueDate() == null) {
            throw new IllegalStateException("Esta solicitud todavía no tiene un plazo que extender");
        }
        entity.setDueDate(businessDaysFrom(entity.getDueDate(), RESPONSE_BUSINESS_DAYS));
        entity.setExtended(true);
        entity.setExtensionReason(req.getReason().trim());
        return toView(repository.save(entity));
    }

    /**
     * Responde legalmente una solicitud {@code PENDING}: decide si procede, procede
     * parcialmente o es improcedente. Improcedente la cierra ({@code REJECTED}, sin
     * ejecución); cualquier otro resultado la deja {@code APPROVED}, lista para {@link
     * #execute}. Manda la constancia en PDF al titular.
     *
     * @throws IllegalStateException si no está {@code PENDING}, o si el tipo exige
     *         identidad fuerte y todavía no se verificó
     * @throws IllegalArgumentException si falta el motivo/fundamento cuando el resultado no
     *         es totalmente procedente
     */
    @Transactional
    public ArcoRequestView respond(Long id, ArcoRequestRespond req, User actor) {
        ArcoRequest entity = findOrThrow(id);
        if (entity.getStatus() != ArcoRequestStatus.PENDING) {
            throw new IllegalStateException("Solo se puede responder una solicitud pendiente "
                    + "(si está esperando información, el titular debe responderla primero)");
        }
        if (entity.getType().needsStrongIdentity() && entity.getIdentityVerifiedAt() == null) {
            throw new IllegalStateException("Verifica la identidad de quien pide antes de responder este tipo de solicitud");
        }
        if (req.getOutcome() != ArcoOutcome.PROCEDENTE && (req.getLegalBasis() == null || req.getLegalBasis().isBlank())) {
            throw new IllegalArgumentException("Explica el motivo/fundamento cuando el resultado no es totalmente procedente");
        }

        entity.setOutcome(req.getOutcome());
        entity.setLegalBasis(blankToNull(req.getLegalBasis()));
        entity.setResolutionNotes(req.getNotes().trim());
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(actor);

        if (req.getOutcome() == ArcoOutcome.IMPROCEDENTE) {
            entity.setStatus(ArcoRequestStatus.REJECTED);
        } else {
            entity.setStatus(ArcoRequestStatus.APPROVED);
            entity.setExecutionDue(businessDaysFrom(LocalDate.now(), EXECUTION_BUSINESS_DAYS));
        }
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} respondida: {} -> {}", saved.getId(), saved.getOutcome(), saved.getStatus());
        emailService.sendArcoRespondedEmail(saved, constanciaPdfService.generate(saved));
        return toView(saved);
    }

    /**
     * Ejecuta una solicitud ya {@code APPROVED}: para Acceso, arma y adjunta un reporte de
     * los datos que el sistema tiene de la persona; para Cancelación, desactiva la cuenta
     * del titular si tiene una (los registros de venta/apartado se conservan, obligación
     * fiscal). El resto de los tipos quedan a criterio manual del SUPER_ADMIN, descrito en
     * {@code summary} — ver el Javadoc de la clase.
     *
     * @throws IllegalStateException si no está {@code APPROVED}
     */
    @Transactional
    public ArcoRequestView execute(Long id, ArcoRequestExecute req, User actor) {
        ArcoRequest entity = findOrThrow(id);
        if (entity.getStatus() != ArcoRequestStatus.APPROVED) {
            throw new IllegalStateException("Solo se puede ejecutar una solicitud ya aprobada");
        }

        StringBuilder summary = new StringBuilder(req.getSummary().trim());
        if (entity.getType() == ArcoRequestType.CANCELLATION && entity.getRequesterUser() != null
                && Boolean.TRUE.equals(entity.getRequesterUser().getIsActive())) {
            User u = entity.getRequesterUser();
            u.setIsActive(false);
            u.setDeletedAt(LocalDateTime.now());
            userRepository.save(u);
            summary.append("\n\n[Automático] Se desactivó la cuenta del titular en el sistema. "
                    + "Sus ventas/apartados históricos se conservan por obligación fiscal/contable.");
        } else if (entity.getType() == ArcoRequestType.ACCESS) {
            summary.append("\n\n[Automático] ").append(buildAccessDataSummary(entity));
        }

        entity.setExecutedAt(LocalDateTime.now());
        entity.setExecutedBy(actor);
        entity.setExecutionSummary(summary.toString());
        entity.setStatus(ArcoRequestStatus.COMPLETED);
        ArcoRequest saved = repository.save(entity);
        log.info("Solicitud ARCO {} ejecutada y completada", saved.getId());
        emailService.sendArcoCompletedEmail(saved, constanciaPdfService.generate(saved));
        return toView(saved);
    }

    /**
     * Resumen de los datos encontrados en el sistema para una solicitud de Acceso: la
     * cuenta de usuario (si tiene) y las ventas/apartados donde aparece como cliente (por
     * correo o teléfono) — texto simple, se adjunta a {@code executionSummary} y por lo
     * tanto sale impreso en la constancia PDF.
     */
    private String buildAccessDataSummary(ArcoRequest request) {
        StringBuilder sb = new StringBuilder("Datos encontrados en el sistema:");
        User u = request.getRequesterUser();
        if (u != null) {
            sb.append("\n- Cuenta de usuario: ").append(u.getName()).append(" <").append(u.getEmail()).append(">");
            if (u.getPhone() != null) sb.append(", tel. ").append(u.getPhone());
            if (u.getRole() != null) sb.append(", rol ").append(u.getRole().getName());
        }
        String email = request.getRequesterEmail() != null ? request.getRequesterEmail() : "";
        String phone = request.getRequesterPhone() != null ? request.getRequesterPhone() : "";
        if (!email.isBlank() || !phone.isBlank()) {
            List<Sale> sales = email.isBlank() ? List.of() : saleRepository.findByCustomerEmailIgnoreCase(email);
            List<Apartado> apartados = apartadoRepository.findByCustomerEmailIgnoreCaseOrCustomerPhone(email, phone);
            sb.append("\n- ").append(sales.size()).append(" venta(s) registrada(s) a su nombre");
            if (!sales.isEmpty()) {
                sb.append(":");
                for (Sale s : sales) {
                    sb.append("\n  · Venta folio ").append(s.getFolio()).append(" en ")
                            .append(s.getTienda() != null ? s.getTienda().getName() : "—")
                            .append(" (").append(s.getCreatedAt().toLocalDate()).append(")");
                }
            }
            sb.append("\n- ").append(apartados.size()).append(" apartado(s) registrado(s) a su nombre");
            if (!apartados.isEmpty()) {
                sb.append(":");
                for (Apartado a : apartados) {
                    sb.append("\n  · Apartado folio ").append(a.getFolio()).append(" en ")
                            .append(a.getTienda().getName())
                            .append(" (").append(a.getRequestedAt().toLocalDate()).append(")");
                }
            }
        }
        return sb.toString();
    }

    // ── Mantenimiento (ver ArcoMaintenanceJob) ──────────────────────────────────────────

    /**
     * Purga las solicitudes WEB que nunca confirmaron su correo (más de 3 días) y cierra
     * como {@code NOT_FILED} las que están esperando información del titular cuyo plazo ya
     * venció.
     *
     * @return cuántas filas se tocaron en total (purgadas + cerradas), para el log del job
     */
    @Transactional
    public int maintenance() {
        int purged = repository.deleteByStatusAndCreatedAtBefore(
                ArcoRequestStatus.PENDING_EMAIL, LocalDateTime.now().minusDays(3));

        List<ArcoRequest> overdue = repository.findByStatusAndInfoDueBefore(ArcoRequestStatus.INFO_REQUESTED, LocalDate.now());
        for (ArcoRequest entity : overdue) {
            entity.setStatus(ArcoRequestStatus.NOT_FILED);
            repository.save(entity);
            log.info("Solicitud ARCO {} cerrada como NOT_FILED: no respondió la información pedida a tiempo", entity.getId());
        }
        return purged + overdue.size();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private ArcoRequest findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + id));
    }

    private void validateRepresentative(Boolean representative, String representativeName) {
        if (Boolean.TRUE.equals(representative) && (representativeName == null || representativeName.isBlank())) {
            throw new IllegalArgumentException("Escribe el nombre de la persona a la que representas");
        }
    }

    private void notifyAdminsNewRequest(ArcoRequest saved) {
        for (User admin : userRepository.findActiveSuperAdmins()) {
            emailService.sendArcoRequestEmail(saved, adminLink(), admin.getEmail());
        }
    }

    private String adminLink() {
        return frontendUrl + "/arco-requests";
    }

    private String trackingUrl(String rawToken) {
        return frontendUrl + "/arco/seguimiento/" + rawToken;
    }

    private LocalDate businessDaysFrom(LocalDate start, int days) {
        return com.boutique.pos.util.BusinessCalendar.addBusinessDays(start, days);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String requireText(String s, String message) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(message);
        return s.trim();
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
                .channel(a.getChannel().name())
                .details(a.getDetails())
                .representative(a.getRepresentative())
                .representativeName(a.getRepresentativeName())
                .createdAt(a.getCreatedAt())
                .receivedAt(a.getReceivedAt())
                .emailVerifiedAt(a.getEmailVerifiedAt())
                .dueDate(a.getDueDate())
                .identityVerifiedAt(a.getIdentityVerifiedAt())
                .identityMethod(a.getIdentityMethod() != null ? a.getIdentityMethod().name() : null)
                .identityNotes(a.getIdentityNotes())
                .infoRequest(a.getInfoRequest())
                .infoDue(a.getInfoDue())
                .infoResponse(a.getInfoResponse())
                .extended(a.getExtended())
                .extensionReason(a.getExtensionReason())
                .outcome(a.getOutcome() != null ? a.getOutcome().name() : null)
                .legalBasis(a.getLegalBasis())
                .resolutionNotes(a.getResolutionNotes())
                .resolvedAt(a.getResolvedAt())
                .resolvedByName(a.getResolvedBy() != null ? a.getResolvedBy().getName() : null)
                .executionDue(a.getExecutionDue())
                .executedAt(a.getExecutedAt())
                .executedByName(a.getExecutedBy() != null ? a.getExecutedBy().getName() : null)
                .executionSummary(a.getExecutionSummary())
                .createdByName(a.getCreatedBy() != null ? a.getCreatedBy().getName() : null)
                .build();
    }
}
