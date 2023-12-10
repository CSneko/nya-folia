package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class RecordItem extends Item {

    private static final Map<SoundEvent, RecordItem> BY_NAME = Maps.newHashMap();
    private final int analogOutput;
    private final SoundEvent sound;
    private final int lengthInTicks;

    protected RecordItem(int comparatorOutput, SoundEvent sound, Item.Properties settings, int lengthInSeconds) {
        super(settings);
        this.analogOutput = comparatorOutput;
        this.sound = sound;
        this.lengthInTicks = lengthInSeconds * 20;
        RecordItem.BY_NAME.put(this.sound, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(Blocks.JUKEBOX) && !(Boolean) iblockdata.getValue(JukeboxBlock.HAS_RECORD)) {
            ItemStack itemstack = context.getItemInHand();

            if (!world.isClientSide) {
                if (true) return InteractionResult.sidedSuccess(world.isClientSide); // CraftBukkit - handled in ItemStack // Paper - fix duplicate animate packet
                Player entityhuman = context.getPlayer();
                BlockEntity tileentity = world.getBlockEntity(blockposition);

                if (tileentity instanceof JukeboxBlockEntity) {
                    JukeboxBlockEntity tileentityjukebox = (JukeboxBlockEntity) tileentity;

                    tileentityjukebox.setFirstItem(itemstack.copy());
                    world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entityhuman, iblockdata));
                }

                itemstack.shrink(1);
                if (entityhuman != null) {
                    entityhuman.awardStat(Stats.PLAY_RECORD);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public int getAnalogOutput() {
        return this.analogOutput;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        tooltip.add(this.getDisplayName().withStyle(ChatFormatting.GRAY));
    }

    public MutableComponent getDisplayName() {
        return Component.translatable(this.getDescriptionId() + ".desc");
    }

    @Nullable
    public static RecordItem getBySound(SoundEvent sound) {
        return (RecordItem) RecordItem.BY_NAME.get(sound);
    }

    public SoundEvent getSound() {
        return this.sound;
    }

    public int getLengthInTicks() {
        return this.lengthInTicks;
    }
}
