package laststand.fx;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The editable tune behind the Sound Test's second hidden track ("??? 2") -- the thing the in-game Music Editor
 * (ui/MusicEditorUI) works on.
 *
 * It's plain data: a melody (a list of notes on a 16th-note grid), one chord per bar, a tempo, and the two
 * "switch on" bars (when the drums come in, when the melody gets its fuller sound). Sfx turns it into audio.
 *
 * The saved copy lives in a small text file next to the game's save files ({@link #FILE}); if that file isn't
 * there, the built-in starter tune from {@link #defaults()} is used. Deleting the file puts the starter back.
 *
 * Time is counted in 16th-note "steps": one bar = 16 steps = 4 beats, so a quarter note is 4 steps.
 * The melody is one voice (like a real NES square channel): two notes can't sound at once -- {@link #normalize()}
 * keeps it that way. A note CAN run across bar lines (one long note in the editor); only the saved file / the
 * synthesizer see it cut at each bar line, as the note plus "T" ties (see {@link #melodyBars()}).
 */
public final class WorkshopTune {

    public static final File FILE = new File("laststand_workshop_track.txt");

    public static final int STEPS_PER_BAR = 16;
    public static final int MIN_BARS = 4, MAX_BARS = 360;   // 360 bars = 12+ minutes at 118 bpm
    public static final int MIN_BPM = 40, MAX_BPM = 240;
    public static final int LOW_NOTE = 36, HIGH_NOTE = 96;     // C2 .. C7

    /** One melody note. start/len are in 16th-note steps from the start of the tune; midi is the pitch (C5 = 72). */
    public static final class Note {
        public int start, len, midi;
        /** true = "join me onto the note touching me on the left". normalize() merges such a note into that one
         *  long note and clears the flag, so after normalize() no note is ever tied. (The file's "T" loads this way.) */
        public boolean tied;
        public Note(int start, int len, int midi) { this.start = start; this.len = len; this.midi = midi; }
        public int end() { return start + len; }
        public int bar() { return start / STEPS_PER_BAR; }
    }

    public int bpm = 96;
    public int bars = 16;
    /** The bar (counting from 0) where the drums come in. Equal to {@link #bars} = no drums at all. */
    public int drumsFromBar = 8;
    /** The bar (counting from 0) where the melody switches from the thin, reedy sound to the fuller one. */
    public int fullSoundFromBar = 8;
    public final List<Note> notes = new ArrayList<>();
    public final List<String> chords = new ArrayList<>();      // one per bar: C, G, Am, F#m, Bb ...
    /** One drum pattern per bar. (A file with no beats= line plays ROCK in every bar, like before.) */
    public final List<Beat> beats = new ArrayList<>();
    /** One lead (melody) sound per bar. (A file with no voices= line uses REEDY before "full sound at" and FULL from there on, like before.) */
    public final List<Lead> voices = new ArrayList<>();

    // ------------------------------------------------------------------ vocals (the sound of the lead melody)

    /**
     * A sound for the lead melody. Each one is one or more stacked waves (see {@link Layer}), plus how the note starts, fades and wobbles.
     * REEDY and FULL are the original two (thin first, fuller later); the rest are new, and any bar can use any of them.
     */
    public enum Lead {
        REEDY("reedy", "REEDY", 1.0, 1, 5.0, 0.012, Layer.sq(0.25, 0.17, 0, 0, 0.006, 0.5)),
        FULL("full", "FULL", 1.0, 1, 5.0, 0.012, Layer.sq(0.5, 0.17, 0, 0, 0.006, 0.5)),
        NASAL("nasal", "NASAL", 1.0, 1, 5.0, 0.012, Layer.sq(0.125, 0.24, 0, 0, 0.004, 0.5)),
        SOFT("soft", "SOFT", 1.0, 1, 5.0, 0.012, Layer.tri(0.27, 0, 0, 0.02, 0.35)),
        CHOIR("choir", "CHOIR", 1.0, 1, 5.0, 0.010, Layer.sq(0.5, 0.13, 0, -7, 0.02, 0.45), Layer.sq(0.25, 0.10, 0, 7, 0.02, 0.45)),
        THICK("thick", "THICK", 1.0, 1, 5.0, 0.012, Layer.sq(0.5, 0.12, 0, 0, 0.006, 0.5), Layer.tri(0.17, -12, 0, 0.006, 0.5)),
        PLUCK("pluck", "PLUCK", 0.45, 0, 0.0, 0.0, Layer.sq(0.25, 0.28, 0, 0, 0.002, 1.2)),
        SINGER("singer", "SINGER", 1.0, 2, 5.5, 0.014, Layer.sq(0.5, 0.14, 0, 0, 0.03, 0.5), Layer.tri(0.12, 0, 0, 0.03, 0.5)),
        BUZZ("buzz", "BUZZ", 1.0, 1, 5.0, 0.010, Layer.saw(0.28, 0, 0, 0.008, 0.5));

        /** The word used in the saved file (voices=full soft ...). */
        public final String code;
        /** Short name for the editor's vocal row. */
        public final String label;
        /** How much of its slot a note sounds (1 = all of it, 0.45 = short and plucked). */
        public final double sustain;
        /** 0 = no vibrato, 1 = a little wobble on long notes, 2 = on every note. */
        public final int vibrato;
        public final double vibHz, vibDepth;
        public final Layer[] layers;

        Lead(String code, String label, double sustain, int vibrato, double vibHz, double vibDepth, Layer... layers) {
            this.code = code; this.label = label; this.sustain = sustain; this.vibrato = vibrato; this.vibHz = vibHz; this.vibDepth = vibDepth; this.layers = layers;
        }

        public Lead step(int dir) {
            Lead[] all = values();
            return all[Math.floorMod(ordinal() + dir, all.length)];
        }

        public static Lead fromCode(String s) {
            String c = s.trim().toLowerCase(Locale.ROOT);
            for (Lead l : values()) if (l.code.equals(c) || l.label.toLowerCase(Locale.ROOT).equals(c)) return l;
            return null;
        }

        /** One wave of a lead sound. */
        public static final class Layer {
            public enum Kind { SQUARE, TRIANGLE, SAW }
            public final Kind kind;
            public final double duty, vol, cents, attack, decay;
            public final int semis;                                   // shift in semitones (-12 = an octave lower)

            private Layer(Kind kind, double duty, double vol, int semis, double cents, double attack, double decay) {
                this.kind = kind; this.duty = duty; this.vol = vol; this.semis = semis; this.cents = cents; this.attack = attack; this.decay = decay;
            }
            static Layer sq(double duty, double vol, int semis, double cents, double attack, double decay) { return new Layer(Kind.SQUARE, duty, vol, semis, cents, attack, decay); }
            static Layer tri(double vol, int semis, double cents, double attack, double decay) { return new Layer(Kind.TRIANGLE, 0.5, vol, semis, cents, attack, decay); }
            static Layer saw(double vol, int semis, double cents, double attack, double decay) { return new Layer(Kind.SAW, 0.5, vol, semis, cents, attack, decay); }
        }
    }

    /** What each bar's lead sounds like. */
    public Lead[] effectiveVoices() {
        normalize();
        return voices.toArray(new Lead[0]);
    }

    /** The "full sound at" setting only decides between the two ORIGINAL sounds: bars that use REEDY or FULL are re-split at it
     *  (REEDY before, FULL from there on); bars with any other sound keep it. */
    public void resplitVoices() {
        normalize();
        for (int b = 0; b < bars; b++) {
            Lead v = voices.get(b);
            if (v == Lead.REEDY || v == Lead.FULL) voices.set(b, b < fullSoundFromBar ? Lead.REEDY : Lead.FULL);
        }
    }

    // ------------------------------------------------------------------ beats

    /**
     * A one-bar drum pattern, written as 16 steps (sixteenth notes) for each drum sound: "." = silence, "x" = a full hit,
     * "o" = a soft hit, "1".."9" = a hit of that strength (9 loudest). ROCK is the original pattern and is played by the
     * synthesizer's own code (it has an extra kick every other bar); the rest are drawn from these lanes.
     */
    public enum Beat {
        NONE("none", "NONE", lanes("", "", "", "", "", "", "", "")),
        ROCK("rock", "ROCK", lanes("x.......x.......", "....x.......x...", "x.6.x.6.x.6.x.6.", "", "", "", "", "")),
        HATS("hats", "HATS", lanes("x.......o.......", "", "x.6.x.6.x.6.x.6.", "", "", "", "", "")),
        CLAPS("clap", "CLAP", lanes("", "", "", "", "", "", "x...x...x...x...", "")),     // a clap on every beat: CLAP - 2 - CLAP - 4 - CLAP - 6 - CLAP - 8
        HEART("heart", "HEART", lanes("x..o....x..o....", "", "", "", "", "", "", "")),
        HALF("half", "HALF", lanes("x.........x.....", "........x.......", "x.6.x.6.x.6.x.6.", "", "", "", "", ""), 1.5),   // sparse, so it plays 50% louder
        FOUR("four", "FLOOR", lanes("x...x...x...x...", "", "", "..x...x...x...x.", "", "", "....x.......x...", "")),
        DOUBLE("double", "DOUBLE", lanes("x.......x.......", "....x.......x...", "x4x4x4x4x4x4x4x4", "", "", "", "", "")),
        BREAK("break", "SYNCOP", lanes("x.........xx....", "....x.......x...", "", "", "", "", "", "")),
        MARCH("march", "MARCH", lanes("x.......x.......", "x.xox.xox.xox.xo", "", "", "", "", "", "")),
        BUILD("build", "BUILD", lanes("x.......x.......", "2233445566778899", "", "", "", "", "", "")),
        FILL("fill", "FILL", lanes("x...............", "x.x.x.x.........", "", "", "........x.x.....", "............x.x.", "", "")),
        CRASH("crash", "CRASH", lanes("x.......x.......", "....x.......x...", "..x...x...x...x.", "", "", "", "", "x..............."));

        public static final int KICK = 0, SNARE = 1, HAT = 2, OPEN_HAT = 3, TOM_HI = 4, TOM_LO = 5, CLAP = 6, CRASH_CYMBAL = 7, LANES = 8;

        /** The word used in the saved file (beats=rock hats ...). */
        public final String code;
        /** Short name for the editor's beat row. */
        public final String label;
        /** LANES strings of 16 characters, one per drum sound (KICK, SNARE, ...). */
        public final String[] lanes;

        /** How loud the whole pattern plays, as a multiple (1.0 = normal): lets a sparse pattern such as HALF stand up next to the busy ones. */
        public final double gain;

        Beat(String code, String label, String[] lanes) { this(code, label, lanes, 1.0); }

        Beat(String code, String label, String[] lanes, double gain) { this.code = code; this.label = label; this.lanes = lanes; this.gain = gain; }

        private static String[] lanes(String... given) {
            String[] out = new String[LANES];
            for (int i = 0; i < LANES; i++) {
                String l = i < given.length ? given[i] : "";
                StringBuilder sb = new StringBuilder(l);
                while (sb.length() < 16) sb.append('.');
                out[i] = sb.substring(0, 16);
            }
            return out;
        }

        /** How hard the hit on this lane / step is, 0 (none) .. 1 (full). */
        public double strength(int lane, int step) {
            char c = lanes[lane].charAt(step);
            if (c == '.') return 0;
            if (c == 'x') return 1.0;
            if (c == 'o') return 0.55;
            return c >= '1' && c <= '9' ? (c - '0') / 9.0 : 0;
        }

        /** strength() with the pattern's own loudness applied: what the synthesizer actually plays. */
        public double level(int lane, int step) { return strength(lane, step) * gain; }

        /** The next (dir = 1) or previous (dir = -1) pattern in the editor's list, wrapping round. */
        public Beat step(int dir) {
            Beat[] all = values();
            return all[Math.floorMod(ordinal() + dir, all.length)];
        }

        /** Reads a name from the file ("rock", "four", also the editor's label like "FLOOR"); null if it isn't one. */
        public static Beat fromCode(String s) {
            String c = s.trim().toLowerCase(Locale.ROOT);
            for (Beat b : values()) if (b.code.equals(c) || b.label.toLowerCase(Locale.ROOT).equals(c)) return b;
            return null;
        }
    }

    /** What each bar actually plays: the beat of its bar, or NONE in the bars before drumsFromBar (the "drums come in at" setting). */
    public Beat[] effectiveBeats() {
        normalize();
        Beat[] out = new Beat[bars];
        for (int b = 0; b < bars; b++) out[b] = b >= drumsFromBar ? beats.get(b) : Beat.NONE;
        return out;
    }

    // ------------------------------------------------------------------ chords

    public static final String[] ROOTS = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};

    /** A bar with no chord: no bass and no arpeggio in that bar (drums still play if they're on). Written "-" in the tune file. */
    public static final String NO_CHORD = "-";

    public static boolean isNoChord(String chord) {
        String c = chord.trim().toLowerCase(Locale.ROOT);
        return c.equals("-") || c.equals("n") || c.equals("no") || c.equals("none");
    }

    /** Same chord, root moved by `semitones` (keeps major/minor). */
    public static String shiftChord(String chord, int semitones) {
        if (isNoChord(chord)) return NO_CHORD;
        boolean minor = chord.endsWith("m");
        int r = chordRootIndex(chord);
        return ROOTS[Math.floorMod(r + semitones, 12)] + (minor ? "m" : "");
    }

    public static String toggleMinor(String chord) {
        if (isNoChord(chord)) return NO_CHORD;
        return chord.endsWith("m") ? chord.substring(0, chord.length() - 1) : chord + "m";
    }

    /** 0..11 for C..B (accepts sharps and flats, like the synthesizer does). */
    public static int chordRootIndex(String chord) {
        String c = chord.trim();
        int semi = c.isEmpty() ? -1 : "C D EF G A B".indexOf(c.charAt(0));
        if (semi < 0) return 0;
        if (c.length() > 1 && c.charAt(1) == '#') semi++;
        else if (c.length() > 1 && c.charAt(1) == 'b') semi--;
        return Math.floorMod(semi, 12);
    }

    // ------------------------------------------------------------------ note names

    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** 72 -> "C5". */
    public static String noteName(int midi) { return NAMES[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1); }

    public static boolean isBlackKey(int midi) {
        int pc = Math.floorMod(midi, 12);
        return pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10;
    }

    /** "C5" -> 72, "F#4" -> 66, "Bb3" -> 58. Throws IllegalArgumentException if it can't be read. */
    public static int parseNote(String name) {
        try {
            int semi = switch (name.charAt(0)) { case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; case 'B' -> 11; default -> throw new IllegalArgumentException(); };
            int i = 1;
            if (name.charAt(i) == '#') { semi++; i++; } else if (name.charAt(i) == 'b') { semi--; i++; }
            return 12 * (Integer.parseInt(name.substring(i)) + 1) + semi;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("can't read the note \"" + name + "\"");
        }
    }

    // ------------------------------------------------------------------ editing helpers

    /** The 0-based bar a step falls in. */
    public static int barOf(int step) { return Math.floorDiv(step, STEPS_PER_BAR); }

    public Note noteAt(int step, int midi) {
        for (Note n : notes) if (n.midi == midi && step >= n.start && step < n.end()) return n;
        return null;
    }

    /** Makes `active` win any overlap: notes it lands on are trimmed, shortened from the front, or removed. */
    public void resolveOverlaps(Note active) {
        int a0 = active.start, a1 = active.end();
        for (Iterator<Note> it = notes.iterator(); it.hasNext(); ) {
            Note n = it.next();
            if (n == active) continue;
            int n0 = n.start, n1 = n.end();
            if (n1 <= a0 || n0 >= a1) continue;
            if (n0 < a0) n.len = a0 - n0;                       // its tail is under the new note
            else if (n1 <= a1) it.remove();                     // completely covered
            else { n.len = n1 - a1; n.start = a1; }             // its head is covered: it starts later
        }
    }

    /** Clamps everything into range and removes overlaps. Safe to call any time. */
    public void normalize() {
        bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, bpm));
        bars = Math.max(MIN_BARS, Math.min(MAX_BARS, bars));
        while (chords.size() < bars) chords.add(chords.isEmpty() ? "C" : chords.get(chords.size() - 1));
        while (chords.size() > bars) chords.remove(chords.size() - 1);
        while (beats.size() < bars) {
            Beat last = beats.isEmpty() ? Beat.ROCK : beats.get(beats.size() - 1);
            beats.add(last == Beat.FILL || last == Beat.CRASH || last == Beat.BUILD ? Beat.ROCK : last);
        }
        while (beats.size() > bars) beats.remove(beats.size() - 1);
        if (voices.isEmpty()) for (int b = 0; b < bars; b++) voices.add(b < fullSoundFromBar ? Lead.REEDY : Lead.FULL);
        while (voices.size() < bars) voices.add(voices.get(voices.size() - 1));
        while (voices.size() > bars) voices.remove(voices.size() - 1);
        drumsFromBar = Math.max(0, Math.min(bars, drumsFromBar));
        fullSoundFromBar = Math.max(0, Math.min(bars, fullSoundFromBar));
        int total = bars * STEPS_PER_BAR;
        for (Iterator<Note> it = notes.iterator(); it.hasNext(); ) {
            Note n = it.next();
            n.midi = Math.max(LOW_NOTE, Math.min(HIGH_NOTE, n.midi));
            if (n.start < 0 || n.start >= total || n.len < 1) { it.remove(); continue; }
            n.len = Math.min(n.len, total - n.start);                                   // a note may cross bar lines, but not run off the end
        }
        notes.sort(Comparator.comparingInt((Note n) -> n.start));
        for (int i = 0; i < notes.size() - 1; i++) {
            Note a = notes.get(i), b = notes.get(i + 1);
            if (b.start == a.start) { notes.remove(i); i--; continue; }                // same start: the later one wins
            if (a.end() > b.start) a.len = b.start - a.start;
        }
        for (int i = 0; i < notes.size(); i++) {                                       // a tie = "carry on the note touching me on the left":
            Note n = notes.get(i);
            if (!n.tied) continue;
            Note prev = i > 0 ? notes.get(i - 1) : null;
            if (prev != null && prev.end() == n.start) {                                // ...so make the two ONE long note (same pitch as the first)
                prev.len += n.len;
                notes.remove(i);
                i--;
            } else {
                n.tied = false;                                                         // nothing to carry on: it's just an ordinary note
            }
        }
    }

    // ------------------------------------------------------------------ to / from the synthesizer's format

    /** One string per bar, e.g. "E5 1, G5 .5, R .5, C5 2" -- exactly what Sfx.playTune reads. Every bar adds up to 4 beats.
     *  A note that runs across a bar line is cut at it: the first piece is the note, each later piece is "T" (a tie). */
    public String[] melodyBars() {
        normalize();
        String[] out = new String[bars];
        int first = 0;                                                                  // notes are sorted and never overlap, so this only moves forward
        for (int b = 0; b < bars; b++) {
            int barStart = b * STEPS_PER_BAR, barEnd = barStart + STEPS_PER_BAR;
            while (first < notes.size() && notes.get(first).end() <= barStart) first++;
            StringBuilder sb = new StringBuilder();
            int pos = barStart;
            for (int i = first; i < notes.size() && notes.get(i).start < barEnd; i++) {
                Note n = notes.get(i);
                int from = Math.max(n.start, barStart), to = Math.min(n.end(), barEnd);
                if (from > pos) append(sb, "R", from - pos);
                append(sb, n.start < barStart ? "T" : noteName(n.midi), to - from);
                pos = to;
            }
            if (pos < barEnd) append(sb, "R", barEnd - pos);
            out[b] = sb.toString();
        }
        return out;
    }

    private static void append(StringBuilder sb, String what, int steps) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(what).append(' ').append(beats(steps));
    }

    /** steps -> beats as the synthesizer writes them: 4 -> "1", 2 -> ".5", 6 -> "1.5", 1 -> ".25". */
    private static String beats(int steps) {
        String s = String.format(Locale.ROOT, "%.2f", steps / 4.0);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s.startsWith("0.") ? s.substring(1) : s;
    }

    public String[] chordNames() { normalize(); return chords.toArray(new String[0]); }

    // ------------------------------------------------------------------ copy / text form

    /** A copy that starts at the given bar (0 = the first): the bars before it are dropped and everything shifts left, so the
     *  editor can PLAY from wherever the player is looking. Drums / full sound keep their original bars; a note that was already
     *  sounding when the new start comes round is sounded again from the very start. (Never touches this tune.) */
    public WorkshopTune startingAtBar(int startBar) {
        normalize();
        startBar = Math.max(0, Math.min(bars - 1, startBar));
        if (startBar == 0) return copy();
        WorkshopTune t = new WorkshopTune();
        t.bpm = bpm;
        t.bars = bars - startBar;
        t.drumsFromBar = Math.max(0, drumsFromBar - startBar);
        t.fullSoundFromBar = Math.max(0, fullSoundFromBar - startBar);
        t.chords.addAll(chords.subList(startBar, bars));
        t.beats.addAll(beats.subList(startBar, bars));
        t.voices.addAll(voices.subList(startBar, bars));
        int shift = startBar * STEPS_PER_BAR;
        for (Note n : notes) {
            if (n.end() <= shift) continue;
            int from = Math.max(n.start, shift);
            t.notes.add(new Note(from - shift, n.end() - from, n.midi));
        }
        t.normalize();                                   // (pads to MIN_BARS if the start was right at the end)
        return t;
    }

    // ================================================================== whole-bar operations (the editor's bar selection)

    /** Whole bars lifted out of a tune: their notes (steps counted from the first copied bar) and the chord of each bar. */
    public static final class BarClip {
        public final int bars;
        public final List<Note> notes = new ArrayList<>();
        public final List<String> chords = new ArrayList<>();
        public final List<Beat> beats = new ArrayList<>();
        public final List<Lead> voices = new ArrayList<>();
        BarClip(int bars) { this.bars = bars; }
    }

    /** Copies bars from..to (inclusive, 0-based): notes AND chords. A note that runs in from before the first bar, or out past the
     *  last one, is cut at the edge. Doesn't change this tune. */
    public BarClip copyBars(int from, int to) {
        normalize();
        from = Math.max(0, Math.min(bars - 1, from));
        to = Math.max(from, Math.min(bars - 1, to));
        BarClip c = new BarClip(to - from + 1);
        int a = from * STEPS_PER_BAR, b = (to + 1) * STEPS_PER_BAR;
        for (Note n : notes) {
            if (n.end() <= a || n.start >= b) continue;
            int s = Math.max(n.start, a), e = Math.min(n.end(), b);
            c.notes.add(new Note(s - a, e - s, n.midi));
        }
        for (int i = from; i <= to; i++) { c.chords.add(chords.get(i)); c.beats.add(beats.get(i)); c.voices.add(voices.get(i)); }
        return c;
    }

    /** Empties bars from..to: their notes go and their chords become "none". A note that started before the first bar is cut where
     *  the range begins; one that runs past the last bar keeps its tail, starting again right after the range. */
    public void clearBars(int from, int to) {
        normalize();
        from = Math.max(0, Math.min(bars - 1, from));
        to = Math.max(from, Math.min(bars - 1, to));
        int a = from * STEPS_PER_BAR, b = (to + 1) * STEPS_PER_BAR;
        List<Note> tails = new ArrayList<>();
        for (Iterator<Note> it = notes.iterator(); it.hasNext(); ) {
            Note n = it.next();
            if (n.end() <= a || n.start >= b) continue;
            if (n.start < a) {                                          // started earlier: cut where the range begins...
                if (n.end() > b) tails.add(new Note(b, n.end() - b, n.midi)); // ...and if it runs right through, what's past the range survives
                n.len = a - n.start;
            }
            else if (n.end() > b) { n.len = n.end() - b; n.start = b; } // started inside, runs on: only the part past the range is left
            else it.remove();
        }
        notes.addAll(tails);
        for (int i = from; i <= to; i++) { chords.set(i, NO_CHORD); beats.set(i, Beat.NONE); }
        normalize();
    }

    /** Pastes a clip so it starts at bar `at`, replacing what was there (notes AND chords). The tune grows to fit (up to MAX_BARS).
     *  Returns how many bars went in. */
    public int pasteBars(BarClip clip, int at) {
        normalize();
        at = Math.max(0, Math.min(MAX_BARS - 1, at));
        int n = Math.min(clip.bars, MAX_BARS - at);
        if (n <= 0) return 0;
        if (at + n > bars) { bars = at + n; normalize(); }             // (normalize pads the chord list)
        clearBars(at, at + n - 1);
        int base = at * STEPS_PER_BAR, limit = n * STEPS_PER_BAR;
        for (Note c : clip.notes) {
            if (c.start >= limit) continue;
            notes.add(new Note(base + c.start, Math.min(c.len, limit - c.start), c.midi));
        }
        for (int i = 0; i < n; i++) { chords.set(at + i, clip.chords.get(i)); beats.set(at + i, clip.beats.get(i)); voices.set(at + i, clip.voices.get(i)); }
        normalize();
        return n;
    }

    /** Moves bars from..to by `delta` bars (notes AND chords), leaving the old place empty. Returns the new first bar. */
    public int moveBars(int from, int to, int delta) {
        normalize();
        from = Math.max(0, Math.min(bars - 1, from));
        to = Math.max(from, Math.min(bars - 1, to));
        delta = Math.max(-from, Math.min(MAX_BARS - 1 - to, delta));
        if (delta == 0) return from;
        BarClip c = copyBars(from, to);
        clearBars(from, to);
        pasteBars(c, from + delta);
        return from + delta;
    }

    /** Trims bars from..to OUT of the tune and closes the gap: everything after them moves left, the tune gets shorter. */
    public void removeBars(int from, int to) {
        normalize();
        from = Math.max(0, Math.min(bars - 1, from));
        to = Math.max(from, Math.min(bars - 1, to));
        int n = to - from + 1, b = (to + 1) * STEPS_PER_BAR;
        clearBars(from, to);
        for (Note x : notes) if (x.start >= b) x.start -= n * STEPS_PER_BAR;
        for (int i = 0; i < n; i++) { chords.remove(from); beats.remove(from); voices.remove(from); }
        bars -= n;
        drumsFromBar = shiftMarker(drumsFromBar, from, to);
        fullSoundFromBar = shiftMarker(fullSoundFromBar, from, to);
        normalize();                                                    // (never shorter than MIN_BARS: it pads)
    }

    private static int shiftMarker(int marker, int from, int to) {
        if (marker > to) return marker - (to - from + 1);
        return marker > from ? from : marker;
    }

    /** Trims the tune down to bars from..to: everything before and after them is thrown away. */
    public void cropToBars(int from, int to) {
        BarClip c = copyBars(from, to);
        int first = Math.max(0, Math.min(bars - 1, from));
        notes.clear();
        chords.clear();
        beats.clear();
        voices.clear();
        notes.addAll(c.notes);
        chords.addAll(c.chords);
        beats.addAll(c.beats);
        voices.addAll(c.voices);
        bars = c.bars;
        drumsFromBar = Math.max(0, drumsFromBar - first);
        fullSoundFromBar = Math.max(0, fullSoundFromBar - first);
        normalize();
    }

    public WorkshopTune copy() {
        WorkshopTune t = new WorkshopTune();
        t.bpm = bpm; t.bars = bars; t.drumsFromBar = drumsFromBar; t.fullSoundFromBar = fullSoundFromBar;
        for (Note n : notes) { Note c = new Note(n.start, n.len, n.midi); c.tied = n.tied; t.notes.add(c); }
        t.chords.addAll(chords);
        t.beats.addAll(beats);
        t.voices.addAll(voices);
        return t;
    }

    /** The saved-file form. Also used by the editor to tell whether anything changed, and for undo. */
    public String toText() {
        WorkshopTune t = copy();
        t.normalize();
        StringBuilder sb = new StringBuilder();
        sb.append("# Last Stand workshop track (\"??? 2\" in the Sound Test) -- written by the in-game Music Editor.\n");
        sb.append("# Delete this file to get the starter tune back.\n");
        sb.append("bpm=").append(t.bpm).append('\n');
        sb.append("bars=").append(t.bars).append('\n');
        sb.append("drums=").append(t.drumsFromBar).append('\n');
        sb.append("full=").append(t.fullSoundFromBar).append('\n');
        sb.append("chords=").append(String.join(" ", t.chords)).append('\n');
        boolean customBeats = false;
        for (Beat b : t.beats) if (b != Beat.ROCK) customBeats = true;
        if (customBeats) {                                               // one drum pattern per bar (the "drums=" bar still decides where drums start)
            List<String> codes = new ArrayList<>();
            for (Beat b : t.beats) codes.add(b.code);
            sb.append("beats=").append(String.join(" ", codes)).append('\n');
        }
        boolean customVoices = false;
        for (int b = 0; b < t.voices.size(); b++) if (t.voices.get(b) != (b < t.fullSoundFromBar ? Lead.REEDY : Lead.FULL)) customVoices = true;
        if (customVoices) {                                              // one lead sound per bar ("full=" still marks the original thin -> full switch)
            List<String> codes = new ArrayList<>();
            for (Lead l : t.voices) codes.add(l.code);
            sb.append("voices=").append(String.join(" ", codes)).append('\n');
        }
        String[] mel = t.melodyBars();
        for (int b = 0; b < mel.length; b++) sb.append("bar").append(b + 1).append('=').append(mel[b]).append('\n');
        return sb.toString();
    }

    /** Reads the text form. Throws IllegalArgumentException with a plain-language reason if it's unreadable. */
    public static WorkshopTune fromText(String text) {
        WorkshopTune t = new WorkshopTune();
        String[] melody = new String[MAX_BARS];
        int declaredBars = -1, rawNext = 0;
        boolean sawBpm = false, sawChords = false;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            String key = eq < 0 ? "" : line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            boolean known = key.equals("bpm") || key.equals("bars") || key.equals("drums") || key.equals("full") || key.equals("chords") || key.equals("beats") || key.equals("voices") || key.matches("bar\\d+");
            if (!known) {
                if (eq >= 0 && key.matches("[a-z][a-z0-9_]*")) continue;                // some other setting we don't know: ignore it
                String cleaned = cleanBar(line);                                         // forgiving: a bar pasted straight out of code
                if (!looksLikeBar(cleaned))                                              //  (quotes, a trailing comma, a // comment, no "barN=")
                    throw new IllegalArgumentException("unreadable line: \"" + line + "\" (a bar line looks like  bar1=E5 1, G5 .5, R .5)");
                while (rawNext < MAX_BARS && melody[rawNext] != null) rawNext++;
                if (rawNext < MAX_BARS) melody[rawNext++] = cleaned;
                continue;
            }
            String val = line.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "bpm" -> { t.bpm = (int) Math.round(Double.parseDouble(val)); sawBpm = true; }
                    case "bars" -> declaredBars = Integer.parseInt(val);
                    case "drums" -> t.drumsFromBar = Integer.parseInt(val);
                    case "full" -> t.fullSoundFromBar = Integer.parseInt(val);
                    case "voices" -> {
                        for (String word : val.split("[\\s,]+")) {
                            if (word.isEmpty()) continue;
                            Lead l = Lead.fromCode(word);
                            if (l == null) { t.notices.add("voices= has \"" + word + "\" for bar " + (t.voices.size() + 1) + ", which isn't a vocal sound, so that bar uses full"); l = Lead.FULL; }
                            t.voices.add(l);
                        }
                    }
                    case "beats" -> {
                        for (String word : val.split("[\\s,]+")) {
                            if (word.isEmpty()) continue;
                            Beat b = Beat.fromCode(word);
                            if (b == null) { t.notices.add("beats= has \"" + word + "\" for bar " + (t.beats.size() + 1) + ", which isn't a drum pattern, so that bar uses rock"); b = Beat.ROCK; }
                            t.beats.add(b);
                        }
                    }
                    case "chords" -> {
                        sawChords = true;
                        for (String c : val.split("[\\s,]+")) { c = normChord(c); if (!c.isEmpty()) t.chords.add(c); }
                    }
                    default -> {
                        int n = Integer.parseInt(key.substring(3));
                        if (n >= 1 && n <= MAX_BARS) melody[n - 1] = cleanBar(val);
                    }
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("can't read \"" + line + "\"");
            }
        }
        int last = -1;
        for (int b = 0; b < MAX_BARS; b++) if (melody[b] != null) last = b;
        if (declaredBars < 0) {                                                            // no bars= line: use as many bars as were given
            if (last < 0) throw new IllegalArgumentException("no bars found (a tune file needs lines like  bar1=E5 1, G5 .5, R .5)");
            declaredBars = last + 1;
        }
        if (last >= declaredBars) {                                                        // more bar lines than bars= says: don't quietly drop the extra bars
            t.notices.add("bars= says " + declaredBars + " but the file has bar lines up to bar " + (last + 1) + ", so it uses " + (last + 1) + " bars");
            declaredBars = last + 1;
        }
        t.bars = Math.max(MIN_BARS, Math.min(MAX_BARS, declaredBars));
        for (int b = 0; b < t.bars; b++) if (melody[b] != null) readBar(t, b, melody[b]);
        if (!sawBpm) t.notices.add("there is no bpm= line, so the speed is " + t.bpm);
        if (!sawChords) t.notices.add("there is no chords= line, so every bar has the chord C");
        t.normalize();
        return t;
    }

    /** Things the loader had to assume (shown to the player instead of failing quietly). */
    public final List<String> notices = new ArrayList<>();

    /** Tidies a bar pasted out of code: drops a // comment, wrapping quotes, and a trailing comma / semicolon / brace. */
    static String cleanBar(String s) {
        int c = s.indexOf("//");
        if (c >= 0) s = s.substring(0, c);
        String prev;
        do {
            prev = s;
            s = s.trim();
            while (!s.isEmpty() && ",;})]".indexOf(s.charAt(s.length() - 1)) >= 0) s = s.substring(0, s.length() - 1).trim();
            while (!s.isEmpty() && "{[(".indexOf(s.charAt(0)) >= 0) s = s.substring(1).trim();
            s = s.replaceAll("^[\"\u201c\u201d']+|[\"\u201c\u201d']+$", "");
        } while (!s.equals(prev));
        return s.trim();
    }

    /** Does this look like a bar of notes ("E5 1, G5 .5, R .5 ...")? */
    static boolean looksLikeBar(String s) {
        return s.matches("(?i)^(R|T|[A-G][#b]?\\d)\\s+\\.?\\d+(\\.\\d+)?\\s*(,.*)?$");
    }

    /** One chord name as written in a file: tidies quotes, and turns "none" / "n" / "-" into the no-chord marker. */
    static String normChord(String c) {
        c = c.trim().replaceAll("^[\"\u201c\u201d']+|[\"\u201c\u201d']+$", "");
        return c.isEmpty() ? "" : (isNoChord(c) ? NO_CHORD : c);
    }

    public static void readBar(WorkshopTune t, int bar, String text) {
        int pos = bar * STEPS_PER_BAR;
        for (String token : text.split(",")) {
            String s = token.trim();
            if (s.isEmpty()) continue;
            String[] p = s.split("\\s+");
            if (p.length < 2) throw new IllegalArgumentException("bar " + (bar + 1) + ": can't read \"" + s + "\"");
            int steps;
            try { steps = (int) Math.round(Double.parseDouble(p[1]) * 4); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("bar " + (bar + 1) + ": can't read \"" + s + "\""); }
            if (steps < 1) steps = 1;
            if (p[0].equals("T")) {                                                       // a tie: carry on the note right before this one
                Note prev = t.notes.isEmpty() ? null : t.notes.get(t.notes.size() - 1);
                if (prev == null || prev.end() != pos)
                    throw new IllegalArgumentException("bar " + (bar + 1) + ": \"T\" holds on to the note right before it, but no note touches this spot (a rest in between ends the note).");
                Note c = new Note(pos, steps, prev.midi);
                c.tied = true;
                t.notes.add(c);
            } else if (!p[0].equals("R")) {
                try { t.notes.add(new Note(pos, steps, parseNote(p[0]))); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("bar " + (bar + 1) + ": " + e.getMessage()); }
            }
            pos += steps;
        }
    }

    // ------------------------------------------------------------------ the built-in starter tune

    /** The starter: the chord plan C-D-Bm-Em x4 with a plain placeholder melody (chord notes -- not a real tune), ready to be replaced. */
    public static WorkshopTune defaults() {
        WorkshopTune t = new WorkshopTune();
        t.bpm = 96; t.bars = 16; t.drumsFromBar = 8; t.fullSoundFromBar = 8;
        String[] pattern = {"E5 1, G5 1, E5 1, C5 1", "F#5 1, A5 1, F#5 1, D5 1", "D5 1, F#5 1, D5 1, B4 1", "B4 1, E5 1, G5 1, E5 1"};
        String[] names = {"C", "D", "Bm", "Em"};
        for (int b = 0; b < 16; b++) {
            t.chords.add(names[b % 4]);
            readBar(t, b, b == 15 ? "E5 3, R 1" : pattern[b % 4]);
        }
        t.normalize();
        return t;
    }

    // ------------------------------------------------------------------ disk

    /** The saved tune if the file exists and reads fine, otherwise the starter. (A broken file is reported on stderr, not fatal.) */
    private static volatile String loadProblem = null;

    /** null if the saved tune file is fine (or there isn't one); otherwise a plain sentence about why it isn't being used, or what had to be assumed. */
    public static String loadProblem() { return loadProblem; }

    public static WorkshopTune loadOrDefault() {
        loadProblem = null;
        if (FILE.isFile()) {
            try {
                WorkshopTune t = fromText(new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8));
                if (!t.notices.isEmpty()) loadProblem = "Read your tune file, but " + String.join(" and ", t.notices) + ".";
                return t;
            } catch (IOException | RuntimeException e) {
                loadProblem = "Couldn't read " + FILE.getName() + ", so the starter tune plays instead: " + e.getMessage();
                System.err.println("[WorkshopTune] " + loadProblem);
            }
        }
        return defaults();
    }

    /** Writes the tune to {@link #FILE} (to a temp file first, so a crash can't leave half a file). */
    public void save() throws IOException {
        File tmp = new File(FILE.getPath() + ".tmp");
        Files.write(tmp.toPath(), toText().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
