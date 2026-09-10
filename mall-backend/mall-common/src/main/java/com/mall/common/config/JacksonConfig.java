package com.mall.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Jackson 配置：Long 包装类型序列化为字符串。
 * 雪花 ID 超出 JS Number 安全整数范围（2^53-1），直接返回数字会在前端丢精度，
 * 导致 id / merchantId 等无法精确匹配（如店铺页报"店铺不存在"）。统一以字符串传输。
 *
 * 注意：仅处理 {@code Long} 包装类型，不改动基本类型 {@code long}，
 * 以免分页对象 Page 的 total/size/current/pages（基本类型）被字符串化而影响前端分页。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
