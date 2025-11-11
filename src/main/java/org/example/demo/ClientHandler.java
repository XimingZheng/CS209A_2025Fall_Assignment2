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
    private final Server server;
    private final Socket socket;
    private final Farm farm;
    private Map<String, String> playerList; //玩家列表
    private volatile String playerId;
    private volatile String viewingId;
    private volatile boolean running = true;

    private final AtomicBoolean dirty = new AtomicBoolean(true);


    public ClientHandler(Server server, Socket socket, Farm farm, String id) {
        this.server = server;
        this.socket = socket;
        this.farm = farm;
        this.playerId = id;
    }

    /** 被 Server 调用：有变化时标记需要推送 */
    public void markDirty() { dirty.set(true); }

    @Override public void run() {
        System.out.println(STR."[Client] connected: \{socket}");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            writeState(out, "welcome");

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
                            writeState(out, STR."planted at (\{r}, \{c})");
                            dirty.set(true);
                        } else if ("harvest".equals(op)) {
                            int r = ((Double) request.get("row")).intValue();
                            int c = ((Double) request.get("col")).intValue();
                            farm.harvest(r, c);
                            writeState(out, STR."harvest at (\{r}, \{c})");
                            dirty.set(true);
                        } else if ("steal".equals(op)) {
                            int r = ((Double) request.get("row")).intValue();
                            int c = ((Double) request.get("col")).intValue();
                            farm.steal(r, c);
                            writeState(out, STR."stolen at (\{r}, \{c})");
                            dirty.set(true);
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
                    writeState(out, "update");
                }

                Thread.sleep(10);
            }
        } catch (Exception e) {
            System.out.println(STR."[Client] closed: \{e.getMessage()}");
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    private void quit() {
        try {
            socket.close();
            server.removeClient(playerId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeState(BufferedWriter out, String msg) throws IOException {
        out.write(GSON.toJson(formatMsg(msg, farm)));
        out.write("\n");
        out.flush();
    }
    private Map<String,Object> formatMsg(String msg, Farm farm){
        Map<String,Object> rsp = new HashMap<>();
        rsp.put("clientId", playerId);
        rsp.put("type","state");
        rsp.put("msg", msg);
        rsp.put("coins", farm.getCoins());

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
