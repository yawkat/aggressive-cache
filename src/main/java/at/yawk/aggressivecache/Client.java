package at.yawk.aggressivecache;

import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
public final class Client {
    private static final Set<HttpString> DISTINGUISH_HEADERS = Set.of(
            Headers.CONTENT_ENCODING,
            Headers.CONTENT_DISPOSITION,
            Headers.CONTENT_LANGUAGE,
            Headers.CONTENT_RANGE,
            Headers.CONTENT_TYPE,
            Headers.LAST_MODIFIED
    );

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(5, TimeUnit.MINUTES)
            .followRedirects(false)
            .build();

    public CompletionStage<CacheResponse> fetch(CacheKey key) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(key.getUrl());
        String mediaType = key.getHeaders().get(Headers.CONTENT_TYPE);
        if (mediaType == null) { mediaType = "text/plain"; }
        requestBuilder.method(key.getMethod().toString(),
                              key.getBody() == null ?
                                      null :
                                      RequestBody.create(MediaType.parse(mediaType), key.getBody()));
        for (Map.Entry<HttpString, String> entry : key.getHeaders().entrySet()) {
            requestBuilder.addHeader(entry.getKey().toString(), entry.getValue());
        }
        CompletableFuture<CacheResponse> future = new CompletableFuture<>();
        httpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                SortedMap<String, String> headers = new TreeMap<>();
                for (HttpString header : DISTINGUISH_HEADERS) {
                    String value = response.header(header.toString());
                    if (value != null) {
                        headers.put(header.toString(), value);
                    }
                }
                try (ResponseBody body = response.body()) {
                    future.complete(CacheResponse.builder()
                                            .headers(headers)
                                            .body(body == null ? new byte[0] : body.bytes())
                                            .statusCode(response.code())
                                            .build());
                }
            }
        });
        return future;
    }
}
