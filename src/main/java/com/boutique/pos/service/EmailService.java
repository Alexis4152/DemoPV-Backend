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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TicketPdfService ticketPdfService;

    // @Async: la venta ya se registró y se le respondió al vendedor; el correo se manda
    // en segundo plano y si falla (SMTP mal configurado, correo inválido, etc.) solo
    // se registra en el log, nunca revierte ni afecta la venta.
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
