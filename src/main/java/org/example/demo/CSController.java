package org.example.demo;


import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.example.demo.GameClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSController {

    @FXML private GridPane gameBoard;

    @FXML private Label coinsLabel;

    @FXML private Label clientID;

    @FXML private Button plantButton;

    @FXML private Button harvestButton;

    @FXML private Button stealButton;

    @FXML private HBox playersBox;

    @FXML private Circle onlineCircle;

    private GameClient client;
    private int rows = 4, cols = 4;
    private ToggleButton[][] cells;
    private PlotState [][] cellState;
    private Timeline refreshTimeline;
    private String statusMsg = "Ready.";

    private int selectedRow = -1;
    private int selectedCol = -1;
    private String myClientId;
    private String currentViewingId;
    private Map<String, String> players = new HashMap<String, String>();   // [player][viewing]
    private boolean connected = false;

    private int coins = 0;

    public void init(String host, int port) throws IOException {
        client = new GameClient(host, port, this);
        connected = client.connect();

        if (connected) {
            cellState = new PlotState[rows][cols];
            createBoard();
            refreshBoard();
            onlineCircle.setFill(Color.LIGHTGREEN);
            startRefreshTicker();
        } else  {
            connected = false;
            statusMsg = "Failed to connect to server.";
            cellState = new PlotState[rows][cols];
            createBoard();
            disableAllActions();
            refreshBoard();
            onlineCircle.setFill(Color.GRAY);
            System.err.println("[CSController] Connection failed.");
        }

    }

    private void createBoard() {
        gameBoard.getChildren().clear();
        cells = new ToggleButton[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                ToggleButton cell = new ToggleButton();
                cell.setPrefSize(60, 60);
                cell.getStyleClass().add("plot-button");
                int r = row;
                int c = col;
                cell.setOnAction(event -> {
                    selectedRow = r;
                    selectedCol = c;
                    refreshBoard();
                });
                gameBoard.add(cell, col, row);
                cells[row][col] = cell;
                cellState[row][col] = PlotState.EMPTY;
            }
        }
    }

    private void refreshBoard() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                ToggleButton cell = cells[row][col];
                cell.setSelected(row == selectedRow && col == selectedCol);
                updateCellState(cell, row, col);
            }
        }
        renderStatus();
    }

    private void updateCellState(ToggleButton cell, int row, int col) {
        PlotState state = cellState[row][col];
        cell.getStyleClass().removeAll("state-empty", "state-growing", "state-ripe");
        cell.setText(switch (state) {
            case EMPTY -> "Empty";
            case GROWING -> "Growing";
            case RIPE -> "Ripe";
        });
        switch (state) {
            case EMPTY -> cell.getStyleClass().add("state-empty");
            case GROWING -> cell.getStyleClass().add("state-growing");
            case RIPE -> cell.getStyleClass().add("state-ripe");
        }
    }

    private boolean ensureSelection() {
        return selectedRow >= 0 && selectedCol >= 0;
    }

    private void startRefreshTicker() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshBoard()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        if (client != null && connected) {
            try {
                client.quit();
                client.close();
            } catch (Exception e) {
                System.err.println("[CSController] Error during shutdown: " + e.getMessage());
            }
        }
    }

    public void handleUpdate(Map<String,Object> state) {
        Object c = state.get("coins");
        if (c instanceof Number n) coins = n.intValue();
        Object m = state.get("msg");
        if (m != null) statusMsg = String.valueOf(m);
        Object clientIdObj = state.get("clientId");
        if (clientIdObj != null) {
            myClientId = String.valueOf(clientIdObj);
            clientID.setText(STR."player: \{myClientId}");
        }
        Object boardObj = state.get("board");
        if (boardObj instanceof List<?> outer) {
            for (int i = 0; i < rows; i++) {
                List<?> rowList = (List<?>) outer.get(i);
                for (int j = 0; j < cols; j++) {
                    String s = String.valueOf(rowList.get(j)); // "EMPTY"/"GROWING"/"RIPE"
                    cellState[i][j] = PlotState.valueOf(s);
                }
            }
        }
        Object playersObj = state.get("players");
        if (playersObj instanceof Map<?,?> playersMap) {
            players.clear();
            playersMap.forEach((k, v) ->
                    players.put(String.valueOf(k), String.valueOf(v))
            );
            currentViewingId = players.get(myClientId);
            updatePlayersList();
            updateActionButtons();
        }
        refreshBoard();
        renderStatus();
    }

    public void handleError(String err) {
        onError(err);
    }

    private void onError(String err) {
        statusMsg = STR."ERR: \{err}";
        renderStatus();
    }
    private void renderStatus() {
        coinsLabel.setText("Coins: "+coins +" | "+ statusMsg);
    }
    private void updatePlayersList() {
        if (playersBox.getChildren().size() > 1) {
            playersBox.getChildren().remove(1, playersBox.getChildren().size());
        }

        for (String player : players.keySet()) {
            Button playerBtn = new Button(player);

            playerBtn.setText(player);
            if (player.equals(myClientId)) {
                playerBtn.setText("Me");
            }

            playerBtn.setOnAction(event -> {
                statusMsg = "Viewing player: " + player;
                renderStatus();
                client.view(player);
            });

            if (player.equals(players.get(player))) {
                // player viewing player -> online
                playerBtn.getStyleClass().add("player-online-button");
            } else {
                playerBtn.getStyleClass().add("player-offline-button");
            }

            playersBox.getChildren().add(playerBtn);
        }
    }
    private void updateActionButtons () {
        if (currentViewingId == null || myClientId == null) {
            return;
        }
        if (currentViewingId.equals(myClientId)){
            // me viewing myself
            harvestButton.setDisable(false);
            plantButton.setDisable(false);
            stealButton.setDisable(true); // cannot steal myself
        } else {
            harvestButton.setDisable(true);
            plantButton.setDisable(true);
            stealButton.setDisable(false);
        }
    }
    private void disableAllActions() {
        if (plantButton != null) plantButton.setDisable(true);
        if (harvestButton != null) harvestButton.setDisable(true);
        if (stealButton != null) stealButton.setDisable(true);
    }
    @FXML private void handlePlant() {
        if (!ensureSelection()) { statusMsg = "Select a plot first."; renderStatus(); return; }
        try {
            client.plant(selectedRow, selectedCol);
            statusMsg = "Plant requested...";
            renderStatus();
        } catch (Exception e) { onError(e.getMessage()); }
    }

    @FXML private void handleHarvest() {
        if (!ensureSelection()) { statusMsg = "Select a plot first."; renderStatus(); return; }
        try {
            client.harvest(selectedRow, selectedCol);
            statusMsg = "Harvest requested...";
            renderStatus();
        } catch (Exception e) { onError(e.getMessage()); }
    }

    @FXML private void handleSteal() {
        if (!ensureSelection()) { statusMsg = "Select a plot first."; renderStatus(); return; }
        try {
            client.steal(selectedRow, selectedCol);
            statusMsg = STR."\{myClientId} steal at (\{selectedRow}, \{selectedCol})";
            renderStatus();
        } catch (Exception e) { onError(e.getMessage()); }
    }

}
