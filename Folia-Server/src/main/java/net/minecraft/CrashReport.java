package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import net.minecraft.util.MemoryReserve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

public class CrashReport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final Throwable exception;
    private final List<CrashReportCategory> details = Lists.newArrayList();
    private File saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];
    private final SystemReport systemReport = new SystemReport();

    public CrashReport(String message, Throwable cause) {
        io.papermc.paper.util.StacktraceDeobfuscator.INSTANCE.deobfuscateThrowable(cause); // Paper
        this.title = message;
        this.exception = cause;
        this.systemReport.setDetail("CraftBukkit Information", new org.bukkit.craftbukkit.CraftCrashReport()); // CraftBukkit
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public String getDetails() {
        StringBuilder stringbuilder = new StringBuilder();

        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public void getDetails(StringBuilder crashReportBuilder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = (StackTraceElement[]) ArrayUtils.subarray(((CrashReportCategory) this.details.get(0)).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            crashReportBuilder.append("-- Head --\n");
            crashReportBuilder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            crashReportBuilder.append("Stacktrace:\n");
            StackTraceElement[] astacktraceelement = this.uncategorizedStackTrace;
            int i = astacktraceelement.length;

            for (int j = 0; j < i; ++j) {
                StackTraceElement stacktraceelement = astacktraceelement[j];

                crashReportBuilder.append("\t").append("at ").append(stacktraceelement);
                crashReportBuilder.append("\n");
            }

            crashReportBuilder.append("\n");
        }

        Iterator iterator = this.details.iterator();

        while (iterator.hasNext()) {
            CrashReportCategory crashreportsystemdetails = (CrashReportCategory) iterator.next();

            crashreportsystemdetails.getDetails(crashReportBuilder);
            crashReportBuilder.append("\n\n");
        }

        this.systemReport.appendToCrashReportString(crashReportBuilder);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Object object = this.exception;

        if (((Throwable) object).getMessage() == null) {
            if (object instanceof NullPointerException) {
                object = new NullPointerException(this.title);
            } else if (object instanceof StackOverflowError) {
                object = new StackOverflowError(this.title);
            } else if (object instanceof OutOfMemoryError) {
                object = new OutOfMemoryError(this.title);
            }

            ((Throwable) object).setStackTrace(this.exception.getStackTrace());
        }

        String s;

        try {
            stringwriter = new StringWriter();
            printwriter = new PrintWriter(stringwriter);
            ((Throwable) object).printStackTrace(printwriter);
            s = stringwriter.toString();
        } finally {
            IOUtils.closeQuietly(stringwriter);
            IOUtils.closeQuietly(printwriter);
        }

        return s;
    }

    public String getFriendlyReport() {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append("---- Minecraft Crash Report ----\n");
        stringbuilder.append("// ");
        stringbuilder.append(CrashReport.getErrorComment());
        stringbuilder.append("\n\n");
        stringbuilder.append("Time: ");
        stringbuilder.append(CrashReport.DATE_TIME_FORMATTER.format(ZonedDateTime.now()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.title);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; ++i) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public File getSaveFile() {
        return this.saveFile;
    }

    public boolean saveToFile(File file) {
        if (this.saveFile != null) {
            return false;
        } else {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            OutputStreamWriter outputstreamwriter = null;

            boolean flag;

            try {
                outputstreamwriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
                outputstreamwriter.write(this.getFriendlyReport());
                this.saveFile = file;
                boolean flag1 = true;

                return flag1;
            } catch (Throwable throwable) {
                CrashReport.LOGGER.error("Could not save crash report to {}", file, throwable);
                flag = false;
            } finally {
                IOUtils.closeQuietly(outputstreamwriter);
            }

            return flag;
        }
    }

    public SystemReport getSystemReport() {
        return this.systemReport;
    }

    public CrashReportCategory addCategory(String name) {
        return this.addCategory(name, 1);
    }

    public CrashReportCategory addCategory(String name, int ignoredStackTraceCallCount) {
        CrashReportCategory crashreportsystemdetails = new CrashReportCategory(name);

        if (this.trackingStackTrace) {
            int j = crashreportsystemdetails.fillInStackTrace(ignoredStackTraceCallCount);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int k = astacktraceelement.length - j;

            if (k < 0) {
                CrashReport.LOGGER.error("Negative index in crash report handler ({}/{})", new Object[]{astacktraceelement.length, j, ")"});
            }

            if (astacktraceelement != null && 0 <= k && k < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[k];
                if (astacktraceelement.length + 1 - j < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - j];
                }
            }

            this.trackingStackTrace = crashreportsystemdetails.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (astacktraceelement != null && astacktraceelement.length >= j && 0 <= k && k < astacktraceelement.length) {
                this.uncategorizedStackTrace = new StackTraceElement[k];
                System.arraycopy(astacktraceelement, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashreportsystemdetails);
        return crashreportsystemdetails;
    }

    private static String getErrorComment() {
        String[] astring = new String[]{"Who set us up the TNT?", "Everything's going to plan. No, really, that was supposed to happen.", "Uh... Did I do that?", "Oops.", "Why did you do that?", "I feel sad now :(", "My bad.", "I'm sorry, Dave.", "I let you down. Sorry :(", "On the bright side, I bought you a teddy bear!", "Daisy, daisy...", "Oh - I know what I did wrong!", "Hey, that tickles! Hehehe!", "I blame Dinnerbone.", "You should try our sister game, Minceraft!", "Don't be sad. I'll do better next time, I promise!", "Don't be sad, have a hug! <3", "I just don't know what went wrong :(", "Shall we play a game?", "Quite honestly, I wouldn't worry myself about that.", "I bet Cylons wouldn't have this problem.", "Sorry :(", "Surprise! Haha. Well, this is awkward.", "Would you like a cupcake?", "Hi. I'm Minecraft, and I'm a crashaholic.", "Ooh. Shiny.", "This doesn't make any sense!", "Why is it breaking :(", "Don't do that.", "Ouch. That hurt :(", "You're mean.", "This is a token for 1 free hug. Redeem at your nearest Mojangsta: [~~HUG~~]", "There are four lights!", "But it works on my machine."};

        try {
            return astring[(int) (Util.getNanos() % (long) astring.length)];
        } catch (Throwable throwable) {
            return "Witty comment unavailable :(";
        }
    }

    public static CrashReport forThrowable(Throwable cause, String title) {
        if (cause instanceof ThreadDeath) com.destroystokyo.paper.util.SneakyThrow.sneaky(cause); // Paper
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }

        CrashReport crashreport;

        if (cause instanceof ReportedException) {
            crashreport = ((ReportedException) cause).getReport();
        } else {
            crashreport = new CrashReport(title, cause);
        }

        return crashreport;
    }

    public static void preload() {
        MemoryReserve.allocate();
        (new CrashReport("Don't panic!", new Throwable())).getFriendlyReport();
    }
}
