package laststand.world;

import java.awt.Color;
import java.awt.Graphics2D;

public class Portal {
    public enum Dir { NORTH, SOUTH, EAST, WEST }

    public final Dir direction;
    public final double x, y; // world coords, center of the portal
    public boolean active = false;
    private double swirl = 0;

    public Portal(Dir direction, double x, double y) {
        this.direction = direction;
        this.x = x;
        this.y = y;
    }

    public void update() {
        swirl += 0.15;
    }

    public void draw(Graphics2D g, int camX, int camY) {
        int sx = (int) x - camX;
        int sy = (int) y - camY;
        int r = 22;
        g.setColor(active ? new Color(120, 40, 160) : new Color(90, 90, 90));
        g.drawOval(sx - r, sy - r, r * 2, r * 2);
        g.drawOval(sx - r + 5, sy - r + 5, r * 2 - 10, r * 2 - 10);
        // little rotating tick so it reads as a swirling portal
        int tx = (int) (Math.cos(swirl) * r);
        int ty = (int) (Math.sin(swirl) * r);
        g.drawLine(sx, sy, sx + tx, sy + ty);
    }
}
