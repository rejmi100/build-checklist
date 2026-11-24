package cz.rejmi.buildchecklist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cz.rejmi.buildchecklist.BuildChecklistClient;
import cz.rejmi.buildchecklist.checklist.Checklist;
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
 * Saves the checklist as JSON, one file per world / server, in config/buildchecklist/.
 *
 * <p>File format (the "lists" object leaves room for multiple named lists later):
 * <pre>{"lists": {"default": {"minecraft:stone": 64, "minecraft:glass": 32}}}</pre>
 */
public class ChecklistStorage {
	private static final String DEFAULT_LIST = "default";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path directory;
	private final Checklist checklist;
	private Path currentFile; // null while not in a world

	public ChecklistStorage(Path directory, Checklist checklist) {
		this.directory = directory;
		this.checklist = checklist;
	}

	/** Called on joining a world/server: picks that world's file and loads it. */
	public void onJoin(Minecraft client) {
		currentFile = directory.resolve(worldKey(client) + ".json");
		checklist.replaceWith(load(currentFile));
	}

	/** Called on leaving: forget the list so it doesn't leak into the next world. */
	public void onDisconnect() {
		currentFile = null;
		checklist.replaceWith(Map.of());
	}

	public void save() {
		if (currentFile == null) return;
		JsonObject items = new JsonObject();
		checklist.entries().forEach((item, count) -> items.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), count));
		JsonObject lists = new JsonObject();
		lists.add(DEFAULT_LIST, items);
		JsonObject root = new JsonObject();
		root.add("lists", lists);

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

	private static Map<Item, Integer> load(Path file) {
		Map<Item, Integer> result = new LinkedHashMap<>();
		if (!Files.exists(file)) return result;
		try {
			JsonObject items = JsonParser.parseString(Files.readString(file))
					.getAsJsonObject().getAsJsonObject("lists").getAsJsonObject(DEFAULT_LIST);
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
