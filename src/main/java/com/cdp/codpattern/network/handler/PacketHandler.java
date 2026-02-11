package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
        private static final String PROTOCOL_VERSION = "3";
        public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation("codpattern", "main"),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals);

        private static int packetId = 0;

        private static int id() {
                return packetId++;
        }

        /**
         * 注册所有网络包
         */
        public static void register() {
                // 注册选择背包的数据包(C2S)
                INSTANCE.messageBuilder(SelectBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(SelectBackpackPacket::decode)
                                .encoder(SelectBackpackPacket::encode)
                                .consumerMainThread(SelectBackpackPacket::handle)
                                .add();

                // 注册添加背包的数据包(C2S)
                INSTANCE.messageBuilder(AddBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(AddBackpackPacket::decode)
                                .encoder(AddBackpackPacket::encode)
                                .consumerMainThread(AddBackpackPacket::handle)
                                .add();

                // 重命名背包 (C2S)
                INSTANCE.messageBuilder(RenameBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RenameBackpackPacket::decode)
                                .encoder(RenameBackpackPacket::encode)
                                .consumerMainThread(RenameBackpackPacket::handle)
                                .add();

                // 删除背包 (C2S)
                INSTANCE.messageBuilder(DeleteBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(DeleteBackpackPacket::decode)
                                .encoder(DeleteBackpackPacket::encode)
                                .consumerMainThread(DeleteBackpackPacket::handle)
                                .add();

                // 复制背包 (C2S)
                INSTANCE.messageBuilder(CloneBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(CloneBackpackPacket::decode)
                                .encoder(CloneBackpackPacket::encode)
                                .consumerMainThread(CloneBackpackPacket::handle)
                                .add();

                // 注册更新武器的数据包(C2S)
                INSTANCE.messageBuilder(UpdateWeaponPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(UpdateWeaponPacket::decode)
                                .encoder(UpdateWeaponPacket::encode)
                                .consumerMainThread(UpdateWeaponPacket::handle)
                                .add();

                // 注册配置同步数据包（S2C）
                INSTANCE.messageBuilder(SyncBackpackConfigPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(SyncBackpackConfigPacket::decode)
                                .encoder(SyncBackpackConfigPacket::encode)
                                .consumerMainThread(SyncBackpackConfigPacket::handle)
                                .add();

                // 请求配置（C2S）
                INSTANCE.messageBuilder(RequestBackpackConfigPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestBackpackConfigPacket::decode)
                                .encoder(RequestBackpackConfigPacket::encode)
                                .consumerMainThread(RequestBackpackConfigPacket::handle)
                                .add();

                // 请求武器过滤配置（C2S）
                INSTANCE.messageBuilder(RequestWeaponFilterPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestWeaponFilterPacket::decode)
                                .encoder(RequestWeaponFilterPacket::encode)
                                .consumerMainThread(RequestWeaponFilterPacket::handle)
                                .add();

                // 同步武器过滤配置（S2C）
                INSTANCE.messageBuilder(SyncWeaponFilterPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(SyncWeaponFilterPacket::decode)
                                .encoder(SyncWeaponFilterPacket::encode)
                                .consumerMainThread(SyncWeaponFilterPacket::handle)
                                .add();

                // 打开背包界面 (S2C)
                INSTANCE.messageBuilder(OpenBackpackScreenPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(OpenBackpackScreenPacket::decode)
                                .encoder(OpenBackpackScreenPacket::encode)
                                .consumerMainThread(OpenBackpackScreenPacket::handle)
                                .add();

                // 请求配件预设 (C2S)
                INSTANCE.messageBuilder(RequestAttachmentPresetPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestAttachmentPresetPacket::decode)
                                .encoder(RequestAttachmentPresetPacket::encode)
                                .consumerMainThread(RequestAttachmentPresetPacket::handle)
                                .add();

                // 同步配件预设 (S2C)
                INSTANCE.messageBuilder(SyncAttachmentPresetPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(SyncAttachmentPresetPacket::decode)
                                .encoder(SyncAttachmentPresetPacket::encode)
                                .consumerMainThread(SyncAttachmentPresetPacket::handle)
                                .add();

                // 保存配件预设 (C2S)
                INSTANCE.messageBuilder(SaveAttachmentPresetPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(SaveAttachmentPresetPacket::decode)
                                .encoder(SaveAttachmentPresetPacket::encode)
                                .consumerMainThread(SaveAttachmentPresetPacket::handle)
                                .add();

                // ========== TDM 数据包 (C2S) ==========

                // 请求房间列表
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.RequestRoomListPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.RequestRoomListPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.RequestRoomListPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.RequestRoomListPacket::handle)
                                .add();

                // 加入房间
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.JoinRoomPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.JoinRoomPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.JoinRoomPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.JoinRoomPacket::handle)
                                .add();

                // 离开房间
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.LeaveRoomPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.LeaveRoomPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.LeaveRoomPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.LeaveRoomPacket::handle)
                                .add();

                // 选择队伍
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.SelectTeamPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.SelectTeamPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.SelectTeamPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.SelectTeamPacket::handle)
                                .add();

                // 投票开始
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.VoteStartPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.VoteStartPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.VoteStartPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.VoteStartPacket::handle)
                                .add();

                // 投票结束
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.VoteEndPacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.VoteEndPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.VoteEndPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.VoteEndPacket::handle)
                                .add();

                // 投票响应（接受/拒绝）
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.VoteResponsePacket.class, id(),
                                NetworkDirection.PLAY_TO_SERVER)
                                .decoder(com.cdp.codpattern.network.tdm.VoteResponsePacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.VoteResponsePacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.VoteResponsePacket::handle)
                                .add();

                // ========== TDM 数据包 (S2C) ==========

                // 同步房间列表
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.RoomListSyncPacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.RoomListSyncPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.RoomListSyncPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.RoomListSyncPacket::handle)
                                .add();

                // 同步队伍玩家列表
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.TeamPlayerListPacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.TeamPlayerListPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.TeamPlayerListPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.TeamPlayerListPacket::handle)
                                .add();

                // 投票弹窗
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.VoteDialogPacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.VoteDialogPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.VoteDialogPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.VoteDialogPacket::handle)
                                .add();

                // 死亡视角
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.DeathCamPacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.DeathCamPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.DeathCamPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.DeathCamPacket::handle)
                                .add();

                // 游戏阶段
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.GamePhasePacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.GamePhasePacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.GamePhasePacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.GamePhasePacket::handle)
                                .add();

                // 倒计时
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.CountdownPacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.CountdownPacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.CountdownPacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.CountdownPacket::handle)
                                .add();

                // 分数更新
                INSTANCE.messageBuilder(com.cdp.codpattern.network.tdm.ScoreUpdatePacket.class, id(),
                                NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(com.cdp.codpattern.network.tdm.ScoreUpdatePacket::decode)
                                .encoder(com.cdp.codpattern.network.tdm.ScoreUpdatePacket::encode)
                                .consumerMainThread(com.cdp.codpattern.network.tdm.ScoreUpdatePacket::handle)
                                .add();
        }

        /**
         * 发送数据包到服务器
         */
        public static <MSG> void sendToServer(MSG message) {
                INSTANCE.sendToServer(message);
        }

        /**
         * 发送数据包到玩家
         */
        public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
}
