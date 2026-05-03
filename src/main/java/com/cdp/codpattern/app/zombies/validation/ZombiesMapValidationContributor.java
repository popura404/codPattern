package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * Adds validation rules for one profile; returned ERROR issues block startup while WARNING issues do not.
 */
public interface ZombiesMapValidationContributor {
    default int order() {
        return 0;
    }

    List<ZombiesValidationIssue> validate(ZombiesMapValidationContext context);

    enum Severity {
        ERROR,
        WARNING
    }

    record ZombiesMapValidationContext(
            RoomId roomId,
            CommonModeMapData commonData,
            List<ModeObjectData> objects,
            String profileKey
    ) {
        public ZombiesMapValidationContext {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(commonData, "commonData");
            objects = objects == null ? List.of() : List.copyOf(objects);
            profileKey = Objects.requireNonNullElse(profileKey, "").trim();
        }
    }

    record ZombiesValidationIssue(
            Severity severity,
            ZombiesErrorCode code,
            String subject,
            String message
    ) {
        public ZombiesValidationIssue {
            severity = severity == null ? Severity.ERROR : severity;
            code = code == null ? ZombiesErrorCode.OK : code;
            subject = Objects.requireNonNullElse(subject, "").trim();
            message = Objects.requireNonNullElse(message, "");
        }
    }
}
