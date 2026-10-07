package com.kyc.services;

import com.kyc.config.KycProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KycProperties properties;

    public SecretCipher(KycProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] key = aesKey();
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] key = aesKey();
            byte[] all = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[12];
            byte[] payload = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, payload, 0, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    private byte[] aesKey() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(properties.ipHashPepper().getBytes(StandardCharsets.UTF_8));
    }
}
