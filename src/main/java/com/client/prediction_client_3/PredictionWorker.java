package com.client.prediction_client_3;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Background worker that polls HearthstoneDeckTracker's log files and reports
 * game start/outcome predictions to the server. Runs on its own thread so the
 * GUI can start/stop/restart it without killing the whole process.
 */
public class PredictionWorker {

    private static final String BASE_URL = "http://maxonbot.ru/rest_predictions";

    private final Consumer<String> log;
    private final Consumer<Boolean> onRunningChanged;

    private volatile boolean running;
    private Thread thread;
    private CloseableHttpClient httpClient;

    public PredictionWorker(Consumer<String> log, Consumer<Boolean> onRunningChanged) {
        this.log = log;
        this.onRunningChanged = onRunningChanged;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        onRunningChanged.accept(true);
        thread = new Thread(this::runLoop, "prediction-worker");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        closeHttpClient();
        onRunningChanged.accept(false);
    }

    public void restart() {
        stop();
        try {
            if (thread != null) {
                thread.join(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        start();
    }

    private void closeHttpClient() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException ignored) {
            }
            httpClient = null;
        }
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void runLoop() {
        Path deckTrackerPath = findDeckTrackerPath();
        if (deckTrackerPath == null) {
            log.accept("DeckTracker directory not found. Install HearthstoneDeckTracker and try again.");
            running = false;
            onRunningChanged.accept(false);
            return;
        }

        org.apache.http.client.CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(CredentialsProvider.USER, CredentialsProvider.PASSWORD));
        httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider).build();

        String alreadySent = getExistingStartAfterTurn1Time(deckTrackerPath);
        log.accept("Watching for games...");

        while (running) {
            String startedTime = waitForNewGame(deckTrackerPath, alreadySent);
            if (!running || startedTime == null) {
                break;
            }
            alreadySent = startedTime;

            log.accept("New game detected at " + startedTime);
            if (!sendPredictionStart(startedTime)) {
                break;
            }

            log.accept("Waiting for the game to finish...");
            String placement = waitForPlacement(deckTrackerPath, startedTime);
            if (!running || placement == null) {
                break;
            }

            reportOutcome(startedTime, placement);
        }

        closeHttpClient();
    }

    private String waitForNewGame(Path deckTrackerPath, String alreadySent) {
        while (running) {
            String existing = getExistingStartAfterTurn1Time(deckTrackerPath);
            if (existing != null && !existing.equals(alreadySent)) {
                return existing;
            }
            if (!sleep(3000)) {
                return null;
            }
        }
        return null;
    }

    private boolean sendPredictionStart(String time) {
        while (running) {
            try (CloseableHttpResponse response = httpClient.execute(new HttpGet(BASE_URL + "?makeNewPrediction=true&time=" + time))) {
                int status = response.getStatusLine().getStatusCode();
                if (status == 409) {
                    log.accept("Prediction for this game was already sent earlier");
                    return true;
                } else if (status == 200) {
                    log.accept("Sent NEW PREDICTION START request. Response: " + EntityUtils.toString(response.getEntity()));
                    return true;
                } else {
                    log.accept("Unexpected response starting prediction: HTTP " + status);
                    return true;
                }
            } catch (HttpHostConnectException e) {
                log.accept("Unable to connect to MaxonBot.ru: server offline or rebooting, retrying...");
                if (!sleep(3000)) {
                    return false;
                }
            } catch (IOException e) {
                log.accept("Error sending prediction start: " + e.getMessage());
                if (!sleep(3000)) {
                    return false;
                }
            }
        }
        return false;
    }

    private String waitForPlacement(Path deckTrackerPath, String time) {
        while (running) {
            String placement = placementOfThisGame(deckTrackerPath, time);
            if (placement != null) {
                return placement;
            }
            if (!sleep(3000)) {
                return null;
            }
        }
        return null;
    }

    private void reportOutcome(String time, String placementString) {
        int placement = Integer.parseInt(placementString);
        boolean win = placement < 5;
        String query = win ? "winPrediction=true" : "losePrediction=true";
        String outcome = win ? "WIN" : "LOSE";
        while (running) {
            try (CloseableHttpResponse response = httpClient.execute(new HttpGet(BASE_URL + "?" + query + "&time=" + time))) {
                log.accept("Sent prediction outcome: " + outcome + ". Response: " + EntityUtils.toString(response.getEntity()));
                return;
            } catch (HttpHostConnectException e) {
                log.accept("Unable to connect to MaxonBot.ru: server offline or rebooting, retrying...");
                if (!sleep(3000)) {
                    return;
                }
            } catch (IOException e) {
                log.accept("Error sending prediction outcome: " + e.getMessage());
                if (!sleep(3000)) {
                    return;
                }
            }
        }
    }

    public static Path findDeckTrackerPath() {
        Path path = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "HearthstoneDeckTracker");
        return Files.exists(path) ? path : null;
    }

    static String placementOfThisGame(Path deckTrackerPath, String time) {
        Path path = deckTrackerPath.resolve("BgsLastGames.xml");
        try {
            List<String> allLines = Files.readAllLines(path);
            if (allLines.isEmpty()) {
                return null;
            }
            Collections.reverse(allLines);
            for (String line : allLines) {
                if (line.contains("<Game Player=")) {
                    int startTimeIndex = line.indexOf("StartTime=");
                    String startTime = line.substring(startTimeIndex + 22, startTimeIndex + 30);
                    if (startTime.equals(time)) {
                        int placementIndex = line.indexOf("Placemenent=");
                        return line.substring(placementIndex + 13, placementIndex + 14);
                    }
                    return null;
                }
            }
        } catch (IOException ignored) {
            // BgsLastGames.xml not written yet / locked - treated as "not ready"
        }
        return null;
    }

    static String getExistingStartAfterTurn1Time(Path deckTrackerPath) {
        Path path = deckTrackerPath.resolve("Logs").resolve("hdt_log.txt");
        try {
            List<String> allLines = Files.readAllLines(path);
            if (allLines.isEmpty()) {
                return null;
            }
            Collections.reverse(allLines);
            for (int i = 0; i < allLines.size(); i++) {
                if (allLines.get(i).contains("--- Player turn 1 ---")) {
                    for (int x = i; x < allLines.size(); x++) {
                        if (allLines.get(x).contains("--- Game start ---")) {
                            String newLine = allLines.get(x);
                            if (newLine.startsWith("0")) {
                                newLine = "0" + newLine;
                            }
                            int indexOfVerticalLineSymbol = newLine.indexOf("|");
                            if (indexOfVerticalLineSymbol == 7) {
                                newLine = "0" + newLine;
                            }
                            return newLine.substring(0, 8);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // hdt_log.txt not available yet - treated as "not ready"
        }
        return null;
    }
}
