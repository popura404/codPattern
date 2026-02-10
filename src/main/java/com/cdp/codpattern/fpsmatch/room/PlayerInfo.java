package com.cdp.codpattern.fpsmatch.room;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * 玩家信息记录
 * 用于客户端显示的玩家信息
 */
public record PlayerInfo(
        UUID uuid,
        String name,
        boolean isReady, // 是否已准备（WAITING阶段）
        int kills, // 击杀数（比赛中）
        int deaths, // 死亡数（比赛中）
        boolean isAlive // 是否存活
) {
    /**
     * 写入网络缓冲
     */
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(name);
        buf.writeBoolean(isReady);
        buf.writeInt(kills);
        buf.writeInt(deaths);
        buf.writeBoolean(isAlive);
    }

    /**
     * 从网络缓冲读取
     */
    public static PlayerInfo read(FriendlyByteBuf buf) {
        return new PlayerInfo(
                buf.readUUID(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean());
    }
}
