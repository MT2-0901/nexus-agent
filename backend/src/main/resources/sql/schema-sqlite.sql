-- Intent: Persist chat request/response records for session-level history retrieval.
-- Scope: SQLite schema for nexus-agent relational persistence v1.
-- Rollback: DROP INDEX IF EXISTS idx_chat_history_session_created; DROP TABLE IF EXISTS chat_history;

CREATE TABLE IF NOT EXISTS chat_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    mode TEXT NOT NULL,
    request_message TEXT NOT NULL,
    response_message TEXT NOT NULL,
    activated_skills_json TEXT NOT NULL,
    event_count INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_history_session_created
    ON chat_history (session_id, created_at DESC);
