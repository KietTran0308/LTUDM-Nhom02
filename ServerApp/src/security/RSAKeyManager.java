package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RSAKeyManager {

    private final Path publicKeyPath;
    private final Path privateKeyPath;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public RSAKeyManager(Path keyDirectory) throws Exception {

        Files.createDirectories(keyDirectory);

        publicKeyPath = keyDirectory.resolve("public.key");
        privateKeyPath = keyDirectory.resolve("private.key");

        loadOrCreateKeys();
    }

    // Kiểm tra khóa đã tồn tại hay chưa
    private void loadOrCreateKeys() throws Exception {

        boolean hasPublic = Files.exists(publicKeyPath);
        boolean hasPrivate = Files.exists(privateKeyPath);

        if (hasPublic && hasPrivate) {

            loadKeys();

        } else if (!hasPublic && !hasPrivate) {

            generateKeyPair();
            saveKeys();

        } else {

            throw new IOException("RSA Key Pair không đầy đủ!");
        }
    }

    // Tạo cặp khóa RSA
    private void generateKeyPair() throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048, new SecureRandom());

        KeyPair keyPair = generator.generateKeyPair();

        publicKey = keyPair.getPublic();
        privateKey = keyPair.getPrivate();
    }

    // Lưu khóa xuống file
    private void saveKeys() throws IOException {

        Files.write(publicKeyPath, publicKey.getEncoded());

        Files.write(privateKeyPath, privateKey.getEncoded());
    }

    // Đọc khóa từ file
    private void loadKeys() throws Exception {

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] publicBytes = Files.readAllBytes(publicKeyPath);
        byte[] privateBytes = Files.readAllBytes(privateKeyPath);

        publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(publicBytes)
        );

        privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(privateBytes)
        );
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}