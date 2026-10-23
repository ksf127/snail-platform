package com.snail.platform.common.build;

/** worker 上传制品后登记元数据。 */
public record ArtifactRegistration(
        String fileName,
        String objectKey,
        long size,
        String sha256,
        String contentType
) {
}
