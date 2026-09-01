package com.client.prediction_client_3;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

public class MainGui extends JFrame {

    static final String DARK_MODE_PREF_KEY = "darkMode";

    private final JTextArea logArea = new JTextArea();
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton restartButton = new JButton("Restart");
    private final JButton testConnectionButton = new JButton("Test Connection");
    private final ToggleSwitch themeToggle = new ToggleSwitch();
    private final JLabel statusLabel = new JLabel("Stopped");

    private PredictionWorker worker;
    private TrayIcon trayIcon;

    public MainGui(boolean darkMode) {
        super("Hearthstone Prediction Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImage(createIcon());
        themeToggle.setSelectedSilently(darkMode);

        add(buildLogPanel(), BorderLayout.CENTER);
        add(buildControlsPanel(), BorderLayout.SOUTH);

        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> onStop());
        restartButton.addActionListener(e -> onRestart());
        testConnectionButton.addActionListener(e -> onTestConnection());
        themeToggle.addActionListener(e -> onToggleTheme());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClose();
            }
        });

        setupTrayIcon();
        updateRunningState(false);

        setSize(560, 420);
        setLocationRelativeTo(null);
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Activity log"));
        return scrollPane;
    }

    private JPanel buildControlsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(restartButton);
        buttons.add(testConnectionButton);
        panel.add(buttons, BorderLayout.WEST);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 5));
        rightSide.add(new JLabel("Light"));
        rightSide.add(themeToggle);
        rightSide.add(new JLabel("Dark"));
        rightSide.add(Box.createHorizontalStrut(12));
        rightSide.add(statusLabel);
        panel.add(rightSide, BorderLayout.EAST);
        return panel;
    }

    private void onToggleTheme() {
        boolean dark = themeToggle.isSelected();
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            log("Failed to switch theme: " + e.getMessage());
            return;
        }
        FlatLaf.updateUI();
        Preferences.userNodeForPackage(MainGui.class).putBoolean(DARK_MODE_PREF_KEY, dark);
    }

    private void onStart() {
        worker = new PredictionWorker(this::log, this::updateRunningState);
        worker.start();
    }

    private void onStop() {
        if (worker != null) {
            worker.stop();
        }
    }

    private void onRestart() {
        if (worker == null) {
            onStart();
        } else {
            log("Restarting...");
            worker.restart();
        }
    }

    private void onTestConnection() {
        testConnectionButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return testConnection();
            }

            @Override
            protected void done() {
                try {
                    log(get());
                } catch (Exception e) {
                    log("Test connection failed: " + e.getMessage());
                } finally {
                    testConnectionButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private String testConnection() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .build();
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(CredentialsProvider.USER, CredentialsProvider.PASSWORD));
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet("http://maxonbot.ru/rest_predictions"))) {
                int status = response.getStatusLine().getStatusCode();
                if (status == 401 || status == 403) {
                    return "Test connection: server reachable, but credentials were rejected (HTTP " + status + ")";
                }
                return "Test connection: server reachable (HTTP " + status + ")";
            }
        } catch (IOException e) {
            return "Test connection: unable to reach server (" + e.getMessage() + ")";
        }
    }

    private void updateRunningState(boolean running) {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(!running);
            stopButton.setEnabled(running);
            statusLabel.setText(running ? "Running" : "Stopped");
            if (trayIcon != null) {
                trayIcon.setToolTip("Hearthstone Prediction Client - " + (running ? "Running" : "Stopped"));
            }
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            return;
        }
        PopupMenu popup = new PopupMenu();

        MenuItem showItem = new MenuItem("Show");
        showItem.addActionListener(e -> restoreWindow());

        MenuItem startItem = new MenuItem("Start");
        startItem.addActionListener(e -> onStart());

        MenuItem stopItem = new MenuItem("Stop");
        stopItem.addActionListener(e -> onStop());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> exitApp());

        popup.add(showItem);
        popup.addSeparator();
        popup.add(startItem);
        popup.add(stopItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(createIcon(), "Hearthstone Prediction Client", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> restoreWindow());

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
        }
    }

    private void restoreWindow() {
        bringToFront();
    }

    /** Un-minimizes and raises the window - called when a second launch attempt signals in. */
    public void bringToFront() {
        setVisible(true);
        setState(Frame.NORMAL);
        toFront();
        requestFocus();
    }

    private void onWindowClose() {
        if (trayIcon != null) {
            setVisible(false);
        } else {
            exitApp();
        }
    }

    private void exitApp() {
        if (worker != null) {
            worker.stop();
        }
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        dispose();
        System.exit(0);
    }

    private static Image createIcon() {
        int size = 32;
        int big = 512;
        Color bg = new Color(0x2E, 0x86, 0xDE);

        BufferedImage large = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = large.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float arc = big * 0.28f;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Float(0, 0, big, big, arc, arc));

        // Glyph outline + bounds-based centering (not drawString/FontMetrics baseline
        // placement) lines up thin strokes like the H crossbar solidly against the
        // downsample grid; the slight synthetic-bold stroke adds a safety margin.
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(big * 0.47f));
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector glyphVector = font.createGlyphVector(frc, "HPC");
        Shape outline = glyphVector.getOutline();
        Rectangle2D bounds = outline.getBounds2D();
        AffineTransform transform = AffineTransform.getTranslateInstance(
                (big - bounds.getWidth()) / 2 - bounds.getX(),
                (big - bounds.getHeight()) / 2 - bounds.getY());
        Shape text = transform.createTransformedShape(outline);

        g.setColor(Color.WHITE);
        g.fill(text);
        g.setStroke(new BasicStroke(big * 0.015f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(text);
        g.dispose();

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = image.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gs.drawImage(large, 0, 0, size, size, null);
        gs.dispose();

        // Small strokes (e.g. the H crossbar) end up made almost entirely of
        // antialiased edge pixels, which blend white into the opaque blue
        // background instead of staying white. Threshold to pure white/blue
        // to remove that washed-out blending (see tools/IconGenerator.java).
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 128) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int gr = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int distWhite = (255 - r) * (255 - r) + (255 - gr) * (255 - gr) + (255 - b) * (255 - b);
                int distBg = (bg.getRed() - r) * (bg.getRed() - r) + (bg.getGreen() - gr) * (bg.getGreen() - gr) + (bg.getBlue() - b) * (bg.getBlue() - b);
                out.setRGB(x, y, distWhite <= distBg ? 0xFFFFFFFF : bg.getRGB());
            }
        }
        return out;
    }
}
