package laststand.shop;

import laststand.core.Constants;
import laststand.entity.Player;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Two independent columns -- one per player, each spending their own wallet.
 * "The shop is separate for Player 1 and Player 2. If either of them are
 * inactive, the said player who is inactive cannot be interacted within the
 * shop." -- hence the wider panel when P2 is active, and a single centered
 * column when playing solo.
 */
public class ShopUI {

    public void draw(Graphics2D g, Player p1, Wallet w1, List<ShopItem> p1Items, int p1Index,
                      Player p2, Wallet w2, List<ShopItem> p2Items, int p2Index, boolean p2Active) {

        int colW = 380;
        int panelW = p2Active ? (colW * 2 + 30) : colW + 20;
        int panelH = 640; // taller to fit the perk row's now-2-line entries
        int px = (Constants.SCREEN_WIDTH - panelW) / 2;
        int py = (Constants.SCREEN_HEIGHT - panelH) / 2;

        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT);

        g.setColor(new Color(35, 30, 45));
        g.fillRoundRect(px, py, panelW, panelH, 16, 16);
        g.setColor(Color.WHITE);
        g.drawRoundRect(px, py, panelW, panelH, 16, 16);

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString("SHOP", px + 20, py + 36);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Each player spends only their own orbs", px + 20, py + 56);

        drawColumn(g, px + 10, py + 75, colW - 20, "Player 1 (" + p1.playerClass.name() + ")",
                Player.colorFor(1), p1, w1, p1Items, p1Index);

        if (p2Active) {
            drawColumn(g, px + colW + 20, py + 75, colW - 20, "Player 2 (" + p2.playerClass.name() + ")",
                    Player.colorFor(2), p2, w2, p2Items, p2Index);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.GRAY);
        String hintLine1 = p2Active
                ? "P1: W/S+SPACE for the shop list, 1-4 for upgrades"
                : "W/S+SPACE for the shop list, 1-4 for upgrades";
        String hintLine2 = p2Active
                ? "P2: Up/Down+ENTER for the shop list, 0/9/8/7 for upgrades   |   E to close"
                : "E to close";
        g.drawString(hintLine1, px + 20, py + panelH - 30);
        g.drawString(hintLine2, px + 20, py + panelH - 14);
    }

    private void drawColumn(Graphics2D g, int x, int y, int w, String title, Color accent,
                             Player player, Wallet wallet, List<ShopItem> items, int selectedIndex) {
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(accent);
        g.drawString(title, x, y);

        drawOrb(g, x, y + 24, new Color(80, 70, 130), wallet.darkOrbs, "Dark");
        drawOrb(g, x + 95, y + 24, new Color(190, 190, 200), wallet.silverOrbs, "Silver");
        drawOrb(g, x + 200, y + 24, new Color(235, 210, 60), wallet.yellowOrbs, "Yellow");

        int rowH = 30;
        int listY = y + 46;
        for (int i = 0; i < items.size(); i++) {
            ShopItem item = items.get(i);
            int rowY = listY + i * rowH;
            boolean selected = i == selectedIndex;

            if (selected) {
                g.setColor(new Color(70, 65, 90));
                g.fillRoundRect(x - 6, rowY - 16, w, rowH - 4, 8, 8);
            }
            g.setColor(!item.actionable ? Color.GRAY : (selected ? Color.YELLOW : Color.WHITE));
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString((selected ? "> " : "  ") + item.label, x, rowY);

            if (!item.costLabel.isEmpty()) {
                g.setColor(Color.LIGHT_GRAY);
                int cw = g.getFontMetrics().stringWidth(item.costLabel);
                g.drawString(item.costLabel, x + w - cw - 10, rowY);
            }
        }

        int perksY = listY + items.size() * rowH + 14;
        drawPerksRow(g, x, perksY, player);
    }

    /**
     * "3 random buffs at random chosen, 1 option to REROLL" -- direct one-key buy/reroll,
     * same slot-key convention as the inventory hotbar (P1: 1,2,3,4 / P2: 0,9,8,7).
     * Two lines per entry (name+level, then effect) since the effect text runs long,
     * especially on the P2 side where a single long line would crowd the edge.
     * Returns the total height used, so the armor row below can follow it.
     */
    private int drawPerksRow(Graphics2D g, int x, int y, Player p) {
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Upgrades -- 1 Yellow Orb each", x, y);

        String[] keys = p.playerNumber == 1 ? new String[]{"1", "2", "3", "4"} : new String[]{"0", "9", "8", "7"};

        int lineH = 15;
        int entryH = lineH * 2 + 5;
        int rowY = y + 20;

        for (int i = 0; i < p.offeredPerks.size(); i++) {
            UpgradeType type = p.offeredPerks.get(i);
            int lvl = p.upgradeLevel(type);
            boolean maxed = lvl >= UpgradeType.MAX_LEVEL;
            int entryY = rowY + i * entryH;

            g.setColor(maxed ? Color.GRAY : type.color);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("[" + keys[i] + "] " + type.displayName() + " Lv" + lvl + "/" + UpgradeType.MAX_LEVEL
                    + (maxed ? " (MAX)" : ""), x, entryY);

            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("      " + type.effectFor(p.playerClass), x, entryY + lineH);
        }

        int rerollY = rowY + p.offeredPerks.size() * entryH + 6;
        boolean canReroll = p.level >= Player.REROLL_LEVEL_REQUIREMENT;
        g.setColor(canReroll ? new Color(120, 200, 230) : Color.GRAY);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("[" + keys[3] + "] Reroll these upgrades", x, rerollY);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String rerollDetail = canReroll
                ? "      costs " + Player.REROLL_LEVEL_REQUIREMENT + " LVL (currently LVL " + p.level + ")"
                : "      requires LVL " + Player.REROLL_LEVEL_REQUIREMENT + " (currently LVL " + p.level + ")";
        g.drawString(rerollDetail, x, rerollY + lineH);

        return (rerollY + lineH) - y;
    }

    /** Small filled-circle currency readout (Dark/Silver/Yellow orb count) shown at the top of
     *  each player's column. */
    private void drawOrb(Graphics2D g, int x, int y, Color color, int count, String label) {
        g.setColor(color);
        g.fillOval(x, y - 10, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(count + " " + label, x + 14, y);
    }
}
