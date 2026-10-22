package com.example.maxenchant;

import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端入口：通过 {@link ConfigRegistry} 注册 Cloth Config 的 AutoConfig，
 * 让 ModConfig 拥有持久化 (config/max-level-enchanting.json) 与实时可变的配置单例，
 * 并初始化 ConfigHolder 的内存缓存。
 *
 * 重复注册由 ConfigRegistry 的幂等标志兜底，单机内嵌服务端同进程也不会冲突。
 */
public class MaxEnchantClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ConfigRegistry.ensureRegistered();
	}
}