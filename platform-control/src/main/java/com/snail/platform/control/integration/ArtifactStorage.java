package com.snail.platform.control.integration;

import com.snail.platform.common.api.BizException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactStorage {

    private final MinioClient minioClient;

    @Value("${platform.minio.bucket:platform-artifacts}")
    private String bucket;

    private volatile boolean bucketChecked = false;

    public String bucket() {
        return bucket;
    }

    public void ensureBucket() {
        if (bucketChecked) {
            return;
        }
        synchronized (this) {
            if (bucketChecked) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("已创建制品 bucket: {}", bucket);
                }
                bucketChecked = true;
            } catch (Exception ex) {
                throw BizException.unavailable("MinIO 不可用，无法访问制品存储: " + ex.getMessage());
            }
        }
    }

    /** 生成 1 小时有效的预签名下载地址。 */
    public String presignedGetUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw BizException.notFound("对象不存在");
        }
        ensureBucket();
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception ex) {
            throw BizException.unavailable("生成下载地址失败: " + ex.getMessage());
        }
    }
}
