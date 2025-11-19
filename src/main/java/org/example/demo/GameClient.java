package org.example.demo;

import com.google.gson.Gson;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GameClient implements Closeable {
    private static final Gson GSON = new Gson();
    private final String host;
    private final int port;
    private final CSController controller;
    private Socket sock;
    private BufferedReader in;
    private PrintWriter out;
    private Thread reader;
    private volatile boolean running = false;

    public GameClient(String host, int port, CSController controller) {
        this.host = host;
        this.port = port;
        this.controller = controller;
    }

    public boolean connect(String existingId) throws IOException {
        try {
            sock = new Socket(host, port);
            in  = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);
            running = true;
            
            // Send handshake
            send(Map.of("op", "login", "id", existingId == null ? "" : existingId));

            reader = new Thread(() -> {
                try {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        Map<?, ?> message = GSON.fromJson(line, Map.class);
                        Object type = message.get("type");
                        if ("state".equals(type)) {
                            Platform.runLater(() -> controller.handleUpdate((Map<String, Object>) message));
                        } else if ("error".equals(type)) {
                            Platform.runLater(() -> controller.handleError(String.valueOf(message.get("msg"))));
                        } else {
                            String finalLine = line;
                            Platform.runLater(() ->  controller.handleError("Unknown: " + finalLine));
                        }
                    }
                } catch (IOException e) {
                    if (running) Platform.runLater(() ->  controller.handleError("Disconnected"));
                }
            }, "net-reader");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (Exception e) {
            if (running) Platform.runLater(() ->  controller.handleError("Disconnected to server"));
            return false;
        }
    }

    private synchronized void send(Map<String,Object> obj) {
        if (out == null) {
            System.err.println("[GameClient] Cannot send - not connected to server");
            Platform.runLater(() -> controller.handleError("disconnect from server"));
            return;
        }
        out.println(GSON.toJson(obj));
        out.flush();
    }

    public void plant(int r, int c) { send(Map.of("op","plant","row",r,"col",c)); }
    public void harvest(int r, int c) { send(Map.of("op","harvest","row",r,"col",c)); }
    public void steal(int r, int c) { send(Map.of("op","steal","row",r,"col",c)); }
    public void view(String player) {send(Map.of("op","view","target", player));}
    public void quit() { send(Map.of("op","quit")); }
    @Override public void close() throws IOException {
        running = false;
        closeResources();
    }
    private void closeResources() {
        try {
            if (in != null) in.close();
        } catch (IOException ignore) {}

        try {
            if (out != null) out.close();
        } catch (Exception ignore) {}

        try {
            if (sock != null && !sock.isClosed()) sock.close();
        } catch (IOException ignore) {}
    }
}
