package com.example.maxenchant;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * 命令权限判定的兼容门面。
 *
 * <p>MC 1.21.11 起命令权限系统重构为 {@code PermissionSet}：
 * <ul>
 *   <li>{@code CommandSourceStack.permissions()} 返回 {@code PermissionSet}；</li>
 *   <li>{@code PermissionSet.hasPermission(Permission)} 判断是否拥有某权限；</li>
 *   <li>{@code Permissions.COMMANDS_GAMEMASTER} 对应旧 OP 等级 2（默认管理员/服主）。</li>
 * </ul>
 * 26.x 沿用该模型。
 *
 * <p>抽成门面的作用：若将来需要支持 1.21 ~ 1.21.10（使用
 * {@code hasPermission(int)} 的旧 OP 等级制），只需替换本文件实现，
 * 所有调用点无需改动。
 */
public final class PermissionBridge {

	private PermissionBridge() {
	}

	/** 命令源是否拥有管理员/服主权限（GAMEMASTERS 及以上，对应旧 OP 等级 2）。 */
	public static boolean isAdmin(CommandSourceStack src) {
		return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
