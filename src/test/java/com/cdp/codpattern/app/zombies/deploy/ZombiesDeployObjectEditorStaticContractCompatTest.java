package com.cdp.codpattern.app.zombies.deploy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesDeployObjectEditorStaticContractCompatTest {
    private static final Path EDITOR = Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployObjectEditor.java");

    private ZombiesDeployObjectEditorStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String editor = read(EDITOR);
        requireContains(editor,
                "private static int nextIndexAfterDelete(int deletedIndex, int newSize) {\n        return newSize <= 0 ? -1 : Math.min(deletedIndex, newSize - 1);\n    }",
                "delete should select next object at same index, previous tail object, or clear selection");
        requireContains(editor,
                "if (selectedIndex < 0) {\n            return mergedFields(type, Map.of());\n        }",
                "fieldsForSelection should return default fields for no selection");
        requireContains(editor,
                "yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);",
                "delete should rebuild fields from the post-delete selected object");
        requireContains(editor,
                "withAmmoBoxes(objects, List.of()),\n                    type,\n                    -1,\n                    mergedFields(type, Map.of()),\n                    objects.ammoBoxes().size())",
                "clear should empty ammo boxes and clear selected index");
        requireContains(editor,
                "withBarriers(objects, List.of()),\n                    type,\n                    -1,\n                    mergedFields(type, Map.of()),\n                    objects.barriers().size())",
                "clear should empty barriers and clear selected index");
        requireContains(editor,
                "withPowerSwitch(objects, Optional.empty()),\n                    type,\n                    -1,\n                    mergedFields(type, Map.of()),\n                    objects.powerSwitch().isPresent() ? 1 : 0)",
                "clear should remove power switch and clear selected index");
        requireContains(editor,
                "return EditResult.failure(failure.code, failure.getMessage(), objects, selectedIndex, resolvedFields);",
                "editor failures should keep original objects and selected index");
        requireContains(editor,
                "static final int MAX_INITIAL_PLAYER_SPAWNS = 4;",
                "INITIAL player spawn deployment should cap at four points");
        requireContains(editor,
                "throw failure(\n                    \"object.max_initial_spawns\",\n                    \"INITIAL player spawn limit is \" + MAX_INITIAL_PLAYER_SPAWNS);",
                "INITIAL add/duplicate should report a clear max-count failure");
        requireContains(editor,
                "case ZombiesDeployFieldSchema.POWER_SWITCH -> {\n                if (objects.powerSwitch().isPresent()) {\n                    throw failure(\"object.single_exists\", \"power_switch already exists; update or delete it first\");\n                }",
                "only power switch add path should enforce a single existing object");
        requireContains(editor,
                "case ZombiesDeployFieldSchema.POWER_SWITCH ->\n                    EditResult.failure(\"object.single_type\", \"power_switch cannot be duplicated\", objects, 0, fieldsForSelection(objects, type, 0));",
                "only power switch duplicate path should enforce single-object duplication");
        requireAbsent(editor,
                "case ZombiesDeployFieldSchema.ZOMBIE_SPAWN ->\n                    EditResult.failure(\"object.single_type\"",
                "zombie spawns must remain duplicable");
        requireAbsent(editor,
                "case ZombiesDeployFieldSchema.BARRIER ->\n                    EditResult.failure(\"object.single_type\"",
                "barriers must remain duplicable");

        System.out.println("PASS zombies deploy object editor static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireAbsent(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError(message + ": found `" + unexpected + "`");
        }
    }
}
