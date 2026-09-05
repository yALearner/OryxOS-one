package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.ActionType;
import com.oryxos.tool.Sandbox;
import com.oryxos.tool.SandboxViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ShellTools 验收 harness——执行前必须先过 sandbox.enforce(SHELL_COMMAND)（坑十）；超时强制销毁 + 明确报错 （构造注入小超时，不等真实
 * 30s）；退出码非 0 → failure（输出进 errorMessage）；平台假设：bash（生产 Linux；本机 Git Bash）。
 */
class ShellToolsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("正常：执行命令返回标准输出")
  void executesCommand() {
    ShellTools tool = new ShellTools(mock(Sandbox.class), 30_000);

    ToolResult result =
        tool.execute(objectMapper.createObjectNode().put("command", "echo hello-shell"));

    assertThat(result.success()).isTrue();
    assertThat(result.content()).contains("hello-shell");
  }

  @Test
  @DisplayName("坑十：执行前先过 enforce(SHELL_COMMAND, 命令)")
  void enforcesBeforeExecution() {
    Sandbox sandbox = mock(Sandbox.class);
    ShellTools tool = new ShellTools(sandbox, 30_000);

    tool.execute(objectMapper.createObjectNode().put("command", "echo x"));

    verify(sandbox)
        .enforce(
            argThat(a -> a.type() == ActionType.SHELL_COMMAND && a.target().contains("echo x")));
  }

  @Test
  @DisplayName("违规：mock 拒绝时异常上抛、不执行")
  void violationBlocksExecution() {
    Sandbox sandbox = mock(Sandbox.class);
    org.mockito.Mockito.doThrow(new SandboxViolationException("命令不在白名单"))
        .when(sandbox)
        .enforce(any());
    ShellTools tool = new ShellTools(sandbox, 30_000);

    assertThatThrownBy(() -> tool.execute(objectMapper.createObjectNode().put("command", "echo x")))
        .isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("退出码非 0：failure，输出进 errorMessage")
  void nonZeroExitFails() {
    ShellTools tool = new ShellTools(mock(Sandbox.class), 30_000);

    ToolResult result =
        tool.execute(objectMapper.createObjectNode().put("command", "echo boom; exit 3"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("boom");
  }

  @Test
  @DisplayName("超时：强制销毁进程 + 明确报错（构造注入 100ms 小超时）")
  void timeoutDestroysProcess() {
    ShellTools tool = new ShellTools(mock(Sandbox.class), 100);

    ToolResult result = tool.execute(objectMapper.createObjectNode().put("command", "sleep 5"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("超时");
  }
}
