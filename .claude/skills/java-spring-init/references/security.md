# 安全检查决策细节（步骤 8 配套）

## 四件套分工（从严，任一失败 CI 红）

| 层 | 工具 | 位置 | 阻断条件 |
|----|------|------|---------|
| 字节码 SAST | SpotBugs + Find Security Bugs | `mvn verify`（本地+CI build job） | effort=Max, threshold=Low |
| 源码 SAST | PMD（含 P3C） | `mvn verify` | 任一 violation |
| 源码 SAST | semgrep（p/java） | CI security job | 任一 finding（默认阻断） |
| 源码 SAST（公开仓库） | CodeQL | 独立 workflow | SARIF 上报 + 默认阻断 |
| SCA | OWASP Dependency-Check | CI security job + 本地显式执行 | failBuildOnCVSS=7 |

## 密钥规范（宪法级）

- 任何密钥/token/API key 一律环境变量占位，禁止明文入库；
  git 历史检查（`git log -S`）也是安全评审的一部分
- `NVD_API_KEY` 属例外管理的"半敏感"key（NVD 免费申请），建议进仓库 secret

## 坑位（实测）

1. **dependency-check 13 起 NVD API key 是必需的**（NVD 2025 年起要求所有用户带 key，
   无 key 不再只是限流）：没有 key 直接报 `Invalid API Key, length of 0 too short...`。
   → 先到 https://nvd.nist.gov/developers/request-an-api-key 免费申请，设 `NVD_API_KEY` 环境变量
2. **空 key 与无 key 同罪**：环境变量**存在但为空字符串**同样报 `Invalid API Key, length of 0`
   - pom 里不要写 `<nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>`（env 缺失时变成空串）
   - CI 空 secret 时**必须 `unset NVD_API_KEY`**，只省略 `-DnvdApiKey` 不够
3. **数据目录缓存**：`~/.m2/repository/org/owasp/dependency-check-data/*/`（H2 odc.mv.db +
   NVD 数据）；修好配置后仍报错时删掉该目录重跑（数据可再生）
4. **首次全量下载可能 10-30 分钟**（带 key 限流放宽但仍慢）：本地首跑放后台执行
5. **semgrep 需要 Python**：本机无 Python 时只能 CI-only；本地验证缺项要在交付报告里写明
3. **suppression 纪律**：`config/dependency-check/suppression.xml` 每新增一条必须注释理由
   （CVE 编号 + 为何不适用），禁止无理由全局抑制——宁可升版本
4. **semgrep 需要 Python**：本机无 Python 时只能 CI-only；本地验证缺项要在交付报告里写明
