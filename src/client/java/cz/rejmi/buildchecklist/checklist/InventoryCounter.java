package cz.rejmi.buildchecklist.checklist;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Counts items the player has. Kept separate so counting in chests / shulker boxes
 * can later be added here without touching the HUD.
 */
public final class InventoryCounter {
	private InventoryCounter() {
	}

	/** Item -> total count over every inventory slot (hotbar, main, armor and offhand). */
	public static Map<Item, Integer> count(Inventory inventory) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}
}
