package com.boutique.pos.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openpay")
public class OpenpayProperties {

    private String merchantId;
    private String privateKey;
    private String publicKey;
    private String baseUrl = "https://sandbox-api.openpay.mx/v1";
    private boolean production = false;
    private int timeoutSeconds = 15;
    private String webhookUser;
    private String webhookPassword;

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isProduction() { return production; }
    public void setProduction(boolean production) { this.production = production; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getWebhookUser() { return webhookUser; }
    public void setWebhookUser(String webhookUser) { this.webhookUser = webhookUser; }

    public String getWebhookPassword() { return webhookPassword; }
    public void setWebhookPassword(String webhookPassword) { this.webhookPassword = webhookPassword; }
}
