package com.restorant.config;

import com.restorant.validation.PhoneNumbers;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How reservation packages are paid. Only the manual GCash transfer flow exists: the customer sends money to
 * the restaurant's GCash number and staff verify it against the receiving account. An automatic provider
 * (see README, "Enabling automatic payment confirmation") needs merchant credentials this project does not have.
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentSettings(String provider, String gcashNumber, String gcashAccountName) {
    public static final String MANUAL = "manual";
    public PaymentSettings {
        provider = provider == null || provider.isBlank() ? MANUAL : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!MANUAL.equals(provider))
            throw new IllegalArgumentException("PAYMENT_PROVIDER '" + provider + "' is not implemented. Only 'manual' (admin-verified GCash transfer) is available; see the README before enabling a provider.");
        gcashNumber = PhoneNumbers.local(PhoneNumbers.normalize(gcashNumber));
        gcashAccountName = gcashAccountName == null ? "" : gcashAccountName.trim();
    }
}
