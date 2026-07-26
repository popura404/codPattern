package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.result.ModeErrorCode;
import com.cdp.codpattern.app.match.model.result.ModeOperationResult;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployServiceResult;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModeOperationResultCompatTest {
    private ModeOperationResultCompatTest() {
    }

    public static void main(String[] args) {
        errorCodesAreValueBased();
        serviceResultRoundTripPreservesNamedDataAndDiagnostics();
        deployResultRoundTripPreservesIndependentMessagesAndOrderedArguments();
        incompatibleFacadeConversionsFailInsteadOfDroppingData();
        System.out.println("PASS mode operation result compat");
    }

    private static void errorCodesAreValueBased() {
        require(ModeErrorCode.of(" object.busy ").equals(ModeErrorCode.of("object.busy")),
                "normalized keys should compare by value");
        require(ModeErrorCode.of("") == ModeErrorCode.OK, "blank keys should use the OK singleton");
        require(ZombiesErrorCode.of("object.busy").toModeErrorCode().equals(ModeErrorCode.of("object.busy")),
                "Zombies error codes should preserve their stable key");
    }

    private static void serviceResultRoundTripPreservesNamedDataAndDiagnostics() {
        Map<String, ModePlayerValue> parameters = new LinkedHashMap<>();
        parameters.put("cost", ModePlayerValue.ofInt(750));
        parameters.put("objectId", ModePlayerValue.ofString("wall-1"));
        ZombiesServiceResult<String> source = new ZombiesServiceResult<>(
                false,
                ZombiesErrorCode.OBJECT_BUSY,
                parameters,
                Optional.of("retained-value"),
                "diagnostic only");

        ModeOperationResult<String> neutral = source.toModeResult();
        require(neutral.messageKey().isEmpty(), "service result must not invent a message key");
        require(neutral.arguments().isEmpty(), "service result must not invent ordered arguments");
        require(source.equals(ZombiesServiceResult.fromModeResult(neutral)),
                "service result should survive a lossless neutral round trip");
    }

    private static void deployResultRoundTripPreservesIndependentMessagesAndOrderedArguments() {
        ZombiesDeployServiceResult<String> first = ZombiesDeployServiceResult.success(
                "snapshot",
                "message.codpattern.zombies.deploy.map_created",
                "alpha",
                "beta");
        ZombiesDeployServiceResult<String> second = ZombiesDeployServiceResult.success(
                "snapshot",
                "message.codpattern.zombies.deploy.selection_saved",
                "alpha",
                "beta");

        ModeOperationResult<String> firstNeutral = first.toModeResult();
        ModeOperationResult<String> secondNeutral = second.toModeResult();
        require(firstNeutral.code().equals(secondNeutral.code()),
                "the same code may intentionally have different presentation keys");
        require(!firstNeutral.messageKey().equals(secondNeutral.messageKey()),
                "message keys must remain independent from error codes");
        require(firstNeutral.arguments().equals(List.of("alpha", "beta")),
                "ordered translation arguments must preserve order");
        require(first.equals(ZombiesDeployServiceResult.fromModeResult(firstNeutral)),
                "deploy result should survive a lossless neutral round trip");
    }

    private static void incompatibleFacadeConversionsFailInsteadOfDroppingData() {
        ModeOperationResult<Void> deployOnly = new ModeOperationResult<>(
                false,
                ModeErrorCode.of("failure"),
                "message.fixture",
                Map.of(),
                List.of("arg"),
                Optional.empty(),
                "");
        requireThrows(() -> ZombiesServiceResult.fromModeResult(deployOnly),
                "service facade should reject presentation fields it cannot represent");

        ModeOperationResult<Void> serviceOnly = new ModeOperationResult<>(
                false,
                ModeErrorCode.of("failure"),
                "message.fixture",
                Map.of("cost", ModePlayerValue.ofInt(1)),
                List.of(),
                Optional.empty(),
                "diagnostic");
        requireThrows(() -> ZombiesDeployServiceResult.fromModeResult(serviceOnly),
                "deploy facade should reject named or diagnostic fields it cannot represent");
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
