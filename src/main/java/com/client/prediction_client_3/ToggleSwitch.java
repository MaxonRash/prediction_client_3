package com.client.prediction_client_3;

import javax.swing.JToggleButton;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class ToggleSwitch extends JToggleButton {

    private static final int WIDTH = 44;
    private static final int HEIGHT = 22;
    private static final Color ON_COLOR = new Color(0x4C, 0xAF, 0x50);
    private static final Color OFF_COLOR = new Color(0x9E, 0x9E, 0x9E);

    private float knobPosition;
    private final Timer animationTimer = new Timer(10, e -> animateStep());

    public ToggleSwitch() {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        addActionListener(e -> animationTimer.start());
    }

    public void setSelectedSilently(boolean selected) {
        setSelected(selected);
        knobPosition = selected ? 1f : 0f;
        repaint();
    }

    private void animateStep() {
        float target = isSelected() ? 1f : 0f;
        float step = 0.2f;
        if (Math.abs(knobPosition - target) <= step) {
            knobPosition = target;
            animationTimer.stop();
        } else {
            knobPosition += Math.signum(target - knobPosition) * step;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g.setColor(blend(OFF_COLOR, ON_COLOR, knobPosition));
        g.fillRoundRect(0, 0, w - 1, h - 1, h, h);

        int knobDiameter = h - 4;
        int knobX = 2 + Math.round(knobPosition * (w - knobDiameter - 4));
        g.setColor(Color.WHITE);
        g.fillOval(knobX, 2, knobDiameter, knobDiameter);

        g.dispose();
    }

    private static Color blend(Color from, Color to, float ratio) {
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio);
        return new Color(r, g, b);
    }
}
