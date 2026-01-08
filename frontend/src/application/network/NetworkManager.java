package application.network;

import application.network.handlers.*;
import application.network.senders.*;
import application.state.UIState;
import javafx.application.Platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages socket connection and message handling for the application.
 * Singleton pattern - provides a centralized interface for network operations.
 * 
 * Uses modular handlers for receiving messages and senders for sending messages.
 */
public class NetworkManager {
    private static NetworkManager instance;
    
    // Server config (set via command-line args)
    private static String serverHost = "localhost";
    private static int serverPort = 8080;
    
    // Core components
    private SocketClient socketClient;
    private UIState uiState;
    
    // Handlers for receiving messages
    private final List<MessageHandler> handlers = new ArrayList<>();
    private AuthHandler authHandler;
    
    // Senders for sending messages
    private AuthSender authSender;
    private GameSender gameSender;
    private FriendSender friendSender;
    private InfoSender infoSender;
    
    private NetworkManager() {
        socketClient = new SocketClient();
    }
    
    /**
     * Get the singleton instance.
     */
    public static NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }
    
    /**
     * Initialize network manager with UI state.
     * Must be called before using the network manager.
     */
    public void initialize(UIState state) {
        this.uiState = state;
        
        // Initialize handlers
        initializeHandlers();
        
        // Initialize senders
        initializeSenders();
        
        // Setup message listener
        setupMessageListener();
    }
    
    private FriendHandler friendHandler; // Store reference for setting root pane
    
    private void initializeHandlers() {
        // Auth handler
        authHandler = new AuthHandler(uiState);
        authHandler.setOnUsernameSet(username -> socketClient.setUsername(username));
        handlers.add(authHandler);
        
        // Game handler
        handlers.add(new GameHandler(uiState));
        
        // Friend handler
        friendHandler = new FriendHandler(uiState);
        handlers.add(friendHandler);
        
        // Info handler
        handlers.add(new InfoHandler(uiState));
    }
    
    /**
     * Set root pane for friend handler to show notification dialogs.
     */
    public void setFriendHandlerRootPane(javafx.scene.layout.Pane rootPane) {
        if (friendHandler != null) {
            friendHandler.setRootPane(rootPane);
        }
    }
    
    private void initializeSenders() {
        authSender = new AuthSender(socketClient);
        gameSender = new GameSender(socketClient);
        friendSender = new FriendSender(socketClient);
        infoSender = new InfoSender(socketClient);
    }
    
    private void setupMessageListener() {
        // Message listener
        socketClient.setMessageListener(message -> {
            Platform.runLater(() -> {
                try {
                    handleMessage(message);
                } catch (Exception e) {
                    System.err.println("[NetworkManager] Error handling message: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
        
        // Disconnect listener (called when connection lost unexpectedly)
        socketClient.setDisconnectListener(reason -> {
            Platform.runLater(() -> {
                System.err.println("[NetworkManager] Disconnected: " + reason);
                // TODO: Show reconnection dialog or notification to user
                // uiState.showConnectionLost();
            });
        });
    }
    
    /**
     * Handle incoming message from server.
     * Routes message to appropriate handler.
     */
    private void handleMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        // Parse message: "MESSAGE_TYPE <JSON_PAYLOAD>"
        String[] parts = message.split(" ", 2);
        if (parts.length == 0) {
            return;
        }
        
        String messageType = parts[0].trim();
        String payload = parts.length > 1 ? parts[1].trim() : "{}";
        
        // Global logging: log all processed messages
        String username = socketClient.getUsername();
        String logPrefix = username != null && !username.isEmpty() 
            ? "[PROCESS user=" + username + "]" 
            : "[PROCESS]";
        System.out.println(logPrefix + " " + messageType);
        
        // Find handler that can handle this message
        for (MessageHandler handler : handlers) {
            if (handler.canHandle(messageType)) {
                if (handler.handle(messageType, payload)) {
                    return; // Message handled
                }
            }
        }
    }
    
    // ========== Connection Methods ==========
    
    /**
     * Connect to the server.
     */
    public void connect(String host, int port) throws IOException {
        socketClient.connect(host, port);
    }
    
    /**
     * Disconnect from server.
     */
    public void disconnect() {
        socketClient.disconnect();
    }
    
    /**
     * Check if connected to server.
     */
    public boolean isConnected() {
        return socketClient.isConnected();
    }
    
    // ========== Sender Accessors ==========
    
    /**
     * Get auth sender for login/register/logout operations.
     * Usage: NetworkManager.getInstance().auth().login(username, password);
     */
    public AuthSender auth() {
        return authSender;
    }
    
    /**
     * Get game sender for game-related operations.
     * Usage: NetworkManager.getInstance().game().sendMove(fromX, fromY, toX, toY);
     */
    public GameSender game() {
        return gameSender;
    }
    
    /**
     * Get friend sender for friend-related operations.
     * Usage: NetworkManager.getInstance().friend().sendFriendRequest(username);
     */
    public FriendSender friend() {
        return friendSender;
    }
    
    /**
     * Get info sender for requesting info from server.
     * Usage: NetworkManager.getInstance().info().requestPlayerList();
     */
    public InfoSender info() {
        return infoSender;
    }
    
    
    /**
     * Get current username from socket client context.
     */
    public String getUsername() {
        return socketClient.getUsername();
    }
    
    // ========== Server Config (Static) ==========
    
    /**
     * Set server host and port from command-line arguments.
     * Call this before launching the application.
     * 
     * @param host Server hostname or IP
     * @param port Server port
     */
    public static void setServerConfig(String host, int port) {
        serverHost = host;
        serverPort = port;
        System.out.println("[NetworkManager] Server config: " + host + ":" + port);
    }
    
    /**
     * Get configured server host.
     */
    public static String getServerHost() {
        return serverHost;
    }
    
    /**
     * Get configured server port.
     */
    public static int getServerPort() {
        return serverPort;
    }
    
    /**
     * Connect to the configured server.
     */
    public void connectToServer() throws IOException {
        connect(serverHost, serverPort);
    }
}
