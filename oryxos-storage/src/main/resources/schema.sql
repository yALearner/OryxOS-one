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

-- tool_invocations 审计表（002-react 新增，手工维护，不依赖 hibernate.ddl-auto 自动迁移）。
-- 与 llm_calls 同口径：成功与失败都落账（success / error_message 两列真实存在），
-- created_at 以 ISO-8601 TEXT 存储（SQLite 无原生 TIMESTAMP，复用 InstantTextConverter）。

CREATE TABLE IF NOT EXISTS tool_invocations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id    TEXT    NOT NULL,
    tool_name     TEXT    NOT NULL,
    input_json    TEXT,
    result_json   TEXT,
    success       INTEGER NOT NULL,
    error_message TEXT,
    duration_ms   INTEGER,
    created_at    TEXT    NOT NULL
);

-- sessions 会话表（003-cli 新增，手工维护，不依赖 hibernate.ddl-auto 自动迁移）。
-- 字段照技术方案 §9.2：session_id 由 SessionManager 按 channel|user|profile 唯一拼接；
-- messages_json 对话历史整体 JSON 序列化一列存（核心阶段不按条拆表）；
-- 时间戳 ISO-8601 TEXT（SQLite 无原生 TIMESTAMP，复用 InstantTextConverter）；归档流转归第 26 节。

CREATE TABLE IF NOT EXISTS sessions (
    session_id     TEXT PRIMARY KEY,
    profile_name   TEXT NOT NULL,
    channel        TEXT NOT NULL,
    user_id        TEXT NOT NULL,
    messages_json  TEXT,
    status         TEXT NOT NULL DEFAULT 'active',
    created_at     TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at    TEXT
);

-- notify_channels 通知渠道全局注册表（004-notify 新增，手工维护，不依赖 hibernate.ddl-auto 自动迁移）。
-- 技术方案 §6.8：Agent 正文按 name 引用渠道，webhook 地址不进对话、不进 frontmatter；
-- type 核心阶段均为 webhook（扩展阶段其他类型自行解释 url 语义）；CRUD 归 Web Service 节。

CREATE TABLE IF NOT EXISTS notify_channels (
    name        TEXT PRIMARY KEY,
    type        TEXT NOT NULL,
    url         TEXT NOT NULL,
    description TEXT
);
