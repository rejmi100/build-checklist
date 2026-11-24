package cz.rejmi.buildchecklist;

import cz.rejmi.buildchecklist.checklist.Checklist;
import cz.rejmi.buildchecklist.command.ChecklistCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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

		LOGGER.info("Build Checklist loaded");
	}
}
