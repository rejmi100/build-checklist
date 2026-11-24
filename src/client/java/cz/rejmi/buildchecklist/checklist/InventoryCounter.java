package cz.rejmi.buildchecklist.checklist;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ContainerComponent;

/**
 * Counts the items the player has: the whole inventory, the contents of shulker boxes and bundles
 * carried in it, and the container the player currently has open (chest, barrel, ender chest...).
 */
public final class InventoryCounter {
	private InventoryCounter() {
	}

	/** Item -> total count. */
	public static Map<Item, Integer> count(Player player) {
		Map<Item, Integer> counts = new HashMap<>();
		Inventory inventory = player.getInventory();
		// Hotbar, main inventory, armor and offhand.
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			addStack(inventory.getItem(slot), counts);
		}
		// Slots of the open container that don't belong to the player's own inventory (already counted above).
		// Crafting result slots only show a preview of what would be crafted, so they are skipped.
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container != inventory && !(slot.container instanceof ResultContainer)) {
				addStack(slot.getItem(), counts);
			}
		}
		// The stack held on the mouse cursor while moving items around is in no slot.
		addStack(player.containerMenu.getCarried(), counts);
		return counts;
	}

	/** Adds the stack itself, then (recursively) whatever is stored inside it. */
	private static void addStack(ItemStack stack, Map<Item, Integer> counts) {
		if (stack.isEmpty()) return;
		counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
		addContents(stack.get(DataComponents.CONTAINER), counts);       // shulker boxes
		addContents(stack.get(DataComponents.BUNDLE_CONTENTS), counts); // bundles
	}

	private static void addContents(ContainerComponent<?> contents, Map<Item, Integer> counts) {
		if (contents != null) {
			contents.itemCopies().forEach(inner -> addStack(inner, counts));
		}
	}
}
