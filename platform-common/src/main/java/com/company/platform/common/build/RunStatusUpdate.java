package com.company.platform.common.build;

/** worker 回报运行状态。 */
public record RunStatusUpdate(String status, String errorMessage, String logObject) {
}
