package com.xiaomi.thermaleditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责温控配置文本的解析与序列化重建
 */
public class ThermalParser {

    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)\\]\\s*$");
    private static final Pattern KV_PATTERN = Pattern.compile("^([A-Za-z0-9_\\-]+)([\\s=\\t]+)([\\s\\S]*)$");

    public static ThermalFile parse(String text, String fileName, ThermalCipher.KeyType keyType) {
        ThermalFile file = new ThermalFile(fileName);
        file.setKeyType(keyType);

        if (text == null || text.isEmpty()) {
            return file;
        }

        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        file.setLineSeparator(eol);

        String[] lines = text.split("\\r?\\n");
        ThermalSection currentSection = null;
        List<String> currentLines = new ArrayList<>();

        for (String line : lines) {
            Matcher m = SECTION_PATTERN.matcher(line);
            if (m.matches()) {
                if (currentSection != null) {
                    processSectionLines(currentSection, currentLines);
                    file.addSection(currentSection);
                    currentLines.clear();
                } else if (!currentLines.isEmpty()) {
                    file.getHeaderComments().addAll(currentLines);
                    currentLines.clear();
                }
                currentSection = new ThermalSection(m.group(1).trim());
            } else {
                currentLines.add(line);
            }
        }

        if (currentSection != null) {
            processSectionLines(currentSection, currentLines);
            file.addSection(currentSection);
        } else if (!currentLines.isEmpty()) {
            file.getHeaderComments().addAll(currentLines);
        }

        return file;
    }

    private static void processSectionLines(ThermalSection section, List<String> rawLines) {
        Map<String, List<String>> multiValueMap = new LinkedHashMap<>();
        List<String[]> normalParams = new ArrayList<>();

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                section.addRawComment(line);
                continue;
            }

            Matcher m = KV_PATTERN.matcher(line);
            if (m.matches()) {
                String key = m.group(1);
                String sep = m.group(2);
                String val = m.group(3);

                // 判断是否是档位相关多值键 (如 trig, clr, target, thresholds, actions, action_info)
                if (isTierKey(key)) {
                    List<String> values = splitValues(val);
                    multiValueMap.put(key, values);
                } else {
                    normalParams.add(new String[]{key, val, sep});
                }
            } else {
                section.addRawComment(line);
            }
        }

        // 检查是否存在档位多值
        if (multiValueMap.containsKey("trig") || multiValueMap.containsKey("thresholds") || multiValueMap.containsKey("target")) {
            section.setTierBased(true);
            int tierCount = 0;
            if (multiValueMap.containsKey("trig")) {
                tierCount = Math.max(tierCount, multiValueMap.get("trig").size());
            }
            if (multiValueMap.containsKey("thresholds")) {
                tierCount = Math.max(tierCount, multiValueMap.get("thresholds").size());
            }
            if (multiValueMap.containsKey("target")) {
                tierCount = Math.max(tierCount, multiValueMap.get("target").size());
            }

            List<String> trigs = multiValueMap.get("trig");
            if (trigs == null) trigs = multiValueMap.get("thresholds");
            List<String> clrs = multiValueMap.get("clr");
            List<String> targets = multiValueMap.get("target");
            if (targets == null) targets = multiValueMap.get("action_info");

            for (int i = 0; i < tierCount; i++) {
                ThermalTier tier = new ThermalTier();
                if (trigs != null && i < trigs.size()) {
                    tier.setTrig(trigs.get(i));
                }
                if (clrs != null && i < clrs.size()) {
                    tier.setClr(clrs.get(i));
                }
                if (targets != null && i < targets.size()) {
                    tier.setTarget(targets.get(i));
                }

                // 其余多值键 (如 actions 等)
                for (Map.Entry<String, List<String>> entry : multiValueMap.entrySet()) {
                    String k = entry.getKey();
                    if (!k.equals("trig") && !k.equals("thresholds") && !k.equals("clr")
                            && !k.equals("target") && !k.equals("action_info")) {
                        if (i < entry.getValue().size()) {
                            tier.setExtra(k, entry.getValue().get(i));
                        }
                    }
                }
                section.addTier(tier);
            }
        }

        for (String[] param : normalParams) {
            section.addParam(param[0], param[1], param[2]);
        }
    }

    private static boolean isTierKey(String key) {
        String lower = key.toLowerCase();
        return lower.equals("trig") || lower.equals("clr") || lower.equals("target")
                || lower.equals("thresholds") || lower.equals("actions") || lower.equals("action_info");
    }

    private static List<String> splitValues(String val) {
        List<String> list = new ArrayList<>();
        if (val == null || val.trim().isEmpty()) return list;

        // 优先按制表符分割，若无制表符则按空格分割
        String[] parts;
        if (val.contains("\t")) {
            parts = val.split("\t+");
        } else {
            parts = val.trim().split("\\s+");
        }

        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }

    public static String serialize(ThermalFile file) {
        StringBuilder sb = new StringBuilder();
        String eol = file.getLineSeparator();

        // 头部注释
        for (String h : file.getHeaderComments()) {
            sb.append(h).append(eol);
        }

        for (ThermalSection sec : file.getSections()) {
            sb.append("[").append(sec.getName()).append("]").append(eol);

            // 基础属性 (algo_type, sensor, device, polling 等)
            for (ThermalSection.ParamPair p : sec.getParams()) {
                sb.append(p.key).append(p.delimiter).append(p.value).append(eol);
            }

            // 档位行 (trig, clr, target, extra)
            if (sec.isTierBased() && !sec.getTiers().isEmpty()) {
                List<ThermalTier> tiers = sec.getTiers();

                // trig 行
                boolean hasTrig = false;
                StringBuilder trigLine = new StringBuilder("trig");
                for (ThermalTier t : tiers) {
                    if (!t.getTrig().isEmpty()) {
                        hasTrig = true;
                    }
                    trigLine.append("\t").append(t.getTrig());
                }
                if (hasTrig) {
                    sb.append(trigLine).append(eol);
                }

                // clr 行
                boolean hasClr = false;
                StringBuilder clrLine = new StringBuilder("clr");
                for (ThermalTier t : tiers) {
                    if (!t.getClr().isEmpty()) {
                        hasClr = true;
                    }
                    clrLine.append("\t").append(t.getClr());
                }
                if (hasClr) {
                    sb.append(clrLine).append(eol);
                }

                // target 行
                boolean hasTarget = false;
                StringBuilder targetLine = new StringBuilder("target");
                boolean isGpu = FrequencyManager.isGpuSection(sec);
                for (ThermalTier t : tiers) {
                    if (!t.getTarget().isEmpty()) {
                        hasTarget = true;
                    }
                    String targetVal = isGpu
                            ? FrequencyManager.sanitizeGpuTargetForSave(t.getTarget())
                            : t.getTarget();
                    targetLine.append("\t").append(targetVal);
                }
                if (hasTarget) {
                    sb.append(targetLine).append(eol);
                }

                // 其他额外并行键
                Map<String, StringBuilder> extraLines = new LinkedHashMap<>();
                for (ThermalTier t : tiers) {
                    for (Map.Entry<String, String> e : t.getExtraKeys().entrySet()) {
                        extraLines.computeIfAbsent(e.getKey(), k -> new StringBuilder(k))
                                .append("\t").append(e.getValue());
                    }
                }
                for (StringBuilder exLine : extraLines.values()) {
                    sb.append(exLine).append(eol);
                }
            }

            // 注释保留
            for (String c : sec.getRawComments()) {
                sb.append(c).append(eol);
            }

            sb.append(eol); // section 之间的空行
        }

        return sb.toString();
    }
}

