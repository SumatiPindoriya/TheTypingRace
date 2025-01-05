import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class server {
    private static final int PORT = 1234; // Port number for the server to listen on
    private static final List<List<Socket>> groups = new ArrayList<>(); // List of groups of client sockets
    private static final int GROUP_SIZE = 3; // Number of clients per group
    private static final List<Socket> clients = new ArrayList<>(); // List of connected client sockets
    private static final Map<Socket, String> socketUsernameMap = new ConcurrentHashMap<>(); // Map of socket to username
    private static final Map<Socket, Boolean> registrationConfirmed = new ConcurrentHashMap<>(); // Map to track registration confirmation
    private static final Map<Socket, ClientHandler> clientHandlers = new ConcurrentHashMap<>(); // Map to track ClientHandler instances
    private static final Map<Socket, Boolean> viewerMap = new ConcurrentHashMap<>(); // Map to track viewers
    private static final Map<String, Integer> scores = new ConcurrentHashMap<>(); // Map to track scores
    private static final Map<Socket, Boolean> playAgainRequests = new ConcurrentHashMap<>(); // Map to track play-again requests

    private static void addClientToGroup(Socket clientSocket, server serverInstance) {
        synchronized (groups) { // Synchronize access to the groups list
            for (List<Socket> group : groups) { // Iterate over each group
                if (group.size() < GROUP_SIZE) { // If the group is not full
                    group.add(clientSocket); // Add the client to the group
                    registrationConfirmed.put(clientSocket, false); // Mark registration as not confirmed
                    viewerMap.put(clientSocket, false); // Mark as not a viewer
                    if (group.size() == GROUP_SIZE) { // If the group is now full
                        new Thread(() -> waitForRegistrations(group, serverInstance)).start(); // Start waiting for registrations in a new thread
                    }
                    return; // Exit the method
                }
            }
            List<Socket> newGroup = new ArrayList<>(); // Create a new group
            newGroup.add(clientSocket); // Add the client to the new group
            registrationConfirmed.put(clientSocket, false); // Mark registration as not confirmed
            viewerMap.put(clientSocket, false); // Mark as not a viewer
            groups.add(newGroup); // Add the new group to the list of groups
        }
    }

    public static void main(String[] args) {
        server serverInstance = new server(); // Create a server instance

        try (ServerSocket serverSocket = new ServerSocket(PORT)) { // Create a server socket listening on PORT
            System.out.println("Server started. Listening on Port " + PORT); // Print server start message
            ExecutorService executorService = Executors.newFixedThreadPool(5); // Create a thread pool for handling clients

            while (true) { // Infinite loop to accept client connections
                Socket clientSocket = serverSocket.accept(); // Accept a new client connection
                System.out.println("New client connected: " + clientSocket); // Print a message with the client's socket info
                synchronized (clients) { // Synchronize access to the clients list
                    clients.add(clientSocket); // Add the new client to the clients list
                }
                ClientHandler clientHandler = new ClientHandler(clientSocket, serverInstance); // Create a new ClientHandler
                clientHandlers.put(clientSocket, clientHandler); // Store the ClientHandler instance
                addClientToGroup(clientSocket, serverInstance); // Add the client to a group
                executorService.submit(clientHandler); // Handle the client in a new thread
            }
        } catch (IOException e) { // Handle exceptions
            System.err.println("Server exception: " + e.getMessage()); // Print the exception message
            e.printStackTrace(); // Print the stack trace
        }
    }

    private static void startGameForGroup(List<Socket> group, server serverInstance) {
        System.out.println("Starting game for group: " + group); // Print a message with the group's info
        for (Socket socket : group) { // Iterate over each client in the group
            ClientHandler handler = clientHandlers.get(socket); // Get the ClientHandler for the socket
            if (handler != null) {
                handler.startGame(); // Notify the ClientHandler to start the game
            }
        }
    }

    private static void waitForRegistrations(List<Socket> group, server serverInstance) {
        // Check for registration confirmation
        while (true) {
            synchronized (registrationConfirmed) {
                boolean allConfirmed = group.stream().allMatch(socket -> registrationConfirmed.get(socket));
                if (allConfirmed) {
                    startGameForGroup(group, serverInstance);
                    break;
                }
            }
            try {
                Thread.sleep(100); // Small delay to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }
    }

    public void updateUsernameMap(Socket socket, String username) {
        socketUsernameMap.put(socket, username); // Update the map with the socket and username
    }

    public void confirmRegistration(Socket socket) {
        registrationConfirmed.put(socket, true); // Mark the registration as confirmed for the given socket
    }

    public void markAsViewer(Socket socket) {
        viewerMap.put(socket, true); // Mark the socket as a viewer
    }

    public void markReadyToPlayAgain(Socket socket) {
        playAgainRequests.put(socket, true); // Mark the socket as ready to play again
        checkAllPlayAgain();
    }

    private void checkAllPlayAgain() {
        synchronized (playAgainRequests) {
            boolean allReady = playAgainRequests.size() == clients.size() &&
                    playAgainRequests.values().stream().allMatch(Boolean::booleanValue);
            if (allReady) {
                playAgainRequests.clear(); // Clear play-again requests
                resetGame();
            }
        }
    }

    private void resetGame() {
        scores.clear();
        for (Socket socket : clients) {
            registrationConfirmed.put(socket, false); // Reset registration confirmations
        }
        for (List<Socket> group : groups) {
            new Thread(() -> waitForRegistrations(group, this)).start(); // Start waiting for registrations again
        }
    }

    public void broadcastWordUpdate(String word, String playerPerformance) {
        StringBuilder liveScoresBuilder = new StringBuilder("Live Scores: ");
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            liveScoresBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append(" ");
        }
        String liveScores = liveScoresBuilder.toString().trim();

        for (Socket socket : socketUsernameMap.keySet()) {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                String message = "UPDATE " + word + " " + playerPerformance + " " + liveScores;
                out.println(message);
                ClientHandler handler = clientHandlers.get(socket);
                if (handler != null) {
                    handler.updateWordForViewer(word, playerPerformance, liveScores);
                }
            } catch (IOException e) {
                System.err.println("Error broadcasting update: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void broadcastWinner(String winner, int score) {
        for (Socket socket : socketUsernameMap.keySet()) {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                String message = "The Winner is " + winner + " with score: " + score;
                out.println(message);
                out.println("Do you want to play again? (yes/no)");
            } catch (IOException e) {
                System.err.println("Error broadcasting winner: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static String getUsername(Socket socket) {
        return socketUsernameMap.get(socket); // Get the username for the given socket
    }

    public static Socket getUsernameSocket(String username) {
        for (Map.Entry<Socket, String> entry : socketUsernameMap.entrySet()) { // Iterate over each entry in the socket-username map
            if (entry.getValue().equals(username)) { // If the username matches
                return entry.getKey(); // Return the corresponding socket
            }
        }
        return null; // Return null if no match found
    }

    public int getGroupSize() {
        return GROUP_SIZE;
    }
}
