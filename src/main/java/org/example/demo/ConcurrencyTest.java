package org.example.demo;

import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ConcurrencyTest {
    private static final int PORT = 5050;
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        try {
            System.out.println("Starting Concurrency Stress Test");

            // 1. Setup Victim
            SimpleClient victim = new SimpleClient("Victim");
            victim.connect();
            String victimId = victim.login(null);
            System.out.println("Victim logged in as ID: " + victimId);

            // 2. Setup Thieves
            SimpleClient thief1 = new SimpleClient("Thief-1");
            thief1.connect();
            String thief1Id = thief1.login(null);
            System.out.println("Thief-1 logged in as ID: " + thief1Id);

            SimpleClient thief2 = new SimpleClient("Thief-2");
            thief2.connect();
            String thief2Id = thief2.login(null);
            System.out.println("Thief-2 logged in as ID: " + thief2Id);

            // 3. Victim plants a crop
            System.out.println("Victim planting at (0,0)...");
            victim.send(Map.of("op", "plant", "row", 0, "col", 0));
            
            // 4. Wait for crop to ripen (10s in Farm.java)
            System.out.println("Waiting 10.5 seconds for crop to ripen...");
            Thread.sleep(10500);

            // 5. Thieves view the victim and victim leaves
            victim.send(Map.of("op", "view", "target", thief1Id));
            thief1.send(Map.of("op", "view", "target", victimId));
            thief2.send(Map.of("op", "view", "target", victimId));
            
            // Give a moment for view to process
            Thread.sleep(500);
            
            // Clear buffers to ensure we see fresh messages
            System.out.println("Clearing input buffers...");
            thief1.clearInput();
            thief2.clearInput();

            // 6. Concurrent Steal Attack
            System.out.println("Initiating Concurrent Steal Attack");
            CountDownLatch latch = new CountDownLatch(1);
            
            Thread t1 = new Thread(() -> {
                try {
                    latch.await(); // Wait for signal
                    System.out.println("Thief-1 sending steal request...");
                    thief1.send(Map.of("op", "steal", "row", 0, "col", 0));
                } catch (Exception e) { e.printStackTrace(); }
            });

            Thread t2 = new Thread(() -> {
                try {
                    latch.await(); // Wait for signal
                    System.out.println("Thief-2 sending steal request...");
                    thief2.send(Map.of("op", "steal", "row", 0, "col", 0));
                } catch (Exception e) { e.printStackTrace(); }
            });

            t1.start();
            t2.start();

            // Release the latch to start both threads simultaneously
            latch.countDown();

            t1.join();
            t2.join();

            // 7. Read responses
            System.out.println("Checking Thief 1 Output");
            readUntilStealResponse(thief1);
            
            System.out.println("Checking Thief 2 Output");
            readUntilStealResponse(thief2);

            System.out.println("Test Complete. Check server logs for atomic handling verification.");
            
            victim.close();
            thief1.close();
            thief2.close();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readUntilStealResponse(SimpleClient client) {
        try {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 2000) { // Increased timeout
                if (client.in.ready()) {
                    String line = client.in.readLine();
                    if (line == null) break;
                    System.out.println("[" + client.name + " RAW]: " + line); // Print everything
                    Map<?,?> msg = GSON.fromJson(line, Map.class);
                    if (msg.containsKey("msg")) {
                        String message = String.valueOf(msg.get("msg"));
                        if (message.contains("stole") || message.contains("Cannot") || message.contains("Not enough")) {
                            System.out.println(">>> [" + client.name + "] MATCH: " + message);
                        }
                    }
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class SimpleClient {
        String name;
        Socket socket;
        BufferedReader in;
        PrintWriter out;

        SimpleClient(String name) { this.name = name; }

        void connect() throws IOException {
            socket = new Socket("localhost", PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }
        
        void clearInput() {
            try {
                while (in.ready()) {
                    in.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String login(String id) throws IOException {
            send(Map.of("op", "login", "id", id == null ? "" : id));
            // Read welcome/state
            String line = in.readLine();
            Map<?,?> msg = GSON.fromJson(line, Map.class);
            return String.valueOf(msg.get("clientId"));
        }

        void send(Map<String, Object> data) {
            out.println(GSON.toJson(data));
        }

        void close() throws IOException {
            socket.close();
        }
    }
}
