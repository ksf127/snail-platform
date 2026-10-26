package com.snail.platform.worker.build;

import com.snail.platform.common.build.RepoUrls;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 日志脱敏：把仓库令牌、构建变量值以及 URL 中的 userinfo 替换为 ***。
 * 所有执行器输出都必须经过这一层，避免凭据进入 MinIO 中的构建日志。
 */
public class MaskingLogSink implements LogSink {

    private final LogSink delegate;
    private final List<String> secrets;

    public MaskingLogSink(LogSink delegate, String repoToken, Map<String, String> secrets) {
        this.delegate = delegate;
        List<String> values = new ArrayList<>();
        if (repoToken != null && !repoToken.isBlank() && repoToken.length() >= 4) {
            values.add(repoToken.trim());
        }
        if (secrets != null) {
            secrets.values().stream()
                    .filter(value -> value != null && value.length() >= 4)
                    .forEach(values::add);
        }
        // 长值优先替换，避免短值先替换破坏长值匹配。
        values.sort(Comparator.comparingInt(String::length).reversed());
        this.secrets = List.copyOf(values);
    }

    @Override
    public void line(String line) {
        if (line == null) {
            return;
        }
        String masked = RepoUrls.mask(line);
        for (String secret : secrets) {
            masked = masked.replace(secret, "***");
        }
        delegate.line(masked);
    }
}
