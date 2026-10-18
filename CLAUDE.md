# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目

「熔岩矿车」:纯 Java Swing 的横版挖矿游戏(仿塞班时代的熔岩矿车 / Motherload)。无第三方依赖,用户直接 `java -jar LavaDigger.jar` 运行。
产物 `LavaDigger.jar` 放在仓库根目录,源码在 `src/lavadigger/`,自测在 `tools/lavadigger/`(不打进 jar)。

## 命令

```bash
# 编译打包(生成根目录 LavaDigger.jar;--release 8,所以源码只能用 Java 8 语法:不要 var/record/switch 表达式/文本块)
powershell -c "cmd /c .\build.bat"        # Bash 工具里直接 cmd //c build.bat 会找不到文件

# 运行
java -jar LavaDigger.jar

# 离屏自测:模拟按键跑逻辑 + 断言 + 把关键画面存 PNG(之后用 Read 看图)
S=<scratchpad>
javac -encoding UTF-8 -nowarn -d "$S/testcls" src/lavadigger/*.java tools/lavadigger/*.java
java -cp "$S/testcls" lavadigger.SelfTest "$S/shots"     # 退出码 0 = 全部通过
java -cp "$S/testcls" lavadigger.V4Test "$S/shots"       # 第 4 版功能(地热、宝藏、探测器、落石、里程碑、存档升级等)

# 经济模拟:脚本化玩一整局,输出通关/全部升满的估算小时数(调价格后必须跑)
java -cp "$S/testcls" lavadigger.EconSim [种子...]      # 可加 -DpriceScale=1.2 试算整体涨价
```

- 游戏开着时 jar 文件被占用,`build.bat` 会在最后一步报 `FileSystemException ... LavaDigger.jar` 并 BUILD FAILED。这时要让用户先关掉游戏,不要擅自结束 java 进程(用户可能正在玩)。
- 自测没有测试框架,`SelfTest.main` 按顺序跑全部场景;要只测一项就临时注释掉其他段落。它会关闭存档和音效(`SaveManager.enabled` / `Sfx.enabled`),存读档测试会临时改写 `user.home`。
- 控制台是 GBK,自测输出的中文会乱码:看 `[OK]`/`[FAIL]` 和退出码,或在 java 后加 `-Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8`。
- 改了画面相关代码,跑完自测要看 `shots/*.png` 确认(例如字体不支持的符号会显示成方框,菜单箭头因此改为手画三角形)。

## 架构

- **`GamePanel`** 是核心,几乎所有逻辑都在这里:状态机 `State`(TITLE/PLAY/SHOP/PAUSE/EXPLODING/REPORT/HELP/ENDING)、物理、钻探、商店菜单、特效、渲染、键盘输入。
  - 用 Swing `Timer` 驱动 `update(dt)`;PLAY 状态按 1/120 秒子步长更新 `updatePlay`。自测直接调用 `update()` 和 `render()`,所以这两个方法不能依赖窗口。
  - 渲染先画到固定 960×640 的 `back` 缓冲,再等比缩放到窗口,窗口大小可随意调。
  - 商店菜单由 `shopItems()` 每帧重新生成 `MenuItem` 列表(文字、是否可用、动作),新增商品改这里即可。
- **钻探不走物理**:`startDig` 记下起止坐标,`digProgress` 插值移动车辆,`finishDig` 结算该格(矿入货舱 / 熔岩扣血 / 瓦斯 3×3 爆炸)。只能向下钻,或在地面上贴墙横向钻,不能向上钻。
- **`World`**:`byte[]` 网格(48×420,每格 32px,第 8 行 `SURFACE` 是地表)。矿物编码为 `ORE + 矿物下标`。`generate(seed)` 按深度概率生成,再做成团扩散、挖空洞、单独摆放 6 颗熔岩之心,最后清理出生点。
- **数值表集中在两处**:`Upgrades`(7 种部件 × 7 档的数值和价格、道具;第 7 种是探测器)和 `Mineral`(价值和深度分布);油耗、油价、摔落阈值、救援费等常量在 `GamePanel` 顶部。调平衡只改这三处。
  - 用户定的节奏:难度逐档上升但满级一定够油;通关 3~5 小时、全部升满约 5 小时。价格是用 `EconSim` 校准出来的,改了价格、矿价、油耗或地热就重跑它(8 个种子看通关/全满小时数)。`V4Test` 里有"第 6 档在底层挖满一舱用油 ≤60%、满级 ≤45%、第 5 档拿心回来剩 ≥25%"的断言。
  - 地热:`GamePanel.heat(深度格)`,150 米(50 格)以下所有地下油耗乘它,散热器减轻;`returnFuel()` 按深度逐格累加地热。HUD 显示的是"车温" `carTempC` = `tempC(heat())`,和油耗倍数同一个公式(用户要求深度、散热器、油耗三者对得上);改装厂散热器那行预览最深处车温。燃油报警:回程 ×1.3 + 挖 3 块矿的油提醒,回程 ×1.1 + 挖 1 块危险(`digFuelHere`)。300 米以下有地震落石(`updateQuake`),碎石是实体,**绝不写进地图**,同一次地震最多扣一次血。
- **加矿种的规矩**:`Mineral.ALL` 的下标就是存档里的格子编码(`ORE + 下标`),**只能往数组末尾追加**,不能插队、删除或改顺序。追加一批新矿时:记下这批的起始下标(如 `FIRST_V3`),把 `SaveManager` 写出的 `version` 加 1,并在 `load` 里对低版本存档调用 `World.injectNewMinerals(起始下标, 比例)`,往没挖过的泥土里补新矿。贴图样式看 `style`(`Textures.makeOre`),动态特效看 `fx`(`GamePanel.drawOreEffects`,画在黑暗遮罩之后)。挖掘硬度由价格决定(`Mineral.hardness`)。
- **熔岩之心和宝藏**(`Mineral.isProtected`):全图数量固定(心 6 颗,宝藏见 `TREASURE_COUNT`),由 `World.placeProtected` 单独摆放(`weight=0`,`pick` 不会随机产出)。炸药炸不掉;爆炸丢失时放回原深度带,所以通关目标恒为 6 颗。
- 新手指引 `tip(id)` 和深度里程碑 `checkMilestones()` 各用 `Player` 里的一个位掩码记"已发生过"。小地图只画玩家挖开的格子(`World.dug`,读档时按种子重新生成原图对比得出)。
- **`Shop`**:四家商店的列位置决定了地表哪几格是水泥(`World.generate` 用 `isUnderShop`),以及进店判定(`Shop.at`)。挪动商店位置会同时影响这两处。
- **`Art`**:商店和矿车用 Java2D 矢量绘制(每帧绘制,带动画),`GamePanel.drawShop` 和 `drawVehicle` 只负责转调。
- **`Textures`**:全部贴图在启动时用代码生成,没有图片资源;土层颜色按深度分 5 档(`band()`)。**`Sfx`** 用代码合成 PCM 音效,声卡不可用时静默。
- **`SaveManager`**:存到 jar 所在文件夹的 `save/lava.properties`(该文件夹已 gitignore;不可写时退回 `~/.lava-digger/`),整张地图用 Base64 保存。启动时会把旧位置(`~/.lava-digger/save.properties`、`存档/熔岩矿车存档.properties`、`save/lava-digger-save.properties`)自动搬过来。用户要求存档路径不用中文。当前 `version=4`,低版本读档走 `injectNewMinerals` / `upgradeToV4`(补心、补宝藏、按最深纪录标记里程碑和指引,不补发奖金)。自测通过 `SaveManager.dirOverride` 和改写 `user.home` 隔离,不会碰真实存档。离开商店、暂停菜单、重生、关窗口时自动存。给 `Player` 新增需要持久化的字段时,要同时改 `save` 和 `load`。

## 约定

- 代码注释和界面文字用中文。源文件是 UTF-8,`build.bat` 只写 ASCII(cmd 是 GBK)。
- 爆炸规则(用户明确要求的核心体验):燃油耗尽或车身归零 → 爆炸 → 丢失全部货物 + 扣现金 25% → 地面重生,油加满、车身修满。货舱里的熔岩之心和宝藏回到地底。
- 用户明确不要:货舱满时自动丢掉便宜矿("满了就是满了")。满舱时贵重矿闪光加倍,只作提示。
