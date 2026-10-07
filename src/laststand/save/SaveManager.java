package laststand.save;

import java.io.*;

/**
 * Reads/writes save slots to files next to wherever the game is run from. Two independent slots
 * now -- one for solo (P1 only) runs, one for 2-player runs -- rather than one shared slot. A
 * solo save can only ever be loaded into a solo (P2 disabled) session, and a 2-player save can
 * only ever be loaded into a 2-player (P2 enabled) session; which file every method reads/writes
 * is selected by the `coop` flag (== the CURRENT session's P2-enabled toggle), not anything
 * recorded inside the save itself. That keeps a save fully self-consistent with the mode it was
 * always meant for, instead of one save trying to serve both.
 */
public final class SaveManager {
    private SaveManager() {}

    public static final File SOLO_SAVE_FILE = new File("laststand_save_solo.dat");
    public static final File COOP_SAVE_FILE = new File("laststand_save_coop.dat");
    private static final File LEGACY_SAVE_FILE = new File("laststand_save.dat"); // pre-v1.1.1 single shared save

    private static File fileFor(boolean coop) {
        return coop ? COOP_SAVE_FILE : SOLO_SAVE_FILE;
    }

    /**
     * One-time migration: v1.1.1 split the single shared save into two per-mode files, which
     * silently orphaned anyone's existing save (the game just stopped looking at the old
     * filename). If that old file is still sitting there, read it, figure out which mode it
     * belongs to (from its own p2Enabled field, which the format has always had), and move it
     * into that mode's new file. Safe to call every time the game starts -- it's a cheap no-op
     * once the legacy file is gone, and never overwrites a save someone's already made in the
     * new format. Leaves the legacy file in place (doesn't delete it) if it can't be read, so a
     * genuinely broken old file doesn't just vanish without a trace.
     */
    public static void migrateLegacySaveIfPresent() {
        if (!LEGACY_SAVE_FILE.isFile()) return;
        SaveData data;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(LEGACY_SAVE_FILE))) {
            data = (SaveData) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Found an old save (" + LEGACY_SAVE_FILE + ") but couldn't read it -- leaving it in place:");
            e.printStackTrace();
            return;
        }
        File target = fileFor(data.p2Enabled);
        if (!target.isFile()) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(target))) {
                out.writeObject(data);
            } catch (IOException e) {
                System.err.println("Found an old save but failed to migrate it to " + target + " -- leaving the old file in place:");
                e.printStackTrace();
                return;
            }
        }
        LEGACY_SAVE_FILE.delete(); // migrated (or a same-mode save already existed) -- don't keep re-checking it
    }

    public static boolean saveExists(boolean coop) {
        return fileFor(coop).isFile();
    }

    /** Returns true on success. Never throws -- I/O problems (read-only disk, etc.) just mean the
     *  save didn't happen; the game itself keeps running either way. */
    public static boolean save(SaveData data, boolean coop) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileFor(coop)))) {
            out.writeObject(data);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to save game:");
            e.printStackTrace();
            return false;
        }
    }

    /** Returns null (rather than throwing) if there's no save, or it can't be read -- e.g. it was
     *  written by an incompatible older version after a save-format change. */
    public static SaveData load(boolean coop) {
        if (!saveExists(coop)) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileFor(coop)))) {
            return (SaveData) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Failed to load save (possibly from an incompatible older version):");
            e.printStackTrace();
            return null;
        }
    }

    public static void delete(boolean coop) {
        fileFor(coop).delete();
    }
}
