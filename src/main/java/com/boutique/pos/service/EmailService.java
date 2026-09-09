package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoItem;
import com.boutique.pos.model.MailConfig;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Envío de correos del sistema: ticket de compra en PDF al cliente, reporte de cierre
 * de caja en Excel al administrador de la tienda, avisos de apartados, y el link de
 * recuperación de contraseña.
 *
 * <p>La cuenta SMTP con la que sale TODO se lee de {@link MailConfig} (vía {@link
 * MailConfigService#getEntity()}) en cada envío, en vez del bean {@code JavaMailSender}
 * autoconfigurado por Spring desde application.properties — así un SUPER_ADMIN puede
 * rotar la contraseña o cambiar de cuenta desde {@code /api/admin/mail-config} sin
 * reiniciar el backend. Es una sola cuenta para toda la plataforma: Gmail no permite
 * mandar con un remitente real distinto al autenticado, así que el correo propio que cada
 * tienda configura ({@link Tienda#getContactEmail()}) se usa como "Responder a", nunca
 * como remitente — ver {@link #applySenderAndReplyTo}.</p>
 *
 * <p>Todos los envíos son asíncronos ({@code @Async}) y "best effort": un fallo de correo
 * (SMTP caído, dirección inválida, etc.) solo se registra en el log y nunca revierte ni
 * bloquea la operación de negocio que lo disparó (la venta ya quedó registrada, el corte
 * ya quedó cerrado, el token de recuperación ya quedó generado).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final MailConfigService mailConfigService;
    private final TicketPdfService ticketPdfService;

    /** Arma un {@link JavaMailSender} de un solo uso con las credenciales vigentes en {@link MailConfig}. */
    private JavaMailSender buildSender(MailConfig cfg) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getSmtpHost());
        sender.setPort(cfg.getSmtpPort());
        sender.setUsername(cfg.getSmtpUsername());
        sender.setPassword(cfg.getSmtpPassword());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }

    /**
     * Pone el remitente (siempre la cuenta autenticada en {@code cfg}, con el nombre de la
     * tienda como texto visible) y, si la tienda capturó un correo propio, lo pone como
     * "Responder a" — para que si el cliente le da "Responder" al ticket, le llegue
     * directo a la tienda y no a la cuenta centralizada de la plataforma.
     *
     * @param tienda tienda dueña del correo, o {@code null} si no aplica (ej. un
     *               SUPER_ADMIN pidiendo recuperar su contraseña) — en ese caso se usa
     *               "Nexora POS" como nombre visible y no se pone "Responder a".
     */
    private void applySenderAndReplyTo(MimeMessageHelper helper, MailConfig cfg, Tienda tienda) throws MessagingException {
        String display = (tienda != null && tienda.getName() != null && !tienda.getName().isBlank())
                ? tienda.getName() : "Nexora POS";
        try {
            helper.setFrom(new InternetAddress(cfg.getSmtpUsername(), display));
        } catch (UnsupportedEncodingException e) {
            helper.setFrom(cfg.getSmtpUsername());
        }
        if (tienda != null && tienda.getContactEmail() != null && !tienda.getContactEmail().isBlank()) {
            helper.setReplyTo(tienda.getContactEmail());
        }
    }

    // @Async: la venta ya se registró y se le respondió al vendedor; el correo se manda
    // en segundo plano y si falla (SMTP mal configurado, correo inválido, etc.) solo
    // se registra en el log, nunca revierte ni afecta la venta.
    /**
     * Genera el ticket en PDF de una venta y lo envía por correo al cliente.
     *
     * @param sale venta ya registrada, usada para generar el PDF con {@link TicketPdfService}
     * @param toEmail correo del cliente al que se envía el ticket
     */
    @Async
    public void sendTicketEmail(Sale sale, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el ticket de la venta {}", sale.getId());
            return;
        }
        try {
            byte[] pdf = ticketPdfService.generate(sale);

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applySenderAndReplyTo(helper, cfg, sale.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Tu ticket de compra #" + sale.getId());
            helper.setText("Gracias por tu compra. Adjuntamos tu ticket en PDF.");
            helper.addAttachment("ticket-" + sale.getId() + ".pdf", new org.springframework.core.io.ByteArrayResource(pdf));

            sender.send(message);
            log.info("Ticket de la venta {} enviado a {}", sale.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el ticket de la venta {} a {}: {}", sale.getId(), toEmail, e.getMessage());
        }
    }

    // Un solo correo por tienda con el reporte en Excel de todos los cortes que se
    // acaban de cerrar (a mano o por el job automático), sin importar cuántos cajeros.
    /**
     * Envía por correo el reporte Excel de cierre de caja de una tienda a un destinatario
     * (típicamente un administrador de esa tienda). El Excel ya viene generado por
     * {@link CashCutReportExcelService} y agrupa todos los cortes cerrados del periodo.
     *
     * @param tienda tienda a la que corresponde el reporte
     * @param cutCount cantidad de cortes incluidos en el reporte, solo para el texto del correo
     * @param excelBytes contenido del archivo .xlsx ya generado
     * @param toEmail correo del destinatario (administrador de la tienda)
     */
    @Async
    public void sendCashCutReportEmail(Tienda tienda, int cutCount, byte[] excelBytes, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el reporte de cierre de {}", tienda.getName());
            return;
        }
        try {
            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applySenderAndReplyTo(helper, cfg, tienda);
            helper.setTo(toEmail);
            helper.setSubject("Reporte de cierre de caja — " + tienda.getName());
            helper.setText(
                    "Se cerraron " + cutCount + " corte(s) de caja en " + tienda.getName() + ".\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            helper.addAttachment("cierre-caja-" + tienda.getId() + ".xlsx",
                    new org.springframework.core.io.ByteArrayResource(excelBytes));

            sender.send(message);
            log.info("Reporte de cierre de caja de {} ({} corte(s)) enviado a {}", tienda.getName(), cutCount, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el reporte de cierre de {} a {}: {}", tienda.getName(), toEmail, e.getMessage());
        }
    }

    // @Async por el mismo motivo que los otros dos envíos: AuthService ya generó el token y
    // respondió al frontend (con el mismo mensaje genérico, exista o no el correo) antes de
    // que este correo termine de mandarse.
    /**
     * Envía por correo el link para restablecer la contraseña de un usuario.
     *
     * @param user usuario que pidió recuperar su contraseña
     * @param resetLink URL (del frontend) con el token de un solo uso, vigente 30 minutos
     */
    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite la recuperación de contraseña de {}", user.getEmail());
            return;
        }
        try {
            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applySenderAndReplyTo(helper, cfg, user.getTienda());
            helper.setTo(user.getEmail());
            helper.setSubject("Recupera tu contraseña");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña. Este link es válido por 30 minutos:\n\n" +
                    resetLink + "\n\n" +
                    "Si tú no pediste esto, puedes ignorar este correo — tu contraseña sigue siendo la misma."
            );

            sender.send(message);
            log.info("Correo de recuperación de contraseña enviado a {}", user.getEmail());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo de recuperación de contraseña a {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // @Async por el mismo motivo que el resto: el usuario ya quedó creado y UserController
    // ya le respondió al admin que lo dio de alta antes de que este correo termine de mandarse.
    /**
     * Envía por correo la contraseña temporal generada al dar de alta un usuario nuevo
     * (ver {@code UserService#create}). El usuario debe poder iniciar sesión con ella de
     * inmediato; el frontend es quien lo obliga a cambiarla justo después (ver
     * {@code User#getMustChangePassword()}), este correo no lo menciona como un link de un
     * solo uso — es una contraseña real, solo temporal.
     *
     * @param user usuario recién creado
     * @param tempPassword contraseña temporal en texto plano, generada al azar
     * @param loginLink URL (del frontend, ya resuelta según el entorno) a la pantalla de login
     */
    @Async
    public void sendNewUserPasswordEmail(User user, String tempPassword, String loginLink) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite la contraseña temporal de {}", user.getEmail());
            return;
        }
        try {
            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applySenderAndReplyTo(helper, cfg, user.getTienda());
            helper.setTo(user.getEmail());
            helper.setSubject("Tu cuenta en Nexora POS");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Se creó una cuenta para ti en el sistema. Estos son tus datos de acceso:\n\n" +
                    "Correo: " + user.getEmail() + "\n" +
                    "Contraseña temporal: " + tempPassword + "\n\n" +
                    "Inicia sesión aquí:\n" + loginLink + "\n\n" +
                    "Al iniciar sesión por primera vez se te va a pedir que la cambies por una de tu elección.\n\n" +
                    "Si tú no esperabas este correo, contacta a tu administrador."
            );

            sender.send(message);
            log.info("Contraseña temporal enviada a {} (usuario nuevo)", user.getEmail());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar la contraseña temporal a {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // Se manda al crearse (PENDING), que es el momento en que alguien tiene que actuar
    // (revisar y confirmar o rechazar) — no hay otro aviso automático por correo en el
    // resto del ciclo de vida del apartado.
    /**
     * Avisa por correo a un administrador de la tienda que llegó un apartado nuevo
     * (estado {@code PENDING}) y necesita revisarlo/confirmarlo.
     *
     * @param apartado apartado recién creado, con sus {@link ApartadoItem} ya cargados
     * @param toEmail correo del administrador a notificar
     */
    @Async
    public void sendApartadoRequestEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso del apartado {}", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("Nuevo apartado de ").append(apartado.getCustomerName());
            if (apartado.getCustomerPhone() != null && !apartado.getCustomerPhone().isBlank()) {
                body.append(" (tel. ").append(apartado.getCustomerPhone()).append(")");
            }
            body.append(".\n\nProductos:\n").append(productLines(apartado));
            body.append("\nEntra al sistema para confirmarlo o cancelarlo.");

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Nuevo apartado #" + apartado.getId() + " — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de apartado {} enviado a {}", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Se manda al cancelarse (PENDING o ACTIVE → CANCELLED), para que el cliente no se
    // quede esperando sin saber que su apartado no se concretó. Solo se llama si el
    // cliente dejó correo al solicitarlo (ver ApartadoService#cancel) — sin eso no hay a
    // quién avisarle, y no es un requisito para poder cancelar.
    /**
     * Avisa por correo al cliente que su apartado fue cancelado, con el motivo que el
     * cajero/admin haya capturado (opcional).
     *
     * @param apartado apartado recién cancelado, con sus {@link ApartadoItem} ya cargados
     * @param reason motivo capturado al cancelar, o {@code null}/vacío si no se dio ninguno
     * @param toEmail correo del cliente al que se avisa
     */
    @Async
    public void sendApartadoCancelledEmail(Apartado apartado, String reason, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de cancelación del apartado {}", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("Hola ").append(apartado.getCustomerName()).append(",\n\n");
            body.append("Tu apartado #").append(apartado.getId()).append(" en ")
                    .append(apartado.getTienda().getName()).append(" fue cancelado y no se pudo concretar.\n\n");
            body.append("Productos:\n").append(productLines(apartado));
            if (reason != null && !reason.isBlank()) {
                body.append("\nMotivo: ").append(reason).append("\n");
            }
            body.append("\nCualquier duda, contáctanos directamente.");

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Tu apartado #" + apartado.getId() + " fue cancelado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de cancelación del apartado {} enviado a {}", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de cancelación del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    /** Lista de productos formateada para el cuerpo de los avisos de apartados ("- 2 x Anillo\n..."), evita repetir el mismo loop en cada método de abajo. */
    private String productLines(Apartado apartado) {
        StringBuilder sb = new StringBuilder();
        for (ApartadoItem item : apartado.getItems()) {
            sb.append("- ").append(item.getQuantity().stripTrailingZeros().toPlainString())
                    .append(" x ").append(item.getProductName()).append("\n");
        }
        return sb.toString();
    }

    // Al personal (no al cliente): que un apartado se confirmó, con quién lo confirmó y
    // hasta cuándo queda vigente — el resto del equipo debe saber que ya se descontó del
    // inventario y cuándo vence, sin tener que entrar al sistema a revisarlo.
    /**
     * Avisa por correo al personal de la tienda que un apartado {@code PENDING} fue
     * confirmado (pasó a {@code ACTIVE}, ya se descontó del inventario).
     *
     * @param apartado apartado recién confirmado, con {@link Apartado#getConfirmedBy()} y
     *                 {@link Apartado#getExpiresAt()} ya establecidos
     * @param toEmail correo del miembro del personal a notificar
     */
    @Async
    public void sendApartadoConfirmedStaffEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de confirmación del apartado {}", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("El apartado #").append(apartado.getId()).append(" de ")
                    .append(apartado.getCustomerName()).append(" fue confirmado");
            if (apartado.getConfirmedBy() != null) {
                body.append(" por ").append(apartado.getConfirmedBy().getName());
            }
            body.append(". Ya se descontó del inventario.\n\n");
            body.append("Productos:\n").append(productLines(apartado));
            if (apartado.getExpiresAt() != null) {
                body.append("\nVence: ").append(apartado.getExpiresAt()).append(" — si el cliente no lo recoge antes, se reintegra solo al inventario.");
            }

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Apartado #" + apartado.getId() + " confirmado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de confirmación del apartado {} enviado a personal ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de confirmación del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Al cliente: que su solicitud ya fue revisada y confirmada, con la fecha límite para
    // recogerlo — antes de esto, solo veía la pantalla de confirmación al momento de
    // solicitarlo, sin saber si alguien ya lo había revisado.
    /**
     * Avisa por correo al cliente que su apartado fue confirmado por la tienda, y hasta
     * cuándo tiene para recogerlo.
     *
     * @param apartado apartado recién confirmado
     * @param toEmail correo del cliente al que se avisa
     */
    @Async
    public void sendApartadoConfirmedCustomerEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de confirmación del apartado {} al cliente", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("Hola ").append(apartado.getCustomerName()).append(",\n\n");
            body.append("¡Buenas noticias! Tu apartado #").append(apartado.getId()).append(" en ")
                    .append(apartado.getTienda().getName()).append(" fue confirmado.\n\n");
            body.append("Productos:\n").append(productLines(apartado));
            if (apartado.getExpiresAt() != null) {
                body.append("\nTienes hasta el ").append(apartado.getExpiresAt())
                        .append(" para recogerlo y pagarlo — después de esa fecha, si no lo recoges, se libera automáticamente.");
            }

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Tu apartado #" + apartado.getId() + " fue confirmado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de confirmación del apartado {} enviado al cliente ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de confirmación del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Al personal: mismo evento que sendApartadoCancelledEmail (al cliente), pero con tono
    // interno y sin depender de si el cliente dejó correo — el equipo debe enterarse de la
    // cancelación de todas formas, la haya hecho quien la haya hecho.
    /**
     * Avisa por correo al personal de la tienda que un apartado fue cancelado, quién lo
     * canceló y el motivo capturado (si hubo alguno).
     *
     * @param apartado apartado recién cancelado, con {@link Apartado#getCancelledBy()} ya establecido
     * @param reason motivo capturado al cancelar, o {@code null}/vacío si no se dio ninguno
     * @param toEmail correo del miembro del personal a notificar
     */
    @Async
    public void sendApartadoCancelledStaffEmail(Apartado apartado, String reason, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de cancelación del apartado {} al personal", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("El apartado #").append(apartado.getId()).append(" de ")
                    .append(apartado.getCustomerName()).append(" fue cancelado");
            if (apartado.getCancelledBy() != null) {
                body.append(" por ").append(apartado.getCancelledBy().getName());
            }
            body.append(".\n\nProductos:\n").append(productLines(apartado));
            if (reason != null && !reason.isBlank()) {
                body.append("\nMotivo: ").append(reason).append("\n");
            }

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Apartado #" + apartado.getId() + " cancelado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de cancelación del apartado {} enviado a personal ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de cancelación del apartado {} a {} (personal): {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Al personal: que el cliente recogió y pagó — al cliente ya le llega su ticket aparte
    // (ver SaleService#completeFromApartado -> sendTicketEmail), no hace falta duplicarlo aquí.
    /**
     * Avisa por correo al personal de la tienda que un apartado fue completado (el
     * cliente recogió y pagó, se generó la venta correspondiente).
     *
     * @param apartado apartado recién completado, con {@link Apartado#getCompletedBy()} y
     *                 {@link Apartado#getSaleId()} ya establecidos
     * @param toEmail correo del miembro del personal a notificar
     */
    @Async
    public void sendApartadoCompletedStaffEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de conclusión del apartado {}", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("El apartado #").append(apartado.getId()).append(" de ")
                    .append(apartado.getCustomerName()).append(" se completó");
            if (apartado.getCompletedBy() != null) {
                body.append(" (lo cobró ").append(apartado.getCompletedBy().getName()).append(")");
            }
            if (apartado.getSaleId() != null) {
                body.append(" — se generó la venta #").append(apartado.getSaleId());
            }
            body.append(".\n\nProductos:\n").append(productLines(apartado));

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Apartado #" + apartado.getId() + " completado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de conclusión del apartado {} enviado a personal ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de conclusión del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Al personal: aviso de que ApartadoExpiryJob venció uno automáticamente y ya
    // reintegró el stock — sin esto, nadie del equipo se entera salvo que entre a revisar
    // la pantalla de Apartados por su cuenta.
    /**
     * Avisa por correo al personal de la tienda que un apartado {@code ACTIVE} venció
     * automáticamente (el cliente no lo recogió a tiempo) y su stock ya se reintegró.
     *
     * @param apartado apartado recién vencido
     * @param toEmail correo del miembro del personal a notificar
     */
    @Async
    public void sendApartadoExpiredStaffEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de vencimiento del apartado {}", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("El apartado #").append(apartado.getId()).append(" de ")
                    .append(apartado.getCustomerName()).append(" venció — el cliente no lo recogió a tiempo. ")
                    .append("El stock ya se reintegró automáticamente al inventario.\n\n");
            body.append("Productos:\n").append(productLines(apartado));

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Apartado #" + apartado.getId() + " venció — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de vencimiento del apartado {} enviado a personal ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de vencimiento del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }

    // Al cliente: mismo evento que arriba, en tono orientado a él — solo se llama si dejó
    // correo al solicitarlo, igual que sendApartadoCancelledEmail.
    /**
     * Avisa por correo al cliente que su apartado venció (no lo recogió a tiempo) y el
     * producto ya se liberó de vuelta al inventario de la tienda.
     *
     * @param apartado apartado recién vencido
     * @param toEmail correo del cliente al que se avisa
     */
    @Async
    public void sendApartadoExpiredCustomerEmail(Apartado apartado, String toEmail) {
        MailConfig cfg = mailConfigService.getEntity();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (mail_config.enabled=false); se omite el aviso de vencimiento del apartado {} al cliente", apartado.getId());
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            body.append("Hola ").append(apartado.getCustomerName()).append(",\n\n");
            body.append("Tu apartado #").append(apartado.getId()).append(" en ")
                    .append(apartado.getTienda().getName()).append(" venció porque no se recogió a tiempo, ")
                    .append("así que el producto ya se liberó de vuelta al inventario de la tienda.\n\n");
            body.append("Productos:\n").append(productLines(apartado));
            body.append("\nSi todavía te interesa, puedes volver a apartarlo desde el mismo link de siempre.");

            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applySenderAndReplyTo(helper, cfg, apartado.getTienda());
            helper.setTo(toEmail);
            helper.setSubject("Tu apartado #" + apartado.getId() + " venció — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            sender.send(message);
            log.info("Aviso de vencimiento del apartado {} enviado al cliente ({})", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de vencimiento del apartado {} a {} (cliente): {}", apartado.getId(), toEmail, e.getMessage());
        }
    }
}
