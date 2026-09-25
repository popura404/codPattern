package com.cdp.codpattern.app.match.runtime.termination;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.ForgeEventFactory;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Vanilla player NBT format, with observable write failure before recovery is acknowledged. */
final class PlayerRecoveryPersistence {
    private PlayerRecoveryPersistence() { }

    static void save(ServerPlayer player) throws IOException {
        Path directory = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Files.createDirectories(directory);
        Path target = directory.resolve(player.getStringUUID() + ".dat");
        Path temporary = Files.createTempFile(directory, player.getStringUUID() + "-recovery-", ".dat");
        try {
            CompoundTag snapshot = player.saveWithoutId(new CompoundTag());
            NbtIo.writeCompressed(snapshot, temporary.toFile());
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.exists(target)) Files.copy(target, directory.resolve(player.getStringUUID() + ".dat_old"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            if (!snapshot.equals(NbtIo.readCompressed(target.toFile()))) throw new IOException("Player recovery save verification failed");
            ForgeEventFactory.firePlayerSavingEvent(player, directory.toFile(), player.getStringUUID());
        } finally { Files.deleteIfExists(temporary); }
    }
}
