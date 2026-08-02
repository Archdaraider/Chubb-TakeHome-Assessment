package com.archdaraider.chubb.claims.claim.application;

import java.math.BigDecimal;

public record ExposureItem(String currency, BigDecimal amount, long claimCount) {}
