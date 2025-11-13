package org.example.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Server {
    private static final int PORT = 5050;
    // ownerId -> farm
    private final Map<String, Farm> farms = new ConcurrentHashMap<>();


    // 谁在看谁：ownerId -> viewers（这些连接正在观看这个owner的农场）
    private final Map<String, Set<ClientHandler>> viewers = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private int nextId = 1;

    // 周期推进所有玩家农场的生长
    private final ScheduledExecutorService ticker =
            Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) throws Exception {
        new Server().start();
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
                System.out.println(ch.getPlayerId() + " connected.");
                broadcastPlayerListUpdate();
            }
        }
    }
    private ClientHandler createClient(Socket s) {
        String playerID = String.valueOf(nextId++);

        Farm farm = new Farm(playerID);
        farms.put(playerID, farm);

        ClientHandler ch = new ClientHandler(this, s, farm, playerID);
        clients.put(playerID, ch);

        ch.setViewingId(playerID);
        viewers.computeIfAbsent(playerID, k -> ConcurrentHashMap.newKeySet()).add(ch);
        return ch;
    }
    public void broadcastPlayerListUpdate() {
        Map<String, String> playerList = farms.keySet()
                .stream()
                .collect(Collectors.toMap(
                        s -> s,
                        s -> clients.get(s).getViewingId()));

        for (ClientHandler client : clients.values()) {
            client.updatePlayerList(playerList);
        }
    }
    public void removeClient(String clientId) {
        for (Set<ClientHandler> viewerSet : viewers.values()) {
            viewerSet.remove(clients.get(clientId));
        }
        farms.remove(clientId);
        clients.remove(clientId);
        viewers.remove(clientId);
        System.out.println(clientId + " disconnected.");
        broadcastPlayerListUpdate();
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

    public void broadcastState(String ownerId) {
        for (ClientHandler ch : viewers.getOrDefault(ownerId, Set.of())) {
            ch.markDirty();
        }
    }

    public Farm getFarm(String player){
        return farms.get(player);
    }

    public void setView(String visitorID, String targetID) {
        ClientHandler visitor = clients.get(visitorID);

        String oldTarget = visitor.getViewingId();

        if (oldTarget != null && !oldTarget.equals(targetID)) {
            Set<ClientHandler> oldViewers = viewers.get(oldTarget);
            // remove visitor
            oldViewers.remove(visitor);
        }
        // add visitor to new target
        viewers.computeIfAbsent(targetID, k -> ConcurrentHashMap.newKeySet()).add(visitor);

        visitor.setViewingId(targetID);
        // mark dirty to update UI
        visitor.markDirty();

        broadcastPlayerListUpdate();
    }

}
