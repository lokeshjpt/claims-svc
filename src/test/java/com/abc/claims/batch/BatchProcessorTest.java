package com.abc.claims.batch;

import com.abc.claims.engine.ClaimsEngine;
import com.abc.claims.model.ClaimResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class BatchProcessorTest {

    @Autowired
    BatchProcessor batchProcessor;

    @Autowired
    CsvClaimParser csvClaimParser;

    @Test
    @DisplayName("processFile reads input CSV and writes a result CSV")
    void processFile_round_trip(@TempDir Path tmp) throws IOException {
        Path input = tmp.resolve("in.csv");
        Path output = tmp.resolve("out.csv");
        Files.writeString(input, """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100002,1000021,2016-06-15,Preventive Care,ROUTINE PHYSICAL EXAM,350
                """);

        batchProcessor.processFile(input.toString(), output.toString());

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSizeGreaterThan(1);
        assertThat(lines.get(0)).contains("PolicyId").contains("Plan Pays").contains("Processing message");
        assertThat(lines.get(1)).contains("100002").contains("No Charge");
    }

    @Test
    @DisplayName("processRequests builds the header row + a row per claim result")
    void processRequests_includes_header_and_rows() {
        ClaimsEngine engine = mock(ClaimsEngine.class);
        when(engine.processForBatchOrGui(any())).thenReturn(new ClaimResult(
                "100001", "1000011", java.time.LocalDate.of(2016, 5, 8),
                "Inpatient Hospital Care", "ROOM AND BOARD",
                new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("400"),
                "40% AFTER DEDUCTIBLE", new BigDecimal("1000"), new BigDecimal("1500"),
                null, null, "OK"));
        BatchProcessor isolated = new BatchProcessor(engine, csvClaimParser, mock(com.abc.claims.audit.ClaimAuditLogger.class));

        List<String[]> rows = isolated.processRequests(List.of(
                new com.abc.claims.model.ClaimRequest(
                        "100001", "1000011", java.time.LocalDate.of(2016, 5, 8),
                        "Inpatient Hospital Care", "ROOM AND BOARD",
                        new BigDecimal("1000"), null, null)
        ));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).contains("Policy Holder pays", "Processing message");
        assertThat(rows.get(1)).contains("100001", "600", "400", "40% AFTER DEDUCTIBLE", "OK");
    }

    @Test
    @DisplayName("processRequests writes empty strings for null money / null fields")
    void processRequests_handles_nulls() {
        ClaimsEngine engine = mock(ClaimsEngine.class);
        when(engine.processForBatchOrGui(any())).thenReturn(new ClaimResult(
                "100001", "1000011", null, null, null, null, null, null,
                null, null, null, "E0001", "Policy holder does not exist", null));
        BatchProcessor isolated = new BatchProcessor(engine, csvClaimParser, mock(com.abc.claims.audit.ClaimAuditLogger.class));

        List<String[]> rows = isolated.processRequests(List.of(
                new com.abc.claims.model.ClaimRequest("100001", "1000011", null, null, null, null, null, null)));

        assertThat(rows.get(1)).contains("E0001", "");
    }

    @Test
    @DisplayName("processFile throws when input cannot be read")
    void processFile_missing_input_throws(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.csv");
        Path output = tmp.resolve("out.csv");
        assertThatThrownBy(() -> batchProcessor.processFile(missing.toString(), output.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse input CSV");
    }
}
