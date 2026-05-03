package com.cdp.codpattern.config.zombies;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZombiesWeaponFilterConfig {
    private List<String> weaponTabs = defaultWeaponTabs();
    private ZombiesBackpackConfig.WeaponData defaultWeapon = ZombiesBackpackConfig.defaultWeapon();
    private Double ammunitionPerMagazineMultiple = 7.0;
    private List<String> blockedItemNamespaces = defaultBlockedItemNamespaces();
    private List<String> blockedWeaponIds = defaultBlockedWeaponIds();
    private List<String> blockedAttachmentNamespaces = defaultBlockedAttachmentNamespaces();
    private List<String> blockedAttachmentIds = defaultBlockedAttachmentIds();

    public List<String> getWeaponTabs() {
        if (weaponTabs == null) {
            weaponTabs = defaultWeaponTabs();
        }
        return weaponTabs;
    }

    public void setWeaponTabs(List<String> weaponTabs) {
        this.weaponTabs = normalizeStringList(weaponTabs);
        if (this.weaponTabs.isEmpty()) {
            this.weaponTabs = defaultWeaponTabs();
        }
    }

    public List<String> getAllowedWeaponTabs() {
        return getWeaponTabs();
    }

    public void setAllowedWeaponTabs(List<String> allowedWeaponTabs) {
        setWeaponTabs(allowedWeaponTabs);
    }

    public ZombiesBackpackConfig.WeaponData getDefaultWeapon() {
        if (defaultWeapon == null) {
            defaultWeapon = ZombiesBackpackConfig.defaultWeapon();
        }
        return defaultWeapon;
    }

    public void setDefaultWeapon(ZombiesBackpackConfig.WeaponData defaultWeapon) {
        this.defaultWeapon = defaultWeapon == null ? ZombiesBackpackConfig.defaultWeapon() : defaultWeapon;
    }

    public List<String> getBlockedItemNamespaces() {
        if (blockedItemNamespaces == null) {
            blockedItemNamespaces = defaultBlockedItemNamespaces();
        }
        return blockedItemNamespaces;
    }

    public void setBlockedItemNamespaces(List<String> blockedItemNamespaces) {
        this.blockedItemNamespaces = normalizeStringList(blockedItemNamespaces);
    }

    public List<String> getBlockedWeaponIds() {
        if (blockedWeaponIds == null) {
            blockedWeaponIds = defaultBlockedWeaponIds();
        }
        return blockedWeaponIds;
    }

    public void setBlockedWeaponIds(List<String> blockedWeaponIds) {
        this.blockedWeaponIds = normalizeStringList(blockedWeaponIds);
    }

    public List<String> getBlockedAttachmentNamespaces() {
        if (blockedAttachmentNamespaces == null) {
            blockedAttachmentNamespaces = defaultBlockedAttachmentNamespaces();
        }
        return blockedAttachmentNamespaces;
    }

    public void setBlockedAttachmentNamespaces(List<String> blockedAttachmentNamespaces) {
        this.blockedAttachmentNamespaces = normalizeStringList(blockedAttachmentNamespaces);
    }

    public List<String> getBlockedAttachmentIds() {
        if (blockedAttachmentIds == null) {
            blockedAttachmentIds = defaultBlockedAttachmentIds();
        }
        return blockedAttachmentIds;
    }

    public void setBlockedAttachmentIds(List<String> blockedAttachmentIds) {
        this.blockedAttachmentIds = normalizeStringList(blockedAttachmentIds);
    }

    public Double getAmmunitionPerMagazineMultiple() {
        return ammunitionPerMagazineMultiple;
    }

    public void setAmmunitionPerMagazineMultiple(Double ammunitionPerMagazineMultiple) {
        this.ammunitionPerMagazineMultiple = ammunitionPerMagazineMultiple;
    }

    public void normalize() {
        setWeaponTabs(weaponTabs);
        setDefaultWeapon(defaultWeapon);
        defaultWeapon.normalize();
        setBlockedItemNamespaces(blockedItemNamespaces);
        setBlockedWeaponIds(blockedWeaponIds);
        setBlockedAttachmentNamespaces(blockedAttachmentNamespaces);
        setBlockedAttachmentIds(blockedAttachmentIds);
        if (ammunitionPerMagazineMultiple == null
                || !Double.isFinite(ammunitionPerMagazineMultiple)
                || ammunitionPerMagazineMultiple < 0.0) {
            ammunitionPerMagazineMultiple = 7.0;
        }
    }

    private static List<String> defaultWeaponTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add("pistol");
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

    private static List<String> defaultBlockedAttachmentNamespaces() {
        List<String> namespaces = new ArrayList<>();
        namespaces.add("example_attachment_pack");
        return namespaces;
    }

    private static List<String> defaultBlockedAttachmentIds() {
        List<String> attachmentIds = new ArrayList<>();
        attachmentIds.add("namespace:attachmentid");
        return attachmentIds;
    }

    private static List<String> normalizeStringList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
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
