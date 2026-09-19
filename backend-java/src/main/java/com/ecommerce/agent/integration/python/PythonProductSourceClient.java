package com.ecommerce.agent.integration.python;

import com.ecommerce.agent.sourceproduct.ProductSourceGateway;
import com.ecommerce.agent.sourceproduct.SourceProduct;
import com.ecommerce.agent.sourceproduct.SourceProductPage;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 通过 HTTP 调用 Python 商品来源服务。 */
@Component
public class PythonProductSourceClient implements ProductSourceGateway {

    private final RestClient restClient;

    @Autowired
    public PythonProductSourceClient(PythonServiceProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .build());
    }

    PythonProductSourceClient(RestClient restClient) {
        // 允许测试传入 Mock RestClient。
        this.restClient = restClient;
    }

    @Override
    public SourceProductPage list(String keyword, int page, int pageSize) {
        // keyword 为空时不向 Python 发送该查询参数。
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/product-sources/products")
                        .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .retrieve()
                .body(SourceProductPage.class);
    }

    @Override
    public SourceProduct get(String sourceProductId) {
        return restClient.get()
                .uri("/internal/product-sources/products/{sourceProductId}", sourceProductId)
                .retrieve()
                .body(SourceProduct.class);
    }
}
