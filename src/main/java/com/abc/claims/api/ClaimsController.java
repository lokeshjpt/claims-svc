package com.abc.claims.api;

import com.abc.claims.api.dto.ClaimRequestDto;
import com.abc.claims.api.dto.ClaimResponseDto;
import com.abc.claims.audit.ClaimAuditLogger;
import com.abc.claims.audit.ClaimAuditLogger.Channel;
import com.abc.claims.batch.CsvClaimParser;
import com.abc.claims.engine.ClaimsEngine;
import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims", description = "Process individual claims through the coverage engine")
public class ClaimsController {

    private final ClaimsEngine claimsEngine;
    private final ClaimAuditLogger auditLogger;
    private final CsvClaimParser csvClaimParser;

    @PostMapping
    @Operation(
        summary = "Process a single claim",
        description = "Runs the claim through validation, coverage-rule lookup, and the deductible engine. " +
                "Returns the calculated plan/holder split plus any error code. Missing or malformed fields " +
                "are reported as business error E0005 in the response body (HTTP 200), consistent with the " +
                "CSV/GUI modes — no field-level 400s.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Claim processed (may include business error code E0001-E0005 in body)"),
        @ApiResponse(responseCode = "401", description = "Missing / invalid HTTP Basic credentials"),
        @ApiResponse(responseCode = "403", description = "Authenticated user lacks the PROCESSOR role")
    })
    public ClaimResponseDto process(@RequestBody ClaimRequestDto request) {
        log.debug("Received claim request for policy={} holder={}",
                request == null ? null : request.policyId(),
                request == null ? null : request.policyHolderId());
        ClaimRequest engineRequest = toEngineRequest(request);
        ClaimResult result = claimsEngine.processForApi(engineRequest);
        auditLogger.audit(Channel.REST, engineRequest, result);
        return new ClaimResponseDto(
                result.policyId(),
                result.policyHolderId(),
                result.dateOfService(),
                result.coverageMainCategory(),
                result.coverageSubCategory(),
                result.billedAmount(),
                result.policyHolderPays(),
                result.planPays(),
                result.ruleUsed(),
                result.individualAccumulatedDeductible(),
                result.familyAccumulatedDeductible(),
                result.errorCode(),
                result.errorMessage(),
                result.processingMessage()
        );
    }

    private ClaimRequest toEngineRequest(ClaimRequestDto request) {
        if (request == null) {
            return new ClaimRequest(null, null, null, null, null, null, null, null);
        }
        return new ClaimRequest(
                request.policyId(),
                request.policyHolderId(),
                request.dateOfService(),
                request.coverageMainCategory(),
                request.coverageSubCategory(),
                request.billedAmount(),
                request.individualAccumulatedDeductible(),
                request.familyAccumulatedDeductible()
        );
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Batch-process a claims CSV and return JSON results",
        description = "Accepts the same CSV format as the GUI upload (PolicyId, Policy holder Id, " +
                "Date of service, Coverage Main Category, Coverage Sub Category, Billed Amount). " +
                "Returns one ClaimResponseDto per row with the calculated plan/holder split, rule used, " +
                "and any business error code. Used by the Angular SPA to render a chip-styled results table.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All rows processed; results in body"),
        @ApiResponse(responseCode = "400", description = "Empty or unparseable CSV"),
        @ApiResponse(responseCode = "401", description = "Missing / invalid HTTP Basic credentials"),
        @ApiResponse(responseCode = "403", description = "Authenticated user lacks the PROCESSOR role")
    })
    public List<ClaimResponseDto> processBatch(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required and must not be empty");
        }
        log.info("Batch upload received name={} size={}B", file.getOriginalFilename(), file.getSize());
        List<ClaimRequest> requests = csvClaimParser.parseStream(file.getInputStream());
        List<ClaimResponseDto> responses = new ArrayList<>(requests.size());
        for (ClaimRequest req : requests) {
            ClaimResult r = claimsEngine.processForBatchOrGui(req);
            auditLogger.audit(Channel.GUI, req, r);
            responses.add(new ClaimResponseDto(
                    r.policyId(), r.policyHolderId(), r.dateOfService(),
                    r.coverageMainCategory(), r.coverageSubCategory(), r.billedAmount(),
                    r.policyHolderPays(), r.planPays(), r.ruleUsed(),
                    r.individualAccumulatedDeductible(), r.familyAccumulatedDeductible(),
                    r.errorCode(), r.errorMessage(), r.processingMessage()
            ));
        }
        return responses;
    }
}

