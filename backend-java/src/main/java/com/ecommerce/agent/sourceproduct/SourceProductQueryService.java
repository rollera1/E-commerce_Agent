package com.ecommerce.agent.sourceproduct;

import org.springframework.stereotype.Service;

/** 来源商品查询服务，将 Web 请求转交给实际商品来源适配器。 */
@Service
public class SourceProductQueryService {

    private final ProductSourceGateway productSourceGateway;

    public SourceProductQueryService(ProductSourceGateway productSourceGateway) {
        this.productSourceGateway = productSourceGateway;
    }

    public SourceProductPage list(String keyword, int page, int pageSize) {
        return productSourceGateway.list(keyword, page, pageSize);
    }

    public SourceProduct get(String sourceProductId) {
        return productSourceGateway.get(sourceProductId);
    }
}
