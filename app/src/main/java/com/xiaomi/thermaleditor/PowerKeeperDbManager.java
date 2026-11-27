package com.xiaomi.thermaleditor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 负责解码与修改 PowerKeeper 的 user_configure.db 温控应用规则，
 * 并联动解码 Thermal.xml 与 thermal-odm-map.conf / thermal-map.conf 展示每条规则对应的温控文件。
 */
public class PowerKeeperDbManager {

    private static final String TAG = "PowerKeeperDbManager";
    public static final String POWERKEEPER_PKG = "com.miui.powerkeeper";
    public static final String DB_PATH = "/data/data/com.miui.powerkeeper/databases/user_configure.db";
    public static final String DB_BAK_PATH = "/data/data/com.miui.powerkeeper/databases/user_configure.db.bak";
    public static final String THERMAL_XML_PATH = "/data/data/com.miui.powerkeeper/shared_prefs/Thermal.xml";

    private static final String KEY_THERMAL_GROUP = "thermal_group";
    private static final String KEY_OPTI_GAME = "key_optimize_game_names";
    private static final String KEY_VIDEOCHAT = "thermal_videochat_config_enable_params2";

    public static class LoadResult {
        public final List<ThermalRule> rules;
        public final Map<Integer, String> idToConfMap;
        public final String errorMessage;

        public LoadResult(List<ThermalRule> rules, Map<Integer, String> idToConfMap, String errorMessage) {
            this.rules = rules;
            this.idToConfMap = idToConfMap;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 解码数据库与系统温控映射表，加载全部温控规则及对应包列表
     */
    public static LoadResult loadRules(Context context) {
        Map<Integer, String> idToConf = loadThermalIdToConfMap();
        Map<String, RuleScenarioInfo> ruleScenarioMap = loadScenarioRuleInfo(idToConf);

        File tempDb = copyDbToCache(context);
        if (tempDb == null || !tempDb.exists()) {
            return new LoadResult(new ArrayList<>(), idToConf, "无法读取 user_configure.db，请检查 Root 权限或系统版本");
        }

        List<ThermalRule> rules = new ArrayList<>();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(tempDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

            String thermalGroupJson = queryMiscValue(db, KEY_THERMAL_GROUP);
            if (thermalGroupJson != null && !thermalGroupJson.trim().isEmpty()) {
                JSONObject obj = new JSONObject(thermalGroupJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String csv = obj.optString(key, "");
                    RuleScenarioInfo info = resolveRuleInfo(key, ruleScenarioMap);
                    ThermalRule rule = new ThermalRule(
                            key,
                            getFriendlyRuleTitle(key),
                            ThermalRule.SourceType.THERMAL_GROUP,
                            info.primaryConf,
                            info.summary
                    );
                    parseCsvIntoRule(csv, rule);
                    rules.add(rule);
                }
            }

            // 读取独立专项规则（优先级高于普通 thermal_group）
            String optiGameCsv = queryMiscValue(db, KEY_OPTI_GAME);
            if (optiGameCsv != null) {
                RuleScenarioInfo info = resolveRuleInfo("optiGame", ruleScenarioMap);
                ThermalRule optiRule = new ThermalRule(
                        KEY_OPTI_GAME,
                        getFriendlyRuleTitle(KEY_OPTI_GAME),
                        ThermalRule.SourceType.MISC_CSV,
                        info.primaryConf,
                        info.summary + " [专项优先]"
                );
                parseCsvIntoRule(optiGameCsv, optiRule);
                rules.add(optiRule);
            }

            String videochatCsv = queryMiscValue(db, KEY_VIDEOCHAT);
            if (videochatCsv != null) {
                RuleScenarioInfo info = resolveRuleInfo("videochatOrCamera", ruleScenarioMap);
                ThermalRule vcRule = new ThermalRule(
                        KEY_VIDEOCHAT,
                        getFriendlyRuleTitle(KEY_VIDEOCHAT),
                        ThermalRule.SourceType.MISC_CSV,
                        info.primaryConf,
                        info.summary + " [专项优先]"
                );
                parseCsvIntoRule(videochatCsv, vcRule);
                rules.add(vcRule);
            }

            return new LoadResult(rules, idToConf, null);
        } catch (Exception e) {
            Log.e(TAG, "loadRules failed", e);
            return new LoadResult(new ArrayList<>(), idToConf, "解码数据库失败: " + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 将修改后的全部规则写回 PowerKeeper 数据库（自动备份原数据库）
     */
    public static boolean saveRules(Context context, List<ThermalRule> rules) {
        File tempDb = copyDbToCache(context);
        if (tempDb == null || !tempDb.exists()) {
            return false;
        }

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(tempDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.beginTransaction();

            // 1. 组装 thermal_group JSON（保留原有其他未知键，避免误删）
            String existingJson = queryMiscValue(db, KEY_THERMAL_GROUP);
            JSONObject thermalGroupObj = (existingJson != null && !existingJson.trim().isEmpty())
                    ? new JSONObject(existingJson)
                    : new JSONObject();

            for (ThermalRule rule : rules) {
                if (rule.sourceType == ThermalRule.SourceType.THERMAL_GROUP) {
                    thermalGroupObj.put(rule.ruleKey, rule.toCommaSeparated());
                } else if (rule.sourceType == ThermalRule.SourceType.MISC_CSV) {
                    upsertMiscValue(db, rule.ruleKey, rule.toCommaSeparated());
                }
            }
            upsertMiscValue(db, KEY_THERMAL_GROUP, thermalGroupObj.toString());

            db.setTransactionSuccessful();
            db.endTransaction();
            db.close();
            db = null;

            return writeCacheDbBackToSystem(tempDb);
        } catch (Exception e) {
            Log.e(TAG, "saveRules failed", e);
            if (db != null) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                    if (db.isOpen()) db.close();
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    /**
     * 从 user_configure.db.bak 恢复备份数据库
     */
    public static boolean restoreBackup() {
        String cmd = "if [ -f \"" + DB_BAK_PATH + "\" ]; then\n"
                + "cp -f \"" + DB_BAK_PATH + "\" \"" + DB_PATH + "\"\n"
                + "rm -f \"" + DB_PATH + "-journal\" \"" + DB_PATH + "-wal\" \"" + DB_PATH + "-shm\"\n"
                + "chown system:system \"" + DB_PATH + "\"\n"
                + "chmod 0660 \"" + DB_PATH + "\"\n"
                + "restorecon \"" + DB_PATH + "\" 2>/dev/null\n"
                + "echo OK\n"
                + "else echo MISS; fi";
        RootShell.CommandResult res = RootShell.runSuGlobal(cmd);
        return res.isSuccess() && res.stdout.contains("OK");
    }

    /**
     * 重启电量与性能 (com.miui.powerkeeper) 使修改后的数据库立即重新加载
     */
    public static boolean restartPowerKeeper() {
        String cmd = "killall " + POWERKEEPER_PKG + " 2>/dev/null || kill -9 $(pidof " + POWERKEEPER_PKG + ") 2>/dev/null\n"
                + "sleep 1\n"
                + "am start-service " + POWERKEEPER_PKG + "/.thermal.ThermalService >/dev/null 2>&1\n"
                + "sleep 1\n"
                + "pidof " + POWERKEEPER_PKG;
        RootShell.CommandResult res = RootShell.runSuGlobal(cmd);
        return res.isSuccess() && !res.stdout.trim().isEmpty();
    }

    private static File copyDbToCache(Context context) {
        File cacheDir = context.getCacheDir();
        File localDb = new File(cacheDir, "user_configure_work.db");
        File localJournal = new File(cacheDir, "user_configure_work.db-journal");

        if (localDb.exists()) localDb.delete();
        if (localJournal.exists()) localJournal.delete();

        byte[] dbBytes = readAnyFileBytes(DB_PATH);
        if (dbBytes == null || dbBytes.length == 0) {
            Log.e(TAG, "copyDbToCache failed: cannot read " + DB_PATH);
            return null;
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localDb)) {
            fos.write(dbBytes);
        } catch (Exception e) {
            Log.e(TAG, "copyDbToCache write local failed", e);
            return null;
        }

        byte[] journalBytes = readAnyFileBytes(DB_PATH + "-journal");
        if (journalBytes != null && journalBytes.length > 0) {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localJournal)) {
                fos.write(journalBytes);
            } catch (Exception ignored) {}
        }
        return localDb;
    }

    private static boolean writeCacheDbBackToSystem(File localDb) {
        if (localDb == null || !localDb.exists()) return false;
        byte[] dbBytes;
        try {
            dbBytes = java.nio.file.Files.readAllBytes(localDb.toPath());
        } catch (Exception e) {
            Log.e(TAG, "writeCacheDbBackToSystem read local failed", e);
            return false;
        }
        String b64 = Base64.encodeToString(dbBytes, Base64.DEFAULT);
        String tmpPath = "/data/local/tmp/.user_configure_edit.db";

        String cmd = "cat <<'__THERMAL_DB_EOF__' | base64 -d > \"" + tmpPath + "\"\n"
                + b64 + "\n"
                + "__THERMAL_DB_EOF__\n"
                + "if [ ! -s \"" + tmpPath + "\" ]; then rm -f \"" + tmpPath + "\"; exit 1; fi\n"
                + "if [ -f \"" + DB_PATH + "\" ]; then cp -f \"" + DB_PATH + "\" \"" + DB_BAK_PATH + "\"; chown system:system \"" + DB_BAK_PATH + "\"; chmod 0660 \"" + DB_BAK_PATH + "\"; fi\n"
                + "cp -f \"" + tmpPath + "\" \"" + DB_PATH + "\"\n"
                + "rm -f \"" + tmpPath + "\" \"" + DB_PATH + "-journal\" \"" + DB_PATH + "-wal\" \"" + DB_PATH + "-shm\"\n"
                + "chown system:system \"" + DB_PATH + "\"\n"
                + "chmod 0660 \"" + DB_PATH + "\"\n"
                + "restorecon \"" + DB_PATH + "\" 2>/dev/null\n"
                + "echo OK";
        RootShell.CommandResult res = RootShell.runSuGlobal(cmd);
        return res.isSuccess() && res.stdout.contains("OK");
    }

    private static String queryMiscValue(SQLiteDatabase db, String key) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT value FROM misc WHERE name = ? LIMIT 1", new String[]{key});
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private static void upsertMiscValue(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("value", value);
        int updated = db.update("misc", cv, "name = ?", new String[]{key});
        if (updated == 0) {
            cv.put("userId", 0);
            cv.put("name", key);
            db.insert("misc", null, cv);
        }
    }

    private static void parseCsvIntoRule(String csv, ThermalRule rule) {
        if (csv == null || csv.trim().isEmpty()) return;
        String[] parts = csv.split(",");
        for (String p : parts) {
            String pkg = p.trim();
            if (!pkg.isEmpty()) {
                rule.addPackage(pkg);
            }
        }
    }

    /**
     * 解密并解析 /data/vendor/thermal/config/thermal-map.conf -> /odm/etc/thermal-odm-map.conf -> /vendor/etc/thermal-map.conf
     */
    private static Map<Integer, String> loadThermalIdToConfMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        String[] paths = {
                "/vendor/etc/thermal-map.conf",
                "/odm/etc/thermal-odm-map.conf",
                "/data/vendor/thermal/config/thermal-map.conf"
        };
        Pattern linePattern = Pattern.compile("\\[(\\d+):([^\\]]+)\\]");

        for (String path : paths) {
            byte[] raw = readAnyFileBytes(path);
            if (raw == null || raw.length == 0) continue;
            try {
                ThermalCipher.DecryptResult dec = ThermalCipher.decrypt(raw);
                String[] lines = dec.content.split("\\r?\\n");
                for (String line : lines) {
                    Matcher m = linePattern.matcher(line.trim());
                    if (m.find()) {
                        int id = Integer.parseInt(m.group(1));
                        String conf = m.group(2).trim();
                        map.put(id, conf);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to decrypt thermal map: " + path, e);
            }
        }
        return map;
    }

    private static class RuleScenarioInfo {
        final String primaryConf;
        final String summary;

        RuleScenarioInfo(String primaryConf, String summary) {
            this.primaryConf = primaryConf;
            this.summary = summary;
        }
    }

    /**
     * 解析 PowerKeeper 的 Thermal.xml 中的 setting_dto，获取规则名对应的主温控文件与 sconfig 摘要
     */
    private static Map<String, RuleScenarioInfo> loadScenarioRuleInfo(Map<Integer, String> idToConf) {
        Map<String, RuleScenarioInfo> result = new HashMap<>();
        byte[] xmlBytes = readAnyFileBytes(THERMAL_XML_PATH);
        if (xmlBytes == null || xmlBytes.length == 0) {
            return result;
        }
        try {
            String xml = new String(xmlBytes, StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("<string\\s+name=\"setting_dto\">(.*?)</string>", Pattern.DOTALL);
            Matcher m = p.matcher(xml);
            if (!m.find()) return result;

            String jsonStr = m.group(1)
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
            JSONObject dto = new JSONObject(jsonStr);
            JSONArray scenarios = dto.optJSONArray("scenarios");
            if (scenarios == null) return result;

            Map<String, List<String>> normalMatches = new HashMap<>();
            Map<String, List<String>> perfMatches = new HashMap<>();
            Map<String, String> primaryConfs = new HashMap<>();

            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject s = scenarios.getJSONObject(i);
                int sid = s.optInt("id", -1);
                String sname = s.optString("scenario_name", "");
                JSONArray def = s.optJSONArray("define");
                if (def == null || def.length() < 9 || sid < 0 || sid >= 200) continue;

                int powerMode = def.optInt(7, 0);
                int iec = def.optInt(8, 0);
                if (iec == 1) continue; // 忽略 iec 衍生分支，保持摘要简洁

                String confName = idToConf.get(sid);
                String entry = (confName != null) ? (sid + " → " + confName) : ("sconfig=" + sid);

                // 去掉 per- / tgame~per- 前缀归并到基础场景名
                String baseKey = normalizeScenarioName(sname);
                if (powerMode == 98) {
                    if (sid == 69) entry = "69/18 → thermal-tgame.conf";
                    addUnique(perfMatches, baseKey, entry);
                } else {
                    addUnique(normalMatches, baseKey, entry);
                    // 对 videochatOrCamera，优先取专属场景 (非 ~ 复合回退分支) 的温控文件名作为主配置
                    if (confName != null && (!sname.contains("~") || !primaryConfs.containsKey(baseKey))) {
                        primaryConfs.put(baseKey, confName);
                    }
                }
            }

            for (String key : normalMatches.keySet()) {
                StringBuilder sb = new StringBuilder("常规: ");
                sb.append(String.join(", ", normalMatches.get(key)));
                if (perfMatches.containsKey(key)) {
                    sb.append(" | 性能: ").append(String.join(", ", perfMatches.get(key)));
                }
                String primaryConf = primaryConfs.containsKey(key) ? primaryConfs.get(key) : key;
                result.put(key, new RuleScenarioInfo(primaryConf, sb.toString()));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse Thermal.xml", e);
        }
        return result;
    }

    private static String normalizeScenarioName(String sname) {
        if (sname == null) return "";
        String key = sname;
        if (key.contains("~")) {
            key = key.substring(key.indexOf('~') + 1);
        }
        if (key.startsWith("per-")) {
            key = key.substring(4);
        }
        if ("nolimits".equalsIgnoreCase(key) || "noLimits".equalsIgnoreCase(key)) {
            return "evaluation";
        }
        return key;
    }

    private static void addUnique(Map<String, List<String>> map, String key, String val) {
        List<String> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        if (!list.contains(val)) {
            list.add(val);
        }
    }

    private static RuleScenarioInfo resolveRuleInfo(String ruleKey, Map<String, RuleScenarioInfo> ruleScenarioMap) {
        if (ruleScenarioMap.containsKey(ruleKey)) {
            return ruleScenarioMap.get(ruleKey);
        }
        switch (ruleKey) {
            case "evaluation":
                return new RuleScenarioInfo("thermal-nolimits.conf", "常规: 6 → thermal-nolimits.conf");
            case "game":
            case "pubg":
            case "game2":
            case "optiGame":
                return new RuleScenarioInfo("thermal-mgame.conf", "常规: 19 → thermal-mgame.conf | 性能: 18 → thermal-tgame.conf");
            case "class0":
                return new RuleScenarioInfo("thermal-class0.conf", "常规: 7 → thermal-class0.conf | 性能: 57 → thermal-per-class0.conf");
            case "video":
                return new RuleScenarioInfo("thermal-video.conf", "常规: 11 → thermal-video.conf | 性能: 61 → thermal-per-video.conf");
            case "navigation":
                return new RuleScenarioInfo("thermal-navigation.conf", "常规: 10 → thermal-navigation.conf | 性能: 60 → thermal-per-navigation.conf");
            case "camera":
                return new RuleScenarioInfo("thermal-camera.conf", "常规: 15 → thermal-camera.conf");
            case "arvr":
                return new RuleScenarioInfo("thermal-arvr.conf", "常规: 9 → thermal-arvr.conf");
            case "huanji":
                return new RuleScenarioInfo("thermal-huanji.conf", "常规: 1 → thermal-huanji.conf");
            case "demo":
                return new RuleScenarioInfo("thermal-demo.conf", "常规: 12 → thermal-demo.conf");
            case "videochatOrCamera":
                return new RuleScenarioInfo("thermal-videochat.conf", "常规: 14 → thermal-videochat.conf / 7 → thermal-class0.conf");
            default:
                return new RuleScenarioInfo(ruleKey, "规则键: " + ruleKey);
        }
    }

    private static String getFriendlyRuleTitle(String key) {
        switch (key) {
            case "evaluation":
                return "不限温控 / 跑分 (evaluation)";
            case "game":
                return "常规游戏 (game)";
            case "pubg":
                return "射击游戏 (pubg)";
            case "game2":
                return "重度游戏 2 (game2)";
            case KEY_OPTI_GAME:
                return "专项优化游戏 (optiGame)";
            case "class0":
                return "日常主流应用 (class0)";
            case "video":
                return "视频与直播 (video)";
            case KEY_VIDEOCHAT:
                return "视频通话专项 (videochat)";
            case "navigation":
                return "地图导航出行 (navigation)";
            case "camera":
                return "相机与图库 (camera)";
            case "arvr":
                return "AR / VR 场景 (arvr)";
            case "huanji":
                return "换机迁移 (huanji)";
            case "demo":
                return "样机演示 (demo)";
            default:
                return key;
        }
    }

    private static byte[] readAnyFileBytes(String fullPath) {
        RootShell.CommandResult res = RootShell.runSuGlobal("if [ -f \"" + fullPath + "\" ]; then base64 \"" + fullPath + "\" 2>/dev/null; fi");
        if (!res.isSuccess() || res.stdout.isEmpty()) {
            return null;
        }
        try {
            return Base64.decode(res.stdout.replaceAll("\\s+", ""), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }
}
