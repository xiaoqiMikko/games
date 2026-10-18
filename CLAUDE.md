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
javac -encoding UTF-8 -nowarn -d "$S/testcls" src/lavadigger/*.java tools/lavadigger/SelfTest.java
java -cp "$S/testcls" lavadigger.SelfTest "$S/shots"     # 退出码 0 = 全部通过
```

- 游戏开着时 jar 文件被占用,`build.bat` 会在最后一步报 `FileSystemException ... LavaDigger.jar` 并 BUILD FAILED。这时要让用户先关掉游戏,不要擅自结束 java 进程(用户可能正在玩)。
- 自测没有测试框架,`SelfTest.main` 按顺序跑全部场景;要只测一项就临时注释掉其他段落。它会关闭存档和音效(`SaveManager.enabled` / `Sfx.enabled`),存读档测试会临时改写 `user.home`。
- 控制台是 GBK,自测输出的中文会乱码:看 `[OK]`/`[FAIL]` 和退出码,或在 java 后加 `-Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8`。
- 改了画面相关代码,跑完自测要看 `shots/*.png` 确认(例如字体不支持的符号会显示成方框,菜单箭头因此改为手画三角形)。

## 架构

- **`GamePanel`** 是核心,几乎所有逻辑都在这里:状态机 `State`(TITLE/PLAY/SHOP/PAUSE/EXPLODING/REPORT)、物理、钻探、商店菜单、特效、渲染、键盘输入。
  - 用 Swing `Timer` 驱动 `update(dt)`;PLAY 状态按 1/120 秒子步长更新 `updatePlay`。自测直接调用 `update()` 和 `render()`,所以这两个方法不能依赖窗口。
  - 渲染先画到固定 960×640 的 `back` 缓冲,再等比缩放到窗口,窗口大小可随意调。
  - 商店菜单由 `shopItems()` 每帧重新生成 `MenuItem` 列表(文字、是否可用、动作),新增商品改这里即可。
- **钻探不走物理**:`startDig` 记下起止坐标,`digProgress` 插值移动车辆,`finishDig` 结算该格(矿入货舱 / 熔岩扣血 / 瓦斯 3×3 爆炸)。只能向下钻,或在地面上贴墙横向钻,不能向上钻。
- **`World`**:`byte[]` 网格(48×420,每格 32px,第 8 行 `SURFACE` 是地表)。矿物编码为 `ORE + 矿物下标`。`generate(seed)` 按深度概率生成,再做成团扩散、挖空洞、单独摆放 6 颗熔岩之心,最后清理出生点。
- **数值表集中在两处**:`Upgrades`(6 种部件 × 7 档的数值和价格、道具)和 `Mineral`(价值和深度分布);油耗、油价、摔落阈值、救援费等常量在 `GamePanel` 顶部。调平衡只改这三处。
- **`Shop`**:四家商店的列位置决定了地表哪几格是水泥(`World.generate` 用 `isUnderShop`),以及进店判定(`Shop.at`)。挪动商店位置会同时影响这两处。
- **`Textures`**:全部贴图在启动时用代码生成,没有图片资源;土层颜色按深度分 5 档(`band()`)。**`Sfx`** 用代码合成 PCM 音效,声卡不可用时静默。
- **`SaveManager`**:存到 `~/.lava-digger/save.properties`,整张地图用 Base64 保存。离开商店、暂停菜单、重生、关窗口时自动存。给 `Player` 新增需要持久化的字段时,要同时改 `save` 和 `load`。

## 约定

- 代码注释和界面文字用中文。源文件是 UTF-8,`build.bat` 只写 ASCII(cmd 是 GBK)。
- 爆炸规则(用户明确要求的核心体验):燃油耗尽或车身归零 → 爆炸 → 丢失全部货物 + 扣现金 25% → 地面重生,油加满、车身修满。
