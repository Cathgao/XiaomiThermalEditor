package com.xiaomi.thermaleditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 控制“应用规则”标签页：按目标温控文件动态聚合规则、图标/显示名展示、增删应用、保存与重启电量服务
 */
public class AppRulesController {

    /**
     * 按主温控文件聚合后的展示分组（底层仍保留原始 ThermalRule 列表以无损写回数据库）
     */
    private static class ThermalFileGroup {
        final String primaryConf;
        final String spinnerTitle;
        final String detailSummary;
        final List<ThermalRule> memberRules = new ArrayList<>();

        ThermalFileGroup(String primaryConf, String spinnerTitle, String detailSummary) {
            this.primaryConf = primaryConf;
            this.spinnerTitle = spinnerTitle;
            this.detailSummary = detailSummary;
        }

        boolean containsPackage(String pkg) {
            for (ThermalRule r : memberRules) {
                if (r.containsPackage(pkg)) return true;
            }
            return false;
        }

        boolean removePackage(String pkg) {
            boolean removed = false;
            for (ThermalRule r : memberRules) {
                if (r.removePackage(pkg)) removed = true;
            }
            return removed;
        }

        ThermalRule getDefaultWriteRule() {
            for (ThermalRule r : memberRules) {
                if (r.sourceType == ThermalRule.SourceType.THERMAL_GROUP) {
                    return r;
                }
            }
            return memberRules.get(0);
        }

        Set<String> collectUniquePackages() {
            Set<String> set = new LinkedHashSet<>();
            for (ThermalRule r : memberRules) {
                set.addAll(r.packages);
            }
            return set;
        }

        String findSubRuleTag(String pkg) {
            if (memberRules.size() <= 1) return "";
            List<String> keys = new ArrayList<>();
            for (ThermalRule r : memberRules) {
                if (r.containsPackage(pkg)) {
                    keys.add("key_optimize_game_names".equals(r.ruleKey) ? "optiGame" : r.ruleKey);
                }
            }
            return String.join("/", keys);
        }
    }

    private final Activity activity;

    private final View layoutPermissionCard;
    private final Button btnGrantAppPerm;
    private final Spinner spRules;
    private final Button btnReloadRules;
    private final TextView tvRuleMapping;
    private final TextView tvRuleCount;
    private final EditText etRuleSearch;
    private final CheckBox cbOnlyInstalled;
    private final Button btnAddRuleApp;
    private final RecyclerView rvRuleApps;

    private final Button btnRestoreDbBak;
    private final Button btnRestartPowerkeeper;
    private final Button btnSaveRules;

    private final List<ThermalRule> rawRules = new ArrayList<>();
    private final List<ThermalFileGroup> fileGroups = new ArrayList<>();
    private final List<String> groupTitles = new ArrayList<>();
    private final ArrayAdapter<String> ruleSpinnerAdapter;
    private final RuleAppAdapter ruleAppAdapter;

    private ThermalFileGroup currentGroup;
    private boolean hasLoadedOnce = false;
    private boolean isDirty = false;

    public AppRulesController(Activity activity, View rootView) {
        this.activity = activity;

        layoutPermissionCard = rootView.findViewById(R.id.layout_app_perm_card);
        btnGrantAppPerm = rootView.findViewById(R.id.btn_grant_app_perm);
        spRules = rootView.findViewById(R.id.sp_rules);
        btnReloadRules = rootView.findViewById(R.id.btn_reload_rules);
        tvRuleMapping = rootView.findViewById(R.id.tv_rule_mapping);
        tvRuleCount = rootView.findViewById(R.id.tv_rule_count);
        etRuleSearch = rootView.findViewById(R.id.et_rule_search);
        cbOnlyInstalled = rootView.findViewById(R.id.cb_only_installed);
        btnAddRuleApp = rootView.findViewById(R.id.btn_add_rule_app);
        rvRuleApps = rootView.findViewById(R.id.rv_rule_apps);

        btnRestoreDbBak = rootView.findViewById(R.id.btn_restore_db_bak);
        btnRestartPowerkeeper = rootView.findViewById(R.id.btn_restart_powerkeeper);
        btnSaveRules = rootView.findViewById(R.id.btn_save_rules);

        View layoutRuleCard = rootView.findViewById(R.id.layout_rule_card);
        ruleSpinnerAdapter = new ArrayAdapter<>(activity, R.layout.item_spinner_selected, groupTitles);
        SpinnerHelper.configure(spRules, layoutRuleCard, ruleSpinnerAdapter);

        ruleAppAdapter = new RuleAppAdapter(this::onRemoveAppClicked);
        rvRuleApps.setLayoutManager(new LinearLayoutManager(activity));
        rvRuleApps.setAdapter(ruleAppAdapter);

        setupListeners();
    }

    private void setupListeners() {
        btnGrantAppPerm.setOnClickListener(v -> onGrantPermissionClicked());

        btnReloadRules.setOnClickListener(v -> {
            AppInfoHelper.invalidateCache();
            loadRulesAsync();
        });

        spRules.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < fileGroups.size()) {
                    currentGroup = fileGroups.get(position);
                    renderCurrentGroup();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        etRuleSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                renderCurrentGroup();
            }
        });

        cbOnlyInstalled.setOnCheckedChangeListener((buttonView, isChecked) -> renderCurrentGroup());

        btnAddRuleApp.setOnClickListener(v -> showAddAppDialog());

        btnSaveRules.setOnClickListener(v -> onSaveRulesClicked());

        btnRestartPowerkeeper.setOnClickListener(v -> onRestartPowerKeeperClicked());

        btnRestoreDbBak.setOnClickListener(v -> onRestoreDbBakClicked());
    }

    public void onTabActivated() {
        refreshPermissionBanner();
        if (rawRules.isEmpty()) {
            if (!hasLoadedOnce) {
                hasLoadedOnce = true;
                if (!AppInfoHelper.hasReadAppListPermission(activity)) {
                    AppInfoHelper.requestReadAppListPermission(activity);
                }
            }
            loadRulesAsync();
        }
    }

    public void onPermissionResult() {
        refreshPermissionBanner();
        AppInfoHelper.invalidateCache();
        if (hasLoadedOnce) {
            renderCurrentGroup();
        }
    }

    private void refreshPermissionBanner() {
        boolean granted = AppInfoHelper.hasReadAppListPermission(activity);
        layoutPermissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void onGrantPermissionClicked() {
        AppInfoHelper.requestReadAppListPermission(activity);
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            if (!AppInfoHelper.hasReadAppListPermission(activity)) {
                boolean ok = AppInfoHelper.grantReadAppListViaRoot(activity);
                activity.runOnUiThread(() -> {
                    refreshPermissionBanner();
                    if (ok) {
                        Toast.makeText(activity, "已通过 Root 授予读取应用列表权限", Toast.LENGTH_SHORT).show();
                        renderCurrentGroup();
                    }
                });
            }
        }).start();
    }

    private void loadRulesAsync() {
        tvRuleMapping.setText("正在解码数据库与系统温控映射...");
        new Thread(() -> {
            AppInfoHelper.getInstalledAppMap(activity, true);
            PowerKeeperDbManager.LoadResult result = PowerKeeperDbManager.loadRules(activity);
            activity.runOnUiThread(() -> {
                if (result.errorMessage != null) {
                    tvRuleMapping.setText(result.errorMessage);
                    Toast.makeText(activity, result.errorMessage, Toast.LENGTH_LONG).show();
                    return;
                }
                rawRules.clear();
                rawRules.addAll(result.rules);
                rebuildFileGroups();

                isDirty = false;
                updateSaveButtonLabel();

                if (!fileGroups.isEmpty()) {
                    int prevIdx = spRules.getSelectedItemPosition();
                    int idx = (prevIdx >= 0 && prevIdx < fileGroups.size()) ? prevIdx : 0;
                    spRules.setSelection(idx);
                    currentGroup = fileGroups.get(idx);
                    renderCurrentGroup();
                } else {
                    tvRuleMapping.setText("未在数据库中找到温控规则");
                }
            });
        }).start();
    }

    /**
     * 按解析出的主温控文件 (primaryConf) 动态归并规则
     */
    private void rebuildFileGroups() {
        fileGroups.clear();
        groupTitles.clear();

        Map<String, List<ThermalRule>> grouped = new LinkedHashMap<>();
        for (ThermalRule r : rawRules) {
            String key = (r.primaryConf != null && !r.primaryConf.isEmpty()) ? r.primaryConf : r.ruleKey;
            List<ThermalRule> bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(key, bucket);
            }
            bucket.add(r);
        }

        for (Map.Entry<String, List<ThermalRule>> entry : grouped.entrySet()) {
            String confName = entry.getKey();
            List<ThermalRule> members = entry.getValue();

            List<String> subKeys = new ArrayList<>();
            for (ThermalRule m : members) {
                subKeys.add("key_optimize_game_names".equals(m.ruleKey) ? "optiGame"
                        : ("thermal_videochat_config_enable_params2".equals(m.ruleKey) ? "videochat" : m.ruleKey));
            }

            String title = buildGroupSpinnerTitle(confName, members);
            String summary = members.get(0).sconfigSummary;
            if (members.size() > 1) {
                summary = summary + " | 含分组: " + String.join(", ", subKeys);
            }

            ThermalFileGroup fg = new ThermalFileGroup(confName, title, summary);
            fg.memberRules.addAll(members);
            fileGroups.add(fg);
            groupTitles.add(title);
        }
        ruleSpinnerAdapter.notifyDataSetChanged();
    }

    private String buildGroupSpinnerTitle(String confName, List<ThermalRule> members) {
        String label;
        switch (confName) {
            case "thermal-mgame.conf":
                label = "游戏温控 (性能模式→tgame)";
                break;
            case "thermal-nolimits.conf":
                label = "不限温控 / 跑分";
                break;
            case "thermal-class0.conf":
                label = "日常主流应用";
                break;
            case "thermal-video.conf":
                label = "视频与直播";
                break;
            case "thermal-videochat.conf":
                label = "视频通话专项 (平时→class0)";
                break;
            case "thermal-navigation.conf":
                label = "地图导航出行";
                break;
            case "thermal-camera.conf":
                label = "相机与图库";
                break;
            case "thermal-arvr.conf":
                label = "AR / VR 场景";
                break;
            case "thermal-huanji.conf":
                label = "换机迁移";
                break;
            case "thermal-demo.conf":
                label = "样机演示";
                break;
            default:
                label = members.get(0).displayName;
                break;
        }
        if (members.size() > 1) {
            return confName + " · " + label + " [" + members.size() + "组]";
        }
        return confName + " · " + label;
    }

    private void renderCurrentGroup() {
        if (currentGroup == null) return;

        tvRuleMapping.setText(currentGroup.detailSummary);

        String query = etRuleSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean onlyInstalled = cbOnlyInstalled.isChecked();

        List<RuleAppAdapter.RuleAppItem> installedMatched = new ArrayList<>();
        List<RuleAppAdapter.RuleAppItem> uninstalledMatched = new ArrayList<>();
        Set<String> uniquePkgs = currentGroup.collectUniquePackages();
        int totalInstalled = 0;

        for (String pkg : uniquePkgs) {
            AppEntry entry = AppInfoHelper.resolveAppEntry(activity, pkg);
            if (entry.installed) {
                totalInstalled++;
            } else if (onlyInstalled) {
                continue;
            }
            if (!query.isEmpty() && !entry.displayName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            String subTag = currentGroup.findSubRuleTag(pkg);
            RuleAppAdapter.RuleAppItem item = new RuleAppAdapter.RuleAppItem(entry, subTag);
            if (entry.installed) {
                installedMatched.add(item);
            } else {
                uninstalledMatched.add(item);
            }
        }

        Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(installedMatched, (a, b) -> collator.compare(a.entry.displayName, b.entry.displayName));
        Collections.sort(uninstalledMatched, (a, b) -> collator.compare(a.entry.displayName, b.entry.displayName));

        List<RuleAppAdapter.RuleAppItem> displayList = new ArrayList<>(installedMatched.size() + uninstalledMatched.size());
        displayList.addAll(installedMatched);
        displayList.addAll(uninstalledMatched);

        ruleAppAdapter.submitList(displayList);
        tvRuleCount.setText("已安装 " + totalInstalled + " / 共 " + uniquePkgs.size());
    }

    private void onRemoveAppClicked(AppEntry entry) {
        if (currentGroup == null) return;
        new AlertDialog.Builder(activity)
                .setTitle("移出温控分类")
                .setMessage("确定将「" + entry.displayName + "」从 " + currentGroup.primaryConf + " 中移除吗？")
                .setPositiveButton("移除", (d, w) -> {
                    if (currentGroup.removePackage(entry.packageName)) {
                        isDirty = true;
                        updateSaveButtonLabel();
                        renderCurrentGroup();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddAppDialog() {
        if (currentGroup == null) {
            Toast.makeText(activity, "请先加载规则", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_picker, null);
        EditText etSearch = dialogView.findViewById(R.id.et_search_app);
        CheckBox cbShowSystem = dialogView.findViewById(R.id.cb_show_system);
        RecyclerView rvPicker = dialogView.findViewById(R.id.rv_picker_apps);
        rvPicker.setLayoutManager(new LinearLayoutManager(activity));

        Map<String, String> existingMap = buildExistingGroupBadgeMap(currentGroup);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        AppPickerAdapter pickerAdapter = new AppPickerAdapter(existingMap, entry -> {
            if (currentGroup.containsPackage(entry.packageName)) {
                Toast.makeText(activity, "「" + entry.displayName + "」已在当前配置中", Toast.LENGTH_SHORT).show();
                return;
            }
            handleAddAppToCurrentGroup(entry, () -> {
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
            });
        });
        rvPicker.setAdapter(pickerAdapter);

        Runnable refreshPicker = () -> {
            String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            boolean includeSys = cbShowSystem.isChecked();
            List<AppEntry> allApps = AppInfoHelper.getSortedInstalledApps(activity, includeSys);
            List<AppEntry> filtered = new ArrayList<>();
            for (AppEntry app : allApps) {
                if (currentGroup.containsPackage(app.packageName)) continue;
                if (!q.isEmpty() && !app.displayName.toLowerCase(Locale.ROOT).contains(q)) continue;
                filtered.add(app);
            }
            pickerAdapter.submitList(filtered);
        };

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                refreshPicker.run();
            }
        });
        cbShowSystem.setOnCheckedChangeListener((b, checked) -> refreshPicker.run());
        refreshPicker.run();

        dialogHolder[0] = new AlertDialog.Builder(activity)
                .setTitle("添加应用到: " + currentGroup.primaryConf)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();
        dialogHolder[0].show();
    }

    private void handleAddAppToCurrentGroup(AppEntry entry, Runnable onAdded) {
        ThermalRule targetRule = currentGroup.getDefaultWriteRule();
        List<ThermalFileGroup> conflictGroups = findConflictGroups(entry.packageName, currentGroup);

        if (conflictGroups.isEmpty()) {
            targetRule.addPackage(entry.packageName);
            isDirty = true;
            updateSaveButtonLabel();
            renderCurrentGroup();
            Toast.makeText(activity, "已添加: " + entry.displayName + " (写入分组 " + targetRule.ruleKey + ")", Toast.LENGTH_SHORT).show();
            onAdded.run();
            return;
        }

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < conflictGroups.size(); i++) {
            if (i > 0) names.append("、");
            names.append(conflictGroups.get(i).primaryConf);
        }

        new AlertDialog.Builder(activity)
                .setTitle("配置冲突提示")
                .setMessage("「" + entry.displayName + "」当前已属于 [" + names + "]。\n\n同一应用在 PowerKeeper 中只能命中一条主规则，是否将其从原配置移除并移动到 [" + currentGroup.primaryConf + "]？")
                .setPositiveButton("移动并添加", (d, w) -> {
                    for (ThermalFileGroup g : conflictGroups) {
                        g.removePackage(entry.packageName);
                    }
                    targetRule.addPackage(entry.packageName);
                    isDirty = true;
                    updateSaveButtonLabel();
                    renderCurrentGroup();
                    Toast.makeText(activity, "已移动到 " + currentGroup.primaryConf + ": " + entry.displayName, Toast.LENGTH_SHORT).show();
                    onAdded.run();
                })
                .setNeutralButton("仅保留两处添加", (d, w) -> {
                    targetRule.addPackage(entry.packageName);
                    isDirty = true;
                    updateSaveButtonLabel();
                    renderCurrentGroup();
                    onAdded.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<ThermalFileGroup> findConflictGroups(String pkg, ThermalFileGroup excludeGroup) {
        List<ThermalFileGroup> conflicts = new ArrayList<>();
        for (ThermalFileGroup g : fileGroups) {
            if (g == excludeGroup) continue;
            if (g.containsPackage(pkg)) {
                conflicts.add(g);
            }
        }
        return conflicts;
    }

    private Map<String, String> buildExistingGroupBadgeMap(ThermalFileGroup excludeGroup) {
        Map<String, String> map = new HashMap<>();
        for (ThermalFileGroup g : fileGroups) {
            if (g == excludeGroup) continue;
            for (String pkg : g.collectUniquePackages()) {
                String prev = map.get(pkg);
                if (prev == null) {
                    map.put(pkg, "当前: " + g.primaryConf);
                } else {
                    map.put(pkg, prev + " / " + g.primaryConf);
                }
            }
        }
        return map;
    }

    private void updateSaveButtonLabel() {
        btnSaveRules.setText(isDirty ? "保存规则到数据库 *" : "保存规则到数据库");
    }

    private void onSaveRulesClicked() {
        if (rawRules.isEmpty()) {
            Toast.makeText(activity, "没有可保存的规则", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSaveRules.setEnabled(false);
        btnSaveRules.setText("正在写回...");

        new Thread(() -> {
            boolean ok = PowerKeeperDbManager.saveRules(activity, rawRules);
            activity.runOnUiThread(() -> {
                btnSaveRules.setEnabled(true);
                if (ok) {
                    isDirty = false;
                    updateSaveButtonLabel();
                    new AlertDialog.Builder(activity)
                            .setTitle("数据库保存成功")
                            .setMessage("已将修改后的应用规则写回 PowerKeeper 数据库（原库已备份为 user_configure.db.bak）。\n\n是否立即重启电量与性能服务 (com.miui.powerkeeper) 使规则生效？")
                            .setPositiveButton("立即重启生效", (d, w) -> onRestartPowerKeeperClicked())
                            .setNegativeButton("稍后手动重启", null)
                            .show();
                } else {
                    updateSaveButtonLabel();
                    Toast.makeText(activity, "保存数据库失败，请检查 Root 权限", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void onRestartPowerKeeperClicked() {
        btnRestartPowerkeeper.setEnabled(false);
        btnRestartPowerkeeper.setText("重启中...");

        new Thread(() -> {
            boolean ok = PowerKeeperDbManager.restartPowerKeeper();
            activity.runOnUiThread(() -> {
                btnRestartPowerkeeper.setEnabled(true);
                btnRestartPowerkeeper.setText("重启电量服务");
                if (ok) {
                    Toast.makeText(activity, "电量与性能服务已重启，新规则已生效！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "未检测到 PowerKeeper 进程重启，请检查 Root 权限", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void onRestoreDbBakClicked() {
        new AlertDialog.Builder(activity)
                .setTitle("恢复数据库备份")
                .setMessage("确定从 user_configure.db.bak 恢复应用规则数据库吗？")
                .setPositiveButton("确定恢复", (d, w) -> {
                    new Thread(() -> {
                        boolean ok = PowerKeeperDbManager.restoreBackup();
                        activity.runOnUiThread(() -> {
                            if (ok) {
                                Toast.makeText(activity, "已恢复备份，正在重新加载...", Toast.LENGTH_SHORT).show();
                                loadRulesAsync();
                            } else {
                                Toast.makeText(activity, "备份文件不存在: user_configure.db.bak", Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
