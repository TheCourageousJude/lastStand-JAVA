package laststand.core;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * The game window. Normal "tab" mode is a fixed-size window (1000x800 content); F11 / Alt+Enter
 * toggles real fullscreen, where the game scales to the monitor (see GamePanel's display scaling).
 */
public class GameWindow extends JFrame {
    private final GamePanel panel = new GamePanel();
    private boolean fullscreen = false;
    private Point windowedLocation;

    public GameWindow() {
        setTitle("Last Stand");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(panel);
        pack();
        fitWindowToScreen();
        setLocationRelativeTo(null);
        panel.setFullscreenToggle(this::toggleFullscreen);
        panel.requestFocusInWindow();
    }

    /**
     * The window is taller than it used to be (800 content px). On a short screen (e.g. a 768px
     * laptop) that wouldn't fit, so shrink it to the available height, keeping the aspect ratio --
     * the panel scales its drawing to whatever size it is, so nothing else needs to know.
     */
    private void fitWindowToScreen() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Insets in = getInsets();
        Dimension pref = panel.getPreferredSize();
        double availW = usable.width - in.left - in.right;
        double availH = usable.height - in.top - in.bottom;
        double f = Math.min(1.0, Math.min(availW / pref.width, availH / pref.height));
        if (f < 1.0) {
            panel.setPreferredSize(new Dimension((int) (pref.width * f), (int) (pref.height * f)));
            pack();
        }
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    /** Switches between the normal window and true fullscreen on whichever monitor the window is on. */
    public void toggleFullscreen() {
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        if (!fullscreen) {
            windowedLocation = getLocation();
            dispose();                  // decorations can only change while the window isn't displayable
            setUndecorated(true);
            if (device.isFullScreenSupported()) {
                device.setFullScreenWindow(this);
            } else {                    // some platforms can't do exclusive fullscreen -- cover the screen with a borderless window
                setBounds(device.getDefaultConfiguration().getBounds());
                setVisible(true);
            }
            fullscreen = true;
        } else {
            if (device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setExtendedState(JFrame.NORMAL);
            pack();
            if (windowedLocation != null) setLocation(windowedLocation); else setLocationRelativeTo(null);
            setVisible(true);
            fullscreen = false;
        }
        SwingUtilities.invokeLater(panel::requestFocusInWindow); // the re-shown window starts without focus
    }
}
