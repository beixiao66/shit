package com.mall.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResultTest {

    @Test
    void ok_with_data() {
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void ok_without_data() {
        Result<Void> r = Result.ok();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isNull();
    }

    @Test
    void fail_sets_code_and_message() {
        Result<Void> r = Result.fail(400, "bad request");
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).isEqualTo("bad request");
        assertThat(r.getData()).isNull();
    }

    @Test
    void fail_401_unauthorized() {
        Result<Void> r = Result.fail(Result.CODE_UNAUTHORIZED, "未登录");
        assertThat(r.getCode()).isEqualTo(401);
    }

    @Test
    void fail_403_forbidden() {
        Result<Void> r = Result.fail(Result.CODE_FORBIDDEN, "无权限");
        assertThat(r.getCode()).isEqualTo(403);
    }

    @Test
    void ok_with_complex_data() {
        Result<Integer> r = Result.ok(42);
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo(42);
    }
}
