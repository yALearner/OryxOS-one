# CI 三 job 门禁模板（生成到 .github/workflows/ci.yml，与既有 workflow 共存）
# 占位符：{{BRANCH}}（默认分支名）、{{JAVA_VERSION}}（默认 21）

name: CI

on:
  push:
    branches: [{{BRANCH}}]
  pull_request:
    branches: [{{BRANCH}}]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    name: build（编译 + 单测 + 全量门禁）
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '{{JAVA_VERSION}}'
          cache: maven
      # verify 包含 enforcer / Error Prone / 单测 / spotless / checkstyle / pmd(P3C) / spotbugs
      - name: Build with full quality gates
        run: mvn -B -ntp verify
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/target/surefire-reports/**'
          retention-days: 7

  style:
    name: style（格式门禁，快速失败）
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '{{JAVA_VERSION}}'
          cache: maven
      - name: Spotless + Checkstyle gates
        run: mvn -B -ntp spotless:check checkstyle:check

  security:
    name: security（semgrep SAST + OWASP Dependency-Check SCA）
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Semgrep SAST (p/java)
        uses: semgrep/semgrep@v1
        with:
          config: p/java
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '{{JAVA_VERSION}}'
          cache: maven
      # NVD_API_KEY 必需（NVD 2025 年政策）：免费申请 https://nvd.nist.gov/developers/request-an-api-key
      # 空 secret 时必须 unset（空字符串被 dependency-check 13 视为非法 key）
      - name: OWASP Dependency-Check (SCA)
        shell: bash
        run: |
          if [ -n "$NVD_API_KEY" ]; then
            mvn -B -ntp dependency-check:check -DskipTests -DnvdApiKey="$NVD_API_KEY"
          else
            unset NVD_API_KEY
            mvn -B -ntp dependency-check:check -DskipTests
          fi
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
      - name: Upload dependency-check report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: '**/target/dependency-check-report.*'
          retention-days: 30
