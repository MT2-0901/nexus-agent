package com.nexus.agent.persistence;

import java.util.List;

public class NoopChatHistoryStore implements ChatHistoryStore {

    @Override
    public void save(ChatHistoryRecord record) {
        // Persistence disabled by configuration.
    }

    @Override
    public List<ChatHistoryRecord> listBySession(String sessionId, int limit) {
        return List.of();
    }
}
