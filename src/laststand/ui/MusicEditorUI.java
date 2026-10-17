package laststand.ui;

import laststand.core.Constants;
import laststand.core.InputHandler;
import laststand.fx.Sfx;
import laststand.fx.WorkshopTune;
import laststand.fx.WorkshopTune.Note;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The in-game Music Editor for the Sound Test's second hidden track ("??? 2"). Open it from the Sound Test
 * (Ctrl+S, select "??? 2", press E or click EDIT).
 *
 * It's a piano roll: the melody is a row of notes on a 16th-note grid (click an empty cell to add a note, drag to
 * stretch it, drag a note to move it, drag its right edge to resize, right-click to remove). Under the roll there's
 * one chord per bar (the bass and the arpeggio follow it), and below that the tempo, the number of bars, and the bars
 * where the drums and the fuller melody sound come in. PLAY auditions the tune you're editing, unsaved.
 *
 * Nothing touches the game until you SAVE (Ctrl+S) -- that writes laststand_workshop_track.txt, and "??? 2" on the
 * Sound Test plays it from then on. DISCARD throws your edits away and goes back to the last saved version; leaving
 * with unsaved edits asks first. Ctrl+Z undoes (even a discard).
 *
 * The melody is one voice, like a real NES square channel: notes can't overlap. A note can run across bar lines as one long block
 * (the saved file cuts it at each bar line and ties the pieces with "T").
 */
public final class MusicEditorUI implements SoundEditor {

    // ---- layout (design space: 1000 x 740) ----
    private static final int PIANO_X = 50, ROLL_X = 106, ROLL_Y = 150, ROW_H = 14, ROWS = 21, COL_W = 13;
    private static final int BARS_SHOWN = 4, STEPS = WorkshopTune.STEPS_PER_BAR;
    private static final int ROLL_W = BARS_SHOWN * STEPS * COL_W, ROLL_H = ROWS * ROW_H;
    private static final int BAR_W = STEPS * COL_W;
    private static final int MAP_X = ROLL_X, MAP_Y = 88, MAP_W = ROLL_W, MAP_H = 30;
    private static final int CHORD_Y = ROLL_Y + ROLL_H + 6, CHORD_H = 30;              // chord row, right under the grid
    private static final int FLAG_Y = CHORD_Y + CHORD_H + 5;                            // the "DRUMS IN" / "FULL SOUND" flags
    private static final int BEAT_Y = FLAG_Y + 19, BEAT_H = 26;                         // the drum pattern of each bar
    private static final int VOCAL_Y = BEAT_Y + BEAT_H + 4, VOCAL_H = 26;              // the lead sound ("vocal") of each bar

    private static final Color PINK = new Color(255, 110, 185), GOLD = new Color(255, 214, 90), GREEN = new Color(45, 195, 135);
    private static final Color RED = new Color(235, 75, 75), BLUE = new Color(70, 140, 255), GRAY = new Color(70, 76, 100);
    private static final Color PANEL = new Color(24, 26, 38), TEXT = new Color(225, 225, 235), MUTED = new Color(150, 155, 175);

    private static final int[] LENGTHS = {1, 2, 4, 8, 16};
    private static final String[] LENGTH_NAMES = {"1/16", "1/8", "1/4", "1/2", "BAR"};

    private enum Drag { NONE, CREATE, MOVE, RESIZE, MINIMAP, GROUP_MOVE, BOX, BARS, RANGE_MOVE, MAPSEL }

    private record Btn(Rectangle r, String label, Color color, boolean enabled, boolean active, boolean repeat, Runnable act) {}

    // ---- the tune being edited ----
    private WorkshopTune t;
    private String savedText;                                        // the tune as of the last save (or when the editor opened)
    private final ArrayDeque<String> undo = new ArrayDeque<>();      // earlier versions, newest first
    private final Runnable onSaved;
    private Note sel;                                                    // the main selected note (always one of `group`, or null)
    private final LinkedHashSet<Note> group = new LinkedHashSet<>();     // every selected note: Shift+click / Shift+drag / Ctrl+A add to it
    private boolean shiftHeld, ctrlHeld;                                 // refreshed every frame (mouse events don't carry them)
    private final Map<Note, int[]> groupOrig = new HashMap<>();         // where each selected note was when a group drag began: start, len, midi
    private int gMinStart, gMaxStart, gMinMidi, gMaxMidi;                // the group's extremes then, so it can't be dragged off the edge
    private int boxX0, boxY0;                                            // where a Shift+drag selection box began
    private Rectangle box;
    private boolean followChords = true;                                 // when the WHOLE tune is selected and moved, the chords go with it (F switches this)
    private int lastDS, lastDP;                                          // the final move of the group drag in progress (so the chords can follow on release)
    private static final Rectangle FOLLOW_PILL = new Rectangle(712, 566, 226, 22);
    private static final Rectangle TOOL_PILL = new Rectangle(166, 124, 140, 18);          // TOOL (V): DRAW / SELECT
    private static final Rectangle START_PILL = new Rectangle(790, 124, 148, 18);        // PLAY FROM: LEFT BAR / BAR 1

    // ---- view ----
    private int viewBar = 0, lowNote = 60;
    private int lenSteps = 4;                                        // length of newly added notes (16th steps)
    private int snapMode = 1;                                        // the grid notes snap to when placed / moved: 1, 2 or 4 sixteenths; 0 = AUTO (follows the note length, at most a beat)
    private static final int[] SNAPS = {1, 2, 4, 0};
    private boolean loop = false;

    // ---- mouse ----
    private int mouseX = -1, mouseY = -1;
    private boolean mouseDown = false;
    private Drag drag = Drag.NONE;
    private Note dragNote;
    private int dragGrab, dragOrigLen;
    private String dragBefore;
    private Btn heldBtn;
    private long nextRepeat;
    private int repeatCount = 0;                                        // how many repeats the held button has fired: the bar spinners speed up

    // ---- state ----
    private boolean confirm = false, closeRequested = false;
    private String toast = null;
    private Color toastColor = GREEN;
    private long toastUntil = 0;
    private final String[] chordBeforeNone = new String[WorkshopTune.MAX_BARS];   // what a bar's chord was before it was set to "none"
    private long pendingUntil = 0;                                   // PLAY pressed, the tune is still being rendered
    private int playBpm = 96, lastPlayBar = -1;
    // ---- bar selection: whole bars (notes AND their chords) that can be moved, duplicated, copied and trimmed ----
    private int barSelFrom = -1, barSelTo = -1;                          // the selected bars, first..last inclusive (-1 = none)
    private WorkshopTune.BarClip clip;                                   // Ctrl+C / Ctrl+X: bars + chords waiting to be pasted
    private boolean selectTool = false;                                  // V: a plain drag draws a bar-snapped selection box (Draw tool: it makes notes)
    private boolean boxBars;                                             // the selection box being dragged snaps to whole bars
    private int rangeAnchor, rangeDelta;                                 // while dragging bar numbers / moving the selected bars
    private boolean startFromView = true;                           // PLAY starts at the leftmost bar on screen (false = always bar 1)
    private int playStartBar = 0;                                   // PLAY starts from the leftmost bar on screen, not always bar 1

    public MusicEditorUI(Runnable onSaved) {
        this.onSaved = onSaved;
        t = WorkshopTune.loadOrDefault();
        savedText = t.toText();
        String problem = WorkshopTune.loadProblem();                      // say so, instead of quietly showing the starter tune
        if (problem != null) {
            toast(problem.length() > 118 ? problem.substring(0, 117) + "\u2026" : problem, problem.startsWith("Couldn't") ? RED : new Color(255, 200, 80));
            toastUntil = System.currentTimeMillis() + 20000;
        }
        // start the view where the music is
        if (!t.notes.isEmpty()) {
            int lo = 200, hi = 0;
            for (Note n : t.notes) { lo = Math.min(lo, n.midi); hi = Math.max(hi, n.midi); }
            setLowNote((lo + hi) / 2 - ROWS / 2);
        }
    }

    /** The exact check (serializes the whole tune: a few ms on a 360-bar song). Use this where it must be right. */
    private boolean isDirtyNow() { return !t.toText().equals(savedText); }

    // The SAVE button / title asterisk ask every frame, so the answer is remembered for 200 ms (and re-checked at once after any edit,
    // undo, discard or save) instead of re-serializing a huge tune 120 times a second.
    private long dirtyCheckedAt = -1;
    private boolean dirtyCache;
    private Object dirtyTuneSeen, dirtySavedSeen;

    public boolean isDirty() {
        long now = System.currentTimeMillis();
        if (dirtyTuneSeen != t || dirtySavedSeen != savedText || now - dirtyCheckedAt > 200) {
            dirtyCache = isDirtyNow();
            dirtyCheckedAt = now; dirtyTuneSeen = t; dirtySavedSeen = savedText;
        }
        return dirtyCache;
    }

    // ================================================================== editing

    /** Runs a change and records the previous version for Ctrl+Z (only if something actually changed). */
    private void edit(Runnable change) {
        dirtyCheckedAt = -1;
        String before = t.toText();
        change.run();
        t.normalize();
        syncSelection();
        pushUndoIfChanged(before);
    }

    /** After any change: forget selected notes that no longer exist, and keep `sel` pointing at one of the rest. */
    private void syncSelection() {
        if (hasBarSel()) { selectBars(barSelFrom, barSelTo); return; }            // bars stay selected: pick their notes up again
        barSelFrom = barSelTo = -1;
        group.retainAll(new HashSet<>(t.notes));
        if (sel == null || !group.contains(sel)) sel = group.isEmpty() ? null : group.iterator().next();
    }

    private void selectOnly(Note n) { barSelFrom = barSelTo = -1; group.clear(); group.add(n); sel = n; }

    private void clearSelection() { group.clear(); sel = null; barSelFrom = barSelTo = -1; }

    private void selectAll() {
        if (t.notes.isEmpty()) { toast("There are no notes to select", MUTED); return; }
        barSelFrom = barSelTo = -1;
        group.clear();
        group.addAll(t.notes);
        sel = group.iterator().next();
        toast("All " + group.size() + " notes selected: arrows move them together (Shift = octave / bar), Esc: deselect", GREEN);
    }

    private boolean allSelected() { return !t.notes.isEmpty() && group.size() == t.notes.size(); }

    private void toggleFollow() {
        followChords = !followChords;
        toast(followChords ? "Chords follow: ON (when ALL notes are selected and moved, the chords move with them)" : "Chords follow: OFF (the chords stay where they are)", MUTED);
    }

    /**
     * When the WHOLE tune is selected and moved, the chords go with it: moving up or down transposes every chord by the same
     * number of semitones, and moving by whole bars slides the chords along by that many bars (a bar left with nothing under
     * it becomes "none", and a chord pushed off the end is gone until Ctrl+Z). A part of the tune moved alone leaves the
     * chords where they are, since the notes left behind still need them. Returns a note for the toast, or "" if nothing moved.
     */
    private String followChordsWith(int dSteps, int dPitch) {
        if (!followChords || !allSelected()) return "";
        List<String> said = new ArrayList<>();
        if (dPitch != 0) {
            for (int i = 0; i < t.chords.size(); i++) t.chords.set(i, WorkshopTune.shiftChord(t.chords.get(i), dPitch));
            said.add((dPitch > 0 ? "up " : "down ") + Math.abs(dPitch) + " semitone" + (Math.abs(dPitch) == 1 ? "" : "s"));
        }
        if (dSteps != 0 && dSteps % STEPS == 0) {
            int k = dSteps / STEPS;
            List<String> old = new ArrayList<>(t.chords);
            for (int i = 0; i < old.size(); i++) { int src = i - k; t.chords.set(i, src >= 0 && src < old.size() ? old.get(src) : WorkshopTune.NO_CHORD); }
            said.add(Math.abs(k) + " bar" + (Math.abs(k) == 1 ? "" : "s") + (k > 0 ? " later" : " earlier"));
        }
        return said.isEmpty() ? "" : "The chords followed: " + String.join(", ", said) + "  (F turns this off, Ctrl+Z undoes)";
    }

    /** Ctrl+Up / Ctrl+Down: every chord up or down a semitone. (Chords and notes aren't tied together: this is how you move the chords too.) */
    private void shiftAllChords(int semitones) {
        edit(() -> { for (int i = 0; i < t.chords.size(); i++) t.chords.set(i, WorkshopTune.shiftChord(t.chords.get(i), semitones)); });
        toast("Every chord moved " + (semitones > 0 ? "up" : "down") + " a semitone (Ctrl+Z undoes it)", GREEN);
    }

    private void pushUndoIfChanged(String before) {
        if (before != null && !t.toText().equals(before)) {
            undo.addFirst(before);
            while (undo.size() > 200) undo.removeLast();
        }
    }

    private void doUndo() {
        if (undo.isEmpty()) { toast("Nothing to undo", MUTED); return; }
        t = WorkshopTune.fromText(undo.removeFirst());
        clearSelection();
        clampView();
    }

    private void toast(String msg, Color c) { toast = msg; toastColor = c; toastUntil = System.currentTimeMillis() + 3500; }

    /** Writes the tune to disk. Returns true on success. */
    private boolean save() {
        try {
            t.normalize();
            t.save();
            Sfx.reloadWorkshop();
            savedText = t.toText();
            if (onSaved != null) onSaved.run();
            toast("Saved. Play \"??? 2\" on the Sound Test to hear it.", GREEN);
            return true;
        } catch (IOException | RuntimeException e) {
            toast("Couldn't save: " + e.getMessage(), RED);
            return false;
        }
    }

    /** Called for Ctrl+S while the editor is open. */
    public void requestSave() {
        if (confirm) return;
        if (isDirtyNow()) save(); else toast("Nothing to save: no changes since the last save", MUTED);
    }

    private void discard() {
        if (!isDirtyNow()) return;
        undo.addFirst(t.toText());                      // so even a discard can be undone with Ctrl+Z
        t = WorkshopTune.fromText(savedText);
        clearSelection();
        clampView();
        toast("Changes discarded: back to the last saved version (Ctrl+Z brings them back)", GOLD);
    }

    private void requestClose() {
        if (isDirtyNow()) confirm = true; else closeRequested = true;
    }

    // ---- playback ----

    private boolean isPlaying() { return Sfx.previewSeconds() >= 0 || System.currentTimeMillis() < pendingUntil; }

    private void togglePlay() {
        if (isPlaying()) { Sfx.stopPreview(); pendingUntil = 0; return; }
        final int startBar = startFromView ? Math.max(0, Math.min(t.bars - 1, viewBar)) : 0;   // the leftmost bar you can see is where it starts
        if (Sfx.previewTune(t.startingAtBar(startBar), loop)) {
            pendingUntil = System.currentTimeMillis() + 4000 + t.bars * 80L; playBpm = t.bpm; lastPlayBar = -1; playStartBar = startBar;
            if (startBar > 0) toast("Playing from bar " + (startBar + 1) + " (the leftmost bar on screen). Switch PLAY FROM above the roll to BAR 1 to always start at the top", MUTED);
        }
        else toast("No sound: it's turned off (F9) or there's no audio device", RED);
    }

    // ================================================================== bar selection

    private long edgeSince, lastAutoScroll;

    /** While you drag bars (along the BAR numbers, with the select-tool box, or moving the selected bars), holding the mouse near the left or
     *  right edge of the grid -- or past it -- scrolls the song along, so a selection can reach as far as you like (bar 2 to bar 200...). */
    private void autoScrollDrag(long now) {
        boolean barDrag = drag == Drag.BARS || drag == Drag.RANGE_MOVE || (drag == Drag.BOX && boxBars);
        int dir = !barDrag || !mouseDown ? 0 : mouseX >= ROLL_X + ROLL_W - 40 ? 1 : mouseX <= ROLL_X + 40 ? -1 : 0;
        if (dir == 0) { edgeSince = 0; return; }
        if (edgeSince == 0) edgeSince = now;
        if (now - edgeSince < 250 || now - lastAutoScroll < 90) return;          // a short pause first, so a plain click at the edge doesn't scroll
        int before = viewBar;
        viewBar += dir;
        clampView();
        lastAutoScroll = now;
        if (viewBar != before) drag(mouseX, mouseY);                             // re-apply the drag with the new bars under the mouse
    }

    private boolean hasBarSel() { return barSelFrom >= 0 && barSelTo >= barSelFrom && barSelFrom < t.bars; }

    /** The bar under an x position (the four bars on screen only). */
    private int barAt(int mx) { return viewBar + Math.max(0, Math.min(BARS_SHOWN - 1, (mx - ROLL_X) / BAR_W)); }

    private String rangeText() {
        int n = barSelTo - barSelFrom + 1;
        return (n == 1 ? "Bar " + (barSelFrom + 1) : "Bars " + (barSelFrom + 1) + "-" + (barSelTo + 1)) + " (" + n + (n == 1 ? " bar" : " bars") + ")";
    }

    /** Selects whole bars first..last (inclusive). Every note that STARTS inside them joins the selection, and their chords go with them. */
    private void selectBars(int first, int last) {
        int lo = Math.max(0, Math.min(first, last)), hi = Math.min(t.bars - 1, Math.max(first, last));
        if (hi < lo) { clearSelection(); return; }
        barSelFrom = lo; barSelTo = hi;
        group.clear();
        for (Note n : t.notes) if (n.start >= lo * STEPS && n.start < (hi + 1) * STEPS) group.add(n);
        sel = group.isEmpty() ? null : group.iterator().next();
    }

    private void showBar(int bar) {
        if (bar < viewBar) viewBar = bar;
        else if (bar >= viewBar + BARS_SHOWN) viewBar = bar - BARS_SHOWN + 1;
        clampView();
    }

    private boolean needBarSel() {
        if (hasBarSel()) return true;
        toast("Select bars first: drag along the BAR numbers (or press V and drag over the notes)", MUTED);
        return false;
    }

    private void toggleTool() {
        selectTool = !selectTool;
        toast(selectTool ? "SELECT tool: drag over the notes to pick whole bars (V = back to drawing). Shift+drag = free box" : "DRAW tool: click makes notes", GOLD);
    }

    private void copyBarSel(boolean cut) {
        if (!needBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        final String what = rangeText();
        clip = t.copyBars(f, to);
        if (cut) {
            edit(() -> t.clearBars(f, to));
            selectBars(f, to);
            toast("Cut " + what + ", chords included: Ctrl+V pastes them at the leftmost bar", GREEN);
        } else toast("Copied " + what + " with their chords: Ctrl+V pastes them at the leftmost bar", GREEN);
    }

    /** Ctrl+V: pastes the copied bars (notes + chords) starting at the leftmost bar on screen, replacing what is there. */
    private void pasteBarClip() {
        if (clip == null) { toast("Nothing copied yet: select bars, then Ctrl+C", MUTED); return; }
        final int at = Math.min(viewBar, WorkshopTune.MAX_BARS - 1);
        final WorkshopTune.BarClip c = clip;
        final int[] done = {0};
        edit(() -> done[0] = t.pasteBars(c, at));
        if (done[0] <= 0) { toast("No room: the tune is at its " + WorkshopTune.MAX_BARS + "-bar limit", RED); return; }
        selectBars(at, at + done[0] - 1);
        toast("Pasted " + rangeText() + " at the leftmost bar, notes and chords (Ctrl+Z undoes)", GREEN);
    }

    /** Ctrl+D: a copy of the selected bars right after them (replacing what is there); the copy becomes the selection, so Ctrl+D again repeats. */
    private void duplicateBarSel() {
        if (!needBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        if (to + 1 >= WorkshopTune.MAX_BARS) { toast("No room: the tune is at its " + WorkshopTune.MAX_BARS + "-bar limit", RED); return; }
        final WorkshopTune.BarClip c = t.copyBars(f, to);
        final int[] done = {0};
        edit(() -> done[0] = t.pasteBars(c, to + 1));
        selectBars(to + 1, to + done[0]);
        showBar(to + 1);
        toast("Duplicated: " + rangeText() + " now selected, right after the original (Ctrl+D again repeats)", GREEN);
    }

    /** Moves the selected bars (notes + chords) by whole bars, leaving their old place empty. */
    private void moveBarSel(int delta) {
        if (!hasBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        final int d = Math.max(-f, Math.min(WorkshopTune.MAX_BARS - 1 - to, delta));
        if (d == 0) { toast("They can't go any further that way", MUTED); return; }
        edit(() -> t.moveBars(f, to, d));
        selectBars(f + d, to + d);
        showBar(f + d);
        toast("Moved to " + rangeText() + ", chords included (Ctrl+Z undoes)", GREEN);
    }

    /** CLEAR: empties the selected bars (notes gone, chords "none") but keeps the song the same length. */
    private void clearBarSel() {
        if (!needBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        final String what = rangeText();
        edit(() -> t.clearBars(f, to));
        selectBars(f, to);
        toast("Cleared " + what + " (Ctrl+Z undoes)", GOLD);
    }

    /** TRIM OUT / Ctrl+Del: takes the selected bars out of the song and closes the gap, so everything after them moves left. */
    private void removeBarSel() {
        if (!needBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        final String what = rangeText();
        edit(() -> t.removeBars(f, to));
        clearSelection();
        showBar(Math.min(f, t.bars - 1));
        toast("Trimmed out " + what + ": the rest moved left to close the gap (Ctrl+Z undoes)", GOLD);
    }

    /** CROP / Ctrl+K: keeps ONLY the selected bars; everything before and after them is trimmed away. */
    private void cropBarSel() {
        if (!needBarSel()) return;
        final int f = barSelFrom, to = barSelTo;
        final String what = rangeText();
        edit(() -> t.cropToBars(f, to));
        clearSelection();
        viewBar = 0;
        clampView();
        toast("Kept only " + what + "; everything else was trimmed away (Ctrl+Z undoes)", GOLD);
    }

    /** Highlights the selected bars over the bar numbers, the grid AND the chord row (the chords are part of the selection). */
    private void drawBarSelection(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));                             // (so you can tell which editor build you are running)
        g.setColor(new Color(90, 95, 115));
        String build = "editor: bar tools r3";
        g.drawString(build, 938 - g.getFontMetrics().stringWidth(build), 18);
        if (hasBarSel()) {
            drawBand(g, barSelFrom, barSelTo, new Color(255, 214, 90, 42), GOLD, false);
            int a = Math.max(barSelFrom, viewBar);
            if (a <= barSelTo && a < viewBar + BARS_SHOWN) {                          // a small tag at the top of the band
                String tag = rangeText();
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                int tx = ROLL_X + (a - viewBar) * BAR_W + 6, ty = ROLL_Y + 14;
                g.setColor(new Color(24, 26, 38, 200));
                g.fillRoundRect(tx - 3, ty - 12, g.getFontMetrics().stringWidth(tag) + 8, 16, 8, 8);
                g.setColor(GOLD);
                g.drawString(tag, tx + 1, ty);
            }
        }
        if (drag == Drag.RANGE_MOVE && rangeDelta != 0 && hasBarSel())
            drawBand(g, barSelFrom + rangeDelta, barSelTo + rangeDelta, new Color(70, 140, 255, 40), BLUE, true);   // where they will land
        if (drag == Drag.BOX && boxBars && box != null && (box.width > 4 || box.height > 4))
            drawBand(g, Math.min(rangeAnchor, barAt(mouseX)), Math.min(t.bars - 1, Math.max(rangeAnchor, barAt(mouseX))), new Color(255, 214, 90, 30), GOLD, true);
    }

    private void drawBand(Graphics2D g, int from, int to, Color fill, Color line, boolean dashed) {
        int a = Math.max(from, viewBar), b = Math.min(to, viewBar + BARS_SHOWN - 1);
        if (b < a) return;
        int x = ROLL_X + (a - viewBar) * BAR_W, w = (b - a + 1) * BAR_W;
        int y = ROLL_Y - 30, h = VOCAL_Y + VOCAL_H - y;
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        java.awt.Stroke old = g.getStroke();
        g.setStroke(dashed ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f) : new BasicStroke(2f));
        g.setColor(line);
        g.drawRect(x, y, w, h);
        g.setStroke(old);
    }

    private void drawToolPill(Graphics2D g) {
        Rectangle r = TOOL_PILL;
        g.setColor(selectTool ? new Color(255, 200, 70) : new Color(50, 56, 78));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
        g.setColor(selectTool ? new Color(50, 36, 6) : new Color(185, 190, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        centerText(g, "TOOL (V): " + (selectTool ? "SELECT" : "DRAW"), r.x + r.width / 2, r.y + 13);
    }

    // ---- view helpers ----

    private void clampView() {
        viewBar = Math.max(0, Math.min(Math.max(0, t.bars - BARS_SHOWN), viewBar));
        setLowNote(lowNote);
    }

    private void setLowNote(int n) { lowNote = Math.max(WorkshopTune.LOW_NOTE, Math.min(WorkshopTune.HIGH_NOTE - ROWS + 1, n)); }

    private int snapUnit() { return snapMode > 0 ? snapMode : Math.min(lenSteps, 4); }

    private String snapName() { return snapMode == 1 ? "1/16" : snapMode == 2 ? "1/8" : snapMode == 4 ? "1/4" : "AUTO"; }

    /** G / the SNAP button: 1/16 -> 1/8 -> 1/4 -> AUTO. The grid is separate from the note length, so any note can start on any step. */
    private void cycleSnap() {
        int i = 0;
        while (i < SNAPS.length - 1 && SNAPS[i] != snapMode) i++;
        snapMode = SNAPS[(i + 1) % SNAPS.length];
        toast(snapMode == 0 ? "Grid snap: AUTO (notes snap to their own length, at most a beat)" : "Grid snap: " + snapName() + " (any note can start every " + snapMode + " sixteenth" + (snapMode == 1 ? "" : "s") + ")", MUTED);
    }

    private int stepToX(int step) { return ROLL_X + (step - viewBar * STEPS) * COL_W; }

    private int xToStep(int mx) {
        int s = viewBar * STEPS + Math.floorDiv(mx - ROLL_X, COL_W);
        return Math.max(viewBar * STEPS, Math.min(viewBar * STEPS + BARS_SHOWN * STEPS - 1, s));
    }

    private int yToMidi(int my) {
        int row = Math.max(0, Math.min(ROWS - 1, (my - ROLL_Y) / ROW_H));
        return lowNote + (ROWS - 1 - row);
    }

    private int midiToY(int midi) { return ROLL_Y + (lowNote + ROWS - 1 - midi) * ROW_H; }

    private static boolean in(Rectangle r, int x, int y) { return r.contains(x, y); }

    // ================================================================== input: keyboard

    /** One frame of keyboard input. Returns true when the editor should close (back to the soundboard). */
    public boolean tick(InputHandler in) {
        long now = System.currentTimeMillis();
        if (closeRequested) { Sfx.stopPreview(); return true; }

        // held-down +/- buttons repeat
        if (heldBtn != null) {
            if (!mouseDown) heldBtn = null;
            else if (now >= nextRepeat) { repeatCount++; heldBtn.act().run(); nextRepeat = now + 70; }
        }

        autoScrollDrag(now);

        if (confirm) {
            if (pressed(in, KeyEvent.VK_ESCAPE)) confirm = false;
            if (pressed(in, KeyEvent.VK_Y) || pressed(in, KeyEvent.VK_ENTER)) { if (save()) closeRequested = true; }
            if (pressed(in, KeyEvent.VK_N)) { closeRequested = true; }
            return closeRequested && finish();
        }

        boolean ctrl = in.isHeld(KeyEvent.VK_CONTROL), shift = in.isHeld(KeyEvent.VK_SHIFT);
        ctrlHeld = ctrl; shiftHeld = shift;
        if (pressed(in, KeyEvent.VK_ESCAPE)) { if (!group.isEmpty() || hasBarSel()) clearSelection(); else requestClose(); }   // Esc: deselect first, then leave
        if (pressed(in, KeyEvent.VK_A) && ctrl) selectAll();
        if (ctrl && pressed(in, KeyEvent.VK_C)) copyBarSel(false);               // Ctrl+C / X / V / D: bars + chords
        if (ctrl && pressed(in, KeyEvent.VK_X)) copyBarSel(true);
        if (ctrl && pressed(in, KeyEvent.VK_V)) pasteBarClip();
        if (ctrl && pressed(in, KeyEvent.VK_D)) duplicateBarSel();
        if (ctrl && pressed(in, KeyEvent.VK_K)) cropBarSel();                    // Ctrl+K: keep only the selected bars
        if (!ctrl && pressed(in, KeyEvent.VK_V)) toggleTool();
        if (pressed(in, KeyEvent.VK_SPACE)) togglePlay();
        if (pressed(in, KeyEvent.VK_L)) loop = !loop;
        if (pressed(in, KeyEvent.VK_F)) toggleFollow();
        if (pressed(in, KeyEvent.VK_G)) cycleSnap();
        if (pressed(in, KeyEvent.VK_T)) toggleTie();
        if (pressed(in, KeyEvent.VK_Z) && ctrl) doUndo();
        if (pressed(in, KeyEvent.VK_DELETE) || pressed(in, KeyEvent.VK_BACK_SPACE)) { if (ctrl && hasBarSel()) removeBarSel(); else deleteSelected(); }   // Ctrl+Del: trim the bars out
        for (int i = 0; i < LENGTHS.length; i++) if (pressed(in, KeyEvent.VK_1 + i)) lenSteps = LENGTHS[i];
        if (pressed(in, KeyEvent.VK_PAGE_UP)) viewBar -= shift ? 16 : BARS_SHOWN;        // Shift = 16 bars at a time
        if (pressed(in, KeyEvent.VK_PAGE_DOWN)) viewBar += shift ? 16 : BARS_SHOWN;
        if (pressed(in, KeyEvent.VK_HOME)) viewBar = 0;
        if (pressed(in, KeyEvent.VK_END)) viewBar = t.bars;

        boolean up = pressed(in, KeyEvent.VK_UP), down = pressed(in, KeyEvent.VK_DOWN);
        boolean left = pressed(in, KeyEvent.VK_LEFT), right = pressed(in, KeyEvent.VK_RIGHT);
        if (ctrl && (up || down)) { shiftAllChords(up ? 1 : -1); up = down = false; }          // Ctrl+Up / Down: every chord a semitone
        if (hasBarSel() && (left || right)) {             // with bars selected: Left / Right move the whole bars (chords too), Ctrl = 4 bars;
            if (shift && !ctrl) {                         // Shift+Left / Right stretches or shrinks the selection one bar at a time
                selectBars(barSelFrom, Math.max(barSelFrom, Math.min(t.bars - 1, barSelTo + (right ? 1 : -1))));
                showBar(barSelTo);
                toast(rangeText(), MUTED);
            } else moveBarSel((right ? 1 : -1) * (ctrl ? BARS_SHOWN : 1));
            left = right = false;
        }
        if (sel != null) {                                // arrows move the selected note (or all the selected notes)
            if (up) nudge(0, shift ? 12 : 1);
            if (down) nudge(0, shift ? -12 : -1);
            if (left) nudge(-(shift ? STEPS : snapUnit()), 0);                      // Shift = a whole bar
            if (right) nudge(shift ? STEPS : snapUnit(), 0);
        } else {                                          // ... or scroll the view
            if (up) setLowNote(lowNote + 3);
            if (down) setLowNote(lowNote - 3);
            if (left) viewBar--;
            if (right) viewBar++;
        }
        clampView();

        // follow the playhead when it moves on to the next page of bars
        double secs = Sfx.previewSeconds();
        if (secs >= 0) {
            pendingUntil = 0;
            int pb = (int) (playStep(secs) / STEPS);
            if (pb != lastPlayBar) {
                lastPlayBar = pb;
                if (pb < viewBar || pb >= viewBar + BARS_SHOWN) { viewBar = pb / BARS_SHOWN * BARS_SHOWN; clampView(); }
            }
        }
        return closeRequested && finish();
    }

    private boolean finish() { Sfx.stopPreview(); return true; }

    private double playStep(double secs) { return secs / (60.0 / playBpm) * 4.0 + playStartBar * STEPS; }   // (the preview itself starts at playStartBar)

    private boolean pressed(InputHandler in, int key) {
        if (in.wasJustPressed(key)) { in.consume(key); return true; }
        return false;
    }

    /** T: join the selected note onto the one touching it on its left, making ONE long note (it takes that note's pitch).
     *  (Undo with Ctrl+Z; to split a long note again, drag its right edge shorter and place a new note.) */
    private void toggleTie() {
        if (sel == null) { toast("Click a note first, then press T to join it onto the note before it", MUTED); return; }
        if (group.size() > 1) { toast("T joins one note at a time: click a single note first", MUTED); return; }
        final Note n = sel;
        boolean touching = false;
        for (Note o : t.notes) if (o != n && o.end() == n.start) touching = true;
        if (!touching) { toast("Joining needs a note touching this one on its left (no gap in between)", RED); return; }
        edit(() -> n.tied = true);                                                     // normalize() merges the two into one long note
        toast("Joined into one long note (Ctrl+Z undoes)", GREEN);
    }

    private void deleteSelected() {
        if (group.isEmpty()) return;
        final List<Note> gone = new ArrayList<>(group);
        final boolean range = hasBarSel();
        final int rf = barSelFrom, rt = barSelTo;
        edit(() -> t.notes.removeAll(gone));
        clearSelection();
        if (range) selectBars(rf, rt);                                         // the bars stay selected (their chords are untouched)
        if (gone.size() > 1) toast("Deleted " + gone.size() + " notes (Ctrl+Z brings them back)", GOLD);
    }

    /** Moves every selected note together by the same amount, stopping when any one of them reaches an edge. */
    private void nudgeGroup(int dSteps, int dPitch) {
        int minS = Integer.MAX_VALUE, maxS = -1, minM = Integer.MAX_VALUE, maxM = -1;
        for (Note n : group) { minS = Math.min(minS, n.start); maxS = Math.max(maxS, n.start); minM = Math.min(minM, n.midi); maxM = Math.max(maxM, n.midi); }
        final int dS = Math.max(-minS, Math.min(t.bars * STEPS - 1 - maxS, dSteps));
        final int dP = Math.max(WorkshopTune.LOW_NOTE - minM, Math.min(WorkshopTune.HIGH_NOTE - maxM, dPitch));
        if (dS == 0 && dP == 0) { toast("They can't go any further that way: one of the notes is at the edge", MUTED); return; }
        final List<Note> moved = new ArrayList<>(group);
        final String[] followed = {""};
        edit(() -> {
            for (Note n : moved) { n.start += dS; n.midi += dP; n.len = Math.min(n.len, t.bars * STEPS - n.start); }
            for (Note n : moved) t.resolveOverlaps(n);
            followed[0] = followChordsWith(dS, dP);                          // the chords go along when it's the whole tune
        });
        if (!followed[0].isEmpty()) toast(followed[0], GREEN);
        if (dP != 0 && sel != null) Sfx.previewNote(sel.midi);
    }

    private void nudge(int dSteps, int dPitch) {
        if (group.size() > 1 || (followChords && allSelected())) { nudgeGroup(dSteps, dPitch); return; }
        final Note n = sel;
        edit(() -> {
            n.start = Math.max(0, Math.min(t.bars * STEPS - 1, n.start + dSteps));
            n.len = Math.min(n.len, t.bars * STEPS - n.start);
            n.midi = Math.max(WorkshopTune.LOW_NOTE, Math.min(WorkshopTune.HIGH_NOTE, n.midi + dPitch));
            t.resolveOverlaps(n);
        });
        if (sel != null) {
            Sfx.previewNote(sel.midi);
            int bar = sel.bar();
            if (bar < viewBar) viewBar = bar; else if (bar >= viewBar + BARS_SHOWN) viewBar = bar - BARS_SHOWN + 1;
            if (sel.midi < lowNote) setLowNote(sel.midi); else if (sel.midi >= lowNote + ROWS) setLowNote(sel.midi - ROWS + 1);
        }
    }

    // ================================================================== input: mouse

    public void hover(int mx, int my) { mouseX = mx; mouseY = my; }

    public void press(int mx, int my, int button) {
        mouseX = mx; mouseY = my; mouseDown = true;
        if (FOLLOW_PILL.contains(mx, my)) { toggleFollow(); return; }
        if (START_PILL.contains(mx, my)) {
            startFromView = !startFromView;
            toast(startFromView ? "PLAY now starts at the leftmost bar on screen" : "PLAY now always starts at bar 1", GOLD);
            return;
        }
        if (TOOL_PILL.contains(mx, my)) { toggleTool(); return; }
        for (Btn b : buttons()) {
            if (in(b.r(), mx, my)) {
                if (b.enabled()) {
                    b.act().run();
                    if (b.repeat()) { heldBtn = b; repeatCount = 0; nextRepeat = System.currentTimeMillis() + 420; }
                }
                return;
            }
        }
        if (confirm) return;

        if (in(new Rectangle(MAP_X, MAP_Y, MAP_W, MAP_H), mx, my)) {
            if (shiftHeld) {                                                       // Shift+drag on the SONG map: select bars across the WHOLE song
                rangeAnchor = mapBar(mx);
                selectBars(rangeAnchor, rangeAnchor);
                drag = Drag.MAPSEL; dragNote = null; dragBefore = null;
            } else { drag = Drag.MINIMAP; mapJump(mx); }
            return;
        }

        if (in(new Rectangle(ROLL_X, ROLL_Y - 30, ROLL_W, 30), mx, my)) {            // click / drag along the BAR numbers: select whole bars
            int b = Math.min(t.bars - 1, barAt(mx));
            rangeAnchor = (shiftHeld && hasBarSel()) ? (b >= barSelFrom ? barSelFrom : barSelTo) : b;   // Shift+click stretches the selection
            selectBars(rangeAnchor, b);
            drag = Drag.BARS; dragNote = null; dragBefore = null;
            return;
        }

        // beat cells: left arrow / right-click = the previous drum pattern, anywhere else = the next one
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            Rectangle cell = new Rectangle(ROLL_X + i * BAR_W, BEAT_Y, BAR_W, BEAT_H);
            if (!in(cell, mx, my)) continue;
            cycleBeat(bar, button == MouseEvent.BUTTON3 || mx < cell.x + 36 ? -1 : 1);
            return;
        }

        // vocal cells: left arrow / right-click = the previous lead sound, anywhere else = the next one
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            Rectangle cell = new Rectangle(ROLL_X + i * BAR_W, VOCAL_Y, BAR_W, VOCAL_H);
            if (!in(cell, mx, my)) continue;
            cycleVoice(bar, button == MouseEvent.BUTTON3 || mx < cell.x + 36 ? -1 : 1);
            return;
        }

        // chord cells: left arrow / right arrow move the root by a semitone, the middle switches major <-> minor
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            Rectangle cell = new Rectangle(ROLL_X + i * BAR_W, CHORD_Y, BAR_W, CHORD_H);
            if (!in(cell, mx, my)) continue;
            final int fb = bar;
            if (button == MouseEvent.BUTTON3 || WorkshopTune.isNoChord(t.chords.get(fb))) {   // right-click: no chord <-> chord (clicking a "none" bar brings its chord back)
                edit(() -> {
                    String cur = t.chords.get(fb);
                    if (WorkshopTune.isNoChord(cur)) t.chords.set(fb, chordBeforeNone[fb] != null ? chordBeforeNone[fb] : "C");
                    else { chordBeforeNone[fb] = cur; t.chords.set(fb, WorkshopTune.NO_CHORD); }
                });
                return;
            }
            if (mx < cell.x + 36) edit(() -> t.chords.set(fb, WorkshopTune.shiftChord(t.chords.get(fb), -1)));
            else if (mx >= cell.x + cell.width - 36) edit(() -> t.chords.set(fb, WorkshopTune.shiftChord(t.chords.get(fb), 1)));
            else edit(() -> t.chords.set(fb, WorkshopTune.toggleMinor(t.chords.get(fb))));
            return;
        }

        if (in(new Rectangle(ROLL_X, ROLL_Y, ROLL_W, ROLL_H), mx, my)) rollPress(mx, my, button);
    }

    private void rollPress(int mx, int my, int button) {
        int step = xToStep(mx), midi = yToMidi(my);
        Note hit = t.noteAt(step, midi);
        if (button == MouseEvent.BUTTON3) {                           // right-click: remove
            if (hit != null) { final Note h = hit; edit(() -> t.notes.remove(h)); if (sel == hit) sel = null; }
            return;
        }
        int pressedBar = step / STEPS;
        if (selectTool && hasBarSel() && !shiftHeld && !ctrlHeld && pressedBar >= barSelFrom && pressedBar <= barSelTo) {
            drag = Drag.RANGE_MOVE; rangeAnchor = pressedBar; rangeDelta = 0;      // grab the selected bars: they move together, chords included
            dragNote = null; dragBefore = null;
            return;
        }
        dragBefore = t.toText();
        if (hit != null) {
            if (shiftHeld || ctrlHeld) {                                      // Shift+click: add the note to the selection, or take it out again
                if (group.contains(hit)) { group.remove(hit); if (sel == hit) sel = group.isEmpty() ? null : group.iterator().next(); }
                else { group.add(hit); sel = hit; Sfx.previewNote(hit.midi); }
                dragBefore = null;
                return;
            }
            boolean edge = mx >= stepToX(hit.end()) - 6;                      // grab the right edge to resize
            dragNote = hit;
            dragOrigLen = hit.len;
            dragGrab = step - hit.start;
            if (!edge && group.size() > 1 && group.contains(hit)) {          // grab one of several selected notes: they all move together
                sel = hit;
                groupOrig.clear();
                gMinStart = gMinMidi = Integer.MAX_VALUE;
                gMaxStart = gMaxMidi = -1;
                for (Note n : group) {
                    groupOrig.put(n, new int[]{n.start, n.len, n.midi});
                    gMinStart = Math.min(gMinStart, n.start); gMaxStart = Math.max(gMaxStart, n.start);
                    gMinMidi = Math.min(gMinMidi, n.midi); gMaxMidi = Math.max(gMaxMidi, n.midi);
                }
                drag = Drag.GROUP_MOVE;
            } else {
                selectOnly(hit);
                drag = edge ? Drag.RESIZE : Drag.MOVE;
            }
            Sfx.previewNote(hit.midi);
        } else if (shiftHeld || selectTool) {  boxBars = selectTool && !shiftHeld; if (boxBars) rangeAnchor = pressedBar;                                               // Shift+drag on an empty spot: draw a selection box
            boxX0 = mx; boxY0 = my;
            box = new Rectangle(mx, my, 0, 0);
            drag = Drag.BOX;
            dragNote = null;
            dragBefore = null;
        } else {
            int snap = snapUnit();
            int start = Math.max(0, Math.min(t.bars * STEPS - 1, Math.floorDiv(step, snap) * snap));
            Note n = new Note(start, Math.min(lenSteps, t.bars * STEPS - start), midi);
            t.notes.add(n);
            selectOnly(n); dragNote = n; drag = Drag.CREATE;
            Sfx.previewNote(midi);
        }
    }

    public void drag(int mx, int my) {
        mouseX = mx; mouseY = my;
        if (drag == Drag.NONE || dragNote == null && drag != Drag.MINIMAP && drag != Drag.BOX && drag != Drag.BARS && drag != Drag.RANGE_MOVE && drag != Drag.MAPSEL) return;
        switch (drag) {
            case MINIMAP -> mapJump(mx);
            case BARS -> selectBars(rangeAnchor, Math.min(t.bars - 1, barAt(mx)));
            case MAPSEL -> selectBars(rangeAnchor, mapBar(mx));
            case RANGE_MOVE -> rangeDelta = Math.max(-barSelFrom, Math.min(WorkshopTune.MAX_BARS - 1 - barSelTo, barAt(mx) - rangeAnchor));
            case CREATE, RESIZE -> {
                int snap = snapUnit();
                int len = xToStep(mx) - dragNote.start + 1;
                len = (len + snap - 1) / snap * snap;
                len = Math.max(drag == Drag.CREATE ? lenSteps : 1, len);
                dragNote.len = Math.min(len, t.bars * STEPS - dragNote.start);
            }
            case MOVE -> {
                int snap = snapUnit();
                int ns = Math.max(0, Math.min(t.bars * STEPS - 1, Math.floorDiv(xToStep(mx) - dragGrab, snap) * snap));
                dragNote.start = ns;
                dragNote.len = Math.min(dragOrigLen, t.bars * STEPS - ns);
                int nm = yToMidi(my);
                if (nm != dragNote.midi) { dragNote.midi = nm; Sfx.previewNote(nm); }
            }
            case GROUP_MOVE -> {
                int snap = snapUnit();
                int[] ao = groupOrig.get(dragNote);
                int anchor = Math.max(0, Math.min(t.bars * STEPS - 1, Math.floorDiv(xToStep(mx) - dragGrab, snap) * snap));
                int dS = Math.max(-gMinStart, Math.min(t.bars * STEPS - 1 - gMaxStart, anchor - ao[0]));
                int dP = Math.max(WorkshopTune.LOW_NOTE - gMinMidi, Math.min(WorkshopTune.HIGH_NOTE - gMaxMidi, yToMidi(my) - ao[2]));
                lastDS = dS; lastDP = dP;
                int before = dragNote.midi;
                for (Map.Entry<Note, int[]> e : groupOrig.entrySet()) {
                    Note n = e.getKey(); int[] o = e.getValue();
                    n.start = o[0] + dS;
                    n.len = Math.min(o[1], t.bars * STEPS - n.start);
                    n.midi = o[2] + dP;
                }
                if (dragNote.midi != before) Sfx.previewNote(dragNote.midi);
            }
            case BOX -> {
                int cx = Math.max(ROLL_X, Math.min(ROLL_X + ROLL_W, mx)), cy = Math.max(ROLL_Y, Math.min(ROLL_Y + ROLL_H, my));
                box = new Rectangle(Math.min(boxX0, cx), Math.min(boxY0, cy), Math.abs(cx - boxX0), Math.abs(cy - boxY0));
            }
            default -> { }
        }
    }

    public void release(int mx, int my) {
        mouseDown = false;
        heldBtn = null;
        if (dragNote != null && (drag == Drag.CREATE || drag == Drag.MOVE || drag == Drag.RESIZE)) {
            t.resolveOverlaps(dragNote);
            t.normalize();
            syncSelection();
            pushUndoIfChanged(dragBefore);
        }
        if (drag == Drag.GROUP_MOVE) {                                        // the whole group was moved: settle it, as one undo step
            for (Note n : new ArrayList<>(groupOrig.keySet())) t.resolveOverlaps(n);
            String followed = followChordsWith(lastDS, lastDP);               // dragging the whole tune takes the chords along
            if (!followed.isEmpty()) toast(followed, GREEN);
            t.normalize();
            syncSelection();
            pushUndoIfChanged(dragBefore);
            groupOrig.clear();
        }
        if (drag == Drag.BOX) {                                               // the selection box was let go
            if (boxBars) {                                                    // select-tool box: snaps to whole bars, picks up their notes AND chords
                if (box != null && (box.width > 4 || box.height > 4)) {
                    selectBars(rangeAnchor, Math.min(t.bars - 1, barAt(mx)));
                    if (hasBarSel()) toast(rangeText() + " selected: drag them to move, Ctrl+D duplicates, Ctrl+C / Ctrl+V copy & paste, Ctrl+Del trims them out", GREEN);
                } else clearSelection();                                      // a plain click on empty space just deselects
            } else if (box != null && (box.width > 4 || box.height > 4)) {   // Shift+drag: the free box picks up every note it touches
                for (Note n : t.notes) {
                    Rectangle nr = new Rectangle(stepToX(n.start), midiToY(n.midi), n.len * COL_W, ROW_H);
                    if (box.intersects(nr) && n.end() > viewBar * STEPS && n.start < (viewBar + BARS_SHOWN) * STEPS) group.add(n);
                }
                if (sel == null || !group.contains(sel)) sel = group.isEmpty() ? null : group.iterator().next();
                if (group.size() > 1) toast(group.size() + " notes selected: drag one of them, or use the arrows, to move them together", MUTED);
            }
            box = null;
            boxBars = false;
        }
        if (drag == Drag.BARS && hasBarSel())
            toast(rangeText() + " selected: " + (selectTool ? "drag them" : "press V, then drag them") + " to move, Ctrl+D duplicates, Ctrl+C / Ctrl+V copy & paste, Ctrl+Del trims them out", GREEN);
        if (drag == Drag.MAPSEL && hasBarSel()) {
            viewBar = barSelFrom; clampView();                                     // show where the selection starts
            toast(rangeText() + " selected from the SONG map: Ctrl+C copies, Ctrl+D duplicates, Left / Right move, Ctrl+Del trims out", GREEN);
        }
        if (drag == Drag.RANGE_MOVE) { int d = rangeDelta; rangeDelta = 0; if (d != 0) moveBarSel(d); }
        drag = Drag.NONE;
        dragNote = null;
        dragBefore = null;
    }

    public void wheel(int mx, int my, int rotation, boolean shift) {
        boolean overBars = shift || ctrlHeld || my < ROLL_Y || my > ROLL_Y + ROLL_H;
        if (overBars) {
            viewBar += rotation * (ctrlHeld ? 16 : 1); clampView();                      // Ctrl+wheel: 16 bars per notch
            if (mouseDown && (drag == Drag.BARS || drag == Drag.RANGE_MOVE || (drag == Drag.BOX && boxBars))) drag(mouseX, mouseY);
        }
        else setLowNote(lowNote - rotation * 2);
    }

    private int mapBar(int mx) { return Math.max(0, Math.min(t.bars - 1, (int) ((mx - MAP_X) / (MAP_W / (double) t.bars)))); }

    private void mapJump(int mx) {
        int bar = (int) ((mx - MAP_X) / (MAP_W / (double) t.bars));
        viewBar = bar - BARS_SHOWN / 2;
        clampView();
    }

    // ================================================================== buttons

    private List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        if (confirm) {
            l.add(new Btn(new Rectangle(268, 384, 148, 34), "SAVE & EXIT", GREEN, true, false, false, () -> { if (save()) closeRequested = true; }));
            l.add(new Btn(new Rectangle(426, 384, 148, 34), "DISCARD & EXIT", RED, true, false, false, () -> closeRequested = true));
            l.add(new Btn(new Rectangle(584, 384, 148, 34), "KEEP EDITING", GRAY, true, false, false, () -> confirm = false));
            return l;
        }
        boolean dirty = isDirty();
        boolean playing = isPlaying();
        l.add(new Btn(new Rectangle(418, 30, 104, 28), playing ? "\u25A0 STOP" : "\u25B6 PLAY", BLUE, true, playing, false, this::togglePlay));
        l.add(new Btn(new Rectangle(530, 30, 104, 28), "LOOP: " + (loop ? "ON" : "OFF"), GREEN, true, loop, false, () -> loop = !loop));
        l.add(new Btn(new Rectangle(642, 30, 96, 28), dirty ? "SAVE" : "SAVED", GREEN, dirty, false, false, this::save));
        l.add(new Btn(new Rectangle(746, 30, 100, 28), "DISCARD", RED, dirty, false, false, this::discard));
        l.add(new Btn(new Rectangle(854, 30, 84, 28), "BACK", GRAY, true, false, false, this::requestClose));

        // tempo / bars / drums / full sound: [-] value [+]
        spinner(l, 50, 586, () -> t.bpm = Math.max(WorkshopTune.MIN_BPM, t.bpm - 1), () -> t.bpm = Math.min(WorkshopTune.MAX_BPM, t.bpm + 1));
        spinner(l, 215, 586, () -> t.bars = Math.max(WorkshopTune.MIN_BARS, t.bars - holdStep()), () -> t.bars = Math.min(WorkshopTune.MAX_BARS, t.bars + holdStep()));
        spinner(l, 380, 586, () -> t.drumsFromBar = Math.max(0, t.drumsFromBar - holdStep()), () -> t.drumsFromBar = Math.min(t.bars, t.drumsFromBar + holdStep()));
        spinner(l, 545, 586, () -> { t.fullSoundFromBar = Math.max(0, t.fullSoundFromBar - holdStep()); t.resplitVoices(); }, () -> { t.fullSoundFromBar = Math.min(t.bars, t.fullSoundFromBar + holdStep()); t.resplitVoices(); });

        for (int i = 0; i < LENGTHS.length; i++) {
            final int steps = LENGTHS[i];
            l.add(new Btn(new Rectangle(50 + i * 60, 646, 54, 28), LENGTH_NAMES[i], PINK, true, lenSteps == steps, false, () -> lenSteps = steps));
        }
        l.add(new Btn(new Rectangle(356, 646, 118, 28), "SNAP (G): " + snapName(), GRAY, true, false, false, this::cycleSnap));
        l.add(new Btn(new Rectangle(484, 646, 80, 28), "UNDO", GRAY, !undo.isEmpty(), false, false, this::doUndo));
        l.add(new Btn(new Rectangle(572, 646, 110, 28), "DELETE NOTE", GRAY, sel != null, false, false, this::deleteSelected));
        l.add(new Btn(new Rectangle(690, 646, 100, 28), "CLEAR ALL", GRAY, !t.notes.isEmpty(), false, false, () -> edit(() -> t.notes.clear())));
        l.add(new Btn(new Rectangle(798, 646, 140, 28), "RESET TO STARTER", GRAY, true, false, false, () -> { edit(() -> t = WorkshopTune.defaults()); sel = null; clampView(); }));
        if (hasBarSel() || clip != null) {                       // what to do with the selected bars (PASTE shows up once something is copied)
            boolean has = hasBarSel(), canPaste = clip != null;
            int bx = 710, bw = 74, bh = 20, y1 = 594, y2 = 618;
            l.add(new Btn(new Rectangle(bx, y1, bw, bh), "DUPLICATE", GREEN, has, false, false, this::duplicateBarSel));
            l.add(new Btn(new Rectangle(bx + 77, y1, bw, bh), "COPY", GRAY, has, false, false, () -> copyBarSel(false)));
            l.add(new Btn(new Rectangle(bx + 154, y1, bw, bh), "PASTE", GRAY, canPaste, false, false, this::pasteBarClip));
            l.add(new Btn(new Rectangle(bx, y2, bw, bh), "CLEAR", GRAY, has, false, false, this::clearBarSel));
            l.add(new Btn(new Rectangle(bx + 77, y2, bw, bh), "TRIM OUT", RED, has, false, false, this::removeBarSel));
            l.add(new Btn(new Rectangle(bx + 154, y2, bw, bh), "CROP", GRAY, has, false, false, this::cropBarSel));
        }
        return l;
    }

    /** A [-] and [+] pair: the value box between them is drawn by render(). Changes go through edit() (so they undo). Hold to repeat. */
    /** Step for the bars / drums / full-sound spinners: 1 for a click, then faster the longer the button is held (a tune can be 360 bars). */
    private int holdStep() { return repeatCount < 12 ? 1 : repeatCount < 30 ? 4 : 16; }

    private void spinner(List<Btn> l, int x, int y, Runnable minus, Runnable plus) {
        l.add(new Btn(new Rectangle(x, y, 28, 28), "\u2212", GRAY, true, false, true, () -> edit(minus)));
        l.add(new Btn(new Rectangle(x + 112, y, 28, 28), "+", GRAY, true, false, true, () -> edit(plus)));
    }

    // ================================================================== drawing

    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long now = System.currentTimeMillis();
        boolean dirty = isDirty();

        // ---- header
        g.setColor(GOLD);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString("MUSIC EDITOR", 50, 52);
        int pillX = 50 + g.getFontMetrics().stringWidth("MUSIC EDITOR") + 14;
        g.setColor(dirty ? new Color(255, 170, 40) : new Color(45, 195, 135));
        g.fillRoundRect(pillX, 32, 92, 22, 22, 22);
        g.setColor(new Color(20, 22, 30));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        centerText(g, dirty ? "UNSAVED" : "SAVED", pillX + 46, 47);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        if (toast != null && now < toastUntil) { g.setColor(toastColor); g.drawString(toast, 50, 74); }
        else {
            g.setColor(MUTED);
            g.drawString("Track \"??? 2\"  \u2022  " + t.bars + " bars  \u2022  " + t.bpm + " BPM  \u2022  " + t.notes.size() + " notes", 50, 74);
        }

        drawMiniMap(g);
        drawRoll(g, now);
        drawChords(g);
        drawBeats(g);
        drawVoices(g);
        drawControls(g);
        drawBarSelection(g);

        // ---- buttons (drawn last so they sit on top)
        for (Btn b : buttons()) drawBtn(g, b);

        drawFollowPill(g);
        drawStartPill(g);
        drawToolPill(g);

        // ---- help (four short lines: each one has to fit the 1000 px screen)
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(170, 170, 185));
        centerText(g, "Click a cell: new note  \u2022  drag a note: move  \u2022  right edge: resize  \u2022  right-click / Del: remove  \u2022  Shift+drag, Ctrl+A: select notes",
                Constants.SCREEN_WIDTH / 2, 692);
        centerText(g, "Space: play from the leftmost bar  L: loop  Ctrl+S: save  Ctrl+Z: undo  1-5: length  arrows: nudge  T: join  Ctrl+Up/Down: chords  Esc: back",
                Constants.SCREEN_WIDTH / 2, 706);
        centerText(g, "Select bars: drag along the BAR numbers (hold at the edge to scroll)  \u2022  Shift+drag the SONG map  \u2022  Shift+Left/Right stretch  \u2022  V: select tool",
                Constants.SCREEN_WIDTH / 2, 720);
        g.setColor(new Color(150, 155, 175));
        centerText(g, "Selected bars: Ctrl+C / V / D copy / paste at left bar / duplicate  \u2022  Left / Right: move (Ctrl = 4)  \u2022  Ctrl+Del: trim out  \u2022  Ctrl+K: crop",
                Constants.SCREEN_WIDTH / 2, 734);

        if (confirm) drawConfirm(g);
    }

    private void drawMiniMap(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("SONG", PIANO_X, MAP_Y + 19);
        g.setColor(PANEL);
        g.fillRoundRect(MAP_X, MAP_Y, MAP_W, MAP_H, 8, 8);
        double cw = MAP_W / (double) t.bars;
        int labelEvery = Math.max(1, (int) Math.ceil(26.0 / cw));                       // a 360-bar song can't label every bar
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (int b = 0; b < t.bars; b++) {
            int x = MAP_X + (int) Math.round(b * cw);
            if (b >= t.drumsFromBar) { g.setColor(new Color(255, 170, 40, 40)); g.fillRect(x, MAP_Y, (int) Math.ceil(cw), MAP_H); }
            if (b % labelEvery != 0) continue;
            g.setColor(new Color(60, 66, 92));
            g.drawLine(x, MAP_Y + 2, x, MAP_Y + MAP_H - 2);
            g.setColor(new Color(110, 116, 140));
            g.drawString(String.valueOf(b + 1), x + 3, MAP_Y + 10);
        }
        if (hasBarSel()) {                                                              // where the selected bars are in the whole song
            g.setColor(new Color(255, 214, 90, 80));
            int x0 = MAP_X + (int) Math.round(barSelFrom * cw), x1 = MAP_X + (int) Math.round((barSelTo + 1) * cw);
            g.fillRect(x0, MAP_Y, Math.max(2, x1 - x0), MAP_H);
        }
        int lo = WorkshopTune.LOW_NOTE, span = WorkshopTune.HIGH_NOTE - lo;
        g.setColor(PINK);
        for (Note n : t.notes) {
            int x = MAP_X + (int) Math.round(n.start * cw / STEPS), w = Math.max(1, (int) Math.round(n.len * cw / STEPS) - 1);
            int y = MAP_Y + MAP_H - 4 - (int) Math.round((n.midi - lo) / (double) span * (MAP_H - 12));
            g.fillRect(x, y, w, 2);
        }
        int vx = MAP_X + (int) Math.round(viewBar * cw), vw = (int) Math.round(Math.min(BARS_SHOWN, t.bars) * cw);
        g.setColor(new Color(255, 214, 90, 40));
        g.fillRoundRect(vx, MAP_Y, vw, MAP_H, 6, 6);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(vx, MAP_Y, vw, MAP_H, 6, 6);
        g.setStroke(new BasicStroke(1f));
        double secs = Sfx.previewSeconds();
        if (secs >= 0) {
            int px = MAP_X + (int) Math.round(playStep(secs) / STEPS * cw);
            g.setColor(Color.WHITE);
            g.drawLine(px, MAP_Y, px, MAP_Y + MAP_H);
        }
    }

    private void drawRoll(Graphics2D g, long now) {
        Rectangle clip = new Rectangle(ROLL_X, ROLL_Y, ROLL_W, ROLL_H);
        g.setColor(PANEL);
        g.fillRoundRect(PIANO_X - 4, ROLL_Y - 30, ROLL_X - PIANO_X + ROLL_W + 8, ROLL_H + 34, 10, 10);

        // ---- bar numbers above the roll
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            g.setColor(bar >= t.drumsFromBar ? new Color(255, 170, 40) : MUTED);
            g.drawString("BAR " + (bar + 1), ROLL_X + i * BAR_W + 6, ROLL_Y - 10);
        }
        if (startFromView) { g.setColor(GOLD); g.drawString("START \u25B6", PIANO_X + 2, ROLL_Y - 10); }   // PLAY begins at this left edge

        // ---- rows (black keys darker), chord tones tinted, piano keys on the left
        int hoverMidi = in(clip, mouseX, mouseY) ? yToMidi(mouseY) : -1;
        for (int row = 0; row < ROWS; row++) {
            int midi = lowNote + ROWS - 1 - row, y = ROLL_Y + row * ROW_H;
            boolean black = WorkshopTune.isBlackKey(midi);
            g.setColor(black ? new Color(18, 20, 30) : new Color(30, 33, 47));
            g.fillRect(ROLL_X, y, ROLL_W, ROW_H);
            for (int i = 0; i < BARS_SHOWN; i++) {                        // chord tones of each bar
                int bar = viewBar + i;
                if (bar >= t.bars) break;
                if (isChordTone(t.chords.get(bar), midi)) {
                    g.setColor(new Color(60, 200, 160, midi % 12 == WorkshopTune.chordRootIndex(t.chords.get(bar)) ? 52 : 30));
                    g.fillRect(ROLL_X + i * BAR_W, y, BAR_W, ROW_H);
                }
            }
            if (midi == hoverMidi) { g.setColor(new Color(255, 255, 255, 18)); g.fillRect(ROLL_X, y, ROLL_W, ROW_H); }
            if (midi % 12 == 0) { g.setColor(new Color(90, 98, 130)); g.drawLine(ROLL_X, y + ROW_H - 1, ROLL_X + ROLL_W, y + ROW_H - 1); }

            g.setColor(black ? new Color(40, 42, 56) : new Color(222, 224, 235));   // the piano key
            g.fillRect(PIANO_X, y, ROLL_X - PIANO_X - 2, ROW_H - 1);
            if (midi == hoverMidi) { g.setColor(new Color(255, 110, 185, 120)); g.fillRect(PIANO_X, y, ROLL_X - PIANO_X - 2, ROW_H - 1); }
            if (midi % 12 == 0) {
                g.setColor(new Color(40, 44, 64));
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.drawString(WorkshopTune.noteName(midi), PIANO_X + 6, y + ROW_H - 3);
            }
        }

        // ---- vertical grid
        for (int c = 0; c <= BARS_SHOWN * STEPS; c++) {
            int x = ROLL_X + c * COL_W;
            if (c % STEPS == 0) g.setColor(new Color(130, 138, 170));
            else if (c % 4 == 0) g.setColor(new Color(70, 76, 102));
            else g.setColor(new Color(42, 46, 64));
            g.drawLine(x, ROLL_Y, x, ROLL_Y + ROLL_H);
        }

        // ---- ghost of the note a click would add
        if (drag == Drag.BOX && box != null && !boxBars) {                                   // the selection box being dragged
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRect(box.x, box.y, box.width, box.height);
            g.setColor(new Color(255, 255, 255, 210));
            g.drawRect(box.x, box.y, box.width, box.height);
        }
        if (drag == Drag.NONE && hoverMidi >= 0 && !confirm) {
            int step = xToStep(mouseX), snap = snapUnit();
            int start = Math.floorDiv(step, snap) * snap;
            int len = Math.min(lenSteps, t.bars * STEPS - start);
            if (t.noteAt(step, hoverMidi) == null) {
                g.setColor(new Color(255, 110, 185, 60));
                g.fillRect(stepToX(start) + 1, midiToY(hoverMidi) + 1, len * COL_W - 1, ROW_H - 1);
            }
        }

        // ---- notes
        java.awt.Shape oldClip = g.getClip();
        g.setClip(clip);
        for (Note n : t.notes) {
            if (n.end() <= viewBar * STEPS || n.start >= (viewBar + BARS_SHOWN) * STEPS) continue;
            if (n.midi < lowNote || n.midi >= lowNote + ROWS) continue;
            int x = stepToX(n.start), y = midiToY(n.midi), w = n.len * COL_W;
            int labelX = Math.max(x, ROLL_X) + 5;                                      // a long note may start before the bars on screen
            boolean s = group.contains(n);
            g.setColor(s ? new Color(255, 170, 215) : PINK);
            g.fillRoundRect(x + 1, y + 1, w - 2, ROW_H - 2, 5, 5);
            g.setColor(s ? Color.WHITE : new Color(150, 40, 100));
            g.setStroke(new BasicStroke(s ? 2f : 1f));
            g.drawRoundRect(x + 1, y + 1, w - 2, ROW_H - 2, 5, 5);
            g.setStroke(new BasicStroke(1f));
            if (w >= 30) {
                g.setColor(new Color(70, 10, 45));
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.drawString(WorkshopTune.noteName(n.midi), labelX, y + ROW_H - 4);
            }
        }

        // ---- playhead
        double secs = Sfx.previewSeconds();
        if (secs >= 0) {
            double ps = playStep(secs);
            int px = ROLL_X + (int) Math.round((ps - viewBar * STEPS) * COL_W);
            if (px >= ROLL_X && px <= ROLL_X + ROLL_W) {
                g.setColor(new Color(255, 255, 255, 60));
                g.fillRect(px - 3, ROLL_Y, 7, ROLL_H);
                g.setColor(Color.WHITE);
                g.fillRect(px - 1, ROLL_Y, 2, ROLL_H);
            }
        } else if (System.currentTimeMillis() < pendingUntil) {
            g.setColor(new Color(255, 255, 255, 190));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString("getting ready\u2026", ROLL_X + 12, ROLL_Y + 22);
        }
        g.setClip(oldClip);
        g.setColor(new Color(90, 98, 130));
        g.drawRect(ROLL_X, ROLL_Y, ROLL_W, ROLL_H);
    }

    private static boolean isChordTone(String chord, int midi) {
        if (WorkshopTune.isNoChord(chord)) return false;
        int r = WorkshopTune.chordRootIndex(chord), pc = Math.floorMod(midi, 12);
        return pc == r || pc == (r + (chord.endsWith("m") ? 3 : 4)) % 12 || pc == (r + 7) % 12;
    }

    /** Changes the drum pattern of a bar -- or of every selected bar, if that bar is one of them. */
    private void cycleBeat(int bar, int dir) {
        final WorkshopTune.Beat next = t.beats.get(bar).step(dir);
        final boolean many = hasBarSel() && bar >= barSelFrom && bar <= barSelTo;
        final int a = many ? barSelFrom : bar, z = many ? barSelTo : bar;
        edit(() -> { for (int b = a; b <= z; b++) t.beats.set(b, next); });
        String where = a == z ? "bar " + (a + 1) : "bars " + (a + 1) + "-" + (z + 1);
        toast("Drums for " + where + ": " + next.label + (bar < t.drumsFromBar && next != WorkshopTune.Beat.NONE ? "  (silent until the drums come in at bar " + (t.drumsFromBar + 1) + ")" : ""), GOLD);
    }

    /** The drum pattern of each visible bar: a name with ‹ › arrows and a little picture of the pattern (kick low, snare middle, hats on top). */
    private void drawBeats(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("BEAT", PIANO_X, BEAT_Y + BEAT_H / 2 + 4);
        boolean hovering = false;
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            int x = ROLL_X + i * BAR_W;
            WorkshopTune.Beat beat = t.beats.get(bar);
            boolean off = bar < t.drumsFromBar;                              // the drums haven't come in yet: this pattern is set but silent
            g.setColor(PANEL);
            g.fillRoundRect(x + 1, BEAT_Y, BAR_W - 2, BEAT_H, 8, 8);
            g.setColor(off ? new Color(120, 120, 135, 80) : new Color(255, 170, 40, 110));
            g.drawRoundRect(x + 1, BEAT_Y, BAR_W - 2, BEAT_H, 8, 8);
            boolean hovL = in(new Rectangle(x, BEAT_Y, 36, BEAT_H), mouseX, mouseY);
            boolean hovR = in(new Rectangle(x + BAR_W - 36, BEAT_Y, 36, BEAT_H), mouseX, mouseY);
            hovering |= hovL || hovR || in(new Rectangle(x, BEAT_Y, BAR_W, BEAT_H), mouseX, mouseY);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(hovL ? Color.WHITE : MUTED);
            g.drawString("\u2039", x + 14, BEAT_Y + BEAT_H / 2 + 7);
            g.setColor(hovR ? Color.WHITE : MUTED);
            g.drawString("\u203A", x + BAR_W - 22, BEAT_Y + BEAT_H / 2 + 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(beat == WorkshopTune.Beat.NONE || off ? MUTED : new Color(255, 190, 90));
            g.drawString(beat.label, x + 40, BEAT_Y + BEAT_H / 2 + 5);
            // the picture of the pattern: 16 steps, hats on the top line, snare / clap / toms in the middle, kick / crash at the bottom
            int px = x + 106, pw = 4;
            for (int st = 0; st < 16; st++) {
                int cx = px + st * pw;
                if (st % 4 == 0) { g.setColor(new Color(255, 255, 255, 28)); g.fillRect(cx, BEAT_Y + 5, 1, BEAT_H - 10); }
                for (int lane = 0; lane < WorkshopTune.Beat.LANES; lane++) {
                    double v = beat.strength(lane, st);
                    if (v <= 0) continue;
                    int row = (lane == WorkshopTune.Beat.HAT || lane == WorkshopTune.Beat.OPEN_HAT) ? 0
                            : (lane == WorkshopTune.Beat.KICK || lane == WorkshopTune.Beat.CRASH_CYMBAL) ? 2 : 1;
                    int a = (int) Math.round(90 + 165 * v);
                    g.setColor(off ? new Color(150, 150, 165, a / 2) : new Color(255, 170, 40, a));
                    g.fillRect(cx, BEAT_Y + 4 + row * 7, pw - 1, 5);
                }
            }
        }
        if (hovering && !(toast != null && System.currentTimeMillis() < toastUntil)) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(MUTED);
            g.drawString("Drums for this bar: click it (or the \u203A) for the next pattern, \u2039 or right-click for the previous. Select bars first to change many at once.", 50, 74);
        }
    }

    /** Changes the lead sound of a bar -- or of every selected bar, if that bar is one of them. */
    private void cycleVoice(int bar, int dir) {
        final WorkshopTune.Lead next = t.voices.get(bar).step(dir);
        final boolean many = hasBarSel() && bar >= barSelFrom && bar <= barSelTo;
        final int a = many ? barSelFrom : bar, z = many ? barSelTo : bar;
        edit(() -> { for (int b = a; b <= z; b++) t.voices.set(b, next); });
        toast("Vocal for " + (a == z ? "bar " + (a + 1) : "bars " + (a + 1) + "-" + (z + 1)) + ": " + next.label, GOLD);
    }

    /** The lead sound of each visible bar: a name with ‹ › arrows and a little picture of its wave. */
    private void drawVoices(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("VOCAL", PIANO_X, VOCAL_Y + VOCAL_H / 2 + 4);
        boolean hovering = false;
        Color ink = new Color(120, 190, 255);
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            int x = ROLL_X + i * BAR_W;
            WorkshopTune.Lead lead = t.voices.get(bar);
            g.setColor(PANEL);
            g.fillRoundRect(x + 1, VOCAL_Y, BAR_W - 2, VOCAL_H, 8, 8);
            g.setColor(new Color(90, 160, 255, 110));
            g.drawRoundRect(x + 1, VOCAL_Y, BAR_W - 2, VOCAL_H, 8, 8);
            boolean hovL = in(new Rectangle(x, VOCAL_Y, 36, VOCAL_H), mouseX, mouseY);
            boolean hovR = in(new Rectangle(x + BAR_W - 36, VOCAL_Y, 36, VOCAL_H), mouseX, mouseY);
            hovering |= in(new Rectangle(x, VOCAL_Y, BAR_W, VOCAL_H), mouseX, mouseY);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(hovL ? Color.WHITE : MUTED);
            g.drawString("\u2039", x + 14, VOCAL_Y + VOCAL_H / 2 + 7);
            g.setColor(hovR ? Color.WHITE : MUTED);
            g.drawString("\u203A", x + BAR_W - 22, VOCAL_Y + VOCAL_H / 2 + 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(ink);
            g.drawString(lead.label, x + 40, VOCAL_Y + VOCAL_H / 2 + 5);
            // the picture: two cycles of each wave the sound is made of (a plucked sound fades away)
            int px = x + 106, pw = 66, cy = VOCAL_Y + VOCAL_H / 2, amp = 7;
            for (int li = 0; li < lead.layers.length; li++) {
                WorkshopTune.Lead.Layer ly = lead.layers[li];
                g.setColor(new Color(120, 190, 255, li == 0 ? 230 : 120));
                int prevX = px, prevY = cy;
                for (int k = 0; k <= pw; k++) {
                    double ph = (k / (pw / 2.0)) % 1.0;                                  // 0..1 through a cycle (two cycles over the width)
                    double v = switch (ly.kind) {
                        case SQUARE -> ph < ly.duty ? 1.0 : -1.0;
                        case TRIANGLE -> ph < 0.5 ? -1 + 4 * ph : 3 - 4 * ph;
                        case SAW -> -1 + 2 * ph;
                    };
                    if (lead.sustain < 1.0) v *= Math.max(0.0, 1.0 - k / (double) pw * 1.15);
                    int yy = cy - (int) Math.round(v * amp * (li == 0 ? 1.0 : 0.7));
                    if (k > 0) g.drawLine(prevX, prevY, px + k, yy);
                    prevX = px + k; prevY = yy;
                }
            }
        }
        if (hovering && !(toast != null && System.currentTimeMillis() < toastUntil)) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(MUTED);
            g.drawString("Vocal (the lead's sound) for this bar: click it (or the \u203A) for the next one, \u2039 or right-click for the previous. Any bar can use any sound.", 50, 74);
        }
    }

    private void drawChords(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("CHORD", PIANO_X, CHORD_Y + CHORD_H / 2 + 4);
        for (int i = 0; i < BARS_SHOWN; i++) {
            int bar = viewBar + i;
            if (bar >= t.bars) break;
            int x = ROLL_X + i * BAR_W;
            g.setColor(PANEL);
            g.fillRoundRect(x + 1, CHORD_Y, BAR_W - 2, CHORD_H, 8, 8);
            g.setColor(new Color(60, 200, 160, 90));
            g.drawRoundRect(x + 1, CHORD_Y, BAR_W - 2, CHORD_H, 8, 8);
            boolean hovL = in(new Rectangle(x, CHORD_Y, 36, CHORD_H), mouseX, mouseY);
            boolean hovR = in(new Rectangle(x + BAR_W - 36, CHORD_Y, 36, CHORD_H), mouseX, mouseY);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(hovL ? Color.WHITE : MUTED);
            g.drawString("\u2039", x + 14, CHORD_Y + CHORD_H / 2 + 8);
            g.setColor(hovR ? Color.WHITE : MUTED);
            g.drawString("\u203A", x + BAR_W - 22, CHORD_Y + CHORD_H / 2 + 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            boolean none = WorkshopTune.isNoChord(t.chords.get(bar));
            g.setColor(none ? MUTED : new Color(110, 235, 195));
            centerText(g, none ? "none" : t.chords.get(bar), x + BAR_W / 2, CHORD_Y + CHORD_H / 2 + 6);

            int fx = x + 4;                                               // where the drums / full sound switch on
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            if (bar == t.drumsFromBar) { fx = flag(g, fx, "DRUMS IN", new Color(255, 170, 40)); }
            if (bar == t.fullSoundFromBar) { flag(g, fx, "FULL SOUND", BLUE); }
        }
    }

    private int flag(Graphics2D g, int x, String text, Color c) {
        int w = g.getFontMetrics().stringWidth(text) + 12;
        g.setColor(c);
        g.fillRoundRect(x, FLAG_Y, w, 15, 8, 8);
        g.setColor(new Color(20, 22, 30));
        g.drawString(text, x + 6, FLAG_Y + 11);
        return x + w + 4;
    }

    private void drawControls(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(MUTED);
        g.drawString("TEMPO (BPM)", 50 + 28, 580);
        g.drawString("BARS", 215 + 28, 580);
        g.drawString("DRUMS COME IN AT", 380 + 4, 580);
        g.drawString("FULL SOUND AT", 545 + 12, 580);
        g.drawString("NEW NOTE LENGTH", 50, 640);
        g.drawString("GRID", 356 + 4, 640);

        valueBox(g, 50, 586, String.valueOf(t.bpm));
        valueBox(g, 215, 586, String.valueOf(t.bars));
        valueBox(g, 380, 586, t.drumsFromBar >= t.bars ? "OFF" : "bar " + (t.drumsFromBar + 1));
        valueBox(g, 545, 586, t.fullSoundFromBar >= t.bars ? "NEVER" : "bar " + (t.fullSoundFromBar + 1));

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(MUTED);
        String sel = group.size() > 1 ? "" + group.size() + " notes (they move together)" : this.sel == null ? "no note selected" : WorkshopTune.noteName(this.sel.midi) + "  \u2022  " + this.sel.len + " / 16 of a bar" + (this.sel.len > STEPS ? "  \u2022  runs across bar lines" : "");
        boolean barTools = hasBarSel() || clip != null;                          // the bar buttons use this spot
        if (!barTools) g.drawString("Selected: " + sel, 710, 604);
        if (!barTools) g.drawString(isPlaying() ? "Playing a snapshot: edits are heard on the next PLAY" : (group.size() > 1 ? "Del removes them  \u2022  Esc deselects" : "Green rows = notes that fit the bar's chord"), 710, 624);
    }

    /** The little "CHORDS FOLLOW (F): ON/OFF" switch above the selection status. */
    /** "PLAY FROM: LEFT BAR / BAR 1" -- above the roll, top right. */
    private void drawStartPill(Graphics2D g) {
        Rectangle r = START_PILL;
        g.setColor(startFromView ? new Color(255, 200, 70) : new Color(50, 56, 78));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
        g.setColor(startFromView ? new Color(50, 36, 6) : new Color(185, 190, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        centerText(g, "PLAY FROM: " + (startFromView ? "LEFT BAR" : "BAR 1"), r.x + r.width / 2, r.y + 13);
    }

    private void drawFollowPill(Graphics2D g) {
        Rectangle r = FOLLOW_PILL;
        g.setColor(followChords ? new Color(45, 195, 135) : new Color(50, 56, 78));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.setColor(followChords ? new Color(8, 40, 26) : new Color(185, 190, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        centerText(g, "CHORDS FOLLOW (F): " + (followChords ? "ON" : "OFF"), r.x + r.width / 2, r.y + 15);
    }

    private void valueBox(Graphics2D g, int x, int y, String text) {
        g.setColor(PANEL);
        g.fillRect(x + 30, y, 80, 28);
        g.setColor(new Color(60, 66, 92));
        g.drawRect(x + 30, y, 80, 28);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        centerText(g, text, x + 70, y + 19);
    }

    private void drawBtn(Graphics2D g, Btn b) {
        Rectangle r = b.r();
        boolean hov = b.enabled() && in(r, mouseX, mouseY);
        Color base = b.active() ? b.color() : (b.color() == GRAY ? GRAY : new Color(50, 56, 78));
        if (b.enabled() && !b.active() && b.color() != GRAY && b.color() != PINK && !b.label().startsWith("LOOP") && !b.label().contains("PLAY")) base = b.color();
        if (!b.enabled()) base = new Color(38, 42, 58);
        if (hov) base = new Color(Math.min(255, base.getRed() + 35), Math.min(255, base.getGreen() + 35), Math.min(255, base.getBlue() + 35));
        g.setColor(base);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g.setColor(!b.enabled() ? new Color(105, 110, 130) : b.active() || base == b.color() ? new Color(15, 18, 26) : new Color(225, 228, 240));
        if (b.color() == GRAY && b.enabled()) g.setColor(new Color(225, 228, 240));
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
        centerText(g, "Save them to \"??? 2\" before leaving, or throw them away?", 500, 346);
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
