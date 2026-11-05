package com.railbound.portal.payments;

import com.railbound.portal.common.InvalidStateException;

import java.util.regex.Pattern;

/**
 * Customer contact details that PayHere requires on every checkout. They are validated and passed
 * straight through to the provider form; they are NOT stored in our database.
 */
public record BillingDetails(
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,200}\\.[^@\\s]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()+\\-]{6,19}$");

    /** Returns a trimmed, control-character-free copy, or throws InvalidStateException (HTTP 400). */
    public BillingDetails validated() {
        BillingDetails clean = new BillingDetails(
                clean(firstName), clean(lastName), clean(email), clean(phone),
                clean(address), clean(city), clean(country));

        require(clean.firstName, "First name", 50);
        require(clean.lastName, "Last name", 50);
        require(clean.email, "Email", 254);
        require(clean.phone, "Phone number", 20);
        require(clean.address, "Address", 120);
        require(clean.city, "City", 60);
        require(clean.country, "Country", 60);

        if (!EMAIL.matcher(clean.email).matches()) {
            throw new InvalidStateException("Please enter a valid email address");
        }
        if (!PHONE.matcher(clean.phone).matches()) {
            throw new InvalidStateException("Please enter a valid phone number");
        }
        return clean;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        // Strip control characters so nothing odd is echoed into the provider form.
        return value.replaceAll("\\p{Cntrl}", " ").trim();
    }

    private static void require(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidStateException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new InvalidStateException(label + " is too long (max " + maxLength + " characters)");
        }
    }
}
