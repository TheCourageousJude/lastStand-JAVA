package laststand.fx;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class DamagePopup {

    public static final long NORMAL_DURATION_MS = 500;
    public static final long CRIT_DURATION_MS = 1500; // "fades by 1.5sec only"

    private static final int NORMAL_FONT_SIZE = 15;
    private static final int CRIT_FONT_SIZE = 30; // "LARGERRR"
    private static final int RISE_PX = 22; // drifts upward as it fades

    private static final Color CRIT_YELLOW = new Color(255, 225, 60);
    private static final Color CRIT_ORANGE = new Color(255, 130, 20);

    public final double x, y;
    public final String text;
    public final Color color;
    public final long spawnTime;
    public final boolean crit;

    public DamagePopup(double x, double y, String text, Color color, long spawnTime, boolean crit) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.spawnTime = spawnTime;
        this.crit = crit;
    }

    private long duration() {
        return crit ? CRIT_DURATION_MS : NORMAL_DURATION_MS;
    }

    public boolean isExpired(long now) {
        return now - spawnTime >= duration();
    }

    public void draw(Graphics2D g, int camX, int camY, long now) {
        long duration = duration();
        double t = Math.min(1.0, (now - spawnTime) / (double) duration);
        int alpha = (int) Math.round(255 * (1 - t));
        if (alpha <= 0) return;

        int sx = (int) x - camX;
        int sy = (int) (y - t * RISE_PX) - camY;

        Color drawColor;
        int fontSize;
        if (crit) {
            // "FLASHIERRR" -- fast yellow<->orange strobe for the whole life of the popup
            double phase = 0.5 + 0.5 * Math.sin((now - spawnTime) / 60.0);
            drawColor = lerp(CRIT_YELLOW, CRIT_ORANGE, phase);
            fontSize = CRIT_FONT_SIZE;
        } else {
            drawColor = color;
            fontSize = NORMAL_FONT_SIZE;
        }

        Color faded = new Color(drawColor.getRed(), drawColor.getGreen(), drawColor.getBlue(), alpha);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        g.setColor(new Color(0, 0, 0, alpha)); // slight shadow for legibility over any background
        g.drawString(text, sx + 2, sy + 2);
        g.setColor(faded);
        g.drawString(text, sx, sy);
    }

    private static Color lerp(Color a, Color b, double t) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int gr = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, gr, bl);
    }
}
