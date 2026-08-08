package in.rsh.cab.payment;

import java.util.UUID;

public record PaymentAccount(
    UUID id, UUID tenantId, String provider, String configReference,
    String webhookSecretReference, boolean active) {}
