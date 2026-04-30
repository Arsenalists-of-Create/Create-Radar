package net.ato.shupapium.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

public class ShupapiumAmmoItem extends Item {
    public ShupapiumAmmoItem() { super(new Properties()); }
    public boolean projectileAffectedByWorldsGravity() { return true; }
    public MainProperties getMainProperties(ItemStack stack) { return null; }

    public static class MainProperties {
        public BallisticPropertiesComponent ballistics() { return null; }
    }
}