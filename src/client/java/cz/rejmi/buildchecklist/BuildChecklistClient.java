package cz.rejmi.buildchecklist;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Fabric calls {@link #onInitializeClient()} once when the game starts.
 * This class only wires the mod's parts together; the logic lives in the sub-packages.
 */
public class BuildChecklistClient implements ClientModInitializer {
	public static final String MOD_ID = "buildchecklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Build Checklist loaded");
	}
}
