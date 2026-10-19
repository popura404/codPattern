package com.cdp.codpattern.config;

import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Converts repository failures to brief player feedback without exposing file paths or stack traces. */
public final class ConfigFailureFeedback {
    private ConfigFailureFeedback() {
    }

    public static boolean run(ServerPlayer player, Runnable action) {
        try {
            action.run();
            return true;
        } catch (BackpackConfigRepository.RepositoryException | WeaponFilterConfigRepository.RepositoryException failure) {
            report(player, failure);
            return false;
        }
    }

    public static void report(ServerPlayer player, RuntimeException failure) {
        player.sendSystemMessage(message(failure));
    }

    public static Component message(Throwable failure) {
        return Component.translatable(translationKey(failure));
    }

    public static String translationKey(Throwable failure) {
        return switch (operation(failure)) {
            case "READ" -> "message.codpattern.config.read_failed";
            case "PARSE" -> "message.codpattern.config.parse_failed";
            case "WRITE" -> "message.codpattern.config.write_failed";
            default -> throw new IllegalStateException("Unknown repository operation");
        };
    }

    public static String resultCode(Throwable failure) {
        return "CONFIG_" + operation(failure) + "_FAILED";
    }

    private static String operation(Throwable failure) {
        if (failure instanceof BackpackConfigRepository.RepositoryException backpackFailure) {
            return backpackFailure.getOperation().name();
        } else if (failure instanceof WeaponFilterConfigRepository.RepositoryException filterFailure) {
            return filterFailure.getOperation().name();
        } else {
            throw new IllegalArgumentException("Expected a configuration repository failure", failure);
        }
    }
}
