package com.minidb.transport.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SessionRegistry {

    public enum SessionState {
        CONNECTING,
        AUTHENTICATED,
        EXECUTING
    }

    public record SessionInfo(
            long sessionId,
            String remoteAddress,
            String username,
            SessionState state,
            long connectedAtEpochMs,
            long lastActivityEpochMs,
            String lastCommand
    ) {}

    private static final class MutableSession {
        private final long sessionId;
        private final String remoteAddress;
        private volatile String username = "";
        private volatile SessionState state = SessionState.CONNECTING;
        private volatile long connectedAtEpochMs = System.currentTimeMillis();
        private volatile long lastActivityEpochMs = connectedAtEpochMs;
        private volatile String lastCommand = "";

        private MutableSession(long sessionId, String remoteAddress) {
            this.sessionId = sessionId;
            this.remoteAddress = remoteAddress;
        }

        private SessionInfo snapshot() {
            return new SessionInfo(
                    sessionId,
                    remoteAddress,
                    username,
                    state,
                    connectedAtEpochMs,
                    lastActivityEpochMs,
                    lastCommand
            );
        }
    }

    private final AtomicLong nextSessionId = new AtomicLong(1L);
    private final Map<Long, MutableSession> sessions = new ConcurrentHashMap<>();

    public long open(String remoteAddress) {
        long sessionId = nextSessionId.getAndIncrement();
        sessions.put(sessionId, new MutableSession(sessionId, remoteAddress));
        return sessionId;
    }

    public void markAuthenticated(long sessionId, String username) {
        MutableSession session = require(sessionId);
        session.username = username;
        session.state = SessionState.AUTHENTICATED;
        session.lastActivityEpochMs = System.currentTimeMillis();
    }

    public void markExecuting(long sessionId, String command) {
        MutableSession session = require(sessionId);
        session.state = SessionState.EXECUTING;
        session.lastCommand = command;
        session.lastActivityEpochMs = System.currentTimeMillis();
    }

    public void markIdle(long sessionId) {
        MutableSession session = sessions.get(sessionId);
        if (session != null) {
            session.state = SessionState.AUTHENTICATED;
            session.lastActivityEpochMs = System.currentTimeMillis();
        }
    }

    public void close(long sessionId) {
        sessions.remove(sessionId);
    }

    public List<SessionInfo> snapshot() {
        List<SessionInfo> list = new ArrayList<>();
        for (MutableSession session : sessions.values()) {
            list.add(session.snapshot());
        }
        list.sort(Comparator.comparingLong(SessionInfo::sessionId));
        return list;
    }

    public static String formatIso(long epochMs) {
        return Instant.ofEpochMilli(epochMs).toString();
    }

    private MutableSession require(long sessionId) {
        MutableSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("Unknown session: " + sessionId);
        }
        return session;
    }
}

