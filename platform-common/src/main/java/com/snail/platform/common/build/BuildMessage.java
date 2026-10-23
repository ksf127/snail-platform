package com.snail.platform.common.build;

/** MQ 消息体：platform.build / build.run。 */
public record BuildMessage(Long runId, Long pipelineId) {
}
