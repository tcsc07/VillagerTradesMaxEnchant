package com.example.maxenchant.mixin;

import com.example.maxenchant.ConfigHolder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「随机附魔」函数产出的附魔书恒为最高等级。
 *
 * <h2>26.x 的村民交易机制（与 1.21 不同）</h2>
 * 26.x 把村民交易重构成完全数据驱动的模型：原版交易全部由
 * {@code net.minecraft.world.item.trading.VillagerTrade} 承载，它内部持有一个
 * {@code List<LootItemFunction> givenItemModifiers}，在
 * {@code VillagerTrade#getOffer} 里依次 apply 到物品上。
 *
 * <p>也就是说，26.x 的「村民卖什么、附魔怎么来」<b>又回到了 loot function 路径</b>：
 * 附魔书由 {@link EnchantRandomlyFunction} 生成，附魔装备/工具由
 * {@link net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction}
 * 生成。这正是本 mixin 能生效的原因。
 *
 * <h2>本 Mixin 做什么</h2>
 * {@code EnchantRandomlyFunction#enchantItem} 会先
 * {@code Mth.nextInt(random, getMinLevel(), getMaxLevel())} 随机取一个等级，
 * 再调用 {@code ItemStack.enchant(Holder, int)}。这里把该调用的等级参数
 * 固定为附魔的最高等级，从而让附魔书直接满级。
 *
 * <p>附魔书走的是 {@code STORED_ENCHANTMENTS} 组件：当物品是 {@code BOOK} 时，
 * {@code enchantItem} 会先把它替换成 {@code ENCHANTED_BOOK} 再调 {@code enchant}，
 * 而 {@code ItemStack.enchant} 对附魔书会自动写入 stored 组件，
 * 因此这里只需改写等级参数即可，无需关心是哪个组件。
 *
 * <h2>版本注意事项</h2>
 * 26.x 的 {@code enchantItem} 是<b>实例方法</b>（参数为
 * {@code ItemStack, Holder<Enchantment>, LootContext}）。
 * 这与 1.21 系列不同 —— 1.21 全系都是 {@code private static} 且只有 2 个参数。
 * Mixin 要求回调方法与目标方法的 static 修饰一致，因此这里的回调<b>不能</b>声明为
 * {@code static}。
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {

	@Redirect(
		method = "enchantItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;enchant(Lnet/minecraft/core/Holder;I)V"
		)
	)
	private void maxTradeEnchant$forceMaxLevel(
		ItemStack stack,
		Holder<Enchantment> enchantment,
		int level
	) {
		if (ConfigHolder.maxEnchantEnabled()) {
			// 隔离模式下，只把原版(minecraft 命名空间)附魔顶到最高，第三方附魔保持原级随机。
			if (ConfigHolder.vanillaOnly() && !ConfigHolder.isVanillaEnchantment(enchantment)) {
				stack.enchant(enchantment, level);
				return;
			}
			stack.enchant(enchantment, enchantment.value().getMaxLevel());
		} else {
			// 开关关闭：保持原版随机等级。
			stack.enchant(enchantment, level);
		}
	}
}
