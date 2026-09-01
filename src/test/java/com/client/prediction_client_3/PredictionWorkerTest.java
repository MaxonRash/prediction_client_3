package com.client.prediction_client_3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PredictionWorkerTest {

    @Test
    void extractsGameStartTimeAfterPlayerTurnOne(@TempDir Path tempDir) throws IOException {
        Path logsDir = Files.createDirectories(tempDir.resolve("Logs"));
        Files.write(logsDir.resolve("hdt_log.txt"), List.of(
                "12:00:00|Line before",
                "12:00:01|--- Game start ---",
                "12:00:05|--- Player turn 1 ---"
        ));

        String result = PredictionWorker.getExistingStartAfterTurn1Time(tempDir);

        assertEquals("12:00:01", result);
    }

    @Test
    void returnsNullWhenNoGameHasStartedYet(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("Logs"));
        // no hdt_log.txt written yet

        String result = PredictionWorker.getExistingStartAfterTurn1Time(tempDir);

        assertNull(result);
    }

    @Test
    void findsPlacementForMatchingStartTime(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("BgsLastGames.xml"), List.of(
                "<Game Player=\"me\" StartTime=\"2024-01-01 12:00:01\" Placemenent=\"3\" />"
        ));

        String result = PredictionWorker.placementOfThisGame(tempDir, "12:00:01");

        assertEquals("3", result);
    }

    @Test
    void returnsNullWhenNoPlacementRecordedYet(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("BgsLastGames.xml"), List.of(
                "<Game Player=\"me\" StartTime=\"2024-01-01 11:00:00\" Placemenent=\"1\" />"
        ));

        String result = PredictionWorker.placementOfThisGame(tempDir, "12:00:01");

        assertNull(result);
    }
}
