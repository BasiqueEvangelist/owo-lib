package io.wispforest.owo.screen;

import io.wispforest.owo.network.NetworkException;
import io.wispforest.owo.network.serialization.PacketBufSerializer;
import io.wispforest.owo.network.serialization.RecordSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

class ServerScreenAction<R extends Record> {
    private final int actionId;
    private final EnvType targetEnv;
    private final RecordSerializer<R> serializer;
    private final Class<R> dataClass;
    Consumer<R> executor;
    private final ServerScreen<?, ?> screen;

    ServerScreenAction(int actionId, EnvType targetEnv, Class<R> klass, Consumer<R> executor, ServerScreen<?, ?> screen) {
        this.actionId = actionId;
        this.targetEnv = targetEnv;
        this.serializer = RecordSerializer.create(klass);
        dataClass = klass;
        this.executor = executor;
        this.screen = screen;
    }

    void run(R data) {
        var screenEnv = screen.player.world.isClient ? EnvType.CLIENT : EnvType.SERVER;

        if (executor == null) {
            throw new NetworkException("Executor wasn't registered for " + dataClass);
        }

        if (screenEnv == targetEnv) {
            executor.accept(data);
        } else {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(screen.screenId);
            buf.writeVarInt(actionId);
            serializer.write(buf, data);

            if (screenEnv == EnvType.CLIENT) {
                ClientPlayNetworking.send(ServerScreenInternals.RUN_ACTION_ID, buf);
            } else {
                ServerPlayNetworking.send((ServerPlayerEntity) screen.player, ServerScreenInternals.RUN_ACTION_ID, buf);
            }
        }
    }

    void readAndRun(PacketByteBuf buf) {
        R data = serializer.read(buf);
        executor.accept(data);
    }
}
