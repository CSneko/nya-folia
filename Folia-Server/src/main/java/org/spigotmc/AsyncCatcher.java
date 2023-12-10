package org.spigotmc;

import net.minecraft.server.MinecraftServer;

public class AsyncCatcher
{

    public static boolean enabled = true;

    public static void catchOp(String reason)
    {
        if ( !io.papermc.paper.util.TickThread.isTickThread() ) // Paper // Paper - rewrite chunk system
        {
            MinecraftServer.LOGGER.error("喵~线程 " + Thread.currentThread().getName() + " 检查主线程失败: " + reason, new Throwable()); // Paper
            throw new IllegalStateException( "Asynchronous " + reason + "!" );
        }
    }
}
