package com.boutique.pos.service;

import com.boutique.pos.model.Sale;
import com.boutique.pos.model.Tienda;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correos del sistema: ticket de compra en PDF al cliente y reporte de cierre
 * de caja en Excel al administrador de la tienda.
 *
 * <p>Ambos envíos son asíncronos ({@code @Async}) y "best effort": un fallo de correo
 * (SMTP caído, dirección inválida, etc.) solo se registra en el log y nunca revierte ni
 * bloquea la operación de negocio que lo disparó (la venta ya quedó registrada, el corte
 * ya quedó cerrado).</p>
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
}
