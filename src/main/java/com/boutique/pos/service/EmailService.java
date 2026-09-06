package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoItem;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correos del sistema: ticket de compra en PDF al cliente, reporte de cierre
 * de caja en Excel al administrador de la tienda, y el link de recuperación de
 * contraseña.
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

    private final JavaMailSender mailSender;
    private final TicketPdfService ticketPdfService;

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
        try {
            byte[] pdf = ticketPdfService.generate(sale);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Tu ticket de compra #" + sale.getId());
            helper.setText("Gracias por tu compra. Adjuntamos tu ticket en PDF.");
            helper.addAttachment("ticket-" + sale.getId() + ".pdf", new org.springframework.core.io.ByteArrayResource(pdf));

            mailSender.send(message);
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Reporte de cierre de caja — " + tienda.getName());
            helper.setText(
                    "Se cerraron " + cutCount + " corte(s) de caja en " + tienda.getName() + ".\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            helper.addAttachment("cierre-caja-" + tienda.getId() + ".xlsx",
                    new org.springframework.core.io.ByteArrayResource(excelBytes));

            mailSender.send(message);
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("Recupera tu contraseña");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña. Este link es válido por 30 minutos:\n\n" +
                    resetLink + "\n\n" +
                    "Si tú no pediste esto, puedes ignorar este correo — tu contraseña sigue siendo la misma."
            );

            mailSender.send(message);
            log.info("Correo de recuperación de contraseña enviado a {}", user.getEmail());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo de recuperación de contraseña a {}: {}", user.getEmail(), e.getMessage());
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
        try {
            StringBuilder body = new StringBuilder();
            body.append("Nuevo apartado de ").append(apartado.getCustomerName());
            if (apartado.getCustomerPhone() != null && !apartado.getCustomerPhone().isBlank()) {
                body.append(" (tel. ").append(apartado.getCustomerPhone()).append(")");
            }
            body.append(".\n\nProductos:\n");
            for (ApartadoItem item : apartado.getItems()) {
                body.append("- ").append(item.getQuantity().stripTrailingZeros().toPlainString())
                        .append(" x ").append(item.getProductName()).append("\n");
            }
            body.append("\nEntra al sistema para confirmarlo o cancelarlo.");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Nuevo apartado #" + apartado.getId() + " — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            mailSender.send(message);
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
        try {
            StringBuilder body = new StringBuilder();
            body.append("Hola ").append(apartado.getCustomerName()).append(",\n\n");
            body.append("Tu apartado #").append(apartado.getId()).append(" en ")
                    .append(apartado.getTienda().getName()).append(" fue cancelado y no se pudo concretar.\n\n");
            body.append("Productos:\n");
            for (ApartadoItem item : apartado.getItems()) {
                body.append("- ").append(item.getQuantity().stripTrailingZeros().toPlainString())
                        .append(" x ").append(item.getProductName()).append("\n");
            }
            if (reason != null && !reason.isBlank()) {
                body.append("\nMotivo: ").append(reason).append("\n");
            }
            body.append("\nCualquier duda, contáctanos directamente.");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Tu apartado #" + apartado.getId() + " fue cancelado — " + apartado.getTienda().getName());
            helper.setText(body.toString());

            mailSender.send(message);
            log.info("Aviso de cancelación del apartado {} enviado a {}", apartado.getId(), toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el aviso de cancelación del apartado {} a {}: {}", apartado.getId(), toEmail, e.getMessage());
        }
    }
}
