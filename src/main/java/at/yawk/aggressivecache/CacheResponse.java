package at.yawk.aggressivecache;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.SortedMap;
import lombok.Builder;
import lombok.Value;

/**
 * @author yawkat
 */
@Value
@Builder
@JsonDeserialize(builder = CacheResponse.CacheResponseBuilder.class)
public final class CacheResponse {
    private final int statusCode;
    private final SortedMap<String, String> headers;
    private final byte[] body;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CacheResponseBuilder {

    }
}
