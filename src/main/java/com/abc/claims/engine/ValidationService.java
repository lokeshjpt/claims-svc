package com.abc.claims.engine;

import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ErrorCode;
import com.abc.claims.model.PolicyHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ValidationService {
    private final LocalDate futureDateThreshold;

    public ValidationService(@Value("${claims.validation.max-service-date:2016-12-31}") LocalDate futureDateThreshold) {
        this.futureDateThreshold = futureDateThreshold;
    }

    public ErrorCode validate(ClaimRequest request, PolicyHolder holder, String rawCoverageRule) {
        if (isMalformed(request)) {
            return ErrorCode.E0005;
        }
        if (holder == null) {
            return ErrorCode.E0001;
        }
        if (request.dateOfService() != null && request.dateOfService().isAfter(futureDateThreshold)) {
            return ErrorCode.E0004;
        }
        if (isOutsideCoveragePeriod(request.dateOfService(), holder)) {
            return ErrorCode.E0002;
        }
        if (rawCoverageRule == null || rawCoverageRule.isBlank()) {
            return ErrorCode.E0003;
        }
        return null;
    }

    private boolean isMalformed(ClaimRequest request) {
        if (request == null) {
            return true;
        }
        if (isBlank(request.policyId()) || isBlank(request.policyHolderId())) {
            return true;
        }
        if (request.dateOfService() == null) {
            return true;
        }
        if (isBlank(request.coverageMainCategory()) || isBlank(request.coverageSubCategory())) {
            return true;
        }
        return request.billedAmount() == null || request.billedAmount().signum() < 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isOutsideCoveragePeriod(LocalDate dateOfService, PolicyHolder holder) {
        if (dateOfService == null) {
            return true;
        }
        if (holder.coverageStartDate() != null && dateOfService.isBefore(holder.coverageStartDate())) {
            return true;
        }
        return holder.coverageEndDate() != null && dateOfService.isAfter(holder.coverageEndDate());
    }
}
