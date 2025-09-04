package com.zeywox.veyronixcore.controllers;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.zeywox.veyronixcore.config.controller.GlobalExceptionHandler;
import com.zeywox.veyronixcore.dto.PatchProductRequest;
import com.zeywox.veyronixcore.dto.Requests;
import com.zeywox.veyronixcore.models.Product;
import com.zeywox.veyronixcore.services.ProductService;
import com.zeywox.veyronixcore.services.ResponseCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService svc;
    private final ResponseCacheService cache;

    public ProductController(ProductService svc, ResponseCacheService cache) {
        this.svc = svc; this.cache = cache;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getOne(@PathVariable("id") String id, HttpServletRequest req) {
        return cache.getProductFresh(
                id,
                () -> svc.getOne(id).orElseThrow(() ->
                        new GlobalExceptionHandler.NotFound("Product not found: " + id)),
                req
        );
    }

    @GetMapping
    public java.util.concurrent.Callable<ResponseEntity<byte[]>> byCategory(
            @RequestParam("category") String category,
            @RequestParam(name = "inStock", required = false) Boolean inStock,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            HttpServletRequest req
    ) {
        if (category == null || category.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "category is required and cannot be blank");
        }
        if (page < 1) page = 1;
        size = 30; // fixed

        var inStockOpt = Optional.ofNullable(inStock);
        final int p = page, s = size;

        // Run the heavy bit off-thread; Undertow worker returns to the pool immediately.
        return () -> cache.getProductsListResponse(
                category, inStockOpt, p, s,
                () -> svc.listByCategory(category, inStockOpt, p, s),
                req
        );
    }


    @PatchMapping("/{id}")
    public Product patch(@PathVariable String id,
                         @Valid @RequestBody PatchProductRequest req) {
        return svc.patch(id, req);  // Bean Validation will 400 if no fields provided
    }

    @PutMapping("/{id}/stock")
    public Map<String, Object> setStock(@PathVariable("id") String id,
                                        @Valid @RequestBody Requests.SetStockRequest req) {
        return Map.of("id", id, "stock", svc.setStock(id, req.stock()));
    }

}

