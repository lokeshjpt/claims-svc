package com.abc.claims.engine.rule;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser registry implementing the strategy pattern. Each entry recognizes a raw rule
 * string from the reference data and builds the matching {@link CoverageRule} instance.
 *
 * <p>Adding a new rule type (e.g. CO_PAY, MAX_OUT_OF_POCKET) requires only a new
 * {@link CoverageRule} implementation and one entry in {@link #parsers}. The engine
 * does not change. Open/closed principle.</p>
 */
@Component
public class CoverageRuleParser {

    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)%\\s+AFTER\\s+DEDUCTIBLE$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLAT_PATTERN = Pattern.compile("^\\$?(\\d+(?:\\.\\d+)?)$");
    private static final Pattern NO_CHARGE_PATTERN = Pattern.compile("^NO\\s+CHARGE$", Pattern.CASE_INSENSITIVE);

    private final List<Function<String, Optional<CoverageRule>>> parsers = List.of(
            raw -> NO_CHARGE_PATTERN.matcher(raw).matches()
                    ? Optional.of(new NoChargeRule(raw)) : Optional.empty(),
            raw -> {
                Matcher matcher = PERCENTAGE_PATTERN.matcher(raw);
                if (!matcher.matches()) {
                    return Optional.empty();
                }
                BigDecimal fraction = new BigDecimal(matcher.group(1)).divide(BigDecimal.valueOf(100));
                return Optional.of(new PercentageAfterDeductibleRule(fraction, raw));
            },
            raw -> {
                Matcher matcher = FLAT_PATTERN.matcher(raw);
                if (!matcher.matches()) {
                    return Optional.empty();
                }
                return Optional.of(new FlatDollarRule(new BigDecimal(matcher.group(1)), raw));
            }
    );

    public Optional<CoverageRule> parse(String rawRule) {
        if (rawRule == null || rawRule.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawRule.trim();
        for (var parser : parsers) {
            Optional<CoverageRule> rule = parser.apply(normalized);
            if (rule.isPresent()) {
                return rule;
            }
        }
        return Optional.empty();
    }
}
