package laststand.ui;

import laststand.core.Constants;
import laststand.entity.AttackType;
import laststand.entity.Player;
import laststand.entity.PlayerClass;
import laststand.shop.EnchantCategory;
import laststand.shop.EnchantSpinOption;
import laststand.shop.UpgradeType;
import laststand.shop.Wallet;
import laststand.wave.WaveManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

public class HUD {

    public void draw(Graphics2D g, List<Player> players, WaveManager waveManager, Wallet wallet1, Wallet wallet2, long nowMs) {
        g.setColor(new Color(25, 25, 25));
        g.fillRect(0, 0, Constants.SCREEN_WIDTH, Constants.HUD_HEIGHT);

        for (Player p : players) {
            if (p.playerNumber == 1) drawPlayerPanel(g, p, 10, wallet1, nowMs);
            else drawPlayerPanel(g, p, Constants.SCREEN_WIDTH - 260, wallet2, nowMs);
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        String waveText = waveManager.bossWave ? ("WAVE " + waveManager.currentWave + " - BOSS!") : ("WAVE " + waveManager.currentWave);
        int tw = g.getFontMetrics().stringWidth(waveText);
        g.setColor(waveManager.bossWave ? new Color(230, 60, 60) : Color.WHITE);
        g.drawString(waveText, (Constants.SCREEN_WIDTH - tw) / 2, 34);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.GRAY);
        String hint = "E: shop   P1: 1-4 select item, SPACE use   P2: 0/9/8/7 select item, ENTER use";
        g.drawString(hint, (Constants.SCREEN_WIDTH - g.getFontMetrics().stringWidth(hint)) / 2, 52);
    }

    private void drawPlayerPanel(Graphics2D g, Player p, int panelX, Wallet wallet, long nowMs) {
        int barW = 250, barH = 14;
        int barY = 26;

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Player.colorFor(p.playerNumber));
        g.drawString("P" + p.playerNumber + " (" + p.playerClass.name() + ") - " + p.weaponTier.displayName, panelX, 14);

        // HP bar
        g.setColor(Color.DARK_GRAY);
        g.fillRect(panelX, barY, barW, barH);
        g.setColor(p.alive ? new Color(60, 200, 60) : Color.RED);
        int fillW = (int) (barW * Math.max(0, p.health) / (double) p.maxHealth);
        g.fillRect(panelX, barY, Math.max(0, fillW), barH);
        g.setColor(Color.WHITE);
        g.drawRect(panelX, barY, barW, barH);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(p.health + "/" + p.maxHealth, panelX + barW / 2 - 16, barY + 11);

        // EXP bar, right below HP -- banked for the upcoming enchantment system
        int expY = barY + barH + 4;
        int expH = 7;
        g.setColor(new Color(40, 40, 60));
        g.fillRect(panelX, expY, barW, expH);
        g.setColor(new Color(140, 90, 220));
        int expFillW = (int) (barW * Math.min(1.0, p.exp / (double) Player.EXP_CAP));
        g.fillRect(panelX, expY, Math.max(0, expFillW), expH);
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(panelX, expY, barW, expH);

        int lineY = expY + expH + 16;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.WHITE);
        String weaponLine = p.playerClass.attackType == AttackType.RANGED
                ? "Arrows: " + p.ammo + (p.ammo <= 0 ? " (Combat Pin!)" : "")
                : "Dagger";
        g.drawString(weaponLine, panelX, lineY);
        g.drawString("Lv" + p.level + "  EXP " + p.exp + "/" + Player.EXP_CAP, panelX + 130, lineY);

        int orbY = lineY + 18;
        drawOrb(g, panelX, orbY, new Color(80, 70, 130), wallet.darkOrbs);
        drawOrb(g, panelX + 75, orbY, new Color(190, 190, 200), wallet.silverOrbs);
        drawOrb(g, panelX + 150, orbY, new Color(235, 210, 60), wallet.yellowOrbs);

        drawHotbar(g, p, panelX, orbY + 18, nowMs);
    }

    private void drawOrb(Graphics2D g, int x, int y, Color color, int count) {
        g.setColor(color);
        g.fillOval(x, y - 9, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.valueOf(count), x + 13, y);
    }

    // ---------------------------------------------------------- hotbar

    private static final String[] P1_KEYS = {"1", "2", "3", "4"};
    private static final String[] P2_KEYS = {"0", "9", "8", "7"};

    private void drawHotbar(Graphics2D g, Player p, int panelX, int y, long nowMs) {
        int slotSize = 26, gap = 6;
        String[] keys = p.playerNumber == 1 ? P1_KEYS : P2_KEYS;

        for (int i = 0; i < 4; i++) {
            int sx = panelX + i * (slotSize + gap);
            boolean selected = p.selectedSlot == i;
            boolean usable = isSlotUsable(p, i, nowMs);

            g.setColor(selected ? new Color(95, 90, 120) : new Color(45, 45, 45));
            g.fillRoundRect(sx, y, slotSize, slotSize, 6, 6);
            g.setColor(selected ? Color.YELLOW : Color.GRAY);
            g.drawRoundRect(sx, y, slotSize, slotSize, 6, 6);

            drawSlotIcon(g, p, i, sx, y, slotSize, usable, nowMs);

            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g.setColor(Color.LIGHT_GRAY);
            g.drawString(keys[i], sx + 2, y + slotSize - 2);
        }
    }

    /** Darkened/translucent version of a color for use as a box fill BEHIND a glyph that's drawn
     *  in a different, full-brightness color on top. 50% instead of the original 35% -- that was
     *  too dark to actually tell the enchant tier color apart from the background. */
    private static Color darkenForBg(Color c) {
        return new Color(c.getRed() * 50 / 100, c.getGreen() * 50 / 100, c.getBlue() * 50 / 100);
    }

    private static final Color NO_ENCHANT_BG = new Color(45, 45, 45); // nothing rolled into this slot yet

    /** The box color behind a gear slot's glyph: Tier 1-5 (see EnchantSpinOption.TIER_COLORS),
     *  darkened, based on how enchanted that specific slot is (Player.enchantTierFor) -- NOT the
     *  material tier, which is what the glyph itself is colored by instead (see drawSlotIcon /
     *  drawArmorIcons). Plain neutral dark gray if nothing's been enchanted into it at all. */
    private static Color enchantTierBg(int tier) {
        return tier <= 0 ? NO_ENCHANT_BG : darkenForBg(EnchantSpinOption.TIER_COLORS[tier - 1]);
    }

    private boolean isSlotUsable(Player p, int slot, long nowMs) {
        return switch (slot) {
            case 0 -> true;
            case 1 -> p.medicKits > 0;
            case 2 -> p.shieldUnlocked && p.shieldCooldownRemainingMs(nowMs) <= 0;
            case 3 -> p.playerClass == PlayerClass.RANGER;
            default -> false;
        };
    }

    /** Blocky icons: slot 0 (weapon) and slot 3 (Combat Pin) get a box fill behind the glyph
     *  colored by that slot's ENCHANT tier (Tier 1-5, darkened -- see enchantTierBg), while the
     *  glyph itself is drawn in the weapon's MATERIAL color (WeaponTier.displayColor) -- two
     *  independent things: what you bought vs. what you've enchanted into it. Slot 0 swaps to
     *  the Combat Pin glyph the moment a Ranger runs out of arrows, since that's a real weapon
     *  change, not just an ammo count. */
    private void drawSlotIcon(Graphics2D g, Player p, int slot, int sx, int sy, int size, boolean usable, long nowMs) {
        int cx = sx + size / 2, cy = sy + size / 2;
        Color dim = new Color(90, 90, 90);
        switch (slot) {
            case 0 -> { // weapon -- Dagger (Tank), Bow (Ranger w/ arrows), or Combat Pin (Ranger, out of arrows)
                Color glyphColor = usable ? p.weaponTier.displayColor : dim; // material color
                EnchantCategory weaponCat = p.playerClass == PlayerClass.TANK ? EnchantCategory.SWORD : EnchantCategory.BOW;
                g.setColor(enchantTierBg(p.enchantTierFor(weaponCat)));
                g.fillRoundRect(sx + 3, sy + 3, size - 6, size - 6, 5, 5);
                if (p.playerClass == PlayerClass.TANK) {
                    Player.drawPixelGlyph(g, Player.DAGGER_GLYPH, cx - 8, cy - 6, 4, glyphColor);
                } else if (p.ammo > 0) {
                    Player.drawPixelGlyph(g, Player.BOW_GLYPH, cx - 8, cy - 5, 5, glyphColor);
                } else {
                    Player.drawPixelGlyph(g, Player.COMBAT_PIN_GLYPH, cx - 8, cy - 6, 4, glyphColor);
                }
            }
            case 1 -> { // medic kit
                g.setColor(usable ? new Color(60, 200, 90) : dim);
                g.fillRect(sx + 5, sy + 5, size - 10, size - 10);
                g.setColor(Color.WHITE);
                g.fillRect(cx - 1, sy + 7, 2, size - 14);
                g.fillRect(sx + 7, cy - 1, size - 14, 2);
                drawCount(g, sx, sy, size, p.medicKits);
            }
            case 2 -> { // shield -- shows a countdown while on cooldown, "?" if not yet purchased
                g.setColor(usable ? new Color(210, 225, 235) : dim);
                g.fillOval(sx + 5, sy + 4, size - 10, size - 8);
                if (!p.shieldUnlocked) {
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("SansSerif", Font.BOLD, 10));
                    g.drawString("?", sx + size / 2 - 3, sy + size - 6);
                } else {
                    long remain = p.shieldCooldownRemainingMs(nowMs);
                    if (remain > 0) {
                        drawCount(g, sx, sy, size, (int) Math.ceil(remain / 1000.0));
                    }
                }
            }
            case 3 -> { // Ranger's Combat Pin -- derives from Bow's enchant tier; blank for Tank
                if (p.playerClass == PlayerClass.RANGER) {
                    Color glyphColor = usable ? p.weaponTier.displayColor : dim;
                    g.setColor(enchantTierBg(p.enchantTierFor(EnchantCategory.BOW)));
                    g.fillRoundRect(sx + 3, sy + 3, size - 6, size - 6, 5, 5);
                    Player.drawPixelGlyph(g, Player.COMBAT_PIN_GLYPH, cx - 8, cy - 6, 4, glyphColor);
                }
            }
        }
    }

    private void drawCount(Graphics2D g, int sx, int sy, int size, int count) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(String.valueOf(count), sx + size - 9, sy + size - 2);
    }

    // ---------------------------------------------------------- buff bar

    /**
     * Bottom-of-screen readout of all 9 upgrade levels per player -- P1 bottom-left,
     * P2 bottom-right, mirroring the inventory hotbar's quantity-badge style.
     */
    public void drawBuffBar(Graphics2D g, List<Player> players) {
        int barY = Constants.SCREEN_HEIGHT - Constants.BUFF_BAR_HEIGHT;
        g.setColor(new Color(20, 20, 20));
        g.fillRect(0, barY, Constants.SCREEN_WIDTH, Constants.BUFF_BAR_HEIGHT);

        for (Player p : players) {
            if (p.playerNumber == 1) drawPlayerBuffs(g, p, 10, barY, false);
            else drawPlayerBuffs(g, p, Constants.SCREEN_WIDTH - 10, barY, true);
        }
    }

    private void drawPlayerBuffs(Graphics2D g, Player p, int anchorX, int barY, boolean rightAlign) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(Player.colorFor(p.playerNumber));
        String label = "BUFFS";
        if (rightAlign) {
            g.drawString(label, anchorX - g.getFontMetrics().stringWidth(label), barY + 13);
        } else {
            g.drawString(label, anchorX, barY + 13);
        }

        UpgradeType[] types = UpgradeType.values();
        int slotSize = 20, gap = 3;
        int totalW = types.length * (slotSize + gap) - gap;
        int startX = rightAlign ? anchorX - totalW : anchorX;
        int iconY = barY + 20;

        for (int i = 0; i < types.length; i++) {
            UpgradeType type = types[i];
            int sx = startX + i * (slotSize + gap);
            int lvl = p.upgradeLevel(type);

            g.setColor(new Color(45, 45, 45));
            g.fillRoundRect(sx, iconY, slotSize, slotSize, 5, 5);
            g.setColor(lvl > 0 ? type.color : new Color(70, 70, 70));
            g.fillRect(sx + 4, iconY + 4, slotSize - 8, slotSize - 8);
            g.setColor(Color.GRAY);
            g.drawRoundRect(sx, iconY, slotSize, slotSize, 5, 5);

            g.setColor(lvl > 0 ? Color.WHITE : Color.LIGHT_GRAY);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.drawString(String.valueOf(lvl), sx + 1, iconY + slotSize - 1);
        }

        drawArmorIcons(g, p, rightAlign ? startX - 10 : startX + totalW + 10, iconY, slotSize, rightAlign);
    }

    /** 3 armor-piece icons (Helmet/Chest/Legs) beside the upgrade icons. The box behind each
     *  glyph is filled with that piece's own ENCHANT tier color (Tier 1-5, darkened -- see
     *  enchantTierBg), while the glyph itself is drawn in the armor's MATERIAL color
     *  (ArmorTier.displayColor, same across all 3 since it's unified) -- two independent things:
     *  what you bought vs. what you've enchanted into that specific piece. */
    private static final EnchantCategory[] ARMOR_PIECE_CATEGORIES =
            {EnchantCategory.HELMET, EnchantCategory.CHESTPLATE, EnchantCategory.LEGGINGS};

    private void drawArmorIcons(Graphics2D g, Player p, int anchorX, int iconY, int slotSize, boolean rightAlign) {
        Color glyphColor = p.armorTier.displayColor;
        int gap = 3;
        int totalW = 3 * (slotSize + gap) - gap;
        int startX = rightAlign ? anchorX - totalW : anchorX;

        for (int i = 0; i < 3; i++) {
            int sx = startX + i * (slotSize + gap);
            g.setColor(enchantTierBg(p.enchantTierFor(ARMOR_PIECE_CATEGORIES[i])));
            g.fillRoundRect(sx + 1, iconY + 1, slotSize - 2, slotSize - 2, 5, 5);
            g.setColor(Color.GRAY);
            g.drawRoundRect(sx, iconY, slotSize, slotSize, 5, 5);

            g.setColor(glyphColor);
            int cx = sx + slotSize / 2;
            switch (i) {
                case 0 -> g.fillArc(sx + 4, iconY + 5, slotSize - 8, slotSize - 8, 0, 180); // helmet: dome
                case 1 -> g.fillRect(sx + 4, iconY + 5, slotSize - 8, slotSize - 9); // chestplate: block
                default -> { // leggings: two "legs"
                    int legW = (slotSize - 10) / 2;
                    g.fillRect(sx + 4, iconY + 4, legW, slotSize - 8);
                    g.fillRect(cx + 1, iconY + 4, legW, slotSize - 8);
                }
            }
        }

        // "DEF: N" to the right of the icons for P1, to the left for P2
        String defText = "DEF: " + p.totalDefense();
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        if (rightAlign) {
            int tw = g.getFontMetrics().stringWidth(defText);
            g.drawString(defText, startX - gap - tw - 6, iconY + slotSize / 2 + 4);
        } else {
            g.drawString(defText, startX + totalW + gap + 6, iconY + slotSize / 2 + 4);
        }
    }
}
