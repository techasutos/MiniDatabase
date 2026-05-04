package com.minidb.common.lifecycle;

/**
 * Lifecycle contract for all stoppable server components.
 * Mirrors the pattern used in Netty / gRPC server lifecycle.
 */
public interface Lifecycle {

    /** Start the component. Called once on application boot. */
    void start() throws Exception;

    /** Gracefully stop the component, releasing all resources. */
    void stop() throws Exception;

    /** Whether the component is currently running. */
    boolean isRunning();
}

