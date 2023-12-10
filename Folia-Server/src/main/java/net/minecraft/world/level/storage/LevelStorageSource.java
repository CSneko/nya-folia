package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.nbt.visitors.SkipFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import net.minecraft.world.level.validation.PathAllowList;
import org.slf4j.Logger;

public class LevelStorageSource {

    static final Logger LOGGER = LogUtils.getLogger();
    static final DateTimeFormatter FORMATTER = (new DateTimeFormatterBuilder()).appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-').appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-').appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('_').appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral('-').appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral('-').appendValue(ChronoField.SECOND_OF_MINUTE, 2).toFormatter();
    private static final ImmutableList<String> OLD_SETTINGS_KEYS = ImmutableList.of("RandomSeed", "generatorName", "generatorOptions", "generatorVersion", "legacy_custom_options", "MapFeatures", "BonusChest");
    private static final String TAG_DATA = "Data";
    private static final PathMatcher NO_SYMLINKS_ALLOWED = (path) -> {
        return false;
    };
    public static final String ALLOWED_SYMLINKS_CONFIG_NAME = "allowed_symlinks.txt";
    private static final int SUMMARY_UNCOMPRESSED_NBT_QUOTA = 104857600;
    public final Path baseDir;
    private final Path backupDir;
    final DataFixer fixerUpper;
    private final DirectoryValidator worldDirValidator;

    public LevelStorageSource(Path savesDirectory, Path backupsDirectory, DirectoryValidator symlinkFinder, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;

        try {
            FileUtil.createDirectoriesSafe(savesDirectory);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }

        this.baseDir = savesDirectory;
        this.backupDir = backupsDirectory;
        this.worldDirValidator = symlinkFinder;
    }

    public static DirectoryValidator parseValidator(Path allowedSymlinksFile) {
        if (Files.exists(allowedSymlinksFile, new LinkOption[0])) {
            try {
                BufferedReader bufferedreader = Files.newBufferedReader(allowedSymlinksFile);

                DirectoryValidator directoryvalidator;

                try {
                    directoryvalidator = new DirectoryValidator(PathAllowList.readPlain(bufferedreader));
                } catch (Throwable throwable) {
                    if (bufferedreader != null) {
                        try {
                            bufferedreader.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (bufferedreader != null) {
                    bufferedreader.close();
                }

                return directoryvalidator;
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to parse {}, disallowing all symbolic links", "allowed_symlinks.txt", exception);
            }
        }

        return new DirectoryValidator(LevelStorageSource.NO_SYMLINKS_ALLOWED);
    }

    public static LevelStorageSource createDefault(Path path) {
        DirectoryValidator directoryvalidator = LevelStorageSource.parseValidator(path.resolve("allowed_symlinks.txt"));

        return new LevelStorageSource(path, path.resolve("../backups"), directoryvalidator, DataFixers.getDataFixer());
    }

    private static <T> DataResult<WorldGenSettings> readWorldGenSettings(Dynamic<T> levelData, DataFixer dataFixer, int version) {
        Dynamic<T> dynamic1 = levelData.get("WorldGenSettings").orElseEmptyMap();
        UnmodifiableIterator unmodifiableiterator = LevelStorageSource.OLD_SETTINGS_KEYS.iterator();

        while (unmodifiableiterator.hasNext()) {
            String s = (String) unmodifiableiterator.next();
            Optional<Dynamic<T>> optional = levelData.get(s).result();

            if (optional.isPresent()) {
                dynamic1 = dynamic1.set(s, (Dynamic) optional.get());
            }
        }

        Dynamic<T> dynamic2 = DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(dataFixer, dynamic1, version);

        return WorldGenSettings.CODEC.parse(dynamic2);
    }

    private static WorldDataConfiguration readDataConfig(Dynamic<?> dynamic) {
        DataResult<WorldDataConfiguration> dataresult = WorldDataConfiguration.CODEC.parse(dynamic); // CraftBukkit - decompile error
        Logger logger = LevelStorageSource.LOGGER;

        Objects.requireNonNull(logger);
        return (WorldDataConfiguration) dataresult.resultOrPartial(logger::error).orElse(WorldDataConfiguration.DEFAULT);
    }

    public String getName() {
        return "Anvil";
    }

    public LevelStorageSource.LevelCandidates findLevelCandidates() throws LevelStorageException {
        if (!Files.isDirectory(this.baseDir, new LinkOption[0])) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        } else {
            try {
                Stream<Path> stream = Files.list(this.baseDir); // CraftBukkit - decompile error

                LevelStorageSource.LevelCandidates convertable_a;

                try {
                    List<LevelStorageSource.LevelDirectory> list = stream.filter((path) -> {
                        return Files.isDirectory(path, new LinkOption[0]);
                    }).map(LevelStorageSource.LevelDirectory::new).filter((convertable_b) -> {
                        return Files.isRegularFile(convertable_b.dataFile(), new LinkOption[0]) || Files.isRegularFile(convertable_b.oldDataFile(), new LinkOption[0]);
                    }).toList();

                    convertable_a = new LevelStorageSource.LevelCandidates(list);
                } catch (Throwable throwable) {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (stream != null) {
                    stream.close();
                }

                return convertable_a;
            } catch (IOException ioexception) {
                throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
            }
        }
    }

    public CompletableFuture<List<LevelSummary>> loadLevelSummaries(LevelStorageSource.LevelCandidates levels) {
        List<CompletableFuture<LevelSummary>> list = new ArrayList(levels.levels.size());
        Iterator iterator = levels.levels.iterator();

        while (iterator.hasNext()) {
            LevelStorageSource.LevelDirectory convertable_b = (LevelStorageSource.LevelDirectory) iterator.next();

            list.add(CompletableFuture.supplyAsync(() -> {
                boolean flag;

                try {
                    flag = DirectoryLock.isLocked(convertable_b.path());
                } catch (Exception exception) {
                    LevelStorageSource.LOGGER.warn("Failed to read {} lock", convertable_b.path(), exception);
                    return null;
                }

                try {
                    LevelSummary worldinfo = (LevelSummary) this.readLevelData(convertable_b, this.levelSummaryReader(convertable_b, flag));

                    return worldinfo != null ? worldinfo : null;
                } catch (OutOfMemoryError outofmemoryerror) {
                    MemoryReserve.release();
                    System.gc();
                    String s = "Ran out of memory trying to read summary of world folder \"" + convertable_b.directoryName() + "\"";

                    LevelStorageSource.LOGGER.error(LogUtils.FATAL_MARKER, s);
                    OutOfMemoryError outofmemoryerror1 = new OutOfMemoryError("Ran out of memory reading level data");

                    outofmemoryerror1.initCause(outofmemoryerror);
                    CrashReport crashreport = CrashReport.forThrowable(outofmemoryerror1, s);
                    CrashReportCategory crashreportsystemdetails = crashreport.addCategory("World details");

                    crashreportsystemdetails.setDetail("Folder Name", (Object) convertable_b.directoryName());

                    try {
                        long i = Files.size(convertable_b.dataFile());

                        crashreportsystemdetails.setDetail("level.dat size", (Object) i);
                    } catch (IOException ioexception) {
                        crashreportsystemdetails.setDetailError("level.dat size", ioexception);
                    }

                    throw new ReportedException(crashreport);
                }
            }, Util.backgroundExecutor()));
        }

        return Util.sequenceFailFastAndCancel(list).thenApply((list1) -> {
            return list1.stream().filter(Objects::nonNull).sorted().toList();
        });
    }

    private int getStorageVersion() {
        return 19133;
    }

    @Nullable
    <T> T readLevelData(LevelStorageSource.LevelDirectory levelSave, BiFunction<Path, DataFixer, T> levelDataParser) {
        if (!Files.exists(levelSave.path(), new LinkOption[0])) {
            return null;
        } else {
            Path path = levelSave.dataFile();

            if (Files.exists(path, new LinkOption[0])) {
                T t0 = levelDataParser.apply(path, this.fixerUpper);

                if (t0 != null) {
                    return t0;
                }
            }

            path = levelSave.oldDataFile();
            return Files.exists(path, new LinkOption[0]) ? levelDataParser.apply(path, this.fixerUpper) : null;
        }
    }

    @Nullable
    private static WorldDataConfiguration getDataConfiguration(Path path, DataFixer dataFixer) {
        try {
            Tag nbtbase = LevelStorageSource.readLightweightData(path);

            if (nbtbase instanceof CompoundTag) {
                CompoundTag nbttagcompound = (CompoundTag) nbtbase;
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
                int i = NbtUtils.getDataVersion(nbttagcompound1, -1);
                Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(dataFixer, new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i);

                return LevelStorageSource.readDataConfig(dynamic);
            }
        } catch (Exception exception) {
            LevelStorageSource.LOGGER.error("Exception reading {}", path, exception);
        }

        return null;
    }

    static BiFunction<Path, DataFixer, Pair<WorldData, WorldDimensions.Complete>> getLevelData(DynamicOps<Tag> ops, WorldDataConfiguration dataConfiguration, Registry<LevelStem> dimensionOptionsRegistry, Lifecycle lifecycle) {
        return (path, datafixer) -> {
            CompoundTag nbttagcompound;

            try {
                nbttagcompound = NbtIo.readCompressed(path.toFile());
            } catch (IOException ioexception) {
                throw new UncheckedIOException(ioexception);
            }

            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
            CompoundTag nbttagcompound2 = nbttagcompound1.contains("Player", 10) ? nbttagcompound1.getCompound("Player") : null;

            nbttagcompound1.remove("Player");
            int i = NbtUtils.getDataVersion(nbttagcompound1, -1);
            Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(datafixer, new Dynamic(ops, nbttagcompound1), i);
            DataResult dataresult = LevelStorageSource.readWorldGenSettings(dynamic, datafixer, i);
            Logger logger = LevelStorageSource.LOGGER;

            Objects.requireNonNull(logger);
            WorldGenSettings generatorsettings = (WorldGenSettings) dataresult.getOrThrow(false, Util.prefix("WorldGenSettings: ", logger::error));
            LevelVersion levelversion = LevelVersion.parse(dynamic);
            LevelSettings worldsettings = LevelSettings.parse(dynamic, dataConfiguration);
            WorldDimensions.Complete worlddimensions_b = generatorsettings.dimensions().bake(dimensionOptionsRegistry);
            Lifecycle lifecycle1 = worlddimensions_b.lifecycle().add(lifecycle);
            PrimaryLevelData worlddataserver = PrimaryLevelData.parse(dynamic, datafixer, i, nbttagcompound2, worldsettings, levelversion, worlddimensions_b.specialWorldProperty(), generatorsettings.options(), lifecycle1);
            worlddataserver.pdc = nbttagcompound1.get("BukkitValues"); // CraftBukkit - Add PDC to world

            return Pair.of(worlddataserver, worlddimensions_b);
        };
    }

    BiFunction<Path, DataFixer, LevelSummary> levelSummaryReader(LevelStorageSource.LevelDirectory levelSave, boolean locked) {
        return (path, datafixer) -> {
            try {
                if (Files.isSymbolicLink(path)) {
                    List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateSymlink(path);

                    if (!list.isEmpty()) {
                        LevelStorageSource.LOGGER.warn("{}", ContentValidationException.getMessage(path, list));
                        return new LevelSummary.SymlinkLevelSummary(levelSave.directoryName(), levelSave.iconFile());
                    }
                }

                Tag nbtbase = LevelStorageSource.readLightweightData(path);

                if (nbtbase instanceof CompoundTag) {
                    CompoundTag nbttagcompound = (CompoundTag) nbtbase;
                    CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
                    int i = NbtUtils.getDataVersion(nbttagcompound1, -1);
                    Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(datafixer, new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i);
                    LevelVersion levelversion = LevelVersion.parse(dynamic);
                    int j = levelversion.levelDataVersion();

                    if (j == 19132 || j == 19133) {
                        boolean flag1 = j != this.getStorageVersion();
                        Path path1 = levelSave.iconFile();
                        WorldDataConfiguration worlddataconfiguration = LevelStorageSource.readDataConfig(dynamic);
                        LevelSettings worldsettings = LevelSettings.parse(dynamic, worlddataconfiguration);
                        FeatureFlagSet featureflagset = LevelStorageSource.parseFeatureFlagsFromSummary(dynamic);
                        boolean flag2 = FeatureFlags.isExperimental(featureflagset);

                        return new LevelSummary(worldsettings, levelversion, levelSave.directoryName(), flag1, locked, flag2, path1);
                    }
                } else {
                    LevelStorageSource.LOGGER.warn("Invalid root tag in {}", path);
                }

                return null;
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Exception reading {}", path, exception);
                return null;
            }
        };
    }

    private static FeatureFlagSet parseFeatureFlagsFromSummary(Dynamic<?> levelData) {
        Set<ResourceLocation> set = (Set) levelData.get("enabled_features").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().map(ResourceLocation::tryParse).stream();
        }).collect(Collectors.toSet());

        return FeatureFlags.REGISTRY.fromNames(set, (minecraftkey) -> {
        });
    }

    @Nullable
    private static Tag readLightweightData(Path path) throws IOException {
        SkipFields skipfields = new SkipFields(new FieldSelector[]{new FieldSelector("Data", CompoundTag.TYPE, "Player"), new FieldSelector("Data", CompoundTag.TYPE, "WorldGenSettings")});

        NbtIo.parseCompressed(path.toFile(), skipfields, NbtAccounter.create(104857600L));
        return skipfields.getResult();
    }

    public boolean isNewLevelIdAcceptable(String name) {
        try {
            Path path = this.getLevelPath(name);

            Files.createDirectory(path);
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ioexception) {
            return false;
        }
    }

    public boolean levelExists(String name) {
        try {
            return Files.isDirectory(this.getLevelPath(name), new LinkOption[0]);
        } catch (InvalidPathException invalidpathexception) {
            return false;
        }
    }

    public Path getLevelPath(String name) {
        return this.baseDir.resolve(name);
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public Path getBackupPath() {
        return this.backupDir;
    }

    public LevelStorageSource.LevelStorageAccess validateAndCreateAccess(String s, ResourceKey<LevelStem> dimensionType) throws IOException, ContentValidationException { // CraftBukkit
        Path path = this.getLevelPath(s);
        List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateDirectory(path, true);

        if (!list.isEmpty()) {
            throw new ContentValidationException(path, list);
        } else {
            return new LevelStorageSource.LevelStorageAccess(s, path, dimensionType); // CraftBukkit
        }
    }

    public LevelStorageSource.LevelStorageAccess createAccess(String s, ResourceKey<LevelStem> dimensionType) throws IOException { // CraftBukkit
        Path path = this.getLevelPath(s);

        return new LevelStorageSource.LevelStorageAccess(s, path, dimensionType); // CraftBukkit
    }

    public DirectoryValidator getWorldDirValidator() {
        return this.worldDirValidator;
    }

    // CraftBukkit start
    public static Path getStorageFolder(Path path, ResourceKey<LevelStem> dimensionType) {
        if (dimensionType == LevelStem.OVERWORLD) {
            return path;
        } else if (dimensionType == LevelStem.NETHER) {
            return path.resolve("DIM-1");
        } else if (dimensionType == LevelStem.END) {
            return path.resolve("DIM1");
        } else {
            return path.resolve("dimensions").resolve(dimensionType.location().getNamespace()).resolve(dimensionType.location().getPath());
        }
    }
    // CraftBukkit end

    public static record LevelCandidates(List<LevelStorageSource.LevelDirectory> levels) implements Iterable<LevelStorageSource.LevelDirectory> {

        public boolean isEmpty() {
            return this.levels.isEmpty();
        }

        public Iterator<LevelStorageSource.LevelDirectory> iterator() {
            return this.levels.iterator();
        }
    }

    public static record LevelDirectory(Path path) {

        public String directoryName() {
            return this.path.getFileName().toString();
        }

        public Path dataFile() {
            return this.resourcePath(LevelResource.LEVEL_DATA_FILE);
        }

        public Path oldDataFile() {
            return this.resourcePath(LevelResource.OLD_LEVEL_DATA_FILE);
        }

        public Path corruptedDataFile(LocalDateTime dateTime) {
            Path path = this.path;
            String s = LevelResource.LEVEL_DATA_FILE.getId();

            return path.resolve(s + "_corrupted_" + dateTime.format(LevelStorageSource.FORMATTER));
        }

        public Path iconFile() {
            return this.resourcePath(LevelResource.ICON_FILE);
        }

        public Path lockFile() {
            return this.resourcePath(LevelResource.LOCK_FILE);
        }

        public Path resourcePath(LevelResource savePath) {
            return this.path.resolve(savePath.getId());
        }
    }

    public class LevelStorageAccess implements AutoCloseable {

        final DirectoryLock lock;
        public final LevelStorageSource.LevelDirectory levelDirectory;
        private final String levelId;
        private final Map<LevelResource, Path> resources = Maps.newHashMap();
        // CraftBukkit start
        public final ResourceKey<LevelStem> dimensionType;

        LevelStorageAccess(String s, Path path, ResourceKey<LevelStem> dimensionType) throws IOException {
            this.dimensionType = dimensionType;
            // CraftBukkit end
            this.levelId = s;
            this.levelDirectory = new LevelStorageSource.LevelDirectory(path);
            this.lock = DirectoryLock.create(path);
        }

        public LevelStorageSource parent() {
            return LevelStorageSource.this;
        }

        public String getLevelId() {
            return this.levelId;
        }

        public Path getLevelPath(LevelResource savePath) {
            Map<LevelResource, Path> map = this.resources; // CraftBukkit - decompile error
            LevelStorageSource.LevelDirectory convertable_b = this.levelDirectory;

            Objects.requireNonNull(this.levelDirectory);
            return (Path) map.computeIfAbsent(savePath, convertable_b::resourcePath);
        }

        public Path getDimensionPath(ResourceKey<Level> key) {
            return LevelStorageSource.getStorageFolder(this.levelDirectory.path(), this.dimensionType); // CraftBukkit
        }

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        @Nullable
        public LevelSummary getSummary() {
            this.checkLock();
            return (LevelSummary) LevelStorageSource.this.readLevelData(this.levelDirectory, LevelStorageSource.this.levelSummaryReader(this.levelDirectory, false));
        }

        @Nullable
        public Pair<WorldData, WorldDimensions.Complete> getDataTag(DynamicOps<Tag> ops, WorldDataConfiguration dataConfiguration, Registry<LevelStem> dimensionOptionsRegistry, Lifecycle lifecycle) {
            this.checkLock();
            return (Pair) LevelStorageSource.this.readLevelData(this.levelDirectory, LevelStorageSource.getLevelData(ops, dataConfiguration, dimensionOptionsRegistry, lifecycle));
        }

        @Nullable
        public WorldDataConfiguration getDataConfiguration() {
            this.checkLock();
            return (WorldDataConfiguration) LevelStorageSource.this.readLevelData(this.levelDirectory, LevelStorageSource::getDataConfiguration);
        }

        public void saveDataTag(RegistryAccess registryManager, WorldData saveProperties) {
            this.saveDataTag(registryManager, saveProperties, (CompoundTag) null);
        }

        public void saveDataTag(RegistryAccess registryManager, WorldData saveProperties, @Nullable CompoundTag nbt) {
            File file = this.levelDirectory.path().toFile();
            CompoundTag nbttagcompound1 = saveProperties.createTag(registryManager, nbt);
            CompoundTag nbttagcompound2 = new CompoundTag();

            nbttagcompound2.put("Data", nbttagcompound1);

            try {
                File file1 = File.createTempFile("level", ".dat", file);

                NbtIo.writeCompressed(nbttagcompound2, file1);
                File file2 = this.levelDirectory.oldDataFile().toFile();
                File file3 = this.levelDirectory.dataFile().toFile();

                Util.safeReplaceFile(file3, file1, file2);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", file, exception);
            }

        }

        public Optional<Path> getIconFile() {
            return !this.lock.isValid() ? Optional.empty() : Optional.of(this.levelDirectory.iconFile());
        }

        public void deleteLevel() throws IOException {
            this.checkLock();
            final Path path = this.levelDirectory.lockFile();

            LevelStorageSource.LOGGER.info("Deleting level {}", this.levelId);
            int i = 1;

            while (i <= 5) {
                LevelStorageSource.LOGGER.info("Attempt {}...", i);

                try {
                    Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                        public FileVisitResult visitFile(Path path1, BasicFileAttributes basicfileattributes) throws IOException {
                            if (!path1.equals(path)) {
                                LevelStorageSource.LOGGER.debug("Deleting {}", path1);
                                Files.delete(path1);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        public FileVisitResult postVisitDirectory(Path path1, @Nullable IOException ioexception) throws IOException {
                            if (ioexception != null) {
                                throw ioexception;
                            } else {
                                if (path1.equals(LevelStorageAccess.this.levelDirectory.path())) {
                                    LevelStorageAccess.this.lock.close();
                                    Files.deleteIfExists(path);
                                }

                                Files.delete(path1);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    });
                    break;
                } catch (IOException ioexception) {
                    if (i >= 5) {
                        throw ioexception;
                    }

                    LevelStorageSource.LOGGER.warn("Failed to delete {}", this.levelDirectory.path(), ioexception);

                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException interruptedexception) {
                        ;
                    }

                    ++i;
                }
            }

        }

        public void renameLevel(String name) throws IOException {
            this.checkLock();
            Path path = this.levelDirectory.dataFile();

            if (Files.exists(path, new LinkOption[0])) {
                CompoundTag nbttagcompound = NbtIo.readCompressed(path.toFile());
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");

                nbttagcompound1.putString("LevelName", name);
                NbtIo.writeCompressed(nbttagcompound, path.toFile());
            }

        }

        public long makeWorldBackup() throws IOException {
            this.checkLock();
            String s = LocalDateTime.now().format(LevelStorageSource.FORMATTER);
            String s1 = s + "_" + this.levelId;
            Path path = LevelStorageSource.this.getBackupPath();

            try {
                FileUtil.createDirectoriesSafe(path);
            } catch (IOException ioexception) {
                throw new RuntimeException(ioexception);
            }

            Path path1 = path.resolve(FileUtil.findAvailableName(path, s1, ".zip"));
            final ZipOutputStream zipoutputstream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path1)));

            try {
                final Path path2 = Paths.get(this.levelId);

                Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path path3, BasicFileAttributes basicfileattributes) throws IOException {
                        if (path3.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            String s2 = path2.resolve(LevelStorageAccess.this.levelDirectory.path().relativize(path3)).toString().replace('\\', '/');
                            ZipEntry zipentry = new ZipEntry(s2);

                            zipoutputstream.putNextEntry(zipentry);
                            com.google.common.io.Files.asByteSource(path3.toFile()).copyTo(zipoutputstream);
                            zipoutputstream.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                });
            } catch (Throwable throwable) {
                try {
                    zipoutputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            zipoutputstream.close();
            return Files.size(path1);
        }

        public void close() throws IOException {
            this.lock.close();
        }
    }
}
