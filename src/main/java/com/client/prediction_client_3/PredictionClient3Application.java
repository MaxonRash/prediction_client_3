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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@SpringBootApplication

public class PredictionClient3Application {
    public static Path PathOfDeckTracker = PathOfDeckTracker();

    public static void main(String[] args) throws IOException, InterruptedException {
        SpringApplication.run(PredictionClient3Application.class, args);

        org.apache.http.client.CredentialsProvider provider = new BasicCredentialsProvider();
        try {
            if (PathOfDeckTracker == null) {
                throw new FileNotFoundException("DeckTracker directory not found");
            }
        } catch (FileNotFoundException e) {
            for (;;) {
                System.err.println(e.getMessage() + ". Ctrl+C to exit.");
                Thread.sleep(3000);
            }
        }
        UsernamePasswordCredentials usernamePasswordCredentials = new UsernamePasswordCredentials(com.client.prediction_client_3.CredentialsProvider.USER,
                com.client.prediction_client_3.CredentialsProvider.PASSWORD);
        provider.setCredentials(AuthScope.ANY, usernamePasswordCredentials);
        String makePredictionRequestString = "http://maxonbot.ru/rest_predictions?makeNewPrediction=true&time=";
        String winPredictionRequestString = "http://maxonbot.ru/rest_predictions?winPrediction=true&time=";
        String losePredictionRequestString = "http://maxonbot.ru/rest_predictions?losePrediction=true&time=";
        String alreadySentStartAfterTurn1Time = null; //
        if (getExistingStartAfterTurn1Time() != null) {
            alreadySentStartAfterTurn1Time = getExistingStartAfterTurn1Time();
        }

        for (; ; ) {
            CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
            for (; ; ) {
                String existingStartAfterTurn1Time = getExistingStartAfterTurn1Time(); //
                if (existingStartAfterTurn1Time != null) { //
                    if (alreadySentStartAfterTurn1Time == null) { //

                        try {
//                                System.out.println(alreadySentStartAfterTurn1Time);
                            CloseableHttpResponse response = httpClient.execute(new HttpGet(makePredictionRequestString + existingStartAfterTurn1Time));
                            if (response.getStatusLine().getStatusCode() == 409) {
                                System.err.println("Unable to start new prediction, this prediction was already executed earlier");
                                response.close();
                            }
                            else if (response.getStatusLine().getStatusCode() == 200) {
                                System.out.println("Sending request to server: NEW PREDICTION START REQUEST!!! TIME: " + existingStartAfterTurn1Time);
                                alreadySentStartAfterTurn1Time = existingStartAfterTurn1Time;
                                System.out.println("Response from server: " + EntityUtils.toString(response.getEntity()));
                                response.close();
                                break; //////////////////////
                            }

                        }
                        catch (HttpHostConnectException e) {
                            for(;;) {
                                System.err.println("Unable to connect to MaxonBot.ru : server is offline or rebooting. Trying again. Ctrl+C - to stop");
                                Thread.sleep(3000);
                            }
                        }
                    }
                    if (existingStartAfterTurn1Time.equals(alreadySentStartAfterTurn1Time)) {
                        System.out.println("No new game is found");
//                            placementOfThisGame("20:52:25");
                    }
                    else {
                        try {
//                                System.out.println("Before HttpResponse: " + makePredictionRequestString + existingStartAfterTurn1Time);
                            CloseableHttpResponse response = httpClient.execute(new HttpGet(makePredictionRequestString + existingStartAfterTurn1Time));
//                                System.out.println("After HttpResponse: " + response.getStatusLine().getStatusCode());
                            if (response.getStatusLine().getStatusCode() == 409) {
                                System.err.println("Unable to start new prediction, this prediction was already executed earlier");
                                response.close();
                            }
                            else if (response.getStatusLine().getStatusCode() == 200) {
                                System.out.println("Sending request to server: NEW PREDICTION START REQUEST!!! TIME: " + existingStartAfterTurn1Time);
                                alreadySentStartAfterTurn1Time = existingStartAfterTurn1Time;
                                System.out.println("Response from server: " + EntityUtils.toString(response.getEntity()));
                                response.close();
                                break; //////////////////////////
                            }

                        }
                        catch (HttpHostConnectException e) {
                            for (;;) {
                                System.err.println("Unable to connect to MaxonBot.ru : server is offline or rebooting. Trying again. Ctrl+C - to stop");
                                Thread.sleep(3000);
                            }
                        }
                    }


                }
                else {
                    System.out.println("Log file is Empty or Game was not started yet");
                }
                Thread.sleep(3000);
            }

            for (;;) {
//                String time = "20:53:25";
                String time = alreadySentStartAfterTurn1Time;
                String finalPlacementString = placementOfThisGame(time);
//                System.out.println(finalPlacementString);
                if (finalPlacementString != null) {
                    int finalPlacement = Integer.parseInt(finalPlacementString);
                    if (finalPlacement < 5) {
                        CloseableHttpResponse response = httpClient.execute(new HttpGet(winPredictionRequestString + time));
                        System.out.println("Sending request to server: Prediction outcome: WIN");
                        System.out.println("Response from server: " + EntityUtils.toString(response.getEntity()));
                        break;
                    }
                    else {
                        CloseableHttpResponse response = httpClient.execute(new HttpGet(losePredictionRequestString + time));
                        System.out.println("Sending request to server: Prediction outcome: LOSE");
                        System.out.println("Response from server: " + EntityUtils.toString(response.getEntity()));
                        break;
                    }

                }
                Thread.sleep(3000);

            }

        }
    }

    public static Path PathOfDeckTracker() {
        Path path = Paths.get(System.getProperty("user.home") + "\\AppData\\Roaming\\HearthstoneDeckTracker");
        if (Files.exists(path)) {
            return path;
        }
        else return null;
    }

    public static String placementOfThisGame(String time) {
        Path path = null;
        if(PathOfDeckTracker != null) {
            path = Paths.get(PathOfDeckTracker + "\\BgsLastGames.xml");
        }
        if (path != null) {
            try {
                List<String> allLines = Files.readAllLines(path);
                if (!allLines.isEmpty()) {
                    Collections.reverse(allLines);
                    for (String line : allLines) {
                        if (line.contains("<Game Player=")) {
                            int startTimeIndex = line.indexOf("StartTime=");
                            String startTime = line.substring(startTimeIndex + 22,  startTimeIndex + 30);
//                            System.out.println("Start time is: " + startTime);
                            if (startTime.equals(time)) {
                                int placementIndex = line.indexOf("Placemenent=");
                                String placement = line.substring(placementIndex + 13, placementIndex + 14);
//                                System.out.println("placementOfThisGame returns: " + placement);
                                return placement;
                            }
                            else {
                                System.out.println("Game started at: " + time + " | No placement found yet. Game is still running.");
                            }
                            return null; //break;
                        }
                    }
                }
                System.out.println("No last games found in BgsLastGames.xml");
            } catch (IOException e) {
                System.err.println("Unable to find BgsLastGames.xml    Ctrl+C to exit");
            }
        }
        return null;
    }

    public static String getExistingStartAfterTurn1Time() {
        Path path = null;
        if(PathOfDeckTracker != null) {
            path = Paths.get(PathOfDeckTracker + "\\Logs\\hdt_log.txt");
        }
        if (path != null) {
            try {
                List<String> allLines = Files.readAllLines(path);
                if (!allLines.isEmpty()) {
                    Collections.reverse(allLines);
                    for (int i = 0; i < allLines.size(); i++) {
                        if (allLines.get(i).contains("--- Player turn 1 ---")) {
                            int x = i;
                            for (; x < allLines.size(); x++) {
                                if (allLines.get(x).contains("--- Game start ---")) {
                                    String newLine = allLines.get(x);
                                    if (newLine.startsWith("0")) {
                                        newLine = "0" + newLine;
                                    }
//                                    System.out.println("getExistingStartAfterTurn1Time returns: " + newLine.substring(0, 8));
                                    return newLine.substring(0, 8);
                                }
                            }
                        }
                    }
                }
                return null;
            } catch (IOException e) {
                for (;;) {
                    System.err.println("Unable to find hdt.log    Ctrl+C to exit");
                }
            }
        }
        return null;
    }
}

