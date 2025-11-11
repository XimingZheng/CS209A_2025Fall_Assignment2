package org.example.demo;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Simplified client for console testing.
 * User types commands:
 *   plant r c
 *   harvest r c
 *   quit
 *
 * This client converts them into JSON and sends to server.
 */
public class Client {
    private static final int PORT = 5050;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", PORT);
            System.out.println("Connected to server");

            new Thread(new MessageSender(socket)).start();
            new Thread(new MessageReceiver(socket)).start();

        } catch (IOException e) {
            System.out.println(STR."Disconnected from server: \{e.getMessage()}");
        }
    }
}

class MessageSender implements Runnable {
    private static final Gson GSON = new Gson();

    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader consoleReader;

    public MessageSender(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            System.out.println("Commands:");
            System.out.println("  plant r c");
            System.out.println("  harvest r c");
            System.out.println("  quit");

            String line;
            while ((line = consoleReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");

                String json;

                if (parts[0].equalsIgnoreCase("plant") && parts.length == 3) {
                    int r = Integer.parseInt(parts[1]);
                    int c = Integer.parseInt(parts[2]);
                    json = GSON.toJson(
                            java.util.Map.of("op", "plant", "row", r, "col", c)
                    );
                }
                else if (parts[0].equalsIgnoreCase("harvest") && parts.length == 3) {
                    int r = Integer.parseInt(parts[1]);
                    int c = Integer.parseInt(parts[2]);
                    json = GSON.toJson(
                            java.util.Map.of("op", "harvest", "row", r, "col", c)
                    );
                }
                else if (parts[0].equalsIgnoreCase("quit")) {
                    json = GSON.toJson(java.util.Map.of("op","quit"));
                    out.println(json);
                    socket.close();
                    break;
                }
                else {
                    System.out.println("Unknown command.");
                    continue;
                }

                out.println(json);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}

class MessageReceiver implements Runnable {
    private final Socket socket;
    private final BufferedReader in;

    public MessageReceiver(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println("[SERVER] " + msg);
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Connection closed.");
        }
    }
}
