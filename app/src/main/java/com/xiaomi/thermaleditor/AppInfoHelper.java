package com.xiaomi.thermaleditor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 负责读取应用列表权限检查/授权，并解析已安装应用的图标与实际显示名称
 */
public class AppInfoHelper {

    public static final String PERMISSION_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS";
    public static final int REQ_CODE_GET_INSTALLED_APPS = 1001;

    private static final Map<String, AppEntry> cachedApps = new HashMap<>();
    private static boolean cacheLoaded = false;

    public static boolean isPermissionSupported(Context context) {
        try {
            context.getPackageManager().getPermissionInfo(PERMISSION_GET_INSTALLED_APPS, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasReadAppListPermission(Context context) {
        if (!isPermissionSupported(context)) {
            return true;
        }
        return context.checkSelfPermission(PERMISSION_GET_INSTALLED_APPS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestReadAppListPermission(Activity activity) {
        if (isPermissionSupported(activity) && !hasReadAppListPermission(activity)) {
            activity.requestPermissions(new String[]{PERMISSION_GET_INSTALLED_APPS}, REQ_CODE_GET_INSTALLED_APPS);
        }
    }

    /**
     * 通过 Root 权限授予本应用读取应用列表权限 (适用于 HyperOS / MIUI)
     */
    public static boolean grantReadAppListViaRoot(Context context) {
        String pkg = context.getPackageName();
        String cmd = "pm grant " + pkg + " " + PERMISSION_GET_INSTALLED_APPS + " 2>/dev/null; "
                + "appops set " + pkg + " 10022 allow 2>/dev/null; "
                + "appops set " + pkg + " GET_INSTALLED_APPS allow 2>/dev/null; "
                + "echo DONE";
        RootShell.CommandResult res = RootShell.runSu(cmd);
        invalidateCache();
        return res.isSuccess() && hasReadAppListPermission(context);
    }

    public static synchronized void invalidateCache() {
        cachedApps.clear();
        cacheLoaded = false;
    }

    public static synchronized Map<String, AppEntry> getInstalledAppMap(Context context, boolean forceReload) {
        if (cacheLoaded && !forceReload) {
            return cachedApps;
        }
        cachedApps.clear();

        PackageManager pm = context.getPackageManager();

        // 收集具备桌面入口的应用包名，用于区分用户常用应用与纯后台组件
        Set<String> launcherPkgs = new HashSet<>();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
        if (resolveInfos != null) {
            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launcherPkgs.add(ri.activityInfo.packageName);
                }
            }
        }

        int flags = PackageManager.GET_META_DATA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
        }
        List<ApplicationInfo> apps = pm.getInstalledApplications(flags);
        if (apps != null) {
            for (ApplicationInfo ai : apps) {
                // 仅保留确实已安装到当前用户空间的应用
                if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                    continue;
                }
                String pkg = ai.packageName;
                CharSequence labelCs = ai.loadLabel(pm);
                String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString().trim() : pkg;
                Drawable icon;
                try {
                    icon = ai.loadIcon(pm);
                } catch (Exception e) {
                    icon = context.getDrawable(android.R.drawable.sym_def_app_icon);
                }
                boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                boolean hasLauncher = launcherPkgs.contains(pkg);

                cachedApps.put(pkg, new AppEntry(pkg, label, icon, true, isSystem, hasLauncher));
            }
        }
        cacheLoaded = true;
        return cachedApps;
    }

    /**
     * 获取规则中某个包对应的显示对象（已安装返回真实应用名和图标；未安装返回占位信息）
     */
    public static AppEntry resolveAppEntry(Context context, String packageName) {
        Map<String, AppEntry> map = getInstalledAppMap(context, false);
        AppEntry installed = map.get(packageName);
        if (installed != null) {
            return installed;
        }
        // 回退尝试直接查询单个包（应对某些受限但可直接查询的系统包）
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence labelCs = ai.loadLabel(pm);
            String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString().trim() : packageName;
            Drawable icon = ai.loadIcon(pm);
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            AppEntry entry = new AppEntry(packageName, label, icon, true, isSystem, false);
            map.put(packageName, entry);
            return entry;
        } catch (Exception ignored) {
        }

        Drawable placeholder = context.getDrawable(android.R.drawable.sym_def_app_icon);
        return new AppEntry(packageName, "未安装应用 (" + packageName + ")", placeholder, false, false, false);
    }

    public static List<AppEntry> getSortedInstalledApps(Context context, boolean includeSystemWithoutLauncher) {
        Map<String, AppEntry> map = getInstalledAppMap(context, false);
        List<AppEntry> result = new ArrayList<>();
        for (AppEntry entry : map.values()) {
            if (!includeSystemWithoutLauncher && entry.isSystem && !entry.hasLauncher) {
                continue;
            }
            result.add(entry);
        }
        Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(result, (a, b) -> {
            if (a.hasLauncher != b.hasLauncher) {
                return a.hasLauncher ? -1 : 1;
            }
            if (a.isSystem != b.isSystem) {
                return a.isSystem ? 1 : -1;
            }
            return collator.compare(a.displayName, b.displayName);
        });
        return result;
    }
}
