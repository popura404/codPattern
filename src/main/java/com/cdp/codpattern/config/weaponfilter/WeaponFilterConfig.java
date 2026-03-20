package com.cdp.codpattern.config.weaponfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 背包与物品发放逻辑的配置模型
 */
public class WeaponFilterConfig {
    private List<String> primaryWeaponTabs = defaultPrimaryWeaponTabs();
    private List<String> secondaryWeaponTabs = defaultSecondaryWeaponTabs();
    private List<String> blockedItemNamespaces = defaultBlockedItemNamespaces();
    private List<String> blockedWeaponIds = defaultBlockedWeaponIds();

    /** null 或旧配置无此键时视为 true */
    private Boolean throwablesEnabled = true;

    // 默认弹匣携带倍数
    private Integer ammunitionPerMagazineMultiple = 6;

    public List<String> getPrimaryWeaponTabs() {
        return primaryWeaponTabs;
    }

    public void setPrimaryWeaponTabs(List<String> primaryWeaponTabs) {
        this.primaryWeaponTabs = primaryWeaponTabs == null ? defaultPrimaryWeaponTabs() : primaryWeaponTabs;
    }

    public List<String> getSecondaryWeaponTabs() {
        return secondaryWeaponTabs;
    }

    public void setSecondaryWeaponTabs(List<String> secondaryWeaponTabs) {
        this.secondaryWeaponTabs = secondaryWeaponTabs == null ? defaultSecondaryWeaponTabs() : secondaryWeaponTabs;
    }

    public List<String> getBlockedItemNamespaces() {
        if (blockedItemNamespaces == null) {
            return defaultBlockedItemNamespaces();
        }
        return blockedItemNamespaces;
    }

    public void setBlockedItemNamespaces(List<String> blockedItemNamespaces) {
        if (blockedItemNamespaces == null) {
            this.blockedItemNamespaces = defaultBlockedItemNamespaces();
            return;
        }
        this.blockedItemNamespaces = normalizeStringList(blockedItemNamespaces);
    }

    public List<String> getBlockedWeaponIds() {
        if (blockedWeaponIds == null) {
            return defaultBlockedWeaponIds();
        }
        return blockedWeaponIds;
    }

    public void setBlockedWeaponIds(List<String> blockedWeaponIds) {
        if (blockedWeaponIds == null) {
            this.blockedWeaponIds = defaultBlockedWeaponIds();
            return;
        }
        this.blockedWeaponIds = normalizeStringList(blockedWeaponIds);
    }

    public Integer getAmmunitionPerMagazineMultiple() {
        return ammunitionPerMagazineMultiple;
    }

    public void setAmmunitionPerMagazineMultiple(Integer ammunitionPerMagazineMultiple) {
        this.ammunitionPerMagazineMultiple = ammunitionPerMagazineMultiple;
    }

    public boolean isThrowablesEnabled() {
        return throwablesEnabled == null || throwablesEnabled;
    }

    public void setThrowablesEnabled(boolean value) {
        this.throwablesEnabled = value;
    }

    public void normalize() {
        setPrimaryWeaponTabs(primaryWeaponTabs);
        setSecondaryWeaponTabs(secondaryWeaponTabs);
        setBlockedItemNamespaces(blockedItemNamespaces);
        setBlockedWeaponIds(blockedWeaponIds);
        if (throwablesEnabled == null) {
            throwablesEnabled = true;
        }
        if (ammunitionPerMagazineMultiple == null) {
            ammunitionPerMagazineMultiple = 6;
        }
    }

    private static List<String> defaultPrimaryWeaponTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add("rifle");
        tabs.add("sniper");
        tabs.add("shotgun");
        tabs.add("smg");
        tabs.add("mg");
        return tabs;
    }

    private static List<String> defaultSecondaryWeaponTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add("pistol");
        tabs.add("rpg");
        tabs.add("melee");
        return tabs;
    }

    private static List<String> defaultBlockedItemNamespaces() {
        List<String> namespaces = new ArrayList<>();
        namespaces.add("example_gunpack");
        return namespaces;
    }

    private static List<String> defaultBlockedWeaponIds() {
        List<String> weaponIds = new ArrayList<>();
        weaponIds.add("namespace:gunid");
        return weaponIds;
    }

    private static List<String> normalizeStringList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
            if (!normalizedValue.isEmpty() && !normalized.contains(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }
}
