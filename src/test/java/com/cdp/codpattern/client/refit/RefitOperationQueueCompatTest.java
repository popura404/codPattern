package com.cdp.codpattern.client.refit;

import java.util.List;
import java.util.UUID;

/** Runs without Minecraft; checks ordering guarantees relied on by the actual client state. */
public final class RefitOperationQueueCompatTest {
    private RefitOperationQueueCompatTest() {
    }

    public static void main(String[] args) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RefitOperationQueue<String> queue = new RefitOperationQueue<>();
        require(queue.begin(first), "begin first session");
        require(queue.enqueue("scope A") && queue.enqueue("scope B") && queue.enqueue("muzzle"),
                "rapid selections are accepted in UI order");
        require("scope A".equals(queue.next()) && queue.next() == null,
                "only the first operation is in flight");
        require(!queue.acknowledge(second) && queue.next() == null,
                "a stale session's result cannot advance the current queue");
        require(queue.close(List.of("laser", "save")) && !queue.enqueue("late click"),
                "closing retains accepted edits and seals the queue");
        require(!queue.begin(second) && !queue.close(List.of("duplicate save")),
                "reopening and duplicate close cannot discard an outstanding save");
        for (String next : List.of("scope B", "muzzle", "laser", "save")) {
            require(queue.acknowledge(first) && next.equals(queue.next()) && queue.next() == null,
                    "ACK alone advances the next command: " + next);
        }
        require(!queue.finish(second) && !queue.begin(second),
                "an old completion cannot reset the live save");
        require(queue.finish(first) && queue.next() == null && queue.begin(second),
                "the matching terminal result releases the session for reopening");

        require(queue.enqueue("scope C") && "scope C".equals(queue.next()) && queue.enqueue("unsent scope D"),
                "a new session can start after completed save");
        require(queue.cancel("cancel") && queue.next() == null,
                "cancel waits for the in-flight operation");
        require(queue.acknowledge(second) && "cancel".equals(queue.next()),
                "cancel replaces unsent edits but follows the actual in-flight result");
        require(queue.acknowledge(second) && queue.next() == null && !queue.enqueue("edit after cancel"),
                "cancellation leaves no queued edit or accidental save");
        require(queue.finish(second), "cancellation terminal result ends its session");

        require(queue.begin(first), "start large click queue");
        for (int i = 0; i < 2048; i++) {
            require(queue.enqueue("candidate " + i), "large queue accepts entry " + i);
        }
        for (int i = 0; i < 2048; i++) {
            require(("candidate " + i).equals(queue.next()) && queue.acknowledge(first),
                    "large queue retains entry " + i);
        }
        require(queue.next() == null, "large queue has exactly its accepted entries");
        queue.reset();
        require(!queue.acknowledge(first) && queue.next() == null && !queue.enqueue("disconnected"),
                "disconnect drops all pending session state");
        System.out.println("PASS attachment refit operation queue compatibility");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
