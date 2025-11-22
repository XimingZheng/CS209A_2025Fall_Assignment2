package org.example.demo;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private static final Gson GSON = new Gson();
    private static String msg;
    private final Server server;
    private final Socket socket;
    private Farm farm;
    private Map<String, String> playerList; //玩家列表
    private volatile String playerId;
    private volatile String viewingId;
    private volatile boolean running = true;

    private final AtomicBoolean dirty = new AtomicBoolean(true);


    public ClientHandler(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        msg = "";
    }

    public void markDirty() { dirty.set(true); }

    @Override public void run() {
        System.out.println(STR."[ClientHandler] connected: \{socket}");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            
            // Handshake
            String handshake = in.readLine();
            if (handshake == null) return;
            
            Map<?,?> loginReq = GSON.fromJson(handshake, Map.class);
            if ("login".equals(loginReq.get("op"))) {
                String reqId = (String) loginReq.get("id");
                Server.LoginResult result = server.login(reqId, this);
                this.playerId = result.id();
                this.farm = result.farm();
                this.viewingId = this.playerId;
            } else {
                System.err.println("Invalid handshake");
                return;
            }

            msg = "welcome";
            writeState(out, farm);

            String line;
            while (running && !socket.isClosed()) {

                if (in.ready() && (line = in.readLine()) != null) {
                    try {
                        Map<?,?> request = GSON.fromJson(line, Map.class);
                        String op = (String) request.get("op");
                        if ("plant".equals(op)) {
                            int r = ((Double) request.get("row")).intValue();
                            int c = ((Double) request.get("col")).intValue();
                            farm.plant(r, c);
                            msg = STR."planted at (\{r}, \{c})";
                            writeState(out, farm);
                            dirty.set(true);
                            server.broadcastState(playerId);
                        } else if ("harvest".equals(op)) {
                            int r = ((Double) request.get("row")).intValue();
                            int c = ((Double) request.get("col")).intValue();
                            farm.harvest(r, c);
                            msg = STR."harvest at (\{r}, \{c})";
                            writeState(out, farm);
                            dirty.set(true);
                            server.broadcastState(playerId);
                        } else if ("steal".equals(op)) {
                            int r = ((Double) request.get("row")).intValue();
                            int c = ((Double) request.get("col")).intValue();
                            String rsp = server.handleSteal(playerId, viewingId, r, c);
                            msg = rsp;
                            dirty.set(true);
                        } else if ("view".equals(op)) {
                            String target = (String) request.get("target");
                            viewingId = target;
                            server.setView(playerId, viewingId);
                        } else if ("quit".equals(op)) {
                            quit();
                        } else {
                            writeError(out, "unknown op");
                        }
                    } catch (Exception ex) {
                        writeError(out, ex.getMessage());
                    }
                }

                if (dirty.compareAndSet(true, false)) {
                    Farm viewingFarm = server.getFarm(viewingId);
                    writeState(out, viewingFarm);
                }

                Thread.sleep(10);
            }
        } catch (Exception e) {
            System.out.println(STR."[Client] closed: \{e.getMessage()}");
        } finally {
            if (playerId != null) {
                server.removeClient(playerId);
            }
        }
    }

    private void quit() {
        try {
            socket.close();
            System.out.println(playerId + "QUIT");
            server.removeClient(playerId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeState(BufferedWriter out, Farm targetFarm) throws IOException {
        out.write(GSON.toJson(formatMsg(targetFarm)));
        out.write("\n");
        out.flush();
    }
    private Map<String,Object> formatMsg(Farm farm){
        Map<String,Object> rsp = new HashMap<>();
        rsp.put("clientId", playerId);
        rsp.put("type","state");
        rsp.put("msg", msg);
        rsp.put("coins", this.farm.getCoins());

        PlotState[][] b = farm.snapshot();
        String[][] arr = new String[b.length][b[0].length];
        for (int i=0;i<b.length;i++)
            for (int j=0;j<b[0].length;j++)
                arr[i][j] = b[i][j].name();
        rsp.put("board", arr);

        if (playerList != null) {
            rsp.put("players", playerList);
        }

        return rsp;
    }

    private void writeError(BufferedWriter out, String msg) throws IOException {
        Map<String,Object> rsp = new HashMap<>();
        rsp.put("type","error");
        rsp.put("msg", msg);
        out.write(GSON.toJson(rsp));
        out.write("\n");
        out.flush();
    }
    public String getPlayerId() {
        return playerId;
    }
    public void setViewingId(String s) {
        this.viewingId = s;
    }
    public String getViewingId() {return this.viewingId; }
    public Farm getFarm() {
        return farm;
    }

    public void updatePlayerList(Map<String, String> players) {
        this.playerList = players;
        markDirty();
    }
}
