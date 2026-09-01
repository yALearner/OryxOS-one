package com.oryxos.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器（最小契约，内存版）。
 *
 * <p>同一 (channel, userId, profileName) 三元组幂等返回同一 {@link Session}；会话标识 = {@code
 * channel|userId|profileName}，**拼接只发生在本类一处**（H4 不变量四）。并发安全由 {@link ConcurrentHashMap} 承载（虚拟线程复用）。
 *
 * <p>{@link #save} 内存版为 no-op 占位——sessions 表与 JPA 持久化归第 18 节，契约不变、只换实现。
 */
public final class SessionManager {

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  /** 按三元组取或建会话（幂等：同三元组永远返回同一实例）。 */
  public Session getOrCreate(String channel, String userId, String profileName) {
    return sessions.computeIfAbsent(
        sessionIdOf(channel, userId, profileName),
        id -> new Session(id, profileName, channel, userId));
  }

  /** 按会话标识查（本节内存版）。 */
  public Optional<Session> get(String sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }

  /** 持久化占位：内存版无需落盘；第 18 节 JPA 化后写 sessions 表。 */
  public void save(Session session) {
    // 最小契约：状态已在内存共享，无操作；签名留给第 18 节实现
  }

  /** 会话标识拼接——全仓库只有这一个地方（H4 不变量四，SessionManagerTest 架构断言钉死）。 */
  private String sessionIdOf(String channel, String userId, String profileName) {
    return channel + "|" + userId + "|" + profileName;
  }
}
