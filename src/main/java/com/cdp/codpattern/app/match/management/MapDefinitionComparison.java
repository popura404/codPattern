package com.cdp.codpattern.app.match.management;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Compares serialized definitions without dropping fields or tolerating value changes. */
public final class MapDefinitionComparison {
    private static final int MAX_DIFFERENCES = 10;
    private static final int MAX_VALUE_LENGTH = 200;

    public enum Kind { MISSING, ADDED, TYPE_CHANGED, VALUE_CHANGED }
    public record Difference(String path, Kind kind, String expected, String actual) { }
    public record Result(List<Difference> differences, boolean truncated) {
        public Result { differences = List.copyOf(differences); }
        public boolean matches() { return differences.isEmpty(); }
    }

    private MapDefinitionComparison() { }

    public static Result compare(JsonElement expected, JsonElement actual) {
        var differences = new ArrayList<Difference>();
        visit("$", JsonParser.parseString(expected.toString()), JsonParser.parseString(actual.toString()), differences);
        boolean truncated = differences.size() > MAX_DIFFERENCES;
        return new Result(differences.subList(0, Math.min(differences.size(), MAX_DIFFERENCES)), truncated);
    }

    private static void visit(String path, JsonElement expected, JsonElement actual, List<Difference> differences) {
        if (differences.size() > MAX_DIFFERENCES) return;
        if (expected == null) { add(path, Kind.ADDED, null, actual, differences); return; }
        if (actual == null) { add(path, Kind.MISSING, expected, null, differences); return; }
        if (!type(expected).equals(type(actual))) {
            add(path, Kind.TYPE_CHANGED, expected, actual, differences);
        } else if (expected.isJsonObject()) {
            var keys = new TreeSet<>(expected.getAsJsonObject().keySet());
            keys.addAll(actual.getAsJsonObject().keySet());
            for (String key : keys) {
                String segment = key.matches("[A-Za-z_][A-Za-z0-9_]*") ? "." + key : "[" + new JsonPrimitive(key) + "]";
                visit(path + segment, expected.getAsJsonObject().get(key), actual.getAsJsonObject().get(key), differences);
                if (differences.size() > MAX_DIFFERENCES) break;
            }
        } else if (expected.isJsonArray()) {
            var left = expected.getAsJsonArray();
            var right = actual.getAsJsonArray();
            for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
                visit(path + "[" + i + "]", i < left.size() ? left.get(i) : null,
                        i < right.size() ? right.get(i) : null, differences);
                if (differences.size() > MAX_DIFFERENCES) break;
            }
        } else {
            // Gson numeric equals converts to double and can hide changes to large integers.
            boolean equal = type(expected).equals("number")
                    ? expected.getAsBigDecimal().compareTo(actual.getAsBigDecimal()) == 0 : expected.equals(actual);
            if (!equal) add(path, Kind.VALUE_CHANGED, expected, actual, differences);
        }
    }

    private static String type(JsonElement value) {
        if (value.isJsonNull()) return "null";
        if (value.isJsonObject()) return "object";
        if (value.isJsonArray()) return "array";
        if (value.getAsJsonPrimitive().isNumber()) return "number";
        if (value.getAsJsonPrimitive().isBoolean()) return "boolean";
        return "string";
    }

    private static void add(String path, Kind kind, JsonElement expected, JsonElement actual, List<Difference> differences) {
        differences.add(new Difference(path, kind, summary(expected), summary(actual)));
    }

    private static String summary(JsonElement value) {
        String text = value == null ? "<missing>" : value.toString();
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH - 14) + "...[truncated]";
    }
}
