package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.taczaddon.TaczAddonRefitCompat;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.network.SyncAttachmentPresetPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AttachmentPresetRequestService {
    public record Result(SyncAttachmentPresetPacket packet, boolean presetLoaded, int sandboxAttachmentCount,
            int truncatedAttachmentCount) {
    }

    private AttachmentPresetRequestService() {
    }

    public static Optional<Result> prepare(ServerPlayer player, int bagId, String slot) {
        if (player.server == null) {
            return Optional.empty();
        }
        if (player.isSpectator()) {
            player.sendSystemMessage(Component.translatable("message.codpattern.refit.spectator_blocked"));
            return Optional.empty();
        }
        if (!"primary".equals(slot) && !"secondary".equals(slot)) {
            return Optional.empty();
        }

        String uuid = player.getUUID().toString();
        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(player.server);
        Path filterPath = ConfigPath.SERVER_FILTER.getPath(player.server);
        BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, backpackPath);
        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
        BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(bagId);
        if (backpack == null) {
            return Optional.empty();
        }
        BackpackConfig.Backpack.ItemData itemData = backpack.getItem_MAP().get(slot);
        if (itemData == null) {
            return Optional.empty();
        }

        ItemStack originalGunStack = buildItemStack(itemData);
        if (!TaczGatewayProvider.gateway().isValidGun(originalGunStack)) {
            return Optional.empty();
        }
        ItemStack gunStack = originalGunStack.copy();

        TaczAddonRefitCompat.sanitizeGunForBackpackRefitSession(gunStack);

        String storedPreset = itemData.getAttachmentPreset();
        Optional<String> storedPresetPayload = storedPreset == null || storedPreset.isBlank()
                ? Optional.empty()
                : Optional.of(storedPreset);
        CompoundTag presetTag = storedPresetPayload.map(AttachmentPresetUtil::parsePresetString).orElseGet(CompoundTag::new);
        if (!presetTag.isEmpty()) {
            AttachmentPresetUtil.applyPresetToGun(gunStack, presetTag);
        }
        boolean sanitizedBlockedAttachments = BackpackAttachmentFilter.removeBlockedInstalledAttachments(filterConfig,
                gunStack);
        if (!presetTag.isEmpty() || sanitizedBlockedAttachments) {
            TaczGatewayProvider.gateway().postAttachmentChanged(player, gunStack);
        }

        String syncedPresetPayloadRaw = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
        Optional<String> syncedPresetPayload = syncedPresetPayloadRaw == null || syncedPresetPayloadRaw.isBlank()
                ? Optional.empty()
                : Optional.of(syncedPresetPayloadRaw);

        Optional<String> expectedGunIdOpt = TaczGatewayProvider.gateway().resolveGunId(gunStack);
        if (expectedGunIdOpt.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> playerOwnedAttachments = collectPlayerOwnedAttachments(player, gunStack, filterConfig);
        List<ItemStack> addonCompatibleAttachments = collectAddonCompatibleAttachments(
                player,
                originalGunStack,
                gunStack,
                filterConfig,
                playerOwnedAttachments);
        List<ItemStack> sandboxAttachments = mergeAttachmentCandidates(playerOwnedAttachments, addonCompatibleAttachments);
        AttachmentEditSession session = AttachmentEditSessionManager.startSession(
                player, bagId, slot, gunStack, sandboxAttachments);
        SyncAttachmentPresetPacket packet = new SyncAttachmentPresetPacket(
                bagId,
                slot,
                syncedPresetPayload.orElse(""),
                expectedGunIdOpt.get());
        return Optional.of(new Result(
                packet,
                syncedPresetPayload.isPresent(),
                session.getSandboxAttachmentCount(),
                session.getTruncatedAttachmentCount()));
    }

    private static List<ItemStack> collectPlayerOwnedAttachments(ServerPlayer player,
            ItemStack gunStack,
            WeaponFilterConfig filterConfig) {
        List<ItemStack> attachments = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (TaczGatewayProvider.gateway().canAttach(gunStack, stack)
                    && !BackpackAttachmentFilter.isAttachmentBlocked(filterConfig, stack)) {
                attachments.add(stack.copy());
            }
        }
        return attachments;
    }

    private static List<ItemStack> collectAddonCompatibleAttachments(ServerPlayer player,
            ItemStack originalGunStack,
            ItemStack workingGunStack,
            WeaponFilterConfig filterConfig,
            List<ItemStack> existingCandidates) {
        List<ItemStack> candidates = new ArrayList<>();
        Set<String> existingAttachmentIds = new HashSet<>();
        for (ItemStack existing : existingCandidates) {
            TaczGatewayProvider.gateway().resolveAttachmentId(existing).ifPresent(existingAttachmentIds::add);
        }

        for (ItemStack candidate : TaczAddonRefitCompat.resolveBackpackRefitCandidates(player, originalGunStack)) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            Optional<String> attachmentIdOpt = TaczGatewayProvider.gateway().resolveAttachmentId(candidate);
            if (attachmentIdOpt.isEmpty()) {
                continue;
            }
            if (existingAttachmentIds.contains(attachmentIdOpt.get())) {
                continue;
            }
            if (BackpackAttachmentFilter.isAttachmentBlocked(filterConfig, candidate)) {
                continue;
            }
            if (!TaczGatewayProvider.gateway().canAttach(workingGunStack, candidate)) {
                continue;
            }
            candidates.add(candidate.copy());
            existingAttachmentIds.add(attachmentIdOpt.get());
        }
        return candidates;
    }

    private static List<ItemStack> mergeAttachmentCandidates(List<ItemStack> playerOwnedAttachments,
            List<ItemStack> addonCompatibleAttachments) {
        if (addonCompatibleAttachments.isEmpty()) {
            return playerOwnedAttachments;
        }
        List<ItemStack> merged = new ArrayList<>(playerOwnedAttachments.size() + addonCompatibleAttachments.size());
        merged.addAll(playerOwnedAttachments);
        merged.addAll(addonCompatibleAttachments);
        return merged;
    }

    private static ItemStack buildItemStack(BackpackConfig.Backpack.ItemData itemData) {
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
            if (itemId == null) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            ItemStack stack = new ItemStack(item, itemData.getCount());
            String nbt = itemData.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                stack.setTag(TagParser.parseTag(nbt));
            }
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
