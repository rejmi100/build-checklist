package cz.rejmi.buildchecklist.gui;

import com.mojang.blaze3d.platform.InputConstants;
import cz.rejmi.buildchecklist.checklist.Checklist;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Screen for editing the checklist with the mouse.
 * Left: search box + grid of all items (click = +1 stack, Shift+click = +1).
 * Right: the current checklist with [−] [+] [×] per row; clicking a count lets you type it.
 *
 * <p>The grid and the list are drawn and hit-tested by hand instead of using one Button widget per
 * item, so nothing has to be rebuilt when the list changes. Only the search box is a vanilla widget.
 */
public class ChecklistScreen extends Screen {
	private static final int CELL = 18;       // item slot size in the grid (16 px icon + 2 px gap)
	private static final int ROW = 18;        // row height in the list
	private static final int TOP = 42;        // y where the grid and the list start
	private static final int MARGIN = 10;
	private static final int BUTTON = 12;     // size of the [−] [+] [×] boxes
	private static final int COUNT_WIDTH = 40; // clickable count area / count edit box width
	private static final int MAX_COUNT = 1_000_000;

	private static final int PANEL_BG = 0xA0000000;
	private static final int HOVER_BG = 0x40FFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;

	private final Checklist checklist;
	private final List<Item> allItems = new ArrayList<>();
	private List<Item> filtered = List.of();
	private EditBox search;
	private EditBox countBox;    // shown over a row's count while typing it
	private Item editingItem;    // the item whose count is being typed, or null
	private int gridScroll = 0; // in rows
	private int listScroll = 0; // in rows

	public ChecklistScreen(Checklist checklist) {
		super(Component.translatable("gui.buildchecklist.title"));
		this.checklist = checklist;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR) allItems.add(item);
		}
	}

	// ---- layout helpers (recomputed from the current window size) ----

	private int gridX() {
		return MARGIN;
	}

	private int gridColumns() {
		return Math.max(1, (width / 2 - MARGIN * 2) / CELL);
	}

	private int visibleRows() {
		return Math.max(1, (height - TOP - MARGIN) / ROW);
	}

	private int listX() {
		return width / 2 + MARGIN / 2;
	}

	private int listRight() {
		return width - MARGIN;
	}

	@Override
	protected void init() {
		// init() runs on open and on every window resize, so the widget is (re)created here.
		String previous = search != null ? search.getValue() : "";
		search = new EditBox(font, gridX(), 20, gridColumns() * CELL, 16, Component.translatable("gui.buildchecklist.search"));
		search.setHint(Component.translatable("gui.buildchecklist.search"));
		search.setValue(previous);
		search.setResponder(text -> applyFilter());
		addRenderableWidget(search);
		setInitialFocus(search);
		applyFilter();

		countBox = new EditBox(font, 0, 0, COUNT_WIDTH, 12, Component.translatable("gui.buildchecklist.count"));
		countBox.setMaxLength(7);
		countBox.setVisible(false);
		addRenderableWidget(countBox);
		editingItem = null; // a resize rebuilds the widgets, so drop any half-typed count
	}

	private void startEditing(Item item, int count, int x, int y) {
		editingItem = item;
		countBox.setX(x);
		countBox.setY(y);
		countBox.setValue(String.valueOf(count));
		countBox.moveCursorToEnd(false);
		countBox.setVisible(true);
		setFocused(countBox);
	}

	/** Applies the typed count if it is a valid number (1 – 1 000 000); anything else is ignored. */
	private void finishEditing(boolean apply) {
		if (editingItem == null) return;
		if (apply) {
			try {
				int value = Integer.parseInt(countBox.getValue().trim());
				if (value > 0 && value <= MAX_COUNT) checklist.set(editingItem, value);
			} catch (NumberFormatException ignored) {
				// not a number: keep the old count
			}
		}
		editingItem = null;
		countBox.setVisible(false);
		setFocused(search);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editingItem != null) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				finishEditing(true);
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE) {
				finishEditing(false); // Esc closes the edit box, not the whole screen
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/** Filters by the localized name or the item ID, e.g. "kámen", "stone" or "minecraft:stone". */
	private void applyFilter() {
		String query = search.getValue().trim().toLowerCase(Locale.ROOT);
		filtered = query.isEmpty() ? allItems : allItems.stream()
				.filter(item -> name(item).toLowerCase(Locale.ROOT).contains(query)
						|| BuiltInRegistries.ITEM.getKey(item).toString().contains(query))
				.toList();
		gridScroll = 0;
	}

	/** Dark panels go in the background layer, so the widgets (search, count box) are drawn on top of them. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int rows = visibleRows();
		graphics.fill(gridX() - 2, TOP - 2, gridX() + gridColumns() * CELL + 2, TOP + rows * CELL + 2, PANEL_BG);
		graphics.fill(listX() - 2, TOP - 2, listRight() + 2, TOP + rows * ROW + 2, PANEL_BG);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta); // background + search box
		graphics.text(font, title, listX(), 8, TEXT, true);
		extractGrid(graphics, mouseX, mouseY);
		extractList(graphics, mouseX, mouseY);
	}

	private void extractGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int cols = gridColumns();
		int rows = visibleRows();

		for (int i = 0; i < rows * cols; i++) {
			int index = gridScroll * cols + i;
			if (index >= filtered.size()) break;
			Item item = filtered.get(index);
			int x = gridX() + (i % cols) * CELL;
			int y = TOP + (i / cols) * CELL;
			if (inside(mouseX, mouseY, x, y, CELL, CELL)) {
				graphics.fill(x, y, x + CELL, y + CELL, HOVER_BG);
				graphics.setTooltipForNextFrame(font, item.getDefaultInstance(), mouseX, mouseY);
			}
			graphics.fakeItem(item.getDefaultInstance(), x + 1, y + 1);
		}
		if (filtered.isEmpty()) {
			graphics.text(font, Component.translatable("gui.buildchecklist.no_results"), gridX() + 4, TOP + 4, TEXT_DIM, true);
		}
	}

	private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int rows = visibleRows();

		List<Map.Entry<Item, Integer>> entries = new ArrayList<>(checklist.entries().entrySet());
		if (entries.isEmpty()) {
			graphics.text(font, Component.translatable("gui.buildchecklist.empty"), listX() + 4, TOP + 4, TEXT_DIM, true);
			return;
		}
		for (int row = 0; row < rows; row++) {
			int index = listScroll + row;
			if (index >= entries.size()) break;
			Item item = entries.get(index).getKey();
			int count = entries.get(index).getValue();
			int y = TOP + row * ROW;

			graphics.fakeItem(item.getDefaultInstance(), listX(), y + 1);
			String countText = String.valueOf(count);
			int buttonsX = listRight() - 3 * (BUTTON + 2);
			int countX = buttonsX - 6 - font.width(countText);
			int countAreaX = buttonsX - 4 - COUNT_WIDTH;
			// Cut long names so they don't overlap the count area.
			String shownName = font.plainSubstrByWidth(name(item), countAreaX - (listX() + 20) - 4);
			graphics.text(font, shownName, listX() + 20, y + 5, TEXT, true);
			if (item != editingItem) {
				boolean hover = inside(mouseX, mouseY, countAreaX, y + 3, COUNT_WIDTH, BUTTON);
				if (hover) graphics.fill(countAreaX, y + 3, countAreaX + COUNT_WIDTH, y + 3 + BUTTON, HOVER_BG);
				graphics.text(font, countText, countX, y + 5, TEXT, true);
			}

			extractButton(graphics, mouseX, mouseY, buttonsX, y + 3, "−");
			extractButton(graphics, mouseX, mouseY, buttonsX + BUTTON + 2, y + 3, "+");
			extractButton(graphics, mouseX, mouseY, buttonsX + 2 * (BUTTON + 2), y + 3, "×");
		}
	}

	private void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, String label) {
		boolean hover = inside(mouseX, mouseY, x, y, BUTTON, BUTTON);
		graphics.fill(x, y, x + BUTTON, y + BUTTON, hover ? 0xFF707070 : 0xFF404040);
		graphics.text(font, label, x + (BUTTON - font.width(label)) / 2 + 1, y + 2, TEXT, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Clicking anywhere outside the count box confirms the typed count.
		if (editingItem != null && !countBox.isMouseOver(event.x(), event.y())) {
			finishEditing(true);
		}
		// Let the widgets (search, count box) handle their own clicks first.
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 0) return false; // left button only
		int mx = (int) event.x();
		int my = (int) event.y();
		boolean single = event.hasShiftDown(); // Shift = change by 1 instead of a whole stack

		// Grid: add the clicked item.
		int cols = gridColumns();
		if (inside(mx, my, gridX(), TOP, cols * CELL, visibleRows() * CELL)) {
			int index = gridScroll * cols + (my - TOP) / CELL * cols + (mx - gridX()) / CELL;
			if (index < filtered.size()) {
				Item item = filtered.get(index);
				checklist.add(item, single ? 1 : stackSize(item));
				return true;
			}
		}

		// List: [−] [+] [×] buttons.
		List<Map.Entry<Item, Integer>> entries = new ArrayList<>(checklist.entries().entrySet());
		int buttonsX = listRight() - 3 * (BUTTON + 2);
		for (int row = 0; row < visibleRows() && listScroll + row < entries.size(); row++) {
			Item item = entries.get(listScroll + row).getKey();
			int count = entries.get(listScroll + row).getValue();
			int y = TOP + row * ROW + 3;
			int step = single ? 1 : stackSize(item);
			if (inside(mx, my, buttonsX - 4 - COUNT_WIDTH, y, COUNT_WIDTH, BUTTON)) {
				startEditing(item, count, buttonsX - 4 - COUNT_WIDTH, y);
				return true;
			} else if (inside(mx, my, buttonsX, y, BUTTON, BUTTON)) {
				checklist.set(item, count - step);
			} else if (inside(mx, my, buttonsX + BUTTON + 2, y, BUTTON, BUTTON)) {
				checklist.set(item, count + step);
			} else if (inside(mx, my, buttonsX + 2 * (BUTTON + 2), y, BUTTON, BUTTON)) {
				checklist.remove(item);
			} else {
				continue;
			}
			clampListScroll();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		finishEditing(true); // the edited row may scroll away
		int step = scrollY > 0 ? -1 : 1; // wheel up = scroll up
		if (mouseX < width / 2.0) {
			int maxRows = (filtered.size() + gridColumns() - 1) / gridColumns();
			gridScroll = Math.clamp(gridScroll + step, 0, Math.max(0, maxRows - visibleRows()));
		} else {
			listScroll += step;
			clampListScroll();
		}
		return true;
	}

	/** Keeps the list scroll valid after rows were removed or the window was resized. */
	private void clampListScroll() {
		listScroll = Math.clamp(listScroll, 0, Math.max(0, checklist.entries().size() - visibleRows()));
	}

	@Override
	public boolean isPauseScreen() {
		return false; // keep the world (and the HUD counts) running while the screen is open
	}

	private static int stackSize(Item item) {
		return item.getDefaultInstance().getMaxStackSize();
	}

	private static String name(Item item) {
		return item.getDefaultInstance().getHoverName().getString();
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
