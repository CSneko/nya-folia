package org.bukkit.craftbukkit;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;

public class Main {
    public static boolean useJline = true;
    public static boolean useConsole = true;

    // Paper start - Hijack log manager to ensure logging on shutdown
    static {
        System.setProperty("java.util.logging.manager", "io.papermc.paper.log.CustomLogManager");
    }
    // Paper end

    public static void main(String[] args) {
        // Paper start
        final String warnWhenLegacyFormattingDetected = String.join(".", "net", "kyori", "adventure", "text", "warnWhenLegacyFormattingDetected");
        if (false && System.getProperty(warnWhenLegacyFormattingDetected) == null) {
            System.setProperty(warnWhenLegacyFormattingDetected, String.valueOf(true));
        }
        // Paper end
        // Todo: Installation script
        if (System.getProperty("jdk.nio.maxCachedBufferSize") == null) System.setProperty("jdk.nio.maxCachedBufferSize", "262144"); // Paper - cap per-thread NIO cache size
        OptionParser parser = new OptionParser() {
            {
                this.acceptsAll(Main.asList("?", "help"), "Show the help");

                this.acceptsAll(Main.asList("c", "config"), "Properties file to use")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("server.properties"))
                        .describedAs("Properties file");

                this.acceptsAll(Main.asList("P", "plugins"), "Plugin directory to use")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("plugins"))
                        .describedAs("Plugin directory");

                this.acceptsAll(Main.asList("h", "host", "server-ip"), "Host to listen on")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("Hostname or IP");

                this.acceptsAll(Main.asList("W", "world-dir", "universe", "world-container"), "World container")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("."))
                        .describedAs("Directory containing worlds");

                this.acceptsAll(Main.asList("w", "world", "level-name"), "World name")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("World name");

                this.acceptsAll(Main.asList("p", "port", "server-port"), "Port to listen on")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .describedAs("Port");

                this.accepts("serverId", "Server ID")
                        .withRequiredArg();

                this.accepts("jfrProfile", "Enable JFR profiling");

                this.accepts("pidFile", "pid File")
                        .withRequiredArg()
                        .withValuesConvertedBy(new PathConverter());

                this.acceptsAll(Main.asList("o", "online-mode"), "Whether to use online authentication")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .describedAs("Authentication");

                this.acceptsAll(Main.asList("s", "size", "max-players"), "Maximum amount of players")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .describedAs("Server size");

                this.acceptsAll(Main.asList("d", "date-format"), "Format of the date to display in the console (for log entries)")
                        .withRequiredArg()
                        .ofType(SimpleDateFormat.class)
                        .describedAs("Log date format");

                this.acceptsAll(Main.asList("log-pattern"), "Specfies the log filename pattern")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("server.log")
                        .describedAs("Log filename");

                this.acceptsAll(Main.asList("log-limit"), "Limits the maximum size of the log file (0 = unlimited)")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(0)
                        .describedAs("Max log size");

                this.acceptsAll(Main.asList("log-count"), "Specified how many log files to cycle through")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(1)
                        .describedAs("Log count");

                this.acceptsAll(Main.asList("log-append"), "Whether to append to the log file")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(true)
                        .describedAs("Log append");

                this.acceptsAll(Main.asList("log-strip-color"), "Strips color codes from log file");

                this.acceptsAll(Main.asList("b", "bukkit-settings"), "File for bukkit settings")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("bukkit.yml"))
                        .describedAs("Yml file");

                this.acceptsAll(Main.asList("C", "commands-settings"), "File for command settings")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("commands.yml"))
                        .describedAs("Yml file");

                this.acceptsAll(Main.asList("forceUpgrade"), "Whether to force a world upgrade");
                this.acceptsAll(Main.asList("eraseCache"), "Whether to force cache erase during world upgrade");
                this.acceptsAll(Main.asList("nogui"), "Disables the graphical console");

                this.acceptsAll(Main.asList("nojline"), "Disables jline and emulates the vanilla console");

                this.acceptsAll(Main.asList("noconsole"), "Disables the console");

                this.acceptsAll(Main.asList("v", "version"), "Show the CraftBukkit Version");

                this.acceptsAll(Main.asList("demo"), "Demo mode");

                this.acceptsAll(Main.asList("initSettings"), "Only create configuration files and then exit"); // SPIGOT-5761: Add initSettings option

                // Spigot Start
                this.acceptsAll(Main.asList("S", "spigot-settings"), "File for spigot settings")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("spigot.yml"))
                        .describedAs("Yml file");
                // Spigot End

                // Paper Start
                acceptsAll(asList("paper-dir", "paper-settings-directory"), "Directory for Paper settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File(io.papermc.paper.configuration.PaperConfigurations.CONFIG_DIR))
                    .describedAs("Config directory");
                acceptsAll(asList("paper", "paper-settings"), "File for Paper settings")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File("paper.yml"))
                        .describedAs("Yml file");

                acceptsAll(asList("add-plugin", "add-extra-plugin-jar"), "Specify paths to extra plugin jars to be loaded in addition to those in the plugins folder. This argument can be specified multiple times, once for each extra plugin jar path.")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File[] {})
                        .describedAs("Jar file");
                // Paper end

                // Paper start
                acceptsAll(asList("server-name"), "Name of the server")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("NanoCraft")
                        .describedAs("Name");
                // Paper end
            }
        };

        OptionSet options = null;

        // Paper start - preload logger classes to avoid plugins mixing versions
        tryPreloadClass("org.apache.logging.log4j.core.Core");
        tryPreloadClass("org.apache.logging.log4j.core.appender.AsyncAppender");
        tryPreloadClass("org.apache.logging.log4j.core.Appender");
        tryPreloadClass("org.apache.logging.log4j.core.ContextDataInjector");
        tryPreloadClass("org.apache.logging.log4j.core.Filter");
        tryPreloadClass("org.apache.logging.log4j.core.ErrorHandler");
        tryPreloadClass("org.apache.logging.log4j.core.LogEvent");
        tryPreloadClass("org.apache.logging.log4j.core.Logger");
        tryPreloadClass("org.apache.logging.log4j.core.LoggerContext");
        tryPreloadClass("org.apache.logging.log4j.core.LogEventListener");
        tryPreloadClass("org.apache.logging.log4j.core.AbstractLogEvent");
        tryPreloadClass("org.apache.logging.log4j.message.AsynchronouslyFormattable");
        tryPreloadClass("org.apache.logging.log4j.message.FormattedMessage");
        tryPreloadClass("org.apache.logging.log4j.message.ParameterizedMessage");
        tryPreloadClass("org.apache.logging.log4j.message.Message");
        tryPreloadClass("org.apache.logging.log4j.message.MessageFactory");
        tryPreloadClass("org.apache.logging.log4j.message.TimestampMessage");
        tryPreloadClass("org.apache.logging.log4j.message.SimpleMessage");
        tryPreloadClass("org.apache.logging.log4j.core.async.AsyncLogger");
        tryPreloadClass("org.apache.logging.log4j.core.async.AsyncLoggerContext");
        tryPreloadClass("org.apache.logging.log4j.core.async.AsyncQueueFullPolicy");
        tryPreloadClass("org.apache.logging.log4j.core.async.AsyncLoggerDisruptor");
        tryPreloadClass("org.apache.logging.log4j.core.async.RingBufferLogEvent");
        tryPreloadClass("org.apache.logging.log4j.core.async.DisruptorUtil");
        tryPreloadClass("org.apache.logging.log4j.core.async.RingBufferLogEventHandler");
        tryPreloadClass("org.apache.logging.log4j.core.impl.ThrowableProxy");
        tryPreloadClass("org.apache.logging.log4j.core.impl.ExtendedClassInfo");
        tryPreloadClass("org.apache.logging.log4j.core.impl.ExtendedStackTraceElement");
        // Paper end
        try {
            options = parser.parse(args);
        } catch (joptsimple.OptionException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, ex.getLocalizedMessage());
        }

        if ((options == null) || (options.has("?"))) {
            try {
                parser.printHelpOn(System.out);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (options.has("v")) {
            System.out.println(CraftServer.class.getPackage().getImplementationVersion());
        } else {
            // Do you love Java using + and ! as string based identifiers? I sure do!
            String path = new File(".").getAbsolutePath();
            if (path.contains("!") || path.contains("+")) {
                System.err.println("Cannot run server in a directory with ! or + in the pathname. Please rename the affected folders and try again.");
                return;
            }

            // Paper start - better java version checks
            boolean skip = Boolean.getBoolean("Paper.IgnoreJavaVersion");
            float javaVersion = Float.parseFloat(System.getProperty("java.class.version"));
            boolean isOldVersion = javaVersion < 61.0;
            if (!skip && isOldVersion) {
                System.err.println("Unsupported Java detected (" + javaVersion + "). This version of Minecraft requires at least Java 17. Check your Java version with the command 'java -version'. For more info see https://docs.papermc.io/misc/java-install");
                return;
            }
            String javaVersionName = System.getProperty("java.version");
            // J2SE SDK/JRE Version String Naming Convention
            boolean isPreRelease = javaVersionName.contains("-");
            if (!skip && isPreRelease) {
                System.err.println("Unsupported Java detected (" + javaVersionName + "). You are running an unsupported, non official, version. Only general availability versions of Java are supported. Please update your Java version. See https://docs.papermc.io/paper/faq#unsupported-java-detected-what-do-i-do for more information.");
                return;
            }

            if (skip && (isOldVersion || isPreRelease)) {
                System.err.println("Unsupported Java detected ("+ javaVersionName + "), but the check was skipped. Proceed with caution! ");
            }
            // Paper end - better java version checks

            try {
                // Paper start - Handled by TerminalConsoleAppender
                /*
                // This trick bypasses Maven Shade's clever rewriting of our getProperty call when using String literals
                String jline_UnsupportedTerminal = new String(new char[]{'j', 'l', 'i', 'n', 'e', '.', 'U', 'n', 's', 'u', 'p', 'p', 'o', 'r', 't', 'e', 'd', 'T', 'e', 'r', 'm', 'i', 'n', 'a', 'l'});
                String jline_terminal = new String(new char[]{'j', 'l', 'i', 'n', 'e', '.', 't', 'e', 'r', 'm', 'i', 'n', 'a', 'l'});

                Main.useJline = !(jline_UnsupportedTerminal).equals(System.getProperty(jline_terminal));

                if (options.has("nojline")) {
                    System.setProperty("user.language", "en");
                    Main.useJline = false;
                }

                if (Main.useJline) {
                    AnsiConsole.systemInstall();
                } else {
                    // This ensures the terminal literal will always match the jline implementation
                    System.setProperty(jline.TerminalFactory.JLINE_TERMINAL, jline.UnsupportedTerminal.class.getName());
                }
                */

                if (options.has("nojline")) {
                    System.setProperty(net.minecrell.terminalconsole.TerminalConsoleAppender.JLINE_OVERRIDE_PROPERTY, "false");
                    useJline = false;
                }
                // Paper end

                if (options.has("noconsole")) {
                    Main.useConsole = false;
                    useJline = false; // Paper
                    System.setProperty(net.minecrell.terminalconsole.TerminalConsoleAppender.JLINE_OVERRIDE_PROPERTY, "false"); // Paper
                }

                if (Main.class.getPackage().getImplementationVendor() != null && System.getProperty("IReallyKnowWhatIAmDoingISwear") == null) {
                    Date buildDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(Main.class.getPackage().getImplementationVendor()); // Paper

                    Calendar deadline = Calendar.getInstance();
                    deadline.add(Calendar.DAY_OF_YEAR, -21);
                    if (buildDate.before(deadline.getTime())) {
                        // Paper start - This is some stupid bullshit
                        System.err.println("*** 喵，主人已经有一段时间没有更新服务端了哦 ***");
                        System.err.println("*** 请尽快下载最新版本喵~  https://github.com/csneko/nya-folia ***"); // Paper
                        //System.err.println("*** Server will start in 20 seconds ***");
                        //Thread.sleep(TimeUnit.SECONDS.toMillis(20));
                        // Paper End
                    }
                }

                // Paper start - Log Java and OS versioning to help with debugging plugin issues
                java.lang.management.RuntimeMXBean runtimeMX = java.lang.management.ManagementFactory.getRuntimeMXBean();
                java.lang.management.OperatingSystemMXBean osMX = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                if (runtimeMX != null && osMX != null) {
                    String javaInfo = "Java " + runtimeMX.getSpecVersion() + " (" + runtimeMX.getVmName() + " " + runtimeMX.getVmVersion() + ")";
                    String osInfo = "Host: " + osMX.getName() + " " + osMX.getVersion() + " (" + osMX.getArch() + ")";

                    System.out.println("系统信息: " + javaInfo + " " + osInfo);
                } else {
                    System.out.println("喵~无法获取系统信息");
                }
                // Paper end
                System.setProperty( "library.jansi.version", "Paper" ); // Paper - set meaningless jansi version to prevent git builds from crashing on Windows
                System.out.println("正在加载依赖喵，请稍等~");
                net.minecraft.server.Main.main(options);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            // Paper start
            // load some required classes to avoid errors during shutdown if jar is replaced
            // also to guarantee our version loads over plugins
            tryPreloadClass("com.destroystokyo.paper.util.SneakyThrow");
            tryPreloadClass("com.google.common.collect.Iterators$PeekingImpl");
            tryPreloadClass("com.google.common.collect.MapMakerInternalMap$Values");
            tryPreloadClass("com.google.common.collect.MapMakerInternalMap$ValueIterator");
            tryPreloadClass("com.google.common.collect.MapMakerInternalMap$WriteThroughEntry");
            tryPreloadClass("com.google.common.collect.Iterables");
            for (int i = 1; i <= 15; i++) {
                tryPreloadClass("com.google.common.collect.Iterables$" + i, false);
            }
            tryPreloadClass("org.apache.commons.lang3.mutable.MutableBoolean");
            tryPreloadClass("org.apache.commons.lang3.mutable.MutableInt");
            tryPreloadClass("org.jline.terminal.impl.MouseSupport");
            tryPreloadClass("org.jline.terminal.impl.MouseSupport$1");
            tryPreloadClass("org.jline.terminal.Terminal$MouseTracking");
            tryPreloadClass("co.aikar.timings.TimingHistory");
            tryPreloadClass("co.aikar.timings.TimingHistory$MinuteReport");
            tryPreloadClass("io.netty.channel.AbstractChannelHandlerContext");
            tryPreloadClass("io.netty.channel.AbstractChannelHandlerContext$11");
            tryPreloadClass("io.netty.channel.AbstractChannelHandlerContext$12");
            tryPreloadClass("io.netty.channel.AbstractChannel$AbstractUnsafe$8");
            tryPreloadClass("io.netty.util.concurrent.DefaultPromise");
            tryPreloadClass("io.netty.util.concurrent.DefaultPromise$1");
            tryPreloadClass("io.netty.util.internal.PromiseNotificationUtil");
            tryPreloadClass("io.netty.util.internal.SystemPropertyUtil");
            tryPreloadClass("org.bukkit.craftbukkit.scheduler.CraftScheduler");
            tryPreloadClass("org.bukkit.craftbukkit.scheduler.CraftScheduler$1");
            tryPreloadClass("org.bukkit.craftbukkit.scheduler.CraftScheduler$2");
            tryPreloadClass("org.bukkit.craftbukkit.scheduler.CraftScheduler$3");
            tryPreloadClass("org.bukkit.craftbukkit.scheduler.CraftScheduler$4");
            tryPreloadClass("org.slf4j.helpers.MessageFormatter");
            tryPreloadClass("org.slf4j.helpers.FormattingTuple");
            tryPreloadClass("org.slf4j.helpers.BasicMarker");
            tryPreloadClass("org.slf4j.helpers.Util");
            tryPreloadClass("com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent");
            tryPreloadClass("com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent");
            // Minecraft, seen during saving
            tryPreloadClass(net.minecraft.world.level.lighting.LayerLightEventListener.DummyLightLayerEventListener.class.getName());
            tryPreloadClass(net.minecraft.world.level.lighting.LayerLightEventListener.class.getName());
            tryPreloadClass(net.minecraft.util.ExceptionCollector.class.getName());
            tryPreloadClass(io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.PlayerChunkLoaderData.class.getName());
            // Paper end
        }
    }

    // Paper start
    private static void tryPreloadClass(String className) {
        tryPreloadClass(className, true);
    }
    private static void tryPreloadClass(String className, boolean printError) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            if (printError) System.err.println("An expected class  " + className + " was not found for preloading: " + e.getMessage());
        }
    }
    // Paper end

    private static List<String> asList(String... params) {
        return Arrays.asList(params);
    }
}
