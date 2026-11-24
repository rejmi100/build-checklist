package cz.rejmi.buildchecklist.checklist;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * The list of materials the player needs: item -> required amount.
 * Pure data, no knowledge of commands, HUD or files, so a future GUI or schematic
 * importer can use it the same way the commands do.
 */
public class Checklist {
	// LinkedHashMap keeps insertion order, so the HUD shows items in the order they were added.
	private final Map<Item, Integer> required = new LinkedHashMap<>();

	/** Adds {@code count} to the item's required amount (adding the same item twice sums up). Returns the new total. */
	public int add(Item item, int count) {
		return required.merge(item, count, Integer::sum);
	}

	/** Returns true if the item was on the list. */
	public boolean remove(Item item) {
		return required.remove(item) != null;
	}

	public void clear() {
		required.clear();
	}

	public boolean isEmpty() {
		return required.isEmpty();
	}

	/** Read-only view in display order. */
	public Map<Item, Integer> entries() {
		return Collections.unmodifiableMap(required);
	}
}
