package com.oryxos.core;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 定时任务调度器——第三种触发源（钟推，宪法 VIII）：到点自动拼一条消息，交给 {@link AgentService}（与 CLI/Web 完全同一入口， ReActLoop
 * 不感知消息从哪个入口来）。
 *
 * <p>职责划窄（课件 25 §二）：只干"到点拼消息交 AgentService"一件事——消息说什么话是 AGENT.md 的事、怎么处理是 ReActLoop 的事、
 * 多实例归属是扩展阶段分布式协调的事。调度机制用 Spring {@link ThreadPoolTaskScheduler} + {@link CronTrigger} 动态注册
 * （坑一：不用 @Scheduled 写死——触发规则按 Profile 配置动态生成）。
 *
 * <p>007 实施前优化（修订说明 ⑦）：
 *
 * <ul>
 *   <li>⑦a 装配处 {@code setPoolSize(4)}——默认单线程下同步阻塞的长 ReAct 会跨任务互相拖累（防重叠锁只管同任务）
 *   <li>⑦b zone 非空先经 {@code ZoneId.of} 校验——非法时区启动明确报错（001 纪律：{@code TimeZone.getTimeZone} 对拼错值 静默回退
 *       GMT 会「到点不触发」），不静默
 *   <li>⑦c {@code schedule} 返回句柄存 {@link #scheduledTasks}——28 节启用停用/扩展阶段重调度的 cancel 前置
 *   <li>拍板 B 锁 key 派生 {@code profileName|cron|message}——不改 Profile.Schedule 字面量（28 节 task_id
 *       来源必须重议， 见需求文档跨节契约 ⑦d）
 * </ul>
 *
 * <p>无组件注解纯类（G4-C1）——由装配处（CliAgentConfiguration）显式 @Bean 并在方法体内调 {@link #registerAll()}。
 */
public class AgentScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AgentScheduler.class);

  private final ThreadPoolTaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final SessionManager sessionManager;
  private final AgentService agentService;
  private final ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks =
      new ConcurrentHashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ThreadPoolTaskScheduler/ProfileRegistry/SessionManager/AgentService"
              + " 为装配处注入的单例（只读使用、不暴露引用），004 WebhookNotifyAdapter 同款先例")
  public AgentScheduler(
      ThreadPoolTaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      SessionManager sessionManager,
      AgentService agentService) {
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.sessionManager = sessionManager;
    this.agentService = agentService;
  }

  /** 启动注册：扫所有 Profile 的 schedules 逐条动态注册（坑一——不用 @Scheduled 写死）。 */
  public void registerAll() {
    for (Profile profile : profileRegistry.list()) {
      for (Profile.Schedule sc : profile.schedules()) {
        String key = taskKey(profile.name(), sc);
        ScheduledFuture<?> future =
            taskScheduler.schedule(
                () -> runOnce(profile, sc),
                sc.zone() == null || sc.zone().isBlank()
                    ? new CronTrigger(sc.cron()) // zone 缺省按系统时区（Profile javadoc）
                    : new CronTrigger(sc.cron(), validatedZone(profile.name(), sc.zone())));
        if (future != null) { // 真实调度器恒非空；mock 返回 null 时跳过登记（⑦c 句柄只对真实注册有意义）
          scheduledTasks.put(key, future);
        }
      }
    }
  }

  /** 一次触发：拿本地锁 → 拼会话 → 交 AgentService → 记失败日志 → 放锁。 */
  public void runOnce(Profile profile, Profile.Schedule sc) {
    String taskKey = taskKey(profile.name(), sc);
    Lock lock = lockFor(taskKey);
    if (!lock.tryLock()) {
      LOG.info("定时任务仍在执行，跳过本次触发: {}", sanitize(taskKey)); // 坑二：不排队、不并行（CRLF 净化，005 先例）
      return;
    }
    try {
      // 宪法 VIII：channel/user 固定 scheduler——同一 Profile 历次触发复用同一 Session
      Session session = sessionManager.getOrCreate("scheduler", "scheduler", profile.name());
      agentService.process(session, sc.message()); // 与 CLI/Web 完全一样的入口
    } catch (Exception e) {
      // 坑三：失败不崩调度器；两行日志满足 FindSecBugs CRLF 门禁——带变量的用 (String, Object) 形式（值已 sanitize），
      // 异常栈用常量 + Throwable 双参（ProfileLoader 先例；用户可控值不入带 Throwable 的三参重载）
      LOG.error("定时任务执行失败: {}", sanitize(taskKey));
      LOG.error("定时任务执行失败详情", e);
    } finally {
      lock.unlock(); // 最值钱之二：锁必须放掉
    }
  }

  /** 日志参数净化 CR/LF（CRLF 注入防线，CliAgentConfiguration 同款机器可判写法）——锁 key 本身不净化。 */
  private String sanitize(String value) {
    return value.replace('\r', '?').replace('\n', '?');
  }

  /** ⑦b：zone 合法性校验——TimeZone.getTimeZone 对拼错时区静默回退 GMT 会「到点不触发」，按 001 纪律不静默。 */
  private TimeZone validatedZone(String profileName, String zone) {
    try {
      ZoneId.of(zone);
      return TimeZone.getTimeZone(zone);
    } catch (ZoneRulesException e) {
      throw new IllegalStateException("定时任务时区非法: profile=" + profileName + " zone=" + zone, e);
    }
  }

  private String taskKey(String profileName, Profile.Schedule sc) {
    return profileName + "|" + sc.cron() + "|" + sc.message();
  }

  /** 测试钩子（课件 25 §四骨架同款）：按派生 key 取任务锁——锁占跳过回归用。 */
  Lock lockFor(String taskKey) {
    return taskLocks.computeIfAbsent(taskKey, k -> new ReentrantLock());
  }

  /** 测试钩子（⑦c）：已登记句柄数——28 节启停/重调度前置的机器证据。 */
  int scheduledTaskCount() {
    return scheduledTasks.size();
  }
}
