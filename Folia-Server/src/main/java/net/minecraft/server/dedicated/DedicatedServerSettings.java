package net.minecraft.server.dedicated;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

// CraftBukkit start
import java.io.File;
import joptsimple.OptionSet;
// CraftBukkit end

public class DedicatedServerSettings {

    private final Path source;
    private DedicatedServerProperties properties;

    // CraftBukkit start
    public DedicatedServerSettings(OptionSet optionset) {
        this.source = ((File) optionset.valueOf("config")).toPath();
        this.properties = DedicatedServerProperties.fromFile(this.source, optionset);
        // CraftBukkit end
    }

    public DedicatedServerProperties getProperties() {
        return this.properties;
    }

    public void forceSave() {
        this.properties.store(this.source);
    }

    public DedicatedServerSettings update(UnaryOperator<DedicatedServerProperties> applier) {
        (this.properties = (DedicatedServerProperties) applier.apply(this.properties)).store(this.source);
        return this;
    }
}
