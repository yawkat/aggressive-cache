package at.yawk.aggressivecache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.undertow.util.HexConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@Slf4j
@RequiredArgsConstructor
public final class Cache {
    private static final int[] TIERS = { 2 };
    private static final Duration EXPIRY_DURATION = Duration.ofDays(7);

    private final Client client;
    private final Path storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path file(CacheKey key) {
        Hasher hasher = Hashing.sha256().newHasher();
        key.hash(hasher);
        String hash = HexConverter.convertToHexString(hasher.hash().asBytes());

        Path path = storageDir;
        int offset = 0;
        for (int tier : TIERS) {
            String piece = hash.substring(offset, offset + tier);
            path = path.resolve(piece);
            offset += tier;
        }
        path = path.resolve(hash.substring(offset));
        return path;
    }

    private void store(Path path, CacheResponse response) throws IOException {
        try {
            Files.createDirectories(path.getParent());
        } catch (FileAlreadyExistsException ignored) {}
        Path tmp = path.getParent().resolve(path.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp)) {
            objectMapper.writeValue(os, response);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    public CompletionStage<CacheResponse> query(CacheKey key) {
        Path file = file(key);
        if (Files.exists(file)) {
            CacheResponse response;
            try (InputStream stream = Files.newInputStream(file)) {
                response = objectMapper.readValue(stream, CacheResponse.class);
            } catch (IOException e) {
                return CompletableFuture.failedStage(e);
            }
            boolean refresh = false;
            try {
                FileTime lastModifiedTime = Files.getLastModifiedTime(file);
                if (Instant.now().isAfter(lastModifiedTime.toInstant().plus(EXPIRY_DURATION))) {
                    client.fetch(key).whenComplete((resp, e) -> {
                        if (e != null) {
                            log.warn("Failed to refresh url {}", key.getUrl(), e);
                        } else {
                            try {
                                store(file, resp);
                            } catch (IOException ex) {
                                log.warn("Failed to store refreshed data at {}", file, e);
                            }
                        }
                    });
                    refresh = true;
                }
            } catch (IOException e) {
                log.warn("Failed to check expiration for {}", file, e);
            }
            log(key, response, refresh ? Mode.HIT_ASYNC_REFRESH : Mode.HIT);
            return CompletableFuture.completedStage(response);
        } else {
            return client.fetch(key).whenComplete((resp, e) -> {
                if (e == null) {
                    log(key, resp, Mode.MISS);
                    try {
                        store(file, resp);
                    } catch (IOException ex) {
                        log.warn("Failed to store refreshed data at {}", file, ex);
                    }
                }
            });
        }
    }

    private void log(CacheKey key, CacheResponse response, Mode mode) {
        log.info("{} {} {} -> {} [{}]",
                 mode,
                 key.getMethod(),
                 key.getUrl(),
                 response.getStatusCode(),
                 response.getBody().length);
    }

    private enum Mode {
        MISS,
        HIT,
        HIT_ASYNC_REFRESH,
    }
}
