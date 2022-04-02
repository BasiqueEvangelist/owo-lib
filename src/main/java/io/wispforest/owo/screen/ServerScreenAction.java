package io.wispforest.owo.screen;

import io.wispforest.owo.network.serialization.PacketBufSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

public class ServerScreenAction<T> {
    private final int actionId;
    private final EnvType targetEnv;
    private final PacketBufSerializer<T> serializer;
    private final Consumer<T> executor;
    private final ServerScreen<?> screen;

    ServerScreenAction(int actionId, EnvType targetEnv, Class<T> klass, Consumer<T> executor, ServerScreen<?> screen) {
        this.actionId = actionId;
        this.targetEnv = targetEnv;
        this.serializer = PacketBufSerializer.get(klass);
        this.executor = executor;
        this.screen = screen;
    }

    public void run(T data) {
        var screenEnv = screen.player.world.isClient ? EnvType.CLIENT : EnvType.SERVER;

        if (screenEnv == targetEnv) {
            executor.accept(data);
        } else {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(screen.screenId);
            buf.writeVarInt(actionId);
            serializer.serializer().accept(buf, data);

            if (screenEnv == EnvType.CLIENT) {
                ClientPlayNetworking.send(ServerScreenInternals.RUN_ACTION_ID, buf);
            } else {
                ServerPlayNetworking.send((ServerPlayerEntity) screen.player, ServerScreenInternals.RUN_ACTION_ID, buf);
            }
        }
    }

    void readAndRun(PacketByteBuf buf) {
        T data = serializer.deserializer().apply(buf);
        executor.accept(data);
    }
}
