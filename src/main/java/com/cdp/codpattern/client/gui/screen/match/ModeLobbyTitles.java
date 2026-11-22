package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The two lobby titles use their own languages, independently of the client's menu language. */
record ModeLobbyTitles(String chinese, String english) {
    private static final Map<String, ModeLobbyTitles> TITLES = new HashMap<>();
    private static Language cachedLanguage;

    static ModeLobbyTitles forMode(ModeDescriptor mode) {
        Language activeLanguage = Language.getInstance();
        if (cachedLanguage != activeLanguage) {
            Language chinese = language("zh_cn");
            Language english = language("en_us");
            TITLES.clear();
            for (ModeDescriptor registered : GameModeRegistry.orderedModes()) {
                String key = registered.displayNameKey();
                String readableId = registered.gameType().replace('_', ' ').replace('-', ' ');
                String localized = activeLanguage.getOrDefault(key, readableId);
                String chineseTitle = chinese.getOrDefault(key, localized);
                String englishTitle = english.getOrDefault(key + ".lobby", english.getOrDefault(key, readableId));
                TITLES.put(registered.gameType(), new ModeLobbyTitles(chineseTitle, englishTitle.toLowerCase(Locale.ROOT)));
            }
            cachedLanguage = activeLanguage;
        }
        ModeLobbyTitles titles = TITLES.get(mode.gameType());
        if (titles != null) {
            return titles;
        }
        String english = mode.gameType().replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        return new ModeLobbyTitles(activeLanguage.getOrDefault(mode.displayNameKey(), english), english);
    }

    private static Language language(String code) {
        Minecraft minecraft = Minecraft.getInstance();
        return code.equals(minecraft.getLanguageManager().getSelected()) ? Language.getInstance()
                : ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of(code), false);
    }
}
