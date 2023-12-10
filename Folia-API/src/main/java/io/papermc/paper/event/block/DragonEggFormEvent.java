package io.papermc.paper.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockFormEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the {@link EnderDragon} is defeated (killed) in a {@link DragonBattle},
 * causing a {@link Material#DRAGON_EGG} (more formally: {@link #getNewState()})
 * to possibly appear depending on {@link #isCancelled()}.
 * <b>This event might be cancelled by default depending on
 * eg. {@link DragonBattle#hasBeenPreviouslyKilled()} and server configuration.</b>
 */
public class DragonEggFormEvent extends BlockFormEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	private final DragonBattle dragonBattle;
	private boolean cancelled;
	
	public DragonEggFormEvent(@NotNull Block block, @NotNull BlockState newState,
			@NotNull DragonBattle dragonBattle) {
		super(block, newState);
		this.dragonBattle = dragonBattle;
	}
	
	@Override
	public boolean isCancelled() {
		return cancelled;
	}
	
	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
	
	/**
	 * Gets the {@link DragonBattle} associated with this event.
	 * Keep in mind that the {@link EnderDragon} is already dead
	 * when this event is called.
	 *
	 * @return the dragon battle
	 */
	@NotNull
	public DragonBattle getDragonBattle() {
		return dragonBattle;
	}
	
	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	@NotNull
	public static HandlerList getHandlerList() {
		return handlers;
	}
}
