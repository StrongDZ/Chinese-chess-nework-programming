package application.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Socket client for connecting to the C++ backend server.
 * Handles connection, message sending/receiving, and maintains user context.
 * 
 * Connection is kept alive until explicitly disconnected via logout.
 */
public class SocketClient {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private String username; // Context: username after login
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean intentionalDisconnect = new AtomicBoolean(false);
    private Thread receiveThread;
    private Consumer<String> messageListener;
    private Consumer<String> disconnectListener; // Called when disconnected unexpectedly

    /**
     * Connect to the server.
     * If already connected, does nothing (returns immediately).
     * 
     * @param host Server hostname or IP
     * @param port Server port
     * @throws IOException if connection fails
     */
    public synchronized void connect(String host, int port) throws IOException {
        // If already connected, don't reconnect
        if (isConnected()) {
            return;
        }

        // Reset flags
        intentionalDisconnect.set(false);
        
        // Clean up any stale state
        cleanupSocket();

        socket = new Socket(host, port);
        
        // Configure socket
        socket.setKeepAlive(true); // Enable TCP keepalive
        socket.setTcpNoDelay(true); // Disable Nagle's algorithm for lower latency
        
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        connected.set(true);

        // Start receive thread
        receiveThread = new Thread(this::receiveLoop, "SocketClient-ReceiveThread");
        receiveThread.setDaemon(true);
        receiveThread.start();
        
        System.out.println("[SocketClient] Connected to " + host + ":" + port);
    }
    
    /**
     * Clean up socket resources.
     */
    private void cleanupSocket() {
        if (socket != null) {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
        }
        input = null;
        output = null;
    }

    /**
     * Disconnect from server intentionally (e.g., on logout).
     * This is the ONLY way to properly close the connection.
     */
    public synchronized void disconnect() {
        intentionalDisconnect.set(true);
        connected.set(false);
        cleanupSocket();
        username = null;
        System.out.println("[SocketClient] Disconnected");
    }

    /**
     * Check if connected to server.
     */
    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    /**
     * Set username context (called after successful login).
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Get current username from context.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Set message listener for incoming messages.
     */
    public void setMessageListener(Consumer<String> listener) {
        this.messageListener = listener;
    }
    
    /**
     * Set disconnect listener (called when connection is lost unexpectedly).
     */
    public void setDisconnectListener(Consumer<String> listener) {
        this.disconnectListener = listener;
    }

    /**
     * Send a message with MessageType and JSON payload.
     * 
     * @param type Message type
     * @param payloadJson JSON payload as string (can be empty string or "{}")
     * @throws IOException if send fails
     */
    public synchronized void send(MessageType type, String payloadJson) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }

        // Process payload JSON
        String finalPayload = payloadJson;
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            finalPayload = "{}";
        }

        // Auto-add username to payload for messages that need it
        if (username != null && !username.isEmpty()) {
            switch (type) {
                case LOGOUT:
                case USER_STATS:
                case GAME_HISTORY:
                    if (!finalPayload.contains("\"username\"")) {
                        finalPayload = addUsernameToJson(finalPayload, username);
                    }
                    break;
                default:
                    break;
            }
        }

        // Build message: "MESSAGE_TYPE <JSON_PAYLOAD>"
        String message;
        if (finalPayload.equals("{}") || finalPayload.trim().isEmpty()) {
            message = type.toProtocolString();
        } else {
            message = type.toProtocolString() + " " + finalPayload;
        }

        // Send: 4 bytes length (network byte order) + message bytes
        byte[] messageBytes = message.getBytes("UTF-8");
        int length = messageBytes.length;

        // Write length as 4-byte big-endian integer
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(length);
        output.write(buffer.array());
        output.write(messageBytes);
        output.flush();
        System.out.println("[DEBUG SocketClient] Sent (len=" + length + "): " + message);
    }

    /**
     * Convenience method to send message with empty payload.
     */
    public void send(MessageType type) throws IOException {
        send(type, "{}");
    }

    /**
     * Helper to add username to JSON string.
     */
    private String addUsernameToJson(String json, String username) {
        json = json.trim();
        if (json.equals("{}") || json.isEmpty()) {
            return "{\"username\":\"" + escapeJson(username) + "\"}";
        }
        if (json.endsWith("}")) {
            String content = json.substring(1, json.length() - 1).trim();
            if (!content.isEmpty() && !content.endsWith(",")) {
                content += ",";
            }
            return "{" + content + "\"username\":\"" + escapeJson(username) + "\"}";
        }
        return json;
    }

    /**
     * Escape string for JSON.
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Receive loop running in background thread.
     * Blocks waiting for messages from server.
     * Only exits when disconnected or error occurs.
     */
    private void receiveLoop() {
        try {
            while (!intentionalDisconnect.get() && isConnected()) {
                // Read one message - this BLOCKS until data arrives
                if (!readMessage()) {
                    // readMessage returned false - connection was closed
                    if (!intentionalDisconnect.get()) {
                        System.out.println("[SocketClient] Server closed connection");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            if (!intentionalDisconnect.get()) {
                System.err.println("[SocketClient] Receive error: " + e.getMessage());
            }
        }
        
        // Mark as disconnected
        connected.set(false);
        
        // Notify disconnect listener if not intentional
        if (!intentionalDisconnect.get() && disconnectListener != null) {
            disconnectListener.accept("Connection lost");
        }
    }
    
    /**
     * Read a single message from the socket.
     * BLOCKS until data arrives or connection is closed.
     * 
     * @return true if message was read successfully, false if connection closed
     */
    private boolean readMessage() {
        try {
            if (input == null) {
                return false;
            }
            
            // Read 4-byte length (network byte order) - BLOCKS HERE
            byte[] lengthBytes = new byte[4];
            int bytesRead = 0;
            while (bytesRead < 4) {
                int n = input.read(lengthBytes, bytesRead, 4 - bytesRead);
                if (n < 0) {
                    // Server closed connection
                    return false;
                }
                bytesRead += n;
            }

            // Convert to int (big-endian)
            ByteBuffer buffer = ByteBuffer.wrap(lengthBytes);
            buffer.order(ByteOrder.BIG_ENDIAN);
            int length = buffer.getInt();

            // Guard against invalid/too large messages
            if (length < 0 || length > 10 * 1024 * 1024) {
                System.err.println("[SocketClient] Invalid message length: " + length);
                return false;
            }

            // Read message bytes
            byte[] messageBytes = new byte[length];
            bytesRead = 0;
            while (bytesRead < length) {
                int n = input.read(messageBytes, bytesRead, length - bytesRead);
                if (n < 0) {
                    return false;
                }
                bytesRead += n;
            }

            String message = new String(messageBytes, "UTF-8");
            System.out.println("[DEBUG SocketClient] Received message (len=" + length + "): " + message);

            // Notify listener
            if (messageListener != null) {
                System.out.println("[DEBUG SocketClient] Notifying messageListener...");
                messageListener.accept(message);
                System.out.println("[DEBUG SocketClient] messageListener notified");
            } else {
                System.err.println("[DEBUG SocketClient] WARNING: messageListener is null!");
            }
            
            return true;
            
        } catch (IOException e) {
            if (!intentionalDisconnect.get()) {
                System.err.println("[SocketClient] Read error: " + e.getMessage());
            }
            return false;
        }
    }
}
