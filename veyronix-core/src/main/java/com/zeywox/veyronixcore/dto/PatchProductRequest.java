package com.zeywox.veyronixcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.zeywox.veyronixcore.deserialization.DoublePriceDeserializer;


// PatchProductRequest.java
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchProductRequest {
    @Size(max = 200)  public String name;
    @Size(max = 100)  public String category;

    @PositiveOrZero
    @JsonDeserialize(using = DoublePriceDeserializer.class)
    public Double price;

    @Size(max = 2000) public String description;
    @PositiveOrZero   public Integer stock;

    public PatchProductRequest() {}

    @JsonIgnore
    public void normalize() {
        if (name != null)        name = name.trim();
        if (category != null)    category = category.trim();
        if (description != null) description = description.trim();
    }

    @JsonIgnore
    @AssertTrue(message = "At least one field must be provided to patch")
    public boolean isAnyFieldProvided() {
        return name != null || category != null || price != null || description != null || stock != null;
    }
}
