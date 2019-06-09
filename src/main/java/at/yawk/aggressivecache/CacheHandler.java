package at.yawk.aggressivecache;

import com.google.common.io.ByteStreams;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@Slf4j
@RequiredArgsConstructor
public final class CacheHandler implements HttpHandler {
    private static final Set<HttpString> DISTINGUISH_HEADERS = Set.of(
            Headers.ACCEPT,
            Headers.ACCEPT_CHARSET,
            Headers.ACCEPT_ENCODING,
            Headers.ACCEPT_LANGUAGE,
            Headers.AUTHORIZATION,
            Headers.CONTENT_TYPE,
            Headers.COOKIE,
            Headers.EXPECT,
            Headers.HOST,
            Headers.IF_MATCH,
            Headers.RANGE
    );

    private final Cache cache;

    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.startBlocking();
        byte[] bytes = ByteStreams.toByteArray(exchange.getInputStream());
        SortedMap<HttpString, String> headers = new TreeMap<>();
        for (HttpString header : DISTINGUISH_HEADERS) {
            String value = exchange.getRequestHeaders().getFirst(header);
            if (value != null) {
                headers.put(header, value);
            }
        }
        String requestURI = exchange.getRequestURI();
        if (!exchange.getQueryString().isEmpty()) {
            requestURI += '?' + exchange.getQueryString();
        }
        CacheKey key = CacheKey.builder()
                .method(exchange.getRequestMethod())
                .url(requestURI)
                .headers(headers)
                .body(bytes.length == 0 ? null : bytes) // TODO
                .build();

        exchange.dispatch();
        cache.query(key).whenComplete((r, e) -> {
            if (e != null) {
                log.warn("Failed to load url {}", key.getUrl(), e);
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            } else {
                exchange.setStatusCode(r.getStatusCode());
                for (Map.Entry<String, String> header : r.getHeaders().entrySet()) {
                    exchange.getResponseHeaders().put(HttpString.tryFromString(header.getKey()), header.getValue());
                }
                exchange.setResponseContentLength(r.getBody().length);
                try {
                    exchange.getOutputStream().write(r.getBody());
                } catch (IOException ex) {
                    log.warn("Failed to write response", e);
                }
            }
        });
    }
}
