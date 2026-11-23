package com.spacz.studyhall.payment.gateway;

public record GatewayCredentials(String keyId, String keySecret, String webhookSecret) {

    /** Never print secrets (e.g. in a log line or an exception message). */
    @Override
    public String toString() {
        return "GatewayCredentials[keyId=" + keyId + "]";
    }
}
