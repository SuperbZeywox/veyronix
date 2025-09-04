package com.zeywox.veyronixcore.models;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.zeywox.veyronixcore.util.ValueCoercions.*;

public record Product(
        String id,
        String name,
        String category,
        Double price,
        String description,
        Integer stock
) {

    // Deserializing Product from Redis format
    public Product(Map<Object, Object> m) {
        this(
                str(m.get("id")),
                trimToNull(str(m.get("name"))),
                defaultCategory(trimToNull(str(m.get("category")))),
                parseRedisPrice(m.get("price")),
                defaultEmpty(str(m.get("description"))),
                parseIntSafe(m.get("stock"))
        );
    }

    // Serializing Product to Redis format
    public Map<String,String> toRedis() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", this.id());
        m.put("name", ns(this.name()));
        m.put("category", ns(this.category()));
        m.put("price", this.price() == null ? "" :
                java.math.BigDecimal.valueOf(this.price())
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                        .toPlainString());
        m.put("description", ns(this.description()));
        m.put("stock", String.valueOf(this.stock() == null ? 0 : this.stock()));
        return m;
    }


}
