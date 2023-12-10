package net.minecraft.server.packs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;

public class PathPackResources extends AbstractPackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final Path root;

    public PathPackResources(String name, Path root, boolean alwaysStable) {
        super(name, alwaysStable);
        this.root = root;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        FileUtil.validatePath(segments);
        Path path = FileUtil.resolvePath(this.root, List.of(segments));
        return Files.exists(path) ? IoSupplier.create(path) : null;
    }

    public static boolean validatePath(Path path) {
        return true;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        Path path = this.root.resolve(type.getDirectory()).resolve(id.getNamespace());
        return getResource(id, path);
    }

    public static IoSupplier<InputStream> getResource(ResourceLocation id, Path path) {
        return FileUtil.decomposePath(id.getPath()).get().map((segments) -> {
            Path path2 = FileUtil.resolvePath(path, segments);
            return returnFileIfExists(path2);
        }, (result) -> {
            LOGGER.error("Invalid path {}: {}", id, result.message());
            return null;
        });
    }

    @Nullable
    private static IoSupplier<InputStream> returnFileIfExists(Path path) {
        return Files.exists(path) && validatePath(path) ? IoSupplier.create(path) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer) {
        FileUtil.decomposePath(prefix).get().ifLeft((prefixSegments) -> {
            Path path = this.root.resolve(type.getDirectory()).resolve(namespace);
            listPath(namespace, path, prefixSegments, consumer);
        }).ifRight((result) -> {
            LOGGER.error("Invalid path {}: {}", prefix, result.message());
        });
    }

    public static void listPath(String namespace, Path path, List<String> prefixSegments, PackResources.ResourceOutput consumer) {
        Path path2 = FileUtil.resolvePath(path, prefixSegments);

        try (Stream<Path> stream = Files.find(path2, Integer.MAX_VALUE, (path2x, attributes) -> {
                return attributes.isRegularFile();
            })) {
            stream.forEach((foundPath) -> {
                String string2 = PATH_JOINER.join(path.relativize(foundPath));
                ResourceLocation resourceLocation = ResourceLocation.tryBuild(namespace, string2);
                if (resourceLocation == null) {
                    Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", namespace, string2));
                } else {
                    consumer.accept(resourceLocation, IoSupplier.create(foundPath));
                }

            });
        } catch (NotDirectoryException | NoSuchFileException var10) {
        } catch (IOException var11) {
            LOGGER.error("Failed to list path {}", path2, var11);
        }

    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = Sets.newHashSet();
        Path path = this.root.resolve(type.getDirectory());

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
            for(Path path2 : directoryStream) {
                String string = path2.getFileName().toString();
                // Paper start
                if (!Files.isDirectory(path2)) {
                    LOGGER.error("Invalid directory entry: {} in {}.", string, this.root, new java.nio.file.NotDirectoryException(string));
                    continue;
                }
                // Paper end
                if (ResourceLocation.isValidNamespace(string)) {
                    set.add(string);
                } else {
                    LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", string, this.root);
                }
            }
        } catch (NotDirectoryException | NoSuchFileException var10) {
        } catch (IOException var11) {
            LOGGER.error("Failed to list path {}", path, var11);
        }

        return set;
    }

    @Override
    public void close() {
    }

    public static class PathResourcesSupplier implements Pack.ResourcesSupplier {
        private final Path content;
        private final boolean isBuiltin;

        public PathResourcesSupplier(Path path, boolean alwaysStable) {
            this.content = path;
            this.isBuiltin = alwaysStable;
        }

        @Override
        public PackResources openPrimary(String name) {
            return new PathPackResources(name, this.content, this.isBuiltin);
        }

        @Override
        public PackResources openFull(String name, Pack.Info metadata) {
            PackResources packResources = this.openPrimary(name);
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packResources;
            } else {
                List<PackResources> list2 = new ArrayList<>(list.size());

                for(String string : list) {
                    Path path = this.content.resolve(string);
                    list2.add(new PathPackResources(name, path, this.isBuiltin));
                }

                return new CompositePackResources(packResources, list2);
            }
        }
    }
}
