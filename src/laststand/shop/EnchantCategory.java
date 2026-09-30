package laststand.shop;

/** Dagger/Bow are single categories; armor is split into its 3 physical pieces so each can be
 *  enchanted independently, even though they all share one unified ArmorTier for raw Defense.
 *  SWORD is kept as the enum constant's identifier (only the displayed name changed to "Dagger"
 *  after the weapon remake) so old saves' enchant slots still resolve correctly. */
public enum EnchantCategory {
    HELMET, CHESTPLATE, LEGGINGS, SWORD, BOW;

    public boolean isArmorPiece() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS;
    }
}
