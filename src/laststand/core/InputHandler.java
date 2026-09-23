package laststand.core;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks held keys plus "just pressed this frame" events (for menu
 * navigation / attack taps, so holding a key doesn't spam actions).
 *
 * Controls (feel free to remap):
 *   Player 1: W A S D move, SPACE attack
 *   Player 2: Arrow keys move, ENTER attack
 *   ESC: back / pause
 */
public class InputHandler extends KeyAdapter {

    private final Set<Integer> held = new HashSet<>();
    private final Set<Integer> pressedThisFrame = new HashSet<>();
    private final Set<Integer> consumed = new HashSet<>();

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (held.add(code)) {
            pressedThisFrame.add(code);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        held.remove(e.getKeyCode());
        consumed.remove(e.getKeyCode());
    }

    public boolean isHeld(int keyCode) {
        return held.contains(keyCode);
    }

    /** True once per physical key-down; caller must call consume() after acting on it. */
    public boolean wasJustPressed(int keyCode) {
        return held.contains(keyCode) && !consumed.contains(keyCode);
    }

    public void consume(int keyCode) {
        consumed.add(keyCode);
    }

    // Player 1 bindings
    public boolean p1Up()    { return isHeld(KeyEvent.VK_W); }
    public boolean p1Down()  { return isHeld(KeyEvent.VK_S); }
    public boolean p1Left()  { return isHeld(KeyEvent.VK_A); }
    public boolean p1Right() { return isHeld(KeyEvent.VK_D); }
    public boolean p1Attack(){ return isHeld(KeyEvent.VK_SPACE); }

    // Edge-triggered attack: true only on the frame the key was freshly pressed, and
    // consumes it immediately so holding the button down can't auto-repeat the attack.
    public boolean p1AttackJustPressed() {
        if (wasJustPressed(KeyEvent.VK_SPACE)) { consume(KeyEvent.VK_SPACE); return true; }
        return false;
    }

    // Player 2 bindings
    public boolean p2Up()    { return isHeld(KeyEvent.VK_UP); }
    public boolean p2Down()  { return isHeld(KeyEvent.VK_DOWN); }
    public boolean p2Left()  { return isHeld(KeyEvent.VK_LEFT); }
    public boolean p2Right() { return isHeld(KeyEvent.VK_RIGHT); }
    public boolean p2Attack(){ return isHeld(KeyEvent.VK_ENTER); }

    public boolean p2AttackJustPressed() {
        if (wasJustPressed(KeyEvent.VK_ENTER)) { consume(KeyEvent.VK_ENTER); return true; }
        return false;
    }

    // Inventory hotbar selection. P1: 1,2,3,4 -> slot 0-3. P2: 0,9,8,7 -> slot 0-3
    // (mirrors P1's layout: weapon, medic kit, shield, secondary). Returns -1 if nothing pressed.
    public int p1SlotJustPressed() {
        int[] keys = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4};
        for (int i = 0; i < keys.length; i++) {
            if (wasJustPressed(keys[i])) { consume(keys[i]); return i; }
        }
        return -1;
    }

    public int p2SlotJustPressed() {
        int[] keys = {KeyEvent.VK_0, KeyEvent.VK_9, KeyEvent.VK_8, KeyEvent.VK_7};
        for (int i = 0; i < keys.length; i++) {
            if (wasJustPressed(keys[i])) { consume(keys[i]); return i; }
        }
        return -1;
    }
}
