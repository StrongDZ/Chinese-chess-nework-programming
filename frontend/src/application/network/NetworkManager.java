package application.network;

import application.network.handlers.*;
import application.network.senders.*;
import application.state.UIState;
import javafx.application.Platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    
    // Reconnection state
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private Thread reconnectThread;
    
    // Saved credentials for auto-login after reconnect
    private String savedUsername;
    private String savedPassword;
    
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
    private GameHandler gameHandler; // Store reference for setting PlayWithFriendPanel
    
    private void initializeHandlers() {
        // Auth handler
        authHandler = new AuthHandler(uiState);
        authHandler.setOnUsernameSet(username -> socketClient.setUsername(username));
        handlers.add(authHandler);
        
        // Game handler
        gameHandler = new GameHandler(uiState);
        handlers.add(gameHandler);
        
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
    
    /**
     * Set PlayWithFriendPanel for game handler to handle challenge requests/responses.
     */
    public void setGameHandlerPlayWithFriendPanel(application.components.PlayWithFriendPanel panel) {
        if (gameHandler != null) {
            gameHandler.setPlayWithFriendPanel(panel);
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
                // Show reconnecting overlay
                if (uiState != null) {
                    uiState.setReconnectingVisible(true);
                }
            });
            // Start reconnection in background thread
            startReconnection();
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
        stopReconnection(); // Stop any ongoing reconnection attempts
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
    
    /**
     * Start reconnection loop in background thread.
     * Will keep trying to reconnect until successful.
     */
    private void startReconnection() {
        // Only start one reconnection thread at a time
        if (isReconnecting.compareAndSet(false, true)) {
            reconnectThread = new Thread(() -> {
                System.out.println("[NetworkManager] Starting reconnection loop...");
                
                while (!isConnected() && isReconnecting.get()) {
                    try {
                        // Wait 2 seconds before attempting reconnect
                        Thread.sleep(2000);
                        
                        System.out.println("[NetworkManager] Attempting to reconnect to " + serverHost + ":" + serverPort);
                        connect(serverHost, serverPort);
                        
                        // If connection successful, wait a bit to verify it's stable
                        if (isConnected()) {
                            Thread.sleep(500);
                            if (isConnected()) {
                                // Connection successful
                                System.out.println("[NetworkManager] Reconnected successfully");
                                
                                // Auto-login if credentials are saved
                                if (savedUsername != null && savedPassword != null &&
                                    !savedUsername.isEmpty() && !savedPassword.isEmpty()) {
                                    try {
                                        System.out.println("[NetworkManager] Auto-login with saved credentials for user: " + savedUsername);
                                        auth().login(savedUsername, savedPassword);
                                        // Server will send AUTHENTICATED which triggers GameHandler to restore game state
                                    } catch (IOException e) {
                                        System.err.println("[NetworkManager] Auto-login failed: " + e.getMessage());
                                    }
                                } else {
                                    // No saved credentials - hide overlay and let user login manually
                                    Platform.runLater(() -> {
                                        if (uiState != null) {
                                            uiState.setReconnectingVisible(false);
                                        }
                                    });
                                }
                                
                                isReconnecting.set(false);
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        // Reconnection cancelled
                        System.out.println("[NetworkManager] Reconnection interrupted");
                        isReconnecting.set(false);
                        return;
                    } catch (IOException e) {
                        // Connection failed, will retry in next iteration
                        System.err.println("[NetworkManager] Reconnection attempt failed: " + e.getMessage());
                    }
                }
                
                // If we exit the loop and still not connected, stop reconnecting
                isReconnecting.set(false);
            }, "NetworkManager-ReconnectThread");
            reconnectThread.setDaemon(true);
            reconnectThread.start();
        }
    }
    
    /**
     * Stop reconnection attempts.
     * Called when intentionally disconnecting.
     */
    public void stopReconnection() {
        if (isReconnecting.get()) {
            isReconnecting.set(false);
            if (reconnectThread != null && reconnectThread.isAlive()) {
                reconnectThread.interrupt();
            }
            Platform.runLater(() -> {
                if (uiState != null) {
                    uiState.setReconnectingVisible(false);
                }
            });
        }
    }
    
    // ========== Credentials Management ==========
    
    /**
     * Save credentials for auto-login after reconnect.
     * Called after successful login.
     * 
     * @param username Username
     * @param password Password (stored in memory only, never persisted)
     */
    public void saveCredentials(String username, String password) {
        this.savedUsername = username;
        this.savedPassword = password;
        System.out.println("[NetworkManager] Credentials saved for user: " + username);
    }
    
    /**
     * Clear saved credentials.
     * Called on logout.
     */
    public void clearCredentials() {
        this.savedUsername = null;
        this.savedPassword = null;
        System.out.println("[NetworkManager] Credentials cleared");
    }
    
    /**
     * Check if credentials are saved for auto-login.
     */
    public boolean hasCredentials() {
        return savedUsername != null && savedPassword != null &&
               !savedUsername.isEmpty() && !savedPassword.isEmpty();
    }
    
    /**
     * Hide reconnecting overlay.
     * Called when auto-login succeeds.
     */
    public void hideReconnectingOverlay() {
        Platform.runLater(() -> {
            if (uiState != null) {
                uiState.setReconnectingVisible(false);
            }
        });
    }
}
