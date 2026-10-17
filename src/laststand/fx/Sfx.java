package laststand.fx;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sound effects, made entirely in code -- no audio files. A tiny chiptune synthesizer in the style
 * of the NES / Game Boy: square waves (with adjustable pulse width), triangle, sawtooth and a
 * noise channel, with pitch slides, vibrato and 8-bit quantization for that crunchy sound.
 * Every effect is a short "recipe" in {@link #recipe(Id)} -- pitch, length and loudness are plain
 * numbers, so retuning a sound is a one-line edit.
 *
 * Playback goes through a small software mixer feeding ONE audio line, so any number of effects can
 * overlap without opening a pile of lines. If the machine has no usable audio device the whole
 * thing quietly does nothing; the game never depends on it.
 *
 * To replace a generated sound with a recorded one, drop a 16-bit (or 8-bit) PCM WAV named after
 * the effect into src/sounds/ (e.g. src/sounds/hit_enemy.wav, any sample rate / mono or stereo) so it
 * ends up at /sounds/ on the classpath; it is used instead of the synthesized version.
 */
public final class Sfx {
    private Sfx() {}

    public static final int SAMPLE_RATE = 22050;
    /** Overall loudness of the mix, 0..1. */
    private static final float MASTER = 0.65f;
    /** Quantize to 8-bit levels -- the gritty "8-bit" character. Turn off for a cleaner sound. */
    private static final boolean CRUSH_TO_8_BIT = true;
    private static final int MAX_VOICES = 14;
    private static final int BLOCK = 256; // mixer block, samples (~11.6 ms)
    /** The card's buffer. Small = low latency, because we keep it full (of silence, when idle) and a new effect has to wait behind it. */
    private static final int LINE_BUFFER_BYTES = 2048; // ~46 ms

    /** Every sound effect. minGapMs stops rapid repeats (e.g. a pack of enemies getting hit) from turning into noise. */
    public enum Id {
        MENU_MOVE(0), MENU_SELECT(0),
        SHOOT_ARROW(0), THROW_DAGGER(0), PIN_STAB(0),
        HIT_ENEMY(45), ENEMY_DEATH(55), PLAYER_HURT(110),
        POTION(0), SHIELD_ON(0), BUY(0), ERROR(80), LEVEL_UP(0),
        ULTIMATE_READY(0), GIANT_ARROW(0), THORNS_ON(0), THORNS_PULSE(120),
        WAVE_START(0), BOSS_WARN(0), WAVE_CLEAR(0), GAME_OVER(0),
        /** A hidden 8-bit tune. The game never plays it -- it only lives on the Ctrl+S Sound Test, under "MISC". */
        SECRET_TRACK(0),
        /** A copy of the hidden tune to edit and make your own (see workshopTrack). Also Sound Test only. */
        SECRET_TRACK_2(0);

        final int minGapMs;
        Id(int minGapMs) { this.minGapMs = minGapMs; }
        /** In-game rate limit for this effect, in ms (0 = none). Shown on the Sound Test screen. */
        public int minGapMs() { return minGapMs; }
    }

    // ================================================================== public API

    private static volatile boolean muted = false;
    /** Called for every play() request (before muting / rate limiting) -- handy for tests. */
    public static volatile Consumer<Id> listener = null;

    public static void play(Id id) {
        Consumer<Id> l = listener;
        if (l != null) l.accept(id);
        if (muted) return;
        if (id.minGapMs > 0) {
            long now = System.nanoTime() / 1_000_000L;
            synchronized (lastPlayedMs) {
                if (now - lastPlayedMs[id.ordinal()] < id.minGapMs) return;
                lastPlayedMs[id.ordinal()] = now;
            }
        }
        enqueue(id);
    }

    private static void enqueue(Id id) {
        ensureInit();
        if (!lineAvailable && !testMode) return;
        short[] data = samples(id);
        synchronized (voices) {
            makeRoom();
            voices.add(new Voice(data));
        }
    }

    /** Drops the oldest voice rather than pile up -- but never a running loop (caller holds the voices lock). */
    private static void makeRoom() {
        if (voices.size() < MAX_VOICES) return;
        for (int i = 0; i < voices.size(); i++) if (!voices.get(i).loop) { voices.remove(i); return; }
        voices.remove(0);
    }

    /**
     * Plays a track on repeat, seamlessly, until silence() or a mute. This is also what the game can call for background
     * music (e.g. Sfx.playLoop(Sfx.Id.SECRET_TRACK)). A sound that isn't a loopable tune just plays once.
     */
    public static void playLoop(Id id) {
        Consumer<Id> l = listener;
        if (l != null) l.accept(id);
        if (muted) return;
        ensureInit();
        if (!lineAvailable && !testMode) return;
        short[] data = loopSamplesFor(id);
        boolean loop = data != null;
        if (!loop) data = samples(id);
        synchronized (voices) {
            makeRoom();
            voices.add(new Voice(data, loop));
        }
    }

    /**
     * The seamless-loop version of a track: exactly its length in bars, with the last bar flowing on into the first
     * (instead of the one-shot's held ending) and any ringing tail wrapped onto the start. null if it can't loop.
     */
    public static short[] loopSamplesFor(Id id) {
        int i = id.ordinal();
        synchronized (cache) { if (loopDone[i]) return loopCache[i]; }
        short[] s = null;                                              // render outside the lock: other sounds keep playing meanwhile
        if (!isRecordedOverride(id) && errorFor(id) == null) {         // (a WAV override or a broken recipe can't loop)
            try {
                Mix mix = recipe(id, true);
                if (mix.loopSeconds > 0) s = toPcm(mix, mix.loopSeconds);
            } catch (RuntimeException e) { s = null; }
        }
        synchronized (cache) { loopCache[i] = s; loopDone[i] = true; return s; }
    }

    /** How long one pass of the loop is, in seconds (0 if this sound can't loop). */
    public static double loopSecondsFor(Id id) {
        short[] s = loopSamplesFor(id);
        return s == null ? 0 : s.length / (double) SAMPLE_RATE;
    }

    // ---- Sound Test hooks (the secret Ctrl+S screen, see ui/SoundTestUI) ----

    /** Plays an effect exactly as the game would, but skips the rate limit so mashing a key always sounds. Respects mute. */
    public static void preview(Id id) {
        Consumer<Id> l = listener;
        if (l != null) l.accept(id);
        if (muted) return;
        enqueue(id);
    }

    /** Cuts off everything currently playing (the Sound Test's \"stop\"). */
    public static void silence() {
        previewGen++;                                               // (also cancels an editor preview that is still rendering)
        synchronized (voices) { voices.removeIf(v -> !v.music); }   // (background music has its own stopMusic())
    }

    // ---- SFX Editor hooks (see ui/SfxEditorUI) ----

    /** The built-in recipe of an effect as an editable patch (null for the two hidden tracks, which aren't patches). */
    public static SfxPatch builtinPatch(Id id) {
        if (isTune(id)) return null;
        Mix m = builtinRecipe(id, false);
        SfxPatch p = new SfxPatch();
        p.smooth = m.smooth;
        for (Tone t : m.tones) {
            SfxPatch.Tone o = new SfxPatch.Tone();
            o.wave = t.wave.name(); o.at = t.at; o.dur = t.dur; o.f0 = t.f0; o.f1 = t.f1; o.vol = t.vol; o.duty = t.duty;
            o.decay = t.decayPow; o.attack = t.attack; o.vibHz = t.vibHz; o.vibDepth = t.vibDepth; o.q = t.q;
            p.tones.add(o);
        }
        return p;
    }

    /** True if this effect currently plays the player's saved edit instead of the built-in recipe. */
    public static boolean hasSfxEdit(Id id) { return !isTune(id) && SfxPatch.overrideFor(id.name()) != null; }

    /** Renders a patch to 16-bit mono PCM -- exactly what the game would play if it were saved. */
    public static short[] renderPatch(SfxPatch p) { return toPcm(mixFromPatch(p)); }

    /** Plays a patch that hasn't been saved (the SFX Editor's PLAY). Replaces any previous preview. Respects mute. */
    public static void previewPatch(SfxPatch p) {
        stopPreview();
        if (muted) return;
        ensureInit();
        if (!lineAvailable && !testMode) return;
        short[] data;
        try { data = renderPatch(p); } catch (RuntimeException e) { return; }
        synchronized (voices) {
            makeRoom();
            Voice v = new Voice(data);
            v.preview = true;
            previewVoice = v;
            voices.add(v);
        }
    }

    /** Call after the SFX Editor saves or resets an effect: the game re-reads the file and re-renders that sound. */
    public static void reloadSfx(Id id) {
        SfxPatch.reload();
        int i = id.ordinal();
        synchronized (cache) { cache[i] = null; overridden[i] = false; errors[i] = null; loopCache[i] = null; loopDone[i] = false; }
    }

    private static Mix mixFromPatch(SfxPatch p) {
        Mix m = new Mix();
        m.smooth = p.smooth;
        for (SfxPatch.Tone o : p.tones) {
            if (!(o.f0 > 0) || !(o.f1 > 0) || !(o.dur > 0))
                throw new IllegalArgumentException("a tone needs a length and pitches above zero");
            Tone t = new Tone(Wave.valueOf(o.wave), Math.max(0, o.at), o.dur, o.f0, o.f1, o.vol, o.duty);
            t.decayPow = o.decay; t.attack = Math.max(1e-4, o.attack); t.vibHz = o.vibHz; t.vibDepth = o.vibDepth; t.q = Math.max(0.1, o.q);
            m.add(t);
        }
        return m;
    }

    // ---- Music Editor hooks (see ui/MusicEditorUI) ----

    private static volatile int previewGen = 0;
    private static volatile Voice previewVoice = null;

    /** Call after the editor saves: the next play of "??? 2" re-reads the saved tune instead of using the old rendering. */
    public static void reloadWorkshop() {
        int i = Id.SECRET_TRACK_2.ordinal();
        synchronized (cache) { cache[i] = null; overridden[i] = false; errors[i] = null; loopCache[i] = null; loopDone[i] = false; }
    }

    /**
     * Plays a tune that hasn't been saved (what the editor's PLAY button does). It's rendered on a background thread -- a
     * long tune takes a moment -- and starts as soon as it's ready. With loop = true it repeats seamlessly until stopped.
     * Pressing it again replaces the previous preview. Returns false if sound is off / unavailable.
     */
    public static boolean previewTune(WorkshopTune tune, boolean loop) {
        stopPreview();
        if (muted) return false;
        ensureInit();
        if (!lineAvailable && !testMode) return false;
        final WorkshopTune t = tune.copy();
        final int gen = ++previewGen;
        Thread th = new Thread(() -> {
            try {
                Mix mix = new Mix();
                mix.loopMode = loop;
                applyTune(mix, t);
                short[] data = toPcm(mix, loop ? mix.loopSeconds : 0);
                synchronized (voices) {
                    if (gen != previewGen || muted) return;               // stopped (or muted) while it was rendering
                    makeRoom();
                    Voice v = new Voice(data, loop);
                    v.preview = true;
                    previewVoice = v;
                    voices.add(v);
                }
            } catch (RuntimeException e) {
                System.err.println("[Sfx] preview: " + e.getMessage());
            }
        }, "tune-preview");
        th.setDaemon(true);
        th.start();
        return true;
    }

    /** Stops the editor's preview (and anything rendering for it). */
    public static void stopPreview() {
        previewGen++;
        synchronized (voices) { voices.removeIf(v -> v.preview); }
        previewVoice = null;
    }

    /** Where the preview is now, in seconds from the start of the tune (wraps when looping); -1 if nothing is playing. */
    public static double previewSeconds() {
        Voice v = previewVoice;
        if (v == null) return -1;
        synchronized (voices) { if (!voices.contains(v)) { previewVoice = null; return -1; } }
        return v.pos / (double) SAMPLE_RATE;
    }

    /** A short blip of one pitch (MIDI number), so notes can be heard as they're placed. Respects mute. */
    public static void previewNote(int midi) {
        if (muted) return;
        ensureInit();
        if (!lineAvailable && !testMode) return;
        Mix mix = new Mix();
        mix.add(sq(0, 0.20, hz(midi), hz(midi), 0.17, 0.25).attack(0.006).decay(0.9));
        short[] data = toPcm(mix);
        synchronized (voices) {
            voices.removeIf(v -> v.preview && v.data.length < SAMPLE_RATE / 2);    // a new blip replaces the last blip
            makeRoom();
            Voice v = new Voice(data);
            v.preview = true;
            voices.add(v);
        }
    }

    // ---- background music (the game plays a looping track during waves; see GamePanel.updateMusic) ----

    /** How loud background music is compared with a normal effect (1 = same). Turned down so effects stay clear on top. */
    public static final float MUSIC_GAIN = 0.30f;

    /** The player's music volume as a percent of MUSIC_GAIN (100 = the original loudness). Only the hidden songs ever play
     *  as background music, so this is only ever changed while one of them is on (see GamePanel.adjustMusicVolume). */
    public static final int MUSIC_VOLUME_MAX_PCT = 200;
    private static volatile int musicVolumePct = 100;

    public static int musicVolumePct() { return musicVolumePct; }

    /** Sets the music volume (clamped to 0..200%) and applies it to the music that is playing right now. */
    public static void setMusicVolumePct(int pct) {
        musicVolumePct = Math.max(0, Math.min(MUSIC_VOLUME_MAX_PCT, pct));
        synchronized (voices) {
            for (Voice v : voices) if (v.music) v.gain = MUSIC_GAIN * musicVolumePct / 100f;
        }
    }

    /** Starts looping a tune as background music (replacing any music already playing). Only tunes can be music. */
    public static void startMusic(Id id) {
        if (muted) return;
        ensureInit();
        if (!lineAvailable && !testMode) return;
        short[] data = loopSamplesFor(id);
        if (data == null) return;
        synchronized (voices) {
            voices.removeIf(v -> v.music);
            makeRoom();
            Voice v = new Voice(data, true);
            v.gain = MUSIC_GAIN * musicVolumePct / 100f;
            v.music = true;
            voices.add(v);
            musicId = id;
        }
    }

    private static volatile Id musicId = null;                       // which tune the music voice is playing

    public static void stopMusic() { synchronized (voices) { voices.removeIf(v -> v.music); } musicId = null; }

    /** The tune that is playing as background music right now, or null if there is none. */
    public static Id currentMusic() { return isMusicPlaying() ? musicId : null; }

    public static boolean isMusicPlaying() {
        synchronized (voices) { for (Voice v : voices) if (v.music) return true; }
        return false;
    }

    /** True if a sound card line opened successfully (opens it now if that hasn't been tried yet). */
    public static boolean isAudioAvailable() {
        ensureInit();
        return lineAvailable || testMode;
    }

    /** The exact samples the game plays for this effect: the WAV override if one exists, else the synthesized recipe. */
    public static short[] samplesFor(Id id) { return samples(id); }

    /** If this effect's recipe has a mistake, the plain-language reason (shown in red on the Sound Test); otherwise null. */
    public static String errorFor(Id id) {
        samples(id);
        return errors[id.ordinal()];
    }

    /** True if this effect is currently coming from a recorded WAV in src/sounds/ rather than the synthesizer. */
    public static boolean isRecordedOverride(Id id) {
        samples(id);
        return overridden[id.ordinal()];
    }

    /** Toggles mute and returns the new state (true = muted). Muting also silences anything currently playing. */
    public static boolean toggleMute() {
        muted = !muted;
        if (muted) synchronized (voices) { voices.clear(); }
        return muted;
    }

    public static boolean isMuted() { return muted; }

    /** Renders an effect to 16-bit mono PCM at SAMPLE_RATE (this is exactly what the game plays). */
    public static short[] render(Id id) {
        return toPcm(recipe(id));
    }

    // ================================================================== the recipes

    // MIDI-style note numbers, so melodies read naturally.
    private static final int C3 = 48, G3 = 55, C4 = 60, E4 = 64, A4 = 69, G4 = 67, B4 = 71, C5 = 72, D5 = 74, E5 = 76, G5 = 79,
            B5 = 83, C6 = 84, E6 = 88, G6 = 91, A2 = 45, F2 = 41, D2 = 38, D4 = 62, D3 = 50;

    private static Mix recipe(Id id) { return recipe(id, false); }

    /** An effect's sound: the player's saved edit from the SFX Editor if there is one, otherwise the built-in recipe below. */
    private static Mix recipe(Id id, boolean loopMode) {
        if (!isTune(id)) {
            SfxPatch edited = SfxPatch.overrideFor(id.name());
            if (edited != null) {
                Mix m = mixFromPatch(edited);
                m.loopMode = loopMode;
                return m;
            }
        }
        return builtinRecipe(id, loopMode);
    }

    private static boolean isTune(Id id) { return id == Id.SECRET_TRACK || id == Id.SECRET_TRACK_2; }

    private static Mix builtinRecipe(Id id, boolean loopMode) {
        Mix m = new Mix();
        m.loopMode = loopMode;
        switch (id) {
            case MENU_MOVE -> m.add(sq(0, 0.035, 880, 880, 0.30, 0.5));
            case MENU_SELECT -> {
                m.add(sq(0, 0.05, 660, 660, 0.32, 0.5));
                m.add(sq(0.05, 0.10, 990, 990, 0.32, 0.25));
            }
            case SHOOT_ARROW -> { // a bow snap: fast downward chirp + a tiny twang of noise
                m.add(sq(0, 0.11, 1500, 420, 0.38, 0.25).decay(2.0));
                m.add(noise(0, 0.04, 7000, 3000, 0.12, false));
            }
            case SECRET_TRACK -> hiddenTrack(m);
            case SECRET_TRACK_2 -> workshopTrack(m);
            case THROW_DAGGER -> { // a knife thrown: one short, focused "fwip" of air
                m.smooth = true;                                                       // skip the retro 8-bit grain for this sound
                m.add(air(0, 0.11, 3000, 1400, 0.28).q(2.5).attack(0.012).decay(1.4)); // air that dulls as the knife flies off
            }
            case PIN_STAB -> m.add(tri(0, 0.04, 1100, 700, 0.28));
            case HIT_ENEMY -> { // a thud
                m.add(noise(0, 0.05, 6000, 1500, 0.30, false).decay(2.0));
                m.add(sq(0, 0.05, 210, 100, 0.28, 0.5).decay(1.5));
            }
            case ENEMY_DEATH -> {
                m.add(sq(0, 0.20, 520, 90, 0.34, 0.125).vib(22, 0.04).decay(1.3));
                m.add(noise(0.02, 0.22, 2600, 300, 0.20, false).decay(1.6));
            }
            case PLAYER_HURT -> { // harsh and low so it cuts through everything else
                m.add(sq(0, 0.18, 360, 110, 0.42, 0.5).decay(1.2));
                m.add(saw(0, 0.16, 180, 70, 0.26));
                m.add(noise(0, 0.08, 4500, 1200, 0.20, false));
            }
            case POTION -> { // glug-glug upward
                m.add(tri(0.00, 0.06, hz(C5), hz(C5), 0.40));
                m.add(tri(0.05, 0.06, hz(E5), hz(E5), 0.40));
                m.add(tri(0.10, 0.07, hz(G5), hz(G5), 0.40));
                m.add(tri(0.16, 0.12, hz(C6), hz(C6), 0.38));
                m.add(noise(0, 0.14, 1800, 5200, 0.07, true));
            }
            case SHIELD_ON -> {
                m.add(sq(0, 0.12, hz(G4), hz(D5), 0.34, 0.25));
                m.add(sq(0.10, 0.18, hz(D5), hz(G5), 0.30, 0.5));
                m.add(tri(0.10, 0.22, 1760, 1760, 0.16).vib(14, 0.03));
            }
            case BUY -> {
                m.add(sq(0, 0.05, hz(B5), hz(B5), 0.32, 0.125));
                m.add(sq(0.05, 0.16, hz(E6), hz(E6), 0.32, 0.125));
            }
            case ERROR -> {
                m.add(sq(0, 0.07, 110, 110, 0.38, 0.5));
                m.add(sq(0.08, 0.10, 82, 82, 0.38, 0.5));
            }
            case LEVEL_UP -> {
                int[] notes = {C5, E5, G5, C6, E6};
                for (int i = 0; i < notes.length; i++) m.add(sq(i * 0.07, i == notes.length - 1 ? 0.28 : 0.075, hz(notes[i]), hz(notes[i]), 0.34, 0.25));
            }
            case ULTIMATE_READY -> { // electric zap + a sparkling rising arpeggio
                m.add(noise(0, 0.26, 8000, 2600, 0.20, false).decay(1.2));
                int[] notes = {C5, E5, G5, C6, E6, G6};
                for (int i = 0; i < notes.length; i++) m.add(sq(i * 0.045, 0.055, hz(notes[i]), hz(notes[i]), 0.30, 0.125));
                m.add(tri(0.27, 0.26, hz(C6), hz(C6), 0.26).vib(18, 0.02));
            }
            case GIANT_ARROW -> { // a heavy rising launch with a whoosh swelling underneath
                m.add(tri(0, 0.36, 90, 520, 0.50).decay(1.0));
                m.add(noise(0, 0.40, 1200, 5200, 0.26, false).attack(0.12).decay(1.4));
                m.add(sq(0.05, 0.30, 260, 1400, 0.20, 0.125).decay(1.3));
            }
            case THORNS_ON -> {
                m.add(sq(0, 0.26, 110, 330, 0.38, 0.5).decay(1.1));
                m.add(sq(0.05, 0.24, 220, 660, 0.24, 0.25).decay(1.2));
                m.add(noise(0, 0.07, 5000, 1500, 0.20, false));
            }
            case THORNS_PULSE -> { // short and quiet -- it repeats twice a second
                m.add(noise(0, 0.07, 5000, 1200, 0.18, false).decay(1.8));
                m.add(sq(0, 0.09, 900, 300, 0.16, 0.125));
            }
            case WAVE_START -> {
                m.add(sq(0, 0.09, hz(E4), hz(E4), 0.34, 0.25));
                m.add(sq(0.10, 0.15, hz(A4), hz(A4), 0.34, 0.25));
            }
            case BOSS_WARN -> { // a dramatic stinger: a swelling riser, a huge boom, then a slow doom motif with a clashing note
                m.add(saw(0, 0.50, hz(A2), hz(A2) * 3.0, 0.26).attack(0.42).decay(0.4));             // riser: a rising growl that swells...
                m.add(noise(0, 0.50, 800, 7000, 0.18, false).attack(0.42).decay(0.4));              //  ...and a rising wash of air
                m.add(tri(0.50, 0.80, 120, 30, 0.45).decay(1.1));                                   // BOOM: a deep falling thud
                m.add(noise(0.50, 0.65, 7000, 300, 0.22, false).decay(1.1));                        //  and a crash on top of it
                m.add(noise(0.50, 1.40, 500, 120, 0.20, true).decay(0.8));                          // a long metallic rumble underneath
                m.add(sq(0.55, 0.30, hz(A2), hz(A2), 0.34, 0.5).attack(0.01));                      // the doom motif: A ...
                m.add(sq(0.90, 0.30, hz(F2), hz(F2), 0.34, 0.5).attack(0.01));                      //  ... F ...
                m.add(sq(1.25, 0.90, hz(D2), hz(D2), 0.36, 0.5).attack(0.01).vib(6, 0.04));         //  ... D, held, shuddering
                m.add(saw(0.55, 0.30, hz(A2 + 12), hz(A2 + 12), 0.10).attack(0.01));                // the same notes an octave up, a little harsh
                m.add(saw(0.90, 0.30, hz(F2 + 12), hz(F2 + 12), 0.10).attack(0.01));
                m.add(saw(1.25, 0.90, hz(D2 + 12), hz(D2 + 12), 0.10).attack(0.01).vib(6, 0.04));
                m.add(sq(1.25, 0.90, hz(D2 + 6), hz(D2 + 6), 0.13, 0.25).attack(0.01).vib(6, 0.04)); // a clashing tritone note: the evil one
            }
            case WAVE_CLEAR -> { // little victory fanfare
                int[] notes = {C5, E5, G5};
                for (int i = 0; i < notes.length; i++) m.add(sq(i * 0.07, 0.08, hz(notes[i]), hz(notes[i]), 0.34, 0.25));
                m.add(sq(0.21, 0.30, hz(C6), hz(C6), 0.34, 0.25));
                m.add(tri(0.21, 0.30, hz(C5), hz(C5), 0.30));
            }
            case GAME_OVER -> { // sad descending phrase, then a slide into the floor
                int[] notes = {G4, E4, C4};
                for (int i = 0; i < notes.length; i++) m.add(tri(i * 0.24, 0.22, hz(notes[i]), hz(notes[i]), 0.46));
                m.add(tri(0.72, 0.55, hz(G3), hz(C3) * 0.8, 0.46).decay(1.2));
                m.add(sq(0.72, 0.55, hz(G3) / 2, hz(C3) / 2, 0.16, 0.5).decay(1.2));
                m.add(noise(0.72, 0.4, 900, 150, 0.10, true));
            }
        }
        return m;
    }

    // ================================================================== synthesizer

    private enum Wave { SQUARE, TRIANGLE, SAW, NOISE, NOISE_SHORT, AIR }

    /** One note/segment. Fluent setters tweak the envelope and vibrato. */
    private static final class Tone {
        final Wave wave; final double at, f0, f1, vol, duty;
        double dur;                               // (not final: a tie in a tune stretches the previous note)
        double decayPow = 1.6, attack = 0.002, vibHz = 0, vibDepth = 0, q = 1.1;
        Tone(Wave wave, double at, double dur, double f0, double f1, double vol, double duty) {
            this.wave = wave; this.at = at; this.dur = dur; this.f0 = f0; this.f1 = f1; this.vol = vol; this.duty = duty;
        }
        Tone decay(double p) { decayPow = p; return this; }
        Tone attack(double s) { attack = s; return this; }
        /** air() only: how narrow the airy band is. Low (~0.7) = broad, fuzzy, sandy; ~2 = a focused whoosh; 5+ starts to whistle. */
        Tone q(double v) { q = v; return this; }
        Tone vib(double hz, double depth) { vibHz = hz; vibDepth = depth; return this; }
    }

    // ================================================================== the hidden tracks

    /** Hidden track #1: an original 8-bit tune (about 40 seconds, 16 bars, 96 BPM) -- the Sound Test's first "???" button. */
    private static void hiddenTrack(Mix m) {
        final String[] melody = {
                // ---- A: bars 1-8 ----
                "E5 1, G5 1, E5 .5, D5 .5, C5 1",
                "D5 1, B4 1, G4 1, B4 1",
                "C5 1, E5 1, A4 1, C5 1",
                "A4 1, C5 1, D5 1, C5 1",
                "E5 1, G5 1, A5 1, G5 1",
                "D5 1.5, E5 .5, D5 1, B4 1",
                "C5 1, F5 1.5, E5 .5, C5 1",
                "B4 1, D5 1, G5 2",
                // ---- B: bars 9-16 ----
                "A5 1.5, G5 .5, E5 1, C5 1",
                "A4 1, C5 1, F5 1.5, E5 .5",
                "G5 1, E5 1, C5 1, E5 1",
                "D5 1.5, G5 .5, D5 1, B4 1",
                "C5 1, E5 1, A5 2",
                "G5 1, F5 1, E5 1, C5 1",
                "D5 1, G5 1, F5 .5, D5 .5, B4 1",
                "C5 3, R 1"};
        final String[] chords = {"C", "G", "Am", "F", "C", "G", "F", "G",   "Am", "F", "C", "G", "Am", "F", "G", "C"};
        playTune(m, 96, melody, chords, 2, 8);
    }

    /**
     * Hidden track #2 -- YOUR WORKSHOP. Its notes, chords, tempo and drum/sound switches come from a {@link WorkshopTune}:
     * the one you saved in the in-game Music Editor (Ctrl+S, click "??? 2", press E), or the built-in starter tune if
     * you haven't saved one yet. Delete laststand_workshop_track.txt to go back to the starter.
     *
     * (The tune is still written in the same bar-by-bar text -- "E5 1" = the note E in octave 5 for 1 beat, "R 1" = a
     * rest -- see WorkshopTune.melodyBars -- so a saved file can also be edited by hand in any text editor.)
     */
    private static void workshopTrack(Mix m) {
        applyTune(m, WorkshopTune.loadOrDefault());
    }

    private static void applyTune(Mix m, WorkshopTune t) {
        playTune(m, t.bpm, t.melodyBars(), t.chordNames(), t.drumsFromBar, t.fullSoundFromBar, t.effectiveBeats(), t.effectiveVoices());
    }

    /**
     * Plays a tune on four old-console channels: a square-wave melody, a quiet square-wave arpeggio, a triangle bass
     * and noise drums. Both hidden tracks use this, so they differ only in their data.
     */
    private static void playTune(Mix m, double bpm, String[] melody, String[] chords, int drumsFromBar, int fullSoundFromBar) {
        playTune(m, bpm, melody, chords, drumsFromBar, fullSoundFromBar, null, null);
    }

    /** @param drumBeats one drum pattern per bar (already NONE before the drums come in); null = the classic rock beat from drumsFromBar on
     *  @param leads one lead sound per bar; null = REEDY before fullSoundFromBar, FULL from there on */
    private static void playTune(Mix m, double bpm, String[] melody, String[] chords, int drumsFromBar, int fullSoundFromBar, WorkshopTune.Beat[] drumBeats, WorkshopTune.Lead[] leads) {
        final double beat = 60.0 / bpm;                                    // seconds per beat
        final int bars = melody.length;
        if (chords.length != bars)
            throw new IllegalArgumentException("The melody has " + bars + " bars but there are " + chords.length + " chords. They must match: one chord per bar.");

        double t = 0;
        List<Tone> prevNote = null;                                         // the last melody note's waves (a tie, "T", stretches them)
        WorkshopTune.Lead prevLead = null;                                  // ...and the sound it was played with (a tie keeps it, even across a change of sound)
        double prevSlot = 0;                                                // ...and how long its slot is so far, in seconds
        for (int b = 0; b < bars; b++) {                                    // ---- melody: square wave (reedy at first, fuller later)
            WorkshopTune.Lead lead = leads != null ? leads[b] : (b < fullSoundFromBar ? WorkshopTune.Lead.REEDY : WorkshopTune.Lead.FULL);
            double beatsInBar = 0;
            for (String token : melody[b].split(",")) {
                String[] p = token.trim().split("\\s+");
                double beats;
                try { beats = Double.parseDouble(p[1]); }
                catch (RuntimeException e) { throw new IllegalArgumentException("Bar " + (b + 1) + ": can't read \"" + token.trim() + "\". Write a note and a length, like \"E5 1\" or \"R .5\"."); }
                double len = beats * beat;
                if (p[0].equals("T")) {                                     // a tie: keep the previous note sounding, with no new start
                    if (prevNote == null) throw new IllegalArgumentException("Bar " + (b + 1) + ": \"T\" holds on to the note before it, but there's no note right before this.");
                    prevSlot += len;
                    for (Tone layer : prevNote) {
                        layer.dur = prevSlot * 0.92 * prevLead.sustain;
                        if (prevLead.vibrato == 1 && prevSlot >= 1.9 * beat && layer.vibHz == 0) layer.vib(prevLead.vibHz, prevLead.vibDepth);
                    }
                } else if (p[0].equals("R")) {
                    prevNote = null;                                        // (a rest ends the note, so a tie can't reach across it)
                } else {
                    int n = noteNum(p[0], b + 1);
                    List<Tone> layers = new ArrayList<>();
                    for (WorkshopTune.Lead.Layer ly : lead.layers) {         // every sound is one or more waves played together
                        double f = hz(n + ly.semis) * Math.pow(2, ly.cents / 1200.0), d = len * 0.92 * lead.sustain;
                        Tone note = switch (ly.kind) {
                            case SQUARE -> sq(t, d, f, f, ly.vol, ly.duty);
                            case TRIANGLE -> tri(t, d, f, f, ly.vol);
                            case SAW -> saw(t, d, f, f, ly.vol);
                        };
                        note.attack(ly.attack).decay(ly.decay);
                        if (lead.vibrato == 2 || (lead.vibrato == 1 && len >= 1.9 * beat)) note.vib(lead.vibHz, lead.vibDepth);   // a little wobble on long notes
                        m.add(note);
                        layers.add(note);
                    }
                    prevNote = layers;
                    prevLead = lead;
                    prevSlot = len;
                }
                t += len;
                beatsInBar += beats;
            }
            if (Math.abs(beatsInBar - 4) > 1e-6)
                throw new IllegalArgumentException("Bar " + (b + 1) + " adds up to " + beatsInBar + " beats, but every bar needs exactly 4: \"" + melody[b] + "\"");
        }

        m.loopSeconds = bars * 4 * beat;                                    // one full pass = all the bars (what a loop repeats)

        for (int b = 0; b < bars; b++) {
            double t0 = b * 4 * beat;
            boolean noChord = WorkshopTune.isNoChord(chords[b]);               // "-" = a bar with no chord: no bass, no arpeggio
            int r = noChord ? 0 : chordRoot(chords[b], b + 1), third = r + 12 + (chords[b].endsWith("m") ? 3 : 4), fifth = r + 12 + 7;
            boolean last = b == bars - 1 && !m.loopMode;                     // (when looping, the last bar keeps the groove going into bar 1)

            if (last) {                                                     // ---- the ending: one long bass note and a cymbal crash
                if (!noChord) m.add(tri(t0, 3.4 * beat, hz(r), hz(r), 0.22).attack(0.004).decay(0.6));
                m.add(tri(t0, 0.13, 170, 45, 0.26).decay(1.6));
                m.add(noise(t0, 0.55, 9000, 3000, 0.10, false).decay(1.2));
                continue;
            }
            if (!noChord) {
            // ---- bass: triangle wave
            m.add(tri(t0, 1.4 * beat, hz(r), hz(r), 0.22).attack(0.004).decay(0.6));
            m.add(tri(t0 + 1.5 * beat, 0.45 * beat, hz(r), hz(r), 0.22).decay(0.8));
            m.add(tri(t0 + 2 * beat, 0.9 * beat, hz(r + 7), hz(r + 7), 0.20).decay(0.8));
            m.add(tri(t0 + 3 * beat, 0.9 * beat, hz(r), hz(r), 0.22).decay(0.8));
            // ---- arpeggio: the chord's three notes, picked one after another (quiet, thin square wave)
            int[] chord = {r + 12, third, fifth};
            int[] order = {0, 1, 2, 1, 0, 1, 2, 1};
            for (int k = 0; k < 8; k++) {
                int n = chord[order[k]];
                m.add(sq(t0 + k * 0.5 * beat, 0.4 * beat, hz(n), hz(n), 0.05, 0.125).decay(1.2));
            }
            }
            // ---- drums
            WorkshopTune.Beat pattern = drumBeats != null ? drumBeats[b] : (b >= drumsFromBar ? WorkshopTune.Beat.ROCK : WorkshopTune.Beat.NONE);
            if (pattern != WorkshopTune.Beat.ROCK) drumPattern(m, t0, beat, pattern);
            else {                                                                      // ROCK: the original beat, exactly as it always was
                m.add(tri(t0, 0.13, 170, 45, 0.26).decay(1.6));                         // kick on beat 1
                m.add(tri(t0 + 2 * beat, 0.13, 170, 45, 0.26).decay(1.6));              // kick on beat 3
                if (b % 2 == 1) m.add(tri(t0 + 2.5 * beat, 0.13, 170, 45, 0.22).decay(1.6)); // an extra kick every other bar
                for (int s2 : new int[]{1, 3}) {                                         // snare on beats 2 and 4
                    m.add(noise(t0 + s2 * beat, 0.09, 9000, 3500, 0.13, false).decay(1.8));
                    m.add(tri(t0 + s2 * beat, 0.05, 330, 190, 0.08).decay(1.5));
                }
                for (int k = 0; k < 8; k++)                                              // hi-hat on every half beat
                    m.add(noise(t0 + k * 0.5 * beat, 0.025, 12000, 9000, k % 2 == 0 ? 0.045 : 0.03, true).decay(2.0));
            }
        }
    }

    /** One bar of a drum pattern: 16 sixteenth-note steps, each lane (kick, snare, hats, toms, clap, crash) hitting where its pattern says. */
    private static void drumPattern(Mix m, double t0, double beat, WorkshopTune.Beat pattern) {
        double step = beat / 4;
        for (int s = 0; s < 16; s++) {
            double at = t0 + s * step, v;
            if ((v = pattern.level(WorkshopTune.Beat.KICK, s)) > 0)
                m.add(tri(at, 0.13, 170, 45, 0.26 * v).decay(1.6));
            if ((v = pattern.level(WorkshopTune.Beat.SNARE, s)) > 0) {
                m.add(noise(at, 0.09, 9000, 3500, 0.13 * v, false).decay(1.8));
                m.add(tri(at, 0.05, 330, 190, 0.08 * v).decay(1.5));
            }
            if ((v = pattern.level(WorkshopTune.Beat.HAT, s)) > 0)
                m.add(noise(at, 0.025, 12000, 9000, 0.045 * v, true).decay(2.0));
            if ((v = pattern.level(WorkshopTune.Beat.OPEN_HAT, s)) > 0)             // open hi-hat: the same metallic hiss, left to ring
                m.add(noise(at, 0.13, 12000, 7000, 0.05 * v, true).decay(1.4));
            if ((v = pattern.level(WorkshopTune.Beat.TOM_HI, s)) > 0)
                m.add(tri(at, 0.16, 260, 140, 0.20 * v).decay(1.5));
            if ((v = pattern.level(WorkshopTune.Beat.TOM_LO, s)) > 0)
                m.add(tri(at, 0.20, 190, 85, 0.22 * v).decay(1.5));
            if ((v = pattern.level(WorkshopTune.Beat.CLAP, s)) > 0) {               // a clap: three quick bursts, then a short tail
                for (int k = 0; k < 3; k++) m.add(noise(at + k * 0.011, 0.03, 7000, 5000, 0.12 * v, false).decay(2.2));
                m.add(noise(at + 0.033, 0.09, 6500, 3000, 0.09 * v, false).decay(1.8));
            }
            if ((v = pattern.level(WorkshopTune.Beat.CRASH_CYMBAL, s)) > 0)
                m.add(noise(at, 0.55, 9000, 3000, 0.10 * v, false).decay(1.2));
        }
    }

    /** The bass note for a chord name: "C" -> 48, "Am" -> 45, "F#" / "Bb" / "Dm" work too. Bass notes sit between F2 (41) and E3 (52). */
    private static int chordRoot(String chord, int bar) {
        String c = chord.trim();
        int semi = c.isEmpty() ? -1 : "C D EF G A B".indexOf(c.charAt(0));    // C=0 D=2 E=4 F=5 G=7 A=9 B=11
        int i = 1;
        if (semi >= 0 && c.length() > 1 && c.charAt(1) == '#') { semi++; i = 2; }
        else if (semi >= 0 && c.length() > 1 && c.charAt(1) == 'b') { semi--; i = 2; }
        if (semi < 0 || !(c.substring(i).isEmpty() || c.substring(i).equals("m")))
            throw new IllegalArgumentException("Bar " + bar + ": don't know the chord \"" + chord + "\". Use a letter A-G, then optionally # or b, then m for minor (C, Am, F#, Bb, Dm).");
        int pc = Math.floorMod(semi, 12);
        int r = 48 + pc;
        return r > 52 ? r - 12 : r;
    }

    /** "C5" -> 72, "F#4" -> 66, "Bb3" -> 58 (MIDI note numbers, so a tune can be written with note names). */
    private static int noteNum(String name, int bar) {
        try {
            int semi = switch (name.charAt(0)) { case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; case 'B' -> 11; default -> throw new IllegalArgumentException(); };
            int i = 1;
            if (name.charAt(i) == '#') { semi++; i++; } else if (name.charAt(i) == 'b') { semi--; i++; }
            return 12 * (Integer.parseInt(name.substring(i)) + 1) + semi;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Bar " + bar + ": can't read the note \"" + name + "\". Write a letter A-G, optionally # or b, then an octave number (C5, F#4, Bb3), or R for a rest.");
        }
    }

    private static Tone sq(double at, double dur, double f0, double f1, double vol, double duty) { return new Tone(Wave.SQUARE, at, dur, f0, f1, vol, duty); }
    private static Tone tri(double at, double dur, double f0, double f1, double vol) { return new Tone(Wave.TRIANGLE, at, dur, f0, f1, vol, 0.5); }
    private static Tone saw(double at, double dur, double f0, double f1, double vol) { return new Tone(Wave.SAW, at, dur, f0, f1, vol, 0.5); }
    /** f0/f1 are the noise generator's clock in Hz: higher = brighter hiss, lower = rumble. shortMode = metallic 7-bit noise. */
    private static Tone noise(double at, double dur, double f0, double f1, double vol, boolean shortMode) {
        return new Tone(shortMode ? Wave.NOISE_SHORT : Wave.NOISE, at, dur, f0, f1, vol, 0.5);
    }

    /**
     * Smooth, realistic air -- the opposite of the chiptune {@link #noise}. Instead of flipping between just +1 and -1,
     * it uses smoothly random values run through a band-pass filter, like a real whoosh. f0 -> f1 is the CENTRE of the
     * airy band in Hz (high = bright "ssss", low = soft "fff"), sliding over the length of the sound; vol is roughly the
     * same strength as noise(...) at the same number. Chain .q(x) to focus the band (higher = a tighter, less sandy whoosh). Pair it with m.smooth = true so the 8-bit grain isn't added on top.
     */
    private static Tone air(double at, double dur, double f0, double f1, double vol) {
        return new Tone(Wave.AIR, at, dur, f0, f1, vol, 0.5);
    }

    private static double hz(int midi) { return 440.0 * Math.pow(2.0, (midi - 69) / 12.0); }

    private static final class Mix {
        final List<Tone> tones = new ArrayList<>();
        /** true = leave this sound smooth: skip the retro 8-bit rounding (for realistic sounds built from air(...)). */
        boolean smooth = false;
        /** Set while rendering the seamless-loop version of a track: the last bar flows on instead of ending. */
        boolean loopMode = false;
        /** Set by a tune: exactly how long one pass is (all its bars), so a loop can restart right on the bar line. 0 = not loopable. */
        double loopSeconds = 0;
        void add(Tone t) { tones.add(t); }
    }

    private static short[] toPcm(Mix mix) { return toPcm(mix, 0); }

    /** loopSeconds > 0 = make a seamless loop of exactly that length: anything ringing past the end wraps round onto the start. */
    private static short[] toPcm(Mix mix, double loopSeconds) {
        double end = 0;
        for (Tone t : mix.tones) end = Math.max(end, t.at + t.dur);
        float[] buf = new float[(int) Math.ceil((end + 0.01) * SAMPLE_RATE)];
        for (Tone t : mix.tones) renderTone(t, buf);
        if (loopSeconds > 0) {
            int L = (int) Math.round(loopSeconds * SAMPLE_RATE);
            float[] folded = new float[L];
            for (int i = 0; i < buf.length; i++) folded[i % L] += buf[i];
            buf = folded;
        }

        // DC blocker (a ~17 Hz high-pass). Narrow pulse waves are lopsided, which leaves a constant offset that
        // eats headroom and can thump on a speaker -- the real NES filtered this out in hardware too.
        float prevIn = 0, prevOut = 0;
        for (int i = 0; i < buf.length; i++) {
            float in = buf[i];
            float out = in - prevIn + 0.995f * prevOut;
            prevIn = in; prevOut = out; buf[i] = out;
        }

        float peak = 0;
        for (float v : buf) peak = Math.max(peak, Math.abs(v));
        float scale = peak > 0.85f ? 0.85f / peak : 1f; // never clip, whatever the recipe adds up to
        short[] out = new short[buf.length];
        for (int i = 0; i < buf.length; i++) {
            double x = buf[i] * scale;
            if (CRUSH_TO_8_BIT && !mix.smooth) x = Math.round(x * 127.0) / 127.0;
            out[i] = (short) Math.round(x * 32767.0);
        }
        return out;
    }

    private static void renderTone(Tone t, float[] buf) {
        int start = (int) Math.round(t.at * SAMPLE_RATE);
        int n = (int) Math.round(t.dur * SAMPLE_RATE);
        double phase = 0, noiseAcc = 0;
        int lfsr = 1;
        int rng = 12345;                       // AIR: simple repeatable random numbers
        double ax1 = 0, ax2 = 0, ay1 = 0, ay2 = 0; // AIR: the band-pass filter's memory
        boolean shortNoise = t.wave == Wave.NOISE_SHORT;
        double noiseOut = 1;
        for (int i = 0; i < n && start + i < buf.length; i++) {
            double u = i / (double) n;                                  // 0..1 through the note
            double f = t.f0 * Math.pow(t.f1 / t.f0, u);                 // exponential (musical) pitch slide
            if (t.vibHz > 0) f *= 1 + t.vibDepth * Math.sin(2 * Math.PI * t.vibHz * i / SAMPLE_RATE);
            double s;
            switch (t.wave) {
                case SQUARE -> { phase += f / SAMPLE_RATE; phase -= Math.floor(phase); s = phase < t.duty ? 1 : -1; }
                case TRIANGLE -> { phase += f / SAMPLE_RATE; phase -= Math.floor(phase); s = 4 * Math.abs(phase - 0.5) - 1; }
                case SAW -> { phase += f / SAMPLE_RATE; phase -= Math.floor(phase); s = 2 * phase - 1; }
                case AIR -> { // smooth random values -> a band-pass filter whose centre is f (Q = t.q: how narrow the band is)
                    rng = rng * 1664525 + 1013904223;
                    double white = ((rng >>> 8) / (double) (1 << 24) - 0.5) * 3.4641;   // evenly random, strength ~1
                    double fc = Math.max(40, Math.min(f, 0.45 * SAMPLE_RATE));
                    double w0 = 2 * Math.PI * fc / SAMPLE_RATE, alpha = Math.sin(w0) / (2 * t.q);
                    double a0 = 1 + alpha, a1 = -2 * Math.cos(w0), a2 = 1 - alpha;
                    double y = (alpha * white - alpha * ax2 - a1 * ay1 - a2 * ay2) / a0;
                    ax2 = ax1; ax1 = white; ay2 = ay1; ay1 = y;
                    double share = Math.max(0.02, Math.min(1.0, (Math.PI / 2) * (fc / t.q) / (SAMPLE_RATE / 2.0)));
                    s = y / Math.sqrt(share);                                           // keep loudness steady as the band slides
                }
                default -> { // NES-style linear-feedback shift register noise
                    noiseAcc += f / SAMPLE_RATE;
                    while (noiseAcc >= 1) {
                        noiseAcc -= 1;
                        int bit = (lfsr ^ (lfsr >> (shortNoise ? 6 : 1))) & 1;
                        lfsr = (lfsr >> 1) | (bit << (shortNoise ? 6 : 14));
                        noiseOut = (lfsr & 1) == 0 ? 1 : -1;
                    }
                    s = noiseOut;
                }
            }
            double env = Math.min(1.0, (i / (double) SAMPLE_RATE) / Math.max(1e-6, t.attack)) * Math.pow(1 - u, t.decayPow);
            buf[start + i] += (float) (s * env * t.vol);
        }
    }

    // ================================================================== mixer / playback

    private static final class Voice {
        final short[] data; int pos = 0;
        final boolean loop;                       // true = start again from the beginning instead of finishing
        float gain = 1f;                          // volume of this voice (background music is turned down so effects stay clear)
        boolean music;                            // the background-music voice: survives silence(), replaced by startMusic()
        boolean preview;                          // the Music Editor's playback voice (see previewTune)
        Voice(short[] data) { this(data, false); }
        Voice(short[] data, boolean loop) { this.data = data; this.loop = loop; }
    }

    private static final List<Voice> voices = new ArrayList<>();
    private static final long[] lastPlayedMs = new long[Id.values().length];
    private static final short[][] cache = new short[Id.values().length][];
    private static final boolean[] overridden = new boolean[Id.values().length];
    private static final String[] errors = new String[Id.values().length];
    private static final short[][] loopCache = new short[Id.values().length][];
    private static final boolean[] loopDone = new boolean[Id.values().length];
    private static boolean initDone = false;
    private static volatile boolean lineAvailable = false;
    private static boolean testMode = false;
    private static SourceDataLine line;

    private static short[] samples(Id id) {
        synchronized (cache) {
            short[] s = cache[id.ordinal()];
            if (s == null) {
                s = loadOverride(id);
                overridden[id.ordinal()] = s != null;
                if (s == null) {
                    try { s = render(id); errors[id.ordinal()] = null; }
                    catch (RuntimeException e) { // a mistake in a recipe (e.g. a hidden-track bar that doesn't add up): say so, play silence
                        errors[id.ordinal()] = e.getMessage() != null ? e.getMessage() : e.toString();
                        System.err.println("[Sfx] " + id + ": " + errors[id.ordinal()]);
                        s = new short[SAMPLE_RATE / 100];
                    }
                }
                cache[id.ordinal()] = s;
            }
            return s;
        }
    }

    private static synchronized void ensureInit() {
        if (initDone) return;
        initDone = true;
        if (Boolean.getBoolean("laststand.nosound")) return;
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) return;
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, LINE_BUFFER_BYTES);
            line.start();
            final SourceDataLine opened = line;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { // don't leave a half-played buffer ringing on exit
                try { opened.stop(); opened.flush(); opened.close(); } catch (Throwable ignored) { }
            }));
            Thread t = new Thread(Sfx::mixerLoop, "sfx-mixer");
            t.setDaemon(true); // never keeps the game open after the window closes
            t.setPriority(Thread.MAX_PRIORITY - 1); // a late block is an audible glitch, so keep this thread responsive
            t.start();
            lineAvailable = true;
        } catch (Throwable e) {
            lineAvailable = false; // no sound card / device busy / etc. -- just stay silent
        }
    }

    /** Where finished audio blocks go. The real one is the sound card's line; tests plug in a fake device. */
    public interface Sink { void write(byte[] data, int off, int len); }

    private static final short[] pumpBlock = new short[BLOCK];
    private static final byte[] pumpBytes = new byte[BLOCK * 2];

    /**
     * Mixes ONE block and hands it to the device -- and writes it even when nothing is playing (all zeros).
     * That is deliberate and important: the sound card plays from a small circular buffer, and some systems
     * (notably Windows' DirectSound) simply REPLAY whatever is in it if the program stops refilling it. We used
     * to go quiet when no effect was active, which left the tail of the last sounds stuck on a loop -- one that
     * muting couldn't stop, because muting only clears what we'd send, and nothing was being sent. Keeping the
     * buffer topped up with silence overwrites any stale audio within a few milliseconds.
     */
    static void pumpOnce(Sink sink) {
        mixBlock(pumpBlock); // no voices -> a block of zeros
        for (int i = 0; i < BLOCK; i++) {
            pumpBytes[2 * i] = (byte) pumpBlock[i];
            pumpBytes[2 * i + 1] = (byte) (pumpBlock[i] >> 8);
        }
        sink.write(pumpBytes, 0, pumpBytes.length);
    }

    private static void mixerLoop() {
        int tooFast = 0;
        while (true) {
            long t0 = System.nanoTime();
            try {
                pumpOnce((d, off, len) -> line.write(d, off, len)); // blocks until the card wants more -- that's the clock
            } catch (Throwable e) {
                lineAvailable = false; // device vanished / closed -- go quiet rather than spin
                return;
            }
            // Safety net: if a driver ever returns instantly instead of blocking, don't spin a whole CPU core.
            if (System.nanoTime() - t0 < 1_000_000L) {
                if (++tooFast > 24) { try { Thread.sleep(6); } catch (InterruptedException e) { return; } }
            } else {
                tooFast = 0;
            }
        }
    }

    /** Sums every playing voice into out[] (one block), dropping voices that have finished. */
    private static void mixBlock(short[] out) {
        int n = out.length;
        int[] sum = new int[n];
        synchronized (voices) {
            for (int v = voices.size() - 1; v >= 0; v--) {
                Voice voice = voices.get(v);
                int done = 0;
                while (done < n) {
                    int len = Math.min(n - done, voice.data.length - voice.pos);
                    if (voice.gain == 1f) for (int i = 0; i < len; i++) sum[done + i] += voice.data[voice.pos + i];
                    else for (int i = 0; i < len; i++) sum[done + i] += Math.round(voice.data[voice.pos + i] * voice.gain);
                    voice.pos += len;
                    done += len;
                    if (voice.pos >= voice.data.length) {
                        if (voice.loop && voice.data.length > 0) voice.pos = 0;        // wrap: the loop restarts seamlessly
                        else { voices.remove(v); break; }
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            int x = Math.round(sum[i] * MASTER);
            out[i] = (short) Math.max(-32768, Math.min(32767, x));
        }
    }

    // ---- test hooks (no sound card needed) ----

    /** Lets tests queue and mix sounds without an audio device. */
    public static void enableTestMode() { testMode = true; initDone = true; }

    /** Mixes and returns the next n samples (test mode only). */
    public static short[] pullForTest(int n) { short[] out = new short[n]; mixBlock(out); return out; }

    /** Runs one iteration of the audio pump against a fake device (test mode only). */
    public static void pumpOnceForTest(Sink sink) { pumpOnce(sink); }

    public static int activeVoicesForTest() { synchronized (voices) { return voices.size(); } }

    public static void resetForTest() {
        synchronized (voices) { voices.clear(); }
        synchronized (lastPlayedMs) { java.util.Arrays.fill(lastPlayedMs, 0L); }
        muted = false;
        listener = null;
        synchronized (cache) { java.util.Arrays.fill(cache, null); java.util.Arrays.fill(overridden, false); java.util.Arrays.fill(loopCache, null); java.util.Arrays.fill(loopDone, false); }
    }

    // ================================================================== WAV override

    private static short[] loadOverride(Id id) {
        String path = "/sounds/" + id.name().toLowerCase() + ".wav";
        try (InputStream raw = Sfx.class.getResourceAsStream(path)) {
            if (raw == null) return null;
            AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
            AudioFormat f = src.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(),
                    f.getChannels() * 2, f.getSampleRate(), false);
            byte[] bytes = AudioSystem.getAudioInputStream(pcm, src).readAllBytes();
            int ch = f.getChannels(), frames = bytes.length / (2 * ch);
            float[] mono = new float[frames];
            for (int i = 0; i < frames; i++) { // down-mix to mono
                float sum = 0;
                for (int c = 0; c < ch; c++) {
                    int idx = (i * ch + c) * 2;
                    sum += (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8)) / 32768f;
                }
                mono[i] = sum / ch;
            }
            // resample to SAMPLE_RATE (linear interpolation)
            double ratio = f.getSampleRate() / (double) SAMPLE_RATE;
            int outLen = (int) Math.floor(frames / ratio);
            short[] out = new short[outLen];
            for (int i = 0; i < outLen; i++) {
                double pos = i * ratio; int i0 = (int) pos; int i1 = Math.min(frames - 1, i0 + 1); double fr = pos - i0;
                out[i] = (short) Math.round((mono[i0] * (1 - fr) + mono[i1] * fr) * 32767.0 * 0.9);
            }
            return outLen > 0 ? out : null;
        } catch (Exception e) {
            System.err.println("Couldn't load custom sound " + path + " (" + e + ") -- using the built-in one.");
            return null;
        }
    }
}
