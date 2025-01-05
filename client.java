import java.io.*;
import java.net.*;

public class client {
    private static final String HOST = "localhost";
    private static final int PORT = 1234;
    private static String userName;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    public static void main(String[] args) {
        try {
            socket = new Socket(HOST, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

            Thread readerThread = new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        System.out.println("Server: " + fromServer);
                        if (fromServer.startsWith("Login successful. Your username is:")) {
                            userName = fromServer.substring(fromServer.lastIndexOf(":") + 2);
                            System.out.println("Logged in as: " + userName);
                        } else if (fromServer.startsWith("WINNER:")) {
                            System.out.println(fromServer);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading from server");
                    e.printStackTrace();
                }
            });

            readerThread.start();

            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                if (userInput.trim().length() > 0) {
                    //System.out.println("Sending to server: " + userInput); debugging statement
                    out.println(userInput);
                }
            }

        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + HOST);
            e.printStackTrace();
        }
    }
}
