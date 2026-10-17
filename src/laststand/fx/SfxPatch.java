package laststand.fx;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One sound effect as plain, editable data: a short list of "tones" (a square / triangle / saw / noise / air layer with a
 * start time, length, pitch slide, volume ...). This is exactly what the recipes in Sfx.java are made of, so every
 * built-in effect can be turned into a patch ({@link Sfx#builtinPatch}) and edited in the in-game SFX Editor
 * (ui/SfxEditorUI).
 *
 * Edits are saved in {@link #FILE} (laststand_sfx.txt, next to the game's save files), one [SECTION] per edited effect.
 * The game then plays the saved version instead of the built-in recipe -- everywhere, not just on the Sound Test.
 * Removing an effect's section (or deleting the file) brings the built-in sound back. The two hidden tracks are not
 * patches: "??? 2" has its own editor ({@link WorkshopTune}).
 */
public final class SfxPatch {

    public static final File FILE = new File("laststand_sfx.txt");
    public static final String[] WAVES = {"SQUARE", "TRIANGLE", "SAW", "NOISE", "NOISE_SHORT", "AIR"};
    public static final int MAX_TONES = 40;

    /** One layer of the sound. Times are in seconds, pitches in Hz (for the noise waves: the noise generator's clock). */
    public static final class Tone {
        public String wave = "SQUARE";
        public double at = 0, dur = 0.1, f0 = 880, f1 = 880, vol = 0.3;
        public double duty = 0.5;          // SQUARE: share of each cycle that is "high" (0.125 thin & reedy ... 0.5 full)
        public double decay = 1.6;         // how fast it fades out (higher = faster)
        public double attack = 0.002;      // fade-in, seconds
        public double vibHz = 0, vibDepth = 0;
        public double q = 1.1;             // AIR: how narrow the airy band is

        public Tone copy() {
            Tone t = new Tone();
            t.wave = wave; t.at = at; t.dur = dur; t.f0 = f0; t.f1 = f1; t.vol = vol; t.duty = duty;
            t.decay = decay; t.attack = attack; t.vibHz = vibHz; t.vibDepth = vibDepth; t.q = q;
            return t;
        }

        public boolean isNoise() { return wave.equals("NOISE") || wave.equals("NOISE_SHORT"); }
    }

    /** true = skip the retro 8-bit rounding (for smooth, realistic sounds built from AIR). */
    public boolean smooth = false;
    public final List<Tone> tones = new ArrayList<>();

    public SfxPatch copy() {
        SfxPatch p = new SfxPatch();
        p.smooth = smooth;
        for (Tone t : tones) p.tones.add(t.copy());
        return p;
    }

    // ------------------------------------------------------------------ text form

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    /** The patch as text (also how the editor tells whether anything changed, and how undo remembers). */
    public String toBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("smooth=").append(smooth).append('\n');
        for (Tone t : tones) {
            sb.append("tone=").append(t.wave)
              .append(" at=").append(num(t.at)).append(" dur=").append(num(t.dur))
              .append(" f0=").append(num(t.f0)).append(" f1=").append(num(t.f1))
              .append(" vol=").append(num(t.vol)).append(" duty=").append(num(t.duty))
              .append(" decay=").append(num(t.decay)).append(" attack=").append(num(t.attack))
              .append(" vibHz=").append(num(t.vibHz)).append(" vibDepth=").append(num(t.vibDepth))
              .append(" q=").append(num(t.q)).append('\n');
        }
        return sb.toString();
    }

    /** Reads {@link #toBody}'s text. Throws IllegalArgumentException with a plain reason if it's unreadable. */
    public static SfxPatch fromBody(String body) {
        SfxPatch p = new SfxPatch();
        for (String raw : body.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("smooth=")) { p.smooth = line.substring(7).trim().equalsIgnoreCase("true"); continue; }
            if (!line.startsWith("tone=")) throw new IllegalArgumentException("unreadable line: " + line);
            String[] parts = line.split("\\s+");
            Tone t = new Tone();
            t.wave = parts[0].substring(5);
            boolean known = false;
            for (String w : WAVES) known |= w.equals(t.wave);
            if (!known) throw new IllegalArgumentException("unknown wave \"" + t.wave + "\"");
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("unreadable: " + parts[i]);
                String k = parts[i].substring(0, eq);
                double v;
                try { v = Double.parseDouble(parts[i].substring(eq + 1)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("can't read \"" + parts[i] + "\""); }
                switch (k) {
                    case "at" -> t.at = v; case "dur" -> t.dur = v; case "f0" -> t.f0 = v; case "f1" -> t.f1 = v;
                    case "vol" -> t.vol = v; case "duty" -> t.duty = v; case "decay" -> t.decay = v;
                    case "attack" -> t.attack = v; case "vibHz" -> t.vibHz = v; case "vibDepth" -> t.vibDepth = v;
                    case "q" -> t.q = v;
                    default -> throw new IllegalArgumentException("unknown setting \"" + k + "\"");
                }
            }
            p.tones.add(t);
        }
        if (p.tones.size() > MAX_TONES) throw new IllegalArgumentException("more than " + MAX_TONES + " tones");
        return p;
    }

    // ------------------------------------------------------------------ the file of saved edits

    private static volatile Map<String, SfxPatch> loaded = null;

    /** The saved edit for this effect (Sfx.Id name, e.g. "HIT_ENEMY"), or null if it hasn't been edited. */
    public static SfxPatch overrideFor(String id) {
        Map<String, SfxPatch> m = loaded;
        if (m == null) { reload(); m = loaded; }
        return m.get(id);
    }

    /** Re-reads the file (after the editor saved). */
    public static synchronized void reload() { loaded = readFile(); }

    private static Map<String, SfxPatch> readFile() {
        Map<String, SfxPatch> out = new LinkedHashMap<>();
        if (!FILE.isFile()) return out;
        try {
            String current = null;
            StringBuilder body = new StringBuilder();
            for (String raw : (new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8) + "\n[END]").split("\\R")) {
                String line = raw.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (current != null) {
                        try { out.put(current, fromBody(body.toString())); }
                        catch (IllegalArgumentException e) { System.err.println("[SfxPatch] ignoring [" + current + "] in " + FILE + ": " + e.getMessage()); }
                    }
                    current = line.substring(1, line.length() - 1).trim();
                    body.setLength(0);
                } else if (current != null) {
                    body.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            System.err.println("[SfxPatch] couldn't read " + FILE + ": " + e.getMessage());
        }
        return out;
    }

    /** Saves (or replaces) one effect's edit. */
    public static synchronized void store(String id, SfxPatch patch) throws IOException {
        Map<String, SfxPatch> all = new LinkedHashMap<>(readFile());
        all.put(id, patch.copy());
        write(all);
    }

    /** Forgets one effect's edit (the built-in sound comes back). Deletes the file when nothing is left in it. */
    public static synchronized void remove(String id) throws IOException {
        Map<String, SfxPatch> all = new LinkedHashMap<>(readFile());
        if (all.remove(id) == null) return;
        write(all);
    }

    private static void write(Map<String, SfxPatch> all) throws IOException {
        if (all.isEmpty()) {
            Files.deleteIfExists(FILE.toPath());
        } else {
            StringBuilder sb = new StringBuilder("# Last Stand sound effect edits -- written by the in-game SFX Editor (Sound Test: select a sound, press E).\n"
                    + "# Delete a [SECTION] (or this whole file) to get that sound's built-in version back.\n");
            for (Map.Entry<String, SfxPatch> e : all.entrySet()) sb.append('\n').append('[').append(e.getKey()).append("]\n").append(e.getValue().toBody());
            File tmp = new File(FILE.getPath() + ".tmp");
            Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        loaded = readFile();
    }
}
