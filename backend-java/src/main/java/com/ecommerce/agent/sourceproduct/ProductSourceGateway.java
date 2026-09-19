package com.ecommerce.agent.sourceproduct;

/** Java 查询外部来源商品的接口，当前由 Python 客户端实现。 */
public interface ProductSourceGateway {

    // 分页查询来源商品，可选传入关键词。
    SourceProductPage list(String keyword, int page, int pageSize);

    // 根据来源商品 ID 查询商品详情。
    SourceProduct get(String sourceProductId);
}
