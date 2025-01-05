import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static Map<String, String> userDatabase = new ConcurrentHashMap<>();
    private static Map<Socket, String> socketUsernameMap = new ConcurrentHashMap<>();
    private static Map<String, Integer> scores = new ConcurrentHashMap<>(); // Map of username to scores
    private final Socket clientSocket;
    private final server server; // Assuming there's a server class that is passed to ClientHandler
    private PrintWriter out;
    private boolean isViewer = false;
    private DisplayWords displayWords;

    public ClientHandler(Socket socket, server server) {
        this.clientSocket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            out.println("Welcome! Do you have an account? (yes/no) /n If you have account that write login to login.");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Client response: " + line);
                handleClientInput(line, in, out);
            }
        } catch (IOException e) {
            System.err.println("Exception caught when trying to listen on port or listening for a connection");
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ex) {
                System.err.println("Could not close socket");
                ex.printStackTrace();
            }
        }
    }

    private void handleClientInput(String line, BufferedReader in, PrintWriter out) throws IOException {
        if ("no".equalsIgnoreCase(line)) {
            handleRegistration(in, out);
        } else if ("login".equalsIgnoreCase(line) || "no".equalsIgnoreCase(line)){
            String username = handleLogin(in, out);
            if (username != null) {
                socketUsernameMap.put(clientSocket, username);
                server.updateUsernameMap(clientSocket, username);
                out.println("Login successful. Your username is: " + username);
                queryGameStart(in, out, username);
            } else {
                out.println("Login failed. Connection will close.");
            }
        } else if ("QUIT".equalsIgnoreCase(line)) {
            handleQuit();
        } else if ("PLAY_AGAIN".equalsIgnoreCase(line)) {
            handlePlayAgain();
        }
    }

    private void handleRegistration(BufferedReader in, PrintWriter out) throws IOException {
        out.println("Please register. Enter username:");
        String username = in.readLine().trim();
        System.out.println("Received username: " + username);
        out.println("Please register. Enter password:");
        String password = in.readLine().trim();
        out.println("Type login to Login.");
        System.out.println("Received password for username " + username);
        registerUser(username, password);
        System.out.println("User registered: " + username);
    }

    private String handleLogin(BufferedReader in, PrintWriter out) throws IOException {
        out.println("Please login. Enter username:");
        String username = in.readLine().trim();
        System.out.println("Received username: " + username);
        out.println("Please login. Enter password:");
        String password = in.readLine().trim();
        System.out.println("Received password for username " + username);
        if (loginUser(username, password)) {
            return username;
        } else {
            return null;
        }
    }

    private void queryGameStart(BufferedReader in, PrintWriter out, String username) throws IOException {
        out.println("Do you want to play the game? (yes/no)");
        String response = in.readLine().trim();
        if ("yes".equalsIgnoreCase(response)) {
            out.println("Waiting for other players to join...");
            server.confirmRegistration(clientSocket);
        } else {
            out.println("You chose not to play the game.");
        }
    }

    public void startGame() {
        SwingUtilities.invokeLater(() -> {
            BiConsumer<String, Integer> scoreSender = (user, score) -> sendScoreToServer(user, score);
            displayWords = new DisplayWords("words.txt", scoreSender, server.getUsername(clientSocket), clientSocket, server);
            displayWords.setVisible(true);
        });
    }

    private void registerUser(String username, String password) {
        userDatabase.put(username, password);
        System.out.println("Registered user: " + username);
    }

    private boolean loginUser(String username, String password) {
        String storedPassword = userDatabase.get(username);
        boolean loginSuccess = storedPassword != null && storedPassword.equals(password);
        System.out.println("Login " + (loginSuccess ? "successful" : "failed") + " for username: " + username);
        return loginSuccess;
    }

    public void sendScoreToServer(String userName, int correctCount) {
        try {
            System.out.println("Received username from DisplayWords: " + userName);
            System.out.println("Received correctCount from DisplayWords: " + correctCount);
            handleGameCompletion(userName, correctCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleGameCompletion(String userName, int correctCount) {
        scores.put(userName, correctCount);
        System.out.println("Updated scores: " + scores);
        if (scores.size() == server.getGroupSize()) {
            determineWinner();
        }
    }

    private void determineWinner() {
        int maxScore = -1;
        String winner = null;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winner = entry.getKey();
            }
        }
        System.out.println("Winner determined: " + winner + " with score: " + maxScore);
        broadcastWinner(winner, maxScore);
    }

    private void broadcastWinner(String winner, int score) {
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

    private void handleQuit() {
        String username = socketUsernameMap.get(clientSocket);
        if (username != null) {
            scores.put(username, 0); // Mark the score as 0 for quitting client
            server.markAsViewer(clientSocket);
            out.println("You have quit the game. You will remain as a viewer.");
            isViewer = true;
        }
    }

    private void handlePlayAgain() {
        server.markReadyToPlayAgain(clientSocket);
    }

    public void updateWordForViewer(String word, String playerPerformance, String liveScores) {
        if (displayWords != null) {
            displayWords.updateWord(word, playerPerformance, liveScores);
        }
    }
}
