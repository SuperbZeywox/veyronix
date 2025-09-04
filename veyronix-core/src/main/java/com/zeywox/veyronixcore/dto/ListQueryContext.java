package com.zeywox.veyronixcore.dto;

import com.zeywox.veyronixcore.models.Product;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public record ListQueryContext(String category, Optional<Boolean> inStock, int page, int size,
                               Supplier<List<Product>> fetcher) {}