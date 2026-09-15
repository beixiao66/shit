package com.mall.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-at-least-32-chars-long";
    private static final long EXPIRE_SECONDS = 3600;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, EXPIRE_SECONDS);

    @Test
    void create_and_parse_roundTrip() {
        String token = jwtUtil.create(1001L, 0, "user1");
        Map<String, Object> claims = jwtUtil.parse(token);
        assertThat(claims.get("sub")).isEqualTo("1001");
        assertThat(claims.get("type")).isEqualTo(0);
        assertThat(claims.get("username")).isEqualTo("user1");
    }

    @Test
    void parse_token_type_field() {
        String token = jwtUtil.create(2002L, 1, "admin");
        Map<String, Object> claims = jwtUtil.parse(token);
        assertThat(claims.get("sub")).isEqualTo("2002");
        assertThat(claims.get("type")).isEqualTo(1);
        assertThat(claims.get("username")).isEqualTo("admin");
    }

    @Test
    void parse_merchant_token() {
        String token = jwtUtil.create(3003L, 2, "seller1");
        Map<String, Object> claims = jwtUtil.parse(token);
        assertThat(claims.get("type")).isEqualTo(2);
        assertThat(claims.get("username")).isEqualTo("seller1");
    }

    @Test
    void parse_expired_token_throws() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, 0);
        String token = expiredUtil.create(1L, 0, "user");
        assertThatThrownBy(() -> expiredUtil.parse(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parse_wrong_secret_throws() {
        String token = jwtUtil.create(1L, 0, "user");
        JwtUtil otherUtil = new JwtUtil("another-secret-key-32-chars-long!!!", EXPIRE_SECONDS);
        assertThatThrownBy(() -> otherUtil.parse(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parse_garbage_token_throws() {
        assertThatThrownBy(() -> jwtUtil.parse("not.a.jwt"))
                .isInstanceOf(Exception.class);
    }
}
