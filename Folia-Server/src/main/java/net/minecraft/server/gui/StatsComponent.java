package net.minecraft.server.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;

public class StatsComponent extends JComponent {
    private static final DecimalFormat DECIMAL_FORMAT = Util.make(new DecimalFormat("########0.000"), (decimalFormat) -> {
        decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    private final int[] values = new int[256];
    private int vp;
    private final String[] msgs = new String[11];
    private final MinecraftServer server;
    private final Timer timer;

    public StatsComponent(MinecraftServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(456, 246));
        this.setMinimumSize(new Dimension(456, 246));
        this.setMaximumSize(new Dimension(456, 246));
        this.timer = new Timer(500, (event) -> {
            this.tick();
        });
        this.timer.start();
        this.setBackground(Color.BLACK);
    }

    private void tick() {
        long l = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        // Paper start - Add tps entry
        double[] tps = org.bukkit.Bukkit.getTPS();
        String[] tpsAvg = new String[tps.length];

        for ( int g = 0; g < tps.length; g++) {
            tpsAvg[g] = format( tps[g] );
        }
        this.msgs[0] = "内存用量: " + l / 1024L / 1024L + " mb (" + Runtime.getRuntime().freeMemory() * 100L / Runtime.getRuntime().maxMemory() + "% 空闲)";
        this.msgs[1] = "平均tick: " + DECIMAL_FORMAT.format(this.getAverage(this.server.tickTimes) * 1.0E-6D) + " ms";
        this.msgs[2] = "最近TPS 1m, 5m, 15m: " + String.join(", ", tpsAvg);
        // Paper end
        this.values[this.vp++ & 255] = (int)(l * 100L / Runtime.getRuntime().maxMemory());
        this.repaint();
    }

    private double getAverage(long[] values) {
        long l = 0L;

        for(long m : values) {
            l += m;
        }

        return (double)l / (double)values.length;
    }

    @Override
    public void paint(Graphics graphics) {
        graphics.setColor(new Color(16777215));
        graphics.fillRect(0, 0, 456, 246);

        for(int i = 0; i < 256; ++i) {
            int j = this.values[i + this.vp & 255];
            graphics.setColor(new Color(j + 28 << 16));
            graphics.fillRect(i, 100 - j, 1, j);
        }

        graphics.setColor(Color.BLACK);

        for(int k = 0; k < this.msgs.length; ++k) {
            String string = this.msgs[k];
            if (string != null) {
                graphics.drawString(string, 32, 116 + k * 16);
            }
        }

    }

    public void close() {
        this.timer.stop();
    }

    // Paper - start Add tps entry
    private static String format(double tps) {
        return (( tps > 21.0 ) ? "*" : "") + Math.min(Math.round(tps * 100.0) / 100.0, 20.0); // only print * at 21, we commonly peak to 20.02 as the tick sleep is not accurate enough, stop the noise
    }
    // Paper end
}
