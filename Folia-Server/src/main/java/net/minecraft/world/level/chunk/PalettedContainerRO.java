package net.minecraft.world.level.chunk;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public interface PalettedContainerRO<T> {
    T get(int x, int y, int z);

    void getAll(Consumer<T> action);

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse void write(FriendlyByteBuf buf);
    void write(FriendlyByteBuf buf, @javax.annotation.Nullable com.destroystokyo.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex);
    // Paper end

    int getSerializedSize();

    boolean maybeHas(Predicate<T> predicate);

    void count(PalettedContainer.CountConsumer<T> counter);

    PalettedContainer<T> recreate();

    PalettedContainerRO.PackedData<T> pack(IdMap<T> idList, PalettedContainer.Strategy paletteProvider);

    public static record PackedData<T>(List<T> paletteEntries, Optional<LongStream> storage) {
    }

    public interface Unpacker<T, C extends PalettedContainerRO<T>> {
        DataResult<C> read(IdMap<T> idList, PalettedContainer.Strategy paletteProvider, PalettedContainerRO.PackedData<T> serialize);
    }
}
