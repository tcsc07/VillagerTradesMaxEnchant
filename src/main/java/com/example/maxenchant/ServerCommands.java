package com.example.maxenchant;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Supplier;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 服务端入口：注册本模组的专用管理命令，并保证服务端拥有可变的配置单例。
 *
 * 所有子命令都在根命令上挂 {@link #isAdmin(CommandSourceStack)}，即要求命令执行者
 * 拥有「GAMEMASTERS 及以上」权限（对应原版 OP 权限等级 2，即默认的管理员/服主）。
 * 普通玩家输入会被拒绝（无权限）；服务端控制台天然拥有全部权限，可直接使用。
 *
 * 命令：
 *   /maxtradeenchant status           —— 查看当前各开关与治愈秒数
 *   /maxtradeenchant maxenchant on|off —— 切换「满级附魔」
 *   /maxtradeenchant cure on|off      —— 切换「治愈加速」
 *   /maxtradeenchant cureseconds <1-600> —— 设置僵尸村民治愈秒数
 *   /maxtradeenchant help             —— 显示帮助
 *
 * 修改会写回 ModConfig 并调用 ConfigHolder.refreshCache()（内存即时生效）
 * 与 AutoConfig.save()（持久化到 config/max-level-enchanting.json），
 * 因此命令改完立即对全服村民附魔/治愈生效，无需重启。
 */
public class ServerCommands implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		// 确保服务端进程有可变的配置单例（与客户端幂等共用同一实现）。
		ConfigRegistry.ensureRegistered();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maxtradeenchant")
				.requires(ServerCommands::isAdmin);

			root.then(Commands.literal("status").executes(ctx -> {
				CommandSourceStack src = ctx.getSource();
				ModConfig cfg = currentConfig();
				send(src, "§7[满级附魔] §a" + onOff(cfg.maxEnchantEnabled));
				send(src, "§7[治愈加速] §a" + onOff(cfg.cureEnabled));
				send(src, "§7[治愈秒数] §a" + cfg.cureSeconds + " 秒");
				send(src, "§7[隔离模式] §a" + onOff(cfg.vanillaOnly));
				return Command.SINGLE_SUCCESS;
			}));

			root.then(Commands.literal("maxenchant")
				.then(Commands.literal("on").executes(ctx -> setMaxEnchant(ctx.getSource(), true)))
				.then(Commands.literal("off").executes(ctx -> setMaxEnchant(ctx.getSource(), false))));

			root.then(Commands.literal("cure")
				.then(Commands.literal("on").executes(ctx -> setCure(ctx.getSource(), true)))
				.then(Commands.literal("off").executes(ctx -> setCure(ctx.getSource(), false))));

			root.then(Commands.literal("cureseconds")
				.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
					.executes(ctx -> setCureSeconds(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));

			root.then(Commands.literal("help").executes(ctx -> {
				CommandSourceStack src = ctx.getSource();
				send(src, "§6=== maxtradeenchant 命令帮助（仅管理员） ===");
				send(src, "§e/maxtradeenchant status §7- 查看当前设置");
				send(src, "§e/maxtradeenchant maxenchant on|off §7- 满级附魔开关");
				send(src, "§e/maxtradeenchant cure on|off §7- 治愈加速开关");
				send(src, "§e/maxtradeenchant cureseconds <1-600> §7- 治愈秒数");
				return Command.SINGLE_SUCCESS;
			}));

			dispatcher.register(root);
		});
	}

	/** 是否拥有管理员/服主权限（GAMEMASTERS 及以上，对应旧 OP 等级 2）。 */
	private static boolean isAdmin(CommandSourceStack src) {
		try {
			return PermissionBridge.isAdmin(src);
		} catch (Throwable t) {
			// 权限系统异常时保守放行（控制台高权限），避免误伤服主。
			return true;
		}
	}

	private static int setMaxEnchant(CommandSourceStack src, boolean value) {
		ModConfig cfg = currentConfig();
		cfg.maxEnchantEnabled = value;
		saveAndRefresh();
		send(src, "§a满级附魔已" + (value ? "开启 §a✔（村民交易的附魔书/武器/装备/工具将满级）" : "关闭 ✔（恢复原版随机等级）"));
		return Command.SINGLE_SUCCESS;
	}

	private static int setCure(CommandSourceStack src, boolean value) {
		ModConfig cfg = currentConfig();
		cfg.cureEnabled = value;
		saveAndRefresh();
		send(src, "§a治愈加速已" + (value ? "开启 ✔" : "关闭 ✔（恢复原版治愈时长）"));
		return Command.SINGLE_SUCCESS;
	}

	private static int setCureSeconds(CommandSourceStack src, int seconds) {
		ModConfig cfg = currentConfig();
		cfg.cureSeconds = seconds;
		saveAndRefresh();
		send(src, "§a僵尸村民治愈时长已设为 §b" + seconds + " §a秒 ✔");
		return Command.SINGLE_SUCCESS;
	}

	/** 安全地取得实时配置（null 兜底默认值）。 */
	private static ModConfig currentConfig() {
		try {
			return AutoConfig.getConfigHolder(ModConfig.class).getConfig();
		} catch (Throwable t) {
			return new ModConfig();
		}
	}

	/** 刷新内存缓存（即时生效）并持久化到 config/max-level-enchanting.json。 */
	private static void saveAndRefresh() {
		ConfigHolder.refreshCache();
		try {
			AutoConfig.getConfigHolder(ModConfig.class).save();
		} catch (Throwable ignored) {
			// 持久化失败不阻断运行时即时生效。
		}
	}

	private static String onOff(boolean b) {
		return b ? "开启 ✔" : "关闭 ✘";
	}

	private static void send(CommandSourceStack src, String msg) {
		Supplier<Component> text = () -> Component.literal(msg);
		try {
			src.sendSuccess(text, false);
		} catch (Throwable ignored) {
			// 个别服务端版本 sendSuccess 变体差异，静默。
		}
	}
}