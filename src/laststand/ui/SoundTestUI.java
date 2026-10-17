package laststand.ui;

import laststand.core.Constants;
import laststand.core.InputHandler;
import laststand.fx.Sfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * The secret Sound Test screen (Ctrl+S, from any screen) -- a soundboard in the style of MyInstants:
 * one big, glossy, colour-coded round button per sound effect in {@link Sfx.Id}, labelled with the
 * sound's name. Click a button (or move to it and press SPACE/ENTER) to hear it. A strip along the
 * bottom shows the selected effect's waveform, length, peak level, where it's used in the game,
 * its source (synth or WAV) and its in-game rate limit.
 *
 * Colours are by group: Menu (blue), Combat (red), Items &amp; Shop (amber), Ultimates (purple),
 * Waves (green). While a button's sound is playing a white ring sweeps around it; pressing it
 * squashes it and sends out a ripple.
 *
 * Controls: mouse click = play, W/A/S/D or arrow keys = move between buttons, SPACE/ENTER = play,
 * R = play every sound in order (R again to stop), X = stop, ESC or Ctrl+S = close, F9 = mute.
 *
 * Select a sound and press E (or click EDIT SOUND) to change it in the in-game SFX Editor; "??? 2" opens the Music Editor
 * instead. Saved edits apply everywhere in the game, not just here.
 *
 * Plays through the same mixer and the same samples the game uses ({@link Sfx#preview}), so what you
 * hear here is exactly what the game plays. The game itself is frozen while this is open.
 *
 * Adding a new effect: add it to {@link Sfx.Id}; it gets a button here automatically (under "OTHER"
 * until you give it a line in the INFO table below).
 */
public final class SoundTestUI {

    private record Info(String label, String group, String when) {}

    private static final Map<Sfx.Id, Info> INFO = new EnumMap<>(Sfx.Id.class);
    private static void info(Sfx.Id id, String group, String label, String when) { INFO.put(id, new Info(label, group, when)); }
    static {
        info(Sfx.Id.MENU_MOVE,      "MENU",          "Menu move",        "Moving the highlight in any menu");
        info(Sfx.Id.MENU_SELECT,    "MENU",          "Menu select",      "Confirming a menu item; sound turned back on");
        info(Sfx.Id.SHOOT_ARROW,    "COMBAT",        "Bow shot",         "Ranger fires an arrow");
        info(Sfx.Id.THROW_DAGGER,   "COMBAT",        "Dagger throw",     "Tank throws the Dagger");
        info(Sfx.Id.PIN_STAB,       "COMBAT",        "Combat Pin",       "Ranger fires the Combat Pin");
        info(Sfx.Id.HIT_ENEMY,      "COMBAT",        "Enemy hit",        "A player's attack lands on an enemy");
        info(Sfx.Id.ENEMY_DEATH,    "COMBAT",        "Enemy death",      "An enemy is slain");
        info(Sfx.Id.PLAYER_HURT,    "COMBAT",        "Player hurt",      "A player takes damage");
        info(Sfx.Id.POTION,         "ITEMS & SHOP",  "Healing Potion",   "Drinking the Healing Potion");
        info(Sfx.Id.SHIELD_ON,      "ITEMS & SHOP",  "Shield on",        "Shield is activated");
        info(Sfx.Id.BUY,            "ITEMS & SHOP",  "Shop buy",         "A successful purchase / enchant spin");
        info(Sfx.Id.ERROR,          "ITEMS & SHOP",  "Error buzz",       "Can't afford it / action not allowed");
        info(Sfx.Id.LEVEL_UP,       "ITEMS & SHOP",  "Level up",         "Every 5th level a player obtains");
        info(Sfx.Id.ULTIMATE_READY, "ULTIMATES",     "Ultimate ready",   "Ultimate charge reaches full");
        info(Sfx.Id.GIANT_ARROW,    "ULTIMATES",     "Giant Arrow",      "Ranger fires the Giant Arrow");
        info(Sfx.Id.THORNS_ON,      "ULTIMATES",     "Thorns on",        "Tank activates Thorns");
        info(Sfx.Id.THORNS_PULSE,   "ULTIMATES",     "Thorns pulse",     "Every 0.5 s while Thorns is up");
        info(Sfx.Id.WAVE_START,     "WAVES",         "Wave start",       "1 s after a new wave begins");
        info(Sfx.Id.BOSS_WARN,      "WAVES",         "Boss warning",     "0.5 s after a boss wave begins");
        info(Sfx.Id.WAVE_CLEAR,     "WAVES",         "Wave cleared",     "Every enemy of the wave is dead");
        info(Sfx.Id.GAME_OVER,      "WAVES",         "Game over",        "Every player is down");
        info(Sfx.Id.SECRET_TRACK,   "MISC",          "???",              "In a game: hold Ctrl+S, press 1");
        info(Sfx.Id.SECRET_TRACK_2, "MISC",          "??? 2",            "In a game: hold Ctrl+S, press 2. E: edit");
    }

    private static final Sfx.Id[] IDS = Sfx.Id.values();
    private static final int N = IDS.length;

    // ---- layout (design space: 1000 x 740) ----
    private static final int COLS = 6, CELL_W = 150, CELL_H = 118, GRID_X = 50, GRID_Y = 96, BTN_D = 80;
    private static final int STRIP_X = 50, STRIP_Y = 584, STRIP_W = 900, STRIP_H = 106;

    private static final String[] GROUPS = {"MENU", "COMBAT", "ITEMS & SHOP", "ULTIMATES", "WAVES", "MISC", "OTHER"};
    private static final int LEGEND_GROUPS = 5; // MISC and OTHER stay out of the legend: they're the hidden corner
    private static final Color[] GROUP_COLORS = {
            new Color(70, 140, 255), new Color(235, 75, 75), new Color(255, 170, 40),
            new Color(165, 95, 255), new Color(45, 195, 135), new Color(255, 110, 185), new Color(150, 150, 165)};

    private final String[] groupOf = new String[N];
    private final Color[] colorOf = new Color[N];
    private final long[] pressedAt = new long[N];    // when each button was last pressed (0 = never), for its squash / ripple / ring
    private final Stats[] stats = new Stats[N];

    // ---- looping songs: the L key or the pill next to the title turns it on; a looping song repeats until stopped (X / ESC) ----
    private static final Rectangle LOOP_PILL = new Rectangle(290, 30, 150, 24);
    private boolean loopSongs = false;
    private Sfx.Id loopingId = null;

    // ---- the Music Editor for the second hidden track (E, or the EDIT pill, while "??? 2" is selected) ----
    private static final Rectangle EDIT_PILL = new Rectangle(STRIP_X + 226, STRIP_Y + 12, 124, 26);
    private SoundEditor editor = null;

    private int selected = 0;
    private int hover = -1;
    private Sfx.Id lastPlayed = null;
    private long lastPlayedAt = 0;
    private boolean playingAll = false;
    private int allIndex = 0;
    private long allNextAt = 0;

    /** Per-effect numbers, computed once from the samples the game actually plays. */
    private static final class Stats {
        double seconds;
        double peak;          // 0..1
        boolean recorded;     // WAV override instead of synth
        short[] data;
    }

    public SoundTestUI() {
        for (int i = 0; i < N; i++) {
            Info inf = INFO.get(IDS[i]);
            groupOf[i] = inf != null ? inf.group : "OTHER";
            int g = 0;
            while (g < GROUPS.length - 1 && !GROUPS[g].equals(groupOf[i])) g++; // unknown groups end up as OTHER (grey)
            colorOf[i] = GROUP_COLORS[g];
        }
    }

    /** The clickable cell of button i (the round button plus its label), in design space. */
    public Rectangle buttonBounds(int i) {
        return new Rectangle(GRID_X + (i % COLS) * CELL_W, GRID_Y + (i / COLS) * CELL_H, CELL_W, CELL_H);
    }

    private int buttonAt(int mx, int my) {
        for (int i = 0; i < N; i++) if (buttonBounds(i).contains(mx, my)) return i;
        return -1;
    }

    /** Called when the screen opens: starts fresh and warms up the audio line so the first sound isn't late. */
    public void open() {
        playingAll = false;
        lastPlayed = null;
        hover = -1;
        loopingId = null;
        editor = null;
        java.util.Arrays.fill(pressedAt, 0L);
        Sfx.isAudioAvailable();
    }

    /** Called when the screen closes. */
    public void close() {
        playingAll = false;
        loopingId = null;
        editor = null;
        Sfx.silence();
    }

    // ---- Music Editor plumbing ----

    /** True while the Music Editor is open on top of the soundboard (the game uses this so Ctrl+S saves instead of closing the screen). */
    public boolean isEditing() { return editor != null; }

    /** Ctrl+S while editing: save the tune. */
    public void editorSave() { if (editor != null) editor.requestSave(); }

    /** Every sound can be edited except the original hidden track "???" (which is kept as it is). */
    private boolean editable() { return IDS[selected] != Sfx.Id.SECRET_TRACK; }

    /** Opens the Music Editor on "??? 2", or the SFX Editor on any sound effect. */
    private void openEditor() {
        if (!editable()) return;
        stopAll();
        Sfx.silence();
        loopingId = null;
        final int idx = selected;
        Runnable saved = () -> stats[idx] = null;                // a save makes the soundboard re-read the sound
        editor = IDS[idx] == Sfx.Id.SECRET_TRACK_2 ? new MusicEditorUI(saved) : new SfxEditorUI(IDS[idx], saved);
    }

    // ================================================================== input

    /** Handles one frame of input. Returns true if the screen should close. */
    public boolean tick(InputHandler in) {
        long now = System.currentTimeMillis();

        if (editor != null) {                                    // the Music Editor has the keyboard (and Esc goes back to here)
            if (editor.tick(in)) { editor = null; Sfx.silence(); }
            return false;
        }

        if (in.wasJustPressed(KeyEvent.VK_ESCAPE)) { in.consume(KeyEvent.VK_ESCAPE); return true; }
        if (pressed(in, KeyEvent.VK_E, -1) && editable()) { openEditor(); return false; }

        if (pressed(in, KeyEvent.VK_A, KeyEvent.VK_LEFT))  { moveTo(selected - 1); stopAll(); }
        if (pressed(in, KeyEvent.VK_D, KeyEvent.VK_RIGHT)) { moveTo(selected + 1); stopAll(); }
        if (pressed(in, KeyEvent.VK_W, KeyEvent.VK_UP))   { moveTo(up(selected)); stopAll(); }
        if (pressed(in, KeyEvent.VK_S, KeyEvent.VK_DOWN)) { moveTo(down(selected)); stopAll(); }

        if (pressed(in, KeyEvent.VK_SPACE, KeyEvent.VK_ENTER)) { stopAll(); play(selected); }

        if (pressed(in, KeyEvent.VK_R, -1)) {
            if (playingAll) stopAll(); else { playingAll = true; allIndex = 0; allNextAt = now; }
        }
        if (pressed(in, KeyEvent.VK_X, -1)) { stopAll(); Sfx.silence(); loopingId = null; }
        if (pressed(in, KeyEvent.VK_L, -1)) toggleLoop();

        if (playingAll && now >= allNextAt) {
            while (allIndex < N && isTrack(IDS[allIndex])) allIndex++; // "play all" skips the long hidden track
            if (allIndex >= N) {
                playingAll = false;
            } else {
                selected = allIndex;
                play(allIndex++);
                allNextAt = now + (long) (statsFor(selected).seconds * 1000) + 400;
            }
        }
        return false;
    }

    /** Mouse click in design-space coordinates: select + play the clicked button. */
    public void click(int mx, int my) { click(mx, my, MouseEvent.BUTTON1); }

    /** Mouse press with the button number (1 = left, 3 = right): the editor uses both; the soundboard only the left one. */
    public void click(int mx, int my, int button) {
        if (editor != null) { editor.press(mx, my, button); return; }
        if (button != MouseEvent.BUTTON1) return;
        if (editable() && EDIT_PILL.contains(mx, my)) { openEditor(); return; }
        if (LOOP_PILL.contains(mx, my)) { toggleLoop(); return; }
        int i = buttonAt(mx, my);
        if (i < 0) return;
        stopAll();
        selected = i;
        play(i);
    }

    /** Mouse moved (design-space coordinates): lights up the button under the pointer. */
    private int hoverX = -1, hoverY = -1;

    public void hover(int mx, int my) {
        hoverX = mx; hoverY = my;
        if (editor != null) { editor.hover(mx, my); return; }
        hover = buttonAt(mx, my);
    }

    /** Mouse dragged with a button held (design space) -- only the editor cares. */
    public void drag(int mx, int my) { if (editor != null) editor.drag(mx, my); }

    /** Mouse button released (design space) -- only the editor cares. */
    public void release(int mx, int my) { if (editor != null) editor.release(mx, my); }

    /** Mouse wheel (design space) -- only the editor cares. */
    public void wheel(int mx, int my, int rotation, boolean shift) { if (editor != null) editor.wheel(mx, my, rotation, shift); }

    private boolean pressed(InputHandler in, int a, int b) {
        boolean hit = false;
        if (in.wasJustPressed(a)) { in.consume(a); hit = true; }
        if (b >= 0 && in.wasJustPressed(b)) { in.consume(b); hit = true; }
        return hit;
    }

    /** Keyboard focus moves between buttons; left/right wrap through the list, up/down wrap within the column. */
    private void moveTo(int i) { selected = Math.floorMod(i, N); }

    private int up(int i) {
        int j = i - COLS;
        return j >= 0 ? j : i + COLS * ((N - 1 - i) / COLS);   // off the top: bottom of this column
    }

    private int down(int i) {
        int j = i + COLS;
        return j < N ? j : i % COLS;                             // off the bottom: top of this column
    }

    /** Loop on/off. Turning it off while a song is repeating stops that song. */
    private void toggleLoop() {
        loopSongs = !loopSongs;
        if (!loopSongs && loopingId != null) { Sfx.silence(); loopingId = null; }
    }

    private void play(int i) {
        if (isTrack(IDS[i])) {
            Sfx.silence(); // pressing it again restarts the song, instead of layering a second copy
            if (loopSongs && Sfx.loopSamplesFor(IDS[i]) != null) { Sfx.playLoop(IDS[i]); loopingId = IDS[i]; }
            else { Sfx.preview(IDS[i]); loopingId = null; }
        } else {
            Sfx.preview(IDS[i]);
        }
        lastPlayed = IDS[i];
        lastPlayedAt = System.currentTimeMillis();
        pressedAt[i] = lastPlayedAt;
    }

    private void stopAll() { playingAll = false; }

    /** The long hidden tunes: they restart (rather than layer) when pressed again, and "play all" skips them. */
    private static boolean isTrack(Sfx.Id id) { return id == Sfx.Id.SECRET_TRACK || id == Sfx.Id.SECRET_TRACK_2; }

    private Stats statsFor(int i) {
        Stats s = stats[i];
        if (s == null) {
            s = new Stats();
            s.data = Sfx.samplesFor(IDS[i]);
            s.seconds = s.data.length / (double) Sfx.SAMPLE_RATE;
            int peak = 0;
            for (short v : s.data) peak = Math.max(peak, Math.abs((int) v));
            s.peak = peak / 32767.0;
            s.recorded = Sfx.isRecordedOverride(IDS[i]);
            stats[i] = s;
        }
        return s;
    }

    // ================================================================== drawing

    /** Draws the screen in design space (1000 x 740); the caller has already dimmed whatever's behind it. */
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long now = System.currentTimeMillis();

        if (editor != null) { editor.render(g); return; }

        g.setColor(new Color(255, 214, 90));
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString("SOUND TEST", GRID_X, 52);
        g.setColor(new Color(150, 150, 165));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int shown = 0;                                   // the hidden MISC corner isn't counted in the subtitle
        for (int i = 0; i < N; i++) if (!"MISC".equals(groupOf[i])) shown++;
        g.drawString(shown + " effects  \u2022  click a button to play it", GRID_X, 74);

        drawLoopPill(g);
        drawLegend(g);
        drawBanner(g);
        for (int i = 0; i < N; i++) drawButton(g, i, now);
        drawStrip(g, now);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(170, 170, 185));
        centerText(g, "Click a button, or arrows / WASD + SPACE    R: play all    L: loop songs    X: stop    E: edit selected    F9: sound on/off    ESC or Ctrl+S: close",
                Constants.SCREEN_WIDTH / 2, 716);
    }

    /** The little "LOOP SONGS (L): ON/OFF" switch beside the title. */
    private void drawLoopPill(Graphics2D g) {
        Rectangle r = LOOP_PILL;
        g.setColor(loopSongs ? new Color(45, 195, 135) : new Color(50, 56, 78));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 24, 24);
        g.setColor(loopSongs ? new Color(8, 40, 26) : new Color(185, 190, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        centerText(g, "LOOP SONGS (L): " + (loopSongs ? "ON" : "OFF"), r.x + r.width / 2, r.y + 16);
    }

    private void drawLegend(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int total = 0;
        for (int k = 0; k < LEGEND_GROUPS; k++) total += 16 + fm.stringWidth(GROUPS[k]) + 16;
        int x = GRID_X + 900 - total;
        for (int k = 0; k < LEGEND_GROUPS; k++) {
            g.setColor(GROUP_COLORS[k]);
            g.fillOval(x, 66, 10, 10);
            g.setColor(new Color(190, 190, 205));
            g.drawString(GROUPS[k], x + 15, 75);
            x += 16 + fm.stringWidth(GROUPS[k]) + 16;
        }
    }

    private void drawBanner(Graphics2D g) {
        String msg = null;
        if (Sfx.isMuted()) msg = "SOUND IS OFF \u2014 press F9 to turn it back on";
        else if (!Sfx.isAudioAvailable()) msg = "No audio device found \u2014 the game will be silent on this machine";
        if (msg == null) return;
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        int w = g.getFontMetrics().stringWidth(msg) + 28;
        int x = GRID_X + 900 - w;
        g.setColor(new Color(150, 30, 30));
        g.fillRoundRect(x, 24, w, 30, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(msg, x + 14, 44);
    }

    private void drawButton(Graphics2D g, int i, long now) {
        Rectangle cell = buttonBounds(i);
        Stats st = statsFor(i);
        Color base = colorOf[i];
        boolean sel = i == selected, hov = i == hover;
        long since = pressedAt[i] == 0 ? Long.MAX_VALUE : now - pressedAt[i];
        boolean looping = IDS[i] == loopingId;
        double loopMs = looping ? Sfx.loopSecondsFor(IDS[i]) * 1000 : 0;
        boolean sounding = looping || since < (long) (st.seconds * 1000);

        int cx = cell.x + cell.width / 2, cy = cell.y + 6 + BTN_D / 2;
        double scale = since < 140 ? 0.88 + 0.12 * (since / 140.0) : 1.0;   // squash when pressed
        if (hov && scale >= 1.0) scale = 1.04;
        int d = (int) Math.round(BTN_D * scale), r = d / 2;

        // ripple sent out when pressed
        if (since < 500) {
            double t = since / 500.0;
            int rr = (int) (BTN_D / 2 + 6 + t * 26);
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) (150 * (1 - t))));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
        }

        // soft shadow
        g.setColor(new Color(0, 0, 0, 110));
        g.fillOval(cx - r + 2, cy - r + 5, d, d);

        // body: vertical gradient, brighter while hovered / sounding
        Color top = brighten(base, hov || sounding ? 0.55 : 0.35), bot = darken(base, 0.62);
        g.setPaint(new GradientPaint(cx, cy - r, top, cx, cy + r, bot));
        g.fillOval(cx - r, cy - r, d, d);
        g.setPaint(null);
        g.setColor(darken(base, 0.45));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(cx - r, cy - r, d, d);

        // gloss
        g.setColor(new Color(255, 255, 255, 70));
        g.fillOval(cx - (int) (r * 0.66), cy - r + 4, (int) (r * 1.32), (int) (r * 0.78));

        // play triangle
        int tr = (int) (r * 0.40);
        int[] xs = {cx - tr / 2 - 1, cx - tr / 2 - 1, cx + tr};
        int[] ys = {cy - tr, cy + tr, cy};
        g.setColor(new Color(255, 255, 255, 235));
        g.fillPolygon(xs, ys, 3);

        // progress ring sweeping around the button while its sound plays
        if (sounding) {
            double frac = looping && loopMs > 0 ? (since % (long) loopMs) / loopMs : since / (st.seconds * 1000.0);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int rr = r + 5;
            g.drawArc(cx - rr, cy - rr, rr * 2, rr * 2, 90, (int) Math.round(-360 * frac));
        }

        // keyboard focus ring
        if (sel) {
            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f));
            int rr = BTN_D / 2 + 11;
            g.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
        }
        g.setStroke(new BasicStroke(1f));

        // label (wraps onto two lines if it has to) + length
        Info inf = INFO.get(IDS[i]);
        String label = inf != null ? inf.label : IDS[i].name();
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(sel || hov ? Color.WHITE : new Color(225, 225, 235));
        int ly = cell.y + BTN_D + 26;
        if (fm.stringWidth(label) <= cell.width - 12) {
            centerText(g, label, cx, ly);
        } else {
            int sp = label.lastIndexOf(' ', label.length() / 2 + 2);
            if (sp < 0) sp = label.indexOf(' ');
            if (sp < 0) { centerText(g, label, cx, ly); }
            else { centerText(g, label.substring(0, sp), cx, ly); centerText(g, label.substring(sp + 1), cx, ly + 14); }
        }
    }

    private void drawStrip(Graphics2D g, long now) {
        Sfx.Id id = IDS[selected];
        Stats st = statsFor(selected);
        Info inf = INFO.get(id);
        Color accent = colorOf[selected];

        g.setColor(new Color(24, 26, 38, 240));
        g.fillRoundRect(STRIP_X, STRIP_Y, STRIP_W, STRIP_H, 16, 16);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(STRIP_X, STRIP_Y, STRIP_W, STRIP_H, 16, 16);
        g.setStroke(new BasicStroke(1f));

        int tx = STRIP_X + 18;
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(id == Sfx.Id.SECRET_TRACK ? "Hidden track" : id == Sfx.Id.SECRET_TRACK_2 ? "Hidden track 2" : (inf != null ? inf.label : id.name()), tx, STRIP_Y + 30);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(accent);
        g.drawString(id.name(), tx, STRIP_Y + 47);
        if (editable()) {                                     // the way into the Music Editor / SFX Editor
            Rectangle r = EDIT_PILL;
            boolean over = r.contains(hoverX, hoverY);
            g.setColor(over ? new Color(255, 150, 205) : accent);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
            g.setColor(new Color(40, 8, 26));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            centerText(g, id == Sfx.Id.SECRET_TRACK_2 ? "\u270E  EDIT TRACK (E)" : "\u270E  EDIT SOUND (E)", r.x + r.width / 2, r.y + 17);
        }
        String err = Sfx.errorFor(id);
        if (err == null && id == Sfx.Id.SECRET_TRACK_2) {              // a problem with the saved tune file (it's shown here instead of failing quietly)
            err = laststand.fx.WorkshopTune.loadProblem();
            if (err != null && err.length() > 128) err = err.substring(0, 127) + "\u2026";
        }
        if (err != null) {                                    // a mistake in this sound's recipe: show why, in red
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(new Color(255, 120, 110));
            FontMetrics efm = g.getFontMetrics();
            int ey = STRIP_Y + 62, lines = 0;
            StringBuilder line = new StringBuilder();
            for (String word : err.split(" ")) {
                if (efm.stringWidth(line + word) > 330 && line.length() > 0) {
                    g.drawString(line.toString(), tx, ey); ey += 16; line.setLength(0);
                    if (++lines == 3) break;
                }
                line.append(word).append(' ');
            }
            if (lines < 3) g.drawString(line.toString(), tx, ey);
        } else {
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(200, 200, 212));
        g.drawString(inf != null ? inf.when : "(not described yet)", tx, STRIP_Y + 68);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(150, 155, 175));
        g.drawString(String.format("%.0f ms  \u2022  peak %.0f%%  \u2022  %s", st.seconds * 1000, st.peak * 100,
                st.recorded ? "WAV file" : Sfx.hasSfxEdit(id) ? "synthesized (your edit)" : "synthesized"), tx, STRIP_Y + 86);
        g.drawString(id.minGapMs() > 0 ? "in-game rate limit: 1 per " + id.minGapMs() + " ms (ignored here)" : "in-game rate limit: none",
                tx, STRIP_Y + 101);
        }

        drawWave(g, st, now, id, STRIP_X + 360, STRIP_Y + 10, STRIP_W - 360 - 14, STRIP_H - 20);

        // play-all progress, drawn as a thin bar along the bottom of the strip
        if (playingAll) {
            g.setColor(new Color(110, 255, 140));
            g.fillRoundRect(STRIP_X + 14, STRIP_Y + STRIP_H - 6, (int) Math.round((STRIP_W - 28) * Math.min(1.0, allIndex / (double) N)), 4, 3, 3);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("\u25B6 Playing all\u2026 " + Math.min(allIndex, N) + " / " + N, STRIP_X + 360 + 8, STRIP_Y + 24);
        }
    }

    private void drawWave(Graphics2D g, Stats st, long now, Sfx.Id id, int x, int y, int w, int h) {
        g.setColor(new Color(12, 14, 22));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(45, 52, 78));
        g.drawLine(x, y + h / 2, x + w, y + h / 2);

        short[] d = st.data;
        if (d.length == 0) return;
        double perPx = d.length / (double) w;
        g.setColor(new Color(110, 200, 255));
        for (int px = 0; px < w; px++) {
            int a = (int) (px * perPx), b = Math.max(a + 1, (int) ((px + 1) * perPx));
            int lo = 0, hi = 0;
            for (int i = a; i < Math.min(b, d.length); i++) { lo = Math.min(lo, d[i]); hi = Math.max(hi, d[i]); }
            int y1 = y + h / 2 - (int) (hi / 32768.0 * (h / 2 - 4));
            int y2 = y + h / 2 - (int) (lo / 32768.0 * (h / 2 - 4));
            g.drawLine(x + px, y1, x + px, Math.max(y1, y2));
        }
        if (id == lastPlayed) { // moving playhead while this effect is sounding
            double t = (now - lastPlayedAt) / 1000.0;
            double loopLen = id == loopingId ? Sfx.loopSecondsFor(id) : 0;
            if (loopLen > 0) t = t % loopLen;                       // a looping song's playhead goes round and round
            if (t >= 0 && t <= st.seconds) {
                int hx = x + (int) Math.round(t / st.seconds * (w - 1));
                g.setColor(new Color(255, 214, 90));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(hx, y + 2, hx, y + h - 2);
                g.setStroke(new BasicStroke(1f));
            }
        }
        g.setColor(new Color(45, 52, 78));
        g.drawRoundRect(x, y, w, h, 10, 10);
    }

    private static Color brighten(Color c, double f) {
        return new Color(c.getRed() + (int) ((255 - c.getRed()) * f), c.getGreen() + (int) ((255 - c.getGreen()) * f),
                c.getBlue() + (int) ((255 - c.getBlue()) * f));
    }

    private static Color darken(Color c, double keep) {
        return new Color((int) (c.getRed() * keep), (int) (c.getGreen() * keep), (int) (c.getBlue() * keep));
    }

    private static void centerText(Graphics2D g, String s, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }
}
