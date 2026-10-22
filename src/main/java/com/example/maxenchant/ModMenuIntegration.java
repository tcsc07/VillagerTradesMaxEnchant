package com.example.maxenchant;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu 集成：为本模组提供「配置」按钮。
 *
 * 配置界面采用 Cloth Config 的 ConfigBuilder 手动构建。所有选项文字通过
 * {@link #t(String, String)} 按照每游戏当前语言在「中文 / English」之间切换：
 * 语言代码以 zh 开头（如 zh_cn/zh_tw）显示中文，否则显示英文。该方案不依赖
 * 语言文件加载，因此必定能随游戏语言即时切换、绝不出现"仍显示中文/键串"。
 *
 * 读写仍走 AutoConfig 的 ModConfig（config/max-level-enchanting.json），三个
 * mixin 从 AutoConfig.getConfigHolder().getConfig() 读实时值，因此在界面修改
 * 后点保存即对村民附魔 / 治愈倒计时即时生效。
 */
public class ModMenuIntegration implements ModMenuApi {

	/** 按当前游戏语言返回中/英文本。语言代码以 zh 开头当作简体/繁体中文。 */
	private static Component t(String zh, String en) {
		String lang;
		try {
			lang = Minecraft.getInstance().options.languageCode;
		} catch (Throwable ex) {
			lang = "en_us";
		}
		return Component.literal(lang == null || lang.startsWith("zh") ? zh : en);
	}

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// 兜底注册 AutoConfig（客户端入口通常已注册，这里防重复）。
		try {
			AutoConfig.getConfigHolder(ModConfig.class);
		} catch (Throwable t) {
			AutoConfig.register(ModConfig.class, me.shedaniel.autoconfig.serializer.GsonConfigSerializer::new);
		}

		return this::createConfigScreen;
	}

	private Screen createConfigScreen(Screen parent) {
		ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(t("VillagerTradesMaxEnchant", "VillagerTradesMaxEnchant"))
			.setSavingRunnable(() -> AutoConfig.getConfigHolder(ModConfig.class).save());

		ConfigEntryBuilder entry = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(t("常规", "General"));

		// 1) 满级附魔开关
		general.addEntry(entry.startBooleanToggle(t("满级附魔", "Max enchantments"), cfg.maxEnchantEnabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> { cfg.maxEnchantEnabled = v; ConfigHolder.refreshCache(); })
			.setTooltip(t(
				"开启后，村民交易的附魔书、武器、装备、工具、弓/弩等一律以最高附魔等级生成；关闭后 mixin 注入立即短路，产物与原版完全一致。",
				"When enabled, enchanted books, weapons, armor, tools and bows/crossbows sold by villagers are generated at the highest enchantment level; when disabled the injection short-circuits immediately and results match vanilla exactly."))
			.build());

		// 2) 治愈加速开关
		general.addEntry(entry.startBooleanToggle(t("治愈加速", "Cure speed-up"), cfg.cureEnabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> { cfg.cureEnabled = v; ConfigHolder.refreshCache(); })
			.setTooltip(t(
				"开启后，被僵尸感染的村民在服用金苹果+虚弱药水后，治愈(转化回村民)的倒计时会缩短到下方设定的秒数。",
				"When enabled, a zombie villager's cure (conversion back to villager) countdown after a golden apple + Weakness effect is shortened to the seconds set below."))
			.build());

		// 3) 治愈秒数（可自定义）
		general.addEntry(entry.startIntField(t("治愈秒数（秒）", "Cure seconds"), cfg.cureSeconds)
			.setDefaultValue(5)
			.setMin(1)
			.setMax(600)
			.setSaveConsumer(v -> { cfg.cureSeconds = v; ConfigHolder.refreshCache(); })
			.setTooltip(t(
				"僵尸村民治愈倒计时的秒数（1 tick = 1/20 秒，默认 5 秒）。可填 1~600。",
				"The zombie villager cure countdown in seconds (1 tick = 1/20 s, default 5). Range 1~600."))
			.build());

		// 4) 隔离模式开关（开启后只对原版附魔生效）
		general.addEntry(entry.startBooleanToggle(t("隔离模式（仅原版）", "Isolation mode (vanilla only)"), cfg.vanillaOnly)
			.setDefaultValue(false)
			.setSaveConsumer(v -> { cfg.vanillaOnly = v; ConfigHolder.refreshCache(); })
			.setTooltip(t(
				"开启后，满级附魔只作用于原版(minecraft 命名空间)的附魔；第三方 mod 添加的自定义附魔保持原等级，不会被顶到最高级。治愈加速不受影响。",
				"When enabled, max-level enchanting applies only to vanilla (minecraft namespace) enchantments; custom enchantments added by other mods keep their original level. Cure speed-up is unaffected."))
			.build());

		return builder.build();
	}
}