package com.oryxos.cli;

import picocli.CommandLine.Command;

/** tool list 命令（轻命令）——工具体系（ToolRegistry/内置工具）归第 20 节，本课如实输出占位提示（不伪装）。 */
@Command(name = "list", description = "列出已注册的 Tool", mixinStandardHelpOptions = true)
public class ToolListCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("内置工具尚未就位（Tool 体系归第 20 节），当前无已注册工具");
  }
}
