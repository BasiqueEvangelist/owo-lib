package io.wispforest.owo.text;

import net.minecraft.text.MutableText;

public interface CustomText extends MutableText {
    CustomTextSerializer<?> serializer();
}
