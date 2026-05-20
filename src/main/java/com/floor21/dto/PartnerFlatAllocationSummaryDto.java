package com.floor21.dto;

import java.util.UUID;

public record PartnerFlatAllocationSummaryDto(UUID partnerId, String partnerName, int assignedCount) {}
