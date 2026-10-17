package laststand.ui;

import laststand.core.InputHandler;

import java.awt.Graphics2D;

/**
 * What the Sound Test needs from an editor screen that opens on top of it: the Music Editor ("??? 2") and the SFX Editor
 * (every sound effect) both are one. Coordinates are in design space (1000 x 740).
 */
interface SoundEditor {
    /** One frame of keyboard input. Returns true when the editor should close (back to the soundboard). */
    boolean tick(InputHandler in);
    void render(Graphics2D g);
    void press(int mx, int my, int button);
    void release(int mx, int my);
    void drag(int mx, int my);
    void hover(int mx, int my);
    void wheel(int mx, int my, int rotation, boolean shift);
    /** Ctrl+S while the editor is open. */
    void requestSave();
}
