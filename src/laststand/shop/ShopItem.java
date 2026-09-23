package laststand.shop;

public class ShopItem {
    public final String label;
    public final String costLabel; // e.g. "20 Dark Orbs", or "" if nothing to show
    public final boolean actionable;
    private final Runnable action;

    public ShopItem(String label, String costLabel, boolean actionable, Runnable action) {
        this.label = label;
        this.costLabel = costLabel;
        this.actionable = actionable;
        this.action = action;
    }

    public void activate() {
        if (actionable && action != null) action.run();
    }
}
