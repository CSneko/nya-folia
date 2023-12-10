package net.minecraft.server.rcon.thread;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.slf4j.Logger;

public class RconThread extends GenericThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerSocket socket;
    private final String rconPassword;
    private final List<RconClient> clients = Lists.newArrayList();
    private final ServerInterface serverInterface;

    private RconThread(ServerInterface server, ServerSocket listener, String password) {
        super("RCON Listener");
        this.serverInterface = server;
        this.socket = listener;
        this.rconPassword = password;
    }

    private void clearClients() {
        this.clients.removeIf((client) -> {
            return !client.isRunning();
        });
    }

    @Override
    public void run() {
        try {
            while(this.running) {
                try {
                    Socket socket = this.socket.accept();
                    RconClient rconClient = new RconClient(this.serverInterface, this.rconPassword, socket);
                    rconClient.start();
                    this.clients.add(rconClient);
                    this.clearClients();
                } catch (SocketTimeoutException var7) {
                    this.clearClients();
                } catch (IOException var8) {
                    if (this.running) {
                        LOGGER.info("IO exception: ", (Throwable)var8);
                    }
                }
            }
        } finally {
            this.closeSocket(this.socket);
        }

    }

    @Nullable
    public static RconThread create(ServerInterface server) {
        DedicatedServerProperties dedicatedServerProperties = server.getProperties();
        String string = dedicatedServerProperties.rconIp; // Paper - Configurable rcon ip
        if (string.isEmpty()) {
            string = "0.0.0.0";
        }

        int i = dedicatedServerProperties.rconPort;
        if (0 < i && 65535 >= i) {
            String string2 = dedicatedServerProperties.rconPassword;
            if (string2.isEmpty()) {
                LOGGER.warn("No rcon password set in server.properties, rcon disabled!");
                return null;
            } else {
                try {
                    ServerSocket serverSocket = new ServerSocket(i, 0, InetAddress.getByName(string));
                    serverSocket.setSoTimeout(500);
                    RconThread rconThread = new RconThread(server, serverSocket, string2);
                    if (!rconThread.start()) {
                        return null;
                    } else {
                        LOGGER.info("RCON running on {}:{}", string, i);
                        return rconThread;
                    }
                } catch (IOException var7) {
                    LOGGER.warn("Unable to initialise RCON on {}:{}", string, i, var7);
                    return null;
                }
            }
        } else {
            LOGGER.warn("Invalid rcon port {} found in server.properties, rcon disabled!", (int)i);
            return null;
        }
    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket(this.socket);
        super.stop();

        for(RconClient rconClient : this.clients) {
            if (rconClient.isRunning()) {
                rconClient.stop();
            }
        }

        this.clients.clear();
    }
    // Paper start
    public void stopNonBlocking() {
        this.running = false;
        for (RconClient client : this.clients) {
            client.running = false;
        }
    }
    // Paper stop

    private void closeSocket(ServerSocket socket) {
        LOGGER.debug("closeSocket: {}", (Object)socket);

        try {
            socket.close();
        } catch (IOException var3) {
            LOGGER.warn("Failed to close socket", (Throwable)var3);
        }

    }
}
