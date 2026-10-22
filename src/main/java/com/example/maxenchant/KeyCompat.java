package com.example.maxenchant;

import net.minecraft.resources.ResourceKey;

/**
 * ResourceKey 命名空间判定的兼容门面。
 *
 * <p>MC 1.21 系列的 {@code ResourceKey.location()} 在 1.21.11 起被移除，改为
 * {@code ResourceKey.identifier()}；26.x 沿用后者。本模组面向 26.2 / 26.3，
 * 故统一使用 {@code identifier()}。
 *
 * <p>单独抽成一个门面类，是为了将来若需要回移到 1.21.10 及更早版本时，
 * 只需替换本文件的实现，而不必改动所有调用点。
 */
public final class KeyCompat {

	private KeyCompat() {
	}

	/** 该资源键是否属于 minecraft 命名空间。 */
	public static boolean isMinecraftResource(ResourceKey<?> key) {
		return "minecraft".equals(key.identifier().getNamespace());
	}
}
