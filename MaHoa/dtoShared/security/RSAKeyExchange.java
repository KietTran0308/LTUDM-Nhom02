package security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.OAEPParameterSpec;

import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;


import javax.crypto.spec.PSource;

import java.util.Base64;

public class RSAKeyExchange {

    // Tạo AES Session Key
    public static SecretKey generateAESKey() throws Exception {

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

        keyGenerator.init(256, new SecureRandom());

        return keyGenerator.generateKey();
    }

    // Mã hóa AES Key bằng RSA Public Key
    public static String encryptAESKey(
            SecretKey aesKey,
            PublicKey publicKey) throws Exception {

        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        );

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);

        byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());

        return Base64.getEncoder().encodeToString(encryptedKey);
    }

    // Giải mã AES Key bằng RSA Private Key
    public static SecretKey decryptAESKey(
            String encryptedAESKey,
            PrivateKey privateKey) throws Exception {

        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        );

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);

        byte[] encryptedBytes = Base64.getDecoder()
                .decode(encryptedAESKey);

        byte[] decryptedKey = cipher.doFinal(encryptedBytes);

        return new SecretKeySpec(decryptedKey, "AES");
    }

    // Chuyển Public Key thành chuỗi Base64
    public static String publicKeyToString(PublicKey publicKey) {

        return Base64.getEncoder()
                .encodeToString(publicKey.getEncoded());
    }

        // Chuyển chuỗi Base64 nhận được thành Public Key
    public static PublicKey stringToPublicKey(String publicKeyString)
            throws Exception {

        byte[] keyBytes = Base64.getDecoder()
                .decode(publicKeyString);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(
                new X509EncodedKeySpec(keyBytes)
        );
    }
}