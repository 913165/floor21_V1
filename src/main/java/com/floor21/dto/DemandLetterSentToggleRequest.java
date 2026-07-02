package com.floor21.dto;

import java.util.UUID;

public record DemandLetterSentToggleRequest(UUID bookingId, UUID slabId, boolean sent) {}
