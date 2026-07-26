package com.cdp.codpattern.app.match.runtime.object;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModeObjectRuntimeCompatTest {
    private static final UUID ACTOR = UUID.fromString("54000000-0000-0000-0000-000000000001");

    private ModeObjectRuntimeCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        indexAndRevisionsPreserveIdentityAndOrder();
        deduplicationRemainsActorObjectAndTickScoped();
        dispatcherKeepsBusinessHandlersOutsideNeutralRuntime();
        targetResolverPreservesExactAndNearestSelection();
        neutralRuntimeContainsNoZombiesPayloadKnowledge();
    }

    private static void indexAndRevisionsPreserveIdentityAndOrder() {
        ModeObjectIndex<String> index = new ModeObjectIndex<>();
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("MixedCase-A", "a");
        replacements.put("object-b", "b");
        index.reset(replacements);
        require(index.objectIds().equals(List.of("MixedCase-A", "object-b")),
                "object index should preserve insertion order and public identity case");
        require(index.get(" MixedCase-A ").orElseThrow().equals("a"),
                "object lookup should preserve the existing trim-only normalization");

        ModeObjectRevisionClock clock = new ModeObjectRevisionClock();
        require(clock.next() == 1L && clock.next() == 2L && clock.current() == 2L,
                "revision clock should remain monotonic and positive");
        ModeObjectRevisionIndex revisions = new ModeObjectRevisionIndex();
        require(revisions.ensure("object-b") == 0L, "unseen current objects should retain revision zero");
        revisions.put("object-b", clock.next());
        require(revisions.ensure("object-b") == 3L, "marked object revisions should retain the shared clock value");
    }

    private static void deduplicationRemainsActorObjectAndTickScoped() {
        ModeObjectInteractionDeduplicator deduplicator = new ModeObjectInteractionDeduplicator(2L);
        require(deduplicator.tryAcquire(ACTOR, "box-a", 10L), "first interaction should be accepted");
        require(!deduplicator.tryAcquire(ACTOR, "box-a", 10L), "same actor/object/tick should be deduplicated");
        require(deduplicator.tryAcquire(ACTOR, "box-b", 10L), "different object should remain independent");
        require(deduplicator.tryAcquire(ACTOR, "box-a", 11L), "next tick should remain a distinct interaction");
        deduplicator.cleanup(14L);
        require(deduplicator.size() == 0, "expired interaction keys should be removed after the retention window");
    }

    private static void dispatcherKeepsBusinessHandlersOutsideNeutralRuntime() {
        ModeObjectInteractionDispatcher<String, Integer, String> dispatcher =
                new ModeObjectInteractionDispatcher<>();
        dispatcher.register("wall", value -> "wall:" + value);
        dispatcher.register("box", value -> "box:" + value);
        require(dispatcher.dispatch("wall", 4).orElseThrow().equals("wall:4"),
                "registered object types should reach their owning handler");
        require(dispatcher.dispatch("unknown", 4).isEmpty(), "unknown object types should not invent a fallback rule");
    }

    private static void targetResolverPreservesExactAndNearestSelection() {
        record Target(String id, int position, double distanceSquared, boolean eligible) {
        }
        List<Target> targets = List.of(
                new Target("far", 1, 12.0D, true),
                new Target("near-disabled", 2, 1.0D, false),
                new Target("near", 3, 4.0D, true));
        require(ModeObjectTargetResolver.exact(targets, Target::position, 1).orElseThrow().id().equals("far"),
                "clicked-position resolution should retain first exact match behavior");
        require(ModeObjectTargetResolver.nearestWithin(
                        targets, Target::eligible, Target::distanceSquared, 9.0D)
                .orElseThrow().id().equals("near"),
                "nearest resolution should honor eligibility and squared range");
        require(!ModeObjectTargetResolver.within(10.0D, 9.0D), "out-of-range targets should remain rejected");
    }

    private static void neutralRuntimeContainsNoZombiesPayloadKnowledge() throws Exception {
        Path directory = Path.of("src/main/java/com/cdp/codpattern/app/match/runtime/object");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                require(!source.contains("Zombies") && !source.contains("zombies"),
                        "neutral object runtime must not contain Zombies types or payload keys: " + file);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
