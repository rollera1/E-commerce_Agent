package com.ecommerce.agent.sourceproduct;

public interface ProductSourceGateway {

    SourceProductPage list(String keyword, int page, int pageSize);

    SourceProduct get(String sourceProductId);
}
