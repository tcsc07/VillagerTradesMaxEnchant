package com.example.maxenchant;

import me.shedaniel.autoconfig.AutoConfig;

/**
 * 读取运行时的实时配置。
 *
 * <p>单机（含存档内联服务器）场景下，客户端入口初始化时会执行
 * {@code AutoConfig.register(ModConfig.class, GsonConfigSerializer::new)}，
 * 之后无论客户端线程还是存档服务器线程，{@code getConfig()} 拿到的都是同一个
 * 可被 Mod Menu 配置界面原地修改的 {@link ModConfig} 单例，因此在游戏中改配置
 * 能立即对村民附魔 / 治愈倒计时生效，无需重启。
 *
 * <p>为让开关判断零开销，这里额外保留一份 {@code volatile} 内存缓存。
 * Mixin 在改写物品之前先读缓存：一旦关闭就立即返回，不对物品做任何改写，
 * 附魔结果与原版逐字节一致；开启时下一次交易立即生效。
 *
 * <p>若在未注册 AutoConfig 的环境下调用，会安全回退到默认值，不会抛异常。
 */
public final class ConfigHolder {

	private ConfigHolder() {
	}

	private static volatile boolean CACHE_MAX_ENCHANT = true;
	private static volatile boolean CACHE_CURE = true;
	private static volatile boolean CACHE_VANILLA_ONLY = false;
	private static volatile int CACHE_CURE_TICKS = 5 * 20;

	/**
	 * 若 AutoConfig 已注册则用其单例值刷新缓存。
	 * 未注册时返回 false（表示未刷新）。启动与配置保存后都应调用一次。
	 */
	public static boolean refreshCache() {
		try {
			ModConfig c = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
			CACHE_MAX_ENCHANT = c.maxEnchantEnabled;
			CACHE_CURE = c.cureEnabled;
			CACHE_VANILLA_ONLY = c.vanillaOnly;
			CACHE_CURE_TICKS = c.cureSeconds * 20;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 是否开启满级附魔（默认开启）。关闭时注入逻辑立即短路，行为与原版一致。 */
	public static boolean maxEnchantEnabled() {
		return CACHE_MAX_ENCHANT;
	}

	/** 是否开启治愈加速（默认开启）。 */
	public static boolean cureEnabled() {
		return CACHE_CURE;
	}

	/** 治愈倒计时秒数转 tick（默认 5 秒 = 100 tick）。 */
	public static int cureSecondsTicks() {
		return CACHE_CURE_TICKS;
	}

	/** 是否开启隔离模式（仅对原版附魔生效，默认关闭）。 */
	public static boolean vanillaOnly() {
		return CACHE_VANILLA_ONLY;
	}

	/** 判断一个附魔是否为原版（minecraft 命名空间）。 */
	public static boolean isVanillaEnchantment(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder) {
		return holder.unwrapKey()
			.map(KeyCompat::isMinecraftResource)
			.orElse(false);
	}
}
