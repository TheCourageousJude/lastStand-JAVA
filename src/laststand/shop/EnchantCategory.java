package laststand.shop;

/** Sword/Bow are single categories; armor is split into its 3 physical pieces so each can be
 *  enchanted independently, even though they all share one unified ArmorTier for raw Defense. */
public enum EnchantCategory {
    HELMET, CHESTPLATE, LEGGINGS, SWORD, BOW;

    public boolean isArmorPiece() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS;
    }
}
