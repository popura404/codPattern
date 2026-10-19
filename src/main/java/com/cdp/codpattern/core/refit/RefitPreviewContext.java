package com.cdp.codpattern.core.refit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** The synchronous side effects of one server-authorized configuration operation. */
public final class RefitPreviewContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private RefitPreviewContext() {
    }

    public static Scope open(ServerPlayer player, ItemStack workingGun) {
        Scope scope = new Scope(Objects.requireNonNull(player), Objects.requireNonNull(workingGun), CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    public static boolean isActiveFor(Player player) {
        Scope scope = CURRENT.get();
        return scope != null && scope.player == player;
    }

    public static boolean isActiveFor(ItemStack gun) {
        Scope scope = CURRENT.get();
        return scope != null && scope.workingGun == gun;
    }

    public static final class Scope implements AutoCloseable {
        private final ServerPlayer player;
        private final ItemStack workingGun;
        private final Scope parent;
        private boolean closed;

        private Scope(ServerPlayer player, ItemStack workingGun, Scope parent) {
            this.player = player;
            this.workingGun = workingGun;
            this.parent = parent;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT.get() != this) {
                throw new IllegalStateException("Refit preview scopes must be closed in order");
            }
            closed = true;
            if (parent == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(parent);
            }
        }
    }
}
