package com.cdp.codpattern.app.match.model;

public record TeamDescriptor(
        String teamName,
        String displayNameKey,
        String shortNameKey,
        int accentColor
) {
}
