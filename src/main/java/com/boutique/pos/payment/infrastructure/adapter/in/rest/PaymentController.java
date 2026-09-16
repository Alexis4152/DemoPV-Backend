package com.boutique.pos.payment.infrastructure.adapter.in.rest;

import com.boutique.pos.model.User;
import com.boutique.pos.payment.application.port.in.CreatePaymentUseCase;
import com.boutique.pos.payment.application.port.in.GetPaymentStatusUseCase;
import com.boutique.pos.payment.application.port.in.RefundCommand;
import com.boutique.pos.payment.application.port.in.RefundPaymentUseCase;
import com.boutique.pos.payment.domain.model.PaymentMethod;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.CreatePaymentRequest;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.PaymentResponse;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.RefundRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Pagos Openpay", description = "Operaciones para procesamiento, consulta y reembolso con la pasarela Openpay")
@SecurityRequirement(name = "BearerAuth")
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final GetPaymentStatusUseCase getPaymentStatusUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final PaymentRestMapper mapper;

    public PaymentController(
            CreatePaymentUseCase createPaymentUseCase,
            GetPaymentStatusUseCase getPaymentStatusUseCase,
            RefundPaymentUseCase refundPaymentUseCase,
            PaymentRestMapper mapper
    ) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.getPaymentStatusUseCase = getPaymentStatusUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Crear cargo o intención de pago", description = "Procesa un pago con tarjeta (token), genera referencia en tiendas Paynet o genera CLABE para SPEI.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago creado o procesado satisfactoriamente",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o faltantes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Tarjeta declinada o fondos insuficientes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Error de comunicación con la pasarela Openpay",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal User actor
    ) {
        if (request.method() == PaymentMethod.CARD && (request.sourceId() == null || request.sourceId().isBlank())) {
            throw new IllegalArgumentException("Para pagos con tarjeta ('CARD') el campo 'sourceId' (token de tarjeta) es obligatorio.");
        }

        Long tiendaId = (actor != null && actor.getTienda() != null) ? actor.getTienda().getId() : null;
        var command = mapper.toCommand(request, tiendaId);
        PaymentTransaction transaction = createPaymentUseCase.execute(command);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transaction.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(transaction));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar estado de un pago", description = "Obtiene la información y estatus actual de una transacción por su ID interno.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transacción encontrada",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transacción no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable("id") String id) {
        PaymentTransaction transaction = getPaymentStatusUseCase.execute(id);
        return ResponseEntity.ok(mapper.toResponse(transaction));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reembolsar pago", description = "Aplica un reembolso total o parcial sobre una transacción previamente completada (restringido a ADMIN).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reembolso procesado exitosamente",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transacción no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Operación inválida (pago no completado o monto excede el saldo)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable("id") String id,
            @Valid @RequestBody RefundRequest request
    ) {
        var command = new RefundCommand(request.amount(), request.reason());
        PaymentTransaction transaction = refundPaymentUseCase.execute(id, command);
        return ResponseEntity.ok(mapper.toResponse(transaction));
    }
}
