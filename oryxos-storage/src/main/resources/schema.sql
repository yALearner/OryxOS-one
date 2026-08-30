-- llm_calls 审计表（手工维护，不依赖 hibernate.ddl-auto 自动迁移）。
-- 相对需求文档 §10 原表结构的补充修订：新增 success / error_message 两列，
-- 保证调用失败时事故在库里同样留痕（与 tool_invocations 对称）。

CREATE TABLE IF NOT EXISTS llm_calls (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id        TEXT    NOT NULL,
    provider          TEXT    NOT NULL,
    model             TEXT    NOT NULL,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    total_tokens      INTEGER,
    success           INTEGER NOT NULL,
    error_message     TEXT,
    duration_ms       INTEGER,
    created_at        TEXT    NOT NULL
);
