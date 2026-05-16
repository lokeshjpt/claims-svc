package com.abc.claims.batch;

import com.abc.claims.model.ClaimRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvClaimParserTest {

    private final CsvClaimParser parser = new CsvClaimParser();

    @Test
    @DisplayName("parses ISO date CSV into ClaimRequest records")
    void parses_iso_date_csv() {
        String csv = """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,2016-05-10,Inpatient Hospital Care,ROOM AND BOARD,1000
                """;

        List<ClaimRequest> rows = parser.parseStream(stream(csv));

        assertThat(rows).hasSize(1);
        ClaimRequest only = rows.get(0);
        assertThat(only.policyId()).isEqualTo("100001");
        assertThat(only.policyHolderId()).isEqualTo("1000011");
        assertThat(only.dateOfService()).isEqualTo(LocalDate.of(2016, 5, 10));
        assertThat(only.coverageMainCategory()).isEqualTo("Inpatient Hospital Care");
        assertThat(only.coverageSubCategory()).isEqualTo("ROOM AND BOARD");
        assertThat(only.billedAmount()).isEqualByComparingTo("1000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2016-05-10", "5/10/2016", "05/10/2016", "2016/5/10"})
    @DisplayName("accepts multiple common date formats")
    void accepts_common_date_formats(String dateValue) {
        String csv = "PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount\n"
                + "100001,1000011," + dateValue + ",Inpatient Hospital Care,ROOM AND BOARD,1000\n";

        List<ClaimRequest> rows = parser.parseStream(stream(csv));

        assertThat(rows).singleElement()
                .extracting(ClaimRequest::dateOfService)
                .isEqualTo(LocalDate.of(2016, 5, 10));
    }

    @Test
    @DisplayName("lenient parsing: unsupported date format yields null DOS (engine surfaces E0005)")
    void lenient_unsupported_date_format() {
        String csv = """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,not-a-date,Inpatient Hospital Care,ROOM AND BOARD,1000
                """;

        List<ClaimRequest> rows = parser.parseStream(stream(csv));

        assertThat(rows).singleElement()
                .extracting(ClaimRequest::dateOfService)
                .isNull();
    }

    @Test
    @DisplayName("lenient parsing: unparseable billed amount yields null (engine surfaces E0005)")
    void lenient_unparseable_amount() {
        String csv = """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,2016-05-10,Inpatient Hospital Care,ROOM AND BOARD,not-a-number
                """;

        List<ClaimRequest> rows = parser.parseStream(stream(csv));

        assertThat(rows).singleElement()
                .extracting(ClaimRequest::billedAmount)
                .isNull();
    }

    @Test
    @DisplayName("returns empty list for an empty stream")
    void empty_stream_returns_empty_list() {
        assertThat(parser.parseStream(stream(""))).isEmpty();
    }

    @Test
    @DisplayName("treats blank billed amount as zero")
    void blank_billed_amount_is_zero() {
        String csv = """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,2016-05-10,Inpatient Hospital Care,ROOM AND BOARD,
                """;

        List<ClaimRequest> rows = parser.parseStream(stream(csv));

        assertThat(rows).singleElement()
                .extracting(ClaimRequest::billedAmount)
                .isEqualTo(BigDecimal.ZERO);
    }

    private ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
