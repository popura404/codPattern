package com.cdp.codpattern.app.zombies.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

final class ZombiesCompatSuiteRunner {
    private ZombiesCompatSuiteRunner() {
    }

    static int runAll(List<TestEntry> tests, String[] args) throws Throwable {
        int skipped = 0;
        for (TestEntry test : tests) {
            if (run(test, args).skipped()) {
                skipped++;
            }
        }
        return skipped;
    }

    static RunResult run(TestEntry test, String[] args) throws Throwable {
        try {
            Class<?> testClass = Class.forName(test.className());
            Method main = testClass.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
            System.out.println("PASS " + test.displayName());
            return RunResult.PASSED;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (shouldSkip(test, cause)) {
                printSkip(test, cause);
                return RunResult.SKIPPED;
            }
            throw cause;
        } catch (Throwable throwable) {
            if (shouldSkip(test, throwable)) {
                printSkip(test, throwable);
                return RunResult.SKIPPED;
            }
            throw throwable;
        }
    }

    static boolean isMissingMinecraftRuntime(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null
                        && (message.contains("net/minecraft")
                        || message.contains("net.minecraft")
                        || message.contains("net/minecraftforge")
                        || message.contains("net.minecraftforge")
                        || message.contains("com/mojang")
                        || message.contains("com.mojang")
                        || message.contains("it/unimi/dsi/fastutil")
                        || message.contains("it.unimi.dsi.fastutil"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldSkip(TestEntry test, Throwable throwable) {
        return test.allowMissingMinecraftRuntime() && isMissingMinecraftRuntime(throwable);
    }

    private static void printSkip(TestEntry test, Throwable throwable) {
        System.err.println("SKIP " + test.displayName()
                + ": missing Minecraft/Forge runtime classpath ("
                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
                + "). Run under Forge/GameTest or a full Gradle runtime classpath for full coverage.");
    }

    record TestEntry(String displayName, String className, boolean allowMissingMinecraftRuntime) {
    }

    enum RunResult {
        PASSED,
        SKIPPED;

        boolean skipped() {
            return this == SKIPPED;
        }
    }
}
