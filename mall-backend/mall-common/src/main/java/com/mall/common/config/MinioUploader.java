package com.mall.common.config;

import com.mall.common.BizException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO 上传基础组件（concurrent-safe）：桶存在判断 → 不存在自动创建 → 上传对象 → 返回公开 URL。
 * 用途：商品主图/广告图/店铺 logo 等图片落 MinIO，数据库仅存返回的 URL 字符串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class MinioUploader {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /** 已确认存在的桶（避免每次上传重复 API 探测；简单并发用 synchronizedList 即可，桶创建本身幂等） */
    private static final Set<String> KNOWN_BUCKETS = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * 上传文件到 {bucket}/{yyyy/MM}/{uuid}.{ext}
     *
     * @param stream     文件流
     * @param size       文件大小
     * @param contentType MIME（如 image/jpeg）
     * @param ext        扩展名（如 jpg、png）
     * @return 公开访问 URL（数据库持久化该值）
     */
    public String upload(InputStream stream, long size, String contentType, String ext) {
        String bucket = minioConfig.getBucket();
        ensureBucket(bucket);
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String objectName = day + "/" + UUID.randomUUID().toString().replace("-", "") + "." + sanitizeExt(ext);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(stream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 上传失败 bucket={} object={}", bucket, objectName, e);
            throw new BizException("图片上传失败，请稍后重试");
        }
        return resolveUrl(bucket, objectName);
    }

    /**
     * 桶存在判断 + 不存在则自动创建（幂等，重复调用安全）；
     * 并确保桶为公开只读，否则返回的图片 URL 匿名访问会 403。
     */
    public void ensureBucket(String bucket) {
        if (KNOWN_BUCKETS.contains(bucket)) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] 桶不存在，已自动创建: {}", bucket);
            }
            // 幂等：无论桶是否新建，都确保公开只读策略（图片需可匿名 GET）
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            KNOWN_BUCKETS.add(bucket);
        } catch (Exception e) {
            log.error("[MinIO] 桶检查/创建/策略设置失败 bucket={}", bucket, e);
            throw new BizException("对象存储暂不可用，请稍后重试");
        }
    }

    /** 公开只读策略：允许匿名 GetObject（图片直接可访问） */
    private String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }

    /** 拼公开 URL：默认 endpoint 同源，可用 public-url 覆盖 */
    private String resolveUrl(String bucket, String objectName) {
        String base = minioConfig.getPublicUrl() != null && !minioConfig.getPublicUrl().isBlank()
                ? minioConfig.getPublicUrl() : minioConfig.getEndpoint();
        // endpoint 形如 http://192.168.193.131:9000；URL 由 {base}/{bucket}/{object}
        String sep = base.endsWith("/") ? "" : "/";
        return base + sep + bucket + "/" + objectName;
    }

    private String sanitizeExt(String ext) {
        if (ext == null || ext.isBlank()) {
            return "bin";
        }
        String e = ext.startsWith(".") ? ext.substring(1) : ext;
        return e.matches("[A-Za-z0-9]{1,8}") ? e : "bin";
    }
}
