package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;

public class CocoaDecorator extends TreeDecorator {
    public static final Codec<CocoaDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability").xmap(CocoaDecorator::new, (decorator) -> {
        return decorator.probability;
    }).codec();
    private final float probability;

    public CocoaDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.COCOA;
    }

    @Override
    public void place(TreeDecorator.Context generator) {
        if (generator.logs().isEmpty()) return; // Paper
        RandomSource randomSource = generator.random();
        if (!(randomSource.nextFloat() >= this.probability)) {
            List<BlockPos> list = generator.logs();
            int i = list.get(0).getY();
            list.stream().filter((pos) -> {
                return pos.getY() - i <= 2;
            }).forEach((pos) -> {
                for(Direction direction : Direction.Plane.HORIZONTAL) {
                    if (randomSource.nextFloat() <= 0.25F) {
                        Direction direction2 = direction.getOpposite();
                        BlockPos blockPos = pos.offset(direction2.getStepX(), 0, direction2.getStepZ());
                        if (generator.isAir(blockPos)) {
                            generator.setBlock(blockPos, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, Integer.valueOf(randomSource.nextInt(3))).setValue(CocoaBlock.FACING, direction));
                        }
                    }
                }

            });
        }
    }
}
