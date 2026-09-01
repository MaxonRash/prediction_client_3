package com.client.prediction_client_3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SingleInstanceGuard {

    private static final int PORT = 58734;

    private ServerSocket serverSocket;

    public boolean acquire(Runnable onActivateRequest) {
        try {
            serverSocket = new ServerSocket(PORT, 0, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            return false;
        }
        Thread listener = new Thread(() -> listen(onActivateRequest), "single-instance-guard");
        listener.setDaemon(true);
        listener.start();
        return true;
    }

    private void listen(Runnable onActivateRequest) {
        while (true) {
            try (Socket ignored = serverSocket.accept()) {
                onActivateRequest.run();
            } catch (IOException e) {
                return; // server socket closed - shutting down
            }
        }
    }

    public static void notifyRunningInstance() {
        try (Socket ignored = new Socket(InetAddress.getLoopbackAddress(), PORT)) {
            // connecting is the signal itself, no payload needed
        } catch (IOException ignored) {
        }
    }
}
