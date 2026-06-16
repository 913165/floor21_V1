package com.floor21.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MilestoneScheduleSaveFormParserTest {

    @Test
    void parsesIndexedLineFieldsFromPostBody() {
        UUID bookingId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("bookingId", bookingId.toString());
        request.addParameter("lines[0].id", lineId.toString());
        request.addParameter("lines[0].dueDate", "2026-03-28");
        request.addParameter("lines[0].milestoneLabel", "Test slab");
        request.addParameter("lines[0].percent", "10");
        request.addParameter("lines[0].agreedAmount", "99999");
        request.addParameter("lines[0].extraAmount", "0");
        request.addParameter("lines[1].id", UUID.randomUUID().toString());
        request.addParameter("lines[1].dueDate", "2026-04-01");
        request.addParameter("lines[1].milestoneLabel", "Second");
        request.addParameter("lines[1].percent", "20");
        request.addParameter("lines[1].agreedAmount", "1000");
        request.addParameter("lines[1].extraAmount", "0");

        BookingPaymentSlabBatchForm form = MilestoneScheduleSaveFormParser.parse(request);

        assertThat(form.getBookingId()).isEqualTo(bookingId);
        assertThat(form.getLines()).hasSize(2);
        assertThat(form.getLines().get(0).getId()).isEqualTo(lineId);
        assertThat(form.getLines().get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 3, 28));
        assertThat(form.getLines().get(1).getDueDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void usesLastNonBlankWhenDuplicateParametersExist() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("bookingId", UUID.randomUUID().toString());
        request.addParameter("lines[0].id", UUID.randomUUID().toString());
        request.addParameter("lines[0].dueDate", "");
        request.addParameter("lines[0].dueDate", "2026-03-28");
        request.addParameter("lines[0].milestoneLabel", "Slab");
        request.addParameter("lines[0].percent", "10");
        request.addParameter("lines[0].agreedAmount", "100");
        request.addParameter("lines[0].extraAmount", "0");

        BookingPaymentSlabBatchForm form = MilestoneScheduleSaveFormParser.parse(request);

        assertThat(form.getLines().getFirst().getDueDate()).isEqualTo(LocalDate.of(2026, 3, 28));
    }
}
