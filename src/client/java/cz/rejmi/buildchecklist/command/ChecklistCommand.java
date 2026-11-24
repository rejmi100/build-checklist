package cz.rejmi.buildchecklist.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import cz.rejmi.buildchecklist.checklist.Checklist;
import cz.rejmi.buildchecklist.checklist.ChecklistLists;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side /checklist command. Client commands never reach the server,
 * which is why the mod works on vanilla servers.
 */
public final class ChecklistCommand {
	private static final int MAX_COUNT = 1_000_000;

	private ChecklistCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext, ChecklistLists lists) {
		Checklist checklist = lists.active(); // always the active list; switching swaps its contents
		dispatcher.register(literal("checklist")
				// /checklist add <item> <count> - ItemArgument is the same argument /give uses, so item ID autocomplete comes for free
				.then(literal("add")
						.then(argument("item", ItemArgument.item(buildContext))
								.then(argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
										.executes(ctx -> add(ctx, checklist, ItemArgument.getItem(ctx, "item").item().value())))))
				// /checklist addhand <count>
				.then(literal("addhand")
						.then(argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
								.executes(ctx -> addHand(ctx, checklist))))
				// /checklist remove <item>
				.then(literal("remove")
						.then(argument("item", ItemArgument.item(buildContext))
								.executes(ctx -> remove(ctx, checklist))))
				// /checklist clear
				.then(literal("clear")
						.executes(ctx -> {
							checklist.clear();
							ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.cleared"));
							return 1;
						}))
				// /checklist list
				.then(literal("list")
						.executes(ctx -> list(ctx, lists)))
				// /checklist lists - show all named lists
				.then(literal("lists")
						.executes(ctx -> showLists(ctx, lists)))
				// /checklist new <name> - word() allows letters, digits and _ - . +
				.then(literal("new")
						.then(argument("name", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (!lists.create(name)) {
										ctx.getSource().sendError(Component.translatable("command.buildchecklist.list_exists", name));
										return 0;
									}
									ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.list_created", name));
									return 1;
								})))
				// /checklist switch <name>
				.then(literal("switch")
						.then(argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(lists.names(), builder))
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (!lists.switchTo(name)) {
										ctx.getSource().sendError(Component.translatable("command.buildchecklist.list_unknown", name));
										return 0;
									}
									ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.list_switched", name));
									return 1;
								})))
				// /checklist delete <name>
				.then(literal("delete")
						.then(argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(lists.names(), builder))
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (!lists.names().contains(name)) {
										ctx.getSource().sendError(Component.translatable("command.buildchecklist.list_unknown", name));
										return 0;
									}
									if (!lists.delete(name)) {
										ctx.getSource().sendError(Component.translatable("command.buildchecklist.list_last"));
										return 0;
									}
									ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.list_deleted", name, lists.activeName()));
									return 1;
								}))));
	}

	private static int showLists(CommandContext<FabricClientCommandSource> ctx, ChecklistLists lists) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(Component.translatable("command.buildchecklist.lists_header").withStyle(ChatFormatting.GOLD));
		for (String name : lists.names()) {
			boolean active = name.equals(lists.activeName());
			source.sendFeedback(Component.literal((active ? " ▶ " : " - ") + name)
					.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
		return 1;
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, Checklist checklist, Item item) {
		int count = IntegerArgumentType.getInteger(ctx, "count");
		int total = checklist.add(item, count);
		ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.added", count, name(item), total));
		return 1;
	}

	private static int addHand(CommandContext<FabricClientCommandSource> ctx, Checklist checklist) {
		ItemStack held = ctx.getSource().getPlayer().getMainHandItem();
		if (held.isEmpty()) {
			ctx.getSource().sendError(Component.translatable("command.buildchecklist.empty_hand"));
			return 0;
		}
		return add(ctx, checklist, held.getItem());
	}

	private static int remove(CommandContext<FabricClientCommandSource> ctx, Checklist checklist) {
		Item item = ItemArgument.getItem(ctx, "item").item().value();
		if (!checklist.remove(item)) {
			ctx.getSource().sendError(Component.translatable("command.buildchecklist.not_on_list", name(item)));
			return 0;
		}
		ctx.getSource().sendFeedback(Component.translatable("command.buildchecklist.removed", name(item)));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx, ChecklistLists lists) {
		Checklist checklist = lists.active();
		FabricClientCommandSource source = ctx.getSource();
		if (checklist.isEmpty()) {
			source.sendFeedback(Component.translatable("command.buildchecklist.list_empty"));
			return 1;
		}
		source.sendFeedback(Component.translatable("command.buildchecklist.list_header", lists.activeName()).withStyle(ChatFormatting.GOLD));
		checklist.entries().forEach((item, required) ->
				source.sendFeedback(Component.literal(" - " + required + "× ").append(name(item))));
		return 1;
	}

	/** Localized item name, e.g. "Stone" / "Kámen". */
	private static Component name(Item item) {
		return item.getDefaultInstance().getHoverName();
	}
}
