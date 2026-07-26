package com.cdp.codpattern.app.match.extension;

/** Public skeleton for a future main or addon to contribute mode definitions. */
@FunctionalInterface
public interface ModeDefinitionContributor {
    void contribute(ModeDefinitionRegistrar registrar);
}
