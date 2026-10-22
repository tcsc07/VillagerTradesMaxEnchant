package com.example.maxenchant.mixin;

import com.example.maxenchant.ConfigHolder;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「被僵尸感染的村民」的治愈(转化回村民)倒计时缩短到配置的秒数。
 *
 * <p>26.x 的 {@code ZombieVillager} 位于
 * {@code net.minecraft.world.entity.monster.zombie.ZombieVillager}
 * （1.21.11 起随本次包路径调整，26.x 沿用）。
 *
 * <h2>原版逻辑</h2>
 * {@code ZombieVillager#tick()} 每 tick 执行
 * {@code villagerConversionTime -= getConversionProgress()}；
 * 当 {@code villagerConversionTime <= 0} 时调用 {@code finishConversion(...)} 变回村民。
 * 初始 {@code villagerConversionTime} 约为 3600~6000 tick（180 ~ 300 秒）。
 *
 * <h2>本 Mixin 做什么</h2>
 * 在 {@code tick()} 开头把 {@code villagerConversionTime} 压到
 * 「配置秒数 × 20」tick（默认 5 秒 = 100 tick），从而把治愈时长整体缩短。
 * 只在 {@code isConverting()} 为真（已喂金苹果且带虚弱）时才压，避免影响未开始治愈的个体。
 */
@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerMixin {

	@Accessor("villagerConversionTime")
	protected abstract int getVillagerConversionTime();

	@Accessor("villagerConversionTime")
	protected abstract void setVillagerConversionTime(int ticks);

	@Inject(method = "tick", at = @At("HEAD"))
	private void maxenchant$shortenCure(CallbackInfo ci) {
		if (!ConfigHolder.cureEnabled()) {
			return;
		}
		int targetTicks = ConfigHolder.cureSecondsTicks();
		if (targetTicks <= 0) {
			return; // 配置<=0：关闭该特性，保持原版
		}
		ZombieVillager self = (ZombieVillager) (Object) this;
		if (!self.isConverting()) {
			return;
		}
		if (getVillagerConversionTime() > targetTicks) {
			setVillagerConversionTime(targetTicks);
		}
	}
}
