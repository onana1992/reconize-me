package com.kyc.authenticity;

import com.kyc.adapters.AwsClientCredentials;
import com.kyc.config.KycProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * JPEG des spécimens sur le disque du processus. La clé d'objet est
 * {@code templates/authenticity/{dossier}/{somme}.jpg}, hors du préfixe des dossiers.
 */
public final class SpecimenCache implements AutoCloseable {

    static final String OBJECT_PREFIX = "templates/authenticity/";

    private final Path root;
    private final S3Client s3;
    private final String bucket;

    private SpecimenCache(Path root, S3Client s3, String bucket) {
        this.root = root;
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static SpecimenCache offline(Path root) {
        return new SpecimenCache(root, null, null);
    }

    public static SpecimenCache open(KycProperties properties, String cacheDir) {
        Path root = cacheDir == null || cacheDir.isBlank() ? defaultDir() : Path.of(cacheDir);
        if (properties != null && properties.aws().s3().enabled()) {
            KycProperties.Aws.S3 s3 = properties.aws().s3();
            S3Client client = S3Client.builder()
                    .region(AwsClientCredentials.region(s3.region(), properties.aws().region()))
                    .credentialsProvider(
                            AwsClientCredentials.require(s3.accessKeyId(), s3.secretAccessKey(), "kyc.aws.s3"))
                    .build();
            return new SpecimenCache(root, client, s3.bucket().trim());
        }
        return fromEnvironment(root);
    }

    private static SpecimenCache fromEnvironment(Path root) {
        if (!"true".equalsIgnoreCase(System.getenv("KYC_AWS_S3_ENABLED"))) {
            return offline(root);
        }
        String bucket = System.getenv("KYC_AWS_S3_BUCKET");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("KYC_AWS_S3_BUCKET is required");
        }
        S3Client client = S3Client.builder()
                .region(AwsClientCredentials.region(
                        System.getenv("KYC_AWS_S3_REGION"), System.getenv("KYC_AWS_REGION")))
                .credentialsProvider(AwsClientCredentials.require(
                        System.getenv("KYC_AWS_S3_ACCESS_KEY_ID"),
                        System.getenv("KYC_AWS_S3_SECRET_ACCESS_KEY"),
                        "kyc.aws.s3"))
                .build();
        return new SpecimenCache(root, client, bucket.trim());
    }

    public static Path defaultDir() {
        String override = System.getenv("KYC_AUTHENTICITY_SPECIMEN_CACHE");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "kyc-specimens");
    }

    /** Somme lue du classpath, fichier de cache vérifié, téléchargement seulement s'il manque. */
    public Path ensure(String folder, String sha256) {
        String sum = normalize(folder, sha256);
        Path cached = existing(folder, sum);
        if (cached != null) {
            return cached;
        }
        Path local = localSpecimen(folder);
        if (local != null && sum.equals(sha256(local))) {
            return store(folder, sum, local);
        }
        if (s3 == null) {
            throw new IllegalStateException("Gabarit " + folder + " : spécimen absent du cache et du poste");
        }
        byte[] downloaded = download(folder, sum);
        if (downloaded == null) {
            throw new IllegalStateException("Gabarit " + folder + " : spécimen absent du stockage");
        }
        if (!sum.equals(sha256(downloaded))) {
            throw new IllegalStateException("Gabarit " + folder + " : la somme du spécimen ne correspond pas");
        }
        return store(folder, sum, downloaded);
    }

    /** Octets du spécimen livré, depuis le cache, le poste, ou le stockage si les clés sont là. */
    public static byte[] shipped(String folder) throws IOException {
        ClassLoader loader = SpecimenCache.class.getClassLoader();
        try (InputStream sum = loader.getResourceAsStream("authenticity/" + folder + "/reference.sha256")) {
            if (sum == null) {
                throw new IllegalStateException("Gabarit " + folder + " : reference.sha256 est absent");
            }
            try (SpecimenCache cache = open(null, "")) {
                return Files.readAllBytes(cache.ensure(folder, readSum(sum, folder)));
            }
        }
    }

    public static String readSum(InputStream in, String folder) throws IOException {
        String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim();
        return normalize(folder, text);
    }

    @Override
    public void close() {
        if (s3 != null) {
            s3.close();
        }
    }

    private Path existing(String folder, String sum) {
        Path jpg = root.resolve(folder).resolve(sum + ".jpg");
        if (Files.isRegularFile(jpg) && sum.equals(sha256(jpg))) {
            return jpg;
        }
        Path png = root.resolve(folder).resolve(sum + ".png");
        if (Files.isRegularFile(png) && sum.equals(sha256(png))) {
            return png;
        }
        return null;
    }

    private static Path localSpecimen(String folder) {
        Path[] roots = {
            Path.of("src/main/resources/authenticity"),
            Path.of("api/src/main/resources/authenticity")
        };
        for (Path root : roots) {
            Path jpg = root.resolve(folder).resolve("reference.jpg");
            Path png = root.resolve(folder).resolve("reference.png");
            boolean hasJpg = Files.isRegularFile(jpg);
            boolean hasPng = Files.isRegularFile(png);
            if (hasJpg && !hasPng) {
                return jpg;
            }
            if (hasPng && !hasJpg) {
                return png;
            }
        }
        return null;
    }

    private byte[] download(String folder, String sum) {
        byte[] jpg = get(OBJECT_PREFIX + folder + "/" + sum + ".jpg");
        if (jpg != null) {
            return jpg;
        }
        return get(OBJECT_PREFIX + folder + "/" + sum + ".png");
    }

    private byte[] get(String key) {
        try {
            ResponseBytes<?> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return bytes.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw new IllegalStateException("Gabarit illisible : stockage indisponible", e);
        }
    }

    private Path store(String folder, String sum, Path source) {
        try {
            return store(folder, sum, Files.readAllBytes(source));
        } catch (IOException e) {
            throw new IllegalStateException("Gabarit " + folder + " : lecture impossible", e);
        }
    }

    private Path store(String folder, String sum, byte[] bytes) {
        String ext = png(bytes) ? ".png" : ".jpg";
        Path directory = root.resolve(folder);
        Path dest = directory.resolve(sum + ext);
        Path temp = directory.resolve(sum + ext + ".part");
        try {
            Files.createDirectories(directory);
            Files.write(temp, bytes);
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomic) {
            try {
                Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Gabarit " + folder + " : écriture du cache impossible", e);
            }
        }
        return dest;
    }

    private static boolean png(byte[] bytes) {
        return bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50;
    }

    private static String normalize(String folder, String text) {
        String sum = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (sum.length() != 64 || !sum.chars().allMatch(ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
            throw new IllegalStateException("Gabarit " + folder + " : reference.sha256 est illisible");
        }
        return sum;
    }

    static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible", e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
