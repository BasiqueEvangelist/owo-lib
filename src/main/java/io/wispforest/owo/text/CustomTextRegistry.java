package io.wispforest.owo.text;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

public final class CustomTextRegistry {

    private static final Map<String, CustomTextSerializer<?>> SERIALIZERS = new HashMap<>();

    private CustomTextRegistry() {}

    public static void register(String baseKey, CustomTextSerializer<?> serializer) {
        SERIALIZERS.put(baseKey, serializer);
    }

    @ApiStatus.Internal
    public static Map<String, CustomTextSerializer<?>> serializerMap() {
        return SERIALIZERS;
    }
}
