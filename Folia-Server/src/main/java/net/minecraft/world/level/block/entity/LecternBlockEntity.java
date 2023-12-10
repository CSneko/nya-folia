package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Lectern;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class LecternBlockEntity extends BlockEntity implements Clearable, MenuProvider, CommandSource { // CraftBukkit - ICommandListener

    public static final int DATA_PAGE = 0;
    public static final int NUM_DATA = 1;
    public static final int SLOT_BOOK = 0;
    public static final int NUM_SLOTS = 1;
    // CraftBukkit start - add fields and methods
    public final Container bookAccess = new LecternInventory();
    public class LecternInventory implements Container {

        public List<HumanEntity> transaction = new ArrayList<>();
        private int maxStack = 1;

        @Override
        public List<ItemStack> getContents() {
            return Arrays.asList(LecternBlockEntity.this.book);
        }

        @Override
        public void onOpen(CraftHumanEntity who) {
            this.transaction.add(who);
        }

        @Override
        public void onClose(CraftHumanEntity who) {
            this.transaction.remove(who);
        }

        @Override
        public List<HumanEntity> getViewers() {
            return this.transaction;
        }

        @Override
        public void setMaxStackSize(int i) {
            this.maxStack = i;
        }

        @Override
        public Location getLocation() {
            if (LecternBlockEntity.this.level == null) return null;
            return CraftLocation.toBukkit(LecternBlockEntity.this.worldPosition, LecternBlockEntity.this.level.getWorld());
        }

        @Override
        public InventoryHolder getOwner() {
            return (Lectern) LecternBlockEntity.this.getOwner();
        }

        public LecternBlockEntity getLectern() {
            return LecternBlockEntity.this;
        }
        // CraftBukkit end

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return LecternBlockEntity.this.book.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? LecternBlockEntity.this.book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book.split(amount);

                if (LecternBlockEntity.this.book.isEmpty()) {
                    LecternBlockEntity.this.onBookItemRemove();
                }

                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot == 0) {
                ItemStack itemstack = LecternBlockEntity.this.book;

                LecternBlockEntity.this.book = ItemStack.EMPTY;
                LecternBlockEntity.this.onBookItemRemove();
                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        // CraftBukkit start
        public void setItem(int slot, ItemStack stack) {
            if (slot == 0) {
                LecternBlockEntity.this.setBook(stack);
                if (LecternBlockEntity.this.getLevel() != null) {
                    LecternBlock.resetBookState(null, LecternBlockEntity.this.getLevel(), LecternBlockEntity.this.getBlockPos(), LecternBlockEntity.this.getBlockState(), LecternBlockEntity.this.hasBook());
                }
            }
        }
        // CraftBukkit end

        @Override
        public int getMaxStackSize() {
            return this.maxStack; // CraftBukkit
        }

        @Override
        public void setChanged() {
            LecternBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(LecternBlockEntity.this, player) && LecternBlockEntity.this.hasBook();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void clearContent() {}
    };
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? LecternBlockEntity.this.page : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                LecternBlockEntity.this.setPage(value);
            }

        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    ItemStack book;
    int page;
    private int pageCount;

    public LecternBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.LECTERN, pos, state);
        this.book = ItemStack.EMPTY;
    }

    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        return this.book.is(Items.WRITABLE_BOOK) || this.book.is(Items.WRITTEN_BOOK);
    }

    public void setBook(ItemStack book) {
        this.setBook(book, (Player) null);
    }

    void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.resetBookState((Entity) null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
    }

    public void setBook(ItemStack book, @Nullable Player player) {
        this.book = this.resolveBook(book, player);
        this.page = 0;
        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.setChanged();
    }

    public void setPage(int currentPage) {
        int j = Mth.clamp(currentPage, 0, this.pageCount - 1);

        if (j != this.page) {
            this.page = j;
            this.setChanged();
            if (this.level != null) LecternBlock.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlockState()); // CraftBukkit
        }

    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? (float) this.getPage() / ((float) this.pageCount - 1.0F) : 1.0F;

        return Mth.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack book, @Nullable Player player) {
        if (this.level instanceof ServerLevel && book.is(Items.WRITTEN_BOOK)) {
            WrittenBookItem.resolveBookComponents(book, this.createCommandSourceStack(player), player);
        }

        return book;
    }

    // CraftBukkit start
    @Override
    public void sendSystemMessage(Component message) {
    }

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitSender(wrapper) : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, this);
    }

    @Override
    public boolean acceptsSuccess() {
        return false;
    }

    @Override
    public boolean acceptsFailure() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    // CraftBukkit end
    private CommandSourceStack createCommandSourceStack(@Nullable Player player) {
        String s;
        Object object;

        if (player == null) {
            s = "Lectern";
            object = Component.literal("Lectern");
        } else {
            s = player.getName().getString();
            object = player.getDisplayName();
        }

        Vec3 vec3d = Vec3.atCenterOf(this.worldPosition);

        // CraftBukkit - this
        return new CommandSourceStack(this, vec3d, Vec2.ZERO, (ServerLevel) this.level, 2, s, (Component) object, this.level.getServer(), player);
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("Book", 10)) {
            this.book = this.resolveBook(ItemStack.of(nbt.getCompound("Book")), (Player) null);
        } else {
            this.book = ItemStack.EMPTY;
        }

        this.pageCount = WrittenBookItem.getPageCount(this.book);
        this.page = Mth.clamp(nbt.getInt("Page"), 0, this.pageCount - 1);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (!this.getBook().isEmpty()) {
            nbt.put("Book", this.getBook().save(new CompoundTag()));
            nbt.putInt("Page", this.page);
        }

    }

    @Override
    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new LecternMenu(syncId, this.bookAccess, this.dataAccess, playerInventory); // CraftBukkit
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.lectern");
    }
}
