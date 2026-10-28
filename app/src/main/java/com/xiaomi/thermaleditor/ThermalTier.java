package com.xiaomi.thermaleditor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代表温控规则中的一个档位 (Tier)
 * 在温控文件中对应 trig、clr、target (或 thresholds/actions/action_info) 中的同一列
 */
public class ThermalTier {
    private String trig = "";
    private String clr = "";
    private String target = "";
    // 保存可能存在的其他并行键值对 (如 action_info, devices 等)
    private final Map<String, String> extraKeys = new LinkedHashMap<>();

    public ThermalTier() {
    }

    public ThermalTier(String trig, String clr, String target) {
        this.trig = trig != null ? trig.trim() : "";
        this.clr = clr != null ? clr.trim() : "";
        this.target = target != null ? target.trim() : "";
    }

    public String getTrig() {
        return trig;
    }

    public void setTrig(String trig) {
        this.trig = trig != null ? trig.trim() : "";
    }

    public String getClr() {
        return clr;
    }

    public void setClr(String clr) {
        this.clr = clr != null ? clr.trim() : "";
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target != null ? target.trim() : "";
    }

    public Map<String, String> getExtraKeys() {
        return extraKeys;
    }

    public void setExtra(String key, String value) {
        extraKeys.put(key, value);
    }

    public String getExtra(String key) {
        return extraKeys.get(key);
    }

    /**
     * 将数值温度转换为易读的摄氏度字符串 (例如 40000 -> 40.0°C)
     */
    public static String formatTempHint(String val) {
        if (val == null || val.isEmpty()) return "";
        try {
            long num = Long.parseLong(val.trim());
            if (num > 1000 && num < 200000) {
                float deg = num / 1000.0f;
                return String.format("%.1f°C", deg);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 将频率转换为易读的 GHz / MHz 格式 (例如 2745600 -> 2.75 GHz)
     */
    public static String formatFreqHint(String val) {
        if (val == null || val.isEmpty()) return "";
        try {
            long freq = Long.parseLong(val.trim());
            if (freq >= 100000) {
                // 通常为 kHz
                float mhz = freq / 1000.0f;
                if (mhz >= 1000) {
                    return String.format("%.2f GHz", mhz / 1000.0f);
                } else {
                    return String.format("%.0f MHz", mhz);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public ThermalTier copy() {
        ThermalTier copy = new ThermalTier(this.trig, this.clr, this.target);
        copy.extraKeys.putAll(this.extraKeys);
        return copy;
    }
}

