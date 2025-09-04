package com.zeywox.veyronixcore.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class Requests {
    public record PatchProductRequest(Map<String, Object> fields) {}

    public record SetStockRequest(
            @NotNull @Min(0) Integer stock
    ) {}
}

