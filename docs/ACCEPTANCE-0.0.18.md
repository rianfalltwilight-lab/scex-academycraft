# AcademyCraft 0.0.18 独立修复与验收

本说明记录本版本的实现、可复现验收入口和证据边界。精确产物 SHA-256、执行日期、每轮失败/修复及最终通过结果保存在随交付提供的外部验收报告；本文中的测试入口不是通过声明。

## 实现变化

- 无线网络换绑时同步撤销旧连接，立即保存不会保留同一设备的双边。读取历史重复 NBT 连接时释放旧边容量。回调中的换绑使用快照遍历，非法能量返回值不会制造能量。
- 修改菜单的请求携带 128 位会话 nonce 和单调序列。服务端核对当前玩家、当前有效菜单、位置/方块实体、存活、权限及会话，拒绝关窗或重开后的迟到包及重放。界面等待完整会话同步后才提交，保留尚未提交的输入；节点名称与密码分别提交，避免同时打开的另一客户端覆盖新字段。
- 网络协议版本为 `academy-1.21.1-payload-v13-data-v4`。客户端与服务端应使用配套同版 JAR；0.0.17 协议不能与本版混用。
- Extra 射线以真实实体 AABB 的首个交点排序，并用方块实际碰撞形状截断。Paper Drill 保留穿透多个实体的语义。Psycho Transmission 的物品获取使用同一遮挡规则。
- 空力使装甲/飞行在干扰、遗忘、退出、死亡及维度变化时清理运行会话和属性。开发模式依旧视为全技能；遗忘测试必须使用非开发模式。
- Liquid Shadow 以玩家 UUID/实例和实际影子实体绑定会话，日常查找为 O(1)，不扫描玩家周围 128 格实体。影子保留 NoAI 防止原版免费攻击，并在真实实体 tick 中按受碰撞约束的位移追击目标；相邻、隔墙和低 CP 情况均有真实时间推进测试。影子不会持久化；卸载、退出、维度变化及重启结束会话。攻击前结算 CP，受阻或无效会话不能隔墙/跨维度攻击；被拒绝的攻击尝试不退款，避免免费增长能力使用上限。
- 使用 DAMAGE 编码电量的设备保留原存档电量字段，但标为隐藏的不可损坏且不可修理，避免工作台、砂轮、铁砧和经验修补把电量当耐久修复；能量条由实际 IF 比例显示。这同时使 JEI 的磨石配方生成器跳过这些设备，保留正确空电/满电展示样本。
- 融合机菜单输入按已加载配方 Ingredient 判断，支持 Extra 的红石等原料。允许先放入不足一次加工数量的原料；加工仍按配方精确数量扣料，两个输出槽保持只取不放。
- 所有实现 IEnergyItem 的 Academy 物品都注册 JEI 空电/有电展示变体，同时保持配方身份不随电量变化。
- 源码元数据与 ZIP 共享 `scripts/source-files.ps1` 清单，包含 docs 和 .github，排除日志/运行目录/缓存。禁止链接越界、危险 ZIP 路径和覆盖已有归档；ZIP 内文件可在不读取原源码树时独立复算。

## 工具链和服务端回归

Minecraft 1.21.1、NeoForge 21.1.248、Gradle wrapper 9.2.0、Java 21。参考验收固定到 Oracle JDK 21.0.12；运行记录要分别核对 Gradle 启动 JVM、编译器、JUnit 和游戏进程，不能只写 JAVA_HOME 后假定工具链已锁定。

下面命令中的 Java 路径和缓存目录由执行者指定，隔离运行根必须使用新目录。`--offline` 要求事先已有完整可信依赖缓存：

```powershell
$env:GRADLE_USER_HOME = '<AcademyCraft任务根绝对路径>\gradle-home'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$toolchain = @('-Porg.gradle.java.installations.auto-detect=false',
  '-Porg.gradle.java.installations.auto-download=false',
  "-Porg.gradle.java.installations.paths=$env:JAVA_HOME")
.\scripts\generate-build-info.ps1
.\gradlew.bat @toolchain --offline --no-daemon --no-configuration-cache test build --console=plain
.\gradlew.bat @toolchain --offline --no-daemon --no-configuration-cache `
  '-PacceptanceRunRoot=<全新隔离目录>' runGameTestServer --console=plain
```

GameTests 的 NetworkAdversarialGameTests 覆盖连接/会话/重入/非法能量；ExtraAdversarialGameTests 覆盖伤害射线、取物遮挡、持续能力和影子支付/生命周期；ExtraRecipeExecutionGameTests 通过真正 CraftingMenu 普通和快速取出、已放置机器的调度 tick 检查 29 配方及缺料、错料、空电、液体不足、堵输出和恢复。它们是服务端测试，不能证明客户端画面或生存解锁流程。

## 隔离客户端验收入口

`scripts/run-packaged-client-gate.ps1` 接受 `InstanceRoot`、`AssetRoot`、独立 `GameDirectoryName`、`QuickPlaySingleplayer` 或 `QuickPlayMultiplayer`，使用已经准备好的正式 NeoForge profile/libraries 启动精确 JAR。每个游戏目录有独立 natives，避免并行客户端覆写同一 DLL；一次只允许启用一种自动验收驱动。需在游戏目录 mods 中只放本轮候选及明确记录的可选模组。

- `-MachineVisualGate`：原机器、无线网络与 UI 的自驱动流程，游戏目录需准备名为 MachineGate 的隔离种子世界。
- `-ExtraJeiGate`：真实 JEI runtime 查找并逐张打开 29 配方页面，记录截图；比较所有能量物品的空电/满电 ingredient UID、配方 UID 以及实际 ingredient 列表。
- `-ExtraJeiTransferGate`：在带有 ISOLATED-ACCEPTANCE 文件的独立游戏目录中，使用真实 JEI transfer handler 与 C2S 转移包填充工作台和机器；应核对服务端输入位置/数量与输出槽不被填入。运行结果另行报告，不能由打开配方页面替代。金属成形的转移按钮只搬材料，仍需按 JEI 配方上的模式提示手动选择 ETCH 等工艺。
- `-ConcurrentRoot <目录> -ConcurrentRole a|b`：两个真实客户端配合专服。协调目录需存在 ISOLATED-ACCEPTANCE；专服另设 academy.concurrentMenuGate=true、academy.concurrentRole=server 和同一 academy.concurrentRoot。测试修改字段、迟到包、重放、撤销权限、错误/正确密码直连及双模式背包。正常保存后同一隔离世界以 academy.concurrentRestart=true 复核持久性。
- `-ExtraSkillVisualGate`：仅在该可选测试驱动完成且实际运行有证据时，记录 19 项按键流程及 4 项环境被动。它初始化已解锁技能，不能冒充玩家逐级成长或开发机解锁。

运行种子单独随验收材料提供为 client-gate-fixture ZIP，并在外部报告列出整个 ZIP 和内部文件的哈希；它不属于源码包，也不是生产世界。解压到源码根可恢复 run/saves/JEIGate 和 run/options.txt，随后 prepareMachineGateWorld 会复制到新隔离运行根。若自行生成种子：先用 runClient 启动独立开发实例，创建名为 JEIGate 的单人测试世界并正常保存退出，同时保留 run/options.txt，再运行门禁；这种自行生成的世界是新的 fixture，不能冒充本轮相同输入。正式源码 ZIP 的普通 build 不依赖种子世界。

所有 Java 驱动默认关闭。注入客户端 KeyMapping 或调用生产 GUI 输入路径的自驱动流程，属于真实客户端自动化；不能宣称物理键盘/人工外部黑盒验收。截图必须结合服务器状态和实际画面审查。

## 归档与限制

冻结源码后先完成必要回归，生成准确 BUILD-INFO，再禁用缓存并强制全量构建最终 JAR，最后打包并独立校验源码。增量编译的匿名类调试信息可能与全新源码编译不同；交付构建必须使用 --no-build-cache --rerun-tasks，不能只依靠 UP-TO-DATE 输出。包已生成后若源码变化，必须重新生成元数据并重建。最终精确 JAR 的运行结果可以记录在外部报告，避免为嵌入其自身哈希而形成循环。

```powershell
.\scripts\generate-build-info.ps1 -ReleaseFixes '<真实变更及证据范围>'
.\gradlew.bat @toolchain --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks build
.\scripts\package-source.ps1 -Destination '<不存在的目标ZIP>'
.\scripts\verify-source-package.ps1 -ArchivePath '<目标ZIP>'
```

源码 ZIP 应另解压到全新目录，直接保留包内 BUILD-INFO（不要再次运行元数据生成器），使用同工具链离线构建并比较产物条目及 SHA-256；源文件哈希匹配不能替代实际复建。JAR 的精确比对结果以外部报告为准。

尚不能由这些测试证明：全部旧版逐帧模型/声音/动画等价、全部技能生存解锁、多人 PvP 平衡、长时间大能源网络性能、长期存档行为。Liquid Shadow 为原版 Drowned 代理外观；Air Blade 等当前即时射线不等同旧版飞行实体全过程。未测试或表现缺失的项目需继续标注，不能用注册数量或通过测试总数补齐。

本开发任务不负责生产部署、玩家整包或公告。任何后续生产交付需使用明确的新旧产物清单、世界/配置备份和回滚步骤，不能混装 0.0.17/0.0.18 客户端与服务端。