package io.wispforest.owo.screen;

import io.wispforest.owo.client.screens.ClientServerScreen;
import io.wispforest.owo.network.NetworkException;
import io.wispforest.owo.network.serialization.PacketBufSerializer;
import io.wispforest.owo.util.OwoFreezer;
import io.wispforest.owo.util.ReflectionUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ServerScreenType<T extends ServerScreen<T, D>, D> {

    static final Map<Identifier, ServerScreenType<?, ?>> REGISTERED_TYPES = new HashMap<>();
    private final Identifier id;
    private final PacketBufSerializer<D> dataSerializer;
    private final String ownerClassName;
    private final Factory<T, D> factory;
    Function<T, ClientServerScreen<T>> screenFactory;

    private ServerScreenType(Identifier id, Class<D> dataType, String ownerClassName, Factory<T, D> factory) {
        this.factory = factory;
        OwoFreezer.checkRegister("Server screens");

        if (REGISTERED_TYPES.containsKey(id)) {
            throw new IllegalStateException("Server screen with id '" + id + "' was already registered from class '" + REGISTERED_TYPES.get(id).ownerClassName + "'");
        }

        REGISTERED_TYPES.put(id, this);

        this.id = id;
        this.dataSerializer = PacketBufSerializer.get(dataType);
        this.ownerClassName = ownerClassName;
    }

    public static <T extends ServerScreen<T, D>, D> ServerScreenType<T, D> create(Identifier id, Class<D> dataClass, Factory<T, D> factory) {
        return new ServerScreenType<>(id, dataClass, ReflectionUtils.getCallingClassName(2), factory);
    }

    public ServerScreenType<T, D> setScreenFactory(Function<T, ClientServerScreen<T>> factory) {
        OwoFreezer.checkRegister("Screen factories");
        if (this.screenFactory != null) throw new NetworkException("Server screen type already has a screen factory");

        screenFactory = factory;
        return this;
    }

    Packet<?> createOpenPacket(D data, int screenId) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeIdentifier(id);
        buf.writeVarInt(screenId);
        dataSerializer.serializer().accept(buf, data);

        return ServerPlayNetworking.createS2CPacket(ServerScreenInternals.OPEN_SCREEN_ID, buf);
    }

    public Identifier getId() {
        return id;
    }

    public interface Factory<S extends ServerScreen<S, T>, T> {
        S createServerScreen(int screenId, PlayerEntity player, T data);
    }

    T readScreen(PlayerEntity player, PacketByteBuf buf) {
        int screenId = buf.readVarInt();
        D data = dataSerializer.deserializer().apply(buf);

        return factory.createServerScreen(screenId, player, data);
    }
}
