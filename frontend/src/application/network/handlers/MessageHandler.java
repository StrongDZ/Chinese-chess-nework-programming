package application.network.handlers;

/**
 * Interface for message handlers.
 * Each handler is responsible for a specific domain (auth, game, friend, etc.)
 */
public interface MessageHandler {
    /**
     * Check if this handler can handle the given message type.
     * 
     * @param messageType The message type string (e.g., "AUTHENTICATED", "MOVE")
     * @return true if this handler can handle the message
     */
    boolean canHandle(String messageType);
    
    /**
     * Handle the message.
     * 
     * @param messageType The message type string
     * @param payload The JSON payload string
     * @return true if handled successfully
     */
    boolean handle(String messageType, String payload);
}

