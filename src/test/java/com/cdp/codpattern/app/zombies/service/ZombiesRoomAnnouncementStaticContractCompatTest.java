package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesRoomAnnouncementStaticContractCompatTest {
    private static final Path BARRIER_DATA =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/map/object/ZombiesBarrierData.java");
    private static final Path FIELD_SCHEMA =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployFieldSchema.java");
    private static final Path OBJECT_EDITOR =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployObjectEditor.java");
    private static final Path OBJECT_STATE_STORE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesObjectStateStore.java");
    private static final Path INTERACTION_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesObjectInteractionService.java");
    private static final Path ANNOUNCEMENT_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesRoomAnnouncementService.java");
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path EN_US_LANG =
            Path.of("src/main/resources/assets/codpattern/lang/en_us.json");
    private static final Path ZH_CN_LANG =
            Path.of("src/main/resources/assets/codpattern/lang/zh_cn.json");

    private ZombiesRoomAnnouncementStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String barrierData = read(BARRIER_DATA);
        String fieldSchema = read(FIELD_SCHEMA);
        String objectEditor = read(OBJECT_EDITOR);
        String objectStateStore = read(OBJECT_STATE_STORE);
        String interactionService = read(INTERACTION_SERVICE);
        String announcementService = read(ANNOUNCEMENT_SERVICE);
        String zombiesMap = read(ZOMBIES_MAP);
        String enUsLang = read(EN_US_LANG);
        String zhCnLang = read(ZH_CN_LANG);

        requireContains(barrierData,
                "String name,",
                "barrier data must expose a persisted display name");
        requireContains(barrierData,
                "Codec.STRING.optionalFieldOf(\"name\", \"\").forGetter(ZombiesBarrierData::name)",
                "barrier name must remain optional for old map files");
        requireContains(barrierData,
                "public String displayName()",
                "barrier data must provide a fallback display name");
        requireContains(barrierData,
                "return name.isBlank() ? objectId : name;",
                "blank barrier names must fall back to object id");

        requireContains(fieldSchema,
                "field(\"name\", FieldType.TEXT, \"\")",
                "deploy schema must expose the barrier name field");
        requireContains(objectEditor,
                "source.name(),",
                "duplicating a barrier must preserve its name");
        requireContains(objectEditor,
                "text(fields, \"name\"),",
                "barrier parsing must read the name field");
        requireContains(objectEditor,
                "fields.put(\"name\", data.name());",
                "barrier editing must round-trip the name field");
        requireContains(objectStateStore,
                "payload.putString(PAYLOAD_NAME, barrier.displayName());",
                "client object state must publish the display name");

        requireContains(announcementService,
                "ClientboundSetSubtitleTextPacket",
                "room announcements must use subtitle display");
        requireContains(announcementService,
                "ClientboundSetTitleTextPacket(Component.empty())",
                "room announcements must clear the title while showing subtitle text");
        requireContains(announcementService,
                "SoundEvents.NOTE_BLOCK_CHIME.get()",
                "room announcements must play a music-box style unlock sound");
        requireContains(interactionService,
                "ANNOUNCEMENT_BARRIER = MESSAGE_PREFIX + \"announcement.barrier\"",
                "barrier opening must have a dedicated announcement key");
        requireContains(interactionService,
                "ANNOUNCEMENT_POWER = MESSAGE_PREFIX + \"announcement.power\"",
                "power opening must have a dedicated announcement key");
        requireContains(interactionService,
                "announcementService.broadcastSubtitle(\n                    ANNOUNCEMENT_BARRIER,\n                    playerDisplayName(player),\n                    barrier.displayName())",
                "barrier purchase success must broadcast player and barrier name");
        requireContains(interactionService,
                "announcementService.broadcastSubtitle(\n                        ANNOUNCEMENT_POWER,\n                        playerDisplayName(player))",
                "initial power purchase success must broadcast player name");
        requireContains(zombiesMap,
                "new ZombiesRoomAnnouncementService(this::survivorPlayers)",
                "runtime map must scope announcements to players in the room");

        requireContains(enUsLang,
                "\"message.codpattern.zombies.interaction.announcement.barrier\"",
                "English lang must include barrier announcement key");
        requireContains(enUsLang,
                "\"message.codpattern.zombies.interaction.announcement.power\"",
                "English lang must include power announcement key");
        requireContains(zhCnLang,
                "\"message.codpattern.zombies.interaction.announcement.barrier\"",
                "Chinese lang must include barrier announcement key");
        requireContains(zhCnLang,
                "\"message.codpattern.zombies.interaction.announcement.power\"",
                "Chinese lang must include power announcement key");
        requireContains(zhCnLang,
                "\"gui.codpattern.zombies.deploy.field.name\"",
                "Chinese lang must include deploy name field label");

        System.out.println("PASS zombies room announcement static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
