package com.xiaomi.thermaleditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView tvDeviceInfo;
    private TextView tvRootStatus;
    private Spinner spFiles;
    private Button btnReload;
    private TextView tvKeyBadge;
    private TextView tvFilePath;

    private Button btnTabVisual;
    private Button btnTabRaw;
    private View layoutVisualMode;
    private View layoutRawMode;

    private Spinner spSections;
    private TextView tvSectionBadge;
    private View layoutTierHeader;
    private Button btnAddTier;
    private RecyclerView rvTiers;
    private LinearLayout containerParams;
    private EditText etRawContent;

    private Button btnRestoreBak;
    private Button btnRestartService;
    private Button btnSave;

    private boolean isRoot = false;
    private final List<String> fileList = new ArrayList<>();
    private ArrayAdapter<String> fileAdapter;

    private ThermalFile currentThermalFile;
    private ThermalSection currentSection;
    private TierAdapter tierAdapter;
    private ItemTouchHelper itemTouchHelper;

    private final List<String> sectionNames = new ArrayList<>();
    private ArrayAdapter<String> sectionAdapter;

    private boolean isRawMode = false;
    private boolean isUserSelectingSection = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        checkRootAndLoadFiles();
    }

    private void initViews() {
        tvDeviceInfo = findViewById(R.id.tv_device_info);
        tvRootStatus = findViewById(R.id.tv_root_status);
        spFiles = findViewById(R.id.sp_files);
        btnReload = findViewById(R.id.btn_reload);
        tvKeyBadge = findViewById(R.id.tv_key_badge);
        tvFilePath = findViewById(R.id.tv_file_path);

        btnTabVisual = findViewById(R.id.btn_tab_visual);
        btnTabRaw = findViewById(R.id.btn_tab_raw);
        layoutVisualMode = findViewById(R.id.layout_visual_mode);
        layoutRawMode = findViewById(R.id.layout_raw_mode);

        spSections = findViewById(R.id.sp_sections);
        tvSectionBadge = findViewById(R.id.tv_section_badge);
        layoutTierHeader = findViewById(R.id.layout_tier_header);
        btnAddTier = findViewById(R.id.btn_add_tier);
        rvTiers = findViewById(R.id.rv_tiers);
        containerParams = findViewById(R.id.container_params);
        etRawContent = findViewById(R.id.et_raw_content);

        btnRestoreBak = findViewById(R.id.btn_restore_bak);
        btnRestartService = findViewById(R.id.btn_restart_service);
        btnSave = findViewById(R.id.btn_save);

        String model = Build.MODEL;
        String device = Build.DEVICE;
        tvDeviceInfo.setText(model + " (" + device + ") Android " + Build.VERSION.RELEASE);

        // 文件切换监听
        fileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileList);
        fileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFiles.setAdapter(fileAdapter);
        spFiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < fileList.size()) {
                    loadFileAsync(fileList.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 区域切换监听
        sectionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sectionNames);
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSections.setAdapter(sectionAdapter);
        spSections.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isUserSelectingSection) return;
                if (currentThermalFile != null && position >= 0 && position < currentThermalFile.getSections().size()) {
                    switchSection(currentThermalFile.getSections().get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 模式切换
        btnTabVisual.setOnClickListener(v -> switchMode(false));
        btnTabRaw.setOnClickListener(v -> switchMode(true));

        // 重新加载按钮
        btnReload.setOnClickListener(v -> {
            String selected = (String) spFiles.getSelectedItem();
            if (selected != null) {
                loadFileAsync(selected);
            }
        });

        // 添加档位按钮
        btnAddTier.setOnClickListener(v -> onAddTierClicked());

        // 保存并加密应用
        btnSave.setOnClickListener(v -> onSaveClicked());

        // 重启温控
        btnRestartService.setOnClickListener(v -> onRestartServiceClicked());

        // 恢复备份
        btnRestoreBak.setOnClickListener(v -> onRestoreBakClicked());
    }

    private void setupRecyclerView() {
        rvTiers.setLayoutManager(new LinearLayoutManager(this));
    }

    private void checkRootAndLoadFiles() {
        tvRootStatus.setText("正在检查 Root...");
        tvRootStatus.setTextColor(Color.parseColor("#F59E0B"));

        new Thread(() -> {
            isRoot = RootShell.isRootAvailable();
            if (isRoot) {
                // 仅在真实获得 Root 权限后，实时探测当前硬件节点的频率表
                FrequencyManager.scanAvailableFrequencies();
            }
            runOnUiThread(() -> {
                if (isRoot) {
                    tvRootStatus.setText("Root 已授权 (uid=0)");
                    tvRootStatus.setTextColor(Color.parseColor("#10B981"));
                    loadThermalFileListAsync();
                } else {
                    tvRootStatus.setText("未获取 Root 权限");
                    tvRootStatus.setTextColor(Color.parseColor("#EF4444"));
                    showRootPermissionRequiredDialog();
                }
            });
        }).start();
    }

    private void showRootPermissionRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要 Root 权限")
                .setMessage("小米温控编辑器必须依赖 Root 权限才能使用。\n\n检测到当前尚未获得 Root 权限，请在 KernelSU / SukiSU / APatch / Magisk 中为本应用授予超级用户权限。")
                .setCancelable(false)
                .setPositiveButton("重新请求授权", (dialog, which) -> {
                    checkRootAndLoadFiles();
                })
                .setNegativeButton("退出应用", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    private void loadThermalFileListAsync() {
        new Thread(() -> {
            List<String> files = RootShell.listThermalFiles();
            runOnUiThread(() -> {
                fileList.clear();
                if (files.isEmpty()) {
                    fileList.add("thermal-normal.conf");
                } else {
                    fileList.addAll(files);
                }
                fileAdapter.notifyDataSetChanged();
                if (!fileList.isEmpty()) {
                    spFiles.setSelection(0);
                }
            });
        }).start();
    }

    private void loadFileAsync(String fileName) {
        tvFilePath.setText(RootShell.THERMAL_CONFIG_DIR + "/" + fileName);
        tvKeyBadge.setText("正在读取并自动解密...");
        tvKeyBadge.setTextColor(Color.parseColor("#F59E0B"));

        new Thread(() -> {
            byte[] data = RootShell.readFileBytes(fileName);
            if (data == null || data.length == 0) {
                runOnUiThread(() -> {
                    tvKeyBadge.setText("文件读取失败 (检查权限或文件是否存在)");
                    tvKeyBadge.setTextColor(Color.parseColor("#EF4444"));
                });
                return;
            }

            try {
                ThermalCipher.DecryptResult decryptResult = ThermalCipher.decrypt(data);
                ThermalFile parsed = ThermalParser.parse(decryptResult.content, fileName, decryptResult.keyType);

                runOnUiThread(() -> {
                    currentThermalFile = parsed;
                    updateKeyBadge(decryptResult.keyType, decryptResult.wasEncrypted);
                    updateSectionList();
                    etRawContent.setText(decryptResult.content);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvKeyBadge.setText("解密失败: " + e.getMessage());
                    tvKeyBadge.setTextColor(Color.parseColor("#EF4444"));
                    Toast.makeText(this, "解密异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void updateKeyBadge(ThermalCipher.KeyType keyType, boolean wasEncrypted) {
        if (!wasEncrypted) {
            tvKeyBadge.setText("格式: 明文未加密");
            tvKeyBadge.setTextColor(Color.parseColor("#9CA3AF"));
        } else if (keyType == ThermalCipher.KeyType.OLD) {
            tvKeyBadge.setText("已自动解密: 经典密钥 (AES-128-CBC)");
            tvKeyBadge.setTextColor(Color.parseColor("#10B981"));
        } else {
            tvKeyBadge.setText("已自动解密: 新版密钥 (AES-128-CBC)");
            tvKeyBadge.setTextColor(Color.parseColor("#06B6D4"));
        }
    }

    private void updateSectionList() {
        if (currentThermalFile == null) return;

        isUserSelectingSection = false;
        sectionNames.clear();

        int targetIndex = 0;
        List<ThermalSection> sections = currentThermalFile.getSections();
        for (int i = 0; i < sections.size(); i++) {
            ThermalSection s = sections.get(i);
            String title = "[" + s.getName() + "] (" + s.getBadgeSummary() + ")";
            sectionNames.add(title);
            // 默认优先选中第一个含档位的 section
            if (s.isTierBased() && targetIndex == 0) {
                targetIndex = i;
            }
        }
        sectionAdapter.notifyDataSetChanged();

        if (!sections.isEmpty()) {
            spSections.setSelection(targetIndex);
            switchSection(sections.get(targetIndex));
        }
        isUserSelectingSection = true;
    }

    private void switchSection(ThermalSection section) {
        currentSection = section;
        if (section == null) return;

        tvSectionBadge.setText(section.getBadgeSummary());

        if (section.isTierBased()) {
            layoutTierHeader.setVisibility(View.VISIBLE);
            rvTiers.setVisibility(View.VISIBLE);

            // 必须使用同一个 adapter 实例，同时提供给 Callback、ItemTouchHelper 与 RecyclerView
            tierAdapter = new TierAdapter(section);
            TierItemTouchHelperCallback callback = new TierItemTouchHelperCallback(tierAdapter);
            itemTouchHelper = new ItemTouchHelper(callback);
            itemTouchHelper.attachToRecyclerView(rvTiers);
            tierAdapter.setItemTouchHelper(itemTouchHelper);
            tierAdapter.setOnTierChangeListener(() -> {
                tvSectionBadge.setText(section.getBadgeSummary());
            });
            rvTiers.setAdapter(tierAdapter);
        } else {
            layoutTierHeader.setVisibility(View.GONE);
            rvTiers.setVisibility(View.GONE);
        }

        // 渲染基础参数区
        renderParams(section);
    }

    private void renderParams(ThermalSection section) {
        containerParams.removeAllViews();
        List<ThermalSection.ParamPair> params = section.getParams();

        if (params.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("本区域无附加参数");
            tvEmpty.setTextColor(Color.parseColor("#6B7280"));
            tvEmpty.setTextSize(11);
            containerParams.addView(tvEmpty);
            return;
        }

        for (ThermalSection.ParamPair p : params) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 4, 0, 4);

            TextView tvKey = new TextView(this);
            tvKey.setText(p.key);
            tvKey.setTextColor(Color.parseColor("#9CA3AF"));
            tvKey.setTextSize(12);
            tvKey.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

            EditText etVal = new EditText(this);
            etVal.setText(p.value);
            etVal.setTextColor(Color.parseColor("#E5E7EB"));
            etVal.setTextSize(12);
            etVal.setBackgroundResource(R.drawable.input_bg);
            etVal.setPadding(16, 8, 16, 8);
            etVal.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
            etVal.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    p.value = s.toString();
                }
            });

            row.addView(tvKey);
            row.addView(etVal);
            containerParams.addView(row);
        }
    }

    private void onAddTierClicked() {
        if (currentSection == null) return;

        ThermalTier newTier = new ThermalTier();
        List<ThermalTier> tiers = currentSection.getTiers();

        if (!tiers.isEmpty()) {
            ThermalTier last = tiers.get(tiers.size() - 1);
            try {
                long lastTrig = Long.parseLong(last.getTrig().trim());
                long lastClr = Long.parseLong(last.getClr().trim());
                long newTrig = lastTrig + 1000;
                long newClr = Math.min(newTrig - 1000, lastClr + 1000);
                newTier.setTrig(String.valueOf(newTrig));
                newTier.setClr(String.valueOf(newClr));
                newTier.setTarget(last.getTarget());
            } catch (Exception e) {
                newTier.setTrig("42000");
                newTier.setClr("40000");
                newTier.setTarget(last.getTarget());
            }
        } else {
            currentSection.setTierBased(true);
            newTier.setTrig("40000");
            newTier.setClr("38000");
            if (FrequencyManager.isGpuSection(currentSection)) {
                newTier.setTarget("5"); // Level 5 (768 MHz)
            } else {
                List<FrequencyManager.FreqOption> opts = FrequencyManager.getOptionsForSection(currentSection);
                if (!opts.isEmpty()) {
                    newTier.setTarget(opts.get(opts.size() / 2).valueToSave);
                } else {
                    newTier.setTarget("2400000");
                }
            }
        }

        currentSection.addTier(newTier);
        if (tierAdapter != null) {
            tierAdapter.notifyItemInserted(tiers.size() - 1);
            rvTiers.smoothScrollToPosition(tiers.size() - 1);
        }
        tvSectionBadge.setText(currentSection.getBadgeSummary());
        Toast.makeText(this, "已增加档位 #" + tiers.size() + "，可按住左侧手柄拖动排序", Toast.LENGTH_SHORT).show();
    }

    private void switchMode(boolean raw) {
        isRawMode = raw;
        if (raw) {
            // 切换到源码模式：先序列化当前可视化模型
            if (currentThermalFile != null) {
                String text = ThermalParser.serialize(currentThermalFile);
                etRawContent.setText(text);
            }
            layoutVisualMode.setVisibility(View.GONE);
            layoutRawMode.setVisibility(View.VISIBLE);
            btnTabVisual.setBackgroundResource(R.drawable.btn_secondary);
            btnTabVisual.setTextColor(Color.parseColor("#9CA3AF"));
            btnTabRaw.setBackgroundResource(R.drawable.btn_primary);
            btnTabRaw.setTextColor(Color.parseColor("#FFFFFF"));
        } else {
            // 切换回可视化模式：先从源码反解析
            String rawText = etRawContent.getText().toString();
            String currentFileName = (currentThermalFile != null) ? currentThermalFile.getFileName() : "thermal-normal.conf";
            ThermalCipher.KeyType keyType = (currentThermalFile != null) ? currentThermalFile.getKeyType() : ThermalCipher.KeyType.OLD;
            currentThermalFile = ThermalParser.parse(rawText, currentFileName, keyType);

            updateSectionList();
            layoutVisualMode.setVisibility(View.VISIBLE);
            layoutRawMode.setVisibility(View.GONE);
            btnTabVisual.setBackgroundResource(R.drawable.btn_primary);
            btnTabVisual.setTextColor(Color.parseColor("#FFFFFF"));
            btnTabRaw.setBackgroundResource(R.drawable.btn_secondary);
            btnTabRaw.setTextColor(Color.parseColor("#9CA3AF"));
        }
    }

    private void onSaveClicked() {
        if (currentThermalFile == null) {
            Toast.makeText(this, "无正在编辑的文件", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalContent;
        if (isRawMode) {
            finalContent = etRawContent.getText().toString();
        } else {
            finalContent = ThermalParser.serialize(currentThermalFile);
        }

        String fileName = currentThermalFile.getFileName();
        ThermalCipher.KeyType keyType = currentThermalFile.getKeyType();

        btnSave.setEnabled(false);
        btnSave.setText("正在加密保存...");

        new Thread(() -> {
            try {
                // 1. 自动加密 (使用该文件检测到的对应 AES 密钥)
                byte[] cipherBytes = ThermalCipher.encrypt(finalContent, keyType);

                // 2. 写入文件并保留备份
                boolean success = RootShell.writeFileBytes(fileName, cipherBytes, true);

                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存并加密应用");

                    if (success) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("保存成功！")
                                .setMessage("文件 " + fileName + " 已使用 " + keyType.getDisplayName()
                                        + " 加密并写回系统温控目录。\n\n原文件已自动备份为 " + fileName + ".bak。\n\n是否立即重启温控服务生效？")
                                .setPositiveButton("立即重启温控", (dialog, which) -> onRestartServiceClicked())
                                .setNegativeButton("稍后手动重启", null)
                                .show();
                    } else {
                        Toast.makeText(MainActivity.this, "写回失败，请检查 Root 权限或文件锁", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存并加密应用");
                    Toast.makeText(MainActivity.this, "加密失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void onRestartServiceClicked() {
        btnRestartService.setEnabled(false);
        btnRestartService.setText("重启中...");

        new Thread(() -> {
            boolean ok = RootShell.restartThermalService();
            runOnUiThread(() -> {
                btnRestartService.setEnabled(true);
                btnRestartService.setText("重启温控");
                if (ok) {
                    Toast.makeText(this, "mi_thermald 温控服务已成功重启并生效！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "重启温控服务未检测到进程，请检查 setprop 权限", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void onRestoreBakClicked() {
        if (currentThermalFile == null) return;
        String fileName = currentThermalFile.getFileName();
        String bakFile = fileName + ".bak";

        new AlertDialog.Builder(this)
                .setTitle("恢复备份")
                .setMessage("确定要从 " + bakFile + " 恢复原始温控配置吗？")
                .setPositiveButton("确定恢复", (dialog, which) -> {
                    new Thread(() -> {
                        String cmd = "if [ -f " + RootShell.THERMAL_CONFIG_DIR + "/" + bakFile + " ]; then\n"
                                + "cp -f " + RootShell.THERMAL_CONFIG_DIR + "/" + bakFile + " " + RootShell.THERMAL_CONFIG_DIR + "/" + fileName + "\n"
                                + "chmod 0771 " + RootShell.THERMAL_CONFIG_DIR + "/" + fileName + "\n"
                                + "chown root:system " + RootShell.THERMAL_CONFIG_DIR + "/" + fileName + "\n"
                                + "echo OK\n"
                                + "else echo MISS; fi\n";
                        RootShell.CommandResult res = RootShell.runSu(cmd);
                        runOnUiThread(() -> {
                            if (res.stdout.contains("OK")) {
                                Toast.makeText(this, "已恢复备份配置", Toast.LENGTH_SHORT).show();
                                loadFileAsync(fileName);
                            } else {
                                Toast.makeText(this, "备份文件不存在: " + bakFile, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

