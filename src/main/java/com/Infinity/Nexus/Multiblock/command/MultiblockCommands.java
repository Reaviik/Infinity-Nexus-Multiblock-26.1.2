package com.Infinity.Nexus.Multiblock.command;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.data.MultiblockStructureExporter;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class MultiblockCommands {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MultiblockCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        register(dispatcher, "infinity_nexus_multiblock");
        register(dispatcher, "inm");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(Commands.literal(name)
                .then(Commands.literal("hand").executes(MultiblockCommands::hand))
                .then(Commands.literal("look").executes(MultiblockCommands::look))
                .then(Commands.literal("list").executes(MultiblockCommands::list))
                .then(Commands.literal("give")
                        .then(Commands.argument("definition", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
                                        builder.suggest(definition.id().toString());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(MultiblockCommands::give))));
    }

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Identifier definitionId = IdentifierArgument.getId(context, "definition");
        MultiblockDefinition definition = MultiblockDefinitions.get(definitionId);
        if (definition == null) {
            source.sendFailure(Component.translatable(
                    "command.infinity_nexus_multiblock.unknown_definition", definitionId.toString()));
            return 0;
        }
        ItemStack stack = ModBlocks.controllerWithDefinition(definitionId);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable(
                "command.infinity_nexus_multiblock.give", definitionId.toString()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (MultiblockDefinitions.all().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.infinity_nexus_multiblock.list.empty"), false);
            return 0;
        }
        for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
            String id = definition.id().toString();
            Component entry = Component.literal(id).withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/inm give " + id))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.translatable("command.infinity_nexus_multiblock.list.give"))));
            source.sendSuccess(() -> entry, false);
        }
        return 1;
    }

    private static int hand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.infinity_nexus_multiblock.hand.empty"));
            return 0;
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, source.registryAccess());
        Ingredient ingredient = stack.getComponentsPatch().isEmpty()
                ? Ingredient.of(stack.getItem())
                : DataComponentIngredient.of(false, stack.getComponentsPatch(), stack.getItem());
        JsonElement input = encode(Ingredient.CODEC, ingredient, ops);
        JsonElement output = encode(
                MultiblockRecipeOutput.CODEC,
                new MultiblockRecipeOutput.Item(ItemStackTemplate.fromNonEmptyStack(stack)),
                ops);
        if (input == null || !(output instanceof JsonObject outputObject)) {
            source.sendFailure(Component.translatable("command.infinity_nexus_multiblock.encode_failed"));
            return 0;
        }

        sendCopyable(source, "command.infinity_nexus_multiblock.hand.input", GSON.toJson(input));
        sendCopyable(
                source,
                "command.infinity_nexus_multiblock.hand.output",
                GSON.toJson(orderedItemOutput(outputObject, stack)));
        return 1;
    }

    private static int look(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.translatable("command.infinity_nexus_multiblock.look.no_block"));
            return 0;
        }

        BlockState state = player.level().getBlockState(blockHit.getBlockPos());
        if (state.isAir()) {
            source.sendFailure(Component.translatable("command.infinity_nexus_multiblock.look.no_block"));
            return 0;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        JsonObject matcher = MultiblockStructureExporter.matcherJson(state);
        JsonObject stateJson = MultiblockStructureExporter.stateJson(state);
        sendCopyable(source, "command.infinity_nexus_multiblock.look.block", blockId);
        sendCopyable(
                source,
                "command.infinity_nexus_multiblock.look.matcher",
                GSON.toJson(matcher));
        if (stateJson.size() > 0) {
            sendCopyable(
                    source,
                    "command.infinity_nexus_multiblock.look.state",
                    GSON.toJson(stateJson));
        }
        return 1;
    }

    private static JsonObject orderedItemOutput(JsonObject encoded, ItemStack stack) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("type", "item");
        if (encoded.has("id")) {
            ordered.add("id", encoded.get("id"));
        }
        ordered.addProperty("count", stack.getCount());
        if (encoded.has("components")) {
            ordered.add("components", encoded.get("components"));
        }
        for (Map.Entry<String, JsonElement> entry : encoded.entrySet()) {
            if (!ordered.has(entry.getKey())) {
                ordered.add(entry.getKey(), entry.getValue());
            }
        }
        return ordered;
    }

    private static <T> @Nullable JsonElement encode(Codec<T> codec, T value, RegistryOps<JsonElement> ops) {
        return codec.encodeStart(ops, value).result().orElse(null);
    }

    private static void sendCopyable(CommandSourceStack source, String labelKey, String json) {
        Component line = Component.translatable(labelKey)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(json).withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(json))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("command.infinity_nexus_multiblock.copy")))));
        source.sendSuccess(() -> line, false);
    }
}
