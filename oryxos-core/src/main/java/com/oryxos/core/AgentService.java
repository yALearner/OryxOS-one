package com.oryxos.core;

/**
 * 三种触发源共用的统一入口，也是一次处理的编排者（宪法 VIII）。
 *
 * <p>{@link #process(Session, String)} 依次：从 {@link ProfileRegistry} 按 session.profileName() 取
 * Profile → 放进 {@link ProfileContext} → 跑 {@link ReActLoop} → {@link SessionManager#save} 持久化 →
 * **finally 清理 ProfileContext**（坑四：处理抛异常也必须清）。ReActLoop 不感知消息从哪个入口来——CLI / Web Service /
 * AgentScheduler 第 18 节起接入本入口。
 */
public final class AgentService {

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  public AgentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry = profileRegistry;
    this.reActLoop = reActLoop;
    this.sessionManager = sessionManager;
  }

  /** 处理一条用户消息，返回 Agent 最终答复。 */
  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .findByName(session.profileName())
            .orElseThrow(() -> new IllegalStateException("Profile 未注册: " + session.profileName()));
    ProfileContext.set(profile);
    try {
      String reply = reActLoop.run(session, userMessage, profile);
      sessionManager.save(session);
      return reply;
    } finally {
      ProfileContext.clear();
    }
  }
}
