package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EnchantmentTableBlockEntity extends BlockEntity implements Nameable {
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();
    private Component name;

    public EnchantmentTableBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.ENCHANTING_TABLE, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.hasCustomName()) {
            nbt.putString("CustomName", Component.Serializer.toJson(this.name));
        }

    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("CustomName", 8)) {
            this.name = io.papermc.paper.util.MCUtil.getBaseComponentFromNbt("CustomName", nbt); // Paper - Catch ParseException
        }

    }

    public static void bookAnimationTick(Level world, BlockPos pos, BlockState state, EnchantmentTableBlockEntity blockEntity) {
        blockEntity.oOpen = blockEntity.open;
        blockEntity.oRot = blockEntity.rot;
        Player player = world.getNearestPlayer((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D, 3.0D, false);
        if (player != null) {
            double d = player.getX() - ((double)pos.getX() + 0.5D);
            double e = player.getZ() - ((double)pos.getZ() + 0.5D);
            blockEntity.tRot = (float)Mth.atan2(e, d);
            blockEntity.open += 0.1F;
            if (blockEntity.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float f = blockEntity.flipT;

                do {
                    blockEntity.flipT += (float)(RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while(f == blockEntity.flipT);
            }
        } else {
            blockEntity.tRot += 0.02F;
            blockEntity.open -= 0.1F;
        }

        while(blockEntity.rot >= (float)Math.PI) {
            blockEntity.rot -= ((float)Math.PI * 2F);
        }

        while(blockEntity.rot < -(float)Math.PI) {
            blockEntity.rot += ((float)Math.PI * 2F);
        }

        while(blockEntity.tRot >= (float)Math.PI) {
            blockEntity.tRot -= ((float)Math.PI * 2F);
        }

        while(blockEntity.tRot < -(float)Math.PI) {
            blockEntity.tRot += ((float)Math.PI * 2F);
        }

        float g;
        for(g = blockEntity.tRot - blockEntity.rot; g >= (float)Math.PI; g -= ((float)Math.PI * 2F)) {
        }

        while(g < -(float)Math.PI) {
            g += ((float)Math.PI * 2F);
        }

        blockEntity.rot += g * 0.4F;
        blockEntity.open = Mth.clamp(blockEntity.open, 0.0F, 1.0F);
        ++blockEntity.time;
        blockEntity.oFlip = blockEntity.flip;
        float h = (blockEntity.flipT - blockEntity.flip) * 0.4F;
        float i = 0.2F;
        h = Mth.clamp(h, -0.2F, 0.2F);
        blockEntity.flipA += (h - blockEntity.flipA) * 0.9F;
        blockEntity.flip += blockEntity.flipA;
    }

    @Override
    public Component getName() {
        return (Component)(this.name != null ? this.name : Component.translatable("container.enchant"));
    }

    public void setCustomName(@Nullable Component customName) {
        this.name = customName;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }
}
