package cz.rejmi.buildchecklist.hud;

import cz.rejmi.buildchecklist.checklist.Checklist;
import cz.rejmi.buildchecklist.checklist.ChecklistLists;
import cz.rejmi.buildchecklist.checklist.InventoryCounter;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * Draws the checklist in the top-left corner.
 * Inventory counting happens in {@link #tick} a few times per second; drawing only reads the cached result.
 */
public class ChecklistHud implements HudElement {
	private static final int RECOUNT_INTERVAL_TICKS = 10; // 2× per second is plenty for a checklist
	private static final int X = 4;
	private static final int Y = 4;
	private static final int ROW_HEIGHT = 18; // 16 px item icon + 2 px spacing
	private static final int PADDING = 3;

	// Text colors are ARGB, the leading FF is full opacity.
	private static final int COLOR_MISSING = 0xFFFF5555;
	private static final int COLOR_PARTIAL = 0xFFFFFF55;
	private static final int COLOR_DONE = 0xFF55FF55;
	private static final int BACKGROUND = 0x80000000;

	private static final int TITLE_HEIGHT = 12;
	private static final int COLOR_TITLE = 0xFFFFAA00;

	private final ChecklistLists lists;
	private final Checklist checklist;
	private Map<Item, Integer> counts = Map.of();
	private int ticksUntilRecount = 0;
	private boolean wasComplete = false;
	private boolean visible = true;

	public ChecklistHud(ChecklistLists lists) {
		this.lists = lists;
		this.checklist = lists.active();
	}

	public void toggleVisible() {
		visible = !visible;
	}

	/** Called at the end of every client tick (20× per second). */
	public void tick(Minecraft client) {
		if (client.player == null || --ticksUntilRecount > 0) {
			return;
		}
		ticksUntilRecount = RECOUNT_INTERVAL_TICKS;
		counts = InventoryCounter.count(client.player);

		// Announce completion once, at the moment the last item gets collected.
		boolean complete = isComplete();
		if (complete && !wasComplete) {
			client.player.sendOverlayMessage(Component.translatable("hud.buildchecklist.complete").withColor(COLOR_DONE));
		}
		wasComplete = complete;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!visible || checklist.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;

		// The list name is only worth showing once there is more than one list.
		Component title = lists.names().size() > 1 ? Component.literal(lists.activeName()) : null;

		// Measure the widest row first so the background fits the text.
		int width = title != null ? font.width(title) - 20 : 0;
		for (Map.Entry<Item, Integer> entry : checklist.entries().entrySet()) {
			width = Math.max(width, font.width(rowText(entry.getKey(), entry.getValue())));
		}
		int height = checklist.entries().size() * ROW_HEIGHT + (title != null ? TITLE_HEIGHT : 0);
		graphics.fill(X - PADDING, Y - PADDING, X + 20 + width + PADDING, Y + height + PADDING - 2, BACKGROUND);

		int y = Y;
		if (title != null) {
			graphics.text(font, title, X, y + 1, COLOR_TITLE, true);
			y += TITLE_HEIGHT;
		}
		for (Map.Entry<Item, Integer> entry : checklist.entries().entrySet()) {
			Item item = entry.getKey();
			int required = entry.getValue();
			graphics.fakeItem(item.getDefaultInstance(), X, y); // "fake" = icon only, not tied to a real inventory slot
			graphics.text(font, rowText(item, required), X + 20, y + 4, color(have(item), required), true);
			y += ROW_HEIGHT;
		}
	}

	private Component rowText(Item item, int required) {
		int have = have(item);
		String prefix = have >= required ? "✔ " : "";
		return Component.literal(prefix).append(item.getDefaultInstance().getHoverName()).append(" " + have + "/" + required);
	}

	private int have(Item item) {
		return counts.getOrDefault(item, 0);
	}

	private static int color(int have, int required) {
		if (have >= required) return COLOR_DONE;
		return have > 0 ? COLOR_PARTIAL : COLOR_MISSING;
	}

	private boolean isComplete() {
		if (checklist.isEmpty()) return false;
		return checklist.entries().entrySet().stream().allMatch(e -> have(e.getKey()) >= e.getValue());
	}
}
