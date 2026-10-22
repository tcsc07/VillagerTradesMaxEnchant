# 村民交易满级附魔

村民交易的附魔书 / 武器 / 装备 / 工具**始终为最高附魔等级**，并把僵尸村民的治愈倒计时缩短到可配置的秒数（默认 5 秒）。

当前支持 **Minecraft 26.2 / 26.3**（Fabric），后续会向下更新。

> English documentation: [README.md](README.md)。

---

## 功能

| 功能 | 说明 | 默认 |
|---|---|---|
| 满级附魔 | 村民售卖的附魔书、剑/斧/镐/锹/锄、盔甲、弓/弩/三叉戟/钓鱼竿等，其附魔一律为各自最高等级 | 开 |
| 治愈加速 | 被僵尸感染的村民，在喂金苹果 + 虚弱药水后，转化回村民的倒计时压缩到指定秒数 | 开（5 秒） |
| 隔离模式 | 只对 `minecraft:` 命名空间的原版附魔顶满级，第三方 mod 的自定义附魔保持原等级 | 关 |

---

## 各版本村民交易附魔的差异

村民交易系统的底层框架仍沿用 1.14 的大更新。26.x 对村民交易与附魔做了大幅重构，与 1.21 相比，1.21 时代的写法在 26.x 上**会静默失效**——不崩溃、不报错，但附魔依然是随机的。

### 1. 村民交易改回"数据驱动 + loot function"路径

| 版本 | 交易实现 | 附魔从哪来 |
|---|---|---|
| 1.21 ~ 1.21.11 | 硬编码内部类 `VillagerTrades$EnchantBookForEmeralds` / `$EnchantedItemForEmeralds`，各自实现 `getOffer(...)` | 直接在 `getOffer` 里调 `EnchantmentHelper.createBook(...)` / `enchantItem(...)` |
| 26.2 / 26.3 | 内部类**全部消失**，改用数据驱动：`net.minecraft.world.item.trading.VillagerTrade` 持有 `List<LootItemFunction> givenItemModifiers`，在 `getOffer(LootContext)` 里依次 `apply` | **重新回到 loot function**：附魔书走 `EnchantRandomlyFunction`，附魔装备走 `EnchantWithLevelsFunction` |

> 包路径也变了：`VillagerTrades` 从 `net.minecraft.world.entity.npc[.villager]` 移到了 `net.minecraft.world.item.trading`。

**结论**：在 1.21 上必须 mixin `getOffer`（因为 loot function 根本不参与村民交易）；在 26.x 上反过来——mixin loot function 就够了。

### 2. `EnchantRandomlyFunction#enchantItem` 从 static 变成实例方法

| 版本 | 签名 | Mixin 回调修饰符 |
|---|---|---|
| 1.21 全系 | `private static ItemStack enchantItem(ItemStack, Holder<Enchantment>)` | 必须 `static` |
| 26.x | `private ItemStack enchantItem(ItemStack, Holder<Enchantment>, LootContext)` | **不能** `static` |

Mixin 强制要求回调方法与目标方法的 `static` 修饰一致，否则抛：

```
InvalidInjectionException: non-static callback method ... targets a static method which is not supported
```

这条在两边是**相反**的，是本项目跨版本时最容易踩的坑。

### 3. `EnchantWithLevelsFunction` 没有 `enchantItem`

26.x 的 `EnchantWithLevelsFunction` 继承 `LootItemConditionalFunction`，只有：

```java
public ItemStack run(ItemStack, LootContext)   // 实现父类的 protected abstract
```

没有 1.21 里的私有辅助方法 `enchantItem`。因此这里用 `@Inject(method = "run", at = @At("RETURN"))`，等函数跑完拿到成品 `ItemStack`，再逐个把附魔等级写成 `getMaxLevel()`。

> 为什么不用 `@ModifyArg` 把 power 改成 30：原版 `enchantItem` 内部还有"等级不超过附魔自身 maxLevel"以及随机降级逻辑，而各附魔 maxLevel 不同（1~5），power=30 并不能保证每个附魔都恰好顶到自己的上限。

### 4. 附魔书与装备是两个不同的数据组件

MC 1.20.5 起附魔被拆开：

- `DataComponents.ENCHANTMENTS` → 装备 / 工具 / 武器
- `DataComponents.STORED_ENCHANTMENTS` → **附魔书**

只处理前者会出现"装备满级、但附魔书仍是随机低等级"。`EnchantMaximizer` 对两个组件都做处理。

### 5. 26.x 的构建配置（Loom）

26.x 客户端 jar **本身就是非混淆的**（类名/方法名即官方命名），并且 Mojang 不再随 version json 提供 `client_mappings`（`downloads` 里只有 `client` / `server`）。因此：

```gradle
// ❌ 会报 "Failed to find official mojang mappings for 26.2"
mappings loom.officialMojangMappings()
```

正确写法是 identity mappings —— 一个只有命名空间声明、没有任何条目的 Tiny v2 头：

```gradle
loom.noIntermediateMappings()           // 不拉 intermediary、不做中间名重映射
mappings loom.layered {
    mappings(file("gradle/identity.tiny"))
}
```

`gradle/identity.tiny` 内容（`\t` 为真实制表符）：

```
tiny	2	0	official	named
```

这样 official / intermediary / named 三个命名空间下名字完全一致，编译与 mixin 直接使用 26.x 官方名字。注意：只用空 `loom.layered {}` 会因为映射树为空导致 `MappingSourceNsSwitch` NPE（`srcNamespace is null`），必须有一个真实的层。

其它环境要求：

- **Java 25**（26.x 的 `javaVersion.majorVersion = 25`，`java-runtime-epsilon`）
- Loom `1.19.1`，Gradle `9.8`
- Fabric Loader `0.19.5+`

---

## 构建

```bash
# 26.2（默认）
./gradlew build

# 26.3
./gradlew build -Pminecraft_version=26.3 \
                -Pfabric_version=0.164.0+26.3 \
                -Pcloth_config_version=26.3.159
```

该 mod 编译产物：`build/libs/max-level-enchanting-1.0.0.jar`

两个版本共用同一套源码：26.2 与 26.3 的 `EnchantRandomlyFunction` / `EnchantWithLevelsFunction` / `VillagerTrade#getOffer` 签名完全一致。

### 依赖版本对照

| MC | Fabric API | Cloth Config | Mod Menu |
|---|---|---|---|
| 26.2 | `0.161.0+26.2` | `26.2.155` | `20.0.3` |
| 26.3 | `0.164.0+26.3` | `26.3.159` | `20.0.x` |

> Cloth Config 与 Mod Menu 只在编译期需要（`modCompileOnly` / `compileOnly`），运行期由玩家自行安装。若想用本地 jar 离线构建：
> `./gradlew build -Pcloth_config_local=/path/to/cloth-config.jar -Pmodmenu_local=/path/to/modmenu.jar`

该 mod 会声明对 `minecraft` 的依赖范围为 `~26.2` / `~26.3`（非精确版本），因此同一份 jar 可在两代小版本之间通用。如需手动覆盖：`-Pmc_dep_range="~26.2"`。

---

## 安装

1. 安装 Fabric Loader（**0.19.5 或更高**）与 **Java 25** 运行时
2. 把 `max-level-enchanting-<ver>.jar` 放进 `mods/`
3. 一并安装：
   - [Fabric API](https://modrinth.com/mod/fabric-api)（必需）
   - [Cloth Config API](https://modrinth.com/mod/cloth-config)（必需，配置界面）
   - [Mod Menu](https://modrinth.com/mod/modmenu)（可选，用于打开图形配置界面）

> 客户端与服务端都要装（`environment: "*"`）。服务端只装本 mod 也能生效，但配置只能用命令改。

---

## 配置

### 图形界面

通过 Mod Menu → 「村民交易满级附魔」→ 配置。选项：

- 满级附魔（开/关）
- 治愈加速（开/关）
- 治愈秒数（1~600）
- 隔离模式（仅原版）

### 命令（管理员）

```
/maxtradeenchant status                        查看当前设置
/maxtradeenchant maxenchant on|off             满级附魔开关
/maxtradeenchant cure on|off                   治愈加速开关
/maxtradeenchant cureseconds <1-600>           治愈秒数
/maxtradeenchant help                          帮助
```

需要 GAMEMASTER 权限（旧 OP 等级 2）。服务端控制台天然可用。命令改完立即对全服生效，无需重启。

### 配置文件

`config/max-level-enchanting.json`

---

## 源码结构

```
src/main/java/com/example/maxenchant/
├── ModConfig.java                     配置数据类（Cloth AutoConfig）
├── ConfigRegistry.java                幂等注册 AutoConfig
├── ConfigHolder.java                  volatile 内存缓存，供 mixin 零开销读取
├── EnchantMaximizer.java              核心：把两个附魔组件的等级顶满
├── KeyCompat.java                     ResourceKey#identifier() 命名空间判定门面
├── PermissionBridge.java              permissions().hasPermission(...) 门面
├── ServerCommands.java                服务端命令入口
├── MaxEnchantClient.java              客户端入口
├── ModMenuIntegration.java            Mod Menu 配置界面（中/英自动切换）
└── mixin/
    ├── EnchantRandomlyFunctionMixin.java   附魔书 → 满级（@Redirect enchant 调用）
    ├── EnchantWithLevelsFunctionMixin.java 附魔装备 → 满级（@Inject RETURN）
    └── ZombieVillagerMixin.java            治愈倒计时压缩
```

设计上把两个易变 API（`ResourceKey` 命名空间、命令权限）抽成了门面类 `KeyCompat` / `PermissionBridge`，mixin 只依赖 `EnchantMaximizer`，因此将来若要回移到 1.21.x 只需替换门面 + 切换 mixin 目标。

---

## 许可

MIT，见 [LICENSE](LICENSE)。
