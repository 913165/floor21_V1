package com.floor21.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResidentialBhkTypesTest {

    @Test
    void defaultColumnOrderGroupsByType() {
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("2BHK", 3);
        mix.put("3BHK", 2);
        assertEquals(
                List.of("2BHK", "2BHK", "2BHK", "3BHK", "3BHK"),
                ResidentialBhkTypes.defaultColumnOrder(mix));
    }

    @Test
    void resolveColumnOrderUsesCustomOrderWhenValid() {
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("2BHK", 2);
        mix.put("3BHK", 3);
        List<String> custom = List.of("3BHK", "2BHK", "3BHK", "2BHK", "3BHK");
        assertEquals(custom, ResidentialBhkTypes.resolveColumnOrder(custom, mix, 5));
    }

    @Test
    void validateColumnOrderRejectsMismatchedCounts() {
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("2BHK", 3);
        mix.put("3BHK", 2);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ResidentialBhkTypes.validateColumnOrder(
                                List.of("3BHK", "3BHK", "3BHK", "2BHK", "2BHK"), mix, 5));
    }
}
