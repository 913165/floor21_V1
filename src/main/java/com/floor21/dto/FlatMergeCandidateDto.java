package com.floor21.dto;

import java.util.UUID;

public record FlatMergeCandidateDto(
        UUID id,
        String flatNumber,
        int floorNumber,
        String bhkType,
        String status,
        boolean verticalDuplex) {}
