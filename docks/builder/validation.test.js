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
      { type: "entity", x: 120, y: 17, width: 36, height: 36 }
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
  assert.equal(json.schema_version, 2);
  assert.equal(json.recipe_type, undefined);
  assert.equal(json.controller, undefined);
  assert.equal(json.id, "infinity_nexus_multiblock:minha_maquina");
  assert.equal(json.type, "storage");
  assert.equal(json.tier, undefined);
  assert.equal(json.priority, undefined);
  assert.equal(json.inventory_rows, undefined);
  assert.deepEqual(json.pattern.layers, [["III", "ICI", "III"]]);
  assert.equal(json.pattern.anchor, "C");
  assert.equal(json.pattern.matchers.I, "minecraft:iron_block");
});

test("namespace externo usa o controller único sem erro", () => {
  const state = withState((value) => {
    value.namespace = "mymod";
    value.path = "fornalha";
    value.type = "recipe";
  });
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.equal(result.checks.find((check) => check.id === "identity").ok, true);
  assert.equal(result.checks.find((check) => check.id === "controller").ok, true);
  assert.equal(V.buildDefinition(state).controller, undefined);
  assert.equal(V.buildDefinition(state).recipe_type, undefined);
});

test("recipe_type e controller antigos são ignorados na importação", () => {
  const imported = V.importDefinition({
    schema_version: 2,
    id: "infinity_nexus_multiblock:legado",
    type: "recipe",
    recipe_type: "infinity_nexus_multiblock:multiblock",
    controller: {
      block: "mymod:fornalha",
      textures: { front: "mymod:block/fornalha/front" }
    },
    pattern: {
      anchor: "C",
      layers: [["ICI"]],
      matchers: { I: "minecraft:iron_block" }
    }
  });
  const json = V.buildDefinition(imported.state);
  assert.equal(json.schema_version, 2);
  assert.equal(json.recipe_type, undefined);
  assert.equal(json.controller, undefined);
  assert.ok(imported.notes.some((note) => note.message.includes("recipe_type")));
  assert.ok(imported.notes.some((note) => note.message.includes("controller dinâmico")));
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
  assert.deepEqual(entity, { type: "entity", x: 120, y: 17, width: 36, height: 36 });
  assert.equal(V.createGuiElement("entity").width, 54);
  assert.equal(V.createGuiElement("entity").height, 54);

  const storage = withGuiState((value) => {
    value.type = "storage";
    value.recipeType = "";
  });
  assert.ok(codes(V.validateState(storage)).includes("gui.recipe.required"));

  const noSize = withGuiState((value) => {
    value.gui.elements.find((element) => element.type === "entity").width = 0;
  });
  assert.ok(codes(V.validateState(noSize)).includes("gui.element.size.invalid"));

  const notMultiple = withGuiState((value) => {
    value.gui.elements.find((element) => element.type === "entity").width = 32;
  });
  assert.ok(codes(V.validateState(notMultiple)).includes("gui.element.size.invalid"));
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
    { x: 158, y: 16, width: 8, height: 54, capability: "energy" }
  );

  const fluid = V.createGuiElement("fluid_bar");
  assert.deepEqual(
    { x: fluid.x, y: fluid.y, width: fluid.width, height: fluid.height },
    { x: 148, y: 16, width: 8, height: 54 }
  );

  const progress = V.createGuiElement("progress_bar");
  assert.deepEqual(
    { x: progress.x, y: progress.y, width: progress.width, height: progress.height, direction: progress.direction },
    { x: 138, y: 16, width: 8, height: 54, direction: "up" }
  );

  const entity = V.createGuiElement("entity");
  assert.deepEqual(
    { x: entity.x, y: entity.y, width: entity.width, height: entity.height },
    { x: 82, y: 16, width: 54, height: 54 }
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

test("campo bloco aceita o JSON do /inmb look", () => {
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
    schema_version: 2,
    id: "mymod:fornalha",
    type: "recipe",
    priority: 4,
    tier: 2,
    inventory_rows: 5,
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
  assert.equal(imported.state.extras.item_slots, undefined);
  assert.equal(imported.state.itemSlots.length, 1);
  assert.equal(imported.state.itemSlots[0].id, "input_0");
  assert.equal(imported.state.itemSlots[0].role, "input");
  assert.equal(imported.state.patternExtras.rotatable, true);
  assert.equal(imported.state.matchers.I.kind, "state");

  const json = V.buildDefinition(imported.state);
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
    schema_version: 2,
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
    schema_version: 2,
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
    value.layers = [["CCC"]];
    value.matchers = {};
  });
  const result = V.validateState(state);
  const byId = Object.fromEntries(result.checks.map((check) => [check.id, check.ok]));
  assert.equal(byId.anchor, false);
  assert.equal(byId.controller, true);
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
    schema_version: 2,
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
    schema_version: 2,
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
    schema_version: 2,
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
    schema_version: 2,
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

test("receita valida exporta inputs e outputs variados", () => {
  const recipe = V.createRecipe();
  recipe.file = "gold_ingot_from_ore";
  recipe.time = 120;
  const itemInput = V.createRecipeInputRow("item");
  itemInput.resource = "minecraft:iron_ore";
  itemInput.count = "2";
  itemInput.componentsRaw = '{"minecraft:custom_name":"minerio"}';
  const tagInput = V.createRecipeInputRow("item");
  tagInput.resource = "#minecraft:coals";
  const fluidInput = V.createRecipeInputRow("fluid");
  fluidInput.resource = "minecraft:water";
  fluidInput.amount = "50";
  const energyInput = V.createRecipeInputRow("energy");
  energyInput.amount = "300";
  recipe.inputRows = [itemInput, tagInput, fluidInput, energyInput];
  const itemOutput = V.createRecipeOutputRow("item");
  itemOutput.resource = "minecraft:gold_ingot";
  itemOutput.count = "2";
  itemOutput.componentsRaw = '{"minecraft:custom_name":"ouro"}';
  itemOutput.chance = "0.5";
  const fluidOutput = V.createRecipeOutputRow("fluid");
  fluidOutput.resource = "minecraft:water";
  fluidOutput.amount = "25";
  fluidOutput.chance = "0.25";
  const entityOutput = V.createRecipeOutputRow("entity");
  entityOutput.resource = "minecraft:pig";
  entityOutput.spawn = "minecraft:iron_block";
  const energyOutput = V.createRecipeOutputRow("energy");
  energyOutput.amount = "500";
  energyOutput.chance = "0";
  recipe.outputRows = [itemOutput, fluidOutput, entityOutput, energyOutput];
  const built = V.buildRecipe(recipe, { definitionId: "infinity_nexus_multiblock:minha_maquina" });
  assert.deepEqual(built, {
    type: "infinity_nexus_multiblock:multiblock",
    definition: "infinity_nexus_multiblock:minha_maquina",
    inputs: [
      {
        type: "item",
        ingredient: {
          "neoforge:ingredient_type": "neoforge:components",
          items: "minecraft:iron_ore",
          components: { "minecraft:custom_name": "minerio" }
        },
        count: 2
      },
      { type: "item", ingredient: "#minecraft:coals" },
      { type: "fluid", ingredient: "minecraft:water", amount: 50 },
      { type: "energy", amount: 300 }
    ],
    outputs: [
      {
        type: "item",
        id: "minecraft:gold_ingot",
        count: 2,
        components: { "minecraft:custom_name": "ouro" },
        chance: 0.5
      },
      { type: "fluid", id: "minecraft:water", amount: 25, chance: 0.25 },
      { type: "entity", id: "minecraft:pig", spawn: "minecraft:iron_block" },
      { type: "energy", amount: 500, chance: 0 }
    ],
    time: 120
  });
  const result = V.validateState(withState((value) => {
    value.type = "recipe";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[1].id = "input_1";
    value.itemSlots[2].id = "output_0";
    value.itemSlots[2].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.fluids = [V.createFluidTank(), V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilities.fluids[1].id = "output_tank";
    value.recipes = [recipe];
  }));
  assert.deepEqual(result.errors, []);
});

test("receita com mais fluidos de entrada e saida que tanques bloqueia", () => {
  const recipe = V.createRecipe();
  recipe.file = "recipe_1";
  const itemInput = V.createRecipeInputRow("item");
  itemInput.resource = "minecraft:dirt";
  itemInput.count = "12";
  const fluidInput = V.createRecipeInputRow("fluid");
  fluidInput.resource = "minecraft:water";
  fluidInput.amount = "20";
  const entityInput = V.createRecipeInputRow("entity");
  entityInput.resource = "minecraft:pig";
  const energyInput = V.createRecipeInputRow("energy");
  energyInput.amount = "350";
  recipe.inputRows = [itemInput, fluidInput, entityInput, energyInput];
  const itemOutput = V.createRecipeOutputRow("item");
  itemOutput.resource = "minecraft:sand";
  itemOutput.count = "6";
  const fluidOutput = V.createRecipeOutputRow("fluid");
  fluidOutput.resource = "minecraft:lava";
  fluidOutput.amount = "125";
  const entityOutput = V.createRecipeOutputRow("entity");
  entityOutput.resource = "minecraft:cow";
  entityOutput.spawn = "C";
  const energyOutput = V.createRecipeOutputRow("energy");
  energyOutput.amount = "50";
  recipe.outputRows = [itemOutput, fluidOutput, entityOutput, energyOutput];
  const result = V.validateRecipes([recipe], {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:fabrica_de_voxel_cube",
    itemInput: true,
    itemOutput: true,
    fluid: true,
    energy: true,
    symbols: ["C"],
    inputSlots: [64],
    outputSlots: [64],
    fluidCapacities: [16000],
    energyCapacity: 100000
  });
  assert.deepEqual(codes(result), ["recipe.fluid_slots"]);
  assert.equal(result.ok, false);
});

test("receitas invalidas e duplicadas bloqueiam", () => {
  const first = V.createRecipe();
  first.file = "same";
  first.time = 0;
  const invalidInput = V.createRecipeInputRow("item");
  invalidInput.resource = "id-invalido";
  const invalidEnergy = V.createRecipeInputRow("energy");
  invalidEnergy.amount = "-1";
  first.inputRows = [invalidInput, invalidEnergy];
  const invalidOutput = V.createRecipeOutputRow("item");
  invalidOutput.resource = "minecraft:stone";
  invalidOutput.count = "0";
  invalidOutput.chance = "2";
  first.outputRows = [invalidOutput];
  const second = V.createRecipe();
  second.file = "same.json";
  const state = withState((value) => {
    value.type = "recipe";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[1].id = "output_0";
    value.itemSlots[1].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.recipes = [first, second];
  });
  const found = codes(V.validateState(state));
  assert.ok(found.includes("recipe.input.item_invalid"));
  assert.ok(found.includes("recipe.file.duplicate"));
  assert.ok(found.includes("recipe.output.count"));
  assert.ok(found.includes("recipe.output.chance"));
  assert.ok(found.includes("recipe.input.energy_invalid"));
  assert.ok(found.includes("recipe.time.invalid"));
  assert.ok(found.includes("recipe.input.required"));
});

test("receita faz round-trip do schema antigo para inputs e outputs", () => {
  const raw = {
    type: "infinity_nexus_multiblock:multiblock",
    definition: "infinity_nexus_multiblock:alloy_smelter",
    ingredients: ["minecraft:iron_ore", "#minecraft:coals"],
    fluid_ingredients: [{ ingredient: "minecraft:water", amount: 100 }],
    output: { type: "item", id: "minecraft:ice", count: 1 },
    energy: 10,
    time: 20,
    custom: true
  };
  const notes = [];
  const parsed = V.parseRecipe(raw, notes);
  parsed.file = "water_to_ice";
  assert.deepEqual(V.buildRecipe(parsed), {
    type: "infinity_nexus_multiblock:multiblock",
    definition: "infinity_nexus_multiblock:alloy_smelter",
    inputs: [
      { type: "item", ingredient: "minecraft:iron_ore" },
      { type: "item", ingredient: "#minecraft:coals" },
      { type: "fluid", ingredient: "minecraft:water", amount: 100 },
      { type: "energy", amount: 10 }
    ],
    outputs: [{ type: "item", id: "minecraft:ice", count: 1 }],
    time: 20,
    custom: true
  });
  assert.deepEqual(notes, []);
  const state = withState((value) => {
    value.type = "recipe";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[1].id = "input_1";
    value.itemSlots[2].id = "output_0";
    value.itemSlots[2].role = "output";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.recipes = [parsed];
  });
  assert.deepEqual(V.validateState(state).errors, []);
});

test("saida fluid aceita energia no input e exige tanque", () => {
  const recipe = V.createRecipe();
  recipe.file = "water_out";
  const itemInput = V.createRecipeInputRow("item");
  itemInput.resource = "minecraft:stone";
  const energyInput = V.createRecipeInputRow("energy");
  energyInput.amount = "50";
  recipe.inputRows = [itemInput, energyInput];
  const fluidOutput = V.createRecipeOutputRow("fluid");
  fluidOutput.resource = "minecraft:water";
  fluidOutput.amount = "500";
  recipe.outputRows = [fluidOutput];
  const built = V.buildRecipe(recipe);
  assert.deepEqual(built.outputs, [{ type: "fluid", id: "minecraft:water", amount: 500 }]);
  const state = withState((value) => {
    value.type = "recipe";
    value.itemSlots = [V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.capabilities = V.createCapabilities();
    value.capabilities.energy = V.createEnergy();
    value.capabilities.energy.id = "energy";
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.recipes = [recipe];
  });
  assert.deepEqual(V.validateState(state).errors, []);
});

test("saida entity usa seletor de bloco e valida o pattern", () => {
  const recipe = V.createRecipe();
  recipe.file = "pig_out";
  const input = V.createRecipeInputRow("item");
  input.resource = "minecraft:carrot";
  recipe.inputRows = [input];
  const output = V.createRecipeOutputRow("entity");
  output.resource = "minecraft:pig";
  output.spawn = "C";
  recipe.outputRows = [output];
  const built = V.buildRecipe(recipe);
  assert.deepEqual(built.outputs, [{ type: "entity", id: "minecraft:pig", spawn: "C" }]);
  const options = {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:nexus_smelting",
    itemInput: true,
    itemOutput: false,
    fluid: false,
    energy: false,
    symbols: ["C", "I"],
    inputSlots: [64],
    outputSlots: [],
    fluidCapacities: [],
    energyCapacity: 0
  };
  assert.deepEqual(V.validateRecipes([recipe], options).issues, []);
  output.spawn = "Z";
  assert.ok(codes({ issues: V.validateRecipes([recipe], options).issues }).includes("recipe.output.spawn"));
  output.spawn = "";
  assert.deepEqual(V.buildRecipe(recipe).outputs, [{ type: "entity", id: "minecraft:pig" }]);
  assert.deepEqual(V.validateRecipes([recipe], options).issues, []);
});

test("entity input e output exportam e validam quantidade", () => {
  const recipe = V.createRecipe();
  recipe.file = "entity_count";
  const input = V.createRecipeInputRow("entity");
  input.resource = "minecraft:pig";
  input.count = "2";
  const output = V.createRecipeOutputRow("entity");
  output.resource = "minecraft:cow";
  output.count = "3";
  output.spawn = "C";
  recipe.inputRows = [input];
  recipe.outputRows = [output];
  assert.deepEqual(V.buildRecipe(recipe).inputs, [
    { type: "entity", id: "minecraft:pig", count: 2 }
  ]);
  assert.deepEqual(V.buildRecipe(recipe).outputs, [
    { type: "entity", id: "minecraft:cow", count: 3, spawn: "C" }
  ]);
  const options = {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:fabrica_de_voxel_cube",
    itemInput: true,
    itemOutput: true,
    fluid: true,
    energy: true,
    symbols: ["C"],
    inputSlots: [64],
    outputSlots: [64],
    fluidCapacities: [16000],
    energyCapacity: 100000
  };
  assert.deepEqual(V.validateRecipes([recipe], options).issues, []);
  input.count = "0";
  output.count = "100";
  const invalid = codes(V.validateRecipes([recipe], options));
  assert.ok(invalid.includes("recipe.input.entity_count"));
  assert.ok(invalid.includes("recipe.output.entity_count"));
});

test("entity input e output aceitam tag", () => {
  const recipe = V.createRecipe();
  recipe.file = "entity_tags";
  const input = V.createRecipeInputRow("entity");
  input.resource = "#c:chickens";
  const output = V.createRecipeOutputRow("entity");
  output.resource = "#minecraft:undead";
  recipe.inputRows = [input];
  recipe.outputRows = [output];
  assert.deepEqual(V.buildRecipe(recipe).inputs, [
    { type: "entity", tag: "#c:chickens" }
  ]);
  assert.deepEqual(V.buildRecipe(recipe).outputs, [
    { type: "entity", tag: "#minecraft:undead" }
  ]);
  const options = {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:nexus_smelting",
    itemInput: false,
    itemOutput: false,
    fluid: false,
    energy: false,
    symbols: ["C"],
    inputSlots: [],
    outputSlots: [],
    fluidCapacities: [],
    energyCapacity: 0
  };
  assert.deepEqual(V.validateRecipes([recipe], options).issues, []);
  input.resource = "#sem_namespace";
  assert.ok(codes(V.validateRecipes([recipe], options)).includes("recipe.input.entity_invalid"));
  input.resource = "#c:chickens";
  output.resource = "#";
  assert.ok(codes(V.validateRecipes([recipe], options)).includes("recipe.output.entity_invalid"));
});

test("parseRecipe importa entity com tag", () => {
  const notes = [];
  const parsed = V.parseRecipe({
    type: "infinity_nexus_multiblock:multiblock",
    definition: "infinity_nexus_multiblock:nexus_smelting",
    inputs: [{ type: "entity", tag: "c:chickens" }],
    outputs: [{ type: "entity", tag: "#minecraft:undead", count: 2 }]
  }, notes);
  assert.equal(parsed.inputRows[0].resource, "#c:chickens");
  assert.equal(parsed.outputRows[0].resource, "#minecraft:undead");
  assert.equal(parsed.outputRows[0].count, "2");
  assert.deepEqual(notes, []);
});

test("receita so com output de entidade e valida", () => {
  const recipe = V.createRecipe();
  recipe.file = "only_entity_out";
  const input = V.createRecipeInputRow("energy");
  input.amount = "100";
  const output = V.createRecipeOutputRow("entity");
  output.resource = "minecraft:chicken";
  recipe.inputRows = [input];
  recipe.outputRows = [output];
  assert.deepEqual(V.buildRecipe(recipe).outputs, [
    { type: "entity", id: "minecraft:chicken" }
  ]);
  const options = {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:nexus_smelting",
    itemInput: false,
    itemOutput: false,
    fluid: false,
    energy: true,
    symbols: ["C"],
    inputSlots: [],
    outputSlots: [],
    fluidCapacities: [],
    energyCapacity: 10000
  };
  assert.deepEqual(V.validateRecipes([recipe], options).issues, []);
});

test("saida item sem slot de saida bloqueia", () => {
  const recipe = V.createRecipe();
  recipe.file = "no_slot";
  const input = V.createRecipeInputRow("item");
  input.resource = "minecraft:stone";
  const output = V.createRecipeOutputRow("item");
  output.resource = "minecraft:stone";
  recipe.inputRows = [input];
  recipe.outputRows = [output];
  const built = V.buildRecipe(recipe);
  assert.equal(built.outputs[0].type, "item");
  const result = V.validateRecipes([recipe], {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:nexus_smelting",
    itemInput: true,
    itemOutput: false,
    fluid: false,
    energy: false,
    symbols: [],
    inputSlots: [64],
    outputSlots: [],
    fluidCapacities: [],
    energyCapacity: 0
  });
  assert.ok(codes(result).includes("recipe.output.item_slot"));
});

test("receita de maquina sempre usa type fixo e definition atual", () => {
  const recipe = V.createRecipe();
  recipe.file = "test";
  recipe.type = "infinity_nexus_multiblock:tipo_antigo";
  const input = V.createRecipeInputRow("item");
  input.resource = "minecraft:stone";
  const output = V.createRecipeOutputRow("item");
  output.resource = "minecraft:stone";
  recipe.inputRows = [input];
  recipe.outputRows = [output];
  const state = withState((value) => {
    value.type = "recipe";
    value.itemSlots = [V.createItemSlot(), V.createItemSlot()];
    value.itemSlots[0].id = "input_0";
    value.itemSlots[1].id = "output_0";
    value.itemSlots[1].role = "output";
    value.recipes = [recipe];
  });
  assert.deepEqual(V.validateState(state).errors, []);
  const built = V.buildRecipe(recipe, { definitionId: "mymod:fornalha" });
  assert.equal(built.type, V.RECIPE_TYPE);
  assert.equal(built.definition, "mymod:fornalha");
});

test("receita valida capacidade de slots, tanques e energia", () => {
  const recipe = V.createRecipe();
  recipe.file = "limits";
  const itemInput = V.createRecipeInputRow("item");
  itemInput.resource = "minecraft:stone";
  itemInput.count = "5";
  const fluidInput = V.createRecipeInputRow("fluid");
  fluidInput.resource = "minecraft:water";
  fluidInput.amount = "12000";
  recipe.inputRows = [itemInput, fluidInput];
  const itemOutput = V.createRecipeOutputRow("item");
  itemOutput.resource = "minecraft:stone";
  itemOutput.count = "5";
  const fluidOutput = V.createRecipeOutputRow("fluid");
  fluidOutput.resource = "minecraft:water";
  fluidOutput.amount = "12000";
  const energyOutput = V.createRecipeOutputRow("energy");
  energyOutput.amount = "100001";
  recipe.outputRows = [itemOutput, fluidOutput, energyOutput];
  const result = V.validateRecipes([recipe], {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:nexus_smelting",
    itemInput: true,
    itemOutput: true,
    fluid: true,
    energy: true,
    symbols: [],
    inputSlots: [4],
    outputSlots: [4],
    fluidCapacities: [10000],
    energyCapacity: 100000
  });
  const found = codes(result);
  assert.ok(found.includes("recipe.input.capacity"));
  assert.ok(found.includes("recipe.input.stack_limit"));
  assert.ok(found.includes("recipe.output.capacity"));
  assert.ok(found.includes("recipe.input.fluid_capacity"));
  assert.ok(found.includes("recipe.input.fluid_tank_capacity"));
  assert.ok(found.includes("recipe.output.fluid_capacity"));
  assert.ok(found.includes("recipe.output.energy_capacity"));
});

test("receita aceita somente entidade e energia", () => {
  const recipe = V.createRecipe();
  recipe.file = "entity_energy";
  const entityInput = V.createRecipeInputRow("entity");
  entityInput.resource = "minecraft:pig";
  entityInput.spawn = "C";
  recipe.inputRows = [entityInput];
  const energyOutput = V.createRecipeOutputRow("energy");
  energyOutput.amount = "500";
  recipe.outputRows = [energyOutput];
  const result = V.validateRecipes([recipe], {
    machineType: "recipe",
    definitionId: "infinity_nexus_multiblock:example_controller",
    itemInput: false,
    itemOutput: false,
    fluid: false,
    energy: true,
    symbols: ["C"],
    inputSlots: [],
    outputSlots: [],
    fluidCapacities: [],
    energyCapacity: 100000
  });
  assert.deepEqual(codes(result), []);
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

test("fluid tank port importa, exporta e valida a key do pattern", () => {
  const imported = V.importDefinition({
    schema_version: 2,
    id: "mymod:tanque_porta",
    type: "storage",
    capabilities: {
      fluids: [{
        id: "water_tank",
        capacity: 8000,
        max_receive: 500,
        filter: "minecraft:water",
        port: "I"
      }]
    },
    pattern: {
      anchor: "C",
      layers: [["III", "ICI", "III"]],
      matchers: {
        I: "minecraft:iron_block"
      }
    }
  });
  assert.equal(imported.state.capabilities.fluids[0].port, "I");
  assert.deepEqual(V.buildDefinition(imported.state).capabilities.fluids[0], {
    id: "water_tank",
    capacity: 8000,
    max_receive: 500,
    filter: "minecraft:water",
    port: "I"
  });
  assert.deepEqual(V.validateState(imported.state).errors, []);
});

test("fluid tank port invalida key fora do pattern e formato invalido", () => {
  const state = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilities.fluids[0].port = "Z";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("fluid.port.invalid"));
  assert.equal(result.checks.find((check) => check.id === "capabilities").ok, false);

  const malformed = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilities.fluids[0].port = "sem dois pontos";
  });
  assert.ok(codes(V.validateState(malformed)).includes("fluid.port.invalid"));
});

test("fluid tank port avisa quando a key nao esta em capability_ports.fluid", () => {
  const state = withState((value) => {
    value.capabilities = V.createCapabilities();
    value.capabilities.fluids = [V.createFluidTank()];
    value.capabilities.fluids[0].id = "water_tank";
    value.capabilities.fluids[0].port = "I";
    value.capabilityPorts = V.createCapabilityPorts();
    value.capabilityPorts.fluid = ["C"];
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("fluid.port.not_exposed"));
  assert.ok(!codes(result).includes("fluid.port.invalid"));
});

test("sounds importa, preserva extras e exporta processing/finish", () => {
  const imported = V.importDefinition({
    schema_version: 2,
    id: "minhamod:maquina",
    type: "recipe",
    sounds: {
      processing: "minecraft:block.furnace.fire_crackle",
      finish: "minhamod:block.custom.finish",
      custom: 1
    },
    pattern: {
      anchor: "C",
      layers: [["III", "ICI", "III"]],
      matchers: { I: "minecraft:iron_block" }
    }
  });
  assert.equal(imported.state.sounds.processing, "minecraft:block.furnace.fire_crackle");
  assert.equal(imported.state.sounds.finish, "minhamod:block.custom.finish");
  assert.equal(imported.state.sounds.extras.custom, 1);
  assert.deepEqual(V.buildDefinition(imported.state).sounds, {
    processing: "minecraft:block.furnace.fire_crackle",
    finish: "minhamod:block.custom.finish",
    custom: 1
  });
});

test("sounds vazios sao omitidos e ids invalidos bloqueiam o checklist", () => {
  assert.equal(V.buildDefinition(V.createDefaultState()).sounds, undefined);

  const invalid = withState((value) => {
    value.sounds.processing = "furnace";
  });
  const result = V.validateState(invalid);
  assert.ok(codes(result).includes("sounds.processing.invalid"));
  assert.equal(result.checks.find((check) => check.id === "sounds").ok, false);
});

test("sounds custom validos e aviso em storage", () => {
  const recipe = withState((value) => {
    value.type = "recipe";
    value.sounds.processing = "minhamod:machine/proc";
    value.sounds.finish = "minhamod:machine/finish";
  });
  const recipeCodes = codes(V.validateState(recipe));
  assert.ok(!recipeCodes.includes("sounds.processing.invalid"));
  assert.ok(!recipeCodes.includes("sounds.finish.invalid"));
  assert.ok(!recipeCodes.includes("sounds.type"));

  const storage = withState((value) => {
    value.sounds.finish = "minecraft:block.anvil.use";
  });
  assert.ok(codes(V.validateState(storage)).includes("sounds.type"));
});

test("sounds invalido e ignorado com aviso na importacao", () => {
  const imported = V.importDefinition({
    schema_version: 2,
    id: "minhamod:maquina",
    type: "recipe",
    sounds: []
  });
  assert.ok(imported.notes.some((note) => note.message.includes("sounds inválido")));
  assert.deepEqual(imported.state.sounds, V.createSounds());
});

test("textures e opcional e nao aparece quando vazio", () => {
  const state = V.createDefaultState();
  const json = V.buildDefinition(state);
  assert.equal(json.textures, undefined);
  const result = V.validateState(state);
  assert.deepEqual(result.errors, []);
  assert.equal(result.checks.find((check) => check.id === "textures").ok, true);
});

test("textures exporta idle e active e faz round-trip", () => {
  const state = withState((value) => {
    value.textures = V.createTextures();
    value.textures.idle.front = "minecraft:block/stone";
    value.textures.idle.side = "minecraft:block/oak_planks";
    value.textures.idle.top = "minecraft:block/oak_log_top";
    value.textures.idle.bottom = "minecraft:block/oak_log_top";
    value.textures.active.front = "minecraft:block/smooth_stone";
    value.textures.active.side = "minecraft:block/oak_log";
  });
  const json = V.buildDefinition(state);
  assert.deepEqual(json.textures, {
    front: "minecraft:block/stone",
    side: "minecraft:block/oak_planks",
    top: "minecraft:block/oak_log_top",
    bottom: "minecraft:block/oak_log_top",
    active: {
      front: "minecraft:block/smooth_stone",
      side: "minecraft:block/oak_log"
    }
  });
  const imported = V.importDefinition(json);
  assert.deepEqual(V.buildDefinition(imported.state).textures, json.textures);
  assert.deepEqual(V.validateState(imported.state).errors, []);
});

test("textures invalidas bloqueiam a exportacao", () => {
  const state = withState((value) => {
    value.textures = V.createTextures();
    value.textures.idle.front = "stone";
    value.textures.active.side = "minecraft:block/oak planks";
  });
  const result = V.validateState(state);
  assert.ok(codes(result).includes("textures.invalid"));
  assert.equal(result.checks.find((check) => check.id === "textures").ok, false);
});

test("textures invalidas na importacao sao ignoradas com nota", () => {
  const imported = V.importDefinition({
    schema_version: 2,
    id: "minhamod:maquina",
    type: "storage",
    textures: {
      front: 42,
      side: "minecraft:block/oak_planks",
      active: "stone"
    }
  });
  assert.equal(imported.state.textures.idle.side, "minecraft:block/oak_planks");
  assert.equal(imported.state.textures.idle.front, "");
  assert.ok(imported.notes.some((note) => note.message.includes("textures.front")));
  assert.ok(imported.notes.some((note) => note.message.includes("textures.active")));
});

if (process.exitCode) {
  console.error("Falhas encontradas.");
} else {
  console.log(passed + " testes passaram.");
}
