package com.example.maxenchant;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

/**
 * 本模组的全局配置（由 Cloth Config 的 AutoConfig 负责读写与生成配置界面）。
 *
 * <p>通过 Mod Menu 打开「村民交易满级附魔」的配置界面即可在游戏中实时修改下列选项。
 * 配置文件保存在 {@code config/max-level-enchanting.json}。
 */
@Config(name = "max-level-enchanting")
public class ModConfig implements ConfigData {

	/** 满级附魔总开关：开启后村民售卖的附魔书/武器/装备/工具等一律为最高附魔等级。 */
	public boolean maxEnchantEnabled = true;

	/** 僵尸村民治愈加速总开关。 */
	public boolean cureEnabled = true;

	/** 治愈倒计时（秒）。会把正在转化的僵尸村民的剩余转化时间压到 cureSeconds 秒。 */
	public int cureSeconds = 5;

	/**
	 * 隔离模式（仅原版）：开启后，满级附魔只作用于原版（minecraft 命名空间）的附魔，
	 * 第三方 mod 添加的自定义附魔等级保持不变，不会被顶到最高级。
	 * 治愈加速本身针对原版僵尸村民，不因该开关变化。
	 */
	public boolean vanillaOnly = false;
}
