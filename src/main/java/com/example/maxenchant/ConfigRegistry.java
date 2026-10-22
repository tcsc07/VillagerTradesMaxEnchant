package com.example.maxenchant;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * 统一的配置注册入口。
 *
 * <p>客户端入口 {@link MaxEnchantClient} 与服务端入口 {@link ServerCommands}
 * 都通过 {@link #ensureRegistered()} 注册 AutoConfig，并用 boolean 标志保证
 * 单进程内只注册一次 —— 单机（客户端内嵌服务端）时两个入口同一进程都存在，
 * 也不会重复注册。
 *
 * <p>注册完成后立即用已落盘/默认的配置刷新 {@link ConfigHolder} 的内存缓存，
 * 让开关即时就绪。
 */
public final class ConfigRegistry {

	private static volatile boolean registered = false;

	private ConfigRegistry() {
	}

	/** 幂等注册 AutoConfig，并初始化内存缓存。可安全地重复调用。 */
	public static synchronized void ensureRegistered() {
		if (registered) {
			return;
		}
		try {
			AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
			registered = true;
		} catch (Throwable t) {
			// 已注册或环境异常：忽略，ConfigHolder 会 null 安全回退。
		}
		ConfigHolder.refreshCache();
	}

	/** 是否已成功注册（供需要判断的环境使用）。 */
	public static boolean isRegistered() {
		return registered;
	}
}
