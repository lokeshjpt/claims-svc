package com.abc.claims.engine;

import com.abc.claims.batch.BatchProcessor;
import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClaimsEngineIntegrationTest {

    @Autowired
    BatchProcessor batchProcessor;

    @Autowired
    ClaimsEngine engine;

    private static ClaimRequest req(String holderId, String date, String main, String sub, String billed) {
        return new ClaimRequest(
                "100001", holderId, LocalDate.parse(date), main, sub, new BigDecimal(billed), null, null);
    }

    @Test
    void replays_sample_transactions_from_workbook() {
        List<ClaimResult> results = engine.processBatch(List.of(
                req("1000011", "2016-04-21", "Inpatient Hospital Care", "ROOM AND BOARD", "1000"),
                req("1000011", "2016-04-22", "Inpatient Hospital Care", "ROOM AND BOARD", "1000"),
                req("1000011", "2016-04-23", "Inpatient Hospital Care", "SURGERY", "4000"),
                req("1000011", "2016-04-23", "Inpatient Hospital Care", "ROOM AND BOARD", "1000"),
                req("1000012", "2016-06-23", "Outpatient Services", "PRIMARY CARE OFFICE VISIT", "140"),
                req("1000012", "2016-06-24", "Prescription Drugs", "GENERIC", "25.6"),
                req("1000016", "2016-09-27", "Prescription Drugs", "GENERIC", "61.4"),
                req("1000011", "2016-06-27", "Inpatient Hospital Care", "ROOM AND BOARD", "1000"),
                req("1000011", "2016-06-28", "Inpatient Hospital Care", "SURGERY", "6000"),
                req("1000011", "2016-06-29", "Inpatient Hospital Care", "ROOM AND BOARD", "1000"),
                req("1000013", "2016-07-01", "Outpatient Services", "PRIMARY CARE OFFICE VISIT", "120")
        ));

        assertThat(results).hasSize(11);

        // Row 4: deductible crosses threshold; plan begins paying 40%.
        ClaimResult crossing = results.get(3);
        assertThat(crossing.policyHolderPays()).isEqualByComparingTo("600");
        assertThat(crossing.planPays()).isEqualByComparingTo("400");
        assertThat(crossing.ruleUsed()).isEqualTo("40% AFTER DEDUCTIBLE");

        // Row 7: unknown holder.
        ClaimResult unknown = results.get(6);
        assertThat(unknown.errorCode()).isEqualTo("E0001");

        // Row 9: surgery 60/40 after deductible.
        ClaimResult surgery = results.get(8);
        assertThat(surgery.policyHolderPays()).isEqualByComparingTo("3600");
        assertThat(surgery.planPays()).isEqualByComparingTo("2400");
    }
}
