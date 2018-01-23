package de.dentrassi.hono.simulator.http;

import static de.dentrassi.hono.demo.common.Register.shouldRegister;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.Call;
import okhttp3.Callback;
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
    public static final AtomicLong BACKLOG = new AtomicLong();
    public static final Map<Integer, AtomicLong> ERRORS = new ConcurrentHashMap<>();

    private static final boolean ASYNC = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_ASYNC", "false"));
    private static final String METHOD = System.getenv().get("HTTP_METHOD");

    private static final boolean AUTO_REGISTER = Boolean
            .parseBoolean(System.getenv().getOrDefault("AUTO_REGISTER", "true"));

    private static final boolean NOAUTH = Boolean.parseBoolean(System.getenv().getOrDefault("HTTP_NOAUTH", "false"));

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

        System.out.println("Running Async: " + ASYNC);
    }

    private final OkHttpClient client;

    private final String auth;

    private final RequestBody body;

    private final Request request;

    private final Register register;

    private final String user;

    private final String deviceId;

    private final String password;

    private final String tenant;

    private final Call call;

    public Device(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register) {
        this.client = client;
        this.register = register;
        this.user = user;
        this.deviceId = deviceId;
        this.tenant = tenant;
        this.password = password;
        this.auth = Credentials.basic(user + "@" + tenant, password);
        this.body = RequestBody.create(JSON, "{foo: 42}");

        if ("POST".equals(METHOD)) {
            this.request = createPostRequest();
        } else {
            this.request = createPutRequest();
        }

        this.call = this.client.newCall(this.request);
    }

    private Request createPostRequest() {
        final Request.Builder builder = new Request.Builder()
                .url(HONO_HTTP_URL)
                .post(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Request createPutRequest() {
        final Request.Builder builder = new Request.Builder()
                .url(
                        HONO_HTTP_URL.newBuilder()
                                .addPathSegment(this.tenant)
                                .addPathSegment(this.deviceId)
                                .build())
                .put(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    public void register() throws Exception {
        if (shouldRegister()) {
            this.register.device(this.deviceId, this.user, this.password);
        }
    }

    public void tick() {

        if (HONO_HTTP_URL == null) {
            return;
        }

        try {
            processTick();
        } catch (final Exception e) {
            logger.warn("Failed to tick", e);
        }

    }

    private void processTick() {
        SENT.incrementAndGet();

        try {
            if (ASYNC) {
                publishAsync();
            } else {
                publishSync();
            }

        } catch (final Exception e) {
            FAILURE.incrementAndGet();
            logger.debug("Failed to publish", e);
        }
    }

    private void publishSync() throws IOException {
        try (final Response response = this.call.execute()) {
            if (response.isSuccessful()) {
                SUCCESS.incrementAndGet();
                handleSuccess(response);
            } else {
                logger.trace("Result code: {}", response.code());
                FAILURE.incrementAndGet();
                handleFailure(response);
            }
        }
    }

    private void publishAsync() {
        BACKLOG.incrementAndGet();
        this.call.enqueue(new Callback() {

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                BACKLOG.decrementAndGet();
                if (response.isSuccessful()) {
                    SUCCESS.incrementAndGet();
                    handleSuccess(response);
                } else {
                    logger.trace("Result code: {}", response.code());
                    FAILURE.incrementAndGet();
                    handleFailure(response);
                }
                response.close();
            }

            @Override
            public void onFailure(final Call call, final IOException e) {
                BACKLOG.decrementAndGet();
                FAILURE.incrementAndGet();
                logger.debug("Failed to tick", e);
            }
        });
    }

    protected void handleSuccess(final Response response) {
    }

    protected void handleFailure(final Response response) {
        final int code = response.code();

        final AtomicLong counter = ERRORS.computeIfAbsent(code, x -> new AtomicLong());
        counter.incrementAndGet();

        try {
            switch (code) {
            case 401:
            case 403: //$FALL-THROUGH$
                if (AUTO_REGISTER && shouldRegister()) {
                    register();
                }
                break;
            }
        } catch (final Exception e) {
            logger.warn("Failed to handle failure", e);
        }
    }
}
