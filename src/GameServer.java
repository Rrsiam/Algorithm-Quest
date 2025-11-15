import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static GameState gameState = new GameState();
    private static boolean gameCompleted = false;
    private static int winnerId = -1;
    
    // Timer for game duration
    private static Timer gameTimer;
    private static final int GAME_DURATION = 600; // 10 minutes in seconds
    
    public static void main(String[] args) {
        System.out.println("Game Server started on port " + PORT);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            
            while (clients.size() < 2) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Player " + (clients.size() + 1) + " connected");
                
                ClientHandler clientThread = new ClientHandler(clientSocket, clients.size() + 1);
                clients.add(clientThread);
                pool.execute(clientThread);
            }
            
            System.out.println("Game starting with 2 players!");
            startGameTimer();
            broadcastToAll("START_GAME");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void startGameTimer() {
        gameTimer = new Timer(true);
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleTimeUp();
            }
        }, GAME_DURATION * 1000); // Convert to milliseconds
    }
    
    private static void handleTimeUp() {
        if (!gameCompleted) {
            gameCompleted = true;
            System.out.println("Time's up! No player found the IT room.");
            
            // Send defeat to all players
            broadcastToAll("TIME_UP_DEFEAT");
            
            // Close all connections after a delay
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    closeAllConnections();
                }
            }, 5000);
        }
    }
    
    public static void broadcastToAll(String message) {
        System.out.println("Broadcasting: " + message);
        if (gameCompleted && (message.startsWith("VICTORY") || message.startsWith("TIME_UP_DEFEAT"))) {
            return; // Prevent multiple victory/time-up messages
        }
        
        Iterator<ClientHandler> iterator = clients.iterator();
        while (iterator.hasNext()) {
            ClientHandler client = iterator.next();
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("Error sending to client " + client.getPlayerId() + ": " + e.getMessage());
                iterator.remove();
            }
        }
    }
    
    public static void updatePlayerPosition(int playerId, double x, double y) {
        gameState.updatePlayerPosition(playerId, x, y);
        broadcastToAll("PLAYER_POSITION:" + playerId + ":" + x + ":" + y);
    }
    
    public static void playerCollectedItem(int playerId, String itemId) {
        gameState.collectItem(itemId);
        broadcastToAll("ITEM_COLLECTED:" + playerId + ":" + itemId);
    }
    
    public static void playerVisitedRoom(int playerId, int roomId) {
        gameState.visitRoom(roomId);
        broadcastToAll("ROOM_VISITED:" + playerId + ":" + roomId);
    }
    
    // FIXED: This method now properly handles victory by sending different messages to winner and loser
    public static synchronized void handleVictory(int playerId) {
        if (!gameCompleted) {
            gameCompleted = true;
            winnerId = playerId;
            System.out.println("Player " + playerId + " won the game!");
            
            // Cancel the game timer since someone won
            if (gameTimer != null) {
                gameTimer.cancel();
            }
            
            // FIXED: Send VICTORY to winner and DEFEAT to loser
            for (ClientHandler client : clients) {
                if (client.getPlayerId() == playerId) {
                    System.out.println("Sending VICTORY to player " + playerId);
                    client.sendMessage("VICTORY:" + playerId);
                } else {
                    System.out.println("Sending DEFEAT to player " + client.getPlayerId());
                    client.sendMessage("DEFEAT:The other player reached IT room first!");
                }
            }
            
            // Close all connections after a delay
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    closeAllConnections();
                }
            }, 5000);
        }
    }
    
    public static void handleTimeUpMessage() {
        handleTimeUp();
    }
    
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Player " + client.getPlayerId() + " disconnected");
        
        // If a player disconnects, the other player wins
        if (clients.size() == 1 && !gameCompleted) {
            ClientHandler remaining = clients.iterator().next();
            handleVictory(remaining.getPlayerId());
        }
    }
    
    private static void closeAllConnections() {
        System.out.println("Closing all connections...");
        for (ClientHandler client : clients) {
            try {
                client.closeConnection();
            } catch (Exception e) {
                System.err.println("Error closing client connection: " + e.getMessage());
            }
        }
        clients.clear();
    }
    
    public static boolean isGameCompleted() {
        return gameCompleted;
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private int playerId;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = true;
    
    public ClientHandler(Socket socket, int playerId) {
        this.socket = socket;
        this.playerId = playerId;
    }
    
    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send player ID to client
            sendMessage("PLAYER_ID:" + playerId);
            
            String inputLine;
            while ((inputLine = in.readLine()) != null && connected) {
                System.out.println("Received from Player " + playerId + ": " + inputLine);
                handleMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Player " + playerId + " disconnected: " + e.getMessage());
        } finally {
            closeConnection();
            GameServer.removeClient(this);
        }
    }
    
    private void handleMessage(String message) {
        if (GameServer.isGameCompleted()) {
            return; // Ignore messages after game completion
        }
        
        String[] parts = message.split(":");
        switch (parts[0]) {
            case "POSITION_UPDATE":
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                GameServer.updatePlayerPosition(playerId, x, y);
                break;
                
            case "ITEM_COLLECTED":
                GameServer.playerCollectedItem(playerId, parts[1]);
                break;
                
            case "ROOM_VISITED":
                int roomId = Integer.parseInt(parts[1]);
                GameServer.playerVisitedRoom(playerId, roomId);
                break;
                
            case "FLOOR_CHANGED":
                GameServer.broadcastToAll("FLOOR_CHANGED:" + playerId + ":" + parts[1]);
                break;
                
            // FIXED: When client reports victory, handle it on server
            case "VICTORY":
                int winningPlayerId = Integer.parseInt(parts[1]);
                System.out.println("Player " + winningPlayerId + " reported victory to server");
                GameServer.handleVictory(winningPlayerId);
                break;
                
            case "TIME_UP":
                GameServer.handleTimeUpMessage();
                break;
                
            case "PLAYER_DISCONNECTING":
                System.out.println("Player " + playerId + " is disconnecting gracefully");
                connected = false;
                break;
        }
    }
    
    public void sendMessage(String message) {
        if (out != null && !socket.isClosed() && connected) {
            try {
                out.println(message);
                out.flush();
                System.out.println("Sent to Player " + playerId + ": " + message);
            } catch (Exception e) {
                System.err.println("Failed to send message to player " + playerId + ": " + e.getMessage());
                connected = false;
            }
        }
    }
    
    public void closeConnection() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection for player " + playerId + ": " + e.getMessage());
        }
    }
    
    public int getPlayerId() {
        return playerId;
    }
}

class GameState {
    private Map<Integer, PlayerPosition> playerPositions = new ConcurrentHashMap<>();
    private Set<String> collectedItems = ConcurrentHashMap.newKeySet();
    private Set<Integer> visitedRooms = ConcurrentHashMap.newKeySet();
    
    public void updatePlayerPosition(int playerId, double x, double y) {
        playerPositions.put(playerId, new PlayerPosition(x, y));
    }
    
    public void collectItem(String itemId) {
        collectedItems.add(itemId);
    }
    
    public void visitRoom(int roomId) {
        visitedRooms.add(roomId);
    }
}

class PlayerPosition {
    double x, y;
    
    PlayerPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
}