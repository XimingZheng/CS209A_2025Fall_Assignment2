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
                System.out.println("[Server] new connection: " + s.getRemoteSocketAddress());

                ClientHandler ch = new ClientHandler(this, s);
                new Thread(ch, "client-" + s.getPort()).start();
            }
        }
    }

    public synchronized LoginResult login(String requestedId, ClientHandler ch) {
        String id;
        Farm farm;
        
        if (requestedId != null && farms.containsKey(requestedId)) {
            // Reconnect
            id = requestedId;
            farm = farms.get(id);
            System.out.println("Player " + id + " reconnected.");
            
            // If there was an old connection, remove it
            if (clients.containsKey(id)) {
                ClientHandler old = clients.get(id);
                // potentially close old socket?
            }
        } else {
            // New player
            id = String.valueOf(nextId++);
            farm = new Farm(id);
            farms.put(id, farm);
            System.out.println("Player " + id + " created.");
        }
        
        clients.put(id, ch);
        
        // Default view self
        ch.setViewingId(id);
        viewers.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(ch);
        
        broadcastPlayerListUpdate();
        return new LoginResult(id, farm);
    }
    
    public record LoginResult(String id, Farm farm) {}

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
        if (clientId == null) return;
        for (Set<ClientHandler> viewerSet : viewers.values()) {
            viewerSet.remove(clients.get(clientId));
        }
        // Do NOT remove farm to allow reconnection
        // farms.remove(clientId);
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

    public String handleSteal(String thiefId, String victimId, int row, int col) {
        ClientHandler victim = clients.get(victimId);
        ClientHandler thief = clients.get(thiefId);

        if (victimId.equals(victim.getViewingId())) {
            return "Owner is at home, cannot steal";
        }

        Farm victimFarm = farms.get(victimId);
        Farm thiefFarm = farms.get(thiefId);
        int amount = victimFarm.steal(row, col);

        thiefFarm.addCoins(amount);

        broadcastState(victimId);
        broadcastState(thiefId);

        return STR."\{thiefId} stole \{amount} from \{victimId} at (\{row},\{col})";
    }

}
