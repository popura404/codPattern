package com.cdp.codpattern.app.match.management;

import com.google.gson.*;
import java.util.List;
import java.util.stream.Stream;
import static com.cdp.codpattern.app.match.management.MapNameValidation.Status.*;

/** Real JSON serialization and shared input rules, without a Minecraft runtime. */
public final class MapRenameCompatTest {
    private static int checks;
    public static void main(String[] args) {
        JsonObject definition = JsonParser.parseString("{\"mapName\":\"教学关\",\"spawns\":[{}]}").getAsJsonObject();
        JsonObject spawn = definition.getAsJsonArray("spawns").get(0).getAsJsonObject();
        spawn.addProperty("yaw", 12.3f);
        spawn.addProperty("pitch", -9.7f);
        var persisted = JsonParser.parseString(new GsonBuilder().setPrettyPrinting().create().toJson(definition));
        require(!definition.equals(persisted), "fixture reproduces Float versus parsed-number mismatch");
        require(MapDefinitionComparison.compare(definition, persisted).matches(), "fractional angles and Chinese survive persistence");
        require(MapDefinitionComparison.compare(persisted, definition).matches(), "comparison is symmetric");
        require(compare("{\"n\":1}", "{\"n\":1.0}").matches(), "equivalent numeric notation");
        require(compare("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}").matches(), "object key order ignored");
        require(!compare("9007199254740992", "9007199254740993").matches(), "large integer differences remain exact");
        require(!compare("12.3", "12.300000000000001").matches(), "no numeric tolerance");
        require(!compare("[1,2]", "[2,1]").matches(), "array ordering matters");
        expect("{\"a\":null}", "{}", MapDefinitionComparison.Kind.MISSING, "$.a");
        expect("{}", "{\"a\":null}", MapDefinitionComparison.Kind.ADDED, "$.a");
        expect("{\"a\":1}", "{\"a\":\"1\"}", MapDefinitionComparison.Kind.TYPE_CHANGED, "$.a");
        expect("true", "\"true\"", MapDefinitionComparison.Kind.TYPE_CHANGED, "$");
        expect("[1]", "[1,2]", MapDefinitionComparison.Kind.ADDED, "$[1]");
        expect("[1,2]", "[1]", MapDefinitionComparison.Kind.MISSING, "$[1]");
        expect("{\"spawns\":[{\"yaw\":12.3}]}", "{\"spawns\":[{\"yaw\":12.4}]}",
                MapDefinitionComparison.Kind.VALUE_CHANGED, "$.spawns[0].yaw");
        expect("{\"a.b\":1}", "{\"a.b\":2}", MapDefinitionComparison.Kind.VALUE_CHANGED, "$[\"a.b\"]");
        JsonObject many = new JsonObject();
        for (int i = 0; i < 10; i++) many.addProperty("value" + i, "中".repeat(250));
        var ten = MapDefinitionComparison.compare(many, new JsonObject());
        require(ten.differences().size() == 10 && !ten.truncated(), "exactly ten differences need no count truncation");
        many.addProperty("extra", 1);
        var capped = MapDefinitionComparison.compare(many, new JsonObject());
        require(capped.differences().size() == 10 && capped.truncated(), "difference list is capped and marked");
        require(capped.differences().stream().allMatch(d -> d.expected().length() <= 200 && d.actual().length() <= 200), "value summaries bounded");
        require(capped.differences().stream().anyMatch(d -> d.expected().endsWith("...[truncated]")), "value truncation explicit");
        require(MapDefinitionComparison.compare(new JsonPrimitive("a\nb"), new JsonPrimitive("other"))
                .differences().get(0).expected().contains("\\n"), "diagnostic strings escape line breaks");

        status(" 教学关 ", VALID);
        require(MapNameValidation.validate(" 教学关 ").normalized().equals("教学关"), "trim shared by client/server");
        status(null, EMPTY); status(" \t ", EMPTY); status("\u2003", EMPTY);
        status("a".repeat(100), VALID); status("a".repeat(101), TOO_LONG);
        status("中".repeat(33) + "a", VALID); status("中".repeat(34), TOO_LONG);
        status("😀".repeat(25), VALID); status("😀".repeat(26), TOO_LONG);
        status("a\nb", INVALID_CHARACTERS); status("a\u0000b", INVALID_CHARACTERS);
        status("a\uD800b", INVALID_CHARACTERS); status("a\uDC00b", INVALID_CHARACTERS);
        require(MapNameValidation.rename(" old ", "old", Stream.of("old")).status() == UNCHANGED, "whitespace-only edit unchanged");
        require(MapNameValidation.rename("old", "old", Stream.of("old")).status() == UNCHANGED, "exact name unchanged");
        require(MapNameValidation.rename("OLD", "old", Stream.of("old")).status() == CONFLICT, "case-only rename conflicts");
        require(MapNameValidation.rename("教学关", "old", Stream.of("old", "教学关")).status() == CONFLICT, "known Chinese name conflicts");
        require(MapNameValidation.rename("新地图", "old", Stream.of("old", "教学关")).valid(), "new Chinese name allowed");
        record Name(String mode, String name) { }
        var names = List.of(new Name("frontline", "old"), new Name("zombies", "教学关"));
        require(MapNameValidation.rename("教学关", "old", names.stream().filter(n -> n.mode().equals("frontline"))
                .map(Name::name)).valid(), "other modes do not reserve a name");
        System.out.println("PASS map rename compatibility (" + checks + " assertions)");
    }
    private static MapDefinitionComparison.Result compare(String left, String right) {
        return MapDefinitionComparison.compare(JsonParser.parseString(left), JsonParser.parseString(right));
    }
    private static void expect(String left, String right, MapDefinitionComparison.Kind kind, String path) {
        var result = compare(left, right);
        require(!result.matches() && result.differences().get(0).kind() == kind
                && result.differences().get(0).path().equals(path), "diagnostic " + kind + " at " + path);
    }
    private static void status(String name, MapNameValidation.Status expected) {
        require(MapNameValidation.validate(name).status() == expected, "name status " + expected);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
