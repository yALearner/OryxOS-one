# Quick Start

> ⚠️ OryxOS is currently in **pre-alpha** (core phase, single-node private deployment). APIs may change.

## Prerequisites

- **Java 21+**
- **Maven 3.8+**

That's it. No PostgreSQL, Redis, or other external dependencies — data is stored in local SQLite.

## Installation

```bash
# Clone the repository
git clone https://github.com/yALearner/OryxOS-one.git
cd oryxos

# Build
mvn clean package -DskipTests

# Initialize the workspace
java -jar oryxos-boot/target/oryxos-boot-*.jar init
```

## Configure Your First Agent

1. Set your LLM API key:

```bash
export DEEPSEEK_API_KEY=sk-your-key-here
```

2. Edit `.oryxos/agents/default/AGENT.md`:

```markdown
---
name: default
description: My first Agent
provider:
  name: deepseek
  model: deepseek-chat
  api_key: ${DEEPSEEK_API_KEY}
tools:
  - read_file
  - write_file
  - shell
  - http_get
  - save_memory
  - recall_memory
settings:
  max_iterations: 10
---
You are a helpful assistant that can read/write files,
execute commands, and search for information.
```

## Start Using

```bash
# Interactive chat
java -jar oryxos-boot/target/oryxos-boot-*.jar chat

# Or start the HTTP API server
java -jar oryxos-boot/target/oryxos-boot-*.jar serve --port 8080

# Then call the API
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profileName":"default","channel":"web","userId":"demo"}'
```

## CLI Commands

```bash
oryxos init                      # Initialize .oryxos/ workspace (idempotent)
oryxos status                    # View configuration and runtime status
oryxos chat [--profile <name>]   # Interactive multi-turn chat
oryxos serve [--port 8080]       # Start HTTP API server
oryxos profile list              # List all Agents
oryxos tool list                 # List available Tools
oryxos session list              # List active Sessions
```

## What's Next

- [Architecture](./architecture) — understand how OryxOS works under the hood
- [Features](./features) — explore all capabilities
- [Scenarios](./scenarios) — see real-world enterprise use cases
