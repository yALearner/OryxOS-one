package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SessionManager 最小契约验收 harness——幂等、三元组隔离、id 拼接只此一处（H4 不变量四）。 */
class SessionManagerTest {

  private final SessionManager manager = new SessionManager();

  @Test
  @DisplayName("同一三元组两次 getOrCreate 返回同一个 Session（幂等）")
  void getOrCreateSameTripleReturnsSameInstance() {
    Session first = manager.getOrCreate("cli", "alice", "ops-agent");
    Session second = manager.getOrCreate("cli", "alice", "ops-agent");

    assertThat(second).isSameAs(first);
  }

  @Test
  @DisplayName("channel/user/profileName 任一不同则得到不同 Session")
  void differentTriplePartsYieldDifferentSessions() {
    Session base = manager.getOrCreate("cli", "alice", "ops-agent");

    assertThat(manager.getOrCreate("web", "alice", "ops-agent")).isNotSameAs(base);
    assertThat(manager.getOrCreate("cli", "bob", "ops-agent")).isNotSameAs(base);
    assertThat(manager.getOrCreate("cli", "alice", "weather-agent")).isNotSameAs(base);
  }

  @Test
  @DisplayName("会话标识按 channel|user|profileName 公式生成")
  void sessionIdFollowsTripleFormula() {
    Session session = manager.getOrCreate("cli", "alice", "ops-agent");

    assertThat(session.id()).isEqualTo("cli|alice|ops-agent");
  }

  @Test
  @DisplayName("get 按 id 命中同一实例")
  void getReturnsSessionById() {
    Session created = manager.getOrCreate("cli", "alice", "ops-agent");

    assertThat(manager.get("cli|alice|ops-agent")).contains(created);
    assertThat(manager.get("cli|nobody|ops-agent")).isEmpty();
  }

  @Test
  @DisplayName("架构断言：Session 无公开构造器——id 只能由 SessionManager 生成（H4 不变量四）")
  void sessionCannotBeConstructedOutsideSessionManager() {
    boolean hasPublicConstructor =
        Arrays.stream(Session.class.getDeclaredConstructors())
            .anyMatch(c -> Modifier.isPublic(c.getModifiers()));

    assertThat(hasPublicConstructor)
        .withFailMessage("Session 不得有公开构造器：会话标识的拼接只能发生在 SessionManager 一处")
        .isFalse();
  }

  @Test
  @DisplayName("会话历史经 append 累积，外部只能读不可变视图")
  void messagesAccumulateAndExposeUnmodifiableView() {
    Session session = manager.getOrCreate("cli", "alice", "ops-agent");
    session.append(Message.user("hi"));
    session.append(Message.assistant("hello"));

    assertThat(session.messages()).hasSize(2);
    assertThat(session.messages().get(0).content()).isEqualTo("hi");
    assertThat(session.messages()).isUnmodifiable();
  }
}
