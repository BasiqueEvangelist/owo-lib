package io.wispforest.owo.serialization.format.nbt;

import io.wispforest.owo.serialization.Endec;
import io.wispforest.owo.serialization.SerializationAttribute;
import io.wispforest.owo.serialization.SerializationAttributes;
import io.wispforest.owo.serialization.Serializer;
import io.wispforest.owo.serialization.util.RecursiveSerializer;
import net.minecraft.nbt.*;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.encoding.VarLongs;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public class NbtSerializer extends RecursiveSerializer<NbtElement> {

    protected NbtElement prefix;

    protected NbtSerializer(NbtElement prefix) {
        super(NbtEnd.INSTANCE);
        this.prefix = prefix;
    }

    public static NbtSerializer of(NbtElement prefix) {
        return new NbtSerializer(prefix);
    }

    public static NbtSerializer of() {
        return of(null);
    }

    // ---

    @Override
    public boolean hasAttribute(SerializationAttribute attribute) {
        return attribute == SerializationAttributes.SELF_DESCRIBING;
    }

    @Override
    public <A> A getAttributeValue(SerializationAttribute.WithValue<A> attribute) {
        throw new IllegalArgumentException("NbtSerializer does not provide any attribute values");
    }

    // ---

    @Override
    public void writeByte(byte value) {
        this.consume(NbtByte.of(value));
    }

    @Override
    public void writeShort(short value) {
        this.consume(NbtShort.of(value));
    }

    @Override
    public void writeInt(int value) {
        this.consume(NbtInt.of(value));
    }

    @Override
    public void writeLong(long value) {
        this.consume(NbtLong.of(value));
    }

    @Override
    public void writeFloat(float value) {
        this.consume(NbtFloat.of(value));
    }

    @Override
    public void writeDouble(double value) {
        this.consume(NbtDouble.of(value));
    }

    // ---

    @Override
    public void writeVarInt(int value) {
        this.consume(switch (VarInts.getSizeInBytes(value)) {
            case 0, 1 -> NbtByte.of((byte) value);
            case 2 -> NbtShort.of((short) value);
            default -> NbtInt.of(value);
        });
    }

    @Override
    public void writeVarLong(long value) {
        this.consume(switch (VarLongs.getSizeInBytes(value)) {
            case 0, 1 -> NbtByte.of((byte) value);
            case 2 -> NbtShort.of((short) value);
            case 3, 4 -> NbtInt.of((int) value);
            default -> NbtLong.of(value);
        });
    }

    // ---

    @Override
    public void writeBoolean(boolean value) {
        this.consume(NbtByte.of(value));
    }

    @Override
    public void writeString(String value) {
        this.consume(NbtString.of(value));
    }

    @Override
    public void writeBytes(byte[] bytes) {
        this.consume(new NbtByteArray(bytes));
    }

    @Override
    public <V> void writeOptional(Endec<V> endec, Optional<V> optional) {
        if (this.isWritingStructField()) {
            optional.ifPresent(v -> endec.encode(this, v));
        } else {
            try (var struct = this.struct()) {
                struct.field("present", Endec.BOOLEAN, optional.isPresent());
                optional.ifPresent(value -> struct.field("value", endec, value));
            }
        }
    }

    // ---

    @Override
    public <E> Serializer.Sequence<E> sequence(Endec<E> elementEndec, int size) {
        return new Sequence<>(elementEndec);
    }

    @Override
    public <V> Serializer.Map<V> map(Endec<V> valueEndec, int size) {
        return new Map<>(valueEndec);
    }

    @Override
    public Struct struct() {
        return new Map<>(null);
    }

    // ---

    private class Map<V> implements Serializer.Map<V>, Struct {

        private final Endec<V> valueEndec;
        private final NbtCompound result;

        private Map(Endec<V> valueEndec) {
            this.valueEndec = valueEndec;

            if (NbtSerializer.this.prefix != null) {
                if (NbtSerializer.this.prefix instanceof NbtCompound prefixMap) {
                    this.result = prefixMap;
                } else {
                    throw new IllegalStateException("Incompatible prefix of type " + NbtSerializer.this.prefix.getClass().getSimpleName() + " provided for NBT map/struct");
                }
            } else {
                this.result = new NbtCompound();
            }
        }

        @Override
        public void entry(String key, V value) {
            NbtSerializer.this.frame(encoded -> {
                this.valueEndec.encode(NbtSerializer.this, value);
                this.result.put(key, encoded.require("map value"));
            }, false);
        }

        @Override
        public <F> Struct field(String name, Endec<F> endec, F value) {
            NbtSerializer.this.frame(encoded -> {
                endec.encode(NbtSerializer.this, value);
                if (encoded.wasEncoded()) {
                    this.result.put(name, encoded.get());
                }
            }, true);

            return this;
        }

        @Override
        public void end() {
            NbtSerializer.this.consume(this.result);
        }
    }

    private class Sequence<V> implements Serializer.Sequence<V> {

        private final Endec<V> valueEndec;
        private final NbtList result;

        private Sequence(Endec<V> valueEndec) {
            this.valueEndec = valueEndec;

            if (NbtSerializer.this.prefix != null) {
                if (NbtSerializer.this.prefix instanceof NbtList prefixList) {
                    this.result = prefixList;
                } else {
                    throw new IllegalStateException("Incompatible prefix of type " + NbtSerializer.this.prefix.getClass().getSimpleName() + " provided for NBT sequence");
                }
            } else {
                this.result = new NbtList();
            }
        }

        @Override
        public void element(V element) {
            NbtSerializer.this.frame(encoded -> {
                this.valueEndec.encode(NbtSerializer.this, element);
                this.result.add(encoded.require("sequence element"));
            }, false);
        }

        @Override
        public void end() {
            NbtSerializer.this.consume(this.result);
        }
    }
}
