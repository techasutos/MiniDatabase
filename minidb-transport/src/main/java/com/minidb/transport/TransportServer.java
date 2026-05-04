package com.minidb.transport;

import com.minidb.common.lifecycle.Lifecycle;

/**
 * Transport server contract — pluggable (TCP, HTTP, Unix socket, etc.)
 */
public interface TransportServer extends Lifecycle {
    int getPort();
}

