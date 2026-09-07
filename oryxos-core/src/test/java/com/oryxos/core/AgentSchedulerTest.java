package com.oryxos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * AgentScheduler 验收 harness（008-scheduler，课件 25 §四）——别真等时间：runOnce 拆成独立方法直接调；cron 触发本身是 Spring
 * 的事，只验"注册参数传对了"。四坑核心组（T002）+ 跳过日志（T004）+ 失败日志口径（T005）+ 注册组 ⑦b/⑦c（T006）。
 */
class AgentSchedulerTest {

  private static final Profile PROFILE = profile("ops-agent");

  private static Profile profile(String name) {
    return new Profile(
        name,
        null,
        new Profile.Identity("定时任务 Agent", "你是一个定时触发的运维助手"),
        new Profile.ProviderRef("deepseek", null, null),
        List.of(),
        List.of(),
        List.of(),
        List.of(new Profile.Schedule("0 0 8 * * *", "Asia/Shanghai", "生成今日天气和穿搭建议")),
        List.of(),
        new Profile.Settings(10, 20));
  }

  private AgentScheduler scheduler(
      ThreadPoolTaskScheduler taskScheduler,
      ProfileRegistry registry,
      SessionManager sessionManager,
      AgentService agentService) {
    return new AgentScheduler(taskScheduler, registry, sessionManager, agentService);
  }

  // ---- 注册参数对（坑四：时区显式；坑一：配置驱动） ----

  @Test
  @DisplayName("坑四：注册参数带配置的 cron 与时区（CronTrigger 显式 TimeZone）")
  void registersWithConfiguredCronAndZone() {
    ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
    ProfileRegistry registry = mock(ProfileRegistry.class);
    when(registry.list()).thenReturn(List.of(PROFILE));
    AgentScheduler scheduler =
        scheduler(taskScheduler, registry, mock(SessionManager.class), mock(AgentService.class));

    scheduler.registerAll();

    ArgumentCaptor<CronTrigger> captor = ArgumentCaptor.forClass(CronTrigger.class);
    verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
    // H3 核实：Spring 6.2 CronTrigger 无公开 getTimeZone()——用 equals（已实现）同时比对 cron 与时区
    assertThat(captor.getValue())
        .isEqualTo(new CronTrigger("0 0 8 * * *", TimeZone.getTimeZone("Asia/Shanghai")));
  }

  // ---- 最值钱之二：上一次还没跑完，本次触发直接跳过（坑二） ----

  @Test
  @DisplayName("最值钱之二：锁被占时本次触发直接跳过、不排队（verify never）")
  void skipsTriggerWhenLockHeld() throws Exception {
    AgentService agentService = mock(AgentService.class);
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            mock(SessionManager.class),
            agentService);
    Profile.Schedule sc = PROFILE.schedules().get(0);
    Lock lock = scheduler.lockFor("ops-agent|" + sc.cron() + "|" + sc.message());
    // 实现级明确：ReentrantLock 可重入——同线程 lock() 后再 tryLock() 恒成功（课件骨架同线程写法走不到跳过分支）；
    // 用虚拟线程模拟"调度线程仍持有锁"的生产语义（无自建线程池——测试辅助线程不违反宪法 VII）
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        Thread.ofVirtual()
            .start(
                () -> {
                  lock.lock(); // 模拟上一次还占着锁（不同线程）
                  try {
                    held.countDown();
                    release.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    lock.unlock();
                  }
                });
    held.await();
    try {
      scheduler.runOnce(PROFILE, sc);
      verify(agentService, never()).process(any(), any()); // 没有叠加执行
    } finally {
      release.countDown();
      holder.join();
    }
  }

  // ---- 最值钱之一：任务抛异常，不外抛且锁必须被释放（坑三，二进宫） ----

  @Test
  @DisplayName("最值钱之一：任务抛异常不外抛且锁被释放（二进宫——finally 漏 unlock 只有它能抓住）")
  void exceptionDoesNotEscapeAndLockIsReleased() throws Exception {
    AgentService agentService = mock(AgentService.class);
    when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"));
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            mock(SessionManager.class),
            agentService);
    Profile.Schedule sc = PROFILE.schedules().get(0);

    assertDoesNotThrow(() -> scheduler.runOnce(PROFILE, sc)); // 调度器不死
    assertDoesNotThrow(() -> scheduler.runOnce(PROFILE, sc)); // 再触发一次
    verify(agentService, times(2)).process(any(), any()); // 能进来——锁真的放了，没有永久卡死
  }

  // ---- 会话身份（宪法 VIII：三元组固定 scheduler） ----

  @Test
  @DisplayName("宪法 VIII：会话三元组固定 (scheduler, scheduler, profileName)，两次触发同一 Session")
  void usesSchedulerTripleSession() {
    SessionManager sessionManager = mock(SessionManager.class);
    Session session = mock(Session.class);
    when(sessionManager.getOrCreate(eq("scheduler"), eq("scheduler"), eq("ops-agent")))
        .thenReturn(session);
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            sessionManager,
            mock(AgentService.class));
    Profile.Schedule sc = PROFILE.schedules().get(0);

    scheduler.runOnce(PROFILE, sc);
    scheduler.runOnce(PROFILE, sc);

    verify(sessionManager, times(2)).getOrCreate("scheduler", "scheduler", "ops-agent");
  }

  @Test
  @DisplayName("到点触发：消息内容交给 AgentService（与 CLI/Web 完全同一入口）")
  void processesMessageThroughAgentService() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    Session session = mock(Session.class);
    when(sessionManager.getOrCreate(anyString(), anyString(), anyString())).thenReturn(session);
    AgentService agentService = mock(AgentService.class);
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            sessionManager,
            agentService);
    Profile.Schedule sc = PROFILE.schedules().get(0);

    scheduler.runOnce(PROFILE, sc);

    verify(agentService).process(session, "生成今日天气和穿搭建议");
  }

  // ---- T004（US2）：锁占跳过的日志证据 ----

  @Test
  @DisplayName("坑二：锁占跳过时记 info 日志（含「跳过本次触发」与派生 taskKey）")
  void skipLogsInfoWithTaskKey() throws Exception {
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            mock(SessionManager.class),
            mock(AgentService.class));
    Profile.Schedule sc = PROFILE.schedules().get(0);
    String taskKey = "ops-agent|" + sc.cron() + "|" + sc.message();
    Logger logger = (Logger) LoggerFactory.getLogger(AgentScheduler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        Thread.ofVirtual()
            .start(
                () -> {
                  scheduler.lockFor(taskKey).lock();
                  try {
                    held.countDown();
                    release.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    scheduler.lockFor(taskKey).unlock();
                  }
                });
    held.await();
    try {
      scheduler.runOnce(PROFILE, sc);
      assertThat(appender.list)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).contains("跳过本次触发").contains(taskKey);
              });
    } finally {
      release.countDown();
      logger.detachAppender(appender);
      holder.join();
    }
  }

  // ---- T005（US3）：失败日志口径（NFR-3，⑧a 澄清） ----

  @Test
  @DisplayName("坑三 / NFR-3：失败记 error 日志含 taskKey 与异常（key 含 message 属运营配置可接受）")
  void failureLogsErrorWithTaskKeyOnly() throws Exception {
    AgentService agentService = mock(AgentService.class);
    when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"));
    AgentScheduler scheduler =
        scheduler(
            mock(ThreadPoolTaskScheduler.class),
            mock(ProfileRegistry.class),
            mock(SessionManager.class),
            agentService);
    Profile.Schedule sc = PROFILE.schedules().get(0);
    String taskKey = "ops-agent|" + sc.cron() + "|" + sc.message();
    Logger logger = (Logger) LoggerFactory.getLogger(AgentScheduler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertDoesNotThrow(() -> scheduler.runOnce(PROFILE, sc)); // 调度器不死
      // 两行日志形态（FindSecBugs CRLF 门禁）：第一行 (String, Object) 带 sanitize 后的 taskKey、无异常；第二行常量 +
      // Throwable
      assertThat(appender.list)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains(taskKey);
              })
          .anySatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("定时任务执行失败详情"); // 常量行带异常栈
                assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(RuntimeException.class.getName());
              });
    } finally {
      logger.detachAppender(appender);
    }
  }

  // ---- T006（US4）：注册组——⑦b 非法 zone / 全量注册 / ⑦c 句柄 / zone 空单参 ----

  private static Profile profileWithSchedule(
      String name, String cron, String zone, String message) {
    return new Profile(
        name,
        null,
        new Profile.Identity("定时任务 Agent", "你是一个定时触发的运维助手"),
        new Profile.ProviderRef("deepseek", null, null),
        List.of(),
        List.of(),
        List.of(),
        List.of(new Profile.Schedule(cron, zone, message)),
        List.of(),
        new Profile.Settings(10, 20));
  }

  @Test
  @DisplayName("⑦b：非法 zone 启动报错（拼错时区不静默回退 GMT，001 纪律）")
  void illegalZoneFailsAtRegistration() {
    ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
    ProfileRegistry registry = mock(ProfileRegistry.class);
    when(registry.list())
        .thenReturn(
            List.of(profileWithSchedule("ops-agent", "0 0 8 * * *", "Asia/Shangha", "msg")));
    AgentScheduler scheduler =
        scheduler(taskScheduler, registry, mock(SessionManager.class), mock(AgentService.class));

    assertThatThrownBy(scheduler::registerAll)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ops-agent")
        .hasMessageContaining("Asia/Shangha");
  }

  @Test
  @DisplayName("坑一：多 Profile 多 schedules 全量注册（schedule 调用次数 = schedules 总数）")
  void registersAllSchedulesAcrossProfiles() {
    ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
    when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
        .thenAnswer(inv -> mock(ScheduledFuture.class));
    ProfileRegistry registry = mock(ProfileRegistry.class);
    when(registry.list())
        .thenReturn(
            List.of(
                profileWithSchedule("a", "0 0 8 * * *", null, "早上好"),
                profileWithSchedule("b", "0 30 9 * * *", "Asia/Shanghai", "日报")));
    AgentScheduler scheduler =
        scheduler(taskScheduler, registry, mock(SessionManager.class), mock(AgentService.class));

    scheduler.registerAll();

    verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
    assertThat(scheduler.scheduledTaskCount()).isEqualTo(2); // ⑦c：句柄已登记
  }

  @Test
  @DisplayName("坑四：zone 空 → 单参 CronTrigger 按系统时区（Profile javadoc 口径）")
  void blankZoneUsesSingleArgCronTrigger() {
    ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
    ProfileRegistry registry = mock(ProfileRegistry.class);
    when(registry.list())
        .thenReturn(List.of(profileWithSchedule("ops-agent", "0 0 8 * * *", null, "msg")));
    AgentScheduler scheduler =
        scheduler(taskScheduler, registry, mock(SessionManager.class), mock(AgentService.class));

    scheduler.registerAll();

    ArgumentCaptor<CronTrigger> captor = ArgumentCaptor.forClass(CronTrigger.class);
    verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
    assertThat(captor.getValue()).isEqualTo(new CronTrigger("0 0 8 * * *"));
  }
}
