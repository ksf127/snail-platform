package com.company.platform.worker.storage;

import com.company.platform.common.build.ArtifactRegistration;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 构建日志与产物的 MinIO 存储。对象键统一以 runs/{runId}/ 开头。
 */
@Component
public class ArtifactStore {

    private final MinioClient minioClient;
    private final String bucket;
    private volatile boolean bucketChecked = false;

    public ArtifactStore(MinioClient minioClient,
                         @Value("${platform.minio.bucket:platform-artifacts}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public String uploadLog(Long runId, Path logFile) throws Exception {
        String objectKey = "runs/" + runId + "/logs/build.log";
        upload(objectKey, logFile, "text/plain");
        return objectKey;
    }

    public ArtifactRegistration uploadArtifact(Long runId, Path file, String relativePath) throws Exception {
        String normalized = relativePath.replace('\\', '/');
        String objectKey = "runs/" + runId + "/artifacts/" + normalized;
        String contentType = contentType(file.getFileName().toString());
        upload(objectKey, file, contentType);
        return new ArtifactRegistration(file.getFileName().toString(), objectKey,
                Files.size(file), sha256(file), contentType);
    }

    private void upload(String objectKey, Path file, String contentType) throws Exception {
        ensureBucket();
        try (InputStream input = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(input, Files.size(file), -1)
                    .build());
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketChecked) {
            return;
        }
        synchronized (this) {
            if (bucketChecked) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketChecked = true;
        }
    }

    private String contentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jar") || lower.endsWith(".war")) {
            return "application/java-archive";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return "application/gzip";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".log") || lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
