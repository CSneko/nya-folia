package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STRUCTURE_DIRECTORY_NAME = "structures";
    private static final String TEST_STRUCTURES_DIR = "gameteststructures";
    private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
    private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
    public final Map<ResourceLocation, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
    private final DataFixer fixerUpper;
    private ResourceManager resourceManager;
    private final Path generatedDir;
    private final List<StructureTemplateManager.Source> sources;
    private final HolderGetter<Block> blockLookup;
    private static final FileToIdConverter LISTER = new FileToIdConverter("structures", ".nbt");

    public StructureTemplateManager(ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, HolderGetter<Block> blockLookup) {
        this.resourceManager = resourceManager;
        this.fixerUpper = dataFixer;
        this.generatedDir = session.getLevelPath(LevelResource.GENERATED_DIR).normalize();
        this.blockLookup = blockLookup;
        ImmutableList.Builder<StructureTemplateManager.Source> builder = ImmutableList.builder();
        builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
        }

        builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
        this.sources = builder.build();
    }

    public StructureTemplate getOrCreate(ResourceLocation id) {
        Optional<StructureTemplate> optional = this.get(id);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            StructureTemplate structureTemplate = new StructureTemplate();
            this.structureRepository.put(id, Optional.of(structureTemplate));
            return structureTemplate;
        }
    }

    public Optional<StructureTemplate> get(ResourceLocation id) {
        return this.structureRepository.computeIfAbsent(id, this::tryLoad);
    }

    public Stream<ResourceLocation> listTemplates() {
        return this.sources.stream().flatMap((provider) -> {
            return provider.lister().get();
        }).distinct();
    }

    private Optional<StructureTemplate> tryLoad(ResourceLocation id) {
        for(StructureTemplateManager.Source source : this.sources) {
            try {
                Optional<StructureTemplate> optional = source.loader().apply(id);
                if (optional.isPresent()) {
                    return optional;
                }
            } catch (Exception var5) {
            }
        }

        return Optional.empty();
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.structureRepository.clear();
    }

    public Optional<StructureTemplate> loadFromResource(ResourceLocation id) {
        ResourceLocation resourceLocation = LISTER.idToFile(id);
        return this.load(() -> {
            return this.resourceManager.open(resourceLocation);
        }, (throwable) -> {
            LOGGER.error("Couldn't load structure {}", id, throwable);
        });
    }

    private Stream<ResourceLocation> listResources() {
        return LISTER.listMatchingResources(this.resourceManager).keySet().stream().map(LISTER::fileToId);
    }

    private Optional<StructureTemplate> loadFromTestStructures(ResourceLocation id) {
        return this.loadFromSnbt(id, Paths.get("gameteststructures"));
    }

    private Stream<ResourceLocation> listTestStructures() {
        return this.listFolderContents(Paths.get("gameteststructures"), "minecraft", ".snbt");
    }

    public Optional<StructureTemplate> loadFromGenerated(ResourceLocation id) {
        if (!Files.isDirectory(this.generatedDir)) {
            return Optional.empty();
        } else {
            Path path = createAndValidatePathToStructure(this.generatedDir, id, ".nbt");
            return this.load(() -> {
                return new FileInputStream(path.toFile());
            }, (throwable) -> {
                LOGGER.error("Couldn't load structure from {}", path, throwable);
            });
        }
    }

    private Stream<ResourceLocation> listGenerated() {
        if (!Files.isDirectory(this.generatedDir)) {
            return Stream.empty();
        } else {
            try {
                return Files.list(this.generatedDir).filter((path) -> {
                    return Files.isDirectory(path);
                }).flatMap((path) -> {
                    return this.listGeneratedInNamespace(path);
                });
            } catch (IOException var2) {
                return Stream.empty();
            }
        }
    }

    private Stream<ResourceLocation> listGeneratedInNamespace(Path namespaceDirectory) {
        Path path = namespaceDirectory.resolve("structures");
        return this.listFolderContents(path, namespaceDirectory.getFileName().toString(), ".nbt");
    }

    private Stream<ResourceLocation> listFolderContents(Path structuresDirectoryPath, String namespace, String extension) {
        if (!Files.isDirectory(structuresDirectoryPath)) {
            return Stream.empty();
        } else {
            int i = extension.length();
            Function<String, String> function = (filename) -> {
                return filename.substring(0, filename.length() - i);
            };

            try {
                return Files.walk(structuresDirectoryPath).filter((path) -> {
                    return path.toString().endsWith(extension);
                }).mapMulti((path, consumer) -> {
                    try {
                        consumer.accept(new ResourceLocation(namespace, function.apply(this.relativize(structuresDirectoryPath, path))));
                    } catch (ResourceLocationException var7) {
                        LOGGER.error("Invalid location while listing pack contents", (Throwable)var7);
                    }

                });
            } catch (IOException var7) {
                LOGGER.error("Failed to list folder contents", (Throwable)var7);
                return Stream.empty();
            }
        }
    }

    private String relativize(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separator, "/");
    }

    private Optional<StructureTemplate> loadFromSnbt(ResourceLocation id, Path path) {
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        } else {
            Path path2 = FileUtil.createPathToResource(path, id.getPath(), ".snbt");

            try (BufferedReader bufferedReader = Files.newBufferedReader(path2)) {
                String string = IOUtils.toString((Reader)bufferedReader);
                return Optional.of(this.readStructure(NbtUtils.snbtToStructure(string)));
            } catch (NoSuchFileException var9) {
                return Optional.empty();
            } catch (CommandSyntaxException | IOException var10) {
                LOGGER.error("Couldn't load structure from {}", path2, var10);
                return Optional.empty();
            }
        }
    }

    private Optional<StructureTemplate> load(StructureTemplateManager.InputStreamOpener opener, Consumer<Throwable> exceptionConsumer) {
        try (InputStream inputStream = opener.open()) {
            return Optional.of(this.readStructure(inputStream));
        } catch (FileNotFoundException var8) {
            return Optional.empty();
        } catch (Throwable var9) {
            exceptionConsumer.accept(var9);
            return Optional.empty();
        }
    }

    public StructureTemplate readStructure(InputStream templateIInputStream) throws IOException {
        CompoundTag compoundTag = NbtIo.readCompressed(templateIInputStream);
        return this.readStructure(compoundTag);
    }

    public StructureTemplate readStructure(CompoundTag nbt) {
        StructureTemplate structureTemplate = new StructureTemplate();
        int i = NbtUtils.getDataVersion(nbt, 500);
        structureTemplate.load(this.blockLookup, ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.STRUCTURE, nbt, i, SharedConstants.getCurrentVersion().getDataVersion().getVersion())); // Paper
        return structureTemplate;
    }

    public boolean save(ResourceLocation id) {
        Optional<StructureTemplate> optional = this.structureRepository.get(id);
        if (optional.isEmpty()) {
            return false;
        } else {
            StructureTemplate structureTemplate = optional.get();
            Path path = createAndValidatePathToStructure(this.generatedDir, id, ".nbt");
            Path path2 = path.getParent();
            if (path2 == null) {
                return false;
            } else {
                try {
                    Files.createDirectories(Files.exists(path2) ? path2.toRealPath() : path2);
                } catch (IOException var13) {
                    LOGGER.error("Failed to create parent directory: {}", (Object)path2);
                    return false;
                }

                CompoundTag compoundTag = structureTemplate.save(new CompoundTag());

                try {
                    try (OutputStream outputStream = new FileOutputStream(path.toFile())) {
                        NbtIo.writeCompressed(compoundTag, outputStream);
                    }

                    return true;
                } catch (Throwable var12) {
                    return false;
                }
            }
        }
    }

    public Path getPathToGeneratedStructure(ResourceLocation id, String extension) {
        return createPathToStructure(this.generatedDir, id, extension);
    }

    public static Path createPathToStructure(Path path, ResourceLocation id, String extension) {
        try {
            Path path2 = path.resolve(id.getNamespace());
            Path path3 = path2.resolve("structures");
            return FileUtil.createPathToResource(path3, id.getPath(), extension);
        } catch (InvalidPathException var5) {
            throw new ResourceLocationException("Invalid resource path: " + id, var5);
        }
    }

    public static Path createAndValidatePathToStructure(Path path, ResourceLocation id, String extension) {
        if (id.getPath().contains("//")) {
            throw new ResourceLocationException("Invalid resource path: " + id);
        } else {
            Path path2 = createPathToStructure(path, id, extension);
            if (path2.startsWith(path) && FileUtil.isPathNormalized(path2) && FileUtil.isPathPortable(path2)) {
                return path2;
            } else {
                throw new ResourceLocationException("Invalid resource path: " + path2);
            }
        }
    }

    public void remove(ResourceLocation id) {
        this.structureRepository.remove(id);
    }

    @FunctionalInterface
    interface InputStreamOpener {
        InputStream open() throws IOException;
    }

    static record Source(Function<ResourceLocation, Optional<StructureTemplate>> loader, Supplier<Stream<ResourceLocation>> lister) {
    }
}
