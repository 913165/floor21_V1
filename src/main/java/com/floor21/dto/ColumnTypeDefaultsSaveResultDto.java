package com.floor21.dto;

import java.util.List;
import java.util.Map;

public record ColumnTypeDefaultsSaveResultDto(
        Map<String, ColumnTypeDefaultsDto> defaults, List<Map<String, Object>> updatedFlats) {}
