# AcademyCraft 0.0.19：网络、伤害与生命周期复核

此版本以冻结0.0.18源码包为起点，保留该版本原JAR与源码ZIP。原版仪器化JAR中783个原生产class已与冻结JAR逐字节比对一致。最终结果以配套输出报告及精确SHA-256为准，本文件不把前版总测试数当作新版证据。

## 协议与运行组合

- Minecraft 1.21.1、NeoForge 21.1.248、Oracle Java 21.0.12、Gradle wrapper 9.2.0。
- 新协议 `academy-1.21.1-payload-v14-data-v4`，客户端与服务端必须同时使用0.0.19。能力存档格式仍为v4。
- 技能开始/即时使用意图带服务端会话nonce与正整数递增序列，会话绑定真实玩家实例及维度；重连、重生和切换维度换nonce。
- 非法owner/nonce/槽位/技能类型不推进有效意图序列；已接受的有效意图即使因冷却、资源或干扰未执行也不能稍后重放。
- 客户端执行S2C会话同步时检查原连接、维度和递增revision；充能epoch握手保留，并避免重连后代次复用。

## 伤害与死亡边界

防御扣费从伤害进入前或公开Incoming事件移到该事件全部监听、盾牌和无敌帧之后、原版armor之前。覆盖Insulation、硬化、上升气流、Offense Armour、矢量偏移/反射、Light Shield及虚数装备IF消耗。满反射仍可取消整个受击，不触发后续击退；嵌套伤害的container和临时字段按实际调用层分别处理。

死亡清理在公开LivingDeathEvent最终未取消后执行。直接受测生命周期是PaperDrill、Armour、Flying；其他持续技能使用相同最终死亡入口，不能据此宣称每项均经过客户端死亡测试。

这是一项精确伤害阶段的约束，不将“HP没有下降”等同于“受击被拒绝”：吸收、后续armor减免及Dummy无限HP均可能保留合法防御行为。后续模组改变LivingDamagePre/Post或覆盖hurt的方法仍需针对性验证。

## 验收层级

- 默认GameTest使用实际ServerPlayer/PlayerList，EmbeddedChannel并非真实网络连接；自然tick、真实hurt/die/teleport和playerdata读写有独立断言。
- 可选兼容命名空间 `academy_recheck_compat` 只在属性开启且七个依赖齐全时注册。专用夹具使用NeoForge官方 `NetworkRegistry.configureMockConnection`，没有屏蔽Curios事件。
- 兼容输入为固定SHA的Botania/Curios/Patchouli/Thaumcraft/ThaumicBases/Dummmmmmy/ModularRouters快照。第三方JAR不收入本源码ZIP；这些是候选快照，不代表最终联合整包已冻结。
- 双客户端验收驱动调用真实KeyMapping和C2S/S2C通道，服务器记录权威状态，截图仅提供画面证据，不冒充物理键盘或外部黑盒验收。
- 持久化探针分别在Logout、ServerStopping采集对应保存时点，在新ServerPlayer首tick前精确比较附件；客户端比对21项稳定同步字段，CP/OL允许正常重连后的恢复。
- Jet停服探针直接启动真实runtime以进入16tick窗口，检验原步速保存恢复；不能据此声称完整Jet技能支付已通过。
- 原版before6在客户端等待ServerStopped后才退出时，Jet最终主档仍为0.07而非原步速0.123；0.0.19在ServerStopping、原版saveAll之前调用持有ACTIVE状态的既有stop路径恢复原步速。Aero飞行重启实测通过，未据静态疑点增加Aero生产补丁。

## 已完成的源码回归与边界

修复前默认GameTest为271项、26项失败，七依赖兼容组10项、7项失败；修复后335项JUnit（包含资源与静态契约）、280项默认GameTest、10项兼容GameTest均通过。不同层级不相加为游戏完成率；新增用例逐项证据见交付报告，仪器化原版的早期夹具失败不计为产品失败。

持久化覆盖真实PlayerList重连、独立UUID、附件与背包/经验、损坏主档回退到未激活技能时的备份，以及受控正常停服后的主档重启。它不证明任意旧存档、活跃技能期间autosave或其.dat_old已具有持久临时权限所有权标记。正式JAR的双客户端结果和独立源码包重建哈希在配套报告记录，避免把构建前记录冒充最终产物验收。

## 构建

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks '-Porg.gradle.java.installations.auto-detect=false' '-Porg.gradle.java.installations.auto-download=false' '-Porg.gradle.java.installations.paths=C:\Program Files\Java\jdk-21.0.12' test build runGameTestServer
```

`BUILD-INFO.txt`由脚本生成；归档与哈希共用 `scripts/source-files.ps1` 清单。最终交付需从源码ZIP在独立目录完整重建并比对JAR字节。

本开发任务不修改生产世界或整包，不执行生产启停、部署或玩家公告。正常关服测试不能代替断电、强制终止、长时间负载或完整旧版视觉等价验收。