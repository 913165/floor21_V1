package com.floor21.dto;

import java.util.List;
import java.util.Map;

public record UnitTypeDefaultsSaveResultDto(
        Map<String, UnitTypeDefaultsDto> defaults, List<Map<String, Object>> updatedFlats) {}
