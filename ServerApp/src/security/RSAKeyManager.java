package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Luu RSA private key tren dia, chi cho phep tai khoan OS dang chay Server doc/ghi. */
public class RSAKeyManager {
    private static final Set<PosixFilePermission> OWNER_DIR =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_FILE =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path publicKeyPath;
    private final Path privateKeyPath;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public RSAKeyManager(Path keyDirectory) throws Exception {
        Path directory = keyDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(directory)) throw new IOException("Thu muc khoa khong duoc la symbolic link");
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Duong dan luu key khong la thu muc an toan");
        }
        restrictOwner(directory, true);
        publicKeyPath = directory.resolve("public.key");
        privateKeyPath = directory.resolve("private.key");
        loadOrCreateKeys();
    }

    private void loadOrCreateKeys() throws Exception {
        boolean hasPublic = Files.exists(publicKeyPath, LinkOption.NOFOLLOW_LINKS);
        boolean hasPrivate = Files.exists(privateKeyPath, LinkOption.NOFOLLOW_LINKS);
        if (hasPublic && hasPrivate) {
            ensureRegularKeyFile(publicKeyPath);
            ensureRegularKeyFile(privateKeyPath);
            restrictOwner(privateKeyPath, false); // Sua quyen file cu neu qua rong.
            loadKeys();
            checkKeyPair();
        } else if (!hasPublic && !hasPrivate) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair keys = generator.generateKeyPair();
            publicKey = keys.getPublic();
            privateKey = keys.getPrivate();
            // Private key duoc tao VOI quyen owner-only, khong tao file rong cong khai truoc.
            Files.write(privateKeyPath, privateKey.getEncoded(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            restrictOwner(privateKeyPath, false);
            Files.write(publicKeyPath, publicKey.getEncoded(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            checkKeyPair();
        } else {
            throw new IOException("RSA Key Pair khong day du; khong tu dong ghi de khoa cu");
        }
    }

    private static void ensureRegularKeyFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("RSA key path khong an toan: " + path);
        }
    }

    private static void restrictOwner(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Khong cho phep symbolic link: " + path);
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, directory ? OWNER_DIR : OWNER_FILE);
        } else {
            // Windows/NTFS: thay ACL bang ACL chi danh cho chu so huu file/thu muc.
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (acl == null) throw new IOException("File system khong ho tro quyen file an toan");
            AclEntry ownerOnly = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            acl.setAcl(List.of(ownerOnly));
        }
    }

    private void loadKeys() throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicKeyPath)));
        privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateKeyPath)));
    }

    private void checkKeyPair() throws Exception {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signing = Signature.getInstance("SHA256withRSA");
        signing.initSign(privateKey);
        signing.update(challenge);
        byte[] signature = signing.sign();
        signing.initVerify(publicKey);
        signing.update(challenge);
        if (!signing.verify(signature)) throw new IOException("RSA public/private key khong cung mot cap");
    }

    /** Fingerprint SHA-256(public.key X.509 DER); can doi chieu truc tiep voi client. */
    public String getFingerprint() throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
    }

    public PublicKey getPublicKey() { return publicKey; }
    public PrivateKey getPrivateKey() { return privateKey; }
}
