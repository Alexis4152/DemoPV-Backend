package com.boutique.pos.payment.infrastructure.adapter.out.openpay;

import com.boutique.pos.payment.application.port.out.GatewayChargeCommand;
import com.boutique.pos.payment.application.port.out.GatewayChargeResult;
import com.boutique.pos.payment.application.port.out.PaymentGatewayPort;
import com.boutique.pos.payment.domain.exception.PaymentDeclinedException;
import com.boutique.pos.payment.domain.exception.PaymentGatewayException;
import com.boutique.pos.payment.domain.model.PaymentMethod;
import com.boutique.pos.payment.domain.model.PaymentMethodDetails;
import com.boutique.pos.payment.domain.model.PaymentStatus;
import com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto.*;
import com.boutique.pos.payment.infrastructure.config.OpenpayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class OpenpayPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(OpenpayPaymentGatewayAdapter.class);

    private final RestClient restClient;
    private final OpenpayProperties properties;
    private final ObjectMapper objectMapper;

    public OpenpayPaymentGatewayAdapter(OpenpayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        var requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 15;
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeout));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));

        String rootUrl = (properties.getBaseUrl() != null ? properties.getBaseUrl().replaceAll("/+$", "") : "https://sandbox-api.openpay.mx/v1")
                + "/" + (properties.getMerchantId() != null ? properties.getMerchantId() : "");

        this.restClient = RestClient.builder()
                .baseUrl(rootUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    String privateKey = properties.getPrivateKey() != null ? properties.getPrivateKey() : "";
                    headers.setBasicAuth(privateKey, "", StandardCharsets.UTF_8);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set(headers.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .build();
    }

    @Override
    public GatewayChargeResult createCharge(GatewayChargeCommand command) {
        log.info("Enviando solicitud de cargo a Openpay para orderId: {}, método: {}", command.orderId(), command.method());

        String openpayMethod = switch (command.method()) {
            case CARD -> "card";
            case STORE -> "store";
            case SPEI -> "bank_account";
        };

        OpenpayCustomerDto customerDto = null;
        if (command.customer() != null) {
            customerDto = new OpenpayCustomerDto(
                    command.customer().name(),
                    command.customer().lastName(),
                    command.customer().email(),
                    command.customer().phoneNumber()
            );
        }

        // Para tarjeta, confirm es true para cobro directo con token.
        // Para SPEI y Tienda, confirm es false (se confirma asíncronamente al pagar el cliente).
        Boolean confirm = command.method() == PaymentMethod.CARD;

        var requestPayload = new OpenpayChargeRequest(
                openpayMethod,
                command.sourceId(),
                command.amount(),
                command.currency(),
                command.description(),
                command.orderId(),
                command.deviceSessionId(),
                customerDto,
                confirm
        );

        try {
            OpenpayChargeResponse response = restClient.post()
                    .uri("/charges")
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        byte[] bodyBytes = resp.getBody().readAllBytes();
                        handleGatewayError(resp.getStatusCode().value(), new String(bodyBytes, StandardCharsets.UTF_8));
                    })
                    .body(OpenpayChargeResponse.class);

            if (response == null) {
                throw new PaymentGatewayException("Respuesta vacía recibida desde la pasarela Openpay");
            }

            return mapToResult(response);

        } catch (RestClientResponseException ex) {
            handleGatewayError(ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Error de comunicación con pasarela: " + ex.getMessage(), ex);
        } catch (PaymentDeclinedException | PaymentGatewayException pex) {
            throw pex;
        } catch (Exception ex) {
            log.error("Error inesperado comunicando con Openpay", ex);
            throw new PaymentGatewayException("Error inesperado comunicando con Openpay: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayChargeResult getChargeStatus(String openpayTransactionId) {
        log.info("Consultando estatus de transacción en Openpay para ID: {}", openpayTransactionId);

        try {
            OpenpayChargeResponse response = restClient.get()
                    .uri("/charges/{id}", openpayTransactionId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        byte[] bodyBytes = resp.getBody().readAllBytes();
                        handleGatewayError(resp.getStatusCode().value(), new String(bodyBytes, StandardCharsets.UTF_8));
                    })
                    .body(OpenpayChargeResponse.class);

            if (response == null) {
                throw new PaymentGatewayException("Respuesta vacía de Openpay para transacción: " + openpayTransactionId);
            }

            return mapToResult(response);

        } catch (RestClientResponseException ex) {
            handleGatewayError(ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Error consultando transacción en Openpay: " + ex.getMessage(), ex);
        } catch (PaymentDeclinedException | PaymentGatewayException pex) {
            throw pex;
        } catch (Exception ex) {
            log.error("Error inesperado verificando estado del cargo en Openpay", ex);
            throw new PaymentGatewayException("Error inesperado verificando estado del cargo en Openpay: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayChargeResult refundCharge(String openpayTransactionId, BigDecimal amount, String reason) {
        log.info("Reembolsando cargo en Openpay para ID: {}, monto: {}", openpayTransactionId, amount);

        var refundRequest = new OpenpayRefundRequest(reason, amount);

        try {
            OpenpayChargeResponse response = restClient.post()
                    .uri("/charges/{id}/refund", openpayTransactionId)
                    .body(refundRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        byte[] bodyBytes = resp.getBody().readAllBytes();
                        handleGatewayError(resp.getStatusCode().value(), new String(bodyBytes, StandardCharsets.UTF_8));
                    })
                    .body(OpenpayChargeResponse.class);

            if (response == null) {
                throw new PaymentGatewayException("Respuesta vacía recibida desde Openpay durante el reembolso");
            }

            return mapToResult(response);

        } catch (RestClientResponseException ex) {
            handleGatewayError(ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Error durante reembolso con Openpay: " + ex.getMessage(), ex);
        } catch (PaymentDeclinedException | PaymentGatewayException pex) {
            throw pex;
        } catch (Exception ex) {
            log.error("Error inesperado ejecutando reembolso en Openpay", ex);
            throw new PaymentGatewayException("Error inesperado ejecutando reembolso en Openpay: " + ex.getMessage(), ex);
        }
    }

    private void handleGatewayError(int httpStatus, String responseBody) {
        log.warn("Openpay respondió con HTTP {} y cuerpo: {}", httpStatus, responseBody);

        try {
            OpenpayErrorResponse error = objectMapper.readValue(responseBody, OpenpayErrorResponse.class);
            if (error != null) {
                Integer errorCode = error.errorCode();
                String desc = error.description() != null ? error.description() : "Error desconocido en pasarela";

                // Códigos 3001 a 3012 representan tarjetas declinadas, expiradas, fondos insuficientes o sospecha de fraude
                if (errorCode != null && (errorCode >= 3000 && errorCode < 4000)) {
                    throw new PaymentDeclinedException(errorCode, desc);
                }

                throw new PaymentGatewayException("Error Openpay [" + errorCode + "]: " + desc, httpStatus, errorCode);
            }
        } catch (PaymentDeclinedException | PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("No fue posible parsear el cuerpo de error de Openpay: {}", responseBody, ex);
        }

        throw new PaymentGatewayException("Error en Openpay con código HTTP: " + httpStatus, httpStatus);
    }

    private GatewayChargeResult mapToResult(OpenpayChargeResponse response) {
        PaymentStatus status = switch (response.status().toLowerCase()) {
            case "completed" -> PaymentStatus.COMPLETED;
            case "in_progress" -> PaymentStatus.IN_PROGRESS;
            case "failed" -> PaymentStatus.FAILED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            case "refunded" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.PENDING;
        };

        PaymentMethodDetails methodDetails = null;
        if (response.paymentMethod() != null) {
            var pm = response.paymentMethod();
            if (pm.reference() != null || pm.barcodeUrl() != null) {
                methodDetails = PaymentMethodDetails.forStore(pm.reference(), pm.barcodeUrl());
            } else if (pm.clabe() != null) {
                methodDetails = PaymentMethodDetails.forSpei(pm.clabe(), pm.bank());
            } else if (pm.url() != null) {
                methodDetails = PaymentMethodDetails.for3DSecure(pm.url());
            }
        }

        return new GatewayChargeResult(
                response.id(),
                status,
                response.amount(),
                response.currency(),
                response.authorization(),
                methodDetails != null ? methodDetails : PaymentMethodDetails.empty(),
                response.errorMessage()
        );
    }
}
