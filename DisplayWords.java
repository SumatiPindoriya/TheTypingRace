import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class DisplayWords extends JFrame {
    private JLabel wordLabel;
    private JLabel timerLabel;
    private JLabel liveScoresLabel;
    private JTextField inputField;
    private BufferedReader reader;
    private List<String> words;
    private List<String> incorrectWords;
    private int currentIndex = 0;
    private int correctCount = 0;
    private Timer countdownTimer;
    private int remainingTime = 30;
    private long startTime;
    private List<Long> typingTimes;
    private boolean gameCompleted = false;
    private boolean isViewer = false;
    private BiConsumer<String, Integer> scoreSender;
    private String userName;
    private Socket socket;
    private server serverInstance;
    private PrintWriter out;
    private JButton playAgainButton;

    public DisplayWords(String filePath, BiConsumer<String, Integer> scoreSender, String userName, Socket socket, server serverInstance) {
        this.scoreSender = scoreSender;
        this.userName = userName;
        this.socket = socket;
        this.serverInstance = serverInstance;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        setTitle("Word Display");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        wordLabel = new JLabel("", SwingConstants.CENTER);
        wordLabel.setFont(new Font("Serif", Font.PLAIN, 24));
        add(wordLabel, BorderLayout.CENTER);

        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Serif", Font.PLAIN, 18));
        add(timerLabel, BorderLayout.NORTH);

        liveScoresLabel = new JLabel("", SwingConstants.CENTER);
        liveScoresLabel.setFont(new Font("Serif", Font.PLAIN, 16));
        add(liveScoresLabel, BorderLayout.SOUTH);

        inputField = new JTextField();
        inputField.setFont(new Font("Serif", Font.PLAIN, 24));
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkInput();
            }
        });
        add(inputField, BorderLayout.SOUTH);

        JButton quitButton = new JButton("Quit");
        quitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleQuit();
                quitButton.setVisible(false); // Hide the quit button after it's clicked
            }
        });

        JButton leaveServerButton = new JButton("Leave Server");
        leaveServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnectFromServer();
            }
        });

        playAgainButton = new JButton("Play Again");
        playAgainButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handlePlayAgain();
            }
        });
        playAgainButton.setVisible(false); // Initially hidden

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(quitButton);
        buttonPanel.add(leaveServerButton);
        buttonPanel.add(playAgainButton); // Add play-again button
        add(buttonPanel, BorderLayout.EAST);

        words = new ArrayList<>();
        incorrectWords = new ArrayList<>();
        typingTimes = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                words.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        displayNextWord();
    }

    private void disconnectFromServer() {
        System.exit(0);
        JOptionPane.showMessageDialog(this, "Disconnected from the server.");
    }

    private void handleQuit() {
        isViewer = true;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        inputField.setEnabled(false);
        sendScoreToServer(); // Ensure score is sent before quitting
        out.println("QUIT");
    }

    private void handlePlayAgain() {
        out.println("PLAY_AGAIN");
        playAgainButton.setVisible(false); // Hide the play-again button after it's clicked
    }

    private void checkInput() {
        if (isViewer) {
            return;
        }

        long endTime = System.currentTimeMillis();
        typingTimes.add(endTime - startTime);

        String input = inputField.getText();
        if (currentIndex < words.size() && input.equalsIgnoreCase(words.get(currentIndex))) {
            correctCount++;
        } else {
            if (currentIndex < words.size()) {
                incorrectWords.add(words.get(currentIndex));
            }
            JOptionPane.showMessageDialog(this, "Incorrect or Timeout! Try the next word.");
        }
        inputField.setText("");
        currentIndex++;
        if (currentIndex < words.size()) {
            displayNextWord();
        } else {
            showResult();
        }
    }

    private void displayNextWord() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }

        if (currentIndex < words.size()) {
            wordLabel.setText(words.get(currentIndex));
            remainingTime = currentIndex < 25 ? 30 - currentIndex : 6;
            timerLabel.setText("Time remaining: " + remainingTime + " seconds");
            startTime = System.currentTimeMillis();

            countdownTimer = new Timer(1000, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    remainingTime--;
                    timerLabel.setText("Time remaining: " + remainingTime + " seconds");
                    if (remainingTime <= 0) {
                        countdownTimer.stop();
                        checkInput();
                    }
                }
            });
            countdownTimer.start();
        }
    }

    private void showResult() {
        double averageTime = typingTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        JOptionPane.showMessageDialog(this, "<html>You typed " + correctCount + " words correctly.<br> Incorrect words: " + incorrectWords +
                ".<br> Average typing speed: " + String.format("%.2f", averageTime / 1000) + " seconds per word.</html>");
        gameCompleted = true;
        sendScoreToServer();
        playAgainButton.setVisible(true); // Show the play-again button after the game ends
    }

    private void sendScoreToServer() {
        scoreSender.accept(userName, correctCount);
    }

    public void updateWord(String word, String playerPerformance, String liveScores) {
        SwingUtilities.invokeLater(() -> {
            wordLabel.setText(word);
            timerLabel.setText(playerPerformance);
            liveScoresLabel.setText(liveScores);
        });
    }

    public int getScore() {
        return correctCount;
    }

    public boolean isGameCompleted() {
        return gameCompleted;
    }
}
