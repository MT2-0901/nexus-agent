package com.nexus.agent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class JdbcChatHistoryStore implements ChatHistoryStore {

    private static final String INSERT_SQL = """
            INSERT INTO chat_history (
              session_id,
              user_id,
              mode,
              request_message,
              response_message,
              activated_skills_json,
              event_count,
              created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String QUERY_BY_SESSION_SQL = """
            SELECT
              id,
              session_id,
              user_id,
              mode,
              request_message,
              response_message,
              activated_skills_json,
              event_count,
              created_at
            FROM chat_history
            WHERE session_id = ?
            ORDER BY id DESC
            LIMIT ?
            """;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcChatHistoryStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ChatHistoryRecord record) {
        jdbcTemplate.update(
                INSERT_SQL,
                record.sessionId(),
                record.userId(),
                record.mode(),
                record.requestMessage(),
                record.responseMessage(),
                writeSkills(record.activatedSkills()),
                record.eventCount(),
                record.timestamp().toString()
        );
    }

    @Override
    public List<ChatHistoryRecord> listBySession(String sessionId, int limit) {
        return jdbcTemplate.query(QUERY_BY_SESSION_SQL, this::mapRow, sessionId, limit);
    }

    private ChatHistoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatHistoryRecord(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("mode"),
                rs.getString("request_message"),
                rs.getString("response_message"),
                readSkills(rs.getString("activated_skills_json")),
                rs.getInt("event_count"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private String writeSkills(List<String> skills) {
        try {
            return objectMapper.writeValueAsString(skills == null ? List.of() : skills);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize activated skills", ex);
        }
    }

    private List<String> readSkills(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize activated skills", ex);
        }
    }
}
