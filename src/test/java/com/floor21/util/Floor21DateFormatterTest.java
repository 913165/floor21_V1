package com.floor21.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Floor21DateFormatterTest {

    @Test
    void formatDisplay_usesThreeLetterMonth() {
        assertThat(Floor21DateFormatter.formatDisplay(LocalDate.of(2026, 6, 28))).isEqualTo("28-Jun-2026");
    }

    @Test
    void parseDisplay_acceptsDisplayFormat() {
        assertThat(Floor21DateFormatter.parseDisplay("28-Jun-2026")).isEqualTo(LocalDate.of(2026, 6, 28));
    }

    @Test
    void parseDisplay_acceptsIso() {
        assertThat(Floor21DateFormatter.parseDisplay("2026-06-28")).isEqualTo(LocalDate.of(2026, 6, 28));
    }

    @Test
    void parseDisplay_stillAcceptsLegacyNumericFormat() {
        assertThat(Floor21DateFormatter.parseDisplay("28-06-2026")).isEqualTo(LocalDate.of(2026, 6, 28));
    }

    @Test
    void parseDisplay_rejectsInvalid() {
        assertThatThrownBy(() -> Floor21DateFormatter.parseDisplay("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
