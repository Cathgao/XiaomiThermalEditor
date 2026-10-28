package com.xiaomi.thermaleditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代表配置文件中的一个独立 [Section]
 */
public class ThermalSection {
    private String name;
    private boolean isTierBased = false;
    private final List<ThermalTier> tiers = new ArrayList<>();
    // 非档位参数 (如 algo_type, sensor, device, polling 等)
    private final List<ParamPair> params = new ArrayList<>();
    // 未识别的注释或原始空行
    private final List<String> rawComments = new ArrayList<>();

    public static class ParamPair {
        public String key;
        public String value;
        public String delimiter = "\t"; // 默认制表符

        public ParamPair(String key, String value, String delimiter) {
            this.key = key;
            this.value = value;
            this.delimiter = delimiter != null ? delimiter : "\t";
        }
    }

    public ThermalSection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isTierBased() {
        return isTierBased;
    }

    public void setTierBased(boolean tierBased) {
        isTierBased = tierBased;
    }

    public List<ThermalTier> getTiers() {
        return tiers;
    }

    public void addTier(ThermalTier tier) {
        tiers.add(tier);
    }

    public void removeTier(int index) {
        if (index >= 0 && index < tiers.size()) {
            tiers.remove(index);
        }
    }

    public List<ParamPair> getParams() {
        return params;
    }

    public void addParam(String key, String value, String delimiter) {
        params.add(new ParamPair(key, value, delimiter));
    }

    public void addRawComment(String line) {
        rawComments.add(line);
    }

    public List<String> getRawComments() {
        return rawComments;
    }

    public String getParamValue(String key) {
        for (ParamPair p : params) {
            if (p.key.equalsIgnoreCase(key)) {
                return p.value;
            }
        }
        return null;
    }

    public void setParamValue(String key, String value) {
        for (ParamPair p : params) {
            if (p.key.equalsIgnoreCase(key)) {
                p.value = value;
                return;
            }
        }
        params.add(new ParamPair(key, value, "\t"));
    }

    public String getBadgeSummary() {
        if (isTierBased) {
            return tiers.size() + " 档位";
        } else {
            return params.size() + " 项参数";
        }
    }
}

