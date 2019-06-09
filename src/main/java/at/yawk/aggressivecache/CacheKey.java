package at.yawk.aggressivecache;

import com.google.common.hash.Hasher;
import io.undertow.util.HttpString;
import java.util.Map;
import java.util.SortedMap;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * @author yawkat
 */
@Value
@Builder
public final class CacheKey {
    private final HttpString method;
    private final String url;
    private final SortedMap<HttpString, String> headers;
    @Nullable private final byte[] body;

    public void hash(Hasher hasher) {
        hasher.putInt(method.length());
        hasher.putUnencodedChars(method.toString());
        hasher.putInt(url.length());
        hasher.putUnencodedChars(url);
        hasher.putInt(headers.size());
        for (Map.Entry<HttpString, String> entry : headers.entrySet()) {
            hasher.putInt(entry.getKey().length());
            hasher.putUnencodedChars(entry.getKey().toString());
            hasher.putInt(entry.getValue().length());
            hasher.putUnencodedChars(entry.getValue());
        }
        if (body == null) {
            hasher.putInt(-1);
        } else {
            hasher.putInt(body.length);
            hasher.putBytes(body);
        }
    }
}
