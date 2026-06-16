package com.floor21.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

/** Verifies indexed {@code lines[n].dueDate} binding for milestone save POST bodies. */
class ClientMilestoneSaveFormBindingTest {

    @Test
    void bindsIndexedDueDateAndAgreedAmount() {
        UUID lineId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("bookingId", UUID.randomUUID().toString());
        request.addParameter("lines[0].id", lineId.toString());
        request.addParameter("lines[0].dueDate", "2026-03-28");
        request.addParameter("lines[0].milestoneLabel", "Test slab");
        request.addParameter("lines[0].percent", "10");
        request.addParameter("lines[0].agreedAmount", "99999");
        request.addParameter("lines[0].extraAmount", "0");

        BookingPaymentSlabBatchForm form = new BookingPaymentSlabBatchForm();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(form, "saveForm");
        binder.setAutoGrowCollectionLimit(512);
        binder.registerCustomEditor(
                LocalDate.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(text == null || text.isBlank() ? null : LocalDate.parse(text.trim()));
                    }
                });
        binder.registerCustomEditor(
                UUID.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(text == null || text.isBlank() ? null : UUID.fromString(text.trim()));
                    }
                });
        binder.registerCustomEditor(
                BigDecimal.class,
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(
                                text == null || text.isBlank()
                                        ? null
                                        : new BigDecimal(text.replace(",", "").trim()));
                    }
                });

        binder.bind(request);

        assertThat(form.getLines()).hasSize(1);
        assertThat(form.getLines().getFirst().getId()).isEqualTo(lineId);
        assertThat(form.getLines().getFirst().getDueDate()).isEqualTo(LocalDate.of(2026, 3, 28));
        assertThat(form.getLines().getFirst().getAgreedAmount()).isEqualByComparingTo("99999");
    }
}
