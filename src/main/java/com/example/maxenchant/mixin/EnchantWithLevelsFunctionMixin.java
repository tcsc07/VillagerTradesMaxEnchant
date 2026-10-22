package com.example.maxenchant.mixin;

import com.example.maxenchant.ConfigHolder;
import com.example.maxenchant.EnchantMaximizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「按等级附魔」函数产出的装备 / 工具 / 武器恒为最高附魔等级。
 *
 * <h2>26.x 的村民交易机制</h2>
 * 26.x 的村民交易是数据驱动的：{@code net.minecraft.world.item.trading.VillagerTrade}
 * 持有 {@code List<LootItemFunction> givenItemModifiers}，在
 * {@code VillagerTrade#getOffer} 里依次 apply。附魔装备走
 * {@link EnchantWithLevelsFunction}，附魔书走
 * {@link net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction}。
 *
 * <h2>为什么用 @Inject(RETURN) 而不是 @ModifyArg 改 power</h2>
 * 1.21 版的实现是把「附魔等级上限(power)」参数改成 30，靠原版随机取到最高。
 * 但原版 {@code enchantItem} 里还有「等级不超过附魔自身最大等级」与
 * 「可能降级」的逻辑，且不同附魔的 maxLevel 不同（1~5），靠 power=30
 * 并不能保证每个附魔都恰好顶到自己的上限。
 *
 * <p>26.x 改为<b>等函数跑完、拿到成品 ItemStack 后</b>，直接把每个已选中的
 * 附魔等级改写为 {@code getMaxLevel()}（见 {@link EnchantMaximizer}）。
 * 这样无论原版内部怎么随机，产物一定是满级。
 *
 * <h2>版本注意事项</h2>
 * 26.x 的 {@code EnchantWithLevelsFunction} 继承
 * {@code LootItemConditionalFunction}，只有一个
 * {@code public ItemStack run(ItemStack, LootContext)}（实现父类的 protected abstract），
 * <b>没有</b> 1.21 系列的 {@code enchantItem} 私有辅助方法。
 * 其 {@code run} 是实例方法，因此本回调不是 static。
 */
@Mixin(EnchantWithLevelsFunction.class)
public abstract class EnchantWithLevelsFunctionMixin {

	@Inject(method = "run", at = @At("RETURN"))
	private void maxTradeEnchant$forceMaxLevel(
		ItemStack stack,
		LootContext context,
		CallbackInfoReturnable<ItemStack> cir
	) {
		if (!ConfigHolder.maxEnchantEnabled()) {
			return;
		}
		EnchantMaximizer.maximize(cir.getReturnValue());
	}
}
