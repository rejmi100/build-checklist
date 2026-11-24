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
	private Runnable onChange = () -> {
	};

	/** Called after every user change (add/remove/clear), e.g. to save the list. */
	public void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	/** Adds {@code count} to the item's required amount (adding the same item twice sums up). Returns the new total. */
	public int add(Item item, int count) {
		int total = required.merge(item, count, Integer::sum);
		onChange.run();
		return total;
	}

	/** Sets the exact required amount; 0 or less removes the item. */
	public void set(Item item, int count) {
		if (count <= 0) {
			required.remove(item);
		} else {
			required.put(item, count);
		}
		onChange.run();
	}

	/** Returns true if the item was on the list. */
	public boolean remove(Item item) {
		boolean removed = required.remove(item) != null;
		if (removed) onChange.run();
		return removed;
	}

	public void clear() {
		required.clear();
		onChange.run();
	}

	/** Replaces the whole list, e.g. after loading from disk. Does not fire onChange. */
	public void replaceWith(Map<Item, Integer> entries) {
		required.clear();
		required.putAll(entries);
	}

	public boolean isEmpty() {
		return required.isEmpty();
	}

	/** Read-only view in display order. */
	public Map<Item, Integer> entries() {
		return Collections.unmodifiableMap(required);
	}
}
