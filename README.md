# CS209A Assignment 2 - QQ Farm

**Course:** CS209A - Computer System Design and Applications  
**Author:** Ximing Zheng (12311011)  
**Date:** 2025-11-22

## 1. Project Overview

This project is a simplified multiplayer version of "QQ Farm" implemented using **Java Socket Programming**, **Multithreading**, and **JavaFX**. The system follows a Client-Server architecture where the server maintains the authoritative game state (crops, coins, player lists) and handles concurrent requests from multiple clients.

Key features include:
- **Real-time Multiplayer:** Multiple clients can connect, view each other's farms, and interact in real-time.
- **Farming Mechanics:** Plant, grow (time-based), and harvest crops.
- **Social Interaction:** Visit friends' farms and steal crops from them.
- **Robust Concurrency:** Thread-safe server implementation ensuring atomic operations for stealing and harvesting.
- **Failure Recovery:** Handles client disconnects and server crashes gracefully with reconnection support.

## 2. How to Run

### Prerequisites
- Java 22 (or compatible JDK)
- Maven
- Dependencies: `com.google.code.gson:gson`, `org.openjfx:javafx-controls`, `org.openjfx:javafx-fxml`

### Step 1: Start the Server
The server must be started first. It listens on port `5050`.

```bash
# Navigate to the project root
mvn clean compile

# Run the Server class
mvn exec:java -Dexec.mainClass="org.example.demo.Server"
```
*You should see: `[Server] starting on 5050 ...`*

### Step 2: Start the Client(s)
You can launch multiple client instances to simulate different players.

```bash
# Open a new terminal for each client
mvn exec:java -Dexec.mainClass="org.example.demo.MainApp"
```
*On startup, a dialog will ask for a Player ID. Leave it empty to create a new user, or enter an existing ID (e.g., "1") to reconnect.*

### Step 3: Run Concurrency Stress Test (Bonus)
A dedicated test script is provided to verify the thread safety of the "Steal" mechanic.

```bash
# Ensure the Server is running first!
mvn exec:java -Dexec.mainClass="org.example.demo.ConcurrencyTest"
```
*This script simulates a Victim and two Thieves. The Thieves attempt to steal from the Victim at the exact same millisecond. Check the server logs to see atomic processing.*

## 3. Architecture & Design

### Client-Server Model
- **Server (`Server.java`):** The central authority. It holds a map of all `Farm` objects and `ClientHandler` threads. It runs a background `ScheduledExecutorService` to tick game time (crop growth).
- **Client (`GameClient.java` + `CSController.java`):** A "dumb" terminal that renders the state provided by the server. It sends user actions (PLANT, STEAL) as JSON requests and updates the JavaFX UI based on JSON broadcasts.

### Key Classes
- **`Farm`:** The core model class. Contains the 4x4 grid state (`PlotState`), coins, and logic for growth. **Crucially, methods like `steal()` and `harvest()` are `synchronized` to ensure thread safety.**
- **`ClientHandler`:** A server-side thread dedicated to one connected client. It listens for incoming JSON requests and dispatches them to the `Farm` model.
- **`CSController`:** The JavaFX Controller. It handles UI events and updates the view using `Platform.runLater()` to ensure thread safety on the client side.
- **`GameClient`:** Handles low-level socket I/O. It uses a separate thread (`Client-Sender`) for sending requests to avoid blocking the UI.

## 4. Communication Protocol

Communication is text-based using **JSON** (via Gson). Every message has an `op` (operation) or `type` field.

### Request Examples (Client -> Server)

**Login / Reconnect:**
```json
{ "op": "login", "id": "1" }
```

**Plant:**
```json
{ "op": "plant", "row": 0, "col": 1 }
```

**Steal:**
```json
{ "op": "steal", "row": 2, "col": 3 }
```

**View Friend:**
```json
{ "op": "view", "target": "2" }
```

### Response Examples (Server -> Client)

**State Update (Broadcast):**
```json
{
  "type": "state",
  "clientId": "1",
  "coins": 100,
  "msg": "Harvested successfully",
  "board": [["RIPE", "EMPTY", ...], ...],
  "players": { "1": "1", "2": "1" } // Who is viewing whom
}
```

**Error:**
```json
{ "type": "error", "msg": "Crop not ripe" }
```

## 5. Concurrency & Threading Model

### Server-Side: Thread Safety
The server must handle concurrent requests (e.g., two players stealing the same crop simultaneously).
- **Synchronization:** The `Farm` class uses `synchronized` methods (`steal`, `harvest`, `plant`). This acts as a monitor lock on the specific `Farm` instance.
- **Atomic Operations:** The `steal` method checks the crop state, calculates the yield, and deducts it in a single atomic block.
    ```java
    public synchronized int steal(int row, int col) {
        // 1. Check if RIPE
        // 2. Check if yield > 20%
        // 3. Deduct yield
        // 4. Return amount
    }
    ```
- **Concurrent Collections:** `ConcurrentHashMap` is used for managing `clients` and `viewers` to prevent `ConcurrentModificationException` during broadcasts.

### Client-Side: UI Responsiveness
- **Background I/O:** Network reading runs in a daemon thread (`net-reader`). Network writing runs in a single-thread executor (`Client-Sender`). **No network I/O happens on the JavaFX Application Thread.**
- **UI Updates:** All updates to the GUI are wrapped in `Platform.runLater(() -> { ... })` to ensure they are executed on the JavaFX thread, preventing `IllegalStateException`.

## 6. Concurrency Stress Test

The project includes a `ConcurrencyTest.java` script to demonstrate the robustness of the server.

- **Scenario:** A "Victim" plants a crop. Once ripe, two "Thieves" (simulated clients) send a `steal` request at the exact same moment using a `CountDownLatch`.
- **Verification:**
    - **Client Output:** Shows that one thief succeeds (or both succeed if yield allows), but the total stolen amount never exceeds the limit.
    - **Server Logs:** The server prints `[Server-Lock]` logs showing that requests are processed sequentially (e.g., "Yield reduced from 50 to 38", then "Yield reduced from 38 to ..."), proving the atomicity of the critical section.


## Environment

**java JDK**: openjdk-22 (Oracle OpenJDK 22.0.2)

**javafx-fxml**: 22.0.1

**javafx-controls**: 22.0.1

**maven**: 3.8.5

## File List

**Application.java**: the main entry point of the demo application

**Game.java**: manages the game logic and controls the game's behavior

**Controller.java**: handles JavaFX UI interactions and events

**board.fxml**: a game board prototype

**resources**: stores pictures for the game board (https://www.iconfont.cn/)

## Logic

- game start: allowing the user to select options and set up the game board

- operations validity: monitoring user actions, validating operations, and updating the board

- game finish: informing the user that the game has ended

## Notes

I suggest that you first complete the single-player mode. If you feel confident, you can directly reconstruct this project to include a two-player mode.

If you encounter any GUI issues while rendering multiple game boards, maybe you can check the $start$ method in the main entry point.

If you have any questions or find any bugs, feel free to contact me 12442018@mail.sustech.edu.cn or QQ:503652093 :)
