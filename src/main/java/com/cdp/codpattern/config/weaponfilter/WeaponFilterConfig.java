package com.cdp.codpattern.config.weaponfilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包与物品发放逻辑的配置模型
 */
public class WeaponFilterConfig {
    private List<String> primaryWeaponTabs = defaultPrimaryWeaponTabs();
    private List<String> secondaryWeaponTabs = defaultSecondaryWeaponTabs();

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
}
