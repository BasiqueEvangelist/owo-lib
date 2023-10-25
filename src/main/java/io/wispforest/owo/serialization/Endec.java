package io.wispforest.owo.serialization;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import io.wispforest.owo.serialization.impl.*;
import io.wispforest.owo.serialization.impl.nbt.NbtEndec;
import io.wispforest.owo.serialization.impl.json.JsonEndec;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Endec<T> {

    Endec<Void> EMPTY = Endec.of((serializer, unused) -> {}, deserializer -> null);

    Endec<Boolean> BOOLEAN = Endec.of(Serializer::writeBoolean, Deserializer::readBoolean);
    Endec<Byte> BYTE = Endec.of(Serializer::writeByte, Deserializer::readByte);
    Endec<Short> SHORT = Endec.of(Serializer::writeShort, Deserializer::readShort);
    Endec<Integer> INT = Endec.of(Serializer::writeInt, Deserializer::readInt);
    Endec<Long> LONG = Endec.of(Serializer::writeLong, Deserializer::readLong);
    Endec<Float> FLOAT = Endec.of(Serializer::writeFloat, Deserializer::readFloat);
    Endec<Double> DOUBLE = Endec.of(Serializer::writeDouble, Deserializer::readDouble);
    Endec<String> STRING = Endec.of(Serializer::writeString, Deserializer::readString);

    Endec<byte[]> BYTE_ARRAY = Endec.of(Serializer::writeBytes, Deserializer::readBytes);

    Endec<int[]> INT_ARRAY = INT.list()
            .then((list) -> list.stream().mapToInt(v -> v).toArray(), (ints) -> Arrays.stream(ints).boxed().toList());

    Endec<long[]> LONG_ARRAY = LONG.list()
            .then((list) -> list.stream().mapToLong(v -> v).toArray(), (longs) -> Arrays.stream(longs).boxed().toList());

    //--

    Endec<JsonElement> JSON_ELEMENT = JsonEndec.INSTANCE;
    Endec<NbtElement> NBT_ELEMENT = NbtEndec.INSTANCE;

    Endec<NbtCompound> COMPOUND = Endec.NBT_ELEMENT.then(element -> ((NbtCompound) element), compound -> compound);

    //--

    Endec<Identifier> IDENTIFIER = Endec.STRING.then(Identifier::new, Identifier::toString);

    Endec<ItemStack> ITEM_STACK = COMPOUND.then(ItemStack::fromNbt, stack -> stack.writeNbt(new NbtCompound()));

    Endec<UUID> UUID = Endec.ifAttr(Endec.STRING.then(java.util.UUID::fromString, java.util.UUID::toString), SerializationAttribute.HUMAN_READABLE)
            .orElse(Endec.INT_ARRAY.then(Uuids::toUuid, Uuids::toIntArray));

    Endec<Date> DATE = Endec.ifAttr(Endec.STRING.then(s -> Date.from(Instant.parse(s)), date -> date.toInstant().toString()), SerializationAttribute.HUMAN_READABLE)
            .orElse(Endec.LONG.then(Date::new, Date::getTime));

    Endec<PacketByteBuf> PACKET_BYTE_BUF = Endec.BYTE_ARRAY
            .then(bytes -> {
                var byteBuf = PacketByteBufs.create();

                byteBuf.writeBytes(bytes);

                return byteBuf;
            }, byteBuf -> {
                var bytes = new byte[byteBuf.readableBytes()];

                byteBuf.readBytes(bytes);

                return bytes;
            });

    Endec<BlockPos> BLOCK_POS = Endec
            .ifAttr(
                    StructEndecBuilder.of(
                            StructField.of("x", StructEndec.INT, BlockPos::getX),
                            StructField.of("y", StructEndec.INT, BlockPos::getX),
                            StructField.of("z", StructEndec.INT, BlockPos::getX),
                            BlockPos::new
                    ),
                    SerializationAttribute.HUMAN_READABLE
            )
            .orElseIf(
                    Endec.INT.list().then(
                            ints -> new BlockPos(ints.get(0), ints.get(1), ints.get(2)),
                            blockPos -> List.of(blockPos.getX(), blockPos.getY(), blockPos.getZ())
                    ),
                    SerializationAttribute.COMPRESSED
            )
            .orElse(Endec.LONG.then(BlockPos::fromLong, BlockPos::asLong));

    Endec<ChunkPos> CHUNK_POS = Endec
            .ifAttr(
                    StructEndecBuilder.of(
                            StructField.of("x", StructEndec.INT, (ChunkPos pos) -> pos.x),
                            StructField.of("z", StructEndec.INT, (ChunkPos pos) -> pos.z),
                            ChunkPos::new
                    ),
                    SerializationAttribute.HUMAN_READABLE)
            .orElse(Endec.LONG.then(ChunkPos::new, ChunkPos::toLong));

    Endec<BitSet> BITSET = Endec.LONG_ARRAY.then(BitSet::valueOf, BitSet::toLongArray);

    Endec<Text> TEXT = Endec.JSON_ELEMENT.then(Text.Serializer::fromJson, Text.Serializer::toJsonTree);
            //endec.STRING.then(Text.Serializer::fromJson, Text.Serializer::toJson);

    Endec<Integer> VAR_INT = Endec.of(Serializer::writeVarInt, Deserializer::readVarInt);

    Endec<Long> VAR_LONG = Endec.of(Serializer::writeVarLong, Deserializer::readVarLong);

    //--

    //Kinda mega cursed but...
    static <T> Endec<T> of(BiConsumer<Serializer, T> encode, Function<Deserializer, T> decode) {
        return new Endec<>() {
            @Override
            public <E> void encode(Serializer<E> serializer, T value) {
                encode.accept(serializer, value);
            }

            @Override
            public <E> T decode(Deserializer<E> deserializer) {
                return decode.apply(deserializer);
            }
        };
    }

    static <T> Endec<T> ofRegistry(Registry<T> registry) {
        return Endec.IDENTIFIER.then(registry::get, registry::getId);
    }

    static <T, K> Endec<T> dispatchedOf(Function<K, Endec<? extends T>> keyToEndec, Function<T, K> keyGetter, Endec<K> keyEndec) {
        return new StructEndec<T>() {
            @Override
            public void encode(StructSerializer struct, T value) {
                var key = keyGetter.apply(value);

                struct.field("key", keyEndec, key)
                        .field("value", (Endec<T>) keyToEndec.apply(key), value);
            }

            @Override
            public T decode(StructDeserializer struct) {
                return struct.field("key", keyToEndec.apply(struct.field("value", keyEndec)));
            }
        };
    }

    static <K, V> Endec<Map<K, V>> mapOf(Endec<K> keyEndec, Endec<V> valueEndec){
        return mapOf(keyEndec, valueEndec, HashMap::new);
    }

    static <K, V> Endec<Map<K, V>> mapOf(Endec<K> keyEndec, Endec<V> valueEndec, Supplier<Map<K, V>> supplier){
        Endec<Map.Entry<K, V>> mapEntryEndec = StructEndecBuilder.of(
                StructField.of("k", keyEndec, Map.Entry::getKey),
                StructField.of("V", valueEndec, Map.Entry::getValue),
                Map::entry
        );

        return mapEntryEndec.list().then(entries -> {
            Map<K, V> map = supplier.get();

            for (Map.Entry<K, V> entry : entries) map.put(entry.getKey(), entry.getValue());

            return map;
        }, kvMap -> List.copyOf(kvMap.entrySet()));
    }

    static <T> AttributeEndecBuilder<T> ifAttr(Endec<T> endec, SerializationAttribute attribute){
        return new AttributeEndecBuilder<>(endec, attribute);
    }

    //--

    default ListEndec<T> list(){
        return new ListEndec<>(this);
    }

    default MapEndec<String, T> map(){
        return MapEndec.of(this);
    }

    default <R> Endec<R> then(Function<T, R> getter, Function<R, T> setter) {
        return new Endec<>() {
            @Override
            public <E> void encode(Serializer<E> serializer, R value) {
                Endec.this.encode(serializer, setter.apply(value));
            }

            @Override
            public <E> R decode(Deserializer<E> deserializer) {
                return getter.apply(Endec.this.decode(deserializer));
            }
        };
    }

    default Endec<Optional<T>> ofOptional(){
        return new Endec<>() {
            @Override
            public <E> void encode(Serializer<E> serializer, Optional<T> value) {
                serializer.writeOptional(Endec.this, value);
            }

            @Override
            public <E> Optional<T> decode(Deserializer<E> deserializer) {
                return deserializer.readOptional(Endec.this);
            }
        };
    }

    default Endec<@Nullable T> ofNullable(){
        return ofOptional().then(o -> o.orElse(null), Optional::ofNullable);
    }

    default Endec<T> onError(TriConsumer<Serializer, T, Exception> encode, BiFunction<Deserializer, Exception, T> decode){
        return new Endec<>() {
            @Override
            public <E> void encode(Serializer<E> serializer, T value) {
                try {
                    Endec.this.encode(serializer, value);
                } catch (Exception e){
                    encode.accept(serializer, value, e);
                }
            }

            @Override
            public <E> T decode(Deserializer<E> deserializer) {
                try {
                    return Endec.this.decode(deserializer);
                } catch (Exception e) {
                    return decode.apply(deserializer, e);
                }
            }
        };
    }

    default Codec<T> codec(){
        return new CooptCodec<>(this);
    }

    //--

    <E> void encode(Serializer<E> serializer, T value);

    <E> T decode(Deserializer<E> deserializer);

    default <E> E encode(Supplier<Serializer<E>> serializerCreator, T value){
        Serializer<E> serializer = serializerCreator.get();

        encode(serializer, value);

        return serializer.result();
    }

    default <E> T decode(Function<E, Deserializer<E>> deserializerCreator, E value){
        return decode(deserializerCreator.apply(value));
    }

}
