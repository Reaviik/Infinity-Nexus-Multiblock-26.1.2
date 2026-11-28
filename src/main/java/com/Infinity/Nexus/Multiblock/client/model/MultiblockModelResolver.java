package com.Infinity.Nexus.Multiblock.client.model;

import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.client.model.UnbakedModelParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MultiblockModelResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter MODEL_FILES = FileToIdConverter.json("models");
    private static final Map<Identifier, Optional<ResolvedModel>> CACHE = new ConcurrentHashMap<>();

    private MultiblockModelResolver() {
    }

    public static @Nullable ResolvedModel resolve(Identifier modelId) {
        Optional<ResolvedModel> cached = CACHE.get(modelId);
        if (cached != null) {
            return cached.orElse(null);
        }
        return CACHE.computeIfAbsent(modelId, MultiblockModelResolver::load).orElse(null);
    }

    private static Optional<ResolvedModel> load(Identifier modelId) {
        Map<Identifier, UnbakedModel> models = new HashMap<>();
        if (!loadModels(modelId, models)) {
            LOGGER.warn("Unable to resolve multiblock controller model {}", modelId);
            return Optional.empty();
        }

        ModelDiscovery discovery = new ModelDiscovery(models, MissingCuboidModel.missingModel());
        discovery.addRoot(resolver -> resolver.markDependency(modelId));

        ResolvedModel missing = discovery.missingModel();
        ResolvedModel resolved = discovery.resolve().get(modelId);
        if (resolved == null || resolved == missing) {
            LOGGER.warn("Multiblock controller model {} resolved to the missing model", modelId);
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private static boolean loadModels(Identifier rootId, Map<Identifier, UnbakedModel> models) {
        Deque<Identifier> queue = new ArrayDeque<>();
        Set<Identifier> visiting = new HashSet<>();
        queue.add(rootId);

        while (!queue.isEmpty()) {
            Identifier id = queue.poll();
            if (models.containsKey(id) || !visiting.add(id)) {
                continue;
            }

            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                    .getResource(MODEL_FILES.idToFile(id));
            if (resource.isEmpty()) {
                LOGGER.warn("Multiblock controller model {} is missing", id);
                return false;
            }

            UnbakedModel model;
            try (BufferedReader reader = resource.get().openAsReader()) {
                model = UnbakedModelParser.parse(reader);
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse multiblock controller model {}: {}", id, exception.getMessage());
                return false;
            }

            models.put(id, model);
            if (model.parent() != null) {
                queue.add(model.parent());
            }
            if (model instanceof ResolvableModel resolvable) {
                resolvable.resolveDependencies(queue::add);
            }
        }
        return true;
    }

    public static void clearCache() {
        CACHE.clear();
        LOGGER.debug("Cleared multiblock controller model cache");
    }
}
