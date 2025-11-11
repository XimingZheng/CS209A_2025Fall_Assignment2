package org.example.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 5050;
    // ownerId -> farm
    private final Map<String, Farm> farms = new ConcurrentHashMap<>();
    // 谁在看谁：ownerId -> viewers（这些连接正在观看这个owner的农场）
    private final Map<String, Set<ClientHandler>> viewers = new ConcurrentHashMap<>();
    // 在线会话
    private final Set<ClientHandler> sessions = ConcurrentHashMap.newKeySet();

    // 全局一个ticker：周期推进所有玩家农场的生长
    private final ScheduledExecutorService ticker =
            Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) throws Exception {
        new Server().start();

//        System.out.println(STR."[Server] starting on \{PORT} ...");
//
//        // 单人模式
//        Farm farm = new Farm("player-1");
//
//        // 全局调度器：每 1 秒推进一次生长；有变化通知 handler 推送
//        ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor(r -> {
//            Thread t = new Thread(r, "farm-ticker");
//            t.setDaemon(true); return t;
//        });
//
//        try (ServerSocket ss = new ServerSocket(PORT)) {
//            while (true) {
//                Socket socket = ss.accept();
//                System.out.println(STR."[Server] new client: \{socket.getRemoteSocketAddress()}");
//
//                ClientHandler handler = new ClientHandler(socket, farm);
//                Thread th = new Thread(handler, "client-handler");
//                th.start();
//
//                // 启动（或复用）定时器，单人版只需要一个；多人时放到全局并广播给所有 handler
//                schedule.scheduleAtFixedRate(() -> {
//                    try {
//                        if (farm.tickGrow()) {
//                            handler.markDirty(); // 标记需要推送
//                        }
//                    } catch (Throwable t) {
//                        t.printStackTrace();
//                    }
//                }, 1, 100, TimeUnit.MILLISECONDS);
//            }
//        } finally {
//            schedule.shutdownNow();
//        }
    }

    public void start() throws IOException {
        System.out.println("[Server] starting on "+ PORT +" ...");

        // 统一的生长调度：100ms tick 一次，收集“有变化”的 owner 并定向广播
        ticker.scheduleAtFixedRate(this::tickAllFarms, 100, 100, TimeUnit.MILLISECONDS);

        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket s = ss.accept();
                System.out.println("[Server] new client: " + s.getRemoteSocketAddress());

                ClientHandler ch = createClient(s);

                new Thread(ch, "client-" + s.getPort()).start();
            }
        }
    }
    private ClientHandler createClient(Socket s) {
        String playerID = UUID.randomUUID().toString();

        Farm farm = farms.computeIfAbsent(playerID, Farm::new);

        ClientHandler ch = new ClientHandler(this, s, farm, playerID);
        sessions.add(ch);

        viewers.computeIfAbsent(playerID, id -> ConcurrentHashMap.newKeySet()).add(ch);
        ch.setViewingId(playerID);
        return ch;
    }

    private void tickAllFarms() {
        List<String> dirtyOwners = new ArrayList<>();
        for (Map.Entry<String, Farm> e : farms.entrySet()) {
            try {
                if (e.getValue().tickGrow()) {
                    dirtyOwners.add(e.getKey());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        for (String ownerId : dirtyOwners) {
            // broadcast all dirty
            broadcastState(ownerId);
        }
    }

    private void broadcastState(String ownerId) {
        for (ClientHandler ch : viewers.getOrDefault(ownerId, Set.of())) {
            ch.markDirty();
        }
    }


}
