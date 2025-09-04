package com.zeywox.veyronixcore.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.zeywox.veyronixcore.dto.Encoded;
import com.zeywox.veyronixcore.models.Product;

import java.util.List;

public final class JsonGzipEncoder {

    private final ObjectWriter listWriter;
    private final ObjectWriter productWriter;


    public JsonGzipEncoder(ObjectMapper om) {
        this.listWriter = om.writerFor(new TypeReference<List<Product>>(){});
        this.productWriter = om.writerFor(Product.class);
    }


    public Encoded encodeList(List<Product> products) {
        try {
            byte[] raw = listWriter.writeValueAsBytes(products);
            return new Encoded(raw, Compression.gzip(raw), Etags.weakCrc32c(raw));
        } catch (Exception e) {
            throw new RuntimeException("serialize list failed", e);
        }
    }


    public Encoded encodeProduct(Product p) {
        try {
            byte[] raw = productWriter.writeValueAsBytes(p);
            return new Encoded(raw, Compression.gzip(raw), Etags.weakCrc32c(raw));
        } catch (Exception e) {
            throw new RuntimeException("serialize product failed", e);
        }
    }




}
