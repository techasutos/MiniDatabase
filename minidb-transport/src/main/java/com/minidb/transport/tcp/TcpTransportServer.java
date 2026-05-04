package com.minidb.transport.tcp;

import com.minidb.transport.TransportServer;
import com.minidb.transport.protocol.ProtocolHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * TCP transport server.
 *
 * - Thread-pool backed (configurable size)
 * - Graceful shutdown via stop()
 * - Accepts a ProtocolHandler for query dispatch
 */
public class TcpTransportServer implements TransportServer {

    private static final Logger LOG = Logger.getLogger(TcpTransportServer.class.getName());

    private final int             port;
    private final int             threadPoolSize;
    private final ProtocolHandler handler;

    private volatile ServerSocket   serverSocket;
    private volatile ExecutorService pool;
    private final AtomicBoolean     running = new AtomicBoolean(false);

    public TcpTransportServer(int port, int threadPoolSize, ProtocolHandler handler) {
        this.port           = port;
        this.threadPoolSize = threadPoolSize;
        this.handler        = handler;
    }

    @Override
    public void start() throws Exception {
        serverSocket = new ServerSocket(port);
        pool = new ThreadPoolExecutor(
                threadPoolSize, threadPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, "minidb-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        running.set(true);
        LOG.info("MiniDB TCP server listening on port " + port);

        Thread acceptThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    pool.submit(() -> handler.handle(client));
                } catch (IOException e) {
                    if (running.get()) {
                        LOG.warning("Accept error: " + e.getMessage());
                    }
                }
            }
        }, "minidb-accept");
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    @Override
    public void stop() throws Exception {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (pool != null) {
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        LOG.info("MiniDB TCP server stopped.");
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public int getPort() { return port; }
}

