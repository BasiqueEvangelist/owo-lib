package io.wispforest.owo.screen;

import io.wispforest.owo.network.serialization.PacketBufSerializer;
import io.wispforest.owo.util.OwoFreezer;
import io.wispforest.owo.util.ReflectionUtils;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class ServerScreenType<T> {

    private static final Map<Identifier, ServerScreenType<?>> REGISTERED_TYPES = new HashMap<>();
    private final Identifier id;
    private final PacketBufSerializer<T> dataSerializer;
    private final String ownerClassName;
    private final Factory<T> factory;

    private ServerScreenType(Identifier id, Class<T> dataType, String ownerClassName, Factory<T> factory) {
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

    public static <T> ServerScreenType<T> create(Identifier id, Class<T> dataClass, Factory<T> factory) {
        return new ServerScreenType<>(id, dataClass, ReflectionUtils.getCallingClassName(2), factory);
    }

    Packet<?> createOpenPacket(T data, int screenId) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeIdentifier(id);
        buf.writeVarInt(screenId);
        dataSerializer.serializer().accept(buf, data);

        return ServerPlayNetworking.createS2CPacket(ServerScreenInternals.OPEN_SCREEN_ID, buf);
    }

    public Identifier getId() {
        return id;
    }

    private interface Factory<T> {
        ServerScreen<T> createServerScreen(int screenId, T data);
    }

    ServerScreen<T> readScreen(PacketByteBuf buf) {
        int screenId = buf.readVarInt();
        T data = dataSerializer.deserializer().apply(buf);

        return factory.createServerScreen(screenId, data);
    }

    static {
        ServerPlayNetworking.registerGlobalReceiver(ServerScreenInternals.OPEN_SCREEN_ID, (server, player, handler, buf, responseSender) -> {
            Identifier id = buf.readIdentifier();
            ServerScreenType<?> screenType = REGISTERED_TYPES.get(id);
            ServerScreen<?> screen = screenType.readScreen(buf);
            screen.openFor(player);
        });
    }
}
