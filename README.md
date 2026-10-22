# VillagerTradesMaxEnchant

> Villager trades max-level enchanting

Enchanted books, weapons, armor and tools sold by villagers are **always generated at their maximum enchantment level**, and zombie villager cure time is shortened to a configurable number of seconds (5 by default).

Currently supports **Minecraft 26.2 / 26.3** (Fabric); older versions are in the pipeline, with 26.1 and 1.21 up next.

> 中文说明见 [README.zh-CN.md](README.zh-CN.md)。

---

## Features

| Feature              | Description                                                                                                                                                         | Default |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| Max-level enchanting | Enchanted books, swords/axes/pickaxes/shovels/hoes, armor, bows/crossbows, tridents and fishing rods sold by villagers always carry their enchantments at max level | On      |
| Cure speed-up        | A zombie villager being cured (golden apple + Weakness) converts back in the configured number of seconds                                                           | On (5s) |
| Vanilla-only mode    | Only enchantments in the `minecraft:` namespace are maxed; custom enchantments from other mods keep their original level                                            | Off     |

---

## How villager trade enchanting differs between versions

The villager trading system still builds on the 1.14 overhaul.

26.x reworked villager trades and enchanting. Compared with 1.21, a 1.21-era implementation **fails silently** on 26.x — it does not crash and does not log an error, the enchantments are simply still random.

The notes below are the most reusable part of this repository for anyone porting a villager-trade or enchanting mod to 26.x.

### 1. Villager trades went back to "data-driven + loot function"

| Version        | Trade implementation                                                                                                                                                                                                       | Where enchantments come from                                                                                                    |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 1.21 – 1.21.11 | Hardcoded inner classes `VillagerTrades$EnchantBookForEmeralds` / `$EnchantedItemForEmeralds`, each implementing `getOffer(...)`                                                                                           | `EnchantmentHelper.createBook(...)` / `enchantItem(...)` called directly inside `getOffer`                                      |
| 26.2 / 26.3    | Inner classes **are gone entirely**. Trades are data-driven: `net.minecraft.world.item.trading.VillagerTrade` holds a `List<LootItemFunction> givenItemModifiers` and applies them in order inside `getOffer(LootContext)` | **Back to loot functions**: enchanted books use `EnchantRandomlyFunction`, enchanted equipment uses `EnchantWithLevelsFunction` |

> The path moved too: `VillagerTrades` went from `net.minecraft.world.entity.npc[.villager]` to `net.minecraft.world.item.trading`.

**Therefore**: on 1.21 you must mixin `getOffer` (loot functions never run for villager trades); on 26.x it is the opposite — mixin the loot functions and you are done.

### 2. `EnchantRandomlyFunction#enchantItem` changed from static to an instance method

| Version       | Signature                                                                    | Required callback modifier |
| ------------- | ---------------------------------------------------------------------------- | -------------------------- |
| All of 1.21.x | `private static ItemStack enchantItem(ItemStack, Holder<Enchantment>)`       | must be `static`           |
| 26.x          | `private ItemStack enchantItem(ItemStack, Holder<Enchantment>, LootContext)` | must **not** be `static`   |

Mixin requires the callback's `static` modifier to match the target method, otherwise it throws:

```
InvalidInjectionException: non-static callback method ... targets a static method which is not supported
```

This is the opposite on the two version lines, and the easiest trap to fall into when porting.

### 3. `EnchantWithLevelsFunction` has no `enchantItem`

In 26.x, `EnchantWithLevelsFunction` extends `LootItemConditionalFunction` and exposes only:

```java
public ItemStack run(ItemStack, LootContext)   // implements the parent's protected abstract
```

The `enchantItem` method from 1.21 does not exist. This mod therefore uses `@Inject(method = "run", at = @At("RETURN"))`: once the function has finished and produced an `ItemStack`, it walks the enchantment components and rewrites every level to `getMaxLevel()`.

> If `@ModifyArg` were used to set the power argument to 30: vanilla's `enchantItem` still clamps the level to each enchantment's own `maxLevel` and may randomly downgrade it, and different enchantments cap out at different levels (1–5). A power of 30 does not guarantee that every enchantment lands exactly on its own maximum.

### 4. Books and equipment use two different data components

Since MC 1.20.5 enchantments are split:

- `DataComponents.ENCHANTMENTS` → equipment / tools / weapons
- `DataComponents.STORED_ENCHANTMENTS` → **enchanted books**

Handling only the first produces "equipment is maxed but books are still random". `EnchantMaximizer` covers both components.

### 5. Building for 26.x (Loom)

The 26.x client jar is **already deobfuscated** — class and method names are the official ones — and Mojang no longer ships `client_mappings` in the version JSON (its `downloads` object only has `client` and `server`). As a result:

```gradle
// ❌ fails with "Failed to find official mojang mappings for 26.2"
mappings loom.officialMojangMappings()
```

The correct approach is identity mappings — a Tiny v2 header with namespace declarations and no entries at all:

```gradle
loom.noIntermediateMappings()           // don't fetch intermediary, don't remap through it
mappings loom.layered {
    mappings(file("gradle/identity.tiny"))
}
```

Contents of `gradle/identity.tiny` (`\t` are real tab characters):

```
tiny	2	0	official	named
```

This makes the `official`, `intermediary` and `named` namespaces identical, so compilation and mixins use the 26.x official names directly. Note that an empty `loom.layered {}` is **not** enough — it leaves the mapping tree empty and causes `MappingSourceNsSwitch` to throw an NPE (`srcNamespace is null`). At least one real layer must be present.

Other environment requirements:

- **Java 25** (26.x declares `javaVersion.majorVersion = 25`, i.e. `java-runtime-epsilon`)
- Loom `1.19.1`, Gradle `9.8`
- Fabric Loader `0.19.5+`

---

## Building

```bash
# 26.2 (default)
./gradlew build

# 26.3
./gradlew build -Pminecraft_version=26.3 \
                -Pfabric_version=0.164.0+26.3 \
                -Pcloth_config_version=26.3.159
```

After building you get: `build/libs/VillagerTradesMaxEnchant-1.0.0.jar`

Both versions share a single source tree: `EnchantRandomlyFunction`, `EnchantWithLevelsFunction` and `VillagerTrade#getOffer` have identical signatures in 26.2 and 26.3.

### Dependency versions

| MC   | Fabric API     | Cloth Config | Mod Menu |
| ---- | -------------- | ------------ | -------- |
| 26.2 | `0.161.0+26.2` | `26.2.155`   | `20.0.3` |
| 26.3 | `0.164.0+26.3` | `26.3.159`   | `20.0.x` |

> Cloth Config and Mod Menu are compile-time only (`modCompileOnly` / `compileOnly`); players supply them at runtime. To build offline against local jars:  
> `./gradlew build -Pcloth_config_local=/path/to/cloth-config.jar -Pmodmenu_local=/path/to/modmenu.jar`

The mod currently declares `minecraft` as `~26.2` / `~26.3`, but one jar works across the 26.x patch line — unless a future 26.x release reworks trades again. Override it with `-Pmc_dep_range="~26.x"`.

---

## Installation

1. Install Fabric Loader (**0.19.5 or newer**) and a **Java 25** runtime
2. Drop `VillagerTradesMaxEnchant-v1.0.0-<MC>.jar` into `mods/`
3. Also install:
   - [Fabric API](https://modrinth.com/mod/fabric-api) (required)
   - [Cloth Config API](https://modrinth.com/mod/cloth-config) (required, for the config screen)
   - [Mod Menu](https://modrinth.com/mod/modmenu) (optional, to open the GUI config)

> Install on both client and server (`environment: "*"`). The mod works on a server by itself, but then the config can only be changed via commands.

---

## Configuration

### GUI

Mod Menu → "VillagerTradesMaxEnchant" → Config. Options:

- Max Enchant (on/off)
- Cure Speed-up (on/off)
- Cure Seconds (1–600)
- Vanilla Only

### Commands (operators)

```
/maxtradeenchant status                        show the current settings
/maxtradeenchant maxenchant on|off             toggle max-level enchanting
/maxtradeenchant cure on|off                   toggle cure speed-up
/maxtradeenchant cureseconds <1-600>           set the cure duration in seconds
/maxtradeenchant help                          help
```

In-game this requires GAMEMASTER permission (legacy OP level 2). Changes apply server-wide immediately, no restart needed.

### Config file

`config/max-level-enchanting.json`

---

## Source layout

```
src/main/java/com/example/maxenchant/
├── ModConfig.java                     config data class (Cloth AutoConfig)
├── ConfigRegistry.java                idempotent AutoConfig registration
├── ConfigHolder.java                  volatile in-memory cache for zero-cost reads from mixins
├── EnchantMaximizer.java              core: raise both enchantment components to max level
├── KeyCompat.java                     facade for ResourceKey#identifier() namespace checks
├── PermissionBridge.java              facade for permissions().hasPermission(...)
├── ServerCommands.java                server command entrypoint
├── MaxEnchantClient.java              client entrypoint
├── ModMenuIntegration.java            Mod Menu config screen (auto zh / en)
└── mixin/
    ├── EnchantRandomlyFunctionMixin.java   enchanted books → max level (@Redirect on enchant)
    ├── EnchantWithLevelsFunctionMixin.java enchanted equipment → max level (@Inject at RETURN)
    └── ZombieVillagerMixin.java            shorten the cure countdown
```

---

## License

MIT — see [LICENSE](LICENSE).
