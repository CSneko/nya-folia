package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import net.minecraft.util.FastBufferedInputStream;

public class RegionFileVersion {
    public static final Int2ObjectMap<RegionFileVersion> VERSIONS = new Int2ObjectOpenHashMap<>(); // Paper - private -> public
    public static final RegionFileVersion VERSION_GZIP = register(new RegionFileVersion(1, (stream) -> {
        return new FastBufferedInputStream(new GZIPInputStream(stream));
    }, (stream) -> {
        return new BufferedOutputStream(new GZIPOutputStream(stream));
    }));
    public static final RegionFileVersion VERSION_DEFLATE = register(new RegionFileVersion(2, (stream) -> {
        return new FastBufferedInputStream(new InflaterInputStream(stream));
    }, (stream) -> {
        return new BufferedOutputStream(new DeflaterOutputStream(stream));
    }));
    public static final RegionFileVersion VERSION_NONE = register(new RegionFileVersion(3, (stream) -> {
        return stream;
    }, (stream) -> {
        return stream;
    }));

    // Paper Start - Configurable region compression format
    public static RegionFileVersion getCompressionFormat() {
        return switch (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.compressionFormat) {
            case GZIP -> VERSION_GZIP;
            case ZLIB -> VERSION_DEFLATE;
            case NONE -> VERSION_NONE;
        };
    }
    // Paper End

    private final int id;
    private final RegionFileVersion.StreamWrapper<InputStream> inputWrapper;
    private final RegionFileVersion.StreamWrapper<OutputStream> outputWrapper;

    private RegionFileVersion(int id, RegionFileVersion.StreamWrapper<InputStream> inputStreamWrapper, RegionFileVersion.StreamWrapper<OutputStream> outputStreamWrapper) {
        this.id = id;
        this.inputWrapper = inputStreamWrapper;
        this.outputWrapper = outputStreamWrapper;
    }

    private static RegionFileVersion register(RegionFileVersion version) {
        VERSIONS.put(version.id, version);
        return version;
    }

    @Nullable
    public static RegionFileVersion fromId(int id) {
        return VERSIONS.get(id);
    }

    public static boolean isValidVersion(int id) {
        return VERSIONS.containsKey(id);
    }

    public int getId() {
        return this.id;
    }

    public OutputStream wrap(OutputStream outputStream) throws IOException {
        return this.outputWrapper.wrap(outputStream);
    }

    public InputStream wrap(InputStream inputStream) throws IOException {
        return this.inputWrapper.wrap(inputStream);
    }

    @FunctionalInterface
    interface StreamWrapper<O> {
        O wrap(O object) throws IOException;
    }
}
