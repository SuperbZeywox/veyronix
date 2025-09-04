package com.zeywox.veyronixcore.repos;

import com.zeywox.veyronixcore.models.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> getOne(String id);
    List<Product> getMany(Collection<String> ids); // pipelined HGETALL

    List<String> listIdsByCategory(String category,
                                   Optional<Boolean> inStock,
                                   int page,
                                   int size);

    void upsert(Product product);   // writes HASH + maintains SET and ZSET indexes
    int setStock(String id, int stock); // toggles in/out indexes; throws if missing
}




