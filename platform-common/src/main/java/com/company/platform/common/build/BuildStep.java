package com.company.platform.common.build;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线中的一个构建步骤。
 * commands 会以 `sh -lc "cmd1 && cmd2"` 的形式在容器内执行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildStep {

    private String name;
    private String image;
    @Builder.Default
    private List<String> commands = new ArrayList<>();
    /** 容器内工作目录，默认 /workspace。 */
    @Builder.Default
    private String workDir = "/workspace";
    /** 相对 workDir 的文件/目录/通配符，构建结束后上传到 MinIO。 */
    @Builder.Default
    private List<String> artifacts = new ArrayList<>();
}
