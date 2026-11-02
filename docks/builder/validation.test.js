const assert = require("node:assert/strict");
const V = require("./validation.js");

let passed = 0;

function test(name, fn) {
  try {
    fn();
    passed += 1;
    console.log("ok - " + name);
  } catch (error) {
    console.error("not ok - " + name);
    console.error(error && error.stack ? error.stack : error);
    process.exitCode = 1;
  }
}

function codes(result) {
  return result.issues.map((entry) => entry.code);
}

function withState(mutator) {
  const state = V.clone(V.createDefaultState());
  mutator(state);
  return state;
}

function controllerTextures(prefix) {
  const textures = {};
  V.CONTROLLER_FACES.forEach((face) => {
    textures[face] = prefix + "/" + face;
  });
  return textures;
}

function rawController(block, prefix, extra) {
  return Object.assign({
    block,
    textures: controllerTextures(prefix)
  }, extra || {});
}

function withController(value, block, prefix, extra) {
  value.controller = V.parseController(rawController(block, prefix, extra), []);
  return value;
}

function rawGui(extra) {
  return Object.assign({
    texture: "infinity_nexus_multiblock:textures/gui/nexus_tester.png",
    width: 176,
    height: 166,
    texture_width: 256,
    texture_height: 256,
    elements: [
      { type: "title", x: 8, y: 6, text: "gui.nexus_tester", color: "0xFF404040", shadow: false },
      { type: "player_inventory", x: 8, y: 84 },
      { type: "energy_bar", x: 165, y: 5, width: 3, height: 64, capability: "energy", color: "0xFF6BD64A" },
      { type: "fluid_bar", x: 160, y: 5, width: 3, height: 64, capability: "water_tank" },
      { type: "progress_bar", x: 63, y: 66, width: 57, height: 3, direction: "right" },
      { type: "arrow", x: 62, y: 35, width: 22, height: 16, u: 176, v: 0, direction: "right" },
      { type: "entity", x: 120, y: 17, width: 32, height: 32 }
    ]
  }, extra || {});
}

function withGuiState(mutator) {
  return withState((value) => {
    value.path = "nexus_tester";
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[0].x = 8;
    value.itemSlots[0].y = 17;
    value.itemSlots[1].id = "output_0";
    value.itemSlots[1].role = "output";
    value.itemSlots[1].x = 86;
    value.itemSlots[1].y = 26;
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.energy.capacity = 10000;
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilities.fluids[0].capacity = 10000;
    value.gui = V.parseGui(rawGui(), []);
    if (mutator) {
      mutator(value);
    }
    return value;
  });
}

test("estado padrão é válido e exporta o esqueleto", () => {
  const result = V.validateState(V.createDefaultState());
  assert.deepEqual(result.errors, []);
  assert.ok(result.checks.every((check) => check.ok));
  const json = V.buildDefinition(V.createDefaultState());
  assert.equal(json.schema_version, 1);
  assert.equal(json.id, "infinity_nexus_multiblock:minha_maquina");
  assert.equal(json.type, "storage");
  assert.equal(json.tier, undefined);
  assert.equal(json.priority, undefined);
  assert.equal(json.inventory_rows, undefined);
  assert.deepEqual(json.pattern.layers, [["III", "ICI", "III"]]);
  assert.equal(json.pattern.anchor, "C");
  assert.equal(json.pattern.matchers.I, "minecraft:iron_block");
});

test("type=recipe exige recipe_type", () => {
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("recipe_type.required"));
  assert.ok(result.errors.length > 0);
});

test("recipe_type desconhecido em namespace externo gera aviso, não erro", () => {
  const state = withState((value) => {
    value.namespace = "mymod";
    value.path = "fornalha";
    value.type = "recipe";
    value.recipeType = "mymod:outro_tipo";
    withController(value, "mymod:fornalha", "mymod:block/fornalha");
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("recipe_type.unknown"));
  assert.equal(result.errors.length, 0);
});

test("recipe_type novo no namespace do mod é aceito sem aviso", () => {
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_tester";
  });
  const result = V.validateState(state);
  assert.ok(!codes(result).includes("recipe_type.unknown"));
  assert.equal(result.errors.length, 0);
});

test("storage com recipe_type gera aviso e o JSON omite o campo", () => {
  const state = withState((value) => {
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("recipe_type.ignored"));
  assert.equal(V.buildDefinition(state).recipe_type, undefined);
});

test("namespace e path inválidos são rejeitados", () => {
  const state = withState((value) => {
    value.namespace = "MeuMod";
    value.path = "Máquina Nova";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("id.namespace.invalid"));
  assert.ok(codes(result).includes("id.path.invalid"));
});

test("namespace externo sem controller bloqueia a exportação", () => {
  const state = withState((value) => {
    value.namespace = "infinity_nexus_build_test";
    value.path = "maquina_teste";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("controller.dynamic.required"));
  assert.ok(result.errors.length > 0);
  assert.equal(result.checks.find((check) => check.id === "identity").ok, true);
  assert.equal(result.checks.find((check) => check.id === "controller").ok, false);
});

test("namespace externo com controller registrado só gera aviso", () => {
  const state = withState((value) => {
    value.namespace = "mymod";
    value.path = "fornalha";
    withController(value, "mymod:fornalha", "mymod:block/fornalha");
  });
  const result = V.validateState(state);
  assert.equal(result.errors.length, 0);
  assert.ok(codes(result).includes("controller.datapack"));
});

test("controller.block é sincronizado com o id no download", () => {
  const state = withState((value) => {
    value.namespace = "infinity_nexus_multiblock";
    value.path = "nexus_tester";
    withController(value, "infinity_nexus_multiblock:assembler", "infinity_nexus_multiblock:block/assembler");
  });
  const result = V.validateState(state);
  assert.equal(result.errors.length, 0);
  assert.ok(codes(result).includes("controller.block.sync"));
  assert.equal(V.buildDefinition(state).controller.block, "infinity_nexus_multiblock:nexus_tester");
  assert.deepEqual(V.buildDefinition(state).controller.textures, controllerTextures("infinity_nexus_multiblock:block/assembler"));
});

test("controller exige as seis texturas e aponta a face ausente", () => {
  const raw = rawController("infinity_nexus_multiblock:nexus_tester", "infinity_nexus_multiblock:block/nexus_tester");
  delete raw.textures.back;
  const state = withState((value) => {
    value.path = "nexus_tester";
    value.controller = V.parseController(raw, []);
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("controller.texture.required"));
  const issue = result.errors.find((entry) => entry.code === "controller.texture.required");
  assert.equal(issue.field, "texture:back");
});

test("controller valida formed_textures, particle e light_level", () => {
  const state = withState((value) => {
    value.path = "nexus_tester";
    withController(value, "infinity_nexus_multiblock:nexus_tester", "infinity_nexus_multiblock:block/nexus_tester", {
      formed_textures: { front: "infinity_nexus_multiblock:block/nexus_tester/formed_front" },
      particle: "minecraft:smoke",
      light_level: 7
    });
  });
  assert.deepEqual(V.validateState(state).errors, []);
  const built = V.buildDefinition(state).controller;
  assert.deepEqual(built.formed_textures, { front: "infinity_nexus_multiblock:block/nexus_tester/formed_front" });
  assert.equal(built.particle, "minecraft:smoke");
  assert.equal(built.light_level, 7);

  const broken = withState((value) => {
    value.path = "nexus_tester";
    withController(value, "infinity_nexus_multiblock:nexus_tester", "infinity_nexus_multiblock:block/nexus_tester", {
      formed_textures: { front: "bad texture" },
      particle: "bad particle"
    });
    value.controller.lightLevel = 16;
  });
  const brokenCodes = codes(V.validateState(broken));
  assert.ok(brokenCodes.includes("controller.formed_texture.invalid"));
  assert.ok(brokenCodes.includes("controller.particle.invalid"));
  assert.ok(brokenCodes.includes("controller.light_level.invalid"));
});

test("controller sem bloco ou com textura inválida bloqueia", () => {
  const missingBlock = withState((value) => {
    value.namespace = "mymod";
    value.path = "nexus_tester";
    withController(value, "", "mymod:block/nexus_tester");
  });
  assert.ok(codes(V.validateState(missingBlock)).includes("controller.block.required"));

  const badTexture = withState((value) => {
    value.path = "nexus_tester";
    const raw = rawController("infinity_nexus_multiblock:nexus_tester", "infinity_nexus_multiblock:block/nexus_tester");
    raw.textures.left = "sem namespace";
    value.controller = V.parseController(raw, []);
  });
  assert.ok(codes(V.validateState(badTexture)).includes("controller.texture.invalid"));
});

test("gui é opcional e só aparece quando definida", () => {
  const json = V.buildDefinition(V.createDefaultState());
  assert.equal(json.gui, undefined);
  const empty = withState((value) => {
    value.gui = V.parseGui({ width: 200, height: 180, elements: [] }, []);
  });
  assert.deepEqual(V.validateState(empty).errors, []);
  assert.equal(V.buildDefinition(empty).gui.width, 200);
  assert.equal(V.buildDefinition(empty).gui.height, 180);
});

test("gui customizada válida exporta elementos e passa no checklist", () => {
  const state = withGuiState();
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.equal(result.checks.find((check) => check.id === "gui").ok, true);
  assert.equal(V.guiProblem(state.gui, state), "");
  const gui = V.buildDefinition(state).gui;
  assert.equal(gui.texture, "infinity_nexus_multiblock:textures/gui/nexus_tester.png");
  assert.equal(gui.elements.length, 7);
  assert.deepEqual(gui.elements[1], {
    type: "player_inventory",
    x: 8,
    y: 84,
    color: "404040",
    shadow: false
  });
  assert.equal(gui.elements[4].direction, "right");
});

test("gui entity exporta a area e exige type=recipe", () => {
  const state = withGuiState();
  const entity = V.buildDefinition(state).gui.elements.find((element) => element.type === "entity");
  assert.deepEqual(entity, { type: "entity", x: 120, y: 17, width: 32, height: 32 });
  assert.equal(V.createGuiElement("entity").width, 32);
  assert.equal(V.createGuiElement("entity").height, 32);

  const storage = withGuiState((value) => {
    value.type = "storage";
    value.recipeType = "";
  });
  assert.ok(codes(V.validateState(storage)).includes("gui.recipe.required"));

  const noSize = withGuiState((value) => {
    value.gui.elements.find((element) => element.type === "entity").width = 0;
  });
  assert.ok(codes(V.validateState(noSize)).includes("gui.element.size.invalid"));
});

test("player_inventory exporta shadow e color proprios", () => {
  const state = withGuiState((value) => {
    const inventory = value.gui.elements.find((element) => element.type === "player_inventory");
    inventory.shadow = false;
    inventory.color = "0xFF112233";
  });
  const gui = V.buildGui(state.gui);
  const inventory = gui.elements.find((element) => element.type === "player_inventory");
  assert.equal(inventory.shadow, false);
  assert.equal(inventory.color, "0xFF112233");
});

test("gui customizada aceita sem item_slots e exige para elementos slot", () => {
  const withoutSlots = withGuiState((value) => {
    value.itemSlots = null;
  });
  assert.ok(!codes(V.validateState(withoutSlots)).includes("gui.item_slots.required"));
  assert.equal(V.guiProblem(withoutSlots.gui, withoutSlots), "");

  const slotWithoutSlots = withGuiState((value) => {
    value.itemSlots = null;
    const slot = V.createGuiElement("slot");
    slot.slot = "input_0";
    value.gui.elements.push(slot);
  });
  assert.ok(codes(V.validateState(slotWithoutSlots)).includes("gui.item_slots.required"));

  const withoutPlayer = withGuiState((value) => {
    value.gui.elements = value.gui.elements.filter((element) => element.type !== "player_inventory");
  });
  assert.ok(codes(V.validateState(withoutPlayer)).includes("gui.player_inventory.count"));
});

test("gui valida capabilities das barras e tipo da receita", () => {
  const noEnergy = withGuiState((value) => {
    value.capabilities.energy = null;
  });
  assert.ok(codes(V.validateState(noEnergy)).includes("gui.energy.required"));

  const wrongEnergy = withGuiState((value) => {
    value.capabilities.energy.id = "other_energy";
  });
  assert.ok(codes(V.validateState(wrongEnergy)).includes("gui.energy.unknown"));

  const wrongFluid = withGuiState((value) => {
    value.gui.elements.find((element) => element.type === "fluid_bar").capability = "lava_tank";
  });
  assert.ok(codes(V.validateState(wrongFluid)).includes("gui.fluid.unknown"));

  const storage = withGuiState((value) => {
    value.type = "storage";
    value.recipeType = "";
  });
  assert.ok(codes(V.validateState(storage)).includes("gui.recipe.required"));
});

test("gui valida bounds, mistura de slots e referências", () => {
  const outOfBounds = withGuiState((value) => {
    value.gui.elements.find((element) => element.type === "player_inventory").x = 20;
  });
  assert.ok(codes(V.validateState(outOfBounds)).includes("gui.bounds.exceeded"));

  const mixed = withGuiState((value) => {
    value.gui.elements.push(V.createGuiElement("slot"));
    value.gui.elements[value.gui.elements.length - 1].slot = "input_0";
  });
  assert.ok(codes(V.validateState(mixed)).includes("gui.slot.mixed"));

  const duplicate = withGuiState((value) => {
    value.itemSlots.forEach((slot) => {
      slot.x = null;
      slot.y = null;
    });
    value.gui.elements.push(V.createGuiElement("slot"));
    value.gui.elements[value.gui.elements.length - 1].slot = "input_0";
    value.gui.elements.push(V.createGuiElement("slot"));
    value.gui.elements[value.gui.elements.length - 1].slot = "input_0";
  });
  assert.ok(codes(V.validateState(duplicate)).includes("gui.slot.duplicate"));
  assert.ok(V.guiProblem(duplicate.gui, duplicate).indexOf("mais de um") !== -1);

  const unknown = withGuiState((value) => {
    value.itemSlots.forEach((slot) => {
      slot.x = null;
      slot.y = null;
    });
    value.gui.elements.push(V.createGuiElement("slot"));
    value.gui.elements[value.gui.elements.length - 1].slot = "missing_slot";
  });
  assert.ok(codes(V.validateState(unknown)).includes("gui.slot.unknown"));
});

test("gui valida tipo, cor e campos de elementos", () => {
  const state = withGuiState((value) => {
    value.gui.elements[0].type = "unknown";
    value.gui.elements[1].color = "not-a-color";
    value.gui.elements[2].direction = "sideways";
    value.gui.elements[3].shadow = "yes";
  });
  const resultCodes = codes(V.validateState(state));
  assert.ok(resultCodes.includes("gui.element.type.invalid"));
  assert.ok(resultCodes.includes("gui.element.color.invalid"));
  assert.ok(resultCodes.includes("gui.element.direction.invalid"));
  assert.ok(resultCodes.includes("gui.element.shadow.invalid"));
});

test("gui barras usam cores padrão verde e vermelha", () => {
  assert.equal(V.createGuiElement("energy_bar").color, "0xFFFF0000");
  assert.equal(V.createGuiElement("progress_bar").color, "0xFF00AA00");
});

test("gui novos elementos começam com o layout do spawner", () => {
  const title = V.createGuiElement("title");
  assert.deepEqual(
    { x: title.x, y: title.y, color: title.color, shadow: title.shadow },
    { x: 8, y: 4, color: "404040", shadow: false }
  );

  const text = V.createGuiElement("text");
  assert.deepEqual(
    { x: text.x, y: text.y, color: text.color, shadow: text.shadow },
    { x: 8, y: 75, color: "404040", shadow: false }
  );

  const inventory = V.createGuiElement("player_inventory");
  assert.deepEqual(
    { x: inventory.x, y: inventory.y, color: inventory.color, shadow: inventory.shadow },
    { x: 8, y: 84, color: "404040", shadow: false }
  );

  const slot = V.createGuiElement("slot");
  assert.deepEqual(
    { x: slot.x, y: slot.y, width: slot.width, height: slot.height },
    { x: 8, y: 16, width: 18, height: 18 }
  );

  const energy = V.createGuiElement("energy_bar");
  assert.deepEqual(
    { x: energy.x, y: energy.y, width: energy.width, height: energy.height, capability: energy.capability },
    { x: 158, y: 10, width: 8, height: 64, capability: "energy" }
  );

  const fluid = V.createGuiElement("fluid_bar");
  assert.deepEqual(
    { x: fluid.x, y: fluid.y, width: fluid.width, height: fluid.height },
    { x: 148, y: 10, width: 8, height: 64 }
  );

  const progress = V.createGuiElement("progress_bar");
  assert.deepEqual(
    { x: progress.x, y: progress.y, width: progress.width, height: progress.height, direction: progress.direction },
    { x: 138, y: 10, width: 8, height: 64, direction: "up" }
  );

  const entity = V.createGuiElement("entity");
  assert.deepEqual(
    { x: entity.x, y: entity.y, width: entity.width, height: entity.height },
    { x: 96, y: 18, width: 32, height: 32 }
  );
});

test("gui importada preserva extras e faz round-trip", () => {
  const original = withGuiState((value) => {
    value.gui.extras.custom_gui = { note: "keep" };
    value.gui.elements[0].extras.custom_element = true;
  });
  const exported = V.buildDefinition(original);
  assert.deepEqual(exported.gui.custom_gui, { note: "keep" });
  assert.equal(exported.gui.elements[0].custom_element, true);
  const imported = V.importDefinition(exported);
  assert.deepEqual(imported.state.gui.extras.custom_gui, { note: "keep" });
  assert.equal(imported.state.gui.elements[0].extras.custom_element, true);
  assert.deepEqual(V.buildDefinition(imported.state), exported);
});

test("pattern precisa de exatamente uma âncora", () => {
  const state = withState((value) => {
    value.layers = [["CCC"]];
    value.matchers = {};
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("pattern.anchor.count"));
  assert.match(result.issues.find((entry) => entry.code === "pattern.anchor.count").message, /3/);
});

test("símbolo usado sem matcher é erro", () => {
  const state = withState((value) => {
    value.matchers = {};
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("pattern.symbol.unmatched"));
  assert.equal(result.errors.length, 1);
});

test("matcher sem uso é apenas aviso", () => {
  const state = withState((value) => {
    value.matchers.G = V.createMatcher("block");
    value.matchers.G.block = "minecraft:glass";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("matcher.unused"));
  assert.equal(result.errors.length, 0);
});

test("camadas e linhas irregulares são detectadas", () => {
  const state = withState((value) => {
    value.layers = [["III", "ICI"], ["III"]];
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("pattern.layer.rows.mismatch"));
});

test("matcher com tag válida usa o prefixo # na exportação", () => {
  const state = withState((value) => {
    value.matchers.I = {
      kind: "tag",
      block: "",
      tag: "c:storage_blocks/iron",
      state: [],
      raw: null
    };
  });
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.equal(V.buildDefinition(state).pattern.matchers.I, "#c:storage_blocks/iron");
});

test("matcher com block + state é exportado como objeto", () => {
  const state = withState((value) => {
    value.matchers.I = {
      kind: "state",
      block: "minecraft:oak_stairs",
      tag: "",
      state: [
        { key: "facing", value: "south" },
        { key: "half", value: "bottom" }
      ],
      raw: null
    };
  });
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.deepEqual(V.buildDefinition(state).pattern.matchers.I, {
    block: "minecraft:oak_stairs",
    state: { facing: "south", half: "bottom" }
  });
});

test("campo bloco aceita o JSON do /inm look", () => {
  const matcher = V.parseMatcherInput(
    '{"block":"infinity_nexus_mod:generator","state":{"facing":"east","lit":"7"}}'
  );
  assert.equal(matcher.kind, "state");
  assert.equal(matcher.block, "infinity_nexus_mod:generator");
  assert.deepEqual(matcher.state, [
    { key: "facing", value: "east" },
    { key: "lit", value: "7" }
  ]);
  assert.equal(V.matcherProblem(matcher), "");
  assert.equal(V.parseMatcherInput("minecraft:iron_block").kind, "block");
});

test("state vazio e state duplicado são inválidos", () => {
  const state = withState((value) => {
    value.matchers.I = {
      kind: "state",
      block: "minecraft:oak_stairs",
      tag: "",
      state: [
        { key: "facing", value: "" },
        { key: "facing", value: "north" }
      ],
      raw: null
    };
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("matcher.state.value.required"));
  assert.ok(codes(result).includes("matcher.state.duplicate"));
});

test("objeto com tag e state vindo do JSON é inválido", () => {
  const matcher = V.parseMatcherValue({ tag: "minecraft:glass", state: { a: "b" } });
  assert.equal(matcher.kind, "invalid");
  const state = withState((value) => {
    value.matchers.I = matcher;
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("matcher.object.invalid"));
});

test("importação preserva campos opcionais desconhecidos", () => {
  const raw = {
    schema_version: 1,
    id: "mymod:fornalha",
    type: "recipe",
    recipe_type: "infinity_nexus_multiblock:nexus_smelting",
    priority: 4,
    tier: 2,
    inventory_rows: 5,
    controller: {
      block: "mymod:fornalha",
      textures: { front: "mymod:block/fornalha/front" },
      custom_controller: { note: "keep" }
    },
    item_slots: [{ id: "input_0", role: "input" }],
    gui: { width: 176, height: 166, elements: [] },
    pattern: {
      anchor: "C",
      rotatable: true,
      layers: [["III", "ICI", "III"]],
      matchers: {
        I: {
          block: "minecraft:iron_block",
          state: { facing: "south" }
        }
      }
    }
  };
  const imported = V.importDefinition(raw);
  assert.equal(imported.state.namespace, "mymod");
  assert.equal(imported.state.path, "fornalha");
  assert.equal(imported.state.priority, 4);
  assert.equal(imported.state.tier, 2);
  assert.equal(imported.state.inventoryRows, 5);
  assert.equal(imported.state.extras.controller, undefined);
  assert.equal(imported.state.controller.block, "mymod:fornalha");
  assert.equal(imported.state.controller.textures.front, "mymod:block/fornalha/front");
  assert.deepEqual(imported.state.controller.extras.custom_controller, { note: "keep" });
  assert.equal(imported.state.extras.item_slots, undefined);
  assert.equal(imported.state.itemSlots.length, 1);
  assert.equal(imported.state.itemSlots[0].id, "input_0");
  assert.equal(imported.state.itemSlots[0].role, "input");
  assert.equal(imported.state.patternExtras.rotatable, true);
  assert.equal(imported.state.matchers.I.kind, "state");

  const json = V.buildDefinition(imported.state);
  assert.deepEqual(json.controller, raw.controller);
  assert.deepEqual(json.item_slots, raw.item_slots);
  assert.equal(json.priority, 4);
  assert.equal(json.tier, 2);
  assert.equal(json.inventory_rows, 5);
  assert.equal(json.pattern.rotatable, true);
  assert.deepEqual(json.pattern.matchers.I, {
    block: "minecraft:iron_block",
    state: { facing: "south" }
  });
});

test("importação normaliza camadas irregulares e registra nota", () => {
  const imported = V.importDefinition({
    schema_version: 1,
    id: "mymod:teste",
    type: "storage",
    pattern: {
      anchor: "C",
      layers: [["II", "ICI"], ["I"]],
      matchers: { I: "minecraft:iron_block" }
    }
  });
  const layers = imported.state.layers;
  assert.equal(layers.length, 2);
  assert.equal(layers[0].length, 2);
  assert.equal(layers[1].length, 2);
  layers.forEach((layer) => {
    layer.forEach((row) => assert.equal(row.length, 3));
  });
  assert.ok(imported.notes.some((note) => /normalizado/.test(note.message)));
});

test("importação de JSON sem id mantém o erro visível", () => {
  const imported = V.importDefinition({
    schema_version: 1,
    type: "storage",
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: {}
    }
  });
  const result = V.validateState(imported.state);
  assert.ok(codes(result).includes("id.namespace.required"));
  assert.ok(codes(result).includes("id.path.required"));
});

test("extras com template geram aviso", () => {
  const state = withState((value) => {
    value.extras.template = true;
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("extras.template"));
});

test("checks refletem os erros do esqueleto", () => {
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "";
    value.layers = [["CCC"]];
    value.matchers = {};
  });
  const result = V.validateState(state);
  const byId = Object.fromEntries(result.checks.map((check) => [check.id, check.ok]));
  assert.equal(byId.anchor, false);
  assert.equal(byId.recipe, false);
  assert.equal(byId.geometry, true);
});

test("item_slots é opcional e só aparece quando definido", () => {
  const state = V.createDefaultState();
  assert.equal(state.itemSlots, null);
  assert.equal(V.buildDefinition(state).item_slots, undefined);

  state.itemSlots = [V.createItemSlot()];
  state.itemSlots[0].id = "input_0";
  state.itemSlots[0].role = "input";
  state.itemSlots[0].filter = "minecraft:iron_ore";
  state.itemSlots[0].limit = 16;
  state.itemSlots[0].x = 8;
  state.itemSlots[0].y = 17;

  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.deepEqual(V.buildDefinition(state).item_slots, [{
    id: "input_0",
    role: "input",
    filter: "minecraft:iron_ore",
    limit: 16,
    x: 8,
    y: 17
  }]);
});

test("filtro é ignorado em slots output", () => {
  const slot = {
    id: "output_0",
    role: "output",
    filter: "sem dois pontos",
    limit: 64,
    x: 86,
    y: 26,
    faces: null,
    extras: {}
  };
  assert.equal(V.slotProblem(slot), "");
  assert.deepEqual(V.buildItemSlot(slot), {
    id: "output_0",
    role: "output",
    x: 86,
    y: 26
  });
});

test("item_slots inválidos geram erros específicos", () => {
  const state = withState((value) => {
    value.itemSlots = [{
      id: "",
      role: "invalido",
      filter: "sem dois pontos",
      limit: 0,
      x: 1,
      y: null,
      faces: null,
      extras: {}
    }];
  });
  const result = V.validateState(state);
  const found = codes(result);
  assert.ok(found.includes("slot.id.required"));
  assert.ok(found.includes("slot.role.invalid"));
  assert.ok(found.includes("slot.filter.invalid"));
  assert.ok(found.includes("slot.limit.invalid"));
  assert.ok(found.includes("slot.position.pair"));
  assert.equal(result.checks.find((check) => check.id === "slots").ok, false);
});

test("item_slots rejeita ids duplicados", () => {
  const state = withState((value) => {
    value.itemSlots = [
      { ...V.createItemSlot(), id: "input_0" },
      { ...V.createItemSlot(), id: "input_0" }
    ];
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("slot.id.duplicate"));
});

test("item_slots importado preserva campos extras e lista vazia", () => {
  const imported = V.importDefinition({
    schema_version: 1,
    id: "mymod:teste",
    type: "storage",
    item_slots: [{
      id: "output_0",
      role: "output",
      x: 86,
      y: 26,
      custom: true
    }],
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: {}
    }
  });
  assert.equal(imported.state.itemSlots[0].extras.custom, true);
  assert.deepEqual(V.buildDefinition(imported.state).item_slots, [{
    id: "output_0",
    role: "output",
    x: 86,
    y: 26,
    custom: true
  }]);

  const empty = V.importDefinition({
    schema_version: 1,
    id: "mymod:vazio",
    type: "storage",
    item_slots: [],
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: {}
    }
  });
  assert.deepEqual(empty.state.itemSlots, []);
  assert.deepEqual(V.buildDefinition(empty.state).item_slots, []);
});

test("capabilities é opcional e exporta energia e tanques", () => {
  const state = V.createDefaultState();
  assert.equal(state.capabilities, null);
  assert.equal(V.buildDefinition(state).capabilities, undefined);

  state.capabilities = V.createCapabilities();
  state.capabilities.energy = V.createEnergy();
  state.capabilities.energy.id = "energy";
  state.capabilities.energy.capacity = 50000;
  state.capabilities.energy.maxReceive = 2500;
  state.capabilities.energy.maxExtract = 0;
  state.capabilities.fluids = [V.createFluidTank(), V.createFluidTank()];
  state.capabilities.fluids[0].id = "water_tank";
  state.capabilities.fluids[0].filter = "minecraft:water";
  state.capabilities.fluids[1].id = "lava_tank";
  state.capabilities.fluids[1].capacity = 8000;
  state.capabilities.fluids[1].maxReceive = 500;
  state.capabilities.fluids[1].filter = "minecraft:lava";

  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.deepEqual(V.buildDefinition(state).capabilities, {
    energy: {
      id: "energy",
      capacity: 50000,
      max_receive: 2500
    },
    fluids: [
      { id: "water_tank", capacity: 16000, max_receive: 1000, filter: "minecraft:water" },
      { id: "lava_tank", capacity: 8000, max_receive: 500, filter: "minecraft:lava" }
    ]
  });
});

test("capabilities inválidas geram erros específicos", () => {
  const state = withState((value) => {
    value.capabilities = {
      energy: {
        id: "",
        capacity: 0,
        maxReceive: -1,
        maxExtract: -2,
        extras: {}
      },
      fluids: [
        {
          id: "",
          capacity: 0,
          maxReceive: -1,
          maxExtract: -2,
          filter: "sem dois pontos",
          extras: {}
        },
        { id: "water_tank", capacity: 1, maxReceive: 0, maxExtract: 0, filter: "", extras: {} },
        { id: "water_tank", capacity: 1, maxReceive: 0, maxExtract: 0, filter: "", extras: {} }
      ],
      extras: {}
    };
  });
  const result = V.validateState(state);
  const found = codes(result);
  assert.ok(found.includes("energy.id.required"));
  assert.ok(found.includes("energy.capacity.invalid"));
  assert.ok(found.includes("energy.max_receive.invalid"));
  assert.ok(found.includes("energy.max_extract.invalid"));
  assert.ok(found.includes("fluid.id.required"));
  assert.ok(found.includes("fluid.capacity.invalid"));
  assert.ok(found.includes("fluid.max_receive.invalid"));
  assert.ok(found.includes("fluid.max_extract.invalid"));
  assert.ok(found.includes("fluid.filter.invalid"));
  assert.ok(found.includes("fluid.id.duplicate"));
  assert.equal(result.checks.find((check) => check.id === "capabilities").ok, false);
});

test("energia não pode reutilizar o id de um tanque", () => {
  const state = withState((value) => {
    value.capabilities = {
      energy: { id: "shared", capacity: 1000, maxReceive: 10, maxExtract: 0, extras: {} },
      fluids: [{ id: "shared", capacity: 1000, maxReceive: 10, maxExtract: 0, filter: "", extras: {} }],
      extras: {}
    };
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("energy.id.conflict"));
  assert.equal(result.checks.find((check) => check.id === "capabilities").ok, false);
});

test("capabilities importadas preservam extras e listas vazias", () => {
  const imported = V.importDefinition({
    schema_version: 1,
    id: "mymod:teste",
    type: "storage",
    capabilities: {
      energy: {
        id: "energy",
        capacity: 1000,
        max_receive: 10,
        custom_energy: true
      },
      fluids: [{
        id: "water_tank",
        capacity: 2000,
        max_receive: 20,
        filter: "#c:water",
        custom_tank: true
      }],
      future_capability: { enabled: true }
    },
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: {}
    }
  });
  assert.equal(imported.state.capabilities.energy.extras.custom_energy, true);
  assert.equal(imported.state.capabilities.fluids[0].extras.custom_tank, true);
  assert.equal(imported.state.capabilities.extras.future_capability.enabled, true);
  assert.deepEqual(V.buildDefinition(imported.state).capabilities, {
    energy: {
      id: "energy",
      capacity: 1000,
      max_receive: 10,
      custom_energy: true
    },
    fluids: [{
      id: "water_tank",
      capacity: 2000,
      max_receive: 20,
      filter: "#c:water",
      custom_tank: true
    }],
    future_capability: { enabled: true }
  });

  const empty = V.importDefinition({
    schema_version: 1,
    id: "mymod:vazio",
    type: "storage",
    capabilities: { fluids: [] },
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: {}
    }
  });
  assert.deepEqual(V.buildDefinition(empty.state).capabilities, { fluids: [] });
});

test("capability_ports exporta filtros e valida simbolos", () => {
  const state = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.item = ["I"];
    value.capabilityPorts.energy = ["C"];
    value.capabilityPorts.fluid = ["#minecraft:glass", "minecraft:iron_block"];
  });
  assert.deepEqual(V.validateState(state).errors, []);
  assert.deepEqual(V.buildDefinition(state).capability_ports, {
    item: ["I"],
    energy: ["C"],
    fluid: ["#minecraft:glass", "minecraft:iron_block"]
  });

  const imported = V.importDefinition(V.buildDefinition(state));
  assert.deepEqual(imported.state.capabilityPorts.item, ["I"]);
  assert.deepEqual(imported.state.capabilityPorts.energy, ["C"]);
  assert.deepEqual(imported.state.capabilityPorts.fluid, ["#minecraft:glass", "minecraft:iron_block"]);

  const invalid = withState((value) => {
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.item = ["Z"];
    value.capabilityPorts.energy = "C";
  });
  const found = codes(V.validateState(invalid));
  assert.ok(found.includes("capability_ports.selector.invalid"));
  assert.ok(found.includes("capability_ports.list.invalid"));
});

test("capability_ports aceita lista vazia e texto do builder", () => {
  const state = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.energy = V.parseCapabilityPortText("[]");
    value.capabilityPorts.fluid = V.parseCapabilityPortText("C, #minecraft:glass");
  });
  assert.deepEqual(state.capabilityPorts.energy, []);
  assert.deepEqual(state.capabilityPorts.fluid, ["C", "#minecraft:glass"]);
  assert.equal(V.capabilityPortText(state.capabilityPorts.energy), "[]");
  assert.equal(V.capabilityPortText(state.capabilityPorts.fluid), "C, #minecraft:glass");
  assert.deepEqual(V.buildDefinition(state).capability_ports, {
    energy: [],
    fluid: ["C", "#minecraft:glass"]
  });
});

test("capability_ports omite capabilities nao implementadas", () => {
  const storage = withState((value) => {
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.item = ["I"];
    value.capabilityPorts.energy = ["C"];
    value.capabilityPorts.fluid = ["minecraft:glass"];
  });
  assert.deepEqual(V.buildDefinition(storage).capability_ports, {
    item: ["I"]
  });

  const withEnergy = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.item = ["I"];
    value.capabilityPorts.energy = ["C"];
    value.capabilityPorts.fluid = ["minecraft:glass"];
  });
  assert.deepEqual(V.buildDefinition(withEnergy).capability_ports, {
    item: ["I"],
    energy: ["C"]
  });
  assert.ok(codes(V.validateState(withEnergy)).includes("capability_ports.capability.missing"));
});

test("JSON exportado pode ser importado de volta sem perdas do escopo", () => {
  const original = V.createExampleState();
  original.controller = V.parseController({
    block: "infinity_nexus_multiblock:alloy_smelter",
    textures: controllerTextures("infinity_nexus_multiblock:block/alloy_smelter"),
    formed_textures: { front: "infinity_nexus_multiblock:block/alloy_smelter/formed_front" },
    particle: "minecraft:smoke",
    light_level: 5,
    custom_controller: { note: "keep" }
  }, []);
  original.capabilities = V.createCapabilities();
  original.capabilities.energy = V.createEnergy();
  original.capabilities.energy.id = "energy";
  original.capabilities.fluids = [V.createFluidTank()];
  original.capabilities.fluids[0].id = "water_tank";
  original.capabilities.fluids[0].filter = "minecraft:water";
  const exported = V.buildDefinition(original);
  const imported = V.importDefinition(exported);
  assert.deepEqual(V.buildDefinition(imported.state), exported);
});

test("item_slots.faces e opcional e faz round-trip", () => {
  const state = withState((value) => {
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "input";
    value.itemSlots[0].faces = { insert: ["north", "up"], extract: ["south"] };
  });
  assert.deepEqual(V.buildDefinition(state).item_slots[0].faces, {
    insert: ["north", "up"],
    extract: ["south"]
  });
  const imported = V.importDefinition(V.buildDefinition(state));
  assert.deepEqual(imported.state.itemSlots[0].faces, {
    insert: ["north", "up"],
    extract: ["south"]
  });
});

test("item_slots.faces invalido bloqueia a exportacao", () => {
  const state = withState((value) => {
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "input";
    value.itemSlots[0].faces = { insert: ["norte"], extract: null };
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("slot.faces.invalid"));
});

test("heranca opcional exporta extends, template e overrides", () => {
  const state = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.mode = "extends";
    value.inheritance.parent = "mymod:template_base";
    value.inheritance.template = true;
    value.inheritance.overrides = { tier: 2, capabilities: { energy: { capacity: 5000 } } };
  });
  const definition = V.buildDefinition(state);
  assert.equal(definition.extends, "mymod:template_base");
  assert.equal(definition.family, undefined);
  assert.equal(definition.template, true);
  assert.deepEqual(definition.overrides, { tier: 2, capabilities: { energy: { capacity: 5000 } } });
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.ok(codes(result).includes("inheritance.template"));
});

test("heranca valida pai, modo, template e overrides reservados", () => {
  const missing = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.mode = "family";
  });
  assert.ok(codes(V.validateState(missing)).includes("inheritance.parent.required"));

  const self = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.mode = "extends";
    value.inheritance.parent = "infinity_nexus_multiblock:" + value.path;
  });
  assert.ok(codes(V.validateState(self)).includes("inheritance.parent.self"));

  const badTemplate = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.template = "sim";
  });
  assert.ok(codes(V.validateState(badTemplate)).includes("inheritance.template.invalid"));

  const reserved = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.overrides = { template: true };
  });
  assert.ok(codes(V.validateState(reserved)).includes("inheritance.overrides.reserved"));

  const invalidOverrides = withState((value) => {
    value.inheritance = V.createInheritance();
    value.inheritance.overridesError = "JSON invalido";
  });
  assert.ok(codes(V.validateState(invalidOverrides)).includes("inheritance.overrides.error"));
});

test("importacao de heranca tira os campos de extras e avisa extends+family", () => {
  const raw = V.buildDefinition(V.createDefaultState());
  raw.extends = "mymod:template_base";
  raw.family = "mymod:other_base";
  const imported = V.importDefinition(raw);
  assert.equal(imported.state.inheritance.mode, "extends");
  assert.equal(imported.state.inheritance.parent, "mymod:template_base");
  assert.equal(imported.state.extras.extends, undefined);
  assert.equal(imported.state.extras.family, undefined);
  assert.ok(imported.notes.some((note) => note.message.includes("extends e family vieram juntos")));
});

test("receita valida exporta ingredientes, fluidos e resultado", () => {
  const recipe = V.createRecipe();
  recipe.file = "gold_ingot_from_ore";
  recipe.type = "infinity_nexus_multiblock:nexus_smelting";
  recipe.ingredientsRaw = "minecraft:iron_ore\n#minecraft:coals";
  recipe.fluidsRaw = "minecraft:water 50";
  recipe.resultId = "minecraft:gold_ingot";
  recipe.resultCount = 2;
  recipe.componentsRaw = '{"minecraft:custom_name":"ouro"}';
  recipe.energy = 300;
  recipe.time = 120;
  const built = V.buildRecipe(recipe);
  assert.deepEqual(built, {
    type: "infinity_nexus_multiblock:nexus_smelting",
    ingredients: ["minecraft:iron_ore", "#minecraft:coals"],
    fluid_ingredients: [{ ingredient: "minecraft:water", amount: 50 }],
    output: {
      type: "item",
      id: "minecraft:gold_ingot",
      count: 2,
      components: { "minecraft:custom_name": "ouro" }
    },
    energy: 300,
    time: 120
  });
  const result = V.validateState(withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[1].id = "output_0";
    value.itemSlots[1].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.recipes = [recipe];
  }));
  assert.deepEqual(result.errors, []);
});

test("receitas invalidas e duplicadas bloqueiam", () => {
  const first = V.createRecipe();
  first.file = "same";
  first.type = "infinity_nexus_multiblock:nexus_smelting";
  first.ingredientsRaw = "id-invalido";
  first.resultId = "minecraft:stone";
  first.resultCount = 0;
  first.energy = -1;
  first.time = 0;
  const second = V.createRecipe();
  second.file = "same.json";
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "output_0";
    value.itemSlots[0].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.recipes = [first, second];
  });
  const found = codes(V.validateState(state));
  assert.ok(found.includes("recipe.ingredient.invalid"));
  assert.ok(found.includes("recipe.file.duplicate"));
  assert.ok(found.includes("recipe.output.count"));
  assert.ok(found.includes("recipe.energy.invalid"));
  assert.ok(found.includes("recipe.time.invalid"));
  assert.ok(found.includes("recipe.input.required"));
});

test("receita faz round-trip pelo parser e aceita somente fluidos", () => {
  const raw = {
    type: "infinity_nexus_multiblock:nexus_smelting",
    fluid_ingredients: [{ ingredient: "minecraft:water", amount: 100 }],
    output: { type: "item", id: "minecraft:ice", count: 1 },
    energy: 10,
    time: 20,
    custom: true
  };
  const notes = [];
  const parsed = V.parseRecipe(raw, notes);
  parsed.file = "water_to_ice";
  assert.deepEqual(V.buildRecipe(parsed), raw);
  assert.deepEqual(notes, []);
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "output_0";
    value.itemSlots[0].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.recipes = [parsed];
  });
  assert.deepEqual(V.validateState(state).errors, []);
});

test("saida fluid omite energia e exige tanque", () => {
  const recipe = V.createRecipe();
  recipe.file = "water_out";
  recipe.type = "infinity_nexus_multiblock:nexus_smelting";
  recipe.outputType = "fluid";
  recipe.ingredientsRaw = "minecraft:stone";
  recipe.resultId = "minecraft:water";
  recipe.resultAmount = 500;
  recipe.energy = 50;
  const built = V.buildRecipe(recipe, {
    energy: false,
    fluid: true,
    itemOutput: false,
    symbols: ["C", "I"]
  });
  assert.deepEqual(built.output, { type: "fluid", id: "minecraft:water", amount: 500 });
  assert.equal(built.energy, undefined);
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.capabilities = V.createCapabilities();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.recipes = [recipe];
  });
  assert.deepEqual(V.validateState(state).errors, []);
});

test("saida entity usa seletor de bloco e valida o pattern", () => {
  const recipe = V.createRecipe();
  recipe.file = "pig_out";
  recipe.type = "infinity_nexus_multiblock:nexus_smelting";
  recipe.outputType = "entity";
  recipe.ingredientsRaw = "minecraft:carrot";
  recipe.resultId = "minecraft:pig";
  recipe.resultSpawn = "C";
  const built = V.buildRecipe(recipe, { energy: false, fluid: false, itemOutput: false, symbols: ["C", "I"] });
  assert.deepEqual(built.output, { type: "entity", id: "minecraft:pig", spawn: "C" });
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.recipes = [recipe];
  });
  assert.deepEqual(V.validateState(state).errors, []);
  recipe.resultSpawn = "Z";
  assert.ok(codes(V.validateState(state)).includes("recipe.output.spawn"));
});

test("saida item sem slot de saida bloqueia e buildRecipe troca para tipo permitido", () => {
  const recipe = V.createRecipe();
  recipe.file = "no_slot";
  recipe.type = "infinity_nexus_multiblock:nexus_smelting";
  recipe.ingredientsRaw = "minecraft:stone";
  recipe.resultId = "minecraft:stone";
  const built = V.buildRecipe(recipe, { energy: false, fluid: true, itemOutput: false, symbols: ["C"] });
  assert.equal(built.output.type, "fluid");
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:nexus_smelting";
    value.recipes = [recipe];
  });
  assert.ok(codes(V.validateState(state)).includes("recipe.output.item_slot"));
});

test("receita de maquina type=recipe exige type igual e avisa diferenca", () => {
  const recipe = V.createRecipe();
  recipe.file = "test";
  recipe.type = "infinity_nexus_multiblock:nexus_smelting";
  recipe.ingredientsRaw = "minecraft:stone";
  recipe.resultId = "minecraft:stone";
  const state = withState((value) => {
    value.type = "recipe";
    value.recipeType = "infinity_nexus_multiblock:13x13";
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "output_0";
    value.itemSlots[0].role = "output";
    value.recipes = [recipe];
  });
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.ok(codes(result).includes("recipe.type.mismatch"));
});

test("receita do controller exporta shaped no root e faz round-trip", () => {
  const state = withState((value) => {
    value.controllerRecipe = V.parseControllerRecipe({
      pattern: ["III", "ICI", "III"],
      key: {
        I: "minecraft:iron_ingot",
        C: "#c:ingots/copper"
      }
    }, []);
  });
  const built = V.buildDefinition(state);
  assert.deepEqual(built.recipe, {
    pattern: ["III", "ICI", "III"],
    key: {
      I: "minecraft:iron_ingot",
      C: "#c:ingots/copper"
    }
  });
  assert.deepEqual(V.validateState(state).errors, []);
  const imported = V.importDefinition(built);
  assert.deepEqual(V.buildDefinition(imported.state).recipe, built.recipe);
});

test("receita do controller valida pattern 3x3, simbolos e ingredientes", () => {
  const state = withState((value) => {
    value.controllerRecipe = V.parseControllerRecipe({
      pattern: ["II", "ICI", "III"],
      key: {
        I: "minecraft:iron_ingot",
        C: "invalid ingredient",
        Z: "minecraft:stone"
      }
    }, []);
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("controller_recipe.pattern.uniform"));
  assert.ok(codes(result).includes("controller_recipe.key.item_invalid"));
  assert.ok(codes(result).includes("controller_recipe.key.unused"));
  assert.equal(result.checks.find((check) => check.id === "controllerRecipe").ok, false);
});

test("receita do controller aceita slots vazios no pattern 3x3", () => {
  const state = withState((value) => {
    value.controllerRecipe = V.parseControllerRecipe({
      pattern: ["I I", " C ", "   "],
      key: {
        I: "minecraft:iron_ingot",
        C: "minecraft:copper_ingot"
      }
    }, []);
  });
  assert.deepEqual(V.validateState(state).errors, []);
  assert.deepEqual(V.buildDefinition(state).recipe.pattern, ["I I", " C ", "   "]);
});

test("receita do controller exige key para todos os simbolos do pattern", () => {
  const state = withState((value) => {
    value.controllerRecipe = V.parseControllerRecipe({
      pattern: ["ICI"],
      key: {
        I: "minecraft:iron_ingot"
      }
    }, []);
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("controller_recipe.key.missing"));
  assert.equal(V.controllerRecipeProblem(state.controllerRecipe).length > 0, true);
});

if (process.exitCode) {
  console.error("Falhas encontradas.");
} else {
  console.log(passed + " testes passaram.");
}
