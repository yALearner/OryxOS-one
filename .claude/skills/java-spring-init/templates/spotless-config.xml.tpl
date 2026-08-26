<!--
  Spotless + google-java-format 配置片段（插入父 pom 的 build/pluginManagement）。

  占位符：{{SPOTLESS_VERSION}}、{{GJF_VERSION}}（实施时按步骤 2 探测）

  注意（实测坑位）：
  - GJF 默认 GOOGLE 风格为 2 空格缩进，与既有 4 空格代码有一次性大 diff；
    如项目已统一 4 空格且不想大改，可改 <style>AOSP</style>（同样自动格式化）
  - checkstyle 若用 canonical google_checks.xml（2 空格），GJF 必须同选 GOOGLE，
    两者缩进不一致会导致 checkstyle 与 spotless 永久打架
-->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>{{SPOTLESS_VERSION}}</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>{{GJF_VERSION}}</version>
                <style>GOOGLE</style>
                <reflowLongStrings>true</reflowLongStrings>
            </googleJavaFormat>
        </java>
    </configuration>
</plugin>

<!-- verify 阶段门禁（父 pom 的 build/plugins） -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>spotless-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
