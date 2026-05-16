package com.abc.claims.audit;

import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimAuditLoggerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ClaimAuditLogger logger = new ClaimAuditLogger(registry);

    private final ClaimRequest request = new ClaimRequest(
            "100001", "1000011", null, null, null, BigDecimal.ZERO, null, null);

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("audit() increments a tagged counter and logs as 'system' when no principal")
    void anonymous_audit_uses_system() {
        ClaimResult result = result("OK rule", null);
        logger.audit(ClaimAuditLogger.Channel.REST, request, result);

        assertThat(counterCount("rest", "OK")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("audit() uses the authenticated principal name and tags counter by errorCode")
    void authenticated_audit_uses_principal_and_error_status() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("processor", "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_PROCESSOR")));

        ClaimResult result = result(null, "E0001");
        logger.audit(ClaimAuditLogger.Channel.BATCH, request, result);

        assertThat(counterCount("batch", "E0001")).isEqualTo(1.0);
    }

    private double counterCount(String channel, String status) {
        return registry.find("claims.processed")
                .tag("channel", channel)
                .tag("status", status)
                .counter()
                .count();
    }

    private ClaimResult result(String rule, String errorCode) {
        return new ClaimResult("100001", "1000011", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                rule, BigDecimal.ZERO, BigDecimal.ZERO,
                errorCode, errorCode == null ? null : "msg", "processed");
    }
}
