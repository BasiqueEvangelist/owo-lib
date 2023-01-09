package io.wispforest.owo.text;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.text.BaseText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.JsonHelper;

import java.util.Optional;

public final class InsertingText extends BaseText implements CustomText {
    private final int index;

    public InsertingText(int index) {
        this.index = index;
    }

    public static void init() {
        CustomTextRegistry.register("index", Serializer.INSTANCE);
    }

    @Override
    public <T> Optional<T> visitSelf(Visitor<T> visitor) {
        var current = TranslationContext.getCurrent();

        if (current == null || current.getArgs().length <= index)
            return visitor.accept("%" + (index + 1) + "$s");

        Object arg = current.getArgs()[index];

        if (arg instanceof Text text) {
            return text.visit(visitor);
        } else {
            return visitor.accept(arg.toString());
        }
    }

    @Override
    public <T> Optional<T> visitSelf(StyledVisitor<T> visitor, Style style) {
        var current = TranslationContext.getCurrent();

        if (current == null || current.getArgs().length <= index)
            return visitor.accept(style, "%" + (index + 1) + "$s");

        Object arg = current.getArgs()[index];

        if (arg instanceof Text text) {
            return text.visit(visitor, style);
        } else {
            return visitor.accept(style, arg.toString());
        }
    }

    @Override
    public CustomTextSerializer<?> serializer() {
        return Serializer.INSTANCE;
    }

    public int index() {
        return index;
    }

    @Override
    public BaseText copy() {
        return new InsertingText(index);
    }

    @Override
    public String toString() {
        return "InsertingText[" +
                "index=" + index + ']';
    }

    private enum Serializer implements CustomTextSerializer<InsertingText> {
        INSTANCE;

        @Override
        public InsertingText deserialize(JsonObject obj, JsonDeserializationContext ctx) {
            return new InsertingText(JsonHelper.getInt(obj, "index"));
        }

        @Override
        public void serialize(InsertingText content, JsonObject obj, JsonSerializationContext ctx) {
            obj.addProperty("index", content.index);
        }
    }
}
