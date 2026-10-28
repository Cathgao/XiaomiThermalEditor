package com.xiaomi.thermaleditor;

import android.util.Base64;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RootShell {
    private static final String TAG = "RootShell";
    public static final String THERMAL_CONFIG_DIR = "/data/vendor/thermal/config";

    public static class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static boolean isRootAvailable() {
        CommandResult result = runSu("id");
        return result.isSuccess() && result.stdout.contains("uid=0");
    }

    public static CommandResult runSu(String cmd) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader es = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            os.write((cmd + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            os.flush();

            String line;
            while ((line = is.readLine()) != null) {
                stdout.append(line).append("\n");
            }
            while ((line = es.readLine()) != null) {
                stderr.append(line).append("\n");
            }

            exitCode = process.waitFor();
            os.close();
            is.close();
            es.close();
        } catch (Exception e) {
            Log.e(TAG, "runSu failed: " + cmd, e);
            stderr.append(e.getMessage());
        }

        return new CommandResult(exitCode, stdout.toString().trim(), stderr.toString().trim());
    }

    /**
     * 列出 /data/vendor/thermal/config 目录下的所有 .conf 配置文件
     */
    public static List<String> listThermalFiles() {
        List<String> list = new ArrayList<>();
        CommandResult res = runSu("ls " + THERMAL_CONFIG_DIR + "/*.conf 2>/dev/null");
        if (res.isSuccess() && !res.stdout.isEmpty()) {
            String[] lines = res.stdout.split("\\n");
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty() && t.endsWith(".conf")) {
                    int slash = t.lastIndexOf('/');
                    String name = (slash >= 0) ? t.substring(slash + 1) : t;
                    list.add(name);
                }
            }
        }
        // 保证 thermal-normal.conf 排在第一位
        if (list.contains("thermal-normal.conf")) {
            list.remove("thermal-normal.conf");
            list.add(0, "thermal-normal.conf");
        }
        return list;
    }

    /**
     * 读取指定温控文件的完整原始字节 (通过 Base64 管道防止编码损坏)
     */
    public static byte[] readFileBytes(String fileName) {
        String fullPath = THERMAL_CONFIG_DIR + "/" + fileName;
        CommandResult res = runSu("base64 " + fullPath + " 2>/dev/null");
        if (!res.isSuccess() || res.stdout.isEmpty()) {
            Log.e(TAG, "readFileBytes failed: " + res.stderr);
            return null;
        }
        try {
            String b64 = res.stdout.replaceAll("\\s+", "");
            return Base64.decode(b64, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Base64 decode failed for " + fileName, e);
            return null;
        }
    }

    /**
     * 将数据写回指定温控文件并妥善设置权限和 SELinux 标签
     */
    public static boolean writeFileBytes(String fileName, byte[] data, boolean backup) {
        String fullPath = THERMAL_CONFIG_DIR + "/" + fileName;
        String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
        String tmpPath = "/data/local/tmp/.thermal_edit_tmp.bin";

        StringBuilder cmd = new StringBuilder();
        // 1. 解锁防写保护属性
        cmd.append("chattr -i ").append(THERMAL_CONFIG_DIR).append(" ")
           .append(fullPath).append(" 2>/dev/null\n");

        // 2. 备份原文件
        if (backup) {
            cmd.append("if [ -f \"").append(fullPath).append("\" ]; then cp -f \"")
               .append(fullPath).append("\" \"").append(fullPath).append(".bak\"; fi\n");
        }

        // 3. 写入临时文件并复制到目标位置
        cmd.append("echo \"").append(b64).append("\" | base64 -d > ").append(tmpPath).append("\n");
        cmd.append("cp -f ").append(tmpPath).append(" \"").append(fullPath).append("\"\n");
        cmd.append("rm -f ").append(tmpPath).append("\n");

        // 4. 恢复权限和所有者
        cmd.append("chmod 0771 \"").append(fullPath).append("\"\n");
        cmd.append("chown root:system \"").append(fullPath).append("\"\n");
        cmd.append("restorecon -DFR ").append(THERMAL_CONFIG_DIR).append(" 2>/dev/null\n");

        CommandResult res = runSu(cmd.toString());
        return res.isSuccess();
    }

    /**
     * 重启 mi_thermald 温控服务使修改即刻生效
     */
    public static boolean restartThermalService() {
        CommandResult res = runSu("setprop ctl.restart mi_thermald");
        if (!res.isSuccess()) {
            return false;
        }
        // 稍微等待并检查进程是否存活
        try {
            Thread.sleep(600);
        } catch (InterruptedException ignored) {}

        CommandResult pidRes = runSu("pidof mi_thermald");
        return pidRes.isSuccess() && !pidRes.stdout.trim().isEmpty();
    }
}

