package com.oryxos.tool;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心阶段唯一 Sandbox 实现：三层白名单校验（007-sandbox FR-1，课件 24 §3.2）。
 *
 * <p>接口墙（{@link Sandbox#enforce}，宪法 VI）后面的第一档实现——按 {@code ActionType} 路由三个**私有**校验方法 （外部只看得到 {@code
 * enforce} 一个入口，避免接口被本档实现带偏）；任意校验失败抛 {@link SandboxViolationException}， 复用 ToolExecutor 既有审计路径落
 * {@code tool_invocations}（success=false + error_message，零新增审计）。
 *
 * <p>口径（007-sandbox 修订说明 ⑦）：
 *
 * <ul>
 *   <li>⑦a 白名单根构造期 {@code normalize().toAbsolutePath()}——相对根按启动目录解析；根不绝对化则相对根与绝对 target
 *       永不匹配、文件类工具全拒
 *   <li>⑦b Windows 下 root/target lower-case 归一后比较（{@code Path.startsWith} 大小写敏感而 NTFS 不敏感，归一
 *       不扩大放行面）；Linux 维持大小写敏感
 *   <li>⑦c 任一白名单为空构造期 WARN（启动诊断——消息只含配置键名，不违反 NFR-3 用户可控值不进日志口径）
 *   <li>⑥d host 解析不到（null）→ 拒绝（课件骨架无此防御，会 NPE——实现级明确新增）
 * </ul>
 *
 * <p>诚实标注：白名单是"劝阻级"防线，防的是模型犯傻误操作，防不住蓄意绕过（软链逃逸/TOCTOU 等不覆盖）。构造期一次性
 * 拷贝三份不可变集合——无共享可变状态，虚拟线程并发下线程安全；配置不可热更新，改白名单须重启。
 */
public class WhitelistSandbox implements Sandbox {

  private static final Logger LOG = LoggerFactory.getLogger(WhitelistSandbox.class);

  private static final boolean WINDOWS =
      System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

  private final List<Path> allowedRoots; // 构造期 normalize + toAbsolutePath（相对根按启动目录解析，⑦a）
  private final Set<String> allowedCommands;
  private final List<String> allowedDomainPatterns;

  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.allowedRoots =
        fileProps.allowedPaths().stream()
            .map(Path::of)
            .map(Path::normalize)
            .map(Path::toAbsolutePath)
            .toList();
    this.allowedCommands = Set.copyOf(shellProps.allowedCommands());
    this.allowedDomainPatterns = List.copyOf(httpProps.allowedDomains());
    warnIfEmpty();
  }

  private void warnIfEmpty() {
    // 启动诊断：消息只含配置键名（非用户可控值），违规原因仍只进审计不进日志（NFR-3，⑦c）
    if (allowedRoots.isEmpty()) {
      LOG.warn("Sandbox 白名单 file.allowed_paths 为空——文件读写将全部被拒（空 = 什么都不允许，不是不校验）");
    }
    if (allowedCommands.isEmpty()) {
      LOG.warn("Sandbox 白名单 shell.allowed_commands 为空——shell 命令将全部被拒（空 = 什么都不允许，不是不校验）");
    }
    if (allowedDomainPatterns.isEmpty()) {
      LOG.warn("Sandbox 白名单 http.allowed_domains 为空——HTTP 请求将全部被拒（空 = 什么都不允许，不是不校验）");
    }
  }

  @Override
  public void enforce(SandboxAction action) {
    // 无 default：新增 ActionType 值时编译期强制处理（枚举四值全覆盖）
    switch (action.type()) {
      case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
      case SHELL_COMMAND -> checkShellCommand(action.target());
      case HTTP_REQUEST -> checkHttpUrl(action.target());
    }
  }

  private void checkFilePath(String rawPath) {
    Path target = Path.of(rawPath).normalize().toAbsolutePath();
    if (allowedRoots.stream().noneMatch(root -> startsWithRoot(target, root))) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  private boolean startsWithRoot(Path target, Path root) {
    if (!WINDOWS) {
      return target.startsWith(root);
    }
    // ⑦b：startsWith 大小写敏感而 NTFS 不敏感——lower-case 归一后按元素比较（Path.startsWith 逐段语义，
    // 不可退化为字符串前缀匹配——"d:\workspace2" 不得命中根 "d:\workspace"）
    Path loweredTarget = Path.of(target.toString().toLowerCase(Locale.ROOT));
    Path loweredRoot = Path.of(root.toString().toLowerCase(Locale.ROOT));
    return loweredTarget.startsWith(loweredRoot);
  }

  private void checkShellCommand(String command) {
    // limit=2：只取首 token，语义与课件骨架 split("\\s+")[0] 一致（ErrorProne StringSplitter 门禁要求显式 limit）
    String firstToken = command.trim().split("\\s+", 2)[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  private void checkHttpUrl(String url) {
    String host = URI.create(url).getHost();
    // ⑥d：解析不到（null）→ 拒绝——畸形 URL 不因 NPE 漏放（课件骨架无此防御）
    boolean allowed =
        host != null
            && allowedDomainPatterns.stream().anyMatch(pattern -> matchesDomain(host, pattern));
    if (!allowed) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  private boolean matchesDomain(String host, String pattern) {
    if (pattern.startsWith("*.")) {
      return host.endsWith(pattern.substring(1)); // ".example.com" 带点号边界——evil-example.com 不命中
    }
    return host.equals(pattern);
  }
}
