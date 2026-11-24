package cz.rejmi.buildchecklist.checklist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * All named checklists of the current world/server and which one is active.
 *
 * <p>The active list always lives in the single {@link Checklist} object that the HUD, commands and GUI use;
 * switching just swaps its contents. The other lists are kept here as plain maps.
 */
public class ChecklistLists {
	public static final String DEFAULT = "default";

	private final Checklist active;
	private final Map<String, Map<Item, Integer>> stored = new LinkedHashMap<>(); // every list; the active one may be stale
	private String activeName = DEFAULT;
	private Runnable onChange = () -> {
	};

	public ChecklistLists(Checklist active) {
		this.active = active;
		stored.put(DEFAULT, new LinkedHashMap<>());
	}

	/** Called after a list is created, deleted or switched, e.g. to save. */
	public void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	public Checklist active() {
		return active;
	}

	public String activeName() {
		return activeName;
	}

	public List<String> names() {
		return new ArrayList<>(stored.keySet());
	}

	/** Replaces everything, e.g. after loading. An unknown active name falls back to the first list. */
	public void load(Map<String, Map<Item, Integer>> lists, String activeName) {
		stored.clear();
		lists.forEach((name, entries) -> stored.put(name, new LinkedHashMap<>(entries)));
		if (stored.isEmpty()) stored.put(DEFAULT, new LinkedHashMap<>());
		this.activeName = stored.containsKey(activeName) ? activeName : stored.keySet().iterator().next();
		active.replaceWith(stored.get(this.activeName));
	}

	/** Snapshot of all lists for saving, with the active list's current contents. */
	public Map<String, Map<Item, Integer>> snapshot() {
		Map<String, Map<Item, Integer>> result = new LinkedHashMap<>(stored);
		result.put(activeName, new LinkedHashMap<>(active.entries()));
		return result;
	}

	/** Creates an empty list and makes it active. Returns false if the name is taken. */
	public boolean create(String name) {
		if (stored.containsKey(name)) return false;
		stored.put(name, new LinkedHashMap<>());
		return switchTo(name);
	}

	/** Returns false if there is no such list. */
	public boolean switchTo(String name) {
		if (!stored.containsKey(name)) return false;
		stored.put(activeName, new LinkedHashMap<>(active.entries())); // keep the edits made to the old active list
		activeName = name;
		active.replaceWith(stored.get(name));
		onChange.run();
		return true;
	}

	/** Switches to the next (+1) or previous (-1) list, wrapping around. */
	public void cycle(int direction) {
		List<String> names = names();
		int index = Math.floorMod(names.indexOf(activeName) + direction, names.size());
		switchTo(names.get(index));
	}

	/** Deletes a list. The last remaining list can't be deleted. Returns false if nothing was deleted. */
	public boolean delete(String name) {
		if (!stored.containsKey(name) || stored.size() == 1) return false;
		stored.remove(name);
		if (name.equals(activeName)) {
			activeName = stored.keySet().iterator().next();
			active.replaceWith(stored.get(activeName));
		}
		onChange.run();
		return true;
	}

	/** First free name of the form "list2", "list3"... for the GUI's [+] button. */
	public String nextFreeName() {
		int n = stored.size() + 1;
		while (stored.containsKey("list" + n)) n++;
		return "list" + n;
	}
}
