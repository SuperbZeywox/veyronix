package com.zeywox.veyronixcore.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.zeywox.veyronixcore.deserialization.DoublePriceDeserializer;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductIn(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String category,

        @PositiveOrZero
        @JsonDeserialize(using = DoublePriceDeserializer.class)
        Double price,

        @Size(max = 2000) String description,
        @PositiveOrZero Integer stock
) {

    public ProductIn {
        name        = (name == null) ? null : name.trim();
        category    = (category == null || category.isBlank()) ? "uncategorized" : category.trim();
        description = (description == null) ? "" : description.trim();
        stock       = (stock == null) ? 0 : stock;
    }

}
