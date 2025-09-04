package com.zeywox.veyronixcore.http.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        String id,
        Integer stock
) {
    public boolean inStock() {
        return stock != null && stock > 0;
    }
}

