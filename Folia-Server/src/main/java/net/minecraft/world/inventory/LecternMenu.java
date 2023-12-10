package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity.LecternInventory;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftInventoryLectern;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
// CraftBukkit end

public class LecternMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryLectern inventory = new CraftInventoryLectern(this.lectern);
        this.bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
    private static final int DATA_COUNT = 1;
    private static final int SLOT_COUNT = 1;
    public static final int BUTTON_PREV_PAGE = 1;
    public static final int BUTTON_NEXT_PAGE = 2;
    public static final int BUTTON_TAKE_BOOK = 3;
    public static final int BUTTON_PAGE_JUMP_RANGE_START = 100;
    private final Container lectern;
    private final ContainerData lecternData;

    // CraftBukkit start - add player
    public LecternMenu(int i, Inventory playerinventory) {
        this(i, new SimpleContainer(1), new SimpleContainerData(1), playerinventory);
    }

    public LecternMenu(int i, Container iinventory, ContainerData icontainerproperties, Inventory playerinventory) {
        // CraftBukkit end
        super(MenuType.LECTERN, i);
        checkContainerSize(iinventory, 1);
        checkContainerDataCount(icontainerproperties, 1);
        this.lectern = iinventory;
        this.lecternData = icontainerproperties;
        this.addSlot(new Slot(iinventory, 0, 0, 0) {
            @Override
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(icontainerproperties);
        this.player = (Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        int j;
        io.papermc.paper.event.player.PlayerLecternPageChangeEvent playerLecternPageChangeEvent; CraftInventoryLectern bukkitView; // Paper

        if (id >= 100) {
            j = id - 100;
            this.setData(0, j);
            return true;
        } else {
            switch (id) {
                case 1:
                    j = this.lecternData.get(0);
                    // Paper start
                    bukkitView = (CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.LEFT, j, j - 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end
                    return true;
                case 2:
                    j = this.lecternData.get(0);
                    // Paper start
                    bukkitView = (CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.RIGHT, j, j + 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end
                    return true;
                case 3:
                    if (!player.mayBuild()) {
                        return false;
                    }

                    // CraftBukkit start - Event for taking the book
                    PlayerTakeLecternBookEvent event = new PlayerTakeLecternBookEvent(this.player, ((CraftInventoryLectern) this.getBukkitView().getTopInventory()).getHolder());
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return false;
                    }
                    // CraftBukkit end
                    ItemStack itemstack = this.lectern.removeItemNoUpdate(0);

                    this.lectern.setChanged();
                    if (!player.getInventory().add(itemstack)) {
                        player.drop(itemstack, false);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (this.lectern instanceof LecternInventory && !((LecternInventory) this.lectern).getLectern().hasBook()) return false; // CraftBukkit
        if (!this.checkReachable) return true; // CraftBukkit
        return this.lectern.stillValid(player);
    }

    public ItemStack getBook() {
        return this.lectern.getItem(0);
    }

    public int getPage() {
        return this.lecternData.get(0);
    }
}
