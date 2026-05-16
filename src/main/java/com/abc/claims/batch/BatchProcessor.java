package com.abc.claims.batch;

import com.abc.claims.audit.ClaimAuditLogger;
import com.abc.claims.audit.ClaimAuditLogger.Channel;
import com.abc.claims.engine.ClaimsEngine;
import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BatchProcessor {
    private final ClaimsEngine claimsEngine;
    private final CsvClaimParser csvClaimParser;
    private final ClaimAuditLogger auditLogger;

    public BatchProcessor(ClaimsEngine claimsEngine, CsvClaimParser csvClaimParser, ClaimAuditLogger auditLogger) {
        this.claimsEngine = claimsEngine;
        this.csvClaimParser = csvClaimParser;
        this.auditLogger = auditLogger;
    }

    public void processFile(String inputPath, String outputPath) {
        log.info("Batch run starting input={} output={}", inputPath, outputPath);
        List<ClaimRequest> requests = csvClaimParser.parseFile(inputPath);
        List<String[]> rows = processRequests(requests, Channel.BATCH);
        csvClaimParser.writeResults(outputPath, rows);
        log.info("Batch run complete: {} claims written to {}", rows.size() - 1, outputPath);
    }

    public List<String[]> processRequests(List<ClaimRequest> requests) {
        return processRequests(requests, Channel.BATCH);
    }

    public List<String[]> processRequests(List<ClaimRequest> requests, Channel channel) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{
                "PolicyId", "Policy holder Id", "Date of service", "Coverage Main Category", "Coverage Sub Category",
                "Billed Amount", "Policy Holder pays", "Plan Pays", "Rule used",
                "Individual accumulated deductible as of service date",
                "Family accumulated deductible as of service date",
                "Error Code", "Error Message", "Processing message"
        });
        for (ClaimRequest request : requests) {
            ClaimResult result = claimsEngine.processForBatchOrGui(request);
            auditLogger.audit(channel, request, result);
            rows.add(new String[]{
                    value(result.policyId()),
                    value(result.policyHolderId()),
                    value(result.dateOfService() == null ? null : result.dateOfService().toString()),
                    value(result.coverageMainCategory()),
                    value(result.coverageSubCategory()),
                    money(result.billedAmount()),
                    money(result.policyHolderPays()),
                    money(result.planPays()),
                    value(result.ruleUsed()),
                    money(result.individualAccumulatedDeductible()),
                    money(result.familyAccumulatedDeductible()),
                    value(result.errorCode()),
                    value(result.errorMessage()),
                    value(result.processingMessage())
            });
        }
        return rows;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
