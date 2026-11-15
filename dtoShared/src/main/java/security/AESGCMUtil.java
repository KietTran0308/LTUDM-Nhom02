package security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

public class AESGCMUtil {

    // AES-GCM sử dụng nonce 12 bytes
    private static final int NONCE_LENGTH = 12;

    // Authentication tag 128 bits
    private static final int TAG_LENGTH = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Mã hóa dữ liệu bằng AES-GCM.
     *
     * Kết quả trả về:
     * [12 bytes nonce] + [ciphertext + authentication tag]
     */
    public static byte[] encrypt(byte[] plainText, SecretKey key)
            throws Exception {

        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(TAG_LENGTH, nonce);

        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] cipherText = cipher.doFinal(plainText);

        // Ghép nonce và ciphertext thành một mảng byte
        byte[] result = new byte[nonce.length + cipherText.length];

        System.arraycopy(nonce, 0, result, 0, nonce.length);

        System.arraycopy(
                cipherText, 0,
                result, nonce.length,
                cipherText.length
        );

        return result;
    }

    /**
     * Giải mã dữ liệu AES-GCM.
     *
     * Tách nonce và ciphertext từ dữ liệu nhận được.
     */
    public static byte[] decrypt(byte[] encryptedData, SecretKey key)
            throws Exception {

        if (encryptedData == null ||
                encryptedData.length <= NONCE_LENGTH) {
            throw new IllegalArgumentException("Dữ liệu mã hóa không hợp lệ");
        }

        // Tách nonce
        byte[] nonce = Arrays.copyOfRange(
                encryptedData, 0, NONCE_LENGTH
        );

        // Tách ciphertext và authentication tag
        byte[] cipherText = Arrays.copyOfRange(
                encryptedData, NONCE_LENGTH, encryptedData.length
        );

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(TAG_LENGTH, nonce);

        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        // Nếu dữ liệu bị chỉnh sửa hoặc sai khóa,
        // quá trình giải mã sẽ thất bại.
        return cipher.doFinal(cipherText);
    }
}