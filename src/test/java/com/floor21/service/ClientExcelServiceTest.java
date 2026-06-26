package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientExcelServiceTest {

    private final ClientExcelService service = new ClientExcelService(null, null);

    @Test
    void parse_readsSampleTemplateRow() throws Exception {
        byte[] template = service.buildImportTemplate();
        List<com.floor21.dto.ClientImportRow> rows = service.parse(new ByteArrayInputStream(template));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().firstName()).isEqualTo("Rahul");
        assertThat(rows.getFirst().lastName()).isEqualTo("Sharma");
        assertThat(rows.getFirst().city()).isEqualTo("Mumbai");
        assertThat(rows.getFirst().phone1()).isEqualTo("9876543210");
        assertThat(rows.getFirst().panNumber()).isEqualTo("ABCDE1234F");
        assertThat(rows.getFirst().dob()).isEqualTo(LocalDate.of(1990, 6, 15));
    }

    @Test
    void parse_allowsPartialRowWithoutCity() throws Exception {
        byte[] template = service.buildImportTemplate();
        List<com.floor21.dto.ClientImportRow> rows = service.parse(new ByteArrayInputStream(template));
        assertThat(rows).hasSize(1);

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(template))) {
            var sheet = wb.getSheetAt(0);
            var partial = sheet.createRow(2);
            partial.createCell(1).setCellValue("Vipul");
            partial.createCell(2).setCellValue("Patel");
            partial.createCell(8).setCellValue("9876543210");
            var out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            rows = service.parse(new ByteArrayInputStream(out.toByteArray()));
        }

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).firstName()).isEqualTo("Vipul");
        assertThat(rows.get(1).city()).isBlank();
    }

    @Test
    void exportClients_writesRowsMatchingImportLayout() throws Exception {
        var client = new com.floor21.entity.Client();
        client.setFirstName("Vipul");
        client.setLastName("Patel");
        client.setCity("Navi Mumbai");
        client.setMobile1("9876543210");
        client.setDob(LocalDate.of(1985, 3, 10));

        byte[] exported = service.exportClients(java.util.List.of(client), false);
        List<com.floor21.dto.ClientImportRow> rows = service.parse(new ByteArrayInputStream(exported));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().firstName()).isEqualTo("Vipul");
        assertThat(rows.getFirst().city()).isEqualTo("Navi Mumbai");
    }
}
