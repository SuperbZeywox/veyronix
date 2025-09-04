package com.zeywox.veyronixcore.deserialization;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.zeywox.veyronixcore.dto.PatchProductRequest;


public class PatchProductNormalizationModule extends SimpleModule {
    @Override
    public void setupModule(SetupContext ctx) {
        ctx.addBeanDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(
                    DeserializationConfig config,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> defaultDeserializer) {

                if (PatchProductRequest.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    return new StdDeserializer<PatchProductRequest>(PatchProductRequest.class) {
                        @Override
                        public PatchProductRequest deserialize(JsonParser p, DeserializationContext ctxt)
                                throws java.io.IOException {
                            // delegate to Jackson’s fast bean deserializer
                            PatchProductRequest req =
                                    (PatchProductRequest) defaultDeserializer.deserialize(p, ctxt);
                            // normalize before validation runs
                            if (req != null) req.normalize();
                            return req;
                        }
                    };
                }
                return defaultDeserializer;
            }
        });
    }
}

