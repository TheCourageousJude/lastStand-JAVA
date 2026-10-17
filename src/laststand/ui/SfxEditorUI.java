package laststand.ui;

import laststand.core.Constants;
import laststand.core.InputHandler;
import laststand.fx.Sfx;
import laststand.fx.SfxPatch;
import laststand.fx.SfxPatch.Tone;
import laststand.fx.WorkshopTune;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The in-game SFX Editor: open it from the Sound Test (Ctrl+S, select any sound effect, press E or click EDIT SOUND).
 *
 * Every sound effect is a short stack of "tones" -- layers of square / triangle / saw / noise / air, each with a start
 * time, length, pitch slide, volume and so on. The list on the left is those layers; the panel on the right edits the
 * selected one (click - and + or hold them to repeat). The wave strip on top shows exactly what will play, and the
 * selected layer's stretch of time is highlighted. PLAY (Space) plays the unsaved sound; AUTO-PLAY plays it after every change.
 *
 * Nothing changes in the game until you SAVE (Ctrl+S): that writes laststand_sfx.txt, and from then on the whole game
 * plays your version of that sound (hits, shots, shop, menus ...). DISCARD goes back to the last saved version,
 * RESET TO ORIGINAL puts the built-in sound back (saving that removes the edit), Ctrl+Z undoes, and leaving with unsaved
 * edits asks first.
 */
public final class SfxEditorUI implements SoundEditor {

    private static final Color PINK = new Color(255, 110, 185), GOLD = new Color(255, 214, 90), GREEN = new Color(45, 195, 135);
    private static final Color RED = new Color(235, 75, 75), BLUE = new Color(70, 140, 255), GRAY = new Color(70, 76, 100);
    private static final Color PANEL = new Color(24, 26, 38), TEXT = new Color(225, 225, 235), MUTED = new Color(150, 155, 175);
    private static final Color[] WAVE_COLORS = {new Color(255, 110, 185), new Color(70, 140, 255), new Color(255, 170, 40),
            new Color(165, 95, 255), new Color(45, 195, 135), new Color(110, 220, 235)};

    private static final int WAVE_X = 50, WAVE_Y = 88, WAVE_W = 888, WAVE_H = 104;
    private static final int LIST_X = 50, LIST_Y = 236, LIST_W = 280, ROW_H = 28, LIST_ROWS = 10;
    private static final int PARAM_X = 350, PARAM_Y = 240, PARAM_ROW_H = 29;
    private static final double[] DUTIES = {0.125, 0.25, 0.5, 0.75};

    private record Btn(Rectangle r, String label, Color color, boolean enabled, boolean active, boolean repeat, boolean edits, Runnable act) {}

    /** One editable setting of the selected tone. */
    private record Param(String label, Predicate<Tone> applies, Function<Tone, String> show, java.util.function.BiConsumer<Tone, Integer> change) {}

    private final Sfx.Id id;
    private final Runnable onSaved;
    private final String label;
    private SfxPatch p;
    private String savedText;
    private final String builtinText;
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private int sel = 0, listTop = 0;

    private boolean autoPlay = true;
    private boolean confirm = false, closeRequested = false;
    private int mouseX = -1, mouseY = -1;
    private boolean mouseDown = false;
    private Btn heldBtn;
    private String heldBefore;
    private long nextRepeat;
    private String toast = null;
    private Color toastColor = GREEN;
    private long toastUntil = 0;

    private String waveText = null;
    private short[] wave = null;
    private String waveError = null;

    public SfxEditorUI(Sfx.Id id, Runnable onSaved) {
        this.id = id;
        this.onSaved = onSaved;
        this.label = id.name().replace('_', ' ');
        SfxPatch builtin = Sfx.builtinPatch(id);
        builtinText = builtin.toBody();
        SfxPatch ov = laststand.fx.SfxPatch.overrideFor(id.name());
        p = ov != null ? ov.copy() : builtin;
        savedText = p.toBody();
    }

    public boolean isDirty() { return !p.toBody().equals(savedText); }

    // ================================================================== the settings of one tone

    private static double r(double v, int places) { double m = Math.pow(10, places); return Math.round(v * m) / m; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String hz(double f) {
        return f >= 1000 ? String.format("%.0f Hz", f) : String.format("%.1f Hz", f).replace(".0 Hz", " Hz");
    }

    private static String hzWithNote(Tone t, double f) {
        if (t.isNoise() || t.wave.equals("AIR")) return hz(f);
        int midi = (int) Math.round(69 + 12 * Math.log(f / 440.0) / Math.log(2));
        return hz(f) + "  \u2248 " + WorkshopTune.noteName(midi);
    }

    private static double semitones(double f, int dir) { return r(clamp(f * Math.pow(2, dir / 12.0), 20, 16000), 1); }

    private static final List<Param> PARAMS = List.of(
            new Param("WAVE", t -> true, t -> t.wave.replace('_', ' '), (t, d) -> {
                int i = 0;
                for (int k = 0; k < SfxPatch.WAVES.length; k++) if (SfxPatch.WAVES[k].equals(t.wave)) i = k;
                t.wave = SfxPatch.WAVES[Math.floorMod(i + d, SfxPatch.WAVES.length)];
            }),
            new Param("STARTS AT", t -> true, t -> String.format("%.0f ms", t.at * 1000), (t, d) -> t.at = r(clamp(t.at + d * 0.005, 0, 5), 3)),
            new Param("LENGTH", t -> true, t -> String.format("%.0f ms", t.dur * 1000), (t, d) -> t.dur = r(clamp(t.dur + d * 0.005, 0.005, 5), 3)),
            new Param("PITCH AT START", t -> !t.isNoise() && !t.wave.equals("AIR"), t -> hzWithNote(t, t.f0), (t, d) -> t.f0 = semitones(t.f0, d)),
            new Param("PITCH AT END", t -> !t.isNoise() && !t.wave.equals("AIR"), t -> hzWithNote(t, t.f1), (t, d) -> t.f1 = semitones(t.f1, d)),
            new Param("HISS RATE AT START", Tone::isNoise, t -> hz(t.f0) + (t.f0 > 6000 ? "  (bright)" : t.f0 < 1200 ? "  (rumble)" : ""), (t, d) -> t.f0 = semitones(t.f0, d * 2)),
            new Param("HISS RATE AT END", Tone::isNoise, t -> hz(t.f1) + (t.f1 > 6000 ? "  (bright)" : t.f1 < 1200 ? "  (rumble)" : ""), (t, d) -> t.f1 = semitones(t.f1, d * 2)),
            new Param("AIR CENTRE AT START", t -> t.wave.equals("AIR"), t -> hz(t.f0), (t, d) -> t.f0 = semitones(t.f0, d * 2)),
            new Param("AIR CENTRE AT END", t -> t.wave.equals("AIR"), t -> hz(t.f1), (t, d) -> t.f1 = semitones(t.f1, d * 2)),
            new Param("VOLUME", t -> true, t -> String.format("%.0f %%", t.vol * 100), (t, d) -> t.vol = r(clamp(t.vol + d * 0.01, 0, 1), 2)),
            new Param("PULSE WIDTH", t -> t.wave.equals("SQUARE"), t -> String.format("%.1f %%", t.duty * 100) + (t.duty < 0.2 ? "  (thin)" : t.duty >= 0.5 ? "  (full)" : ""), (t, d) -> {
                int i = 0;
                for (int k = 0; k < DUTIES.length; k++) if (Math.abs(DUTIES[k] - t.duty) < Math.abs(DUTIES[i] - t.duty)) i = k;
                t.duty = DUTIES[(int) clamp(i + d, 0, DUTIES.length - 1)];
            }),
            new Param("FADE-OUT SPEED", t -> true, t -> String.format("%.1f", t.decay), (t, d) -> t.decay = r(clamp(t.decay + d * 0.1, 0.1, 6), 1)),
            new Param("FADE-IN", t -> true, t -> String.format("%.0f ms", t.attack * 1000), (t, d) -> t.attack = r(clamp(t.attack + d * 0.001, 0.001, 0.1), 3)),
            new Param("VIBRATO SPEED", t -> true, t -> t.vibHz == 0 ? "off" : String.format("%.0f Hz", t.vibHz), (t, d) -> t.vibHz = clamp(Math.round(t.vibHz + d), 0, 40)),
            new Param("VIBRATO DEPTH", t -> t.vibHz > 0, t -> String.format("%.1f %%", t.vibDepth * 100), (t, d) -> t.vibDepth = r(clamp(t.vibDepth + d * 0.005, 0, 0.3), 3)),
            new Param("BAND SHARPNESS", t -> t.wave.equals("AIR"), t -> String.format("%.1f", t.q) + (t.q < 1 ? "  (sandy)" : t.q > 4 ? "  (whistly)" : "  (whoosh)"), (t, d) -> t.q = r(clamp(t.q + d * 0.1, 0.3, 12), 1))
    );

    private List<Param> activeParams() {
        List<Param> out = new ArrayList<>();
        if (p.tones.isEmpty()) return out;
        Tone t = p.tones.get(sel);
        for (Param pr : PARAMS) if (pr.applies().test(t)) out.add(pr);
        return out;
    }

    // ================================================================== editing

    private void pushUndoIfChanged(String before) {
        if (before != null && !p.toBody().equals(before)) {
            undo.addFirst(before);
            while (undo.size() > 200) undo.removeLast();
        }
    }

    private void doUndo() {
        if (undo.isEmpty()) { toast("Nothing to undo", MUTED); return; }
        p = SfxPatch.fromBody(undo.removeFirst());
        clampSel();
    }

    private void clampSel() {
        sel = Math.max(0, Math.min(p.tones.size() - 1, sel));
        if (sel < listTop) listTop = sel;
        if (sel >= listTop + LIST_ROWS) listTop = sel - LIST_ROWS + 1;
        listTop = Math.max(0, Math.min(Math.max(0, p.tones.size() - LIST_ROWS), listTop));
    }

    private void toast(String msg, Color c) { toast = msg; toastColor = c; toastUntil = System.currentTimeMillis() + 3500; }

    private boolean save() {
        try {
            if (p.toBody().equals(builtinText)) SfxPatch.remove(id.name());       // same as the original: no edit to keep
            else SfxPatch.store(id.name(), p);
            Sfx.reloadSfx(id);
            savedText = p.toBody();
            if (onSaved != null) onSaved.run();
            toast(p.toBody().equals(builtinText) ? "Saved: this is the original sound again." : "Saved. The game now plays your version of this sound.", GREEN);
            return true;
        } catch (IOException | RuntimeException e) {
            toast("Couldn't save: " + e.getMessage(), RED);
            return false;
        }
    }

    @Override public void requestSave() {
        if (confirm) return;
        if (isDirty()) save(); else toast("Nothing to save: no changes since the last save", MUTED);
    }

    private void discard() {
        if (!isDirty()) return;
        undo.addFirst(p.toBody());
        p = SfxPatch.fromBody(savedText);
        clampSel();
        toast("Changes discarded: back to the last saved version (Ctrl+Z brings them back)", GOLD);
    }

    private void requestClose() { if (isDirty()) confirm = true; else closeRequested = true; }

    private void play() { Sfx.previewPatch(p); }

    private void resetToOriginal() {
        p = SfxPatch.fromBody(builtinText);
        clampSel();
    }

    private void addTone() {
        if (p.tones.size() >= SfxPatch.MAX_TONES) return;
        p.tones.add(new Tone());
        sel = p.tones.size() - 1;
        clampSel();
    }

    private void duplicateTone() {
        if (p.tones.isEmpty() || p.tones.size() >= SfxPatch.MAX_TONES) return;
        Tone c = p.tones.get(sel).copy();
        c.at = r(c.at + c.dur, 3);
        p.tones.add(sel + 1, c);
        sel++;
        clampSel();
    }

    private void deleteTone() {
        if (p.tones.isEmpty()) return;
        p.tones.remove(sel);
        clampSel();
    }

    // ================================================================== keyboard

    @Override public boolean tick(InputHandler in) {
        long now = System.currentTimeMillis();
        if (closeRequested) { Sfx.stopPreview(); return true; }

        if (heldBtn != null) {
            if (!mouseDown) heldBtn = null;
            else if (now >= nextRepeat) { heldBtn.act().run(); nextRepeat = now + 70; }
        }

        if (confirm) {
            if (pressed(in, KeyEvent.VK_ESCAPE)) confirm = false;
            if (pressed(in, KeyEvent.VK_Y) || pressed(in, KeyEvent.VK_ENTER)) { if (save()) closeRequested = true; }
            if (pressed(in, KeyEvent.VK_N)) closeRequested = true;
            if (closeRequested) { Sfx.stopPreview(); return true; }
            return false;
        }

        boolean ctrl = in.isHeld(KeyEvent.VK_CONTROL);
        if (pressed(in, KeyEvent.VK_ESCAPE)) requestClose();
        if (pressed(in, KeyEvent.VK_SPACE)) play();
        if (pressed(in, KeyEvent.VK_Z) && ctrl) doUndo();
        if (pressed(in, KeyEvent.VK_DELETE) || pressed(in, KeyEvent.VK_BACK_SPACE)) {
            String before = p.toBody(); deleteTone(); pushUndoIfChanged(before);
        }
        if (pressed(in, KeyEvent.VK_UP)) { sel--; clampSel(); }
        if (pressed(in, KeyEvent.VK_DOWN)) { sel++; clampSel(); }
        if (closeRequested) { Sfx.stopPreview(); return true; }
        return false;
    }

    private boolean pressed(InputHandler in, int key) {
        if (in.wasJustPressed(key)) { in.consume(key); return true; }
        return false;
    }

    // ================================================================== mouse

    @Override public void hover(int mx, int my) { mouseX = mx; mouseY = my; }

    @Override public void press(int mx, int my, int button) {
        mouseX = mx; mouseY = my; mouseDown = true;
        for (Btn b : buttons()) {
            if (!b.r().contains(mx, my)) continue;
            if (!b.enabled()) return;
            String before = p.toBody();
            b.act().run();
            if (b.repeat()) { heldBtn = b; heldBefore = before; nextRepeat = System.currentTimeMillis() + 420; }
            else if (b.edits()) { pushUndoIfChanged(before); if (autoPlay && !p.tones.isEmpty()) play(); }
            return;
        }
        if (confirm) return;
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = listTop + i;
            if (idx < p.tones.size() && new Rectangle(LIST_X, LIST_Y + i * ROW_H, LIST_W, ROW_H).contains(mx, my)) { sel = idx; return; }
        }
    }

    @Override public void release(int mx, int my) {
        mouseDown = false;
        if (heldBtn != null) {
            pushUndoIfChanged(heldBefore);
            if (autoPlay && !p.tones.isEmpty()) play();
        }
        heldBtn = null;
        heldBefore = null;
    }

    @Override public void drag(int mx, int my) { mouseX = mx; mouseY = my; }

    @Override public void wheel(int mx, int my, int rotation, boolean shift) {
        if (mx >= LIST_X && mx < LIST_X + LIST_W) { listTop += rotation; clampSel2(); }
    }

    private void clampSel2() { listTop = Math.max(0, Math.min(Math.max(0, p.tones.size() - LIST_ROWS), listTop)); }

    // ================================================================== buttons

    private List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        if (confirm) {
            l.add(new Btn(new Rectangle(268, 384, 148, 34), "SAVE & EXIT", GREEN, true, false, false, false, () -> { if (save()) closeRequested = true; }));
            l.add(new Btn(new Rectangle(426, 384, 148, 34), "DISCARD & EXIT", RED, true, false, false, false, () -> closeRequested = true));
            l.add(new Btn(new Rectangle(584, 384, 148, 34), "KEEP EDITING", GRAY, true, false, false, false, () -> confirm = false));
            return l;
        }
        boolean dirty = isDirty();
        l.add(new Btn(new Rectangle(530, 30, 104, 28), "\u25B6 PLAY", BLUE, !p.tones.isEmpty(), false, false, false, this::play));
        l.add(new Btn(new Rectangle(642, 30, 96, 28), dirty ? "SAVE" : "SAVED", GREEN, dirty, false, false, false, this::save));
        l.add(new Btn(new Rectangle(746, 30, 100, 28), "DISCARD", RED, dirty, false, false, false, this::discard));
        l.add(new Btn(new Rectangle(854, 30, 84, 28), "BACK", GRAY, true, false, false, false, this::requestClose));

        l.add(new Btn(new Rectangle(50, 548, 80, 28), "ADD TONE", PINK, p.tones.size() < SfxPatch.MAX_TONES, false, false, true, this::addTone));
        l.add(new Btn(new Rectangle(136, 548, 102, 28), "DUPLICATE", GRAY, !p.tones.isEmpty() && p.tones.size() < SfxPatch.MAX_TONES, false, false, true, this::duplicateTone));
        l.add(new Btn(new Rectangle(244, 548, 86, 28), "DELETE", GRAY, !p.tones.isEmpty(), false, false, true, this::deleteTone));

        if (!p.tones.isEmpty()) {
            Tone t = p.tones.get(sel);
            List<Param> params = activeParams();
            for (int i = 0; i < params.size(); i++) {
                Param pr = params.get(i);
                int y = PARAM_Y + i * PARAM_ROW_H;
                l.add(new Btn(new Rectangle(PARAM_X + 200, y, 26, 24), "\u2212", GRAY, true, false, true, true, () -> pr.change().accept(t, -1)));
                l.add(new Btn(new Rectangle(PARAM_X + 200 + 26 + 188, y, 26, 24), "+", GRAY, true, false, true, true, () -> pr.change().accept(t, 1)));
            }
        }

        l.add(new Btn(new Rectangle(350, 604, 170, 28), "8-BIT CRUNCH: " + (p.smooth ? "OFF" : "ON"), GREEN, true, !p.smooth, false, true, () -> p.smooth = !p.smooth));
        l.add(new Btn(new Rectangle(528, 604, 140, 28), "AUTO-PLAY: " + (autoPlay ? "ON" : "OFF"), BLUE, true, autoPlay, false, false, () -> autoPlay = !autoPlay));
        l.add(new Btn(new Rectangle(676, 604, 80, 28), "UNDO", GRAY, !undo.isEmpty(), false, false, false, this::doUndo));
        l.add(new Btn(new Rectangle(764, 604, 174, 28), "RESET TO ORIGINAL", GRAY, !p.toBody().equals(builtinText), false, false, true, this::resetToOriginal));
        return l;
    }

    // ================================================================== drawing

    private void refreshWave() {
        String text = p.toBody();
        if (text.equals(waveText)) return;
        waveText = text;
        try { wave = p.tones.isEmpty() ? new short[0] : Sfx.renderPatch(p); waveError = null; }
        catch (RuntimeException e) { wave = null; waveError = e.getMessage(); }
    }

    @Override public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long now = System.currentTimeMillis();
        boolean dirty = isDirty();
        refreshWave();

        g.setColor(GOLD);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString("SFX EDITOR", 50, 52);
        int pillX = 50 + g.getFontMetrics().stringWidth("SFX EDITOR") + 14;
        g.setColor(dirty ? new Color(255, 170, 40) : GREEN);
        g.fillRoundRect(pillX, 32, 92, 22, 22, 22);
        g.setColor(new Color(20, 22, 30));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        centerText(g, dirty ? "UNSAVED" : "SAVED", pillX + 46, 47);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        if (toast != null && now < toastUntil) { g.setColor(toastColor); g.drawString(toast, 50, 74); }
        else {
            g.setColor(MUTED);
            double ms = wave == null ? 0 : wave.length * 1000.0 / Sfx.SAMPLE_RATE;
            String state = Sfx.hasSfxEdit(id) ? "edited" : "original";
            g.drawString(label + "  \u2022  " + p.tones.size() + " tone" + (p.tones.size() == 1 ? "" : "s") + "  \u2022  " + String.format("%.0f ms", ms) + "  \u2022  saved sound: " + state, 50, 74);
        }

        drawWavePanel(g);
        drawList(g);
        drawParams(g);
        for (Btn b : buttons()) drawBtn(g, b);

        if (Sfx.isRecordedOverride(id)) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(255, 170, 40));
            g.drawString("Note: a WAV file in src/sounds replaces this sound in the game, so edits here won't be heard there until that file is removed.", 50, 660);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(170, 170, 185));
        centerText(g, "Each tone is one layer of the sound. Pick a tone on the left, change it on the right (hold - / + to repeat). Space plays the sound as it is now.",
                Constants.SCREEN_WIDTH / 2, 698);
        centerText(g, "Space: play   Ctrl+S: save   Ctrl+Z: undo   Up/Down: pick tone   Del: delete tone   Esc: back   (Save changes the sound everywhere in the game)",
                Constants.SCREEN_WIDTH / 2, 716);

        if (confirm) drawConfirm(g);
    }

    private void drawWavePanel(Graphics2D g) {
        g.setColor(new Color(12, 14, 22));
        g.fillRoundRect(WAVE_X, WAVE_Y, WAVE_W, WAVE_H, 10, 10);
        g.setColor(new Color(45, 52, 78));
        g.drawLine(WAVE_X, WAVE_Y + WAVE_H / 2, WAVE_X + WAVE_W, WAVE_Y + WAVE_H / 2);
        if (waveError != null) {
            g.setColor(new Color(255, 120, 110));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(waveError, WAVE_X + 14, WAVE_Y + WAVE_H / 2 + 5);
        } else if (wave == null || wave.length == 0) {
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("No tones: this sound is silent. Click ADD TONE.", WAVE_X + 14, WAVE_Y + WAVE_H / 2 + 5);
        } else {
            double totalSec = wave.length / (double) Sfx.SAMPLE_RATE;
            if (!p.tones.isEmpty() && sel < p.tones.size()) {                       // the selected tone's stretch of time
                Tone t = p.tones.get(sel);
                int x0 = WAVE_X + (int) Math.round(t.at / totalSec * WAVE_W), x1 = WAVE_X + (int) Math.round(Math.min(totalSec, t.at + t.dur) / totalSec * WAVE_W);
                g.setColor(new Color(255, 110, 185, 55));
                g.fillRect(x0, WAVE_Y + 2, Math.max(2, x1 - x0), WAVE_H - 4);
            }
            double perPx = wave.length / (double) WAVE_W;
            g.setColor(new Color(110, 200, 255));
            for (int px = 0; px < WAVE_W; px++) {
                int a = (int) (px * perPx), b = Math.max(a + 1, (int) ((px + 1) * perPx));
                int lo = 0, hi = 0;
                for (int i = a; i < Math.min(b, wave.length); i++) { lo = Math.min(lo, wave[i]); hi = Math.max(hi, wave[i]); }
                int y1 = WAVE_Y + WAVE_H / 2 - (int) (hi / 32768.0 * (WAVE_H / 2 - 4)), y2 = WAVE_Y + WAVE_H / 2 - (int) (lo / 32768.0 * (WAVE_H / 2 - 4));
                g.drawLine(WAVE_X + px, y1, WAVE_X + px, Math.max(y1, y2));
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));                          // time marks
            g.setColor(new Color(110, 116, 140));
            double stepMs = totalSec > 0.6 ? 100 : totalSec > 0.25 ? 50 : 20;
            for (double ms = stepMs; ms < totalSec * 1000; ms += stepMs) {
                int x = WAVE_X + (int) Math.round(ms / 1000.0 / totalSec * WAVE_W);
                g.drawLine(x, WAVE_Y + WAVE_H - 8, x, WAVE_Y + WAVE_H - 3);
                g.drawString((int) ms + "", x + 2, WAVE_Y + WAVE_H - 4);
            }
            double secs = Sfx.previewSeconds();
            if (secs >= 0 && secs <= totalSec) {
                int px = WAVE_X + (int) Math.round(secs / totalSec * (WAVE_W - 1));
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(px, WAVE_Y + 2, px, WAVE_Y + WAVE_H - 2);
                g.setStroke(new BasicStroke(1f));
            }
        }
        g.setColor(new Color(45, 52, 78));
        g.drawRoundRect(WAVE_X, WAVE_Y, WAVE_W, WAVE_H, 10, 10);
    }

    private void drawList(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(MUTED);
        g.drawString("TONES  (layers of the sound)", LIST_X, 224);
        g.setColor(PANEL);
        g.fillRoundRect(LIST_X - 4, LIST_Y - 4, LIST_W + 8, LIST_ROWS * ROW_H + 8, 10, 10);
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = listTop + i;
            if (idx >= p.tones.size()) break;
            Tone t = p.tones.get(idx);
            int y = LIST_Y + i * ROW_H;
            boolean s = idx == sel, hov = new Rectangle(LIST_X, y, LIST_W, ROW_H).contains(mouseX, mouseY);
            if (s) { g.setColor(new Color(255, 110, 185, 60)); g.fillRoundRect(LIST_X, y + 1, LIST_W, ROW_H - 2, 8, 8); g.setColor(PINK); g.drawRoundRect(LIST_X, y + 1, LIST_W, ROW_H - 2, 8, 8); }
            else if (hov) { g.setColor(new Color(255, 255, 255, 18)); g.fillRoundRect(LIST_X, y + 1, LIST_W, ROW_H - 2, 8, 8); }
            int wi = 0;
            for (int k = 0; k < SfxPatch.WAVES.length; k++) if (SfxPatch.WAVES[k].equals(t.wave)) wi = k;
            g.setColor(WAVE_COLORS[wi]);
            g.fillOval(LIST_X + 10, y + 9, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(s ? Color.WHITE : TEXT);
            g.drawString((idx + 1) + "  " + t.wave.replace('_', ' '), LIST_X + 28, y + 18);
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g.setColor(MUTED);
            String info = String.format("%.0f+%.0f ms", t.at * 1000, t.dur * 1000);
            g.drawString(info, LIST_X + LIST_W - 8 - g.getFontMetrics().stringWidth(info), y + 18);
        }
        if (p.tones.size() > LIST_ROWS) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(MUTED);
            g.drawString((listTop + 1) + "-" + Math.min(p.tones.size(), listTop + LIST_ROWS) + " of " + p.tones.size() + "  (wheel scrolls)", LIST_X, LIST_Y + LIST_ROWS * ROW_H + 18);
        }
    }

    private void drawParams(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(MUTED);
        g.drawString(p.tones.isEmpty() ? "TONE SETTINGS" : "TONE " + (sel + 1) + " SETTINGS", PARAM_X, 224);
        g.setColor(PANEL);
        g.fillRoundRect(PARAM_X - 4, PARAM_Y - 8, 592, 16 * PARAM_ROW_H + 12 - 80, 10, 10);
        if (p.tones.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("This sound has no tones yet. Click ADD TONE to start one.", PARAM_X + 16, PARAM_Y + 30);
            return;
        }
        Tone t = p.tones.get(sel);
        List<Param> params = activeParams();
        for (int i = 0; i < params.size(); i++) {
            Param pr = params.get(i);
            int y = PARAM_Y + i * PARAM_ROW_H;
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(MUTED);
            g.drawString(pr.label(), PARAM_X + 12, y + 16);
            int bx = PARAM_X + 200 + 26;
            g.setColor(new Color(14, 16, 26));
            g.fillRect(bx, y, 188, 24);
            g.setColor(new Color(60, 66, 92));
            g.drawRect(bx, y, 188, 24);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            centerText(g, pr.show().apply(t), bx + 94, y + 16);
        }
    }

    private void drawBtn(Graphics2D g, Btn b) {
        Rectangle r = b.r();
        boolean hov = b.enabled() && r.contains(mouseX, mouseY);
        Color base;
        if (!b.enabled()) base = new Color(38, 42, 58);
        else if (b.active()) base = b.color();
        else if (b.color() == GRAY || b.color() == PINK && false) base = GRAY;
        else base = b.color() == BLUE || b.color() == GREEN && b.label().startsWith("8-BIT") ? new Color(50, 56, 78) : b.color();
        if (hov) base = new Color(Math.min(255, base.getRed() + 35), Math.min(255, base.getGreen() + 35), Math.min(255, base.getBlue() + 35));
        g.setColor(base);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        boolean darkText = b.enabled() && (b.active() || (base != GRAY && !(base.getRed() < 90 && base.getGreen() < 100)));
        g.setColor(!b.enabled() ? new Color(105, 110, 130) : darkText ? new Color(15, 18, 26) : new Color(225, 228, 240));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        centerText(g, b.label(), r.x + r.width / 2, r.y + r.height / 2 + 4);
    }

    private void drawConfirm(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, Constants.SCREEN_WIDTH, 740);
        g.setColor(new Color(30, 33, 48));
        g.fillRoundRect(250, 270, 500, 170, 18, 18);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(250, 270, 500, 170, 18, 18);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        centerText(g, "You have unsaved changes", 500, 314);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(TEXT);
        centerText(g, "Save them to this sound before leaving, or throw them away?", 500, 346);
        g.setColor(MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        centerText(g, "Y: save & exit    N: discard & exit    Esc: keep editing", 500, 432);
        for (Btn b : buttons()) drawBtn(g, b);
    }

    private static void centerText(Graphics2D g, String s, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }
}
