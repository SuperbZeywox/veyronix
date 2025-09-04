package com.zeywox.veyronixcore.http.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeywox.veyronixcore.http.dto.ProductDto;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class ProductDataUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String ensureTargetProductId(
            String baseUrl,
            String category,
            HttpClient client,
            Duration timeout,
            int stockToSet
    ) {
        // (1) Try in-stock first
        var inStock = fetchProducts(baseUrl, category, true, client, timeout);
        for (ProductDto p : inStock) {
            if (p.id() != null && p.inStock()) return p.id();
        }

        // (2) Else: grab up to two and seed stock via PUT
        var all = fetchProducts(baseUrl, category, false, client, timeout);
        if (all.isEmpty()) {
            throw new IllegalStateException("No products found for category=" + category);
        }

        int seeded = 0;
        for (ProductDto p : all) {
            if (p.id() == null) continue;
            if (setStock(baseUrl, p.id(), stockToSet, client, timeout)) {
                return p.id(); // first successful PUT becomes target
            }
            if (++seeded >= 2) break; // only try two ids as requested
        }
        // Fallback: if PUTs failed, use the first with a non-null id
        for (ProductDto p : all) if (p.id() != null) return p.id();
        throw new IllegalStateException("Could not determine a target product id");
    }

    private static List<ProductDto> fetchProducts(
            String baseUrl, String category, boolean onlyInStock,
            HttpClient client, Duration timeout
    ) {
        try {
            String q = "/products?category=" + urlEnc(category) + (onlyInStock ? "&inStock=true" : "");
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + q))
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip") // explicit is fine; server is gzip-only anyway
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 400) {
                throw new IllegalStateException("GET " + q + " -> " + resp.statusCode());
            }

            byte[] bytes = resp.body();
            String enc = resp.headers().firstValue("Content-Encoding").orElse("");
            if ("gzip".equalsIgnoreCase(enc)) {
                try (var gis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
                    bytes = gis.readAllBytes();
                }
            }

            return MAPPER.readValue(bytes,
                    new TypeReference<List<ProductDto>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch products (inStock=" + onlyInStock + ")", e);
        }
    }

    private static boolean setStock(String baseUrl, String id, int stock,
                                    HttpClient client, Duration timeout) {
        try {
            String path = "/products/" + urlEnc(id) + "/stock";
            String json = "{\"stock\":" + stock + "}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private static String urlEnc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
