package io.papermc.paper.plugin.loader;

import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.loader.library.ClassPathLibrary;
import io.papermc.paper.plugin.loader.library.PaperLibraryStore;
import io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader;
import io.papermc.paper.plugin.provider.configuration.PaperPluginMeta;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class PaperClasspathBuilder implements PluginClasspathBuilder {

    private final List<ClassPathLibrary> libraries = new ArrayList<>();

    private final PluginProviderContext context;

    public PaperClasspathBuilder(PluginProviderContext context) {
        this.context = context;
    }

    @Override
    public @NotNull PluginProviderContext getContext() {
        return this.context;
    }

    @Override
    public @NotNull PluginClasspathBuilder addLibrary(@NotNull ClassPathLibrary classPathLibrary) {
        this.libraries.add(classPathLibrary);
        return this;
    }

    public PaperPluginClassLoader buildClassLoader(Logger logger, Path source, JarFile jarFile, PaperPluginMeta configuration) {
        PaperLibraryStore paperLibraryStore = new PaperLibraryStore();
        for (ClassPathLibrary library : this.libraries) {
            library.register(paperLibraryStore);
        }

        List<Path> paths = paperLibraryStore.getPaths();
        URL[] urls = new URL[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            Path path = paperLibraryStore.getPaths().get(i);
            try {
                urls[i] = path.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }

        try {
            return new PaperPluginClassLoader(logger, source, jarFile, configuration, this.getClass().getClassLoader(), new URLClassLoader(urls, getClass().getClassLoader()));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
