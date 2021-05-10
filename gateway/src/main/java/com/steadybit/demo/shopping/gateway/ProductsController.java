/*
 * Copyright 2019 steadybit GmbH. All rights reserved.
 */

package com.steadybit.demo.shopping.gateway;

import com.steadybit.shopping.domain.Product;
import com.steadybit.shopping.domain.Products;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ParameterizedTypeReference<Product> productTypeReference = new ParameterizedTypeReference<Product>() {
    };
    private final ParameterizedTypeReference<List<Product>> productListTypeReference = new ParameterizedTypeReference<List<Product>>() {
    };

    @Value("${rest.endpoint.fashion}")
    private String urlFashion;
    @Value("${rest.endpoint.toys}")
    private String urlToys;
    @Value("${rest.endpoint.hotdeals}")
    private String urlHotDeals;

    public ProductsController(RestTemplate restTemplate, WebClient webClient) {
        this.restTemplate = restTemplate;
        this.webClient = webClient;
    }

    @GetMapping
    public Products getProducts() {
        Products products = new Products();
        products.setFashion(this.getProduct(this.urlFashion));
        products.setToys(this.getProduct(this.urlToys));
        products.setHotDeals(this.getProduct(this.urlHotDeals));
        return products;
    }

    @GetMapping("/parallel")
    public Mono<Products> getProductsParallel() {
        Mono<List<Product>> hotdeals = this.getProductReactive("/products/hotdeals");
        Mono<List<Product>> fashion = this.getProductReactive("/products/fashion");
        Mono<List<Product>> toys = this.getProductReactive("/products/toys");

        return Mono.zip(hotdeals, fashion, toys)
                .flatMap(transformer -> Mono.just(new Products(transformer.getT1(), transformer.getT2(), transformer.getT3())));
    }
    
    @GetMapping({ "/circuitbreaker", "/cb", "/v2" })
    public Mono<Products> getProductsCircuitBreaker() {
        Mono<List<Product>> hotdeals = this.getProductReactive("/products/hotdeals/circuitbreaker");
        Mono<List<Product>> fashion = this.getProductReactive("/products/fashion/circuitbreaker");
        Mono<List<Product>> toys = this.getProductReactive("/products/toys/circuitbreaker");

        return Mono.zip(hotdeals, fashion, toys)
                .flatMap(transformer -> Mono.just(new Products(transformer.getT1(), transformer.getT2(), transformer.getT3())));
    }

    @GetMapping("/fallback")
    public ResponseEntity<List<Product>> getProductsFallback() {
        log.info("fallback enabled");
        HttpHeaders headers = new HttpHeaders();
        headers.add("fallback", "true");
        return ResponseEntity.ok().headers(headers).body(Collections.emptyList());
    }

    private List<Product> getProduct(String url) {
        return this.restTemplate.exchange(url, HttpMethod.GET, null, this.productListTypeReference).getBody();
    }

    private Mono<List<Product>> getProductReactive(String uri) {
        return this.webClient.get().uri(uri)
                .retrieve()
                .bodyToFlux(this.productTypeReference)
                .collectList()
                .flatMap(Mono::just)
                .doOnError(throwable -> log.error("Error occured", throwable));
    }
}
