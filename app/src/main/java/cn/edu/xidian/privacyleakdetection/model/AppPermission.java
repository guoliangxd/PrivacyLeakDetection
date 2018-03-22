package cn.edu.xidian.privacyleakdetection.model;

import android.graphics.drawable.Drawable;

/**
 * Created by 99544 on 2018/3/22/022.
 * 用于存储应用名，图标id，总权限数，申请权限数，权限数组；
 */

public class AppPermission {
    private String appName;
    private Drawable icon;
    private int totalPermission;
    private int privacyPermission;
    private String[] commonPermission;
    private String[] dangerPermission;

    //所有属性的获取和设置方法

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public int getTotalPermission() {
        return totalPermission;
    }

    public void setTotalPermission(int totalPermission) {
        this.totalPermission = totalPermission;
    }

    public int getPrivacyPermission() {
        return privacyPermission;
    }

    public void setPrivacyPermission(int privacyPermission) {
        this.privacyPermission = privacyPermission;
    }

    public String[] getCommonPermission() {
        return commonPermission;
    }

    public void setCommonPermission(String[] commonPermission) {
        this.commonPermission = commonPermission;
    }

    public String[] getDangerPermission() {
        return dangerPermission;
    }

    public void setDangerPermission(String[] dangerPermission) {
        this.dangerPermission = dangerPermission;
    }

}
