package net.minecraft.server.rcon.thread;

import com.mojang.logging.LogUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import net.minecraft.server.ServerInterface;
// CraftBukkit start
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.rcon.PktUtils;
import net.minecraft.server.rcon.RconConsoleSource;

public class RconClient extends GenericThread {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_RESPONSE_VALUE = 0;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;
    private static final int SERVERDATA_AUTH_FAILURE = -1;
    private boolean authed;
    private final Socket client;
    private final byte[] buf = new byte[1460];
    private final String rconPassword;
    // CraftBukkit start
    private final DedicatedServer serverInterface;
    private final RconConsoleSource rconConsoleSource;
    // CraftBukkit end

    RconClient(ServerInterface server, String password, Socket socket) {
        super("RCON Client " + socket.getInetAddress());
        this.serverInterface = (DedicatedServer) server; // CraftBukkit
        this.client = socket;

        try {
            this.client.setSoTimeout(0);
        } catch (Exception exception) {
            this.running = false;
        }

        this.rconPassword = password;
        this.rconConsoleSource = new net.minecraft.server.rcon.RconConsoleSource(this.serverInterface, socket.getRemoteSocketAddress()); // CraftBukkit
    }

    public void run() {
        // CraftBukkit start - decompile error: switch try / while statement
        try {
            while (true) {
                // CraftBukkit end
                if (!this.running) {
                    return;
                }

                BufferedInputStream bufferedinputstream = new BufferedInputStream(this.client.getInputStream());
                int i = bufferedinputstream.read(this.buf, 0, 1460);

                if (10 > i) {
                    return;
                }

                byte b0 = 0;
                int j = PktUtils.intFromByteArray(this.buf, 0, i);

                if (j == i - 4) {
                    int k = b0 + 4;
                    int l = PktUtils.intFromByteArray(this.buf, k, i);

                    k += 4;
                    int i1 = PktUtils.intFromByteArray(this.buf, k);

                    k += 4;
                    switch (i1) {
                        case 2:
                            if (this.authed) {
                                String s = PktUtils.stringFromByteArray(this.buf, k, i);

                                try {
                                    this.sendCmdResponse(l, this.serverInterface.runCommand(this.rconConsoleSource, s)); // CraftBukkit
                                } catch (Exception exception) {
                                    this.sendCmdResponse(l, "Error executing: " + s + " (" + exception.getMessage() + ")");
                                }
                                continue;
                            }

                            this.sendAuthFailure();
                            continue;
                        case 3:
                            String s1 = PktUtils.stringFromByteArray(this.buf, k, i);
                            int j1 = k + s1.length();

                            if (!s1.isEmpty() && s1.equals(this.rconPassword)) {
                                this.authed = true;
                                this.send(l, 2, "");
                                continue;
                            }

                            this.authed = false;
                            this.sendAuthFailure();
                            continue;
                        default:
                            this.sendCmdResponse(l, String.format(Locale.ROOT, "Unknown request %s", Integer.toHexString(i1)));
                            continue;
                    }
                }
        } // CraftBukkit - decompile error: switch try / while statement
            } catch (IOException ioexception) {
                return;
            } catch (Exception exception1) {
                RconClient.LOGGER.error("Exception whilst parsing RCON input", exception1);
                return;
            } finally {
                this.closeSocket();
                RconClient.LOGGER.info("Thread {} shutting down", this.name);
                this.running = false;
            }

            // CraftBukkit start - decompile error: switch try / while statement
            // return;
        // }
        // CraftBukkit end
    }

    private void send(int sessionToken, int responseType, String message) throws IOException {
        ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(1248);
        DataOutputStream dataoutputstream = new DataOutputStream(bytearrayoutputstream);
        byte[] abyte = message.getBytes(StandardCharsets.UTF_8);

        dataoutputstream.writeInt(Integer.reverseBytes(abyte.length + 10));
        dataoutputstream.writeInt(Integer.reverseBytes(sessionToken));
        dataoutputstream.writeInt(Integer.reverseBytes(responseType));
        dataoutputstream.write(abyte);
        dataoutputstream.write(0);
        dataoutputstream.write(0);
        this.client.getOutputStream().write(bytearrayoutputstream.toByteArray());
    }

    private void sendAuthFailure() throws IOException {
        this.send(-1, 2, "");
    }

    private void sendCmdResponse(int sessionToken, String message) throws IOException {
        int j = message.length();

        do {
            int k = 4096 <= j ? 4096 : j;

            this.send(sessionToken, 0, message.substring(0, k));
            message = message.substring(k);
            j = message.length();
        } while (0 != j);

    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket();
        super.stop();
    }

    private void closeSocket() {
        try {
            this.client.close();
        } catch (IOException ioexception) {
            RconClient.LOGGER.warn("Failed to close socket", ioexception);
        }

    }
}
