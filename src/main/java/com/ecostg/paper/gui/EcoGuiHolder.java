package com.ecostg.paper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EcoGuiHolder implements InventoryHolder {

    private final GuiSession session;
    private Inventory inventory;

    public EcoGuiHolder(GuiSession session) {
        this.session = session;
    }

    public GuiSession session() {
        return session;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
