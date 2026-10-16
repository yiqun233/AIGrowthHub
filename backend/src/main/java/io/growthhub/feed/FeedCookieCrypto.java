package io.growthhub.feed;

import io.growthhub.common.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class FeedCookieCrypto {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    public FeedCookieCrypto(Environment environment) { this.environment = environment; }

    public String encrypt(String plainText) {
        byte[] key = key();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) { throw new ApiException(500, "FEED_COOKIE_ENCRYPTION_FAILED", "Cookie 加密失败"); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    public String decrypt(String encryptedValue) {
        byte[] key = key();
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedValue);
            if (packed.length <= IV_BYTES) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            return new String(cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new ApiException(500, "FEED_COOKIE_DECRYPTION_FAILED", "Cookie 解密失败，请检查加密密钥"); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    private byte[] key() {
        String encoded = environment.getProperty("FEED_COOKIE_ENCRYPTION_KEY", "").strip();
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length == 32) return key;
        } catch (IllegalArgumentException ignored) {}
        throw new ApiException(503, "FEED_COOKIE_KEY_MISSING", "尚未配置有效的 FEED_COOKIE_ENCRYPTION_KEY");
    }
}
