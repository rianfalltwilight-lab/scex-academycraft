# AcademyCraft test evidence policy

Test counts are not feature-completion percentages. Release evidence must identify the layer that
was actually exercised:

1. **Unit/policy tests** execute isolated calculations, state policies, codecs, or ledgers.
2. **Source/resource contracts** read source text or verify that assets/registrations exist. They
   catch accidental deletion but do not prove rendering, input, networking, or gameplay parity.
3. **GameTests** execute server-side behavior in a generated Minecraft test world.
4. **Userdev client gates** launch a real client from compiled development classes and resources.
   Self-driven fixtures must be described as such and are not packaged-JAR black-box tests.
5. **Packaged gates** load the exact release JAR through a formal NeoForge client or server launch.
   Loading alone does not prove a recipe, GUI, multiplayer, or skill workflow.
6. **External black-box parity tests** drive player-visible inputs from outside production code and
   assert authoritative server state plus rendered/audio output. Only this layer can close a full
   end-to-end parity row.

`BUILD-INFO.txt` records each layer separately. A passing JUnit aggregate may include source
contracts and must never be converted into an AcademyCraft behavior-completion claim. GameTest and
client/server gate claims must be regenerated for the exact release JAR; inherited results from an
older version are recorded as unverified, not copied forward.

中文摘要：源码字符串、类名和资源存在性测试只能防止文件误删，不能证明游戏行为或视觉等价。
发布记录必须分别标注单元测试、GameTest、开发客户端门禁、正式 JAR 门禁和外部黑盒验收，
不得把测试数量直接换算成功能完成度。
