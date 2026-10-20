package com.cdp.codpattern.compat.arcana;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a deliberately small, pure subset of Arcana's loaded form rules. No skill, condition or action
 * is executed. The only possible mutation is the root GunId, after the whole component is validated.
 * Complex actions remain subject to the existing preview skill gates.
 */
public final class ArcanaGunFormRules {
    public enum Status { NOT_LOADED, UNAVAILABLE, UNCHANGED, CHANGED, UNSUPPORTED }

    public record Result(Status status, String originalGunId, String resultGunId,
                         List<String> ruleNames, String reason) {
        public Result { ruleNames = List.copyOf(ruleNames); }
        public boolean isResolved() { return status == Status.UNCHANGED || status == Status.CHANGED; }
        public boolean isSupportedComponent() { return isResolved() && !ruleNames.isEmpty(); }
    }

    /** Lists the entire connected form component, including why any member makes it unsupported. */
    public record ComponentInfo(Set<String> gunIds, List<String> ruleNames, List<String> unsupportedReasons) {
        public ComponentInfo {
            gunIds = Set.copyOf(gunIds);
            ruleNames = List.copyOf(ruleNames);
            unsupportedReasons = List.copyOf(unsupportedReasons);
        }
        public boolean supported() { return unsupportedReasons.isEmpty(); }
    }

    /** UNCHANGED means a current server catalog was read; it does not claim every component is supported. */
    public record Catalog(Status status, List<ComponentInfo> components, String reason) {
        public Catalog { components = List.copyOf(components); }
        public ComponentInfo component(String gunId) {
            return components.stream().filter(c -> c.gunIds().contains(gunId)).findFirst().orElse(null);
        }
    }

    private ArcanaGunFormRules() { }

    public static Catalog catalog() { return snapshot().catalog; }

    /** Stable candidate catalogs can include every legal form without renumbering slots after a change. */
    public static Set<String> relatedGunIds(ItemStack gun) {
        String current = rootId(gun);
        Snapshot snapshot = snapshot();
        Catalog catalog = snapshot.catalog;
        ComponentInfo component = catalog.component(current);
        return catalog.status() == Status.UNCHANGED && component != null && component.supported()
                && sourceProblem(snapshot, gun.copy(), current) == null
                ? component.gunIds() : current.isEmpty() ? Set.of() : Set.of(current);
    }

    public static Result inspect(ItemStack gun) {
        String original = rootId(gun);
        Snapshot snapshot = snapshot();
        if (snapshot.catalog.status() != Status.UNCHANGED) {
            return result(snapshot.catalog.status(), original, original, List.of(), snapshot.catalog.reason());
        }
        if (IGun.getIGunOrNull(gun) == null || validGun(original) == null) {
            return result(Status.UNSUPPORTED, original, original, List.of(), "Invalid root gun identity");
        }
        ItemStack working = gun.copy();
        Result sourceProblem = sourceProblem(snapshot, working, original);
        if (sourceProblem != null) return sourceProblem;
        ComponentInfo component = snapshot.catalog.component(original);
        if (component == null) return result(Status.UNCHANGED, original, original, List.of(), "No form rule applies");
        if (!component.supported()) {
            return result(Status.UNSUPPORTED, original, original, component.ruleNames(), String.join("; ", component.unsupportedReasons()));
        }

        // All evaluation happens on a copy. A cycle or ambiguity must never leave a partially changed gun.
        Set<String> visited = new HashSet<>();
        List<String> applied = new ArrayList<>();
        String current = original;
        while (visited.add(current)) {
            sourceProblem = sourceProblem(snapshot, working, current);
            if (sourceProblem != null) return result(sourceProblem.status(), original, original, applied, sourceProblem.reason());
            Set<String> attachments = attachmentIds(working);
            String next = current;
            List<String> matched = new ArrayList<>();
            for (Rule rule : snapshot.rules) {
                if (!rule.sources.contains(current) || !rule.matches(attachments)) continue;
                if (!next.equals(current) && !next.equals(rule.target)) {
                    return result(Status.UNSUPPORTED, original, original, applied, "Conflicting form targets for " + current);
                }
                next = rule.target;
                matched.add(rule.name);
            }
            if (next.equals(current)) {
                return result(current.equals(original) ? Status.UNCHANGED : Status.CHANGED,
                        original, current, applied.isEmpty() ? component.ruleNames() : applied, "Validated static form rules");
            }
            if (!component.gunIds().contains(next) || validGun(next) == null) {
                return result(Status.UNSUPPORTED, original, original, applied, "Target left the validated form component");
            }
            applied.addAll(matched);
            current = next;
            working.getOrCreateTag().putString(GunItemDataAccessor.GUN_ID_TAG, current);
        }
        return result(Status.UNSUPPORTED, original, original, applied, "Form rules cycle for the installed attachments");
    }

    /** Must be called on the server thread. Client/unavailable/unsupported results never mutate the item. */
    public static Result normalize(ItemStack gun) {
        Result result = inspect(gun);
        if (result.status() == Status.CHANGED) {
            gun.getOrCreateTag().putString(GunItemDataAccessor.GUN_ID_TAG, result.resultGunId());
        }
        return result;
    }

    private static Result result(Status status, String original, String derived, List<String> rules, String reason) {
        return new Result(status, original, derived, rules, reason);
    }

    private static String rootId(ItemStack gun) {
        return gun != null && gun.hasTag() ? gun.getTag().getString(GunItemDataAccessor.GUN_ID_TAG) : "";
    }

    private static ResourceLocation validId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed != null && parsed.toString().equals(id) ? parsed : null;
    }

    private static ResourceLocation validGun(String id) {
        ResourceLocation parsed = validId(id);
        return parsed != null && TimelessAPI.getCommonGunIndex(parsed).isPresent() ? parsed : null;
    }

    private static Set<String> attachmentIds(ItemStack gun) {
        IGun api = IGun.getIGunOrNull(gun);
        Set<String> ids = new HashSet<>();
        // Arcana HasAttachment includes built-ins, even when a slot is not user configurable.
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            for (ResourceLocation id : new ResourceLocation[]{api.getAttachmentId(gun, type), api.getBuiltInAttachmentId(gun, type)}) {
                if (id != null && !DefaultAssets.isEmptyAttachmentId(id)) ids.add(id.toString());
            }
        }
        return ids;
    }

    private record Rule(String name, Set<String> sources, String target, List<Set<String>> conditions, boolean allFalse) {
        boolean matches(Set<String> installed) {
            for (Set<String> condition : conditions) {
                boolean matched = condition.isEmpty() || condition.stream().anyMatch(installed::contains);
                if (matched == allFalse) return false;
            }
            return true;
        }
    }

    private record Mention(String name, Set<String> nodes, Rule rule, String rejection) { }
    private record SourceCheck(Object skill, String name, Set<String> explicitGuns) { }
    private record Snapshot(Catalog catalog, List<Rule> rules, List<SourceCheck> sources) { }

    /** Arcana sources are ORed. Extra lore/attachment/pack bindings must not masquerade as no-rule success. */
    private static Result sourceProblem(Snapshot snapshot, ItemStack working, String current) {
        try {
            for (SourceCheck source : snapshot.sources) {
                if (source.explicitGuns.contains(current)) continue;
                Class<?> type = source.skill.getClass();
                for (String[] names : new String[][]{{"LCErDI92", "checkMatchConfig"}, {"fDo0fKxC", "checkMatchGun"},
                        {"VPvopG09", "checkMatchAttachment"}, {"wcz9mNd9", "checkMatchLore"}}) {
                    Method match;
                    try { match = type.getMethod(names[0], ItemStack.class); }
                    catch (NoSuchMethodException absent) { match = type.getMethod(names[1], ItemStack.class); }
                    if ((boolean) match.invoke(source.skill, working)) {
                        return result(Status.UNSUPPORTED, current, current, List.of(source.name),
                                "GunId skill matches through a source outside its explicit Guns: " + source.name);
                    }
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return result(Status.UNAVAILABLE, current, current, List.of(), "Cannot verify Arcana skill sources: " + failure.getClass().getSimpleName());
        }
    }

    private static Snapshot unavailable(Status status, String reason) {
        return new Snapshot(new Catalog(status, List.of(), reason), List.of(), List.of());
    }

    private static Snapshot snapshot() {
        if (!ArcanaRefitCompat.isLoaded()) return unavailable(Status.NOT_LOADED, "Arcana is not installed");
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return unavailable(Status.UNAVAILABLE, "Server form rules are unavailable on this thread");
        }
        try {
            Binding api = Binding.INSTANCE;
            Map<?, ?> loaded = (Map<?, ?>) api.loaded.invoke(api.manager);
            if (server.getTickCount() == 0 && loaded.values().stream().allMatch(value -> value instanceof List<?> list && list.isEmpty())) {
                return unavailable(Status.UNAVAILABLE, "Arcana server skills have not been populated");
            }
            List<?> skills = loaded.entrySet().stream()
                    .filter(e -> e.getKey() instanceof Enum<?> value && value.name().equals("ON_CHANGE_ATTACHMENT"))
                    .map(e -> (List<?>) e.getValue()).findFirst().orElse(null);
            if (skills == null) return unavailable(Status.UNAVAILABLE, "Arcana attachment trigger is unavailable");
            Map<?, ?> chains = (Map<?, ?>) api.chains.invoke(api.manager);
            List<Mention> mentions = new ArrayList<>();
            List<SourceCheck> sourceChecks = new ArrayList<>();
            List<String> globalIssues = new ArrayList<>();
            for (Object skill : List.copyOf(skills)) {
                Object config = method(skill.getClass(), "D6t3W57i", "getConfig").invoke(skill);
                JsonObject json = api.json(config);
                Targets targets = new Targets();
                scanActions(json.get("Actions"), targets, chains, api, new HashSet<>(), 0);
                scanActions(json.get("ReverseActions"), targets, chains, api, new HashSet<>(), 0);
                if (!targets.possibleFormChange) continue;
                String name = string(json, "Name");
                Set<String> sources = sourceIds(json.get("Guns"));
                sourceChecks.add(new SourceCheck(skill, name, Set.copyOf(sources)));
                if (bool(object(json.get("Flags")), "UseRegex") && json.get("Guns").isJsonArray()) {
                    for (JsonElement pattern : json.getAsJsonArray("Guns")) {
                        java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(pattern.getAsString());
                        TimelessAPI.getAllCommonGunIndex().stream().map(entry -> entry.getKey().toString())
                                .filter(id -> compiled.matcher(id).matches()).forEach(sources::add);
                    }
                }
                Set<String> nodes = new LinkedHashSet<>(sources);
                nodes.addAll(targets.ids);
                Rule rule = null;
                String rejection = null;
                try { rule = parseRule(json); }
                catch (IllegalArgumentException failure) { rejection = name + ": " + failure.getMessage(); }
                if (targets.uncertain && rejection == null) rejection = name + ": Unresolved form action";
                if (nodes.isEmpty()) globalIssues.add(name + ": Form action has no bounded gun identities");
                else mentions.add(new Mention(name, nodes, rule, rejection));
            }
            return components(mentions, globalIssues, sourceChecks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return unavailable(Status.UNAVAILABLE, "Cannot read Arcana static form rules: " + failure.getClass().getSimpleName());
        }
    }

    private static Snapshot components(List<Mention> mentions, List<String> globalIssues, List<SourceCheck> sourceChecks) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (Mention mention : mentions) for (String id : mention.nodes) {
            graph.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).addAll(mention.nodes);
        }
        List<ComponentInfo> components = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String start : graph.keySet()) {
            if (visited.contains(start)) continue;
            Set<String> nodes = new LinkedHashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                String node = pending.remove();
                if (!nodes.add(node)) continue;
                pending.addAll(graph.getOrDefault(node, Set.of()));
            }
            visited.addAll(nodes);
            Set<String> names = new LinkedHashSet<>();
            // Unbounded sources are checked against the actual item by sourceProblem, not assigned
            // to unrelated components merely because another gun pack contains such a skill.
            Set<String> issues = new LinkedHashSet<>();
            List<Rule> local = new ArrayList<>();
            for (Mention mention : mentions) {
                if (mention.nodes.stream().noneMatch(nodes::contains)) continue;
                names.add(mention.name);
                if (mention.rejection != null) issues.add(mention.rejection);
                else if (mention.rule != null) local.add(mention.rule);
            }
            for (String node : nodes) {
                if (validGun(node) == null) issues.add("Missing gun index: " + node);
                if (local.stream().noneMatch(rule -> rule.sources.contains(node))) {
                    issues.add("No supported return rule from " + node);
                }
            }
            // A forward-only recognisable rule must not silently support half of a transformation.
            for (String node : nodes) if (!reachable(node, nodes, local)) issues.add("Incomplete reversible component at " + node);
            components.add(new ComponentInfo(nodes, new ArrayList<>(names), new ArrayList<>(issues)));
            if (issues.isEmpty()) rules.addAll(local);
        }
        String reason = globalIssues.isEmpty() ? "Current Arcana server rule catalog"
                : "Current catalog; unbounded sources require per-item checks: " + String.join("; ", globalIssues);
        return new Snapshot(new Catalog(Status.UNCHANGED, components, reason), rules, sourceChecks);
    }

    private static boolean reachable(String start, Set<String> expected, List<Rule> rules) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.remove();
            if (!seen.add(id)) continue;
            for (Rule rule : rules) if (rule.sources.contains(id)) queue.add(rule.target);
        }
        return seen.containsAll(expected);
    }

    private static Rule parseRule(JsonObject json) {
        keys(json, "Name", "Lore", "Guns", "Attachments", "Trigger", "Flags", "Conditions", "Actions", "ReverseActions");
        require(string(json, "Trigger").equals("ON_CHANGE_ATTACHMENT"), "Wrong trigger");
        Set<String> sources = ids(array(json.get("Guns")), false);
        require(!sources.isEmpty(), "Explicit gun sources are required");
        require(empty(json.get("Attachments")), "Attachment-based skill source is unsupported");
        require(empty(json.get("ReverseActions")), "Extra reverse actions are unsupported");
        JsonObject flags = object(json.get("Flags"));
        keys(flags, "TriggerProbability", "CooldownGroup", "Cooldown", "KeepCheckTimes", "BypassFirstConditionCheck",
                "Frequency", "RepeatTimes", "RepeatDelay", "ConditionExpression", "ReverseTriggerConditions", "UseRegex", "StrictSourceMode");
        require(number(flags, "TriggerProbability") == 100, "Probabilistic conversion is unsupported");
        require(nil(flags.get("CooldownGroup")), "Cooldown groups are unsupported");
        for (String key : List.of("Cooldown", "KeepCheckTimes", "Frequency", "RepeatTimes", "RepeatDelay")) {
            require(number(flags, key) == 0, key + " is unsupported");
        }
        require(!bool(flags, "BypassFirstConditionCheck") && !bool(flags, "UseRegex") && bool(flags, "StrictSourceMode"), "Dynamic source/condition flags are unsupported");
        String expression = string(flags, "ConditionExpression");
        require(expression.equals("ALL_TRUE") || expression.equals("ALL_FALSE"), "Condition expression is unsupported");
        JsonArray reverse = array(flags.get("ReverseTriggerConditions"));
        require(reverse.size() == 1 && reverse.get(0).getAsString().equals("NEED_TRIGGER_ONCE"), "Reverse trigger flags are unsupported");
        List<Set<String>> conditions = new ArrayList<>();
        for (JsonElement element : array(json.get("Conditions"))) {
            JsonObject condition = object(element);
            keys(condition, "__cdp_rule_type", "attachments", "useRegex");
            require(string(condition, "__cdp_rule_type").equals("HasAttachment") && !bool(condition, "useRegex"), "Only exact HasAttachment conditions are supported");
            conditions.add(ids(array(condition.get("attachments")), true));
        }
        require(!conditions.isEmpty(), "An attachment condition is required");
        JsonArray actions = array(json.get("Actions"));
        require(actions.size() == 1, "Exactly one action is required; mixed actions cannot be partially applied");
        JsonObject action = object(actions.get(0));
        keys(action, "__cdp_rule_type", "action", "path", "pathIsExpression", "index", "data", "dataIsExpression", "unsafeModifySelf", "selector", "delay");
        require(string(action, "__cdp_rule_type").equals("NBTEdit") && string(action, "action").equals("set") && string(action, "path").equals("GunId"), "Only SET root GunId is supported");
        require(!bool(action, "pathIsExpression") && !bool(action, "dataIsExpression") && !bool(action, "unsafeModifySelf"), "Dynamic or entity NBT edits are unsupported");
        require(number(action, "index") == 0 && nil(action.get("selector")) && nil(action.get("delay")), "Selector, delayed or indexed edits are unsupported");
        String target = target(string(action, "data"));
        require(target != null && validGun(target) != null, "Invalid target gun identity");
        return new Rule(string(json, "Name"), sources, target, conditions, expression.equals("ALL_FALSE"));
    }

    private static final class Targets {
        final Set<String> ids = new LinkedHashSet<>();
        boolean possibleFormChange;
        boolean uncertain;
    }

    /** Discovery is broader than acceptance, so a mixed/branched reverse conversion blocks its whole component. */
    private static void scanActions(JsonElement element, Targets targets, Map<?, ?> chains, Binding api,
                                    Set<String> chainPath, int depth) throws ReflectiveOperationException {
        if (nil(element)) return;
        if (depth > 64) { targets.possibleFormChange = true; targets.uncertain = true; return; }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) scanActions(child, targets, chains, api, chainPath, depth + 1);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        String type = object.has("__cdp_rule_type") && object.get("__cdp_rule_type").isJsonPrimitive() ? object.get("__cdp_rule_type").getAsString() : "";
        if (type.equals("NBTEdit")) {
            String path = object.has("path") ? string(object, "path") : "";
            if (bool(object, "pathIsExpression") || path.contains("GunId") || path.equals("{}") || path.isBlank()) {
                targets.possibleFormChange = true;
                String target = !bool(object, "dataIsExpression") && path.equals("GunId") ? target(string(object, "data")) : null;
                if (target == null) targets.uncertain = true;
                else targets.ids.add(target);
            }
        }
        if ((type.equals("Chain") || object.has("actions") && object.has("repeat")) && !nil(object.get("from"))) {
            String name = string(object, "from");
            if (!chainPath.add(name) || !chains.containsKey(name)) {
                targets.possibleFormChange = true;
                targets.uncertain = true;
            } else {
                scanActions(api.json(chains.get(name)).get("actions"), targets, chains, api, chainPath, depth + 1);
                chainPath.remove(name);
            }
        }
        for (JsonElement child : object.asMap().values()) {
            if (child.isJsonArray() || child.isJsonObject()) scanActions(child, targets, chains, api, chainPath, depth + 1);
        }
    }

    private static Set<String> sourceIds(JsonElement value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null && value.isJsonArray()) for (JsonElement id : value.getAsJsonArray()) {
            if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isString() && validId(id.getAsString()) != null) result.add(id.getAsString());
        }
        return result;
    }

    private static Set<String> ids(JsonArray values, boolean attachments) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : values) {
            require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "Identity must be a string");
            String text = value.getAsString();
            ResourceLocation id = validId(text);
            require(id != null, "Identity is not an exact namespaced ID");
            require(!attachments || TimelessAPI.getCommonAttachmentIndex(id).isPresent(), "Missing attachment index: " + text);
            result.add(text);
        }
        return result;
    }

    private static String target(String snbt) {
        try {
            CompoundTag value = TagParser.parseTag("{value:" + snbt + "}");
            if (value.size() != 1 || !value.contains("value", Tag.TAG_STRING)) return null;
            String id = value.getString("value");
            return validId(id) == null ? null : id;
        } catch (Exception ignored) { return null; }
    }

    private static void keys(JsonObject value, String... keys) {
        require(value.keySet().equals(Set.of(keys)), "Unrecognised config schema: " + value.keySet());
    }
    private static void require(boolean test, String reason) { if (!test) throw new IllegalArgumentException(reason); }
    private static boolean nil(JsonElement value) { return value == null || value.isJsonNull(); }
    private static boolean empty(JsonElement value) { return nil(value) || value.isJsonArray() && value.getAsJsonArray().isEmpty(); }
    private static JsonObject object(JsonElement value) { require(value != null && value.isJsonObject(), "Expected object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value) { require(value != null && value.isJsonArray(), "Expected array"); return value.getAsJsonArray(); }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "Expected string " + key);
        return value.getAsString();
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(), "Expected boolean " + key);
        return value.getAsBoolean();
    }
    private static int number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && value.getAsString().matches("-?[0-9]+"), "Expected integer " + key);
        return value.getAsInt();
    }

    private static Method method(Class<?> type, String shipped, String source) throws NoSuchMethodException {
        try { return type.getMethod(shipped); }
        catch (NoSuchMethodException absent) { return type.getMethod(source); }
    }
    private static Class<?> optionalClass(String shipped, String source) throws ClassNotFoundException {
        try { return Class.forName(shipped); }
        catch (ClassNotFoundException absent) { return Class.forName(source); }
    }
    private static Object singleton(Class<?> type, String shipped, String source) throws ReflectiveOperationException {
        try { return type.getField(shipped).get(null); }
        catch (NoSuchFieldException absent) { return type.getField(source).get(null); }
    }

    /** Optional classes are first resolved only after the mod/server/thread availability checks above. */
    private static final class Binding {
        static final Binding INSTANCE = create();
        final Object manager;
        final Method loaded;
        final Method chains;
        final Object json;
        final Class<?> serializerType;
        final Method encode;
        final Map<Class<?>, Object> serializers = new HashMap<>();

        Binding() throws ReflectiveOperationException {
            Class<?> managerType = optionalClass("group.taczexpands.dist.Gcj8PhOz", "group.taczexpands.server.skill.SkillManager");
            manager = singleton(managerType, "cLHuUMKr", "INSTANCE");
            loaded = method(managerType, "WAX43K6K", "getLOADED_SKILLS");
            chains = method(managerType, "P0Pn0vh5", "getLOADED_CHAIN_ACTIONS");
            Class<?> extensions = optionalClass("group.taczexpands.dist.IeZYUpP2", "group.taczexpands.server.util.ExtensionsKt");
            Object yaml = method(extensions, "ypMzAw12", "getYAML").invoke(null);
            Object module = yaml.getClass().getMethod("getSerializersModule").invoke(yaml);
            Class<?> moduleType = Class.forName("kotlinx.serialization.modules.SerializersModule");
            Class<?> jsonType = Class.forName("kotlinx.serialization.json.Json");
            Class<?> functionType = Class.forName("kotlin.jvm.functions.Function1");
            Object configure = Proxy.newProxyInstance(functionType.getClassLoader(), new Class<?>[]{functionType}, (proxy, called, args) -> {
                if (!called.getName().equals("invoke")) throw new UnsupportedOperationException(called.toString());
                Object builder = args[0];
                builder.getClass().getMethod("setEncodeDefaults", boolean.class).invoke(builder, true);
                builder.getClass().getMethod("setClassDiscriminator", String.class).invoke(builder, "__cdp_rule_type");
                builder.getClass().getMethod("setSerializersModule", moduleType).invoke(builder, module);
                return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
            });
            json = Class.forName("kotlinx.serialization.json.JsonKt").getMethod("Json", jsonType, functionType)
                    .invoke(null, jsonType.getField("Default").get(null), configure);
            serializerType = Class.forName("kotlinx.serialization.KSerializer");
            encode = jsonType.getMethod("encodeToString", Class.forName("kotlinx.serialization.SerializationStrategy"), Object.class);
        }

        static Binding create() {
            try { return new Binding(); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Arcana rule serialization API", failure); }
        }

        JsonObject json(Object value) throws ReflectiveOperationException {
            Object serializer = serializers.get(value.getClass());
            if (serializer == null) {
                List<Object> found = new ArrayList<>();
                for (Field field : value.getClass().getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    for (Method method : field.getType().getMethods()) {
                        if (method.getParameterCount() == 0 && method.getReturnType() == serializerType) {
                            found.add(method.invoke(field.get(null)));
                        }
                    }
                }
                if (found.size() != 1) throw new NoSuchMethodException("Expected one generated serializer for " + value.getClass().getName());
                serializer = found.get(0);
                serializers.put(value.getClass(), serializer);
            }
            return object(JsonParser.parseString((String) encode.invoke(json, serializer, value)));
        }
    }
}
