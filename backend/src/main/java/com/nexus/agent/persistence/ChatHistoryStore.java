package com.nexus.agent.persistence;

import java.util.List;

public interface ChatHistoryStore {

    void save(ChatHistoryRecord record);

    List<ChatHistoryRecord> listBySession(String sessionId, int limit);
}
