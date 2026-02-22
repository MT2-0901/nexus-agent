# Persistence ER Diagram

```mermaid
erDiagram
    CHAT_HISTORY {
        INTEGER id PK
        TEXT session_id
        TEXT user_id
        TEXT mode
        TEXT request_message
        TEXT response_message
        TEXT activated_skills_json
        INTEGER event_count
        TEXT created_at
    }
```

Notes:
- `activated_skills_json` stores activated skill names as JSON array string for cross-RDB compatibility.
- Index `idx_chat_history_session_created` supports session history retrieval.
