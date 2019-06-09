package at.yawk.aggressivecache;

import io.undertow.Undertow;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author yawkat
 */
public final class Main {
    public static void main(String[] args) throws IOException {
        Cache cache = new Cache(new Client(), Paths.get(args[0]));
        CacheHandler cacheHandler = new CacheHandler(cache);
        Undertow server = Undertow.builder()
                .addHttpListener(3128, "0.0.0.0", exchange -> exchange.dispatch(cacheHandler))
                .build();
        server.start();
    }
}
