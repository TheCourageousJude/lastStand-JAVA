package laststand.shop;

/**
 * A single shared wallet, spent/earned by either player (per the design doc:
 * "shop is for player 1 first, that is shared towards player 2").
 *
 *   dark orbs   +10 per wave completed
 *   silver orbs 33% chance per enemy kill
 *   yellow orbs granted on boss kill, OR traded for: 100 dark orbs, or 20 silver orbs
 */
public class Wallet {
    public int darkOrbs = 0;
    public int silverOrbs = 0;
    public int yellowOrbs = 0;

    public static final int DARK_ORBS_PER_WAVE = 10;
    public static final double SILVER_ORB_DROP_CHANCE = 0.33;
    public static final int DARK_ORBS_PER_YELLOW = 100;
    public static final int SILVER_ORBS_PER_YELLOW = 20;

    public void addDarkOrbs(int amount) { darkOrbs += amount; }
    public void addSilverOrbs(int amount) { silverOrbs += amount; }
    public void addYellowOrbs(int amount) { yellowOrbs += amount; }

    public int balanceOf(Currency currency) {
        return switch (currency) {
            case DARK -> darkOrbs;
            case SILVER -> silverOrbs;
            case YELLOW -> yellowOrbs;
        };
    }

    /** Returns true if the player could afford it and the amount was deducted. */
    public boolean trySpend(Currency currency, int amount) {
        if (balanceOf(currency) < amount) return false;
        switch (currency) {
            case DARK -> darkOrbs -= amount;
            case SILVER -> silverOrbs -= amount;
            case YELLOW -> yellowOrbs -= amount;
        }
        return true;
    }

    /** Called on game over: "reset all resources back to zero each time the game is over". */
    public void reset() {
        darkOrbs = 0;
        silverOrbs = 0;
        yellowOrbs = 0;
    }

    /** Returns true if the trade happened. */
    public boolean tryTradeDarkForYellow() {
        if (darkOrbs < DARK_ORBS_PER_YELLOW) return false;
        darkOrbs -= DARK_ORBS_PER_YELLOW;
        yellowOrbs += 1;
        return true;
    }

    /** Returns true if the trade happened. */
    public boolean tryTradeSilverForYellow() {
        if (silverOrbs < SILVER_ORBS_PER_YELLOW) return false;
        silverOrbs -= SILVER_ORBS_PER_YELLOW;
        yellowOrbs += 1;
        return true;
    }
}
