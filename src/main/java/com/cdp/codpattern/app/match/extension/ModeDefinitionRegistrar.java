package com.cdp.codpattern.app.match.extension;

import com.cdp.codpattern.app.match.model.GameModeDefinition;

/** Receiver used by mode contributors without exposing a concrete global registry. */
@FunctionalInterface
public interface ModeDefinitionRegistrar {
    void register(GameModeDefinition definition);
}
