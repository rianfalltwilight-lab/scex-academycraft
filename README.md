![](https://raw.githubusercontent.com/MohistMC/AcademyCraft/master/blob/logo.png)

A Minecraft mod about superability. The inspiration of AcademyCraft comes from [A Certain Scientific Railgun (とある科学の超電磁砲)](https://en.wikipedia.org/wiki/A_Certain_Scientific_Railgun) but the mod content is not limited of the background.

Original: https://github.com/LambdaInnovation/AcademyCraft

## Development planning:
 Prioritize functional implementation, then consider beautification (UI, skill effects, etc.)  
 优先实现功能，再考虑美化(UI, 技能特效等等)


Issue(Idea, Bug) Submission
============

Please go to [Issues](https://github.com/MohistMC/AcademyCraft/issues) and submit a new ticket.

Commands
========

Ability administration requires permission level 2. `/aim` targets the executing player and
`/aimp <players>` targets online players or selectors. Use `/aim help` or `/aimp <players> help` in game.
The 1.0.7-compatible library includes `cat`, `catlist`, `learn`, `unlearn`, `learn_all`, `learned`,
`skills`, `level`, `exp`, `fullcp`, `cd_clear`, `maxout`, `reset`, and developer-mode controls.
Category and skill arguments accept either their stable ID or the copyable `#index` printed by `catlist`/`skills`.

能力管理指令要求权限等级 2。`/aim` 作用于执行者，`/aimp <玩家或选择器>` 作用于指定在线玩家。
请在游戏内输入 `/aim help` 查看完整语法。该指令库复刻了 1.0.7 的能力系、技能、等级、熟练度、
CP、冷却、重置与调试模式管理功能。
能力系与技能参数既可使用稳定 ID，也可直接使用 `catlist`/`skills` 输出的 `#索引`。

### Legacy machine ownership migration / 遗留机器所有权迁移

Versions that predate machine ownership, command-placed blocks, and damaged saves can contain
ownerless nodes, Matrices, Ability Interferers, or Wind Generator main blocks. They are never
silently assigned to the first visitor. A permission-level-2 administrator can use:

- `/acmigrate ownership scan [radius]` — count protected and ownerless machines in loaded chunks.
- `/acmigrate ownership claim <x> <y> <z> [player]` — claim one ownerless machine.
- `/acmigrate ownership claim_nearby <radius> [player]` — migrate all ownerless protected machines
  in the loaded area without overwriting existing owners. Radius is limited to 128 blocks.

升级前版本、命令放置或损坏存档可能留下没有所有者的节点、矩阵、能力干扰器或风力发电机主机。
系统不会把它们静默分配给第一个访客。权限等级 2 的管理员可先使用
`/acmigrate ownership scan [半径]` 盘点，再用 `claim` 认领单台，或用
`claim_nearby` 将已加载范围内的无主机器批量分配给指定玩家；已有所有者永远不会被覆盖，
扫描也不会强制加载区块。

Test evidence is classified in [docs/TESTING.md](docs/TESTING.md). Static source/resource contract
tests are regression hints and are never reported as gameplay-parity passes.

Misc
====

## Donation

You can support developement of AcademyCraft by donating. This will secure us more time to make the mod more intriguing!

You would also be able to be in our donator list, both on website and in-game :beer:

## License

All versions of AcademyCraft are licensed under [GPLv3](http://www.gnu.org/licenses/gpl.html).

And all versions of AcademyCraft are additionally licensed as following:

Prohibits any person, company, business, organization, etc. from selling AcademyCraft and its contents in any form, including but not limited to paid downloads (including but not limited to various legal currencies, virtual currency, game token, etc.) AcademyCraft's items, the sale of AcademyCraft ability within the game, etc.

Lambda Innovation retains the copyright, the right of authorship, the ownership, etc. of AcademyCraft, regardless of all agreements, and any provision that requires these rights or a part of them is deemed invalid.

Lambda Innovation reserves the right of final interpretation and reserves the right to deny all agreements to revoke all authorizations.

所有版本的AcademyCraft使用[GPLv3](http://www.gnu.org/licenses/gpl.html)协议。

并且所有版本的AcademyCraft同时附加有以下版权限制：

禁止任何个人、公司、企业、组织等以任何形式出售 AcademyCraft 及其内容，包括但不限于付费下载(包括但不限于各种法定货币、虚拟货币、虚拟币、游戏代币等)，游戏内出售 AcademyCraft 物品，游戏内出售 AcademyCraft 能力等。

LambdaInnovation对于AcademyCraft的著作权、署名权、拥有权、版权等无视一切协议而保留，任何要求这些权利或其中一部分的条款均视为无效。

LambdaInnovation保留最终解释权，并保留否定一切协议撤销一切授权的权利。

## Modpack permission

Yes. >)

## Regarding Toaru Magic Index

Many people have been asking questions about whether or how much the mod will be related to
the original story _A Certain Magic Index_. Our answer is that although AC is based on the
_Railgun_, which is a spinoff of _Index_, the mod will only focus on the science side of
the story, and thus just loosely related to _Index_.

The mod is dedicated to build an interesting experience evolved around the idea of **superability**,
that's really everything.
