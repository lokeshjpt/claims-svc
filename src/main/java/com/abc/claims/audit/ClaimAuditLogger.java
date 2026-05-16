package com.abc.claims.audit;

import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Emits an audit log line for every processed claim and records Micrometer
 * counters so claims throughput / error rate are scrape-able by Prometheus.
 *
 * <p>Log fields:
 * <pre>
 *   AUDIT user={who} channel={REST|BATCH|GUI} policy={id} holder={id}
 *         rule={rule} planPays={x} holderPays={y} status={OK|errorCode}
 * </pre>
 * The user is also placed into MDC under key {@code user} so it appears in
 * every log line emitted while handling the request (see logging.pattern in
 * application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimAuditLogger {

    public enum Channel { REST, BATCH, GUI }

    private final MeterRegistry meterRegistry;

    public void audit(Channel channel, ClaimRequest request, ClaimResult result) {
        String user = currentUser();
        MDC.put("user", user);
        try {
            String status = result.errorCode() == null ? "OK" : result.errorCode();
            log.info("AUDIT user={} channel={} policy={} holder={} rule={} planPays={} holderPays={} status={}",
                    user, channel, result.policyId(), result.policyHolderId(),
                    result.ruleUsed(), result.planPays(), result.policyHolderPays(), status);

            Counter.builder("claims.processed")
                    .description("Total claims processed")
                    .tag("channel", channel.name().toLowerCase())
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        } finally {
            MDC.remove("user");
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
