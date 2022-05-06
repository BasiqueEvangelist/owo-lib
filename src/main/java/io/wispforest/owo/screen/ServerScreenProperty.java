package io.wispforest.owo.screen;

import io.wispforest.owo.network.serialization.PacketBufSerializer;
import net.minecraft.network.PacketByteBuf;

import java.util.Objects;

public class ServerScreenProperty<T> {
    private final PacketBufSerializer<T> serializer;
    private T value;
    private final int id;
    private boolean dirty = false;

    ServerScreenProperty(Class<T> klass, T defaultValue, int id) {
        this.serializer = PacketBufSerializer.get(klass);
        this.value = defaultValue;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        if (Objects.equals(this.value, value)) return;

        this.value = value;
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    void read(PacketByteBuf buf) {
        value = serializer.deserializer().apply(buf);
    }

    void write(PacketByteBuf buf) {
        dirty = false;
        serializer.serializer().accept(buf, value);
    }
}
