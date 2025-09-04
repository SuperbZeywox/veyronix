package com.zeywox.veyronixcore.deserialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DoublePriceDeserializer extends StdDeserializer<Double> {
    public DoublePriceDeserializer() { super(Double.class); }

    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonToken t = p.currentToken();
        BigDecimal bd;

        if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
            // Use decimal path to avoid binary FP surprises while validating
            bd = p.getDecimalValue(); // exact token-as-decimal when available
        } else if (t == JsonToken.VALUE_STRING) {
            String s = p.getValueAsString();
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;

            // Accept only plain digits or digits with dot + up to 2 decimals (e.g., "825" or "825.00")
            if (!s.matches("^\\d+(?:\\.\\d{1,2})?$")) {
                throw new JsonMappingException(p, "price must be numeric with up to 2 decimals (e.g. 825 or 825.00)");
            }
            try {
                bd = new BigDecimal(s);
            } catch (NumberFormatException ex) {
                throw new JsonMappingException(p, "Invalid price: " + s, ex);
            }
        } else if (t == JsonToken.VALUE_NULL) {
            return null;
        } else {
            throw new JsonMappingException(p, "Unsupported price token: " + t);
        }

        if (bd.signum() < 0) {
            throw new JsonMappingException(p, "price must be non-negative");
        }

        // Enforce <= 2 decimals; normalize to 2 decimals HALF_UP
        if (bd.stripTrailingZeros().scale() > 2) {
            throw new JsonMappingException(p, "price must have at most 2 decimal places");
        }

        bd = bd.setScale(2, RoundingMode.HALF_UP);
        double d = bd.doubleValue();
        if (!Double.isFinite(d)) throw new JsonMappingException(p, "price not finite");
        return d;
    }
}


