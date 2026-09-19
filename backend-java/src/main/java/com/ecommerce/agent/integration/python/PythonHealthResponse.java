package com.ecommerce.agent.integration.python;

/** Python 健康检查接口返回的数据。 */
public record PythonHealthResponse(
        // Python 服务状态。
        String status,
        // Python 服务名称。
        String service,
        // Python 服务版本。
        String version) {
}
