package org.example.demo;


import java.util.Random;

/**
 * Minimal game logic to demonstrate multithreading and synchronization.
 */
public class Farm {
    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int HARVEST_REWARD = 12;
    private static final int STEAL_REWARD = 3; // 25%
    private static final long GROW_MS = 10_000;

    private final PlotState[][] board = new PlotState[ROWS][COLS];
    private final long[][] plantedAt = new long[ROWS][COLS];
    private final int[][] plotYield = new int[ROWS][COLS];
    private final Random random = new Random();
    private final String id;
    private int coins = 40;
    public Farm(String id) {
        this.id = id;
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = PlotState.EMPTY;
                plotYield[i][j] = 0;
            }
        }
    }
    public synchronized int getCoins() {
        return coins;
    }
    public synchronized void addCoins(int amount) {
        this.coins += amount;
    }
    public synchronized PlotState getState(int row, int col) {
        return board[row][col];
    }
    public synchronized void plant(int row, int col) {
        if (!checkInBounds(row,col)) {
            throw new IllegalStateException("Out of bound");
        }
        if (board[row][col] != PlotState.EMPTY) {
            throw new IllegalStateException("Plot occupied");
        }
        if (coins < PLANT_COST) {
            throw new IllegalStateException("Not enough coins");
        }
        coins -= PLANT_COST;
        plantedAt[row][col] = System.currentTimeMillis();
        board[row][col] = PlotState.GROWING;
        plotYield[row][col] = HARVEST_REWARD;
    }

    public synchronized void harvest(int row, int col) {
        if (!checkInBounds(row,col)) {
            throw new IllegalStateException("Out of bound");
        }
        if (board[row][col] != PlotState.RIPE) {
            throw new IllegalStateException("Crop not ripe");
        }
        board[row][col] = PlotState.EMPTY;
        int yield = plotYield[row][col];
        plotYield[row][col] = 0;
        
        coins += yield;
    }

    public synchronized int steal(int row, int col) {
        System.out.println(Thread.currentThread().getName() + " [Server-Lock] Start processing steal request at (" + row + "," + col + ")");
        if (!checkInBounds(row,col)) return -1;
        
        // Must be RIPE
        if (board[row][col] != PlotState.RIPE) {
            System.out.println(Thread.currentThread().getName() + " [Server-Lock] Failed: Crop not ripe");
            return -2; 
        }

        int currentYield = plotYield[row][col];
        // Stealable period is yield in 20% to 100%
        double minYield = HARVEST_REWARD * 0.20;
        
        if (currentYield < minYield) {
            System.out.println(Thread.currentThread().getName() + " [Server-Lock] Failed: Yield too low (" + currentYield + " < " + minYield + ")");
            return -3;
        }

        // Steal 0% to 25% of current yield
        int maxAmount = (int) (currentYield * 0.25);

        int amount = random.nextInt(maxAmount + 1);
        
        plotYield[row][col] -= amount;
        System.out.println(Thread.currentThread().getName() + " [Server-Lock] Success: Stole " + amount + ". Yield reduced from " + currentYield + " to " + plotYield[row][col]);
        return amount;
    }

    public synchronized boolean tickGrow() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (board[i][j] == PlotState.GROWING) {
                    if (now - plantedAt[i][j] >= GROW_MS) {
                        board[i][j] = PlotState.RIPE;
                        plantedAt[i][j] = 0L;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    public int getRows() {
        return ROWS;
    }

    public int getCols() {
        return COLS;
    }
    public String getId() {
        return id;
    }
    public synchronized PlotState[][] snapshot() {
        PlotState[][] copy = new PlotState[ROWS][COLS];
        for (int i = 0; i < ROWS; i++)
            System.arraycopy(board[i], 0, copy[i], 0, COLS);
        return copy;
    }
    private boolean checkInBounds(int r, int c) {
        if (r < 0 || r >= ROWS || c < 0 || c >= COLS) return false;
        else return true;
    }
}