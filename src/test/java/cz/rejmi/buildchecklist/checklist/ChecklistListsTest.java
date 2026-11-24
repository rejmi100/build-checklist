package cz.rejmi.buildchecklist.checklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** List management logic. Uses empty lists only, so no Minecraft registries need to be loaded. */
class ChecklistListsTest {
	@Test
	void startsWithDefaultList() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		assertEquals(List.of(ChecklistLists.DEFAULT), lists.names());
		assertEquals(ChecklistLists.DEFAULT, lists.activeName());
	}

	@Test
	void createSwitchesAndRejectsDuplicates() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		assertTrue(lists.create("tower"));
		assertEquals("tower", lists.activeName());
		assertFalse(lists.create("tower"));
		assertFalse(lists.switchTo("missing"));
	}

	@Test
	void cycleWrapsAround() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		lists.create("b");
		lists.cycle(1); // b -> default (wraps)
		assertEquals(ChecklistLists.DEFAULT, lists.activeName());
		lists.cycle(-1); // default -> b (wraps backwards)
		assertEquals("b", lists.activeName());
	}

	@Test
	void lastListCannotBeDeletedAndDeletingActiveFallsBack() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		assertFalse(lists.delete(ChecklistLists.DEFAULT));
		lists.create("tower");
		assertTrue(lists.delete("tower"));
		assertEquals(ChecklistLists.DEFAULT, lists.activeName());
	}

	@Test
	void loadFallsBackToFirstListWhenActiveIsUnknown() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		lists.load(Map.of("a", Map.of()), "gone");
		assertEquals("a", lists.activeName());
		lists.load(Map.of(), "whatever"); // nothing saved yet -> fresh default list
		assertEquals(List.of(ChecklistLists.DEFAULT), lists.names());
	}

	@Test
	void nextFreeNameSkipsTakenNames() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		lists.create("list2");
		assertEquals("list3", lists.nextFreeName());
	}

	@Test
	void changesNotifyListener() {
		ChecklistLists lists = new ChecklistLists(new Checklist());
		int[] calls = {0};
		lists.setOnChange(() -> calls[0]++);
		lists.create("x");
		lists.switchTo(ChecklistLists.DEFAULT);
		lists.delete("x");
		assertEquals(3, calls[0]);
	}
}
