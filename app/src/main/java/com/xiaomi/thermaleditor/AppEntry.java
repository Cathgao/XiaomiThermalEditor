package com.xiaomi.thermaleditor;

import android.graphics.drawable.Drawable;

/**
 * 应用显示信息模型（包含图标、实际显示名称及安装状态）
 */
public class AppEntry {
    public final String packageName;
    public final String displayName;
    public final Drawable icon;
    public final boolean installed;
    public final boolean isSystem;
    public final boolean hasLauncher;

    public AppEntry(String packageName, String displayName, Drawable icon,
                    boolean installed, boolean isSystem, boolean hasLauncher) {
        this.packageName = packageName;
        this.displayName = displayName;
        this.icon = icon;
        this.installed = installed;
        this.isSystem = isSystem;
        this.hasLauncher = hasLauncher;
    }
}
