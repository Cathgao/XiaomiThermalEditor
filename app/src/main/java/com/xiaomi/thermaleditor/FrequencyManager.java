package com.xiaomi.thermaleditor;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 频率管理器：完全基于真实硬件 sysfs 动态探测与缓存，拒绝任何机型假数据兜底
 */
public class FrequencyManager {
    private static final String TAG = "FrequencyManager";

    public static class FreqOption {
        public final String valueToSave; // 存入温控配置文件的真实值 (如 CPU 为 2745600, GPU 为 5)
        public final String mainLabel;   // 主显标题 (如 "2.75 GHz" 或 "768 MHz")
        public final String subLabel;    // 副标题/提示 (如 "2745600 kHz" 或 "Level 5")

        public FreqOption(String valueToSave, String mainLabel, String subLabel) {
            this.valueToSave = valueToSave;
            this.mainLabel = mainLabel;
            this.subLabel = subLabel;
        }

        public String getDialogDisplayText() {
            if (subLabel == null || subLabel.isEmpty()) {
                return mainLabel;
            }
            return mainLabel + " (" + subLabel + ")";
        }
    }

    public static class GpuLevelInfo {
        public final int level;
        public final int freqMhz;

        public GpuLevelInfo(int level, int freqMhz) {
            this.level = level;
            this.freqMhz = freqMhz;
        }
    }

    // 动态缓存的真实 CPU 频率表: 按 policy 与所属核心映射
    private static final Map<String, List<FreqOption>> CPU_FREQ_CACHE = new LinkedHashMap<>();

    // 动态缓存的真实 GPU 频率/档位表 (按 sysfs 实际层级解析)
    private static final List<GpuLevelInfo> GPU_FREQ_CACHE = new ArrayList<>();

    /**
     * 仅当获得 Root 权限后，实时读取当前设备的真实 sysfs 节点
     */
    public static synchronized void scanAvailableFrequencies() {
        scanCpuFrequencies();
        scanGpuFrequencies();
    }

    private static void scanCpuFrequencies() {
        CPU_FREQ_CACHE.clear();
        RootShell.CommandResult policyList = RootShell.runSu("ls -d /sys/devices/system/cpu/cpufreq/policy* 2>/dev/null");
        if (policyList.isSuccess() && !policyList.stdout.isEmpty()) {
            String[] policies = policyList.stdout.split("\\n");
            int clusterIndex = 0;
            for (String polPath : policies) {
                polPath = polPath.trim();
                if (polPath.isEmpty()) continue;

                String polName = polPath.substring(polPath.lastIndexOf('/') + 1); // e.g. policy0
                String freqsRaw = RootShell.runSu("cat " + polPath + "/scaling_available_frequencies 2>/dev/null").stdout;
                String cpusRaw = RootShell.runSu("cat " + polPath + "/affected_cpus " + polPath + "/related_cpus 2>/dev/null").stdout;

                List<FreqOption> options = parseCpuFreqLine(freqsRaw);
                if (!options.isEmpty()) {
                    CPU_FREQ_CACHE.put(polName.toLowerCase(), options);

                    // 映射该 policy 下所有关联的核心 ID (cpu0, cpu1... 等)
                    String[] cpus = cpusRaw.trim().split("\\s+");
                    for (String cpuId : cpus) {
                        cpuId = cpuId.trim();
                        if (!cpuId.isEmpty() && !cpuId.contains("/")) {
                            CPU_FREQ_CACHE.put("cpu" + cpuId, options);
                        }
                    }

                    // 兼容 cluster 命名
                    CPU_FREQ_CACHE.put("cpu-cluster" + clusterIndex, options);
                    CPU_FREQ_CACHE.put("cluster" + clusterIndex, options);
                    clusterIndex++;
                }
            }
        }

        Log.d(TAG, "Dynamic CPU scan finished: " + CPU_FREQ_CACHE.keySet().size() + " mappings found.");
    }

    private static List<FreqOption> parseCpuFreqLine(String line) {
        List<FreqOption> list = new ArrayList<>();
        if (line == null || line.trim().isEmpty()) return list;

        String[] parts = line.trim().split("\\s+");
        List<Long> freqs = new ArrayList<>();
        for (String p : parts) {
            try {
                long val = Long.parseLong(p.trim());
                if (val > 0 && !freqs.contains(val)) {
                    freqs.add(val);
                }
            } catch (Exception ignored) {}
        }

        // 统一降序排序：最高频置顶
        Collections.sort(freqs, Collections.reverseOrder());

        for (Long khz : freqs) {
            String val = String.valueOf(khz);
            String main = formatCpuKhz(khz);
            String sub = val + " kHz";
            list.add(new FreqOption(val, main, sub));
        }
        return list;
    }

    private static String formatCpuKhz(long khz) {
        float mhz = khz / 1000.0f;
        if (mhz >= 1000) {
            return String.format("%.2f GHz", mhz / 1000.0f);
        } else {
            return String.format("%.0f MHz", mhz);
        }
    }

    private static void scanGpuFrequencies() {
        GPU_FREQ_CACHE.clear();

        // 优先读取 Adreno 标准节点 freq_table_mhz
        RootShell.CommandResult mhzRes = RootShell.runSu("cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz 2>/dev/null");
        if (mhzRes.isSuccess() && !mhzRes.stdout.isEmpty()) {
            String[] parts = mhzRes.stdout.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                try {
                    int mhz = Integer.parseInt(parts[i].trim());
                    GPU_FREQ_CACHE.add(new GpuLevelInfo(i, mhz));
                } catch (Exception ignored) {}
            }
        }

        // 备用：尝试读取 gpu_available_frequencies (Hz)
        if (GPU_FREQ_CACHE.isEmpty()) {
            RootShell.CommandResult hzRes = RootShell.runSu("cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null");
            if (hzRes.isSuccess() && !hzRes.stdout.isEmpty()) {
                String[] parts = hzRes.stdout.trim().split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    try {
                        long hz = Long.parseLong(parts[i].trim());
                        int mhz = (int) (hz / 1000000L);
                        GPU_FREQ_CACHE.add(new GpuLevelInfo(i, mhz));
                    } catch (Exception ignored) {}
                }
            }
        }

        Log.d(TAG, "Dynamic GPU scan finished: " + GPU_FREQ_CACHE.size() + " levels detected.");
    }

    /**
     * 判断某个 Section 是否是 GPU 限制区域
     */
    public static boolean isGpuSection(ThermalSection section) {
        if (section == null) return false;
        String dev = section.getParamValue("device");
        if (dev != null && dev.equalsIgnoreCase("gpu")) {
            return true;
        }
        String name = section.getName().toUpperCase();
        return name.contains("GPU") || name.contains("KGSL");
    }

    /**
     * 判断某个 Section 是否是 CPU 限制区域
     */
    public static boolean isCpuSection(ThermalSection section) {
        if (section == null) return false;
        String dev = section.getParamValue("device");
        if (dev != null && (dev.toLowerCase().contains("cpu") || dev.toLowerCase().contains("cluster"))) {
            return true;
        }
        String name = section.getName().toUpperCase();
        return name.contains("CPU") || name.contains("SS-");
    }

    /**
     * 获取指定 Section 的真实可用频率列表
     * 如果当前硬件架构未探测到频率，返回空列表，由 UI 自动退回手动输入/通用选择，绝不伪造机型频率
     */
    public static List<FreqOption> getOptionsForSection(ThermalSection section) {
        if (isGpuSection(section)) {
            return getGpuOptions();
        }

        if (isCpuSection(section)) {
            String dev = section.getParamValue("device");
            if (dev == null) dev = "";
            dev = dev.toLowerCase().trim();

            // 1. 精确匹配 device 名 (如 cpu0, cpu6, cpu-cluster0...)
            if (CPU_FREQ_CACHE.containsKey(dev)) {
                return CPU_FREQ_CACHE.get(dev);
            }

            // 2. 模糊匹配 section 名中包含的 core 号
            for (Map.Entry<String, List<FreqOption>> entry : CPU_FREQ_CACHE.entrySet()) {
                if (dev.contains(entry.getKey()) || section.getName().toLowerCase().contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // 3. 如果有 CPU 频率缓存，按出现顺序作为备选
            if (!CPU_FREQ_CACHE.isEmpty()) {
                return CPU_FREQ_CACHE.values().iterator().next();
            }
        }

        return Collections.emptyList();
    }

    /**
     * 获取 GPU 档位选择项列表 (仅当真实读取到了硬件 GPU 档位时生效)
     */
    public static List<FreqOption> getGpuOptions() {
        List<FreqOption> list = new ArrayList<>();
        int count = GPU_FREQ_CACHE.size();
        for (int i = 0; i < count; i++) {
            GpuLevelInfo info = GPU_FREQ_CACHE.get(i);
            String levelVal = String.valueOf(info.level);
            String main = info.freqMhz + " MHz";
            String sub = "Level " + info.level + (i == 0 ? " - 最高" : (i == count - 1 ? " - 最低" : ""));
            list.add(new FreqOption(levelVal, main, sub));
        }
        return list;
    }

    /**
     * 将目标值翻译为卡片主显示文字
     * GPU: 仅当成功探测到真实 GPU 表时，才将 level 翻译为实际 MHz；否则如实显示原始值
     */
    public static String formatTargetDisplay(ThermalSection section, String rawTarget) {
        if (rawTarget == null || rawTarget.trim().isEmpty()) {
            return "选择频率";
        }
        rawTarget = rawTarget.trim();

        if (isGpuSection(section) && !GPU_FREQ_CACHE.isEmpty()) {
            try {
                int level = Integer.parseInt(rawTarget);
                for (GpuLevelInfo info : GPU_FREQ_CACHE) {
                    if (info.level == level) {
                        return info.freqMhz + " MHz";
                    }
                }
                if (level >= 100) {
                    return level + " MHz";
                }
            } catch (Exception ignored) {}
        }

        if (isCpuSection(section)) {
            try {
                long khz = Long.parseLong(rawTarget);
                if (khz >= 100000) {
                    return formatCpuKhz(khz);
                }
            } catch (Exception ignored) {}
        }

        return rawTarget;
    }

    /**
     * 将目标值翻译为卡片副提示文字
     */
    public static String formatTargetHint(ThermalSection section, String rawTarget) {
        if (rawTarget == null || rawTarget.trim().isEmpty()) {
            return "";
        }
        rawTarget = rawTarget.trim();

        if (isGpuSection(section) && !GPU_FREQ_CACHE.isEmpty()) {
            try {
                int level = Integer.parseInt(rawTarget);
                if (level < 30) {
                    return "Level " + level;
                } else {
                    for (GpuLevelInfo info : GPU_FREQ_CACHE) {
                        if (info.freqMhz == level) {
                            return "Level " + info.level;
                        }
                    }
                }
            } catch (Exception ignored) {}
            return "GPU 档位";
        }

        if (isCpuSection(section)) {
            try {
                long khz = Long.parseLong(rawTarget);
                if (khz > 10000) {
                    return khz + " kHz";
                }
            } catch (Exception ignored) {}
        }

        return "";
    }

    /**
     * 当保存 GPU Section 时，确保写入温控文件的是真正的 level 整数
     */
    public static String sanitizeGpuTargetForSave(String targetVal) {
        if (targetVal == null || targetVal.trim().isEmpty()) return "0";
        targetVal = targetVal.trim();
        if (GPU_FREQ_CACHE.isEmpty()) {
            return targetVal; // 未探测到 GPU 结构时不臆测修改
        }

        try {
            int num = Integer.parseInt(targetVal);
            if (num < 30) {
                return String.valueOf(num);
            }
            // 误填了 MHz 数值，反查对应的 Level
            for (GpuLevelInfo info : GPU_FREQ_CACHE) {
                if (info.freqMhz == num) {
                    return String.valueOf(info.level);
                }
            }
        } catch (Exception ignored) {}
        return targetVal;
    }
}
