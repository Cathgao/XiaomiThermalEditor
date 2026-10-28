package com.xiaomi.thermaleditor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ThermalCipher {

    public enum KeyType {
        OLD("经典密钥 (thermalopenssl.h)"),
        NEW("新版密钥 (AES-128-NEW)"),
        PLAIN("未加密 (明文)");

        private final String displayName;

        KeyType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 经典小米 / 高通温控密钥 (16 bytes ASCII)
    public static final byte[] KEY_OLD = "thermalopenssl.h".getBytes(StandardCharsets.UTF_8);

    // 新版小米温控密钥 (16 bytes AES-128)
    public static final byte[] KEY_NEW = new byte[] {
        (byte) 0x6b, (byte) 0xc1, (byte) 0xbe, (byte) 0xe2,
        (byte) 0x2e, (byte) 0x40, (byte) 0x9f, (byte) 0x96,
        (byte) 0xe9, (byte) 0x3d, (byte) 0x7e, (byte) 0x11,
        (byte) 0x73, (byte) 0x93, (byte) 0x17, (byte) 0x2a
    };

    public static class DecryptResult {
        public final String content;
        public final KeyType keyType;
        public final boolean wasEncrypted;

        public DecryptResult(String content, KeyType keyType, boolean wasEncrypted) {
            this.content = content;
            this.keyType = keyType;
            this.wasEncrypted = wasEncrypted;
        }
    }

    /**
     * 自动检测并解密温控配置文件
     */
    public static DecryptResult decrypt(byte[] data) throws GeneralSecurityException {
        if (data == null || data.length == 0) {
            return new DecryptResult("", KeyType.PLAIN, false);
        }

        // 1. 先尝试判断是否已经是明文
        if (isLikelyPlainText(data)) {
            String plainText = new String(data, StandardCharsets.UTF_8);
            return new DecryptResult(plainText, KeyType.PLAIN, false);
        }

        // 2. 尝试使用经典旧密钥解密 (AES-128-CBC, IV = KEY, PKCS5/7 padding)
        try {
            byte[] decrypted = decryptWithKey(data, KEY_OLD);
            if (decrypted != null && isLikelyDecryptedThermal(decrypted)) {
                String text = new String(decrypted, StandardCharsets.UTF_8);
                return new DecryptResult(text, KeyType.OLD, true);
            }
        } catch (Exception ignored) {
        }

        // 3. 尝试使用新版密钥解密
        try {
            byte[] decrypted = decryptWithKey(data, KEY_NEW);
            if (decrypted != null && isLikelyDecryptedThermal(decrypted)) {
                String text = new String(decrypted, StandardCharsets.UTF_8);
                return new DecryptResult(text, KeyType.NEW, true);
            }
        } catch (Exception ignored) {
        }

        // 4. 如果都不匹配但数据能转为文本且包含 '['
        try {
            String maybeText = new String(data, StandardCharsets.UTF_8);
            if (maybeText.contains("[")) {
                return new DecryptResult(maybeText, KeyType.PLAIN, false);
            }
        } catch (Exception ignored) {
        }

        throw new GeneralSecurityException("未能自动解密此温控文件，可能格式损坏或使用了未知密钥。");
    }

    /**
     * 加密温控文本
     */
    public static byte[] encrypt(String content, KeyType keyType) throws GeneralSecurityException {
        if (content == null) {
            content = "";
        }
        byte[] inputBytes = content.getBytes(StandardCharsets.UTF_8);

        if (keyType == KeyType.PLAIN) {
            return inputBytes;
        }

        byte[] key = (keyType == KeyType.NEW) ? KEY_NEW : KEY_OLD;
        return encryptWithKey(inputBytes, key);
    }

    private static byte[] decryptWithKey(byte[] cipherData, byte[] key) throws GeneralSecurityException {
        if (cipherData.length % 16 != 0) {
            // AES-CBC 要求密文长度为 16 的倍数
            return null;
        }
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(key); // 小米温控加密特征：IV 与 KEY 相同

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(cipherData);
    }

    private static byte[] encryptWithKey(byte[] plainData, byte[] key) throws GeneralSecurityException {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(key); // IV 与 KEY 相同

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(plainData);
    }

    private static boolean isLikelyPlainText(byte[] data) {
        if (data.length < 3) return false;
        // 检查前几个字符是否是 [BAT_SOC] 或注释或可打印字符
        int printable = 0;
        int checkLen = Math.min(data.length, 128);
        for (int i = 0; i < checkLen; i++) {
            byte b = data[i];
            if (b == '\n' || b == '\r' || b == '\t' || (b >= 32 && b <= 126)) {
                printable++;
            }
        }
        if ((float) printable / checkLen > 0.95f) {
            String sample = new String(data, 0, checkLen, StandardCharsets.UTF_8);
            return sample.trim().startsWith("[") || sample.contains("[") || sample.startsWith("#");
        }
        return false;
    }

    private static boolean isLikelyDecryptedThermal(byte[] decrypted) {
        if (decrypted == null || decrypted.length == 0) return false;
        String sample = new String(decrypted, 0, Math.min(decrypted.length, 256), StandardCharsets.UTF_8);
        return sample.contains("[") || sample.contains("algo_type") || sample.contains("sensor") || sample.contains("polling");
    }
}

