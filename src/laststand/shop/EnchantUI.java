package laststand.shop;

import laststand.entity.Player;
import laststand.entity.PlayerClass;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;

public class EnchantUI {

    public void draw(Graphics2D g, Player p, int screenWidth, int screenHeight, long now) {
        int panelW = 700, panelH = 560;
        int px = (screenWidth - panelW) / 2;
        int py = (screenHeight - panelH) / 2;
        boolean spinning = p.isSpinning(now);

        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, screenWidth, screenHeight);

        g.setColor(new Color(30, 32, 45));
        g.fillRoundRect(px, py, panelW, panelH, 16, 16);
        g.setColor(Color.WHITE);
        g.drawRoundRect(px, py, panelW, panelH, 16, 16);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Press \"E\" to exit", px + 16, py + 22);

        // Current LVL box (the spin currency now), top-right
        g.setColor(new Color(30, 130, 70));
        g.fillRoundRect(px + panelW - 210, py + 8, 194, 28, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Your LVL: " + p.level + "  (EXP " + p.exp + "/" + Player.EXP_CAP + ")", px + panelW - 200, py + 27);

        // Big "what's selected" label -- the piece missing from the last pass
        Color accent = Player.colorFor(p.playerNumber);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(accent);
        String selLabel = "P" + p.playerNumber + " enchanting: " + categoryLabel(p.enchantCategory);
        g.drawString(selLabel, px + 16, py + 58);

        int catY = py + 82;
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int catX = px + 16;
        EnchantCategory mainWeapon = p.playerClass == PlayerClass.TANK ? EnchantCategory.SWORD : EnchantCategory.BOW;
        String mainLabel = (p.playerClass == PlayerClass.TANK ? "Sword" : "Bow") + " (Main)";
        catX = drawCategoryChip(g, catX, catY, mainLabel, mainWeapon, p, true);
        catX = drawCategoryChip(g, catX, catY, "[Z/,] Helmet", EnchantCategory.HELMET, p, true);
        catX = drawCategoryChip(g, catX, catY, "[X/.] Chest", EnchantCategory.CHESTPLATE, p, true);
        drawCategoryChip(g, catX, catY, "[C//] Legs", EnchantCategory.LEGGINGS, p, true);

        // The wheel itself -- visualizes the DEFAULT (1 XP) odds; spins for 1 second on a roll
        int wheelD = 200;
        int wheelCx = px + panelW / 2 - 40;
        int wheelCy = py + 235;
        double spinProgress = spinning ? 1.0 - (p.spinAnimUntil - now) / (double) Player.SPIN_ANIM_MS : 1.0;
        drawWheel(g, wheelCx, wheelCy, wheelD, spinning, spinProgress, p.lastSpinTier, p.lastSpinOption, p.lastSpinLandingFraction);
        drawArrowAndSpinButton(g, wheelCx + wheelD / 2, wheelCy);

        // Priced spin options, sketch-style boxes
        int boxY = py + 340;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Default: 1 LVL  --  [1] SPIN", px + 16, boxY);
        int boxX = px + 16;
        int[] keys = {2, 3, 4, 5};
        EnchantSpinOption[] options = {EnchantSpinOption.SPIN_30, EnchantSpinOption.SPIN_90,
                EnchantSpinOption.SPIN_270, EnchantSpinOption.SPIN_450};
        for (int i = 0; i < options.length; i++) {
            boxX = drawPriceBox(g, boxX, boxY + 14, keys[i], options[i]);
        }

        // Last spin result -- hidden while the wheel is still "spinning" (no reveal early)
        int resultY = boxY + 100;
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        if (spinning) {
            g.setColor(Color.YELLOW);
            g.drawString("Spinning...", px + 16, resultY);
        } else if (p.lastSpinTier > 0) {
            g.setColor(EnchantSpinOption.TIER_COLORS[p.lastSpinTier - 1]);
            g.drawString("Last spin: Tier " + p.lastSpinTier + " -- " + describeResult(p.lastSpinResult), px + 16, resultY);
        } else {
            g.setColor(Color.GRAY);
            g.drawString("No spin yet -- press 1 to try your luck", px + 16, resultY);
        }

        // Currently equipped summary for this category
        int equipY = resultY + 26;
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Currently equipped (" + categoryLabel(p.enchantCategory) + "):", px + 16, equipY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        List<EnchantType> pool = EnchantType.forCategory(p.enchantCategory);
        int lineY = equipY + 20;
        boolean any = false;
        for (EnchantType type : pool) {
            Integer amp = p.enchants.getOrDefault(p.enchantCategory, Map.of()).get(type);
            if (amp == null) continue;
            any = true;
            g.setColor(Color.WHITE);
            g.drawString(type.displayName + " " + EnchantType.romanNumeral(amp) + " -- " + describe(type, amp), px + 16, lineY);
            lineY += 18;
        }
        if (!any) {
            g.setColor(Color.GRAY);
            g.drawString("(none yet)", px + 16, lineY);
        }
    }

    private String categoryLabel(EnchantCategory cat) {
        return switch (cat) {
            case SWORD -> "Sword";
            case BOW -> "Bow";
            case HELMET -> "Helmet";
            case CHESTPLATE -> "Chestplate";
            case LEGGINGS -> "Leggings";
        };
    }

    private String describeResult(Map<EnchantType, Integer> result) {
        if (result.isEmpty()) return "(nothing rolled)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EnchantType, Integer> e : result.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey().displayName).append(" ").append(EnchantType.romanNumeral(e.getValue()));
        }
        return sb.toString();
    }

    private int drawCategoryChip(Graphics2D g, int x, int y, String label, EnchantCategory cat, Player p, boolean allowed) {
        boolean selected = p.enchantCategory == cat;
        g.setFont(new Font("SansSerif", selected ? Font.BOLD : Font.PLAIN, 13));
        g.setColor(!allowed ? Color.DARK_GRAY : (selected ? Color.YELLOW : Color.WHITE));
        g.drawString(label, x, y);
        return x + g.getFontMetrics().stringWidth(label) + 18;
    }

    /** Static pie chart of the DEFAULT (1 XP) spin odds -- "no enchantment reveals" up front,
     *  this only shows the tier odds, never what you'll actually roll within a tier. */
    /**
     * Static pie chart of the DEFAULT (1 LVL) spin odds. While spinning, the whole wheel
     * rotates fast-then-slow (ease-out) and lands exactly on resultTier's wedge, at the arrow
     * (screen angle 0 / 3 o'clock, where the arrow tip touches the wheel edge) -- previously
     * it just spun to an arbitrary stop with no relation to the actual roll.
     *
     * Graphics2D.rotate(+theta) is a visually CLOCKWISE screen rotation, while fillArc's
     * startAngle is visually COUNTERCLOCKWISE-positive from 3 o'clock, so a wedge whose
     * (unrotated) angle is A needs rotate(theta = +A) to end up sitting at screen angle 0.
     */
    /**
     * Pie chart for whichever spin option was actually used last (falls back to DEFAULT before
     * any spin has happened) -- previously this always showed DEFAULT's odds regardless of
     * which price the player actually paid.
     *
     * Motion: a real spin has two phases -- a fast, roughly constant-speed spin for the first
     * 55% of the window (so it visibly reads as "spinning", not just easing the whole time),
     * then a deceleration into the exact landing spot for the remaining 45%. The landing spot
     * itself is a random point within the winning wedge (not always its exact center), so even
     * repeat Tier-1 results don't all stop at the identical angle.
     */
    private void drawWheel(Graphics2D g, int cx, int cy, int diameter, boolean spinning, double progress,
                            int resultTier, EnchantSpinOption option, double landingFraction) {
        Graphics2D wheelG = (Graphics2D) g.create();
        if (resultTier >= 1 && resultTier <= 5) {
            double targetRad = Math.toRadians(wedgeLandingAngleDeg(resultTier, option, landingFraction));
            if (spinning) {
                double t = Math.max(0, Math.min(1, progress));
                double splitT = 0.55;          // fraction of the duration spent at full speed
                double constantPortion = 0.75; // fraction of the total rotation covered during that phase
                double totalSpins = 7;
                double r; // remaining-rotation fraction, 1 at t=0 down to 0 at t=1
                if (t <= splitT) {
                    r = 1 - (t / splitT) * constantPortion;
                } else {
                    double t2 = (t - splitT) / (1 - splitT);
                    r = (1 - constantPortion) * (1 - t2) * (1 - t2); // ease-out into the landing
                }
                double extraSpins = Math.toRadians(360.0 * totalSpins) * r;
                wheelG.rotate(targetRad + extraSpins, cx, cy);
            } else {
                wheelG.rotate(targetRad, cx, cy);
            }
        }

        int r = diameter / 2;
        double startAngle = 0;
        for (int tier = 1; tier <= 5; tier++) {
            double weight = option.weightFor(tier);
            if (weight <= 0) continue;
            double sweep = weight / 100.0 * 360.0;
            wheelG.setColor(EnchantSpinOption.TIER_COLORS[tier - 1]);
            wheelG.fillArc(cx - r, cy - r, diameter, diameter, (int) Math.round(startAngle), (int) Math.round(sweep) + 1);
            startAngle += sweep;
        }
        wheelG.setColor(Color.BLACK);
        wheelG.drawOval(cx - r, cy - r, diameter, diameter);

        // Tier labels near the middle of each big-enough wedge
        wheelG.setFont(new Font("SansSerif", Font.BOLD, 13));
        startAngle = 0;
        for (int tier = 1; tier <= 5; tier++) {
            double weight = option.weightFor(tier);
            if (weight <= 0) continue;
            double sweep = weight / 100.0 * 360.0;
            if (sweep > 8) { // only label wedges big enough to read
                double midAngleRad = Math.toRadians(-(startAngle + sweep / 2)); // screen Y is flipped
                int lx = cx + (int) (Math.cos(midAngleRad) * r * 0.6);
                int ly = cy + (int) (Math.sin(midAngleRad) * r * 0.6);
                wheelG.setColor(Color.BLACK);
                wheelG.drawString("T" + tier, lx - 8, ly + 5);
            }
            startAngle += sweep;
        }
        wheelG.dispose();
    }

    /** Same accumulation the wedges are drawn with, so the rotation math lines up exactly.
     *  Lands at a random point within the wedge (padded off the edges) rather than always
     *  the exact center, so repeat results on a huge wedge like Tier 1 don't feel identical. */
    private double wedgeLandingAngleDeg(int targetTier, EnchantSpinOption option, double landingFraction) {
        double startAngle = 0;
        for (int tier = 1; tier <= 5; tier++) {
            double weight = option.weightFor(tier);
            if (weight <= 0) continue;
            double sweep = weight / 100.0 * 360.0;
            if (tier == targetTier) {
                double pad = Math.min(sweep * 0.15, 6);
                double usable = Math.max(0, sweep - 2 * pad);
                return startAngle + pad + landingFraction * usable;
            }
            startAngle += sweep;
        }
        return 0;
    }

    private void drawArrowAndSpinButton(Graphics2D g, int arrowX, int arrowY) {
        g.setColor(new Color(150, 80, 190));
        int[] xs = {arrowX + 5, arrowX + 35, arrowX + 35};
        int[] ys = {arrowY, arrowY - 14, arrowY + 14};
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(200, 40, 40));
        g.fillRect(arrowX + 35, arrowY - 3, 40, 6);

        // SPIN button, directly under the arrow per the sketch
        int btnW = 80, btnH = 26;
        int btnX = arrowX + 20;
        int btnY = arrowY + 22;
        g.setColor(new Color(200, 60, 60));
        g.fillRoundRect(btnX, btnY, btnW, btnH, 8, 8);
        g.setColor(Color.WHITE);
        g.drawRoundRect(btnX, btnY, btnW, btnH, 8, 8);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("[1] SPIN", btnX + 6, btnY + 17);
    }

    private int drawPriceBox(Graphics2D g, int x, int y, int key, EnchantSpinOption option) {
        int w = 150, h = 60;
        g.setColor(new Color(45, 45, 55));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(Color.WHITE);
        g.drawRoundRect(x, y, w, h, 8, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("[" + key + "] " + option.levelCost + " LVL", x + 8, y + 18);

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        int lineY = y + 34;
        for (int tier = 1; tier <= 5; tier++) {
            double weight = option.weightFor(tier);
            if (weight <= 0) continue;
            g.setColor(EnchantSpinOption.TIER_COLORS[tier - 1]);
            g.drawString("T" + tier + ": " + trimZero(weight) + "%", x + 8, lineY);
            lineY += 12;
            if (lineY > y + h - 4) break;
        }
        return x + w + 12;
    }

    private String trimZero(double v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private String describe(EnchantType type, int amp) {
        double amt = type.amountFor(amp);
        String n = (amt == Math.floor(amt)) ? String.valueOf((int) amt) : String.valueOf(amt);
        String sign = type == EnchantType.QUICK_CHARGE || type == EnchantType.SWING_SPEED ? "-" : "+";
        return sign + n + " " + type.unit;
    }
}
