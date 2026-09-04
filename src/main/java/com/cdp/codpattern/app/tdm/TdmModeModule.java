package com.cdp.codpattern.app.tdm;

import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import com.cdp.codpattern.app.match.extension.ModeModule;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.tdm.model.TdmGameModeDefinitions;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmLoginRecoveryContributor;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Built-in Frontline and Team Deathmatch module. */
public final class TdmModeModule implements ModeModule {
    public static final TdmModeModule INSTANCE = new TdmModeModule();
    private static final ResourceLocation ID = new ResourceLocation("codpattern", "team_modes");
    private static final List<ModePlayerLoginContributor> LOGIN_CONTRIBUTORS =
            List.of(new CodTdmLoginRecoveryContributor());
    private static final List<ModeHeldToolPreviewContributor> TOOL_PREVIEWS = List.of(
            new MapCreatorPreviewContributor(),
            new SpawnPointPreviewContributor());

    private TdmModeModule() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<GameModeDefinition> definitions() {
        return TdmGameModeDefinitions.definitions();
    }

    @Override
    public List<ModePlayerLoginContributor> playerLoginContributors() {
        return LOGIN_CONTRIBUTORS;
    }

    @Override
    public List<ModeHeldToolPreviewContributor> heldToolPreviewContributors() {
        return TOOL_PREVIEWS;
    }

    private static final class MapCreatorPreviewContributor implements ModeHeldToolPreviewContributor {
        @Override
        public String id() {
            return "fpsm.map_creator";
        }

        @Override
        public int order() {
            return 10;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return stack != null && stack.getItem() instanceof MapCreatorTool;
        }

        @Override
        public void sync(ServerPlayer player, ItemStack stack) {
            ((MapCreatorTool) stack.getItem()).syncHeldPreview(player, stack);
        }

        @Override
        public void clear(ServerPlayer player) {
            MapCreatorTool.clearHeldPreview(player);
        }
    }

    private static final class SpawnPointPreviewContributor implements ModeHeldToolPreviewContributor {
        @Override
        public String id() {
            return "fpsm.spawn_point";
        }

        @Override
        public int order() {
            return 20;
        }

        @Override
        public boolean matches(ItemStack stack) {
            return stack != null && stack.getItem() instanceof SpawnPointTool;
        }

        @Override
        public void sync(ServerPlayer player, ItemStack stack) {
            ((SpawnPointTool) stack.getItem()).syncHeldPreview(player, stack);
        }

        @Override
        public void clear(ServerPlayer player) {
            SpawnPointTool.clearHeldPreview(player);
        }
    }
}
