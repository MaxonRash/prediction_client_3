package com.client.prediction_client_3;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

@SpringBootApplication
public class PredictionClient3Application {

    public static void main(String[] args) {
        AtomicReference<MainGui> guiRef = new AtomicReference<>();
        SingleInstanceGuard guard = new SingleInstanceGuard();
        boolean isPrimaryInstance = guard.acquire(() -> {
            MainGui gui = guiRef.get();
            if (gui != null) {
                SwingUtilities.invokeLater(gui::bringToFront);
            }
        });
        if (!isPrimaryInstance) {
            SingleInstanceGuard.notifyRunningInstance();
            return;
        }

        // Spring Boot defaults java.awt.headless to true, which would silently block the GUI from opening.
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(PredictionClient3Application.class, args);

        boolean dark = Preferences.userNodeForPackage(PredictionClient3Application.class).getBoolean(MainGui.DARK_MODE_PREF_KEY, false);
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        SwingUtilities.invokeLater(() -> {
            MainGui gui = new MainGui(dark);
            guiRef.set(gui);
            gui.setVisible(true);
        });
    }
}
