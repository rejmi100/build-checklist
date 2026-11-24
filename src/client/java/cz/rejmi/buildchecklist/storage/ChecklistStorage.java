package cz.rejmi.buildchecklist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cz.rejmi.buildchecklist.BuildChecklistClient;
import cz.rejmi.buildchecklist.checklist.ChecklistLists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Saves all checklists as JSON, one file per world / server, in config/buildchecklist/.
 *
 * <p>File format ("active" is optional, older files without it still load):
 * <pre>{"active": "default", "lists": {"default": {"minecraft:stone": 64}, "tower": {...}}}</pre>
 */
public class ChecklistStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path directory;
	private final ChecklistLists lists;
	private Path currentFile; // null while not in a world

	public ChecklistStorage(Path directory, ChecklistLists lists) {
		this.directory = directory;
		this.lists = lists;
	}

	/** Called on joining a world/server: picks that world's file and loads it. */
	public void onJoin(Minecraft client) {
		currentFile = directory.resolve(worldKey(client) + ".json");
		load(currentFile);
	}

	/** Called on leaving: forget the lists so they don't leak into the next world. */
	public void onDisconnect() {
		currentFile = null;
		lists.load(Map.of(), ChecklistLists.DEFAULT);
	}

	public void save() {
		if (currentFile == null) return;
		JsonObject listsJson = new JsonObject();
		lists.snapshot().forEach((name, entries) -> {
			JsonObject items = new JsonObject();
			entries.forEach((item, count) -> items.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), count));
			listsJson.add(name, items);
		});
		JsonObject root = new JsonObject();
		root.addProperty("active", lists.activeName());
		root.add("lists", listsJson);

		try {
			Files.createDirectories(directory);
			// Write to a temp file and move it over the real one, so a crash mid-write can't corrupt the saved list.
			Path tmp = currentFile.resolveSibling(currentFile.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, currentFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			BuildChecklistClient.LOGGER.error("Failed to save checklist to {}", currentFile, e);
		}
	}

	private void load(Path file) {
		Map<String, Map<Item, Integer>> result = new LinkedHashMap<>();
		String active = ChecklistLists.DEFAULT;
		if (Files.exists(file)) {
			try {
				JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				if (root.has("active")) active = root.get("active").getAsString();
				for (Map.Entry<String, JsonElement> list : root.getAsJsonObject("lists").entrySet()) {
					result.put(list.getKey(), parseItems(list.getValue().getAsJsonObject(), file));
				}
			} catch (IOException | RuntimeException e) {
				// RuntimeException covers malformed JSON / unexpected structure.
				// Move the broken file aside so the next save doesn't overwrite what the user may want to recover.
				BuildChecklistClient.LOGGER.error("Failed to load checklist from {}, keeping it as .broken", file, e);
				try {
					Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException moveError) {
					BuildChecklistClient.LOGGER.error("Could not back up {}", file, moveError);
				}
				result.clear();
			}
		}
		lists.load(result, active);
	}

	private static Map<Item, Integer> parseItems(JsonObject items, Path file) {
		Map<Item, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : items.entrySet()) {
			Identifier id = Identifier.tryParse(e.getKey());
			int count = e.getValue().getAsInt();
			// Skip unknown items (e.g. from a mod that was removed) and nonsense counts instead of failing the whole file.
			if (id == null || count <= 0 || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
				BuildChecklistClient.LOGGER.warn("Skipping invalid checklist entry {}={} in {}", e.getKey(), e.getValue(), file);
				continue;
			}
			result.put(BuiltInRegistries.ITEM.getValue(id), count);
		}
		return result;
	}

	/** "sp_<world folder>" in singleplayer, "mp_<server address>" in multiplayer, made safe for a file name. */
	private static String worldKey(Minecraft client) {
		String key;
		if (client.getSingleplayerServer() != null) {
			Path worldDir = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			key = "sp_" + worldDir.getFileName();
		} else {
			ServerData server = client.getCurrentServer();
			key = "mp_" + (server != null ? server.ip : "unknown");
		}
		return key.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
