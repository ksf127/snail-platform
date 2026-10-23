package com.snail.platform.control.security;

import com.snail.platform.control.entity.UserEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-with-at-least-32-bytes!!", 30);

    @Test
    void generatedTokenCarriesUserClaims() {
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setUsername("dev");
        user.setDisplayName("开发者");
        user.setRole("DEVELOPER");

        Claims claims = jwtService.parse(jwtService.generate(user));

        assertThat(claims.getSubject()).isEqualTo("dev");
        assertThat(claims.get("uid", Integer.class)).isEqualTo(42);
        assertThat(claims.get("role", String.class)).isEqualTo("DEVELOPER");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 30))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService("another-secret-key-with-32-bytes!!!!!", 30);
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("ADMIN");

        assertThatThrownBy(() -> jwtService.parse(other.generate(user)))
                .isInstanceOf(RuntimeException.class);
    }
}
