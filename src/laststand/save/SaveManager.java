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

    private static File fileFor(boolean coop) {
        return coop ? COOP_SAVE_FILE : SOLO_SAVE_FILE;
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
