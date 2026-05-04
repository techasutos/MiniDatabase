package com.minidb.transport.protocol;

import java.net.Socket;

/**
 * Protocol handler — decouples transport from query execution.
 * Implementations handle authentication, framing, and dispatch.
 */
public interface ProtocolHandler {
    void handle(Socket socket);
}

