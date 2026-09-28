package laststand.save;

import java.io.*;

/**
 * Reads/writes a single save slot to a file next to wherever the game is run from. One slot is
 * plenty for a prototype -- saving always overwrites the previous save.
 */
public final class SaveManager {
    private SaveManager() {}

    public static final File SAVE_FILE = new File("laststand_save.dat");

    public static boolean saveExists() {
        return SAVE_FILE.isFile();
    }

    /** Returns true on success. Never throws -- I/O problems (read-only disk, etc.) just mean the
     *  save didn't happen; the game itself keeps running either way. */
    public static boolean save(SaveData data) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
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
    public static SaveData load() {
        if (!saveExists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(SAVE_FILE))) {
            return (SaveData) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Failed to load save (possibly from an incompatible older version):");
            e.printStackTrace();
            return null;
        }
    }

    public static void delete() {
        SAVE_FILE.delete();
    }
}
