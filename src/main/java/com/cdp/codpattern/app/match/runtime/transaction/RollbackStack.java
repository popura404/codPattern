package com.cdp.codpattern.app.match.runtime.transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** LIFO compensation stack that records every rollback outcome. */
public final class RollbackStack<C, R> {
    private final Deque<Action<C, R>> actions = new ArrayDeque<>();

    public void push(Action<C, R> action) {
        if (action != null) {
            actions.push(action);
        }
    }

    public int size() {
        return actions.size();
    }

    public Report<R> rollback(
            C context,
            Predicate<R> successPolicy,
            Function<RuntimeException, R> exceptionPolicy
    ) {
        Objects.requireNonNull(successPolicy, "successPolicy");
        Objects.requireNonNull(exceptionPolicy, "exceptionPolicy");
        List<Step<R>> steps = new ArrayList<>();
        while (!actions.isEmpty()) {
            Action<C, R> action = actions.pop();
            R result;
            boolean success;
            try {
                result = action.rollback(context);
                success = successPolicy.test(result);
            } catch (RuntimeException exception) {
                result = exceptionPolicy.apply(exception);
                success = successPolicy.test(result);
            }
            steps.add(new Step<>(action.name(), success, result));
        }
        return new Report<>(steps);
    }

    public interface Action<C, R> {
        String name();

        R rollback(C context);
    }

    public record Step<R>(String actionName, boolean success, R result) {
        public Step {
            actionName = Objects.requireNonNullElse(actionName, "").trim();
        }
    }

    public record Report<R>(List<Step<R>> steps) {
        public Report {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public boolean success() {
            return steps.stream().allMatch(Step::success);
        }
    }
}
