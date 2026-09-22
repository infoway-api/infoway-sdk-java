package io.infoway.sdk;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * One shared OkHttp stack for every {@link HttpClient} and WebSocket.
 *
 * <p>OkHttp's default dispatcher is a non-daemon pool with an unbounded max, and
 * {@code close()} used to call {@code executorService().shutdown()} on a
 * <em>per-instance</em> client. A test or a process that constructs many clients
 * therefore allocates a native thread per client. {@code OkHttp TaskRunner} in
 * 4.12 is already a process-wide daemon pool; the leak is the dispatcher.</p>
 *
 * <p>Instances share this dispatcher and connection pool. {@code close()} cancels
 * that instance's calls and must not shut the shared pool down.</p>
 */
final class HttpTransports {

    private static final int MAX_THREADS = 64;

    private static final Dispatcher DISPATCHER = daemonDispatcher();
    private static final ConnectionPool POOL = new ConnectionPool(5, 30, TimeUnit.SECONDS);

    private static final OkHttpClient BASE = new OkHttpClient.Builder()
            .dispatcher(DISPATCHER)
            .connectionPool(POOL)
            .build();

    private static final OkHttpClient WEB_SOCKET = BASE.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build();

    private HttpTransports() {}

    static OkHttpClient http(long timeoutSeconds) {
        return BASE.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    static OkHttpClient webSocket() {
        return WEB_SOCKET;
    }

    private static Dispatcher daemonDispatcher() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                MAX_THREADS,
                30,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "infoway-okhttp");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        Dispatcher dispatcher = new Dispatcher(executor);
        dispatcher.setMaxRequests(MAX_THREADS);
        dispatcher.setMaxRequestsPerHost(32);
        return dispatcher;
    }
}
