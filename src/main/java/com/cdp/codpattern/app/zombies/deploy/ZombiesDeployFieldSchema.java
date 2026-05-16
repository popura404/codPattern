package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

public final class ZombiesDeployFieldSchema {
    public static final String INITIAL = "INITIAL";
    public static final String ZOMBIE_SPAWN = "zombie_spawn";
    public static final String BARRIER = "barrier";
    public static final String WEAPON_WALL = "weapon_wall";
    public static final String AMMO_BOX = "ammo_box";
    public static final String ARMOR_STATION = "armor_station";
    public static final String POWER_SWITCH = "power_switch";
    public static final String SODA_MACHINE = "soda_machine";
    public static final String ULTIMATE_MACHINE = "ultimate_machine";

    public static final String PROFILE_MVP1 = ZombiesMapValidationProfile.MVP1_MINIMAL_KEY;
    public static final String PROFILE_MVP2 = ZombiesMapValidationProfile.MVP2_PURCHASES_KEY;
    public static final String PROFILE_MVP3 = ZombiesMapValidationProfile.MVP3_FULL_INITIAL_KEY;

    private static final List<String> PROFILES = List.of(PROFILE_MVP1, PROFILE_MVP2, PROFILE_MVP3);

    private static final List<ObjectTypeSchema> OBJECT_TYPES = List.of(
            new ObjectTypeSchema(INITIAL, "gui.codpattern.zombies.deploy.type.initial", false, false, List.of(
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("yaw", FieldType.DECIMAL, "0.0"),
                    field("pitch", FieldType.DECIMAL, "0.0")
            )),
            new ObjectTypeSchema(ZOMBIE_SPAWN, "gui.codpattern.zombies.deploy.type.zombie_spawn", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("group", FieldType.INTEGER, "1"),
                    field("weight", FieldType.DECIMAL, "1.0"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("yaw", FieldType.DECIMAL, "0.0"),
                    field("pitch", FieldType.DECIMAL, "0.0")
            )),
            new ObjectTypeSchema(BARRIER, "gui.codpattern.zombies.deploy.type.barrier", true, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("group", FieldType.INTEGER, "2"),
                    field("cost", FieldType.INTEGER, "750"),
                    field("blocksPlayersOnly", FieldType.BOOLEAN, "true"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("areaFromX", FieldType.INTEGER, "0"),
                    field("areaFromY", FieldType.INTEGER, "64"),
                    field("areaFromZ", FieldType.INTEGER, "0"),
                    field("areaToX", FieldType.INTEGER, "0"),
                    field("areaToY", FieldType.INTEGER, "66"),
                    field("areaToZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "65"),
                    field("interactionZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(WEAPON_WALL, "gui.codpattern.zombies.deploy.type.weapon_wall", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("weaponLevel", FieldType.INTEGER, "1"),
                    field("levelDamageMultiplier", FieldType.DECIMAL, "1.0"),
                    field("price", FieldType.INTEGER, "500"),
                    field("maxReserveAmmo", FieldType.INTEGER, "90"),
                    field("refreshWaves", FieldType.LIST, "1"),
                    field("rarityPools", FieldType.LIST, "common=1,1.0,0.0"),
                    field("weapons", FieldType.LIST, "codpattern:pistol|common=1.0"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "64"),
                    field("interactionZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(AMMO_BOX, "gui.codpattern.zombies.deploy.type.ammo_box", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("pricesByWeaponLevel", FieldType.LIST, "1=0,2=250,3=500"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "64"),
                    field("interactionZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(ARMOR_STATION, "gui.codpattern.zombies.deploy.type.armor_station", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("armorLevel", FieldType.INTEGER, "1"),
                    field("buyCost", FieldType.INTEGER, "500"),
                    field("damageTakenMultiplier", FieldType.DECIMAL, "0.9"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "64"),
                    field("interactionZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(POWER_SWITCH, "gui.codpattern.zombies.deploy.type.power_switch", false, true, List.of(
                    field("objectId", FieldType.TEXT, "power_switch"),
                    field("block", FieldType.TEXT, "codpattern:zombies_power_switch"),
                    field("cost", FieldType.INTEGER, "1000"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(SODA_MACHINE, "gui.codpattern.zombies.deploy.type.soda_machine", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("buffId", FieldType.TEXT, "double_health"),
                    field("cost", FieldType.INTEGER, "1500"),
                    field("requiresPower", FieldType.BOOLEAN, "true"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "64"),
                    field("interactionZ", FieldType.INTEGER, "0")
            )),
            new ObjectTypeSchema(ULTIMATE_MACHINE, "gui.codpattern.zombies.deploy.type.ultimate_machine", false, false, List.of(
                    field("objectId", FieldType.TEXT, ""),
                    field("maxUpgradeLevel", FieldType.INTEGER, "2"),
                    field("levels", FieldType.LIST, "1=2500:1.5;2=5000:2.0"),
                    field("requiresPower", FieldType.BOOLEAN, "true"),
                    field("dimension", FieldType.TEXT, "minecraft:overworld"),
                    field("posX", FieldType.INTEGER, "0"),
                    field("posY", FieldType.INTEGER, "64"),
                    field("posZ", FieldType.INTEGER, "0"),
                    field("interactionX", FieldType.INTEGER, "0"),
                    field("interactionY", FieldType.INTEGER, "64"),
                    field("interactionZ", FieldType.INTEGER, "0")
            ))
    );

    private ZombiesDeployFieldSchema() {
    }

    public static List<ObjectTypeSchema> objectTypes() {
        return OBJECT_TYPES;
    }

    public static Optional<ObjectTypeSchema> objectType(String key) {
        String normalized = normalizeObjectType(key);
        return OBJECT_TYPES.stream()
                .filter(type -> type.key().equals(normalized))
                .findFirst();
    }

    public static List<String> objectTypeKeys() {
        return OBJECT_TYPES.stream().map(ObjectTypeSchema::key).toList();
    }

    public static List<String> profiles() {
        return PROFILES;
    }

    public static String normalizeObjectType(String key) {
        String lower = normalizeObjectTypeRaw(key);
        if (INITIAL.equals(lower)) {
            return INITIAL;
        }
        for (ObjectTypeSchema type : OBJECT_TYPES) {
            if (type.key().equals(lower)) {
                return type.key();
            }
        }
        return INITIAL;
    }

    private static String normalizeObjectTypeRaw(String key) {
        String normalized = Objects.requireNonNullElse(key, "").trim();
        if (normalized.equalsIgnoreCase(INITIAL)
                || normalized.equalsIgnoreCase("initial")
                || normalized.equalsIgnoreCase("initial_spawn")) {
            return INITIAL;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeProfile(String profileKey) {
        String normalized = Objects.requireNonNullElse(profileKey, "").trim().toUpperCase(Locale.ROOT);
        return PROFILES.contains(normalized) ? normalized : PROFILE_MVP1;
    }

    public static Map<String, String> defaultFields(String objectType) {
        Map<String, String> defaults = new LinkedHashMap<>();
        objectType(objectType).orElse(OBJECT_TYPES.get(0)).fields().forEach(field ->
                defaults.put(field.key(), field.defaultValue()));
        return defaults;
    }

    public static String labelKeyForField(String fieldKey) {
        return "gui.codpattern.zombies.deploy.field." + Objects.requireNonNullElse(fieldKey, "").trim();
    }

    private static FieldDefinition field(String key, FieldType type, String defaultValue) {
        return new FieldDefinition(
                key,
                labelKeyForField(key),
                type,
                defaultValue,
                true);
    }

    public enum FieldType {
        TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        LIST
    }

    public record ObjectTypeSchema(
            String key,
            String labelKey,
            boolean areaType,
            boolean singleObject,
            List<FieldDefinition> fields
    ) {
        public ObjectTypeSchema {
            key = normalizeObjectTypeRaw(key);
            labelKey = Objects.requireNonNullElse(labelKey, "").trim();
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record FieldDefinition(
            String key,
            String labelKey,
            FieldType type,
            String defaultValue,
            boolean editable
    ) {
        public FieldDefinition {
            key = Objects.requireNonNullElse(key, "").trim();
            labelKey = Objects.requireNonNullElse(labelKey, "").trim();
            type = type == null ? FieldType.TEXT : type;
            defaultValue = Objects.requireNonNullElse(defaultValue, "");
        }
    }
}
