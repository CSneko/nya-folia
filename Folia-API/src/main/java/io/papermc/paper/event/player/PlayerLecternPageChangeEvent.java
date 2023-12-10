package io.papermc.paper.event.player;

import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerLecternPageChangeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private boolean cancelled;
    private final Lectern lectern;
    private final ItemStack book;
    private final PageChangeDirection pageChangeDirection;
    private final int oldPage;
    private int newPage;

    public PlayerLecternPageChangeEvent(@NotNull Player player, @NotNull Lectern lectern, @NotNull ItemStack book, @NotNull PageChangeDirection pageChangeDirection, int oldPage, int newPage) {
        super(player);
        this.lectern = lectern;
        this.book = book;
        this.pageChangeDirection = pageChangeDirection;
        this.oldPage = oldPage;
        this.newPage = newPage;
    }

    /**
     * Gets the lectern involved.
     *
     * @return the Lectern
     */
    @NotNull
    public Lectern getLectern() {
        return lectern;
    }

    /**
     * Gets the current ItemStack on the lectern.
     *
     * @return the ItemStack on the Lectern
     */
    @NotNull
    public ItemStack getBook() {
        return this.book;
    }

    /**
     * Gets the page change direction. This is essentially returns which button the player clicked, left or right.
     *
     * @return the page change direction
     */
    @NotNull
    public PageChangeDirection getPageChangeDirection() {
        return pageChangeDirection;
    }

    /**
     * Gets the page changed from. <i>Pages are 0-indexed.</i>
     *
     * @return the page changed from
     */
    public int getOldPage() {
        return oldPage;
    }

    /**
     * Gets the page changed to. <i>Pages are 0-indexed.</i>
     *
     * @return the page changed to
     */
    public int getNewPage() {
        return newPage;
    }

    /**
     * Sets the page changed to. <i>Pages are 0-indexed.</i>
     * Page indices that are greater than the number of pages will show the last page.
     *
     * @param newPage the new paged changed to
     */
    public void setNewPage(int newPage) {
        this.newPage = newPage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public enum PageChangeDirection {
        LEFT,
        RIGHT,
    }
}
