package cz.rejmi.buildchecklist;

import com.mojang.blaze3d.platform.InputConstants;
import cz.rejmi.buildchecklist.checklist.Checklist;
import cz.rejmi.buildchecklist.command.ChecklistCommand;
import cz.rejmi.buildchecklist.hud.ChecklistHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Fabric calls {@link #onInitializeClient()} once when the game starts.
 * This class only wires the mod's parts together; the logic lives in the sub-packages.
 */
public class BuildChecklistClient implements ClientModInitializer {
	public static final String MOD_ID = "buildchecklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The active checklist (in memory for now; saving per world comes later). */
	public static final Checklist CHECKLIST = new Checklist();

	@Override
	public void onInitializeClient() {
		// Fabric fires this event whenever the client (re)builds its command tree, e.g. on joining a world.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				ChecklistCommand.register(dispatcher, buildContext, CHECKLIST));

		ChecklistHud hud = new ChecklistHud(CHECKLIST);

		// Default H, rebindable in Options -> Controls (Minecraft saves the choice in options.txt).
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.buildchecklist.toggle_hud", InputConstants.KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() returns true once per press, so a held key doesn't flicker the HUD.
			while (toggleKey.consumeClick()) {
				hud.toggleVisible();
			}
			hud.tick(client);
		});
		// Draw just before the chat, so open chat stays on top of our overlay.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "checklist"), hud);

		LOGGER.info("Build Checklist loaded");
	}
}
