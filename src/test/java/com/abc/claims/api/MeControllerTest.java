package com.abc.claims.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
class MeControllerTest {

    private final MeController controller = new MeController();

    @Test
    @DisplayName("returns username and stripped role names for an authenticated user")
    void returns_user_with_roles() {
        Authentication auth = new UsernamePasswordAuthenticationToken("processor", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PROCESSOR")));
        Map<String, Object> body = controller.me(auth);

        assertThat(body.get("authenticated")).isEqualTo(true);
        assertThat(body.get("username")).isEqualTo("processor");
        assertThat(body.get("roles")).isEqualTo(List.of("PROCESSOR"));
    }

    @Test
    @DisplayName("admin user with multiple roles returns both, role prefix stripped")
    void returns_multiple_roles() {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_PROCESSOR")));

        Map<String, Object> body = controller.me(auth);

        assertThat(body.get("username")).isEqualTo("admin");
        assertThat(body.get("roles")).isEqualTo(List.of("ADMIN", "PROCESSOR"));
    }

    @Test
    @DisplayName("null authentication returns unauthenticated response with empty roles")
    void returns_unauthenticated_when_no_principal() {
        Map<String, Object> body = controller.me(null);

        assertThat(body.get("authenticated")).isEqualTo(false);
        assertThat(body.get("username")).isEqualTo("");
        assertThat(body.get("roles")).isEqualTo(List.of());
    }
}
