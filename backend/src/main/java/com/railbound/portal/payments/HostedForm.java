package com.railbound.portal.payments;

import java.util.Map;

/** Everything the browser needs to POST the customer to a provider's hosted checkout page. */
public record HostedForm(String action, Map<String, String> fields) {}
