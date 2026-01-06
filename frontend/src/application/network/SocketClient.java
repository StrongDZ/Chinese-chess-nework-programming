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
 */
public class SocketClient {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private String username; // Context: username after login
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private Thread receiveThread;
    private Consumer<String> messageListener;

    /**
     * Connect to the server.
     * 
     * @param host Server hostname or IP
     * @param port Server port
     * @throws IOException if connection fails
     */
    public void connect(String host, int port) throws IOException {
        if (connected.get()) {
            disconnect();
        }

        socket = new Socket(host, port);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        connected.set(true);

        // Start receive thread
        receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * Disconnect from server.
     */
    public void disconnect() {
        connected.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        socket = null;
        input = null;
        output = null;
    }

    /**
     * Check if connected.
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
     * Send a message with MessageType and JSON payload.
     * Automatically adds username to payload if available and needed.
     * 
     * @param type Message type
     * @param payloadJson JSON payload as string (can be empty string or "{}")
     * @throws IOException if send fails
     */
    public void send(MessageType type, String payloadJson) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }

        // Process payload JSON and add username if needed
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
                    // Add username if not already present
                    if (!finalPayload.contains("\"username\"")) {
                        finalPayload = addUsernameToJson(finalPayload, username);
                    }
                    break;
                default:
                    // Other messages may or may not need username
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
        // Remove closing brace and add username field
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
     */
    private void receiveLoop() {
        try {
            while (connected.get() && socket != null && !socket.isClosed()) {
                // Read 4-byte length (network byte order)
                byte[] lengthBytes = new byte[4];
                int bytesRead = 0;
                while (bytesRead < 4) {
                    int n = input.read(lengthBytes, bytesRead, 4 - bytesRead);
                    if (n < 0) {
                        // Connection closed
                        connected.set(false);
                        break;
                    }
                    bytesRead += n;
                }

                if (!connected.get()) {
                    break;
                }

                // Convert to int (big-endian)
                ByteBuffer buffer = ByteBuffer.wrap(lengthBytes);
                buffer.order(ByteOrder.BIG_ENDIAN);
                int length = buffer.getInt();

                // Guard against too large messages (10MB limit)
                if (length < 0 || length > 10 * 1024 * 1024) {
                    System.err.println("Invalid message length: " + length);
                    connected.set(false);
                    break;
                }

                // Read message bytes
                byte[] messageBytes = new byte[length];
                bytesRead = 0;
                while (bytesRead < length) {
                    int n = input.read(messageBytes, bytesRead, length - bytesRead);
                    if (n < 0) {
                        connected.set(false);
                        break;
                    }
                    bytesRead += n;
                }

                if (!connected.get()) {
                    break;
                }

                String message = new String(messageBytes, "UTF-8");

                // Notify listener
                if (messageListener != null) {
                    messageListener.accept(message);
                }
            }
        } catch (IOException e) {
            if (connected.get()) {
                System.err.println("Receive error: " + e.getMessage());
            }
            connected.set(false);
        }
    }
}

