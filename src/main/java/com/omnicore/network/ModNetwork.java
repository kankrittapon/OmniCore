package com.omnicore.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("omnicore", "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL)
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(AnnouncementPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnnouncementPacket::encode)
                .decoder(AnnouncementPacket::decode)
                .consumerMainThread(AnnouncementPacket::handle)
                .add();

        CHANNEL.messageBuilder(WorldBossSyncPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldBossSyncPacket::encode)
                .decoder(WorldBossSyncPacket::decode)
                .consumerMainThread(WorldBossSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(TogglePvPPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TogglePvPPacket::encode)
                .decoder(TogglePvPPacket::decode)
                .consumerMainThread(TogglePvPPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncPvPStatePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPvPStatePacket::encode)
                .decoder(SyncPvPStatePacket::decode)
                .consumerMainThread(SyncPvPStatePacket::handle)
                .add();
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
