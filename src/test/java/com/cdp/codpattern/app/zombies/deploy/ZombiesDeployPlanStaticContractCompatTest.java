package com.cdp.codpattern.app.zombies.deploy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ZombiesDeployPlanStaticContractCompatTest {
    private static final Path PLAN = Path.of("docs/ZOMBIES_DEPLOY_GUI_CORRECTION_PLAN.md");

    private static final List<String> VALIDATION_CASES = List.of(
            "V-01", "V-02", "V-03", "V-04", "V-05", "V-06",
            "V-07", "V-08", "V-09", "V-10", "V-11", "V-12");
    private static final List<String> DEPLOY_CASES = List.of(
            "D-01", "D-02", "D-03", "D-04", "D-05", "D-06", "D-07", "D-08");
    private static final List<String> MANUAL_CASES = List.of(
            "G-01", "G-02", "G-03", "G-04", "G-05", "G-06", "G-07", "G-08", "G-09", "G-10", "G-11", "G-12", "G-13", "G-14",
            "L-01", "L-02", "L-03", "L-04", "L-05", "L-06",
            "T-UI-01", "T-UI-02", "T-UI-03", "T-UI-04",
            "I-01", "I-02", "I-03", "I-04", "I-05", "I-06", "I-07",
            "P-01", "P-02", "P-03", "P-04",
            "E-01", "E-02", "E-03", "E-04", "E-05");

    private ZombiesDeployPlanStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String plan = read(PLAN);
        requireContains(plan, "| 版本 | v3.10 |", "plan version should reflect the latest audit");
        requireContains(plan, "## 16. 完成定义（DoD）", "plan must keep explicit DoD section");
        requireContains(plan, "### 15.7 手测留档模板（必须填写）", "plan must keep the manual evidence template");
        requireContains(plan, "总目标尚不能标记 complete", "plan must not claim completion while manual gates remain open");
        requireContains(plan, "最终 DoD 还不能关闭", "plan must explicitly keep the final DoD open");
        requireContains(plan, "`./gradlew runZombiesMvp123CompatSuite` 通过", "plan must record JVM compat suite evidence");
        requireContains(plan, "`./gradlew runGameTestServer` 已通过", "plan must record GameTest evidence");
        requireContains(plan, "All 13 required tests passed :)", "plan must record the GameTest pass signal");

        requireCaseDefinitions(plan, VALIDATION_CASES);
        requireCaseDefinitions(plan, DEPLOY_CASES);
        requireCaseDefinitions(plan, MANUAL_CASES);
        requireManualTemplateRows(plan, MANUAL_CASES);
        requireContains(plan, "结果只能填写 `PASS`、`FAIL`、`BLOCKED` 或 `N/A`",
                "manual template must define allowed result states");
        requireContains(plan, "全部 P0/P1 修复并复测通过前，不得关闭 DoD",
                "manual template must block DoD closure on unresolved P0/P1 issues");

        System.out.println("PASS zombies deploy plan static contract compat");
    }

    private static void requireCaseDefinitions(String plan, List<String> cases) {
        for (String id : cases) {
            requireContains(plan, "| " + id + " |", "plan must define case " + id);
        }
    }

    private static void requireManualTemplateRows(String plan, List<String> cases) {
        for (String id : cases) {
            requireContains(plan, "| " + id + " | TODO |", "manual template must keep TODO row for " + id);
        }
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
