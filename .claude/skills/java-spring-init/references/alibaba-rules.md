# 阿里编码规约（P3C）接入与回退

## 主路径：p3c-pmd 官方版（PMD 6 线）

- 坐标 `com.alibaba.p3c:p3c-pmd:2.1.1`，**基于 PMD 6.15**（官方版从未适配 PMD 7）
- 因此 maven-pmd-plugin 必须钉 **3.21.2（PMD 6.55.0）**——PMD 6.55 支持 JDK 21 运行，
  实测 ThreadPoolCreationRule 等规则在 JDK 21 上正常触发（2026-08-26 负面验证通过）
- **2.1.1 没有聚合版 `ali-pmd.xml`**（1.x 的聚合文件已拆），必须逐个列出 9 个独立 ruleset：
  `ali-comment / ali-concurrent / ali-constant / ali-exception / ali-flowcontrol /
  ali-naming / ali-oop / ali-other / ali-set`（ali-orm 为占位，官方推荐列表不含）
- maven-pmd-plugin 3.x 从**项目 classpath** 解析 ruleset 路径：p3c-pmd 需作为插件依赖
  （提供规则类，如 com.alibaba.p3c.pmd.lang.java.rule.*）+ 项目依赖 provided（提供 ruleset 文件）
- 验证「真的在执行」：写一段 `Executors.newFixedThreadPool` → pmd:check 应报
  `ThreadPoolCreationRule`。**只看 BUILD SUCCESS 不够**——ruleset 没加载也可能静默通过

## 规则速查（高频触发）

| Ruleset | 典型规则 | 触发场景 |
|---------|---------|---------|
| ali-comment | ClassMustHaveAuthorRule | 类 javadoc 缺 @author |
| ali-comment | CommentsMustBeJavadocFormat | 注释格式不符 |
| ali-concurrent | ThreadPoolCreationRule | 用 Executors 建线程池 |
| ali-exception | MethodReturnWrapperTypeRule / 异常处理类 | try-catch 吞异常、返回包装类型 |
| ali-naming | ClassNamingShouldBeCamelRule 等 | 命名不符合驼峰 |
| ali-oop | EqualsAvoidNullRule / BigDecimal 类 | equals 顺序、BigDecimal 构造 |
| ali-set | ClassCastExceptionWithToArrayRule | toArray 强转 |

## 回退路径（主路径失效时，报告必须写明原因）

1. **回退一**：PMD 7 + 社区移植版 P3C ruleset（需自行评估维护性）
2. **回退二**：P3C 降为参考层（本文件）+ IDE 插件「阿里巴巴 Java 编码规约」即时提示，
   CI 只保留 PMD 标准 rulesets
3. 无论哪条回退路径：交付报告写明失效原因、回退选择、遗留风险

## 冲突裁决

- 风格冲突以 google-java-format 为准（自动修、省争论）——P3C 的格式类规则与 GJF 冲突时让位
- 规约冲突（命名/并发/异常处理）按阿里规约执行，修代码而不是改规则
