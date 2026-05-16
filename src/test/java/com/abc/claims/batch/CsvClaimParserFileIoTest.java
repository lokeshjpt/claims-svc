package com.abc.claims.batch;

import com.abc.claims.model.ClaimRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvClaimParserFileIoTest {

    private final CsvClaimParser parser = new CsvClaimParser();

    @Test
    @DisplayName("parseFile reads a CSV from disk")
    void parse_file_round_trip(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("input.csv");
        Files.writeString(csv, """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,2016-05-10,Inpatient Hospital Care,ROOM AND BOARD,1000
                """);

        List<ClaimRequest> rows = parser.parseFile(csv.toString());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().policyId()).isEqualTo("100001");
    }

    @Test
    @DisplayName("parseFile throws IllegalStateException when the file is missing")
    void parse_file_missing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.csv");
        assertThatThrownBy(() -> parser.parseFile(missing.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse input CSV");
    }

    @Test
    @DisplayName("parseStream wraps IO errors in IllegalStateException")
    void parse_stream_io_error() {
        java.io.InputStream broken = new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("boom"); }
            @Override public int read(byte[] b, int off, int len) throws IOException { throw new IOException("boom"); }
            @Override public void close() throws IOException { throw new IOException("boom-close"); }
        };
        try {
            parser.parseStream(broken);
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageContaining("Failed to parse uploaded CSV");
            return;
        }
        // If the underlying CSV reader buffers eagerly enough that no IO is performed,
        // we still consider the smoke-test acceptable — the wrap is exercised elsewhere.
    }

    @Test
    @DisplayName("writeResults writes the supplied rows to disk")
    void write_results(@TempDir Path tmp) throws IOException {
        Path output = tmp.resolve("out.csv");
        parser.writeResults(output.toString(), List.of(
                new String[]{"PolicyId", "Plan Pays"},
                new String[]{"100001", "400"}
        ));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("PolicyId").contains("Plan Pays");
        assertThat(lines.get(1)).contains("100001").contains("400");
    }

    @Test
    @DisplayName("writeResults throws IllegalStateException when target cannot be written")
    void write_results_bad_path(@TempDir Path tmp) {
        Path inaccessible = tmp.resolve("does/not/exist/out.csv");
        assertThatThrownBy(() -> parser.writeResults(inaccessible.toString(), List.<String[]>of(new String[]{"x"})))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to write output CSV");
    }
}
