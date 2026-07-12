package com.floor21.dto;

import java.math.BigDecimal;

public record BrokerQuickCreateRequest(
        String fullName, String phone, String email, BigDecimal commissionPct) {}
