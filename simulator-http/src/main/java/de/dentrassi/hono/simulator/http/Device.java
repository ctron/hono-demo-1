package de.dentrassi.hono.simulator.http;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Device {

    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String HONO_HTTP_PROTO = System.getenv("HONO_HTTP_PROTO");
    private static final String HONO_HTTP_HOST = System.getenv("HONO_HTTP_HOST");
    private static final String HONO_HTTP_PORT = System.getenv("HONO_HTTP_PORT");
    private static final HttpUrl HONO_HTTP_URL;

    public static final AtomicLong SENT = new AtomicLong();
    public static final AtomicLong SUCCESS = new AtomicLong();
    public static final AtomicLong FAILURE = new AtomicLong();

    static {
        String url = System.getenv("HONO_HTTP_URL");

        if (url == null && HONO_HTTP_HOST != null && HONO_HTTP_PORT != null) {
            final String proto = HONO_HTTP_PROTO != null ? HONO_HTTP_PROTO : "http";
            url = String.format("%s://%s:%s", proto, HONO_HTTP_HOST, HONO_HTTP_PORT);
        }

        if (url != null) {
            HONO_HTTP_URL = HttpUrl.parse(url).resolve("/telemetry");
        } else {
            HONO_HTTP_URL = null;
        }
    }

    private final OkHttpClient client;

    private final String auth;

    public Device(final String user, final String password, final OkHttpClient client) {
        this.client = client;
        this.auth = Credentials.basic(user, password);
    }

    public void tick() {

        if (HONO_HTTP_URL == null) {
            return;
        }

        final Request request = new Request.Builder()
                .url(HONO_HTTP_URL)
                .post(RequestBody.create(JSON, "{foo: 42}"))
                .header("Authorization", this.auth)
                .build();

        final Call call = this.client.newCall(request);

        SENT.incrementAndGet();

        try (final Response result = call.execute()) {
            if (result.isSuccessful()) {
                SUCCESS.incrementAndGet();
            } else {
                logger.debug("Result code: {}", result.code());
                FAILURE.incrementAndGet();
            }
        } catch (final IOException e) {
            FAILURE.incrementAndGet();
            logger.debug("Failed to tick", e);
        }

    }
}
