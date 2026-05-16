package com.abc.claims.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes the authenticated user's identity and granted roles so the Angular SPA
 * can render role-aware navigation (e.g., hide the REST API menu from the
 * {@code PROCESSOR}-only persona and reveal it for {@code ADMIN}).
 *
 * <p>Returning a stripped-down DTO — never the password, never raw authorities —
 * keeps the contract small and avoids accidentally leaking Spring internals.</p>
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null) {
            return Map.of("authenticated", false, "username", "", "roles", List.of());
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        return Map.of(
                "authenticated", true,
                "username", authentication.getName(),
                "roles", roles
        );
    }
}
