package com.xiaomi.thermaleditor;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示一条温控应用分组规则（来自 PowerKeeper 数据库的 thermal_group 或专项规则键）
 */
public class ThermalRule {

    public enum SourceType {
        THERMAL_GROUP,
        MISC_CSV
    }

    public final String ruleKey;
    public final String displayName;
    public final SourceType sourceType;
    public String primaryConf;
    public String sconfigSummary;
    public final List<String> packages = new ArrayList<>();

    public ThermalRule(String ruleKey, String displayName, SourceType sourceType,
                       String primaryConf, String sconfigSummary) {
        this.ruleKey = ruleKey;
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.primaryConf = primaryConf;
        this.sconfigSummary = sconfigSummary;
    }

    public boolean containsPackage(String pkg) {
        return packages.contains(pkg);
    }

    public void addPackage(String pkg) {
        if (pkg != null && !pkg.trim().isEmpty() && !packages.contains(pkg.trim())) {
            packages.add(pkg.trim());
        }
    }

    public boolean removePackage(String pkg) {
        return packages.remove(pkg);
    }

    public String toCommaSeparated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(packages.get(i));
        }
        return sb.toString();
    }
}
