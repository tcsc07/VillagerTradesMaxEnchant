package com.example.maxenchant;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 把一件物品上「已经选中的附魔」的等级统一顶到各自最高等级。
 *
 * <p>核心策略：<b>不改变随机挑选附魔种类的逻辑</b>（保留原版手感），
 * 只把已经挑中的每个附魔的等级设为该附魔的 {@code getMaxLevel()}。
 *
 * <h2>必须同时处理两个数据组件（关键）</h2>
 * MC 1.20.5 起附魔被拆成两个独立的数据组件：
 * <ul>
 *   <li>{@link DataComponents#ENCHANTMENTS} —— 直接附魔在物品上，
 *       用于附魔<b>武器 / 装备 / 工具</b>；</li>
 *   <li>{@link DataComponents#STORED_ENCHANTMENTS} —— 附魔书里「存着」的附魔，
 *       用于<b>附魔书</b>。</li>
 * </ul>
 * 只处理前者会导致「装备满级但附魔书仍是原版随机等级」。
 * 因此这里对两个组件分别处理，任一存在即改写。
 */
public final class EnchantMaximizer {

	private EnchantMaximizer() {
	}

	/**
	 * 就地修改传入的 {@link ItemStack}：把其附魔组件里每个附魔的等级设为最高。
	 * 同时处理 ENCHANTMENTS 与 STORED_ENCHANTMENTS。
	 *
	 * @return 是否发生了改写
	 */
	public static boolean maximize(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean a = maximizeComponent(stack, DataComponents.ENCHANTMENTS);
		boolean b = maximizeComponent(stack, DataComponents.STORED_ENCHANTMENTS);
		return a || b;
	}

	/**
	 * 处理单个附魔组件。组件不存在或为空时返回 false。
	 *
	 * @param stack 目标物品
	 * @param type  组件类型（ENCHANTMENTS / STORED_ENCHANTMENTS）
	 */
	private static boolean maximizeComponent(ItemStack stack, DataComponentType<ItemEnchantments> type) {
		ItemEnchantments enchantments = stack.get(type);
		if (enchantments == null || enchantments.isEmpty()) {
			return false;
		}
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
		boolean changed = false;
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			// 隔离模式下：只把原版(minecraft 命名空间)附魔顶到最高，第三方附魔保持原级。
			if (ConfigHolder.vanillaOnly() && !ConfigHolder.isVanillaEnchantment(holder)) {
				continue;
			}
			int max = holder.value().getMaxLevel();
			if (mutable.getLevel(holder) != max) {
				mutable.set(holder, max);
				changed = true;
			}
		}
		if (changed) {
			stack.set(type, mutable.toImmutable());
		}
		return changed;
	}
}
