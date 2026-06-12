package com.floor21.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FlatPriceUpdateDto(
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal basePrice) {}
