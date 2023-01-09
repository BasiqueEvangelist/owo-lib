package io.wispforest.uwu.text;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import io.wispforest.owo.text.CustomText;
import io.wispforest.owo.text.CustomTextSerializer;
import net.minecraft.text.BaseText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.util.JsonHelper;

import java.util.Optional;

public class BasedText extends BaseText implements CustomText {
    private final String basedText;

    public BasedText(String basedText) {
        this.basedText = basedText;
    }

    @Override
    public <T> Optional<T> visitSelf(StringVisitable.Visitor<T> visitor) {
        return visitor.accept("I am extremely based: " + basedText);
    }

    @Override
    public <T> Optional<T> visitSelf(StringVisitable.StyledVisitor<T> visitor, Style style) {
        return visitor.accept(style, "I am extremely based: " + basedText);
    }

    @Override
    public CustomTextSerializer<?> serializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public BaseText copy() {
        return new BasedText(basedText);
    }

    public static class Serializer implements CustomTextSerializer<BasedText> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public BasedText deserialize(JsonObject obj, JsonDeserializationContext ctx) {
            return new BasedText(JsonHelper.getString(obj, "based"));
        }

        @Override
        public void serialize(BasedText content, JsonObject obj, JsonSerializationContext ctx) {
            obj.addProperty("based", content.basedText);
        }
    }
}
