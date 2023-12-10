package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public class NoteBlock extends Block {

    public static final EnumProperty<NoteBlockInstrument> INSTRUMENT = BlockStateProperties.NOTEBLOCK_INSTRUMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty NOTE = BlockStateProperties.NOTE;
    public static final int NOTE_VOLUME = 3;

    public NoteBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.HARP)).setValue(NoteBlock.NOTE, 0)).setValue(NoteBlock.POWERED, false));
    }

    private BlockState setInstrument(LevelAccessor world, BlockPos pos, BlockState state) {
        NoteBlockInstrument blockpropertyinstrument = world.getBlockState(pos.above()).instrument();

        if (blockpropertyinstrument.worksAboveNoteBlock()) {
            return (BlockState) state.setValue(NoteBlock.INSTRUMENT, blockpropertyinstrument);
        } else {
            NoteBlockInstrument blockpropertyinstrument1 = world.getBlockState(pos.below()).instrument();
            NoteBlockInstrument blockpropertyinstrument2 = blockpropertyinstrument1.worksAboveNoteBlock() ? NoteBlockInstrument.HARP : blockpropertyinstrument1;

            return (BlockState) state.setValue(NoteBlock.INSTRUMENT, blockpropertyinstrument2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return this.defaultBlockState(); // Paper - place without considering instrument
        return this.setInstrument(ctx.getLevel(), ctx.getClickedPos(), this.defaultBlockState());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return state; // Paper - prevent noteblock instrument from updating
        boolean flag = direction.getAxis() == Direction.Axis.Y;

        return flag ? this.setInstrument(world, pos, state) : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return; // Paper - prevent noteblock powered-state from updating
        boolean flag1 = world.hasNeighborSignal(pos);

        if (flag1 != (Boolean) state.getValue(NoteBlock.POWERED)) {
            if (flag1) {
                this.playNote((Entity) null, state, world, pos);
                state = world.getBlockState(pos); // CraftBukkit - SPIGOT-5617: update in case changed in event
            }

            world.setBlock(pos, (BlockState) state.setValue(NoteBlock.POWERED, flag1), 3);
        }

    }

    private void playNote(@Nullable Entity entity, BlockState state, Level world, BlockPos pos) {
        if (((NoteBlockInstrument) state.getValue(NoteBlock.INSTRUMENT)).worksAboveNoteBlock() || world.getBlockState(pos.above()).isAir()) {
            // CraftBukkit start
            // org.bukkit.event.block.NotePlayEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callNotePlayEvent(world, pos, state.getValue(NoteBlock.INSTRUMENT), state.getValue(NoteBlock.NOTE));
            // if (event.isCancelled()) {
            //     return;
            // }
            // CraftBukkit end
            // Paper - TODO any way to cancel the game event?
            world.blockEvent(pos, this, 0, 0);
            world.gameEvent(entity, GameEvent.NOTE_BLOCK_PLAY, pos);
        }

    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS) && hit.getDirection() == Direction.UP) {
            return InteractionResult.PASS;
        } else if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (!io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) state = (BlockState) state.cycle(NoteBlock.NOTE); // Paper - prevent noteblock note from updating
            world.setBlock(pos, state, 3);
            this.playNote(player, state, world, pos);
            player.awardStat(Stats.TUNE_NOTEBLOCK);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        if (!world.isClientSide) {
            this.playNote(player, state, world, pos);
            player.awardStat(Stats.PLAY_NOTEBLOCK);
        }
    }

    public static float getPitchFromNote(int note) {
        return (float) Math.pow(2.0D, (double) (note - 12) / 12.0D);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        NoteBlockInstrument blockpropertyinstrument = (NoteBlockInstrument) state.getValue(NoteBlock.INSTRUMENT);
        // Paper start - move NotePlayEvent call to fix instrument/note changes
        org.bukkit.event.block.NotePlayEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callNotePlayEvent(world, pos, blockpropertyinstrument, state.getValue(NOTE));
        if (event.isCancelled()) return false;
        // Paper end
        float f;

        if (blockpropertyinstrument.isTunable()) {
            int k = event.getNote().getId(); // Paper

            f = NoteBlock.getPitchFromNote(k);
            world.addParticle(ParticleTypes.NOTE, (double) pos.getX() + 0.5D, (double) pos.getY() + 1.2D, (double) pos.getZ() + 0.5D, (double) k / 24.0D, 0.0D, 0.0D);
        } else {
            f = 1.0F;
        }

        Holder holder;

        if (blockpropertyinstrument.hasCustomSound()) {
            ResourceLocation minecraftkey = this.getCustomSoundId(world, pos);

            if (minecraftkey == null) {
                return false;
            }

            holder = Holder.direct(SoundEvent.createVariableRangeEvent(minecraftkey));
        } else {
            holder = org.bukkit.craftbukkit.block.data.CraftBlockData.toNMS(event.getInstrument(), NoteBlockInstrument.class).getSoundEvent(); // Paper
        }

        world.playSeededSound((Player) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, holder, SoundSource.RECORDS, 3.0F, f, world.random.nextLong());
        return true;
    }

    @Nullable
    private ResourceLocation getCustomSoundId(Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos.above());

        if (tileentity instanceof SkullBlockEntity) {
            SkullBlockEntity tileentityskull = (SkullBlockEntity) tileentity;

            return tileentityskull.getNoteBlockSound();
        } else {
            return null;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NoteBlock.INSTRUMENT, NoteBlock.POWERED, NoteBlock.NOTE);
    }
}
