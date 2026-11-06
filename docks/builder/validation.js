(function (root, factory) {
  var api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.MultiblockValidation = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  var SCHEMA_VERSION = 2;
  var RECIPE_TYPE = "infinity_nexus_multiblock:multiblock";
  var KNOWN_RECIPE_TYPES = [RECIPE_TYPE];
  var NAMESPACE_PATTERN = /^[a-z0-9_.-]+$/;
  var PATH_PATTERN = /^[a-z0-9_./-]+$/;
  var ITEM_SLOT_ROLES = ["input", "output", "fuel", "upgrade", "component", "storage", "fluid"];
  var ITEM_SLOT_KEYS = ["id", "role", "filter", "limit", "faces", "x", "y"];
  var DIRECTIONS = ["north", "south", "east", "west", "up", "down"];
  var CAPABILITY_MAX = 2147483647;
  var CAPABILITY_PORT_KEYS = ["item", "energy", "fluid"];
  var ENERGY_KEYS = ["id", "capacity", "max_receive", "max_extract"];
  var FLUID_KEYS = ["id", "capacity", "max_receive", "max_extract", "filter"];
  var CONTROLLER_FACES = ["front", "back", "left", "right", "top", "bottom"];
  var CONTROLLER_KEYS = ["block", "textures", "formed_textures", "particle", "light_level"];
  var CONTROLLER_RECIPE_KEYS = ["pattern", "key"];
  var CONTROLLER_RECIPE_MAX_SIZE = 3;
  var MAX_LIGHT_LEVEL = 15;
  var GUI_ELEMENT_TYPES = ["title", "text", "player_inventory", "slot", "energy_bar", "fluid_bar", "progress_bar", "arrow", "entity"];
  var GUI_ELEMENT_KEYS = ["type", "x", "y", "width", "height", "text", "slot", "capability", "color", "shadow", "direction", "u", "v"];
  var GUI_DIRECTIONS = ["up", "down", "left", "right"];
  var GUI_KEYS = ["texture", "width", "height", "texture_width", "texture_height", "elements"];
  var GUI_DEFAULT_WIDTH = 176;
  var GUI_DEFAULT_HEIGHT = 166;
  var GUI_DEFAULT_TEXTURE_SIZE = 256;
  var GUI_MAX_SIZE = 4096;
  var GUI_SLOT_SIZE = 18;
  var GUI_ENTITY_CELL_SIZE = 18;
  var GUI_PLAYER_WIDTH = 162;
  var GUI_PLAYER_HEIGHT = 76;
  var KNOWN_ROOT_KEYS = [
    "schema_version",
    "id",
    "type",
    "recipe_type",
    "priority",
    "tier",
    "inventory_rows",
    "item_slots",
    "capabilities",
    "capability_ports",
    "controller",
    "recipe",
    "gui",
    "pattern",
    "extends",
    "family",
    "template",
    "overrides"
  ];
  var KNOWN_PATTERN_KEYS = ["anchor", "layers", "matchers"];
  var STEP_ORDER = ["identity", "pattern", "matchers", "slots", "capabilities", "controller", "gui", "recipes", "inheritance", "review"];
  var CHECK_LABELS = {
    schema: "schema_version = 2",
    identity: "id, namespace, path e type válidos",
    geometry: "camadas e linhas uniformes",
    anchor: "exatamente uma âncora",
    matchers: "todos os símbolos com matcher válido",
    slots: "item_slots válidos",
    capabilities: "capabilities válidas",
    capabilityPorts: "capability_ports válidas",
    controller: "controller único e item compartilhado",
    controllerRecipe: "receita shaped do controller válida",
    gui: "gui e elementos válidos",
    recipes: "receitas data-driven válidas",
    inheritance: "herança, template e overrides válidos",
    metadata: "tier, priority e inventory_rows válidos"
  };

  function clone(value) {
    if (value === undefined) {
      return undefined;
    }
    if (typeof structuredClone === "function") {
      return structuredClone(value);
    }
    return JSON.parse(JSON.stringify(value));
  }

  function isPlainObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function trimString(value) {
    return typeof value === "string" ? value.trim() : "";
  }

  function toInt(value) {
    if (typeof value === "number") {
      return Number.isInteger(value) ? value : null;
    }
    if (typeof value === "string" && /^-?\d+$/.test(value.trim())) {
      var parsed = Number.parseInt(value.trim(), 10);
      return Number.isSafeInteger(parsed) ? parsed : null;
    }
    return null;
  }

  function toIntOr(value, fallback) {
    var parsed = toInt(value);
    return parsed === null ? fallback : parsed;
  }

  function isValidNamespace(value) {
    return typeof value === "string" && NAMESPACE_PATTERN.test(value);
  }

  function isValidPath(value) {
    return typeof value === "string" && PATH_PATTERN.test(value);
  }

  function isValidIdentifier(value) {
    var text = trimString(value);
    if (!text) {
      return false;
    }
    var separator = text.indexOf(":");
    if (separator <= 0 || separator === text.length - 1) {
      return false;
    }
    return isValidNamespace(text.slice(0, separator)) && isValidPath(text.slice(separator + 1));
  }

  function isSingleSymbol(value) {
    return typeof value === "string" && value.length === 1;
  }

  function stripTagPrefix(value) {
    var text = trimString(value);
    return text.startsWith("#") ? text.slice(1) : text;
  }

  function createMatcher(kind) {
    return {
      kind: kind || "block",
      block: "",
      tag: "",
      state: [],
      raw: null
    };
  }

  function createItemSlot() {
    return {
      id: "",
      role: "input",
      filter: "",
      limit: 64,
      faces: null,
      x: null,
      y: null,
      extras: {}
    };
  }

  function parseItemSlot(value, notes, index) {
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "item_slots[" + index + "] não é um objeto e foi ignorado."
      });
      return null;
    }

    var slot = createItemSlot();
    var id = trimString(value.id);
    if (id) {
      slot.id = id;
    } else {
      notes.push({
        severity: "warning",
        message: "item_slots[" + index + "] está sem id."
      });
    }

    if (typeof value.role === "string") {
      slot.role = value.role;
      if (ITEM_SLOT_ROLES.indexOf(value.role) === -1) {
        notes.push({
          severity: "warning",
          message: "item_slots[" + index + "].role não é um role conhecido."
        });
      }
    } else {
      slot.role = "";
      notes.push({
        severity: "warning",
        message: "item_slots[" + index + "] está sem role."
      });
    }

    if (typeof value.filter === "string") {
      slot.filter = value.filter;
    } else if (value.filter !== undefined) {
      notes.push({
        severity: "warning",
        message: "item_slots[" + index + "].filter inválido foi ignorado."
      });
    }

    if (value.limit !== undefined) {
      var limit = toInt(value.limit);
      if (limit === null || limit < 1 || limit > 64) {
        notes.push({
          severity: "warning",
          message: "item_slots[" + index + "].limit inválido foi normalizado para 64."
        });
      } else {
        slot.limit = limit;
      }
    }

    if (value.x !== undefined) {
      var x = toInt(value.x);
      if (x === null) {
        notes.push({
          severity: "warning",
          message: "item_slots[" + index + "].x inválido foi ignorado."
        });
      } else {
        slot.x = x;
      }
    }
    if (value.y !== undefined) {
      var y = toInt(value.y);
      if (y === null) {
        notes.push({
          severity: "warning",
          message: "item_slots[" + index + "].y inválido foi ignorado."
        });
      } else {
        slot.y = y;
      }
    }

    if (value.faces !== undefined) {
      if (isPlainObject(value.faces)) {
        slot.faces = clone(value.faces);
      } else {
        notes.push({
          severity: "warning",
          message: "item_slots[" + index + "].faces inválido foi ignorado."
        });
      }
    }

    Object.keys(value).forEach(function (key) {
      if (!ITEM_SLOT_KEYS.includes(key)) {
        slot.extras[key] = clone(value[key]);
      }
    });

    return slot;
  }

  function parseItemSlots(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!Array.isArray(value)) {
      notes.push({
        severity: "warning",
        message: "item_slots inválido foi ignorado."
      });
      return null;
    }
    var slots = [];
    value.forEach(function (entry, index) {
      var slot = parseItemSlot(entry, notes, index);
      if (slot) {
        slots.push(slot);
      }
    });
    return slots;
  }

  function createInheritance() {
    return {
      mode: "none",
      parent: "",
      template: false,
      overrides: null,
      overridesRaw: "",
      overridesError: ""
    };
  }

  function parseInheritance(source, notes) {
    var inheritance = createInheritance();
    if (!isPlainObject(source)) {
      return inheritance;
    }
    var hasExtends = source.extends !== undefined;
    var hasFamily = source.family !== undefined;
    var rawParent = hasExtends ? source.extends : hasFamily ? source.family : undefined;
    if (hasExtends && hasFamily) {
      notes.push({
        severity: "warning",
        message: "extends e family vieram juntos; o mod rejeita ambos. family foi ignorado."
      });
    }
    if (rawParent !== undefined) {
      if (typeof rawParent === "string") {
        inheritance.mode = hasExtends ? "extends" : "family";
        inheritance.parent = rawParent.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "A referência de herança precisa ser um identifier e foi ignorada."
        });
      }
    }
    if (source.template !== undefined) {
      if (typeof source.template === "boolean") {
        inheritance.template = source.template;
      } else {
        notes.push({
          severity: "warning",
          message: "template precisa ser true ou false; foi ignorado."
        });
      }
    }
    if (source.overrides !== undefined && source.overrides !== null) {
      if (isPlainObject(source.overrides)) {
        inheritance.overrides = clone(source.overrides);
        inheritance.overridesRaw = JSON.stringify(source.overrides, null, 2);
      } else {
        notes.push({
          severity: "warning",
          message: "overrides precisa ser um objeto e foi ignorado."
        });
      }
    }
    return inheritance;
  }

  function buildInheritance(inheritance) {
    var result = {};
    if (!isPlainObject(inheritance)) {
      return result;
    }
    var parent = trimString(inheritance.parent);
    if (inheritance.mode === "extends" && parent) {
      result.extends = parent;
    } else if (inheritance.mode === "family" && parent) {
      result.family = parent;
    }
    if (inheritance.template === true) {
      result.template = true;
    }
    if (isPlainObject(inheritance.overrides) && Object.keys(inheritance.overrides).length > 0) {
      result.overrides = clone(inheritance.overrides);
    }
    return result;
  }

  function createRecipe() {
    return {
      file: "",
      type: RECIPE_TYPE,
      definition: "",
      inputRows: [],
      outputRows: [],
      time: 100,
      extras: {}
    };
  }

  function createRecipeInputRow(kind) {
    if (kind === "fluid") {
      return { kind: "fluid", resource: "", amount: "1000" };
    }
    if (kind === "entity") {
      return { kind: "entity", resource: "", spawn: "", count: "1" };
    }
    if (kind === "energy") {
      return { kind: "energy", amount: "0" };
    }
    return { kind: "item", resource: "", count: "1", componentsRaw: "" };
  }

  function createRecipeOutputRow(kind) {
    if (kind === "fluid") {
      return { kind: "fluid", resource: "", amount: "1000", chance: "1" };
    }
    if (kind === "entity") {
      return { kind: "entity", resource: "", spawn: "", count: "1", chance: "1" };
    }
    if (kind === "energy") {
      return { kind: "energy", amount: "0", chance: "1" };
    }
    return { kind: "item", resource: "", count: "1", componentsRaw: "", chance: "1" };
  }

  function parseRecipeResource(value) {
    if (typeof value === "string") {
      return value.trim();
    }
    if (value !== undefined && value !== null) {
      return JSON.stringify(value);
    }
    return "";
  }

  function recipeRowKind(value) {
    var kind = trimString(value) || "item";
    return ["item", "fluid", "entity", "energy"].indexOf(kind) === -1 ? "item" : kind;
  }

  function parseRecipe(raw, notes) {
    var recipe = createRecipe();
    if (!isPlainObject(raw)) {
      notes.push({
        severity: "warning",
        message: "A receita nao e um objeto e foi ignorada."
      });
      return recipe;
    }
    if (typeof raw.type === "string" && raw.type !== RECIPE_TYPE) {
      notes.push({ severity: "warning", message: "recipe.type antigo foi substituido por " + RECIPE_TYPE + "." });
    }
    if (typeof raw.definition === "string") {
      recipe.definition = raw.definition;
    } else if (raw.definition !== undefined) {
      notes.push({ severity: "warning", message: "recipe.definition invalido foi ignorado." });
    }
    if (Array.isArray(raw.inputs)) {
      raw.inputs.forEach(function (entry) {
        if (!isPlainObject(entry)) {
          notes.push({ severity: "warning", message: "Uma entrada de inputs invalida foi ignorada." });
          return;
        }
        var kind = recipeRowKind(entry.type);
        var row = createRecipeInputRow(kind);
        if (kind === "item") {
          row.resource = parseRecipeResource(entry.ingredient);
          row.count = entry.count === undefined ? "1" : String(entry.count);
          if (entry.components !== undefined) {
            row.componentsRaw = JSON.stringify(entry.components, null, 2);
          }
        } else if (kind === "fluid") {
          row.resource = parseRecipeResource(entry.ingredient);
          row.amount = entry.amount === undefined ? "1000" : String(entry.amount);
        } else if (kind === "entity") {
          row.resource = typeof entry.id === "string" ? entry.id : "";
          row.spawn = typeof entry.spawn === "string" ? entry.spawn : "";
          row.count = entry.count === undefined ? "1" : String(entry.count);
        } else {
          row.amount = entry.amount === undefined ? "0" : String(entry.amount);
        }
        recipe.inputRows.push(row);
      });
    } else {
      if (Array.isArray(raw.ingredients)) {
        raw.ingredients.forEach(function (entry) {
          var row = createRecipeInputRow("item");
          row.resource = parseRecipeResource(entry);
          recipe.inputRows.push(row);
        });
      } else if (raw.ingredients !== undefined) {
        notes.push({ severity: "warning", message: "recipe.ingredients invalido foi ignorado." });
      }
      if (Array.isArray(raw.fluid_ingredients)) {
        raw.fluid_ingredients.forEach(function (entry) {
          if (!isPlainObject(entry) || !trimString(entry.ingredient)) {
            notes.push({ severity: "warning", message: "Uma entrada de fluid_ingredients invalida foi ignorada." });
            return;
          }
          var row = createRecipeInputRow("fluid");
          row.resource = trimString(entry.ingredient);
          row.amount = String(toIntOr(entry.amount, 1000));
          recipe.inputRows.push(row);
        });
      } else if (raw.fluid_ingredients !== undefined) {
        notes.push({ severity: "warning", message: "recipe.fluid_ingredients invalido foi ignorado." });
      }
      if (raw.energy !== undefined && toInt(raw.energy) > 0) {
        var energyRow = createRecipeInputRow("energy");
        energyRow.amount = String(raw.energy);
        recipe.inputRows.push(energyRow);
      }
    }
    if (Array.isArray(raw.outputs)) {
      raw.outputs.forEach(function (entry) {
        if (!isPlainObject(entry)) {
          notes.push({ severity: "warning", message: "Uma entrada de outputs invalida foi ignorada." });
          return;
        }
        var kind = recipeRowKind(entry.type);
        var row = createRecipeOutputRow(kind);
        if (kind === "item") {
          row.resource = typeof entry.id === "string" ? entry.id : "";
          row.count = entry.count === undefined ? "1" : String(entry.count);
          row.chance = entry.chance === undefined ? "1" : String(entry.chance);
          if (entry.components !== undefined) {
            row.componentsRaw = JSON.stringify(entry.components, null, 2);
          }
        } else if (kind === "fluid") {
          row.resource = typeof entry.id === "string" ? entry.id : "";
          row.amount = entry.amount === undefined ? "1000" : String(entry.amount);
          row.chance = entry.chance === undefined ? "1" : String(entry.chance);
        } else if (kind === "entity") {
          row.resource = typeof entry.id === "string" ? entry.id : "";
          row.spawn = typeof entry.spawn === "string" ? entry.spawn : "";
          row.count = entry.count === undefined ? "1" : String(entry.count);
          row.chance = entry.chance === undefined ? "1" : String(entry.chance);
        } else {
          row.amount = entry.amount === undefined ? "0" : String(entry.amount);
          row.chance = entry.chance === undefined ? "1" : String(entry.chance);
        }
        recipe.outputRows.push(row);
      });
    } else if (isPlainObject(raw.output)) {
      var outputKind = recipeRowKind(raw.output.type);
      var outputRow = createRecipeOutputRow(outputKind);
      outputRow.resource = typeof raw.output.id === "string" ? raw.output.id : "";
      if (outputKind === "fluid") {
        outputRow.amount = raw.output.amount === undefined ? "1000" : String(raw.output.amount);
      } else if (outputKind === "entity") {
        outputRow.spawn = typeof raw.output.spawn === "string" ? raw.output.spawn : "";
        outputRow.count = raw.output.count === undefined ? "1" : String(raw.output.count);
      } else if (outputKind === "energy") {
        outputRow.amount = raw.output.amount === undefined ? "0" : String(raw.output.amount);
      } else {
        outputRow.count = raw.output.count === undefined ? "1" : String(raw.output.count);
        if (raw.output.components !== undefined) {
          outputRow.componentsRaw = JSON.stringify(raw.output.components, null, 2);
        }
      }
      outputRow.chance = raw.output.chance === undefined ? "1" : String(raw.output.chance);
      recipe.outputRows.push(outputRow);
    } else if (isPlainObject(raw.result)) {
      var resultRow = createRecipeOutputRow("item");
      resultRow.resource = typeof raw.result.id === "string" ? raw.result.id : "";
      resultRow.count = raw.result.count === undefined ? "1" : String(raw.result.count);
      if (raw.result.components !== undefined) {
        resultRow.componentsRaw = JSON.stringify(raw.result.components, null, 2);
      }
      recipe.outputRows.push(resultRow);
    } else if (raw.output !== undefined || raw.result !== undefined) {
      notes.push({ severity: "warning", message: "recipe.output/result invalido foi ignorado." });
    }
    if (raw.time !== undefined) {
      recipe.time = raw.time;
    }
    Object.keys(raw).forEach(function (key) {
      if (["type", "definition", "inputs", "outputs", "ingredients", "fluid_ingredients",
        "output", "result", "energy", "time"].indexOf(key) === -1) {
        recipe.extras[key] = clone(raw[key]);
      }
    });
    return recipe;
  }

  function parseIngredientLines(text) {
    var values = [];
    var errors = [];
    String(text === undefined || text === null ? "" : text).split(/\r?\n/).forEach(function (rawLine, index) {
      var line = trimString(rawLine);
      if (!line) {
        return;
      }
      if (line.charAt(0) === "[" || line.charAt(0) === "{") {
        try {
          var parsed = JSON.parse(line);
          if (Array.isArray(parsed) || isPlainObject(parsed)) {
            values.push(parsed);
          } else {
            errors.push("Linha " + (index + 1) + " precisa ser um id, tag ou JSON de ingrediente.");
          }
        } catch (error) {
          errors.push("Linha " + (index + 1) + " tem JSON inválido: " + error.message);
        }
        return;
      }
      if (isValidIdentifier(stripTagPrefix(line))) {
        values.push(line);
      } else {
        errors.push("Linha " + (index + 1) + " não é um id ou tag de item válido.");
      }
    });
    return { values: values, errors: errors };
  }

  function parseFluidLines(text) {
    var values = [];
    var errors = [];
    String(text === undefined || text === null ? "" : text).split(/\r?\n/).forEach(function (rawLine, index) {
      var line = trimString(rawLine);
      if (!line) {
        return;
      }
      var match = /^(#?\S+)\s+(\d+)$/.exec(line);
      if (!match || !isValidIdentifier(stripTagPrefix(match[1]))) {
        errors.push("Linha " + (index + 1) + " de fluido precisa ser \"id quantidade\".");
        return;
      }
      var amount = toInt(match[2]);
      if (amount === null || amount < 1) {
        errors.push("Linha " + (index + 1) + " precisa de quantidade inteira positiva.");
        return;
      }
      values.push({ ingredient: match[1], amount: amount });
    });
    return { values: values, errors: errors };
  }

  function parseComponents(text) {
    var raw = trimString(text);
    if (!raw) {
      return { value: null, error: "" };
    }
    try {
      var value = JSON.parse(raw);
      if (!isPlainObject(value)) {
        return { value: null, error: "components precisa ser um objeto JSON." };
      }
      return { value: value, error: "" };
    } catch (error) {
      return { value: null, error: "JSON inválido em components: " + error.message };
    }
  }

  function recipePatternSymbols(state) {
    var symbols = new Set();
    var layers = Array.isArray(state.layers) ? state.layers : [];
    layers.forEach(function (layer) {
      if (!Array.isArray(layer)) {
        return;
      }
      layer.forEach(function (row) {
        if (typeof row !== "string") {
          return;
        }
        for (var index = 0; index < row.length; index += 1) {
          var symbol = row.charAt(index);
          if (symbol !== " ") {
            symbols.add(symbol);
          }
        }
      });
    });
    if (isSingleSymbol(state.anchor) && state.anchor !== " ") {
      symbols.add(state.anchor);
    }
    return Array.from(symbols).sort();
  }

  function recipeAvailability(state) {
    var source = isPlainObject(state) ? state : {};
    var capabilities = isPlainObject(source.capabilities) ? source.capabilities : null;
    var fluids = capabilities && Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    var slots = Array.isArray(source.itemSlots) ? source.itemSlots : [];
    var inputSlots = slots.filter(function (slot) {
      return isPlainObject(slot) && slot.role === "input";
    }).map(function (slot) {
      return toIntOr(slot.limit, 64);
    });
    var outputSlots = slots.filter(function (slot) {
      return isPlainObject(slot) && slot.role === "output";
    }).map(function (slot) {
      return toIntOr(slot.limit, 64);
    });
    return {
      energy: !!(capabilities && isPlainObject(capabilities.energy)),
      fluid: fluids.length > 0,
      itemInput: inputSlots.length > 0,
      itemOutput: outputSlots.length > 0,
      symbols: recipePatternSymbols(source),
      inputSlots: inputSlots,
      outputSlots: outputSlots,
      fluidCapacities: fluids.map(function (tank) {
        return isPlainObject(tank) ? toIntOr(tank.capacity, 0) : 0;
      }),
      energyCapacity: capabilities && isPlainObject(capabilities.energy)
        ? toIntOr(capabilities.energy.capacity, 0)
        : 0
    };
  }

  function availabilityAllowed(options, key) {
    return !isPlainObject(options) || options[key] !== false;
  }

  function validSpawnSelector(value, symbols) {
    var text = trimString(value);
    if (!text) {
      return false;
    }
    if (text.length === 1) {
      return text !== " " && (!Array.isArray(symbols) || symbols.indexOf(text) !== -1);
    }
    if (text.charAt(0) === "#") {
      return isValidIdentifier(text.slice(1));
    }
    return isValidIdentifier(text);
  }

  function validCapabilityPortSelector(value, symbols) {
    return validSpawnSelector(value, symbols);
  }

  function buildRecipeIngredientValue(resource, componentsRaw) {
    var text = trimString(resource);
    if (!text) {
      return null;
    }
    var components = parseComponents(componentsRaw);
    if (components.error) {
      return null;
    }
    if (components.value !== null) {
      return {
        "neoforge:ingredient_type": "neoforge:components",
        items: text,
        components: clone(components.value)
      };
    }
    if (text.charAt(0) === "{" || text.charAt(0) === "[") {
      try {
        return JSON.parse(text);
      } catch (error) {
        return null;
      }
    }
    return text;
  }

  function validRecipeIngredient(value) {
    if (typeof value === "string") {
      return isValidIdentifier(stripTagPrefix(value));
    }
    return isPlainObject(value);
  }

  function recipeChance(value) {
    if (value === undefined || value === null || value === "") {
      return 1;
    }
    var parsed = typeof value === "number" ? value : Number(String(value).trim());
    return Number.isFinite(parsed) ? parsed : null;
  }

  function buildRecipe(recipe, options) {
    var source = isPlainObject(recipe) ? recipe : createRecipe();
    var inputs = [];
    var outputs = [];
    var inputRows = Array.isArray(source.inputRows) ? source.inputRows : [];
    var outputRows = Array.isArray(source.outputRows) ? source.outputRows : [];
    inputRows.forEach(function (row) {
      if (!isPlainObject(row)) {
        return;
      }
      var kind = recipeRowKind(row.kind);
      if (kind === "item") {
        var ingredient = buildRecipeIngredientValue(row.resource, row.componentsRaw);
        if (ingredient === null) {
          return;
        }
        var itemInput = { type: "item", ingredient: ingredient };
        var count = toIntOr(row.count, 1);
        if (count !== 1) {
          itemInput.count = count;
        }
        inputs.push(itemInput);
      } else if (kind === "fluid") {
        var fluidId = trimString(row.resource);
        var amount = toIntOr(row.amount, 1000);
        if (!fluidId || amount < 1) {
          return;
        }
        inputs.push({ type: "fluid", ingredient: fluidId, amount: amount });
      } else if (kind === "entity") {
        var entityId = trimString(row.resource);
        if (!entityId) {
          return;
        }
        var entityInput = { type: "entity", id: entityId };
        var entityCount = toIntOr(row.count, 1);
        if (entityCount !== 1) {
          entityInput.count = entityCount;
        }
        var spawn = trimString(row.spawn);
        if (spawn) {
          entityInput.spawn = spawn;
        }
        inputs.push(entityInput);
      } else {
        var energyAmount = toIntOr(row.amount, 0);
        if (energyAmount < 1) {
          return;
        }
        inputs.push({ type: "energy", amount: energyAmount });
      }
    });
    outputRows.forEach(function (row) {
      if (!isPlainObject(row)) {
        return;
      }
      var kind = recipeRowKind(row.kind);
      var chance = recipeChance(row.chance);
      if (chance === null) {
        chance = 1;
      }
      if (kind === "item") {
        var itemId = trimString(row.resource);
        if (!itemId) {
          return;
        }
        var itemOutput = { type: "item", id: itemId, count: toIntOr(row.count, 1) };
        var components = parseComponents(row.componentsRaw).value;
        if (components !== null) {
          itemOutput.components = clone(components);
        }
        if (chance !== 1) {
          itemOutput.chance = chance;
        }
        outputs.push(itemOutput);
      } else if (kind === "fluid") {
        var fluidId = trimString(row.resource);
        var amount = toIntOr(row.amount, 1000);
        if (!fluidId || amount < 1) {
          return;
        }
        var fluidOutput = { type: "fluid", id: fluidId, amount: amount };
        if (chance !== 1) {
          fluidOutput.chance = chance;
        }
        outputs.push(fluidOutput);
      } else if (kind === "entity") {
        var entityId = trimString(row.resource);
        var spawn = trimString(row.spawn);
        if (!entityId) {
          return;
        }
        var entityOutput = { type: "entity", id: entityId };
        var entityCount = toIntOr(row.count, 1);
        if (entityCount !== 1) {
          entityOutput.count = entityCount;
        }
        if (spawn) {
          entityOutput.spawn = spawn;
        }
        if (chance !== 1) {
          entityOutput.chance = chance;
        }
        outputs.push(entityOutput);
      } else {
        var energyAmount = toIntOr(row.amount, 0);
        if (energyAmount < 1) {
          return;
        }
        var energyOutput = { type: "energy", amount: energyAmount };
        if (chance !== 1) {
          energyOutput.chance = chance;
        }
        outputs.push(energyOutput);
      }
    });
    var definitionId = trimString(options && options.definitionId) || trimString(source.definition);
    var built = { type: RECIPE_TYPE };
    if (definitionId) {
      built.definition = definitionId;
    }
    if (inputs.length > 0) {
      built.inputs = inputs;
    }
    if (outputs.length > 0) {
      built.outputs = outputs;
    }
    built.time = toIntOr(source.time, 100);
    var extras = isPlainObject(source.extras) ? source.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (["type", "definition", "inputs", "outputs", "ingredients", "fluid_ingredients",
        "output", "result", "energy", "time"].indexOf(key) === -1) {
        built[key] = clone(extras[key]);
      }
    });
    return built;
  }

  function validateRecipes(recipes, options) {
    var settings = options || {};
    var list = Array.isArray(recipes) ? recipes : [];
    var machineType = settings.machineType;
    var definitionId = trimString(settings.definitionId);
    var issues = [];
    var ok = true;
    var seenFiles = new Set();
    var inputSlots = Array.isArray(settings.inputSlots) ? settings.inputSlots : null;
    var outputSlots = Array.isArray(settings.outputSlots) ? settings.outputSlots : null;
    var fluidCapacities = Array.isArray(settings.fluidCapacities) ? settings.fluidCapacities : null;
    var energyCapacity = toInt(settings.energyCapacity);

    function addRecipeIssue(code, severity, index, field, message) {
      issues.push(issue(code, severity, "recipes", "recipe:" + index + ":" + field, message));
      if (severity === "error") {
        ok = false;
      }
    }

    function sum(values) {
      return values.reduce(function (total, value) {
        return total + (toIntOr(value, 0) || 0);
      }, 0);
    }

    if (!Array.isArray(recipes)) {
      addRecipeIssue("recipes.invalid", "error", 0, "list", "recipes precisa ser uma lista.");
      return { issues: issues, ok: ok };
    }
    if (machineType !== "recipe" && list.length > 0) {
      addRecipeIssue("recipes.storage", "warning", 0, "list",
        "Receitas so sao processadas por uma definicao type=recipe.");
    }

    list.forEach(function (recipe, index) {
      if (!isPlainObject(recipe)) {
        addRecipeIssue("recipe.invalid", "error", index, "recipe", "A receita precisa ser um objeto.");
        return;
      }
      var file = trimString(recipe.file).replace(/\.json$/i, "");
      if (!file) {
        addRecipeIssue("recipe.file.required", "error", index, "file", "Informe o nome do arquivo da receita.");
      } else if (!isValidPath(file)) {
        addRecipeIssue("recipe.file.invalid", "error", index, "file",
          "O arquivo aceita apenas a-z, 0-9, _, ., / e -.");
      } else if (seenFiles.has(file)) {
        addRecipeIssue("recipe.file.duplicate", "error", index, "file",
          "Ja existe outra receita com este arquivo.");
      } else {
        seenFiles.add(file);
      }

      var definition = trimString(recipe.definition) || definitionId;
      if (!definition) {
        addRecipeIssue("recipe.definition.required", "error", index, "definition",
          "A receita precisa apontar para uma definicao.");
      } else if (!isValidIdentifier(definition)) {
        addRecipeIssue("recipe.definition.invalid", "error", index, "definition",
          "definition precisa ser um identifier valido.");
      }

      var inputRows = Array.isArray(recipe.inputRows) ? recipe.inputRows : [];
      var outputRows = Array.isArray(recipe.outputRows) ? recipe.outputRows : [];
      if (inputRows.length === 0) {
        addRecipeIssue("recipe.input.required", "error", index, "inputs",
          "A receita precisa de pelo menos um input.");
      }
      if (outputRows.length === 0) {
        addRecipeIssue("recipe.output.required", "error", index, "outputs",
          "A receita precisa de pelo menos um output.");
      }

      var itemInputRows = 0;
      var itemInputTotal = 0;
      var fluidInputRows = 0;
      var fluidInputTotal = 0;
      inputRows.forEach(function (row, rowIndex) {
        var field = "input:" + rowIndex + ":";
        if (!isPlainObject(row)) {
          addRecipeIssue("recipe.input.invalid", "error", index, field + "row", "Input invalido.");
          return;
        }
        var kind = trimString(row.kind) || "item";
        if (["item", "fluid", "entity", "energy"].indexOf(kind) === -1) {
          addRecipeIssue("recipe.input.type", "error", index, field + "kind",
            "input.kind precisa ser item, fluid, entity ou energy.");
          return;
        }
        if (kind === "item") {
          if (!availabilityAllowed(settings, "itemInput")) {
            addRecipeIssue("recipe.input.item_slot", "error", index, field + "kind",
              "Esta maquina nao tem item_slot de input.");
          }
          var ingredientValue = buildRecipeIngredientValue(row.resource, row.componentsRaw);
          if (ingredientValue === null || !validRecipeIngredient(ingredientValue)) {
            addRecipeIssue("recipe.input.item_invalid", "error", index, field + "resource",
              "O ingrediente precisa ser id, tag, JSON valido ou usar components.");
          }
          var components = parseComponents(row.componentsRaw);
          if (components.error) {
            addRecipeIssue("recipe.input.components", "error", index, field + "components", components.error);
          }
          var count = toInt(row.count);
          if (count === null || count < 1 || count > 99) {
            addRecipeIssue("recipe.input.count", "error", index, field + "count",
              "A quantidade do input precisa ser um inteiro de 1 a 99.");
          } else {
            itemInputRows += 1;
            itemInputTotal += count;
            if (inputSlots !== null && count > Math.max.apply(null, inputSlots.concat([0]))) {
              addRecipeIssue("recipe.input.stack_limit", "error", index, field + "count",
                "A quantidade do input excede o limite de um unico slot de input.");
            }
          }
        } else if (kind === "fluid") {
          if (!availabilityAllowed(settings, "fluid")) {
            addRecipeIssue("recipe.input.fluid_tank", "error", index, field + "kind",
              "Esta maquina nao tem tanque de fluido.");
          }
          var fluidId = trimString(row.resource);
          if (!fluidId || !isValidIdentifier(stripTagPrefix(fluidId))) {
            addRecipeIssue("recipe.input.fluid_invalid", "error", index, field + "resource",
              "O fluido precisa ser um id ou tag valido.");
          }
          var amount = toInt(row.amount);
          if (amount === null || amount < 1 || amount > CAPABILITY_MAX) {
            addRecipeIssue("recipe.input.amount", "error", index, field + "amount",
              "A quantidade de fluido precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
          } else {
            fluidInputRows += 1;
            fluidInputTotal += amount;
            if (fluidCapacities !== null
                && amount > Math.max.apply(null, fluidCapacities.concat([0]))) {
              addRecipeIssue("recipe.input.fluid_tank_capacity", "error", index, field + "amount",
                "A quantidade do input de fluido excede a capacidade de um unico tanque.");
            }
          }
        } else if (kind === "entity") {
          var entityId = trimString(row.resource);
          if (!entityId || !isValidIdentifier(entityId)) {
            addRecipeIssue("recipe.input.entity_invalid", "error", index, field + "resource",
              "O id da entidade precisa ser um identifier valido.");
          }
          var entityCount = toInt(row.count);
          if (entityCount === null || entityCount < 1 || entityCount > 99) {
            addRecipeIssue("recipe.input.entity_count", "error", index, field + "count",
              "A quantidade da entidade precisa ser um inteiro de 1 a 99.");
          }
          var spawn = trimString(row.spawn);
          if (spawn && !validSpawnSelector(spawn, settings.symbols)) {
            addRecipeIssue("recipe.input.spawn", "error", index, field + "spawn",
              "spawn precisa ser vazio, um simbolo do pattern, um bloco ou uma tag valida.");
          }
        } else {
          if (!availabilityAllowed(settings, "energy")) {
            addRecipeIssue("recipe.input.energy_unavailable", "error", index, field + "kind",
              "Esta maquina nao tem capabilities.energy.");
          }
          var energy = toInt(row.amount);
          if (energy === null || energy < 1 || energy > CAPABILITY_MAX) {
            addRecipeIssue("recipe.input.energy_invalid", "error", index, field + "amount",
              "energy precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
          } else if (energyCapacity !== null && energy > energyCapacity) {
            addRecipeIssue("recipe.input.energy_capacity", "error", index, field + "amount",
              "energy do input excede a capacidade energetica da maquina.");
          }
        }
      });

      var itemOutputRows = 0;
      var itemOutputTotal = 0;
      var fluidOutputRows = 0;
      var fluidOutputTotal = 0;
      outputRows.forEach(function (row, rowIndex) {
        var field = "output:" + rowIndex + ":";
        if (!isPlainObject(row)) {
          addRecipeIssue("recipe.output.invalid", "error", index, field + "row", "Output invalido.");
          return;
        }
        var kind = trimString(row.kind) || "item";
        if (["item", "fluid", "entity", "energy"].indexOf(kind) === -1) {
          addRecipeIssue("recipe.output.type", "error", index, field + "kind",
            "output.kind precisa ser item, fluid, entity ou energy.");
          return;
        }
        var chance = recipeChance(row.chance);
        if (chance === null || chance < 0 || chance > 1) {
          addRecipeIssue("recipe.output.chance", "error", index, field + "chance",
            "chance precisa ser um numero entre 0 e 1.");
        }
        if (kind === "item") {
          if (!availabilityAllowed(settings, "itemOutput")) {
            addRecipeIssue("recipe.output.item_slot", "error", index, field + "kind",
              "Esta maquina nao tem item_slot de output.");
          }
          var outputId = trimString(row.resource);
          if (!outputId || !isValidIdentifier(outputId)) {
            addRecipeIssue("recipe.output.invalid", "error", index, field + "resource",
              "O id do output precisa ser um identifier valido.");
          }
          var count = toInt(row.count);
          if (count === null || count < 1 || count > 99) {
            addRecipeIssue("recipe.output.count", "error", index, field + "count",
              "A quantidade do output precisa ser um inteiro de 1 a 99.");
          } else {
            itemOutputRows += 1;
            itemOutputTotal += count;
          }
          var components = parseComponents(row.componentsRaw);
          if (components.error) {
            addRecipeIssue("recipe.output.components", "error", index, field + "components", components.error);
          }
        } else if (kind === "fluid") {
          if (!availabilityAllowed(settings, "fluid")) {
            addRecipeIssue("recipe.output.fluid_tank", "error", index, field + "kind",
              "Esta maquina nao tem tanque de fluido.");
          }
          var fluidId = trimString(row.resource);
          if (!fluidId || !isValidIdentifier(stripTagPrefix(fluidId))) {
            addRecipeIssue("recipe.output.fluid_invalid", "error", index, field + "resource",
              "O fluido precisa ser um id ou tag valido.");
          }
          var amount = toInt(row.amount);
          if (amount === null || amount < 1 || amount > CAPABILITY_MAX) {
            addRecipeIssue("recipe.output.amount", "error", index, field + "amount",
              "A quantidade de fluido precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
          } else {
            fluidOutputRows += 1;
            fluidOutputTotal += amount;
          }
        } else if (kind === "entity") {
          var entityId = trimString(row.resource);
          if (!entityId || !isValidIdentifier(entityId)) {
            addRecipeIssue("recipe.output.entity_invalid", "error", index, field + "resource",
              "O id da entidade precisa ser um identifier valido.");
          }
          var entityCount = toInt(row.count);
          if (entityCount === null || entityCount < 1 || entityCount > 99) {
            addRecipeIssue("recipe.output.entity_count", "error", index, field + "count",
              "A quantidade da entidade precisa ser um inteiro de 1 a 99.");
          }
          var spawn = trimString(row.spawn);
          if (spawn && !validSpawnSelector(spawn, settings.symbols)) {
            addRecipeIssue("recipe.output.spawn", "error", index, field + "spawn",
              "spawn precisa ser vazio, um simbolo do pattern, um bloco ou uma tag valida.");
          }
        } else {
          if (!availabilityAllowed(settings, "energy")) {
            addRecipeIssue("recipe.output.energy_unavailable", "error", index, field + "kind",
              "Esta maquina nao tem capabilities.energy.");
          }
          var energy = toInt(row.amount);
          if (energy === null || energy < 1 || energy > CAPABILITY_MAX) {
            addRecipeIssue("recipe.output.energy_invalid", "error", index, field + "amount",
              "energy precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
          } else if (energyCapacity !== null && energy > energyCapacity) {
            addRecipeIssue("recipe.output.energy_capacity", "error", index, field + "amount",
              "energy do output excede a capacidade energetica da maquina.");
          }
        }
      });

      if (inputSlots !== null) {
        if (itemInputRows > inputSlots.length) {
          addRecipeIssue("recipe.input.slots", "error", index, "inputs",
            "Ha mais inputs de item do que slots de input.");
        }
        if (itemInputTotal > sum(inputSlots)) {
          addRecipeIssue("recipe.input.capacity", "error", index, "inputs",
            "A quantidade total de inputs excede a capacidade dos slots de input.");
        }
      }
      if (outputSlots !== null) {
        if (itemOutputRows > outputSlots.length) {
          addRecipeIssue("recipe.output.slots", "error", index, "outputs",
            "Ha mais outputs de item do que slots de output.");
        }
        if (itemOutputTotal > sum(outputSlots)) {
          addRecipeIssue("recipe.output.capacity", "error", index, "outputs",
            "A quantidade total de outputs excede a capacidade dos slots de output.");
        }
      }
      if (fluidCapacities !== null) {
        var fluidCapacity = sum(fluidCapacities);
        if (fluidInputRows > fluidCapacities.length) {
          addRecipeIssue("recipe.input.fluid_slots", "error", index, "inputs",
            "Ha mais inputs de fluido do que tanques.");
        }
        if (fluidInputTotal > fluidCapacity) {
          addRecipeIssue("recipe.input.fluid_capacity", "error", index, "inputs",
            "A quantidade total de fluidos de input excede a capacidade dos tanques.");
        }
        if (fluidOutputRows > fluidCapacities.length) {
          addRecipeIssue("recipe.output.fluid_slots", "error", index, "outputs",
            "Ha mais outputs de fluido do que tanques.");
        }
        if (fluidOutputTotal > fluidCapacity) {
          addRecipeIssue("recipe.output.fluid_capacity", "error", index, "outputs",
            "A quantidade total de fluidos de output excede a capacidade dos tanques.");
        }
        if (fluidInputRows + fluidOutputRows > fluidCapacities.length) {
          addRecipeIssue("recipe.fluid_slots", "error", index, "inputs+outputs",
            "A receita tem mais fluidos de entrada e saida do que tanques disponiveis.");
        }
      }

      var time = toInt(recipe.time);
      if (time === null || time < 1 || time > CAPABILITY_MAX) {
        addRecipeIssue("recipe.time.invalid", "error", index, "time",
          "time precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
      }
    });

    return { issues: issues, ok: ok };
  }

  function createCapabilities() {
    return {
      energy: null,
      fluids: null,
      extras: {}
    };
  }

  function createCapabilityPorts() {
    return {
      item: null,
      energy: null,
      fluid: null,
      extras: {}
    };
  }

  function capabilityPortAvailability(capabilities) {
    var caps = isPlainObject(capabilities) ? capabilities : null;
    return {
      item: true,
      energy: Boolean(caps && isPlainObject(caps.energy)),
      fluid: Boolean(caps && Array.isArray(caps.fluids) && caps.fluids.length > 0)
    };
  }

  function parseCapabilityPortText(value) {
    var text = trimString(value);
    if (!text) {
      return null;
    }
    if (text === "[]") {
      return [];
    }
    return text.split(/[\s,]+/).filter(function (entry) {
      return entry !== "";
    });
  }

  function capabilityPortText(value) {
    if (!Array.isArray(value)) {
      return "";
    }
    return value.length === 0 ? "[]" : value.join(", ");
  }

  function createEnergy() {
    return {
      id: "energy",
      capacity: 100000,
      maxReceive: 10000,
      maxExtract: 0,
      extras: {}
    };
  }

  function createFluidTank() {
    return {
      id: "",
      capacity: 16000,
      maxReceive: 1000,
      maxExtract: 0,
      filter: "",
      extras: {}
    };
  }

  function parseCapabilityNumber(value, fallback) {
    if (value === undefined) {
      return fallback;
    }
    var parsed = toInt(value);
    return parsed === null ? fallback : parsed;
  }

  function parseEnergy(value, notes, path) {
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: path + " inválida foi ignorada."
      });
      return null;
    }
    var energy = createEnergy();
    energy.id = trimString(value.id);
    if (!energy.id) {
      notes.push({
        severity: "warning",
        message: path + " está sem id."
      });
    }
    if (value.capacity === undefined) {
      notes.push({
        severity: "warning",
        message: path + " está sem capacity."
      });
    }
    energy.capacity = parseCapabilityNumber(value.capacity, 0);
    energy.maxReceive = parseCapabilityNumber(value.max_receive, 0);
    energy.maxExtract = parseCapabilityNumber(value.max_extract, 0);
    Object.keys(value).forEach(function (key) {
      if (!ENERGY_KEYS.includes(key)) {
        energy.extras[key] = clone(value[key]);
      }
    });
    return energy;
  }

  function parseFluidTank(value, notes, path) {
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: path + " inválido foi ignorado."
      });
      return null;
    }
    var fluid = createFluidTank();
    fluid.id = trimString(value.id);
    if (!fluid.id) {
      notes.push({
        severity: "warning",
        message: path + " está sem id."
      });
    }
    if (value.capacity === undefined) {
      notes.push({
        severity: "warning",
        message: path + " está sem capacity."
      });
    }
    fluid.capacity = parseCapabilityNumber(value.capacity, 0);
    fluid.maxReceive = parseCapabilityNumber(value.max_receive, 0);
    fluid.maxExtract = parseCapabilityNumber(value.max_extract, 0);
    if (typeof value.filter === "string") {
      fluid.filter = value.filter;
    } else if (value.filter !== undefined) {
      notes.push({
        severity: "warning",
        message: path + ".filter inválido foi ignorado."
      });
    }
    Object.keys(value).forEach(function (key) {
      if (!FLUID_KEYS.includes(key)) {
        fluid.extras[key] = clone(value[key]);
      }
    });
    return fluid;
  }

  function parseFluids(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!Array.isArray(value)) {
      notes.push({
        severity: "warning",
        message: "capabilities.fluids inválido foi ignorado."
      });
      return null;
    }
    var fluids = [];
    value.forEach(function (entry, index) {
      var fluid = parseFluidTank(entry, notes, "capabilities.fluids[" + index + "]");
      if (fluid) {
        fluids.push(fluid);
      }
    });
    return fluids;
  }

  function parseCapabilities(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "capabilities inválidas foram ignoradas."
      });
      return null;
    }
    var capabilities = createCapabilities();
    if (value.energy !== undefined) {
      capabilities.energy = parseEnergy(value.energy, notes, "capabilities.energy");
    }
    capabilities.fluids = parseFluids(value.fluids, notes);
    Object.keys(value).forEach(function (key) {
      if (key !== "energy" && key !== "fluids") {
        capabilities.extras[key] = clone(value[key]);
      }
    });
    return capabilities;
  }

  function parseCapabilityPorts(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "capability_ports inválidas foram ignoradas."
      });
      return null;
    }
    var ports = createCapabilityPorts();
    CAPABILITY_PORT_KEYS.forEach(function (key) {
      if (value[key] === undefined) {
        return;
      }
      if (!Array.isArray(value[key])) {
        notes.push({
          severity: "warning",
          message: "capability_ports." + key + " precisa ser uma lista e foi ignorado."
        });
        return;
      }
      var selectors = [];
      value[key].forEach(function (entry, index) {
        if (typeof entry !== "string") {
          notes.push({
            severity: "warning",
            message: "capability_ports." + key + "[" + index + "] inválido foi ignorado."
          });
          return;
        }
        var selector = entry.trim();
        if (selector) {
          selectors.push(selector);
        }
      });
      ports[key] = selectors;
    });
    Object.keys(value).forEach(function (key) {
      if (CAPABILITY_PORT_KEYS.indexOf(key) === -1) {
        ports.extras[key] = clone(value[key]);
      }
    });
    return ports;
  }

  function buildCapabilityPorts(ports, capabilities) {
    if (!isPlainObject(ports)) {
      return null;
    }
    var filterByCapabilities = capabilities !== undefined;
    var availability = capabilityPortAvailability(capabilities);
    var result = {};
    CAPABILITY_PORT_KEYS.forEach(function (key) {
      if (filterByCapabilities && !availability[key]) {
        return;
      }
      if (!Array.isArray(ports[key])) {
        return;
      }
      result[key] = ports[key].map(function (entry) {
        return trimString(entry);
      }).filter(function (entry) {
        return entry !== "";
      });
    });
    var extras = isPlainObject(ports.extras) ? ports.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (CAPABILITY_PORT_KEYS.indexOf(key) === -1) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function capabilityNumberProblem(value, minimum, label) {
    var parsed = toInt(value);
    if (parsed === null || parsed < minimum || parsed > CAPABILITY_MAX) {
      return label + " precisa ser um inteiro de " + minimum + " a " + CAPABILITY_MAX + ".";
    }
    return "";
  }

  function energyProblem(energy) {
    if (!isPlainObject(energy)) {
      return "Energia inválida.";
    }
    if (!trimString(energy.id)) {
      return "Informe o id da energia.";
    }
    var problem = capabilityNumberProblem(energy.capacity, 1, "capacity da energia");
    if (problem) {
      return problem;
    }
    problem = capabilityNumberProblem(energy.maxReceive, 0, "max_receive da energia");
    if (problem) {
      return problem;
    }
    return capabilityNumberProblem(energy.maxExtract, 0, "max_extract da energia");
  }

  function fluidProblem(fluid) {
    if (!isPlainObject(fluid)) {
      return "Tanque inválido.";
    }
    if (!trimString(fluid.id)) {
      return "Informe o id do tanque.";
    }
    var problem = capabilityNumberProblem(fluid.capacity, 1, "capacity do tanque");
    if (problem) {
      return problem;
    }
    problem = capabilityNumberProblem(fluid.maxReceive, 0, "max_receive do tanque");
    if (problem) {
      return problem;
    }
    problem = capabilityNumberProblem(fluid.maxExtract, 0, "max_extract do tanque");
    if (problem) {
      return problem;
    }
    var filter = trimString(fluid.filter);
    if (filter && !isValidIdentifier(stripTagPrefix(filter))) {
      return "O filtro do tanque precisa ser um fluido ou tag válido.";
    }
    return "";
  }

  function buildEnergy(energy) {
    if (!isPlainObject(energy)) {
      return clone(energy);
    }
    var result = {};
    var id = trimString(energy.id);
    var capacity = toInt(energy.capacity);
    var maxReceive = toInt(energy.maxReceive);
    var maxExtract = toInt(energy.maxExtract);
    if (id) {
      result.id = id;
    }
    if (capacity !== null) {
      result.capacity = capacity;
    }
    if (maxReceive !== null && maxReceive !== 0) {
      result.max_receive = maxReceive;
    }
    if (maxExtract !== null && maxExtract !== 0) {
      result.max_extract = maxExtract;
    }
    var extras = isPlainObject(energy.extras) ? energy.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!ENERGY_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function buildFluidTank(fluid) {
    if (!isPlainObject(fluid)) {
      return clone(fluid);
    }
    var result = {};
    var id = trimString(fluid.id);
    var capacity = toInt(fluid.capacity);
    var maxReceive = toInt(fluid.maxReceive);
    var maxExtract = toInt(fluid.maxExtract);
    var filter = trimString(fluid.filter);
    if (id) {
      result.id = id;
    }
    if (capacity !== null) {
      result.capacity = capacity;
    }
    if (maxReceive !== null && maxReceive !== 0) {
      result.max_receive = maxReceive;
    }
    if (maxExtract !== null && maxExtract !== 0) {
      result.max_extract = maxExtract;
    }
    if (filter) {
      result.filter = filter;
    }
    var extras = isPlainObject(fluid.extras) ? fluid.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!FLUID_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function buildCapabilities(capabilities) {
    if (!isPlainObject(capabilities)) {
      return clone(capabilities);
    }
    var result = {};
    if (capabilities.energy !== null && capabilities.energy !== undefined) {
      result.energy = buildEnergy(capabilities.energy);
    }
    if (Array.isArray(capabilities.fluids)) {
      result.fluids = capabilities.fluids.map(buildFluidTank);
    } else if (capabilities.fluids !== null && capabilities.fluids !== undefined) {
      result.fluids = clone(capabilities.fluids);
    }
    var extras = isPlainObject(capabilities.extras) ? capabilities.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (key !== "energy" && key !== "fluids") {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function emptyControllerTextures(prefix) {
    var textures = {};
    CONTROLLER_FACES.forEach(function (face) {
      textures[face] = prefix ? prefix + face : "";
    });
    return textures;
  }

  function createController() {
    return {
      block: "",
      textures: emptyControllerTextures(""),
      formedTextures: null,
      particle: "",
      lightLevel: 0,
      extras: {}
    };
  }

  function createFormedTextures() {
    return emptyControllerTextures("");
  }

  function hasControllerTexture(textures) {
    if (!isPlainObject(textures)) {
      return false;
    }
    return CONTROLLER_FACES.some(function (face) {
      return Boolean(trimString(textures[face]));
    });
  }

  function parseControllerTextures(value, notes, path) {
    var textures = emptyControllerTextures("");
    if (value === undefined) {
      return textures;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: path + " inválidas foram ignoradas."
      });
      return textures;
    }
    CONTROLLER_FACES.forEach(function (face) {
      if (typeof value[face] === "string") {
        textures[face] = value[face].trim();
      } else if (value[face] !== undefined) {
        notes.push({
          severity: "warning",
          message: path + "." + face + " inválida foi ignorada."
        });
      }
    });
    return textures;
  }

  function parseController(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "controller inválido foi ignorado."
      });
      return null;
    }
    var controller = createController();
    controller.block = trimString(value.block);
    controller.textures = parseControllerTextures(value.textures, notes, "controller.textures");
    if (value.formed_textures !== undefined) {
      var formed = parseControllerTextures(value.formed_textures, notes, "controller.formed_textures");
      controller.formedTextures = hasControllerTexture(formed) ? formed : null;
    }
    if (typeof value.particle === "string") {
      controller.particle = value.particle.trim();
    } else if (value.particle !== undefined) {
      notes.push({
        severity: "warning",
        message: "controller.particle inválido foi ignorado."
      });
    }
    if (value.light_level !== undefined) {
      var lightLevel = toInt(value.light_level);
      if (lightLevel === null || lightLevel < 0 || lightLevel > MAX_LIGHT_LEVEL) {
        notes.push({
          severity: "warning",
          message: "controller.light_level inválido foi ignorado."
        });
      } else {
        controller.lightLevel = lightLevel;
      }
    }
    Object.keys(value).forEach(function (key) {
      if (!CONTROLLER_KEYS.includes(key)) {
        controller.extras[key] = clone(value[key]);
      }
    });
    return controller;
  }

  function buildControllerTextures(textures) {
    var result = {};
    if (!isPlainObject(textures)) {
      return result;
    }
    CONTROLLER_FACES.forEach(function (face) {
      var value = trimString(textures[face]);
      if (value) {
        result[face] = value;
      }
    });
    return result;
  }

  function buildController(controller) {
    if (!isPlainObject(controller)) {
      return clone(controller);
    }
    var result = {};
    var block = trimString(controller.block);
    if (block) {
      result.block = block;
    }
    var textures = buildControllerTextures(controller.textures);
    if (Object.keys(textures).length > 0) {
      result.textures = textures;
    }
    if (isPlainObject(controller.formedTextures)) {
      var formed = buildControllerTextures(controller.formedTextures);
      if (Object.keys(formed).length > 0) {
        result.formed_textures = formed;
      }
    }
    var particle = trimString(controller.particle);
    if (particle) {
      result.particle = particle;
    }
    var lightLevel = toInt(controller.lightLevel);
    if (lightLevel !== null && lightLevel !== 0) {
      result.light_level = lightLevel;
    }
    var extras = isPlainObject(controller.extras) ? controller.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!CONTROLLER_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function createControllerRecipe() {
    return {
      pattern: [],
      key: [],
      extras: {}
    };
  }

  function parseControllerRecipe(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "recipe inválido foi ignorado."
      });
      return null;
    }

    var recipe = createControllerRecipe();
    if (Array.isArray(value.pattern)) {
      recipe.pattern = value.pattern.map(function (row) {
        if (typeof row === "string") {
          return row.replace(/\r$/, "");
        }
        notes.push({
          severity: "warning",
          message: "recipe.pattern contém uma linha inválida."
        });
        return "";
      });
    } else if (value.pattern !== undefined) {
      notes.push({
        severity: "warning",
        message: "recipe.pattern inválido foi ignorado."
      });
    }

    if (isPlainObject(value.key)) {
      Object.keys(value.key).forEach(function (symbol) {
        var item = value.key[symbol];
        if (typeof item !== "string") {
          notes.push({
            severity: "warning",
            message: "recipe.key." + symbol + " precisa ser um item ou tag."
          });
          return;
        }
        recipe.key.push({
          symbol: symbol,
          item: item.trim()
        });
      });
    } else if (value.key !== undefined) {
      notes.push({
        severity: "warning",
        message: "recipe.key inválido foi ignorado."
      });
    }

    Object.keys(value).forEach(function (key) {
      if (!CONTROLLER_RECIPE_KEYS.includes(key)) {
        recipe.extras[key] = clone(value[key]);
      }
    });
    return recipe;
  }

  function buildControllerRecipe(recipe) {
    if (!isPlainObject(recipe)) {
      return clone(recipe);
    }
    var result = {};
    if (Array.isArray(recipe.pattern) && recipe.pattern.length > 0) {
      result.pattern = recipe.pattern.map(function (row) {
        return typeof row === "string" ? row.replace(/\r$/, "") : "";
      });
    }

    var key = {};
    if (Array.isArray(recipe.key)) {
      recipe.key.forEach(function (row) {
        if (!isPlainObject(row)) {
          return;
        }
        var symbol = trimString(row.symbol);
        var item = trimString(row.item);
        if (symbol && item) {
          key[symbol] = item;
        }
      });
    }
    if (Object.keys(key).length > 0) {
      result.key = key;
    }

    var extras = isPlainObject(recipe.extras) ? recipe.extras : {};
    Object.keys(extras).forEach(function (entry) {
      if (!CONTROLLER_RECIPE_KEYS.includes(entry)) {
        result[entry] = clone(extras[entry]);
      }
    });
    return result;
  }

  function controllerRecipeIssues(recipe) {
    var issues = [];
    function add(code, field, message) {
      issues.push({
        code: code,
        field: field,
        message: message
      });
    }

    if (!isPlainObject(recipe)) {
      add("controller_recipe.invalid", "controllerRecipe", "A receita do controller precisa ser um objeto.");
      return issues;
    }

    var rows = Array.isArray(recipe.pattern) ? recipe.pattern : null;
    var width = null;
    var usedSymbols = new Set();
    if (!rows) {
      add("controller_recipe.pattern.invalid", "controllerRecipePattern",
        "recipe.pattern precisa ser uma lista de linhas.");
    } else if (rows.length < 1 || rows.length > CONTROLLER_RECIPE_MAX_SIZE) {
      add("controller_recipe.pattern.rows", "controllerRecipePattern",
        "recipe.pattern precisa ter de 1 a " + CONTROLLER_RECIPE_MAX_SIZE + " linhas.");
    } else {
      rows.forEach(function (row, index) {
        var text = typeof row === "string" ? row : "";
        if (text.length < 1 || text.length > CONTROLLER_RECIPE_MAX_SIZE) {
          add("controller_recipe.pattern.width", "controllerRecipePattern",
            "A linha " + (index + 1) + " do pattern precisa ter de 1 a " +
            CONTROLLER_RECIPE_MAX_SIZE + " caracteres.");
          return;
        }
        if (width === null) {
          width = text.length;
        } else if (text.length !== width) {
          add("controller_recipe.pattern.uniform", "controllerRecipePattern",
            "Todas as linhas do pattern precisam ter a mesma largura.");
        }
        for (var cursor = 0; cursor < text.length; cursor += 1) {
          var symbol = text.charAt(cursor);
          if (symbol !== " ") {
            usedSymbols.add(symbol);
          }
        }
      });
      if (usedSymbols.size === 0) {
        add("controller_recipe.pattern.empty", "controllerRecipePattern",
          "O pattern da receita do controller não pode ser vazio.");
      }
    }

    var keyRows = Array.isArray(recipe.key) ? recipe.key : null;
    var keySymbols = new Set();
    if (!keyRows) {
      add("controller_recipe.key.invalid", "controllerRecipeKey",
        "recipe.key precisa ser uma lista de símbolos.");
    } else if (keyRows.length === 0) {
      add("controller_recipe.key.required", "controllerRecipeKey",
        "Informe ao menos um símbolo em recipe.key.");
    } else {
      keyRows.forEach(function (row, index) {
        if (!isPlainObject(row)) {
          add("controller_recipe.key.row", "controllerRecipeKey",
            "O símbolo " + (index + 1) + " da recipe.key é inválido.");
          return;
        }
        var symbol = trimString(row.symbol);
        var item = trimString(row.item);
        if (!isSingleSymbol(symbol) || symbol === " ") {
          add("controller_recipe.key.symbol", "controllerRecipeKey",
            "O símbolo " + (index + 1) + " precisa ter exatamente um caractere.");
        } else if (keySymbols.has(symbol)) {
          add("controller_recipe.key.duplicate", "controllerRecipeKey",
            "O símbolo \"" + symbol + "\" aparece mais de uma vez em recipe.key.");
        } else {
          keySymbols.add(symbol);
        }
        if (!item) {
          add("controller_recipe.key.item_required", "controllerRecipeKey",
            "Informe o item ou tag do símbolo \"" + (symbol || index + 1) + "\".");
        } else if (!isValidIdentifier(stripTagPrefix(item))) {
          add("controller_recipe.key.item_invalid", "controllerRecipeKey",
            "O item do símbolo \"" + (symbol || index + 1) + "\" precisa ser um item ou tag válido.");
        }
      });
    }

    usedSymbols.forEach(function (symbol) {
      if (!keySymbols.has(symbol)) {
        add("controller_recipe.key.missing", "controllerRecipeKey",
          "O símbolo \"" + symbol + "\" aparece no pattern, mas não tem recipe.key.");
      }
    });
    keySymbols.forEach(function (symbol) {
      if (!usedSymbols.has(symbol)) {
        add("controller_recipe.key.unused", "controllerRecipeKey",
          "O símbolo \"" + symbol + "\" está em recipe.key, mas não aparece no pattern.");
      }
    });

    return issues;
  }

  function controllerRecipeProblem(recipe) {
    var issues = controllerRecipeIssues(recipe);
    return issues.length > 0 ? issues[0].message : "";
  }

  function controllerProblem(controller) {
    if (!isPlainObject(controller)) {
      return "Controller inválido.";
    }
    if (!trimString(controller.block)) {
      return "Informe o bloco do controller.";
    }
    if (!isValidIdentifier(controller.block)) {
      return "O bloco do controller não é um identifier válido.";
    }
    if (!isPlainObject(controller.textures)) {
      return "Informe as seis texturas do controller.";
    }
    for (var index = 0; index < CONTROLLER_FACES.length; index += 1) {
      var face = CONTROLLER_FACES[index];
      var texture = trimString(controller.textures[face]);
      if (!texture) {
        return "Informe a textura " + face + " do controller.";
      }
      if (!isValidIdentifier(texture)) {
        return "A textura " + face + " não é um identifier válido.";
      }
    }
    if (controller.formedTextures !== null && controller.formedTextures !== undefined) {
      if (!isPlainObject(controller.formedTextures)) {
        return "formed_textures precisa ser um objeto.";
      }
      for (var formedIndex = 0; formedIndex < CONTROLLER_FACES.length; formedIndex += 1) {
        var formedFace = CONTROLLER_FACES[formedIndex];
        var formedTexture = trimString(controller.formedTextures[formedFace]);
        if (formedTexture && !isValidIdentifier(formedTexture)) {
          return "A textura formada " + formedFace + " não é um identifier válido.";
        }
      }
    }
    var particle = trimString(controller.particle);
    if (particle && !isValidIdentifier(particle)) {
      return "A partícula do controller não é um identifier válido.";
    }
    var lightLevel = toInt(controller.lightLevel);
    if (lightLevel === null || lightLevel < 0 || lightLevel > MAX_LIGHT_LEVEL) {
      return "light_level precisa ser um inteiro de 0 a " + MAX_LIGHT_LEVEL + ".";
    }
    return "";
  }

  function createGui() {
    return {
      texture: "",
      width: GUI_DEFAULT_WIDTH,
      height: GUI_DEFAULT_HEIGHT,
      textureWidth: GUI_DEFAULT_TEXTURE_SIZE,
      textureHeight: GUI_DEFAULT_TEXTURE_SIZE,
      elements: [],
      extras: {}
    };
  }

  function createGuiElement(type) {
    var element = {
      type: GUI_ELEMENT_TYPES.indexOf(type) === -1 ? "title" : type,
      x: 0,
      y: 0,
      width: 0,
      height: 0,
      text: "",
      slot: "",
      capability: "",
      color: "",
      shadow: true,
      direction: "up",
      u: -1,
      v: -1,
      extras: {}
    };
    if (element.type === "title") {
      element.x = 8;
      element.y = 4;
      element.color = "404040";
      element.shadow = false;
    } else if (element.type === "text") {
      element.x = 8;
      element.y = 75;
      element.color = "404040";
      element.shadow = false;
    } else if (element.type === "player_inventory") {
      element.x = 8;
      element.y = 84;
      element.color = "404040";
      element.shadow = false;
    } else if (element.type === "slot") {
      element.x = 8;
      element.y = 16;
      element.width = GUI_SLOT_SIZE;
      element.height = GUI_SLOT_SIZE;
    } else if (element.type === "energy_bar") {
      element.x = 158;
      element.y = 10;
      element.width = 8;
      element.height = 64;
      element.capability = "energy";
      element.color = "0xFFFF0000";
    } else if (element.type === "fluid_bar") {
      element.x = 148;
      element.y = 10;
      element.width = 8;
      element.height = 64;
    } else if (element.type === "progress_bar") {
      element.x = 138;
      element.y = 10;
      element.width = 8;
      element.height = 64;
      element.direction = "up";
      element.color = "0xFF00AA00";
    } else if (element.type === "arrow") {
      element.x = 62;
      element.y = 35;
      element.width = 22;
      element.height = 16;
      element.direction = "right";
    } else if (element.type === "entity") {
      element.x = 96;
      element.y = 18;
      element.width = GUI_ENTITY_CELL_SIZE * 2;
      element.height = GUI_ENTITY_CELL_SIZE * 2;
    }
    return element;
  }

  function parseGuiInt(value, fallback, notes, path) {
    if (value === undefined) {
      return fallback;
    }
    var parsed = toInt(value);
    if (parsed === null) {
      notes.push({
        severity: "warning",
        message: path + " inválido foi ignorado."
      });
      return fallback;
    }
    return parsed;
  }

  function parseGuiElement(value, notes, index) {
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "gui.elements[" + index + "] não é um objeto e foi ignorado."
      });
      return null;
    }
    var type = trimString(value.type);
    if (GUI_ELEMENT_TYPES.indexOf(type) === -1) {
      notes.push({
        severity: "warning",
        message: "gui.elements[" + index + "].type inválido foi ignorado."
      });
      return null;
    }
    var element = createGuiElement(type);
    element.x = parseGuiInt(value.x, element.x, notes, "gui.elements[" + index + "].x");
    element.y = parseGuiInt(value.y, element.y, notes, "gui.elements[" + index + "].y");
    element.width = parseGuiInt(value.width, element.width, notes, "gui.elements[" + index + "].width");
    element.height = parseGuiInt(value.height, element.height, notes, "gui.elements[" + index + "].height");
    element.u = parseGuiInt(value.u, element.u, notes, "gui.elements[" + index + "].u");
    element.v = parseGuiInt(value.v, element.v, notes, "gui.elements[" + index + "].v");
    if (value.text !== undefined) {
      if (typeof value.text === "string") {
        element.text = value.text.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].text inválido foi ignorado."
        });
      }
    }
    if (value.slot !== undefined) {
      if (typeof value.slot === "string") {
        element.slot = value.slot.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].slot inválido foi ignorado."
        });
      }
    }
    if (value.capability !== undefined) {
      if (typeof value.capability === "string") {
        element.capability = value.capability.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].capability inválido foi ignorado."
        });
      }
    }
    if (value.color !== undefined) {
      if (typeof value.color === "string") {
        element.color = value.color.trim();
      } else if (typeof value.color === "number") {
        element.color = value.color;
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].color inválido foi ignorado."
        });
      }
    }
    if (value.shadow !== undefined) {
      if (typeof value.shadow === "boolean") {
        element.shadow = value.shadow;
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].shadow inválido foi ignorado."
        });
      }
    }
    if (value.direction !== undefined) {
      if (typeof value.direction === "string") {
        element.direction = value.direction.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements[" + index + "].direction inválido foi ignorado."
        });
      }
    }
    Object.keys(value).forEach(function (key) {
      if (!GUI_ELEMENT_KEYS.includes(key)) {
        element.extras[key] = clone(value[key]);
      }
    });
    return element;
  }

  function parseGui(value, notes) {
    if (value === undefined) {
      return null;
    }
    if (!isPlainObject(value)) {
      notes.push({
        severity: "warning",
        message: "gui inválido foi ignorado."
      });
      return null;
    }
    var gui = createGui();
    if (value.texture !== undefined) {
      if (typeof value.texture === "string") {
        gui.texture = value.texture.trim();
      } else {
        notes.push({
          severity: "warning",
          message: "gui.texture inválido foi ignorado."
        });
      }
    }
    gui.width = parseGuiInt(value.width, gui.width, notes, "gui.width");
    gui.height = parseGuiInt(value.height, gui.height, notes, "gui.height");
    gui.textureWidth = parseGuiInt(value.texture_width, gui.textureWidth, notes, "gui.texture_width");
    gui.textureHeight = parseGuiInt(value.texture_height, gui.textureHeight, notes, "gui.texture_height");
    if (value.elements !== undefined) {
      if (Array.isArray(value.elements)) {
        value.elements.forEach(function (rawElement, index) {
          var element = parseGuiElement(rawElement, notes, index);
          if (element) {
            gui.elements.push(element);
          }
        });
      } else {
        notes.push({
          severity: "warning",
          message: "gui.elements inválido foi ignorado."
        });
      }
    }
    Object.keys(value).forEach(function (key) {
      if (!GUI_KEYS.includes(key)) {
        gui.extras[key] = clone(value[key]);
      }
    });
    return gui;
  }

  function hasGuiColor(value) {
    if (typeof value === "number") {
      return Number.isInteger(value);
    }
    return typeof value === "string" && value.trim() !== "";
  }

  function isValidGuiColor(value) {
    if (typeof value === "number") {
      return Number.isInteger(value);
    }
    if (typeof value !== "string") {
      return false;
    }
    var text = value.trim();
    if (!text) {
      return true;
    }
    if (/^-?\d+$/.test(text)) {
      return Number.isSafeInteger(Number(text));
    }
    if (text.startsWith("#")) {
      text = text.slice(1);
    } else if (text.startsWith("0x") || text.startsWith("0X")) {
      text = text.slice(2);
    }
    if (text.length === 6) {
      text = "FF" + text;
    }
    return /^[0-9a-fA-F]{8}$/.test(text);
  }

  function buildGuiElement(element) {
    if (!isPlainObject(element)) {
      return clone(element);
    }
    var type = trimString(element.type);
    if (GUI_ELEMENT_TYPES.indexOf(type) === -1) {
      return clone(element);
    }
    var result = { type: type };
    var x = toIntOr(element.x, 0);
    var y = toIntOr(element.y, 0);
    var width = toIntOr(element.width, 0);
    var height = toIntOr(element.height, 0);
    var u = toIntOr(element.u, -1);
    var v = toIntOr(element.v, -1);
    if (x !== 0) {
      result.x = x;
    }
    if (y !== 0) {
      result.y = y;
    }
    if (width > 0) {
      result.width = width;
    }
    if (height > 0) {
      result.height = height;
    }
    var text = trimString(element.text);
    var slot = trimString(element.slot);
    var capability = trimString(element.capability);
    if (text) {
      result.text = text;
    }
    if (slot) {
      result.slot = slot;
    }
    if (capability) {
      result.capability = capability;
    }
    if (hasGuiColor(element.color)) {
      result.color = typeof element.color === "number" ? element.color : element.color.trim();
    }
    if (element.shadow === false) {
      result.shadow = false;
    }
    if (element.direction && element.direction !== "up") {
      result.direction = element.direction;
    }
    if (u >= 0) {
      result.u = u;
    }
    if (v >= 0) {
      result.v = v;
    }
    var extras = isPlainObject(element.extras) ? element.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!GUI_ELEMENT_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function buildGui(gui) {
    if (!isPlainObject(gui)) {
      return clone(gui);
    }
    var result = {};
    var texture = trimString(gui.texture);
    if (texture) {
      result.texture = texture;
    }
    var width = toIntOr(gui.width, GUI_DEFAULT_WIDTH);
    var height = toIntOr(gui.height, GUI_DEFAULT_HEIGHT);
    var textureWidth = toIntOr(gui.textureWidth, GUI_DEFAULT_TEXTURE_SIZE);
    var textureHeight = toIntOr(gui.textureHeight, GUI_DEFAULT_TEXTURE_SIZE);
    if (width !== GUI_DEFAULT_WIDTH) {
      result.width = width;
    }
    if (height !== GUI_DEFAULT_HEIGHT) {
      result.height = height;
    }
    if (textureWidth !== GUI_DEFAULT_TEXTURE_SIZE) {
      result.texture_width = textureWidth;
    }
    if (textureHeight !== GUI_DEFAULT_TEXTURE_SIZE) {
      result.texture_height = textureHeight;
    }
    if (Array.isArray(gui.elements) && gui.elements.length > 0) {
      result.elements = gui.elements.map(buildGuiElement);
    }
    var extras = isPlainObject(gui.extras) ? gui.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!GUI_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function guiInside(gui, x, y, width, height) {
    return x >= 0 && y >= 0 && x + width <= gui.width && y + height <= gui.height;
  }

  function guiElementProblem(element, gui) {
    if (!isPlainObject(element)) {
      return "Elemento de GUI inválido.";
    }
    var type = trimString(element.type);
    if (GUI_ELEMENT_TYPES.indexOf(type) === -1) {
      return "Tipo de elemento de GUI inválido.";
    }
    var x = toInt(element.x);
    var y = toInt(element.y);
    var width = toIntOr(element.width, 0);
    var height = toIntOr(element.height, 0);
    if (x === null || y === null) {
      return "x/y do elemento de GUI precisam ser inteiros.";
    }
    if (width < 0 || width > GUI_MAX_SIZE || height < 0 || height > GUI_MAX_SIZE) {
      return "width/height do elemento de GUI precisam estar entre 0 e " + GUI_MAX_SIZE + ".";
    }
    if (GUI_DIRECTIONS.indexOf(trimString(element.direction)) === -1) {
      return "direction do elemento de GUI é inválido.";
    }
    if (!isValidGuiColor(element.color)) {
      return "color do elemento de GUI é inválido.";
    }
    if (typeof element.shadow !== "boolean") {
      return "shadow do elemento de GUI precisa ser booleano.";
    }
    if (toInt(element.u) === null || toInt(element.v) === null) {
      return "u/v do elemento de GUI precisam ser inteiros.";
    }
    if (type === "slot") {
      if (!trimString(element.slot)) {
        return "Elemento slot precisa de um id de item_slots.";
      }
      if (!guiInside(gui, x, y, GUI_SLOT_SIZE, GUI_SLOT_SIZE)) {
        return "O slot " + element.slot + " excede os limites da GUI.";
      }
    } else if (type === "player_inventory") {
      if (!guiInside(gui, x, y, GUI_PLAYER_WIDTH, GUI_PLAYER_HEIGHT)) {
        return "player_inventory excede os limites da GUI.";
      }
    } else if (type === "energy_bar" || type === "fluid_bar"
        || type === "progress_bar" || type === "arrow" || type === "entity") {
      if (width <= 0 || height <= 0) {
        return "Elementos size-dependentes de GUI precisam de width e height positivos.";
      }
      if (type === "entity"
          && (width < GUI_ENTITY_CELL_SIZE || height < GUI_ENTITY_CELL_SIZE
            || width % GUI_ENTITY_CELL_SIZE !== 0 || height % GUI_ENTITY_CELL_SIZE !== 0)) {
        return "O elemento entity precisa de width/height múltiplos de "
          + GUI_ENTITY_CELL_SIZE + " (mínimo " + GUI_ENTITY_CELL_SIZE + ").";
      }
      if (!guiInside(gui, x, y, width, height)) {
        return "O elemento " + type + " excede os limites da GUI.";
      }
    }
    return "";
  }

  function guiProblem(gui, state) {
    if (!isPlainObject(gui)) {
      return "GUI inválida.";
    }
    var width = toInt(gui.width);
    var height = toInt(gui.height);
    var textureWidth = toInt(gui.textureWidth);
    var textureHeight = toInt(gui.textureHeight);
    if (width === null || width < 1 || width > GUI_MAX_SIZE) {
      return "A largura da GUI precisa estar entre 1 e " + GUI_MAX_SIZE + ".";
    }
    if (height === null || height < 1 || height > GUI_MAX_SIZE) {
      return "A altura da GUI precisa estar entre 1 e " + GUI_MAX_SIZE + ".";
    }
    if (textureWidth === null || textureWidth < 1 || textureWidth > GUI_MAX_SIZE
        || textureHeight === null || textureHeight < 1 || textureHeight > GUI_MAX_SIZE) {
      return "texture_width/texture_height precisam estar entre 1 e " + GUI_MAX_SIZE + ".";
    }
    var texture = trimString(gui.texture);
    if (texture && !isValidIdentifier(texture)) {
      return "A textura da GUI precisa ser um identifier válido.";
    }
    if (!Array.isArray(gui.elements)) {
      return "gui.elements precisa ser uma lista.";
    }
    var normalizedGui = {
      width: width,
      height: height
    };
    for (var index = 0; index < gui.elements.length; index += 1) {
      var elementProblem = guiElementProblem(gui.elements[index], normalizedGui);
      if (elementProblem) {
        return elementProblem;
      }
    }
    if (gui.elements.length === 0 || !isPlainObject(state)) {
      return "";
    }
    var itemSlots = Array.isArray(state.itemSlots) ? state.itemSlots : [];
    var slotElements = gui.elements.filter(function (element) {
      return trimString(element.type) === "slot";
    });
    if (itemSlots.length === 0 && slotElements.length > 0) {
      return "GUI customizada com elementos slot exige item_slots.";
    }
    var positionedSlots = itemSlots.filter(function (slot) {
      return isPlainObject(slot) && toInt(slot.x) !== null && toInt(slot.y) !== null;
    });
    if (slotElements.length > 0 && positionedSlots.length > 0) {
      return "Use x/y em item_slots ou elementos slot, não os dois.";
    }
    var playerInventories = gui.elements.filter(function (element) {
      return trimString(element.type) === "player_inventory";
    });
    if (playerInventories.length !== 1) {
      return "GUI customizada exige exatamente um player_inventory.";
    }
    var energy = isPlainObject(state.capabilities) && isPlainObject(state.capabilities.energy)
      ? state.capabilities.energy
      : null;
    var tanks = isPlainObject(state.capabilities) && Array.isArray(state.capabilities.fluids)
      ? state.capabilities.fluids
      : [];
    var slotIds = {};
    itemSlots.forEach(function (slot) {
      if (isPlainObject(slot) && trimString(slot.id)) {
        slotIds[trimString(slot.id)] = true;
      }
    });
    var usedSlotElements = {};
    for (var cursor = 0; cursor < gui.elements.length; cursor += 1) {
      var element = gui.elements[cursor];
      var type = trimString(element.type);
      if (type === "slot") {
        var slotId = trimString(element.slot);
        if (usedSlotElements[slotId]) {
          return "O item_slots.id " + slotId + " aparece em mais de um elemento slot.";
        }
        usedSlotElements[slotId] = true;
        if (!slotIds[slotId]) {
          return "O elemento slot referencia um item_slots.id desconhecido: " + slotId + ".";
        }
      } else if (type === "energy_bar") {
        if (!energy) {
          return "energy_bar exige capabilities.energy.";
        }
        if (trimString(element.capability) !== trimString(energy.id)) {
          return "energy_bar.capability precisa ser igual a capabilities.energy.id.";
        }
      } else if (type === "fluid_bar") {
        var knownTank = tanks.some(function (tank) {
          return isPlainObject(tank) && trimString(tank.id) === trimString(element.capability);
        });
        if (!knownTank) {
          return "fluid_bar.capability precisa ser o id de um tanque.";
        }
      } else if ((type === "progress_bar" || type === "arrow" || type === "entity")
          && state.type !== "recipe") {
        return type + " só funciona em type=recipe.";
      }
    }
    for (var slotIndex = 0; slotIndex < positionedSlots.length; slotIndex += 1) {
      var positioned = positionedSlots[slotIndex];
      if (!guiInside(normalizedGui, toInt(positioned.x), toInt(positioned.y), GUI_SLOT_SIZE, GUI_SLOT_SIZE)) {
        return "O slot " + trimString(positioned.id) + " excede os limites da GUI.";
      }
    }
    return "";
  }

  function roleIsValid(role) {
    return ITEM_SLOT_ROLES.indexOf(trimString(role)) !== -1;
  }

  function facesProblem(faces) {
    if (!isPlainObject(faces)) {
      return "faces precisa ser um objeto.";
    }
    var keys = ["insert", "extract"];
    for (var index = 0; index < keys.length; index += 1) {
      var key = keys[index];
      if (faces[key] === undefined) {
        continue;
      }
      if (!Array.isArray(faces[key])) {
        return "faces." + key + " precisa ser uma lista.";
      }
      for (var cursor = 0; cursor < faces[key].length; cursor += 1) {
        if (DIRECTIONS.indexOf(faces[key][cursor]) === -1) {
          return "faces." + key + " contém uma direção inválida.";
        }
      }
    }
    return "";
  }

  function slotProblem(slot) {
    if (!isPlainObject(slot)) {
      return "Slot inválido.";
    }
    if (!trimString(slot.id)) {
      return "Informe o id do slot.";
    }
    if (!roleIsValid(slot.role)) {
      return "Role inválido.";
    }
    var filter = trimString(slot.role) === "output" ? "" : trimString(slot.filter);
    if (filter && !isValidIdentifier(stripTagPrefix(filter))) {
      return "Filtro precisa ser um item ou tag válido.";
    }
    var limit = toInt(slot.limit);
    if (limit === null || limit < 1 || limit > 64) {
      return "Limit precisa ser um inteiro de 1 a 64.";
    }
    var hasX = slot.x !== null && slot.x !== undefined;
    var hasY = slot.y !== null && slot.y !== undefined;
    if (hasX !== hasY) {
      return "Informe x e y juntos.";
    }
    if (hasX && (toInt(slot.x) === null || toInt(slot.y) === null)) {
      return "x e y precisam ser inteiros.";
    }
    if (slot.faces !== null && slot.faces !== undefined) {
      var problem = facesProblem(slot.faces);
      if (problem) {
        return problem;
      }
    }
    return "";
  }

  function buildItemSlot(slot) {
    if (!isPlainObject(slot)) {
      return clone(slot);
    }
    var result = {};
    var id = trimString(slot.id);
    var role = trimString(slot.role);
    var filter = trimString(slot.filter);
    var limit = toInt(slot.limit);
    var x = toInt(slot.x);
    var y = toInt(slot.y);

    if (id) {
      result.id = id;
    }
    if (role) {
      result.role = role;
    }
    if (filter && role !== "output") {
      result.filter = filter;
    }
    if (limit !== null && limit !== 64) {
      result.limit = limit;
    }
    if (x !== null) {
      result.x = x;
    }
    if (y !== null) {
      result.y = y;
    }
    if (isPlainObject(slot.faces)) {
      result.faces = clone(slot.faces);
    }

    var extras = isPlainObject(slot.extras) ? slot.extras : {};
    Object.keys(extras).forEach(function (key) {
      if (!ITEM_SLOT_KEYS.includes(key)) {
        result[key] = clone(extras[key]);
      }
    });
    return result;
  }

  function createDefaultState() {
    return {
      schemaVersion: SCHEMA_VERSION,
      namespace: "infinity_nexus_multiblock",
      path: "minha_maquina",
      type: "storage",
      tier: 1,
      priority: 0,
      inventoryRows: 3,
      itemSlots: null,
      capabilities: null,
      capabilityPorts: null,
      controllerRecipe: null,
      gui: null,
      recipes: [],
      inheritance: createInheritance(),
      anchor: "C",
      layers: [
        ["III", "ICI", "III"]
      ],
      matchers: {
        I: {
          kind: "block",
          block: "minecraft:iron_block",
          tag: "",
          state: [],
          raw: null
        }
      },
      extras: {},
      patternExtras: {}
    };
  }

  function createExampleState() {
    return {
      schemaVersion: SCHEMA_VERSION,
      namespace: "infinity_nexus_multiblock",
      path: "alloy_smelter",
      type: "recipe",
      tier: 1,
      priority: 0,
      inventoryRows: 3,
      itemSlots: null,
      capabilities: null,
      capabilityPorts: null,
      controllerRecipe: null,
      gui: null,
      recipes: [],
      inheritance: createInheritance(),
      anchor: "C",
      layers: [
        ["III", "III", "III"],
        ["III", "ICI", "III"],
        ["III", "III", "III"]
      ],
      matchers: {
        I: {
          kind: "block",
          block: "minecraft:iron_block",
          tag: "",
          state: [],
          raw: null
        }
      },
      extras: {},
      patternExtras: {}
    };
  }

  function createEmptyState() {
    var state = createDefaultState();
    state.matchers = {};
    state.layers = [["   ", "   ", "   "]];
    return state;
  }

  function parseMatcherValue(value) {
    if (typeof value === "string") {
      var text = value.trim();
      if (text.startsWith("#")) {
        return {
          kind: "tag",
          block: "",
          tag: text.slice(1),
          state: [],
          raw: null
        };
      }
      return {
        kind: "block",
        block: text,
        tag: "",
        state: [],
        raw: null
      };
    }

    if (isPlainObject(value)) {
      var hasBlock = typeof value.block === "string";
      var hasTag = typeof value.tag === "string";
      if (hasBlock === hasTag) {
        return {
          kind: "invalid",
          block: hasBlock ? value.block : "",
          tag: hasTag ? value.tag : "",
          state: [],
          raw: clone(value)
        };
      }
      if (hasTag) {
        if (value.state !== undefined) {
          return {
            kind: "invalid",
            block: "",
            tag: value.tag,
            state: [],
            raw: clone(value)
          };
        }
        return {
          kind: "tag",
          block: "",
          tag: value.tag,
          state: [],
          raw: null
        };
      }
      if (value.state !== undefined && !isPlainObject(value.state)) {
        return {
          kind: "invalid",
          block: value.block,
          tag: "",
          state: [],
          raw: clone(value)
        };
      }
      var rows = [];
      if (isPlainObject(value.state)) {
        Object.keys(value.state).forEach(function (key) {
          var rawValue = value.state[key];
          rows.push({
            key: key,
            value: typeof rawValue === "string" ? rawValue : String(rawValue)
          });
        });
      }
      return {
        kind: rows.length > 0 ? "state" : "block",
        block: value.block,
        tag: "",
        state: rows,
        raw: null
      };
    }

    return {
      kind: "invalid",
      block: "",
      tag: "",
      state: [],
      raw: clone(value)
    };
  }

  function parseMatcherInput(value) {
    if (typeof value === "string") {
      var text = value.trim();
      if (text.charAt(0) === "{") {
        try {
          var parsed = JSON.parse(text);
          if (isPlainObject(parsed)) {
            return parseMatcherValue(parsed);
          }
        } catch (error) {
          // Texto incompleto durante a digitação continua como bloco literal.
        }
      }
    }
    return parseMatcherValue(value);
  }

  function normalizeLayers(value, notes) {
    var source = Array.isArray(value) ? value : [];
    if (!Array.isArray(value)) {
      notes.push({
        severity: "warning",
        message: "pattern.layers ausente ou inválido; foi criada uma grade 3×3×1 vazia."
      });
      return [["   ", "   ", "   "]];
    }
    if (source.length === 0) {
      notes.push({
        severity: "warning",
        message: "pattern.layers estava vazio; foi criada uma camada 3×3 vazia."
      });
      return [["   ", "   ", "   "]];
    }

    var changed = false;
    var layers = source.map(function (layer) {
      if (!Array.isArray(layer)) {
        changed = true;
        return [];
      }
      return layer.map(function (row) {
        if (typeof row === "string") {
          return row;
        }
        changed = true;
        return row === null || row === undefined ? "" : String(row);
      });
    });

    var maxRows = Math.max(1, layers.reduce(function (max, layer) {
      return Math.max(max, layer.length);
    }, 0));
    var maxWidth = 1;
    layers.forEach(function (layer) {
      layer.forEach(function (row) {
        maxWidth = Math.max(maxWidth, row.length);
      });
    });

    var normalized = layers.map(function (layer) {
      var rows = [];
      for (var rowIndex = 0; rowIndex < maxRows; rowIndex += 1) {
        var row = rowIndex < layer.length ? layer[rowIndex] : "";
        if (row.length < maxWidth) {
          row += " ".repeat(maxWidth - row.length);
          changed = true;
        }
        rows.push(row);
      }
      if (layer.length !== maxRows) {
        changed = true;
      }
      return rows;
    });

    if (changed) {
      notes.push({
        severity: "info",
        message: "O pattern foi normalizado para " + maxWidth + "×" + maxRows + "×" + normalized.length + "."
      });
    }

    return normalized;
  }

  function importDefinition(raw) {
    if (!isPlainObject(raw)) {
      throw new Error("O JSON raiz precisa ser um objeto.");
    }

    var notes = [];
    var state = createEmptyState();
    state.schemaVersion = SCHEMA_VERSION;
    state.matchers = {};

    if (raw.schema_version !== SCHEMA_VERSION) {
      notes.push({
        severity: "warning",
        message: "schema_version diferente de 2 foi normalizado para 2."
      });
    }

    var rawId = typeof raw.id === "string" ? raw.id.trim() : "";
    if (!rawId) {
      state.namespace = "";
      state.path = "";
      notes.push({
        severity: "error",
        message: "O JSON importado não tem um id válido."
      });
    } else {
      var separator = rawId.indexOf(":");
      if (separator > 0) {
        state.namespace = rawId.slice(0, separator);
        state.path = rawId.slice(separator + 1);
      } else {
        state.namespace = "";
        state.path = rawId;
        notes.push({
          severity: "warning",
          message: "O id importado não tem namespace. Informe namespace e path."
        });
      }
    }

    if (raw.type === "recipe" || raw.type === "storage") {
      state.type = raw.type;
    } else {
      state.type = "storage";
      notes.push({
        severity: "warning",
        message: "type inválido foi normalizado para storage."
      });
    }

    if (raw.recipe_type !== undefined) {
      notes.push({
        severity: "warning",
        message: "recipe_type foi removido no schema 2 e foi ignorado."
      });
    }
    if (raw.controller !== undefined) {
      notes.push({
        severity: "warning",
        message: "controller dinâmico foi removido no schema 2 e foi ignorado."
      });
    }
    state.tier = toIntOr(raw.tier, 1);
    state.priority = toIntOr(raw.priority, 0);
    state.inventoryRows = toIntOr(raw.inventory_rows, 3);
    state.itemSlots = parseItemSlots(raw.item_slots, notes);
    state.capabilities = parseCapabilities(raw.capabilities, notes);
    state.capabilityPorts = parseCapabilityPorts(raw.capability_ports, notes);
    state.controllerRecipe = parseControllerRecipe(raw.recipe, notes);
    state.gui = parseGui(raw.gui, notes);
    state.inheritance = parseInheritance(raw, notes);

    var pattern = isPlainObject(raw.pattern) ? raw.pattern : null;
    if (!pattern) {
      notes.push({
        severity: "warning",
        message: "pattern ausente no JSON importado; a grade começou vazia."
      });
    }

    var anchor = pattern && pattern.anchor !== undefined ? pattern.anchor : "C";
    if (isSingleSymbol(anchor) && anchor !== " ") {
      state.anchor = anchor;
    } else {
      state.anchor = "C";
      notes.push({
        severity: "warning",
        message: "pattern.anchor inválido foi normalizado para C."
      });
    }

    state.layers = normalizeLayers(pattern ? pattern.layers : undefined, notes);

    if (pattern && pattern.matchers !== undefined) {
      if (isPlainObject(pattern.matchers)) {
        Object.keys(pattern.matchers).forEach(function (symbol) {
          state.matchers[symbol] = parseMatcherValue(pattern.matchers[symbol]);
        });
      } else {
        notes.push({
          severity: "warning",
          message: "pattern.matchers inválido foi ignorado."
        });
      }
    }

    if (pattern) {
      Object.keys(pattern).forEach(function (key) {
        if (!KNOWN_PATTERN_KEYS.includes(key)) {
          state.patternExtras[key] = clone(pattern[key]);
        }
      });
    }

    Object.keys(raw).forEach(function (key) {
      if (!KNOWN_ROOT_KEYS.includes(key)) {
        state.extras[key] = clone(raw[key]);
      }
    });

    return {
      state: state,
      notes: notes
    };
  }

  function buildMatcherValue(matcher) {
    if (!matcher || typeof matcher !== "object") {
      return null;
    }
    if (matcher.kind === "invalid") {
      return clone(matcher.raw);
    }
    if (matcher.kind === "tag") {
      var tag = stripTagPrefix(matcher.tag);
      return "#" + tag;
    }
    if (matcher.kind === "state") {
      var stateValues = {};
      var rows = Array.isArray(matcher.state) ? matcher.state : [];
      rows.forEach(function (row) {
        var key = trimString(row && row.key);
        if (key) {
          stateValues[key] = typeof row.value === "string" ? row.value : String(row.value === undefined ? "" : row.value);
        }
      });
      if (Object.keys(stateValues).length === 0) {
        return trimString(matcher.block);
      }
      return {
        block: trimString(matcher.block),
        state: stateValues
      };
    }
    return trimString(matcher.block);
  }

  function buildDefinition(state) {
    var namespace = trimString(state.namespace);
    var path = trimString(state.path);
    var id = namespace + ":" + path;
    var definition = {
      schema_version: SCHEMA_VERSION,
      id: id,
      type: state.type === "recipe" ? "recipe" : "storage"
    };

    var inheritance = buildInheritance(state.inheritance);
    Object.keys(inheritance).forEach(function (key) {
      definition[key] = inheritance[key];
    });

    var tier = toIntOr(state.tier, 1);
    var priority = toIntOr(state.priority, 0);
    var inventoryRows = toIntOr(state.inventoryRows, 3);
    var extras = isPlainObject(state.extras) ? state.extras : {};

    if (tier !== 1) {
      definition.tier = tier;
    }
    if (priority !== 0) {
      definition.priority = priority;
    }
    if (inventoryRows !== 3) {
      definition.inventory_rows = inventoryRows;
    }
    if (Array.isArray(state.itemSlots)) {
      definition.item_slots = state.itemSlots.map(buildItemSlot);
    }
    if (isPlainObject(state.capabilities)) {
      definition.capabilities = buildCapabilities(state.capabilities);
    }
    if (isPlainObject(state.capabilityPorts)) {
      var capabilityPorts = buildCapabilityPorts(state.capabilityPorts, state.capabilities);
      if (capabilityPorts && Object.keys(capabilityPorts).length > 0) {
        definition.capability_ports = capabilityPorts;
      }
    }
    if (isPlainObject(state.controllerRecipe)) {
      var controllerRecipe = buildControllerRecipe(state.controllerRecipe);
      if (Object.keys(controllerRecipe).length > 0) {
        definition.recipe = controllerRecipe;
      }
    }
    if (isPlainObject(state.gui)) {
      definition.gui = buildGui(state.gui);
    }

    Object.keys(extras).forEach(function (key) {
      if (KNOWN_ROOT_KEYS.includes(key)) {
        return;
      }
      definition[key] = clone(extras[key]);
    });

    var matchers = {};
    var matcherSource = isPlainObject(state.matchers) ? state.matchers : {};
    Object.keys(matcherSource).sort().forEach(function (symbol) {
      matchers[symbol] = buildMatcherValue(matcherSource[symbol]);
    });

    var pattern = {
      anchor: state.anchor,
      layers: clone(Array.isArray(state.layers) ? state.layers : []),
      matchers: matchers
    };
    var patternExtras = isPlainObject(state.patternExtras) ? state.patternExtras : {};
    Object.keys(patternExtras).forEach(function (key) {
      if (!KNOWN_PATTERN_KEYS.includes(key)) {
        pattern[key] = clone(patternExtras[key]);
      }
    });
    definition.pattern = pattern;

    return definition;
  }

  function issue(code, severity, step, field, message) {
    return {
      code: code,
      severity: severity,
      step: step,
      field: field || "",
      message: message
    };
  }

  function validateState(state) {
    var issues = [];
    var checks = [];
    var errorCodes = new Set();

    function add(code, severity, step, field, message) {
      issues.push(issue(code, severity, step, field, message));
      if (severity === "error") {
        errorCodes.add(code);
      }
    }

    var schema = toInt(state.schemaVersion);
    if (schema !== SCHEMA_VERSION) {
      add("schema.unsupported", "error", "identity", "schemaVersion",
        "schema_version precisa ser 2.");
    }

    var namespace = trimString(state.namespace);
    var path = trimString(state.path);
    if (!namespace) {
      add("id.namespace.required", "error", "identity", "namespace",
        "Informe o namespace do id.");
    } else if (!isValidNamespace(namespace)) {
      add("id.namespace.invalid", "error", "identity", "namespace",
        "Namespace inválido. Use apenas a-z, 0-9, _, ., -.");
    }
    if (!path) {
      add("id.path.required", "error", "identity", "path",
        "Informe o path do id.");
    } else if (!isValidPath(path)) {
      add("id.path.invalid", "error", "identity", "path",
        "Path inválido. Use apenas a-z, 0-9, _, ., /, -.");
    }

    var typeIsValid = state.type === "recipe" || state.type === "storage";
    if (!typeIsValid) {
      add("type.invalid", "error", "identity", "type",
        "type precisa ser recipe ou storage.");
    }

    var inheritance = isPlainObject(state.inheritance) ? state.inheritance : createInheritance();
    var inheritanceMode = trimString(inheritance.mode) || "none";
    var inheritanceParent = trimString(inheritance.parent);
    var inheritanceOk = true;
    if (["none", "extends", "family"].indexOf(inheritanceMode) === -1) {
      inheritanceOk = false;
      add("inheritance.mode.invalid", "error", "inheritance", "inheritanceMode",
        "O modo de herança precisa ser extends, family ou none.");
    }
    if (inheritanceMode !== "none") {
      if (!inheritanceParent) {
        inheritanceOk = false;
        add("inheritance.parent.required", "error", "inheritance", "inheritanceParent",
          "Informe o id da definição pai.");
      } else if (!isValidIdentifier(inheritanceParent)) {
        inheritanceOk = false;
        add("inheritance.parent.invalid", "error", "inheritance", "inheritanceParent",
          "O id da definição pai precisa ser um identifier válido.");
      } else if (namespace && path && isValidNamespace(namespace) && isValidPath(path) &&
        inheritanceParent === namespace + ":" + path) {
        inheritanceOk = false;
        add("inheritance.parent.self", "error", "inheritance", "inheritanceParent",
          "Uma definição não pode herdar de si mesma.");
      }
    }
    if (typeof inheritance.template !== "boolean") {
      inheritanceOk = false;
      add("inheritance.template.invalid", "error", "inheritance", "inheritanceTemplate",
        "template precisa ser true ou false.");
    } else if (inheritance.template) {
      add("inheritance.template", "warning", "inheritance", "inheritanceTemplate",
        "template=true faz o loader ignorar esta definição como máquina registrada.");
    }
    if (inheritance.overridesError) {
      inheritanceOk = false;
      add("inheritance.overrides.error", "error", "inheritance", "inheritanceOverrides",
        inheritance.overridesError);
    } else if (inheritance.overrides !== null && inheritance.overrides !== undefined &&
      !isPlainObject(inheritance.overrides)) {
      inheritanceOk = false;
      add("inheritance.overrides.invalid", "error", "inheritance", "inheritanceOverrides",
        "overrides precisa ser um objeto JSON.");
    } else if (isPlainObject(inheritance.overrides)) {
      ["extends", "family", "overrides", "template"].forEach(function (key) {
        if (Object.prototype.hasOwnProperty.call(inheritance.overrides, key)) {
          inheritanceOk = false;
          add("inheritance.overrides.reserved", "error", "inheritance", "inheritanceOverrides",
            "overrides não pode definir " + key + ".");
        }
      });
    }

    var tier = toInt(state.tier);
    var recipeValidation = validateRecipes(state.recipes, Object.assign({
      machineType: state.type,
      definitionId: namespace && path ? namespace + ":" + path : ""
    }, recipeAvailability(state)));
    recipeValidation.issues.forEach(function (entry) {
      issues.push(entry);
      if (entry.severity === "error") {
        errorCodes.add(entry.code);
      }
    });

    if (tier === null || tier < 1) {
      add("tier.invalid", "error", "identity", "tier",
        "tier precisa ser um inteiro maior ou igual a 1.");
    }
    if (toInt(state.priority) === null) {
      add("priority.invalid", "error", "identity", "priority",
        "priority precisa ser um inteiro.");
    }
    var inventoryRows = toInt(state.inventoryRows);
    if (inventoryRows === null || inventoryRows < 1 || inventoryRows > 6) {
      add("inventory_rows.invalid", "error", "identity", "inventoryRows",
        "inventory_rows precisa ser um inteiro de 1 a 6.");
    }

    var controllerRecipeOk = true;
    if (state.controllerRecipe !== null && state.controllerRecipe !== undefined) {
      controllerRecipeIssues(state.controllerRecipe).forEach(function (entry) {
        controllerRecipeOk = false;
        add(entry.code, "error", "controller", entry.field, entry.message);
      });
    }

    if (state.gui !== null && state.gui !== undefined) {
      var gui = state.gui;
      if (!isPlainObject(gui)) {
        add("gui.invalid", "error", "gui", "gui", "gui precisa ser um objeto.");
      } else {
        var guiWidth = toInt(gui.width);
        var guiHeight = toInt(gui.height);
        var guiTextureWidth = toInt(gui.textureWidth);
        var guiTextureHeight = toInt(gui.textureHeight);
        if (guiWidth === null || guiWidth < 1 || guiWidth > GUI_MAX_SIZE) {
          add("gui.width.invalid", "error", "gui", "guiWidth",
            "gui.width precisa estar entre 1 e " + GUI_MAX_SIZE + ".");
        }
        if (guiHeight === null || guiHeight < 1 || guiHeight > GUI_MAX_SIZE) {
          add("gui.height.invalid", "error", "gui", "guiHeight",
            "gui.height precisa estar entre 1 e " + GUI_MAX_SIZE + ".");
        }
        if (guiTextureWidth === null || guiTextureWidth < 1 || guiTextureWidth > GUI_MAX_SIZE) {
          add("gui.texture_width.invalid", "error", "gui", "guiTextureWidth",
            "gui.texture_width precisa estar entre 1 e " + GUI_MAX_SIZE + ".");
        }
        if (guiTextureHeight === null || guiTextureHeight < 1 || guiTextureHeight > GUI_MAX_SIZE) {
          add("gui.texture_height.invalid", "error", "gui", "guiTextureHeight",
            "gui.texture_height precisa estar entre 1 e " + GUI_MAX_SIZE + ".");
        }
        var guiTexture = trimString(gui.texture);
        if (guiTexture && !isValidIdentifier(guiTexture)) {
          add("gui.texture.invalid", "error", "gui", "guiTexture",
            "gui.texture precisa ser um identifier válido.");
        }
        var guiElements = Array.isArray(gui.elements) ? gui.elements : null;
        if (guiElements === null) {
          add("gui.elements.invalid", "error", "gui", "gui",
            "gui.elements precisa ser uma lista.");
        } else {
          var guiBounds = {
            width: guiWidth === null ? 0 : guiWidth,
            height: guiHeight === null ? 0 : guiHeight
          };
          var hasSlotElements = false;
          var playerInventoryCount = 0;
          var elementSlotIds = {};
          guiElements.forEach(function (element, index) {
            var field = "guiElement:" + index;
            if (!isPlainObject(element)) {
              add("gui.element.invalid", "error", "gui", field,
                "gui.elements[" + index + "] precisa ser um objeto.");
              return;
            }
            var type = trimString(element.type);
            if (GUI_ELEMENT_TYPES.indexOf(type) === -1) {
              add("gui.element.type.invalid", "error", "gui", field,
                "gui.elements[" + index + "].type é inválido.");
              return;
            }
            var x = toInt(element.x);
            var y = toInt(element.y);
            var width = toInt(element.width);
            var height = toInt(element.height);
            if (x === null) {
              add("gui.element.x.invalid", "error", "gui", field,
                "gui.elements[" + index + "].x precisa ser um inteiro.");
            }
            if (y === null) {
              add("gui.element.y.invalid", "error", "gui", field,
                "gui.elements[" + index + "].y precisa ser um inteiro.");
            }
            if (width === null || width < 0 || width > GUI_MAX_SIZE) {
              add("gui.element.width.invalid", "error", "gui", field,
                "gui.elements[" + index + "].width precisa estar entre 0 e " + GUI_MAX_SIZE + ".");
            }
            if (height === null || height < 0 || height > GUI_MAX_SIZE) {
              add("gui.element.height.invalid", "error", "gui", field,
                "gui.elements[" + index + "].height precisa estar entre 0 e " + GUI_MAX_SIZE + ".");
            }
            if (GUI_DIRECTIONS.indexOf(trimString(element.direction)) === -1) {
              add("gui.element.direction.invalid", "error", "gui", field,
                "gui.elements[" + index + "].direction é inválido.");
            }
            if (!isValidGuiColor(element.color)) {
              add("gui.element.color.invalid", "error", "gui", field,
                "gui.elements[" + index + "].color é inválido.");
            }
            if (typeof element.shadow !== "boolean") {
              add("gui.element.shadow.invalid", "error", "gui", field,
                "gui.elements[" + index + "].shadow precisa ser booleano.");
            }
            if (toInt(element.u) === null) {
              add("gui.element.u.invalid", "error", "gui", field,
                "gui.elements[" + index + "].u precisa ser um inteiro.");
            }
            if (toInt(element.v) === null) {
              add("gui.element.v.invalid", "error", "gui", field,
                "gui.elements[" + index + "].v precisa ser um inteiro.");
            }
            if (type === "slot") {
              hasSlotElements = true;
              var slotId = trimString(element.slot);
              if (!slotId) {
                add("gui.element.slot.required", "error", "gui", field,
                  "Elemento slot precisa de um id de item_slots.");
              } else if (elementSlotIds[slotId]) {
                add("gui.slot.duplicate", "error", "gui", field,
                  "O item_slots.id " + slotId + " aparece em mais de um elemento slot.");
              } else {
                elementSlotIds[slotId] = true;
              }
              if (x !== null && y !== null && guiWidth !== null && guiHeight !== null
                  && !guiInside(guiBounds, x, y, GUI_SLOT_SIZE, GUI_SLOT_SIZE)) {
                add("gui.bounds.exceeded", "error", "gui", field,
                  "O slot " + slotId + " excede os limites da GUI.");
              }
            } else if (type === "player_inventory") {
              playerInventoryCount += 1;
              if (x !== null && y !== null && guiWidth !== null && guiHeight !== null
                  && !guiInside(guiBounds, x, y, GUI_PLAYER_WIDTH, GUI_PLAYER_HEIGHT)) {
                add("gui.bounds.exceeded", "error", "gui", field,
                  "player_inventory excede os limites da GUI.");
              }
            } else if (type === "energy_bar" || type === "fluid_bar"
                || type === "progress_bar" || type === "arrow" || type === "entity") {
              if (width === null || width <= 0 || height === null || height <= 0) {
                add("gui.element.size.invalid", "error", "gui", field,
                  "Elementos size-dependentes de GUI precisam de width e height positivos.");
              } else if (type === "entity"
                  && (width < GUI_ENTITY_CELL_SIZE || height < GUI_ENTITY_CELL_SIZE
                    || width % GUI_ENTITY_CELL_SIZE !== 0 || height % GUI_ENTITY_CELL_SIZE !== 0)) {
                add("gui.element.size.invalid", "error", "gui", field,
                  "O elemento entity precisa de width/height múltiplos de "
                  + GUI_ENTITY_CELL_SIZE + " (mínimo " + GUI_ENTITY_CELL_SIZE + ").");
              } else if (x !== null && y !== null && guiWidth !== null && guiHeight !== null
                  && !guiInside(guiBounds, x, y, width, height)) {
                add("gui.bounds.exceeded", "error", "gui", field,
                  "O elemento " + type + " excede os limites da GUI.");
              }
            }
          });
          if (guiElements.length > 0) {
            var guiItemSlots = Array.isArray(state.itemSlots) ? state.itemSlots : [];
            if (hasSlotElements && guiItemSlots.length === 0) {
              add("gui.item_slots.required", "error", "gui", "gui",
                "GUI customizada com elementos slot exige item_slots.");
            }
            var positionedSlots = guiItemSlots.filter(function (slot) {
              return isPlainObject(slot) && toInt(slot.x) !== null && toInt(slot.y) !== null;
            });
            if (hasSlotElements && positionedSlots.length > 0) {
              add("gui.slot.mixed", "error", "gui", "gui",
                "Use x/y em item_slots ou elementos slot, não os dois.");
            }
            if (playerInventoryCount !== 1) {
              add("gui.player_inventory.count", "error", "gui", "gui",
                "GUI customizada exige exatamente um player_inventory.");
            }
            var guiSlotIds = {};
            guiItemSlots.forEach(function (slot) {
              if (isPlainObject(slot) && trimString(slot.id)) {
                guiSlotIds[trimString(slot.id)] = true;
              }
            });
            var guiEnergy = isPlainObject(state.capabilities) && isPlainObject(state.capabilities.energy)
              ? state.capabilities.energy
              : null;
            var guiTanks = isPlainObject(state.capabilities) && Array.isArray(state.capabilities.fluids)
              ? state.capabilities.fluids
              : [];
            guiElements.forEach(function (element, index) {
              if (!isPlainObject(element)) {
                return;
              }
              var type = trimString(element.type);
              var field = "guiElement:" + index;
              if (type === "slot") {
                var slotId = trimString(element.slot);
                if (slotId && !guiSlotIds[slotId]) {
                  add("gui.slot.unknown", "error", "gui", field,
                    "O elemento slot referencia um item_slots.id desconhecido: " + slotId + ".");
                }
              } else if (type === "energy_bar") {
                if (!guiEnergy) {
                  add("gui.energy.required", "error", "gui", field,
                    "energy_bar exige capabilities.energy.");
                } else if (trimString(element.capability) !== trimString(guiEnergy.id)) {
                  add("gui.energy.unknown", "error", "gui", field,
                    "energy_bar.capability precisa ser igual a capabilities.energy.id.");
                }
              } else if (type === "fluid_bar") {
                var knownTank = guiTanks.some(function (tank) {
                  return isPlainObject(tank) && trimString(tank.id) === trimString(element.capability);
                });
                if (!knownTank) {
                  add("gui.fluid.unknown", "error", "gui", field,
                    "fluid_bar.capability precisa ser o id de um tanque.");
                }
              } else if ((type === "progress_bar" || type === "arrow" || type === "entity")
                  && state.type !== "recipe") {
                add("gui.recipe.required", "error", "gui", field,
                  type + " só funciona em type=recipe.");
              }
            });
            positionedSlots.forEach(function (slot) {
              if (guiWidth !== null && guiHeight !== null
                  && !guiInside(guiBounds, toInt(slot.x), toInt(slot.y), GUI_SLOT_SIZE, GUI_SLOT_SIZE)) {
                add("gui.bounds.exceeded", "error", "gui", "gui",
                  "O slot " + trimString(slot.id) + " excede os limites da GUI.");
              }
            });
          }
        }
      }
    }

    var layers = Array.isArray(state.layers) ? state.layers : [];
    var geometryOk = true;
    var anchorOk = true;
    var matchersOk = true;

    if (!Array.isArray(state.layers) || layers.length === 0) {
      geometryOk = false;
      add("pattern.layers.empty", "error", "pattern", "pattern",
        "O pattern precisa ter pelo menos uma camada.");
    } else {
      var expectedRows = null;
      var expectedWidth = null;
      layers.forEach(function (layer, layerIndex) {
        if (!Array.isArray(layer) || layer.length === 0) {
          geometryOk = false;
          add("pattern.layer.rows.empty", "error", "pattern", "pattern",
            "A camada Y" + layerIndex + " precisa ter pelo menos uma linha.");
          return;
        }
        if (expectedRows === null) {
          expectedRows = layer.length;
        } else if (layer.length !== expectedRows) {
          geometryOk = false;
          add("pattern.layer.rows.mismatch", "error", "pattern", "pattern",
            "Todas as camadas precisam ter o mesmo número de linhas. A camada Y" +
            layerIndex + " tem " + layer.length + ".");
        }
        layer.forEach(function (row, rowIndex) {
          if (typeof row !== "string" || row.length === 0) {
            geometryOk = false;
            add("pattern.row.width.empty", "error", "pattern", "pattern",
              "A linha Z" + rowIndex + " da camada Y" + layerIndex +
              " precisa ter pelo menos um caractere.");
            return;
          }
          if (expectedWidth === null) {
            expectedWidth = row.length;
          } else if (row.length !== expectedWidth) {
            geometryOk = false;
            add("pattern.row.width.mismatch", "error", "pattern", "pattern",
              "Todas as linhas precisam ter a mesma largura. Encontrado " +
              row.length + " em vez de " + expectedWidth + ".");
          }
        });
      });
    }

    if (!isSingleSymbol(state.anchor) || state.anchor === " ") {
      anchorOk = false;
      add("pattern.anchor.invalid", "error", "pattern", "anchor",
        "A âncora precisa ser um único caractere e não pode ser espaço.");
    }

    var symbolCounts = new Map();
    var anchorCount = 0;
    if (Array.isArray(state.layers)) {
      state.layers.forEach(function (layer) {
        if (!Array.isArray(layer)) {
          return;
        }
        layer.forEach(function (row) {
          if (typeof row !== "string") {
            return;
          }
          for (var index = 0; index < row.length; index += 1) {
            var symbol = row.charAt(index);
            if (symbol === " ") {
              continue;
            }
            symbolCounts.set(symbol, (symbolCounts.get(symbol) || 0) + 1);
            if (symbol === state.anchor) {
              anchorCount += 1;
            }
          }
        });
      });
    }

    if (anchorCount !== 1) {
      anchorOk = false;
      add("pattern.anchor.count", "error", "pattern", "anchor",
        "O pattern precisa ter exatamente uma âncora " + state.anchor +
        "; encontrado " + anchorCount + ".");
    }

    var matcherSource = isPlainObject(state.matchers) ? state.matchers : {};
    var matcherKeys = Object.keys(matcherSource);
    var usedSymbols = Array.from(symbolCounts.keys());

    matcherKeys.forEach(function (symbol) {
      if (!isSingleSymbol(symbol)) {
        matchersOk = false;
        add("matcher.key.invalid", "error", "matchers", "matcher:" + symbol,
          "A chave de matcher \"" + symbol + "\" precisa ter exatamente um caractere.");
      }
      if (symbol === state.anchor) {
        add("matcher.anchor.ignored", "warning", "matchers", "matcher:" + symbol,
          "O matcher da âncora é ignorado pelo loader.");
      } else if (!symbolCounts.has(symbol)) {
        add("matcher.unused", "warning", "matchers", "matcher:" + symbol,
          "O símbolo \"" + symbol + "\" tem matcher, mas não aparece no pattern.");
      }
    });

    usedSymbols.forEach(function (symbol) {
      if (symbol === state.anchor) {
        return;
      }
      if (!Object.prototype.hasOwnProperty.call(matcherSource, symbol)) {
        matchersOk = false;
        add("pattern.symbol.unmatched", "error", "matchers", "matcher:" + symbol,
          "O símbolo \"" + symbol + "\" aparece no pattern, mas não tem matcher.");
      }
      if (/[\u0000-\u001f\u007f]/.test(symbol)) {
        add("pattern.symbol.control", "warning", "pattern", "pattern",
          "O pattern contém um caractere de controle invisível.");
      }
    });

    matcherKeys.forEach(function (symbol) {
      var matcher = matcherSource[symbol];
      if (!matcher || typeof matcher !== "object") {
        matchersOk = false;
        add("matcher.invalid", "error", "matchers", "matcher:" + symbol,
          "O matcher \"" + symbol + "\" é inválido.");
        return;
      }

      if (matcher.kind === "invalid") {
        matchersOk = false;
        add("matcher.object.invalid", "error", "matchers", "matcher:" + symbol,
          "O matcher \"" + symbol + "\" precisa ter exatamente um de block ou tag, e state só é aceito com block.");
        return;
      }

      if (matcher.kind === "tag") {
        var tag = stripTagPrefix(matcher.tag);
        if (!tag) {
          matchersOk = false;
          add("matcher.tag.required", "error", "matchers", "matcher:" + symbol,
            "Informe a tag do matcher \"" + symbol + "\".");
        } else if (!isValidIdentifier(tag)) {
          matchersOk = false;
          add("matcher.tag.invalid", "error", "matchers", "matcher:" + symbol,
            "A tag do matcher \"" + symbol + "\" não é um identifier válido.");
        }
      } else if (matcher.kind === "block" || matcher.kind === "state") {
        var block = trimString(matcher.block);
        if (!block) {
          matchersOk = false;
          add("matcher.block.required", "error", "matchers", "matcher:" + symbol,
            "Informe o bloco do matcher \"" + symbol + "\".");
        } else if (!isValidIdentifier(block)) {
          matchersOk = false;
          add("matcher.block.invalid", "error", "matchers", "matcher:" + symbol,
            "O bloco do matcher \"" + symbol + "\" não é um identifier válido.");
        }
        if (matcher.kind === "state") {
          if (!Array.isArray(matcher.state)) {
            matchersOk = false;
            add("matcher.state.invalid", "error", "matchers", "matcher:" + symbol,
              "A lista de propriedades do matcher \"" + symbol + "\" é inválida.");
          } else {
            var seenKeys = new Set();
            matcher.state.forEach(function (row) {
              var key = trimString(row && row.key);
              var value = trimString(row && row.value);
              if (!key) {
                matchersOk = false;
                add("matcher.state.key.required", "error", "matchers", "matcher:" + symbol,
                  "Toda propriedade de state do matcher \"" + symbol + "\" precisa de nome.");
                return;
              }
              if (!value) {
                matchersOk = false;
                add("matcher.state.value.required", "error", "matchers", "matcher:" + symbol,
                  "A propriedade \"" + key + "\" do matcher \"" + symbol + "\" precisa de valor.");
              }
              if (seenKeys.has(key)) {
                matchersOk = false;
                add("matcher.state.duplicate", "error", "matchers", "matcher:" + symbol,
                  "A propriedade \"" + key + "\" aparece mais de uma vez no matcher \"" + symbol + "\".");
              }
              seenKeys.add(key);
            });
          }
        }
      } else {
        matchersOk = false;
        add("matcher.kind.invalid", "error", "matchers", "matcher:" + symbol,
          "Formato de matcher desconhecido para \"" + symbol + "\".");
      }
    });

    var slotsOk = true;
    if (state.itemSlots !== null && state.itemSlots !== undefined) {
      if (!Array.isArray(state.itemSlots)) {
        slotsOk = false;
        add("item_slots.invalid", "error", "slots", "itemSlots",
          "item_slots precisa ser uma lista ou ficar ausente.");
      } else {
        var seenSlotIds = new Set();
        state.itemSlots.forEach(function (slot, index) {
          var field = "slot:" + index;
          if (!isPlainObject(slot)) {
            slotsOk = false;
            add("slot.invalid", "error", "slots", field,
              "O slot " + index + " precisa ser um objeto.");
            return;
          }

          var slotId = trimString(slot.id);
          if (!slotId) {
            slotsOk = false;
            add("slot.id.required", "error", "slots", field,
              "O slot " + index + " precisa de um id.");
          } else if (seenSlotIds.has(slotId)) {
            slotsOk = false;
            add("slot.id.duplicate", "error", "slots", field,
              "O id \"" + slotId + "\" aparece mais de uma vez nos slot(s).");
          } else {
            seenSlotIds.add(slotId);
          }

          if (!roleIsValid(slot.role)) {
            slotsOk = false;
            add("slot.role.invalid", "error", "slots", field,
              "O slot \"" + (slotId || index) + "\" tem role inválido. Use " +
              ITEM_SLOT_ROLES.join(", ") + ".");
          }

          var slotFilter = trimString(slot.filter);
          if (slotFilter && !isValidIdentifier(stripTagPrefix(slotFilter))) {
            slotsOk = false;
            add("slot.filter.invalid", "error", "slots", field,
              "O filtro do slot \"" + (slotId || index) + "\" precisa ser um item ou tag válido.");
          }

          var slotLimit = toInt(slot.limit);
          if (slotLimit === null || slotLimit < 1 || slotLimit > 64) {
            slotsOk = false;
            add("slot.limit.invalid", "error", "slots", field,
              "O limit do slot \"" + (slotId || index) + "\" precisa ser um inteiro de 1 a 64.");
          }

          var hasX = slot.x !== null && slot.x !== undefined;
          var hasY = slot.y !== null && slot.y !== undefined;
          if (hasX !== hasY) {
            slotsOk = false;
            add("slot.position.pair", "error", "slots", field,
              "O slot \"" + (slotId || index) + "\" precisa definir x e y juntos.");
          } else if (hasX && (toInt(slot.x) === null || toInt(slot.y) === null)) {
            slotsOk = false;
            add("slot.position.invalid", "error", "slots", field,
              "x e y do slot \"" + (slotId || index) + "\" precisam ser inteiros.");
          }

          if (slot.faces !== null && slot.faces !== undefined) {
            var faceProblem = facesProblem(slot.faces);
            if (faceProblem) {
              slotsOk = false;
              add("slot.faces.invalid", "error", "slots", field,
                "O slot \"" + (slotId || index) + "\" tem faces inválidas: " + faceProblem);
            }
          }
        });
      }
    }

    var capabilitiesOk = true;
    var capabilities = state.capabilities;
    var energyId = "";
    var fluidIds = new Set();
    if (capabilities !== null && capabilities !== undefined) {
      if (!isPlainObject(capabilities)) {
        capabilitiesOk = false;
        add("capabilities.invalid", "error", "capabilities", "capabilities",
          "capabilities precisa ser um objeto ou ficar ausente.");
      } else {
        var energy = capabilities.energy;
        if (energy !== null && energy !== undefined) {
          if (!isPlainObject(energy)) {
            capabilitiesOk = false;
            add("capability.energy.invalid", "error", "capabilities", "energy",
              "capabilities.energy precisa ser um objeto.");
          } else {
            var energyName = trimString(energy.id);
            if (!energyName) {
              capabilitiesOk = false;
              add("energy.id.required", "error", "capabilities", "energy",
                "A energia precisa de um id.");
            } else {
              energyId = energyName;
            }
            var energyCapacity = toInt(energy.capacity);
            if (energyCapacity === null || energyCapacity < 1 || energyCapacity > CAPABILITY_MAX) {
              capabilitiesOk = false;
              add("energy.capacity.invalid", "error", "capabilities", "energy",
                "A capacidade da energia precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
            }
            var energyReceive = toInt(energy.maxReceive);
            if (energyReceive === null || energyReceive < 0 || energyReceive > CAPABILITY_MAX) {
              capabilitiesOk = false;
              add("energy.max_receive.invalid", "error", "capabilities", "energy",
                "max_receive da energia precisa ser um inteiro de 0 a " + CAPABILITY_MAX + ".");
            }
            var energyExtract = toInt(energy.maxExtract);
            if (energyExtract === null || energyExtract < 0 || energyExtract > CAPABILITY_MAX) {
              capabilitiesOk = false;
              add("energy.max_extract.invalid", "error", "capabilities", "energy",
                "max_extract da energia precisa ser um inteiro de 0 a " + CAPABILITY_MAX + ".");
            }
          }
        }

        var fluids = capabilities.fluids;
        if (fluids !== null && fluids !== undefined) {
          if (!Array.isArray(fluids)) {
            capabilitiesOk = false;
            add("capability.fluids.invalid", "error", "capabilities", "capabilities",
              "capabilities.fluids precisa ser uma lista ou ficar ausente.");
          } else {
            fluids.forEach(function (fluid, index) {
              var field = "fluid:" + index;
              if (!isPlainObject(fluid)) {
                capabilitiesOk = false;
                add("fluid.invalid", "error", "capabilities", field,
                  "O tanque " + index + " precisa ser um objeto.");
                return;
              }
              var fluidId = trimString(fluid.id);
              if (!fluidId) {
                capabilitiesOk = false;
                add("fluid.id.required", "error", "capabilities", field,
                  "O tanque " + index + " precisa de um id.");
              } else if (fluidIds.has(fluidId)) {
                capabilitiesOk = false;
                add("fluid.id.duplicate", "error", "capabilities", field,
                  "O id de tanque \"" + fluidId + "\" aparece mais de uma vez.");
              } else {
                fluidIds.add(fluidId);
              }
              var fluidCapacity = toInt(fluid.capacity);
              if (fluidCapacity === null || fluidCapacity < 1 || fluidCapacity > CAPABILITY_MAX) {
                capabilitiesOk = false;
                add("fluid.capacity.invalid", "error", "capabilities", field,
                  "A capacidade do tanque \"" + (fluidId || index) + "\" precisa ser um inteiro de 1 a " + CAPABILITY_MAX + ".");
              }
              var fluidReceive = toInt(fluid.maxReceive);
              if (fluidReceive === null || fluidReceive < 0 || fluidReceive > CAPABILITY_MAX) {
                capabilitiesOk = false;
                add("fluid.max_receive.invalid", "error", "capabilities", field,
                  "max_receive do tanque \"" + (fluidId || index) + "\" precisa ser um inteiro de 0 a " + CAPABILITY_MAX + ".");
              }
              var fluidExtract = toInt(fluid.maxExtract);
              if (fluidExtract === null || fluidExtract < 0 || fluidExtract > CAPABILITY_MAX) {
                capabilitiesOk = false;
                add("fluid.max_extract.invalid", "error", "capabilities", field,
                  "max_extract do tanque \"" + (fluidId || index) + "\" precisa ser um inteiro de 0 a " + CAPABILITY_MAX + ".");
              }
              var fluidFilter = trimString(fluid.filter);
              if (fluidFilter && !isValidIdentifier(stripTagPrefix(fluidFilter))) {
                capabilitiesOk = false;
                add("fluid.filter.invalid", "error", "capabilities", field,
                  "O filtro do tanque \"" + (fluidId || index) + "\" precisa ser um fluido ou tag válido.");
              }
            });
          }
        }

        if (energyId && fluidIds.has(energyId)) {
          capabilitiesOk = false;
          add("energy.id.conflict", "error", "capabilities", "energy",
            "O id da energia \"" + energyId + "\" não pode ser igual ao id de um tanque.");
        }
      }
    }

    var capabilityPortsOk = true;
    var capabilityPorts = state.capabilityPorts;
    if (capabilityPorts !== null && capabilityPorts !== undefined) {
      if (!isPlainObject(capabilityPorts)) {
        capabilityPortsOk = false;
        add("capability_ports.invalid", "error", "capabilities", "capabilityPorts",
          "capability_ports precisa ser um objeto ou ficar ausente.");
      } else {
        var portSymbols = recipePatternSymbols(state);
        var portAvailability = capabilityPortAvailability(state.capabilities);
        CAPABILITY_PORT_KEYS.forEach(function (key) {
          var selectors = capabilityPorts[key];
          if (selectors === null || selectors === undefined) {
            return;
          }
          if (!Array.isArray(selectors)) {
            capabilityPortsOk = false;
            add("capability_ports.list.invalid", "error", "capabilities", "capabilityPorts:" + key,
              "capability_ports." + key + " precisa ser uma lista ou ficar ausente.");
            return;
          }
          if (!portAvailability[key]) {
            add("capability_ports.capability.missing", "warning", "capabilities", "capabilityPorts:" + key,
              "capability_ports." + key + " será omitido: a máquina não implementa essa capability.");
          }
          selectors.forEach(function (selector, index) {
            var text = trimString(selector);
            if (!validCapabilityPortSelector(text, portSymbols)) {
              capabilityPortsOk = false;
              add("capability_ports.selector.invalid", "error", "capabilities", "capabilityPorts:" + key,
                "capability_ports." + key + "[" + index + "] precisa ser um símbolo do pattern, um bloco ou uma tag válida.");
            }
          });
        });
      }
    }

    var extras = isPlainObject(state.extras) ? state.extras : {};
    var extraKeys = Object.keys(extras);
    if (extras.template === true) {
      add("extras.template", "warning", "identity", "template",
        "template=true faz o loader ignorar esta definição como máquina.");
    }
    if (extras.extends !== undefined || extras.family !== undefined) {
      add("extras.inheritance", "warning", "identity", "inheritance",
        "Herança detectada. Campos herdados não são validados nesta fase; o resultado final depende do pai.");
    }
    if (extras.overrides !== undefined) {
      add("extras.overrides", "warning", "identity", "overrides",
        "overrides é aplicado depois dos campos do filho e pode sobrescrever o que o builder gerou.");
    }
    if (extraKeys.length > 0) {
      add("extras.preserved", "info", "identity", "extras",
        "Campos preservados sem edição nesta fase: " + extraKeys.join(", ") + ".");
    }
    var patternExtras = isPlainObject(state.patternExtras) ? state.patternExtras : {};
    if (Object.keys(patternExtras).length > 0) {
      add("pattern.extras", "info", "pattern", "pattern",
        "Campos extras do pattern preservados: " + Object.keys(patternExtras).join(", ") + ".");
    }

    checks.push({
      id: "schema",
      label: CHECK_LABELS.schema,
      ok: !errorCodes.has("schema.unsupported"),
      step: "identity"
    });
    checks.push({
      id: "identity",
      label: CHECK_LABELS.identity,
      ok: !["id.namespace.required", "id.namespace.invalid", "id.path.required",
        "id.path.invalid", "type.invalid"].some(function (code) {
        return errorCodes.has(code);
      }),
      step: "identity"
    });
    checks.push({
      id: "geometry",
      label: CHECK_LABELS.geometry,
      ok: geometryOk,
      step: "pattern"
    });
    checks.push({
      id: "anchor",
      label: CHECK_LABELS.anchor,
      ok: anchorOk,
      step: "pattern"
    });
    checks.push({
      id: "matchers",
      label: CHECK_LABELS.matchers,
      ok: matchersOk,
      step: "matchers"
    });
    checks.push({
      id: "slots",
      label: CHECK_LABELS.slots,
      ok: slotsOk,
      step: "slots"
    });
    checks.push({
      id: "capabilities",
      label: CHECK_LABELS.capabilities,
      ok: capabilitiesOk,
      step: "capabilities"
    });
    checks.push({
      id: "capabilityPorts",
      label: CHECK_LABELS.capabilityPorts,
      ok: capabilityPortsOk,
      step: "capabilities"
    });
    checks.push({
      id: "controller",
      label: CHECK_LABELS.controller,
      ok: true,
      step: "controller"
    });
    checks.push({
      id: "controllerRecipe",
      label: CHECK_LABELS.controllerRecipe,
      ok: controllerRecipeOk,
      step: "controller"
    });
    checks.push({
      id: "gui",
      label: CHECK_LABELS.gui,
      ok: !["gui.invalid", "gui.width.invalid", "gui.height.invalid",
        "gui.texture_width.invalid", "gui.texture_height.invalid", "gui.texture.invalid",
        "gui.elements.invalid", "gui.element.invalid", "gui.element.type.invalid",
        "gui.element.x.invalid", "gui.element.y.invalid", "gui.element.width.invalid",
        "gui.element.height.invalid", "gui.element.direction.invalid", "gui.element.color.invalid",
        "gui.element.shadow.invalid", "gui.element.u.invalid", "gui.element.v.invalid",
        "gui.element.size.invalid", "gui.element.slot.required", "gui.slot.duplicate",
        "gui.slot.unknown", "gui.slot.mixed", "gui.item_slots.required",
        "gui.player_inventory.count", "gui.energy.required", "gui.energy.unknown",
        "gui.fluid.unknown", "gui.recipe.required", "gui.bounds.exceeded"].some(function (code) {
        return errorCodes.has(code);
      }),
      step: "gui"
    });
    checks.push({
      id: "recipes",
      label: CHECK_LABELS.recipes,
      ok: recipeValidation.ok,
      step: "recipes"
    });
    checks.push({
      id: "inheritance",
      label: CHECK_LABELS.inheritance,
      ok: inheritanceOk,
      step: "inheritance"
    });
    checks.push({
      id: "metadata",
      label: CHECK_LABELS.metadata,
      ok: !["tier.invalid", "priority.invalid", "inventory_rows.invalid"].some(function (code) {
        return errorCodes.has(code);
      }),
      step: "identity"
    });

    issues.sort(function (left, right) {
      var severityOrder = { error: 0, warning: 1, info: 2 };
      var severityDelta = severityOrder[left.severity] - severityOrder[right.severity];
      if (severityDelta !== 0) {
        return severityDelta;
      }
      var stepDelta = STEP_ORDER.indexOf(left.step) - STEP_ORDER.indexOf(right.step);
      return stepDelta;
    });

    return {
      issues: issues,
      checks: checks,
      errors: issues.filter(function (entry) {
        return entry.severity === "error";
      }),
      warnings: issues.filter(function (entry) {
        return entry.severity === "warning";
      }),
      infos: issues.filter(function (entry) {
        return entry.severity === "info";
      })
    };
  }

  function matcherProblem(matcher) {
    if (!matcher || typeof matcher !== "object") {
      return "Matcher inválido.";
    }
    if (matcher.kind === "invalid") {
      return "Use block ou tag (exatamente um); state só vale com block.";
    }
    if (matcher.kind === "tag") {
      return isValidIdentifier(stripTagPrefix(matcher.tag)) ? "" : "Tag inválida.";
    }
    if (matcher.kind === "block" || matcher.kind === "state") {
      return isValidIdentifier(matcher.block) ? "" : "Bloco inválido.";
    }
    return "Formato desconhecido.";
  }

  return {
    SCHEMA_VERSION: SCHEMA_VERSION,
    RECIPE_TYPE: RECIPE_TYPE,
    KNOWN_RECIPE_TYPES: KNOWN_RECIPE_TYPES.slice(),
    ITEM_SLOT_ROLES: ITEM_SLOT_ROLES.slice(),
    CONTROLLER_FACES: CONTROLLER_FACES.slice(),
    GUI_ELEMENT_TYPES: GUI_ELEMENT_TYPES.slice(),
    GUI_DIRECTIONS: GUI_DIRECTIONS.slice(),
    GUI_DEFAULT_WIDTH: GUI_DEFAULT_WIDTH,
    GUI_DEFAULT_HEIGHT: GUI_DEFAULT_HEIGHT,
    GUI_DEFAULT_TEXTURE_SIZE: GUI_DEFAULT_TEXTURE_SIZE,
    createDefaultState: createDefaultState,
    createExampleState: createExampleState,
    createEmptyState: createEmptyState,
    createMatcher: createMatcher,
    createItemSlot: createItemSlot,
    createInheritance: createInheritance,
    parseInheritance: parseInheritance,
    buildInheritance: buildInheritance,
    createRecipe: createRecipe,
    createRecipeInputRow: createRecipeInputRow,
    createRecipeOutputRow: createRecipeOutputRow,
    parseRecipeResource: parseRecipeResource,
    recipeRowKind: recipeRowKind,
    parseRecipe: parseRecipe,
    buildRecipe: buildRecipe,
    validateRecipes: validateRecipes,
    recipeAvailability: recipeAvailability,
    recipePatternSymbols: recipePatternSymbols,
    validSpawnSelector: validSpawnSelector,
    validCapabilityPortSelector: validCapabilityPortSelector,
    parseIngredientLines: parseIngredientLines,
    parseFluidLines: parseFluidLines,
    createCapabilities: createCapabilities,
    createCapabilityPorts: createCapabilityPorts,
    capabilityPortAvailability: capabilityPortAvailability,
    parseCapabilityPortText: parseCapabilityPortText,
    capabilityPortText: capabilityPortText,
    createEnergy: createEnergy,
    createFluidTank: createFluidTank,
    createController: createController,
    createFormedTextures: createFormedTextures,
    createControllerRecipe: createControllerRecipe,
    createGui: createGui,
    createGuiElement: createGuiElement,
    parseItemSlots: parseItemSlots,
    parseCapabilities: parseCapabilities,
    parseCapabilityPorts: parseCapabilityPorts,
    parseController: parseController,
    parseControllerRecipe: parseControllerRecipe,
    parseGui: parseGui,
    importDefinition: importDefinition,
    buildDefinition: buildDefinition,
    buildItemSlot: buildItemSlot,
    buildEnergy: buildEnergy,
    buildFluidTank: buildFluidTank,
    buildCapabilities: buildCapabilities,
    buildCapabilityPorts: buildCapabilityPorts,
    buildController: buildController,
    buildControllerRecipe: buildControllerRecipe,
    buildGui: buildGui,
    buildGuiElement: buildGuiElement,
    validateState: validateState,
    isValidIdentifier: isValidIdentifier,
    isValidNamespace: isValidNamespace,
    isValidPath: isValidPath,
    isSingleSymbol: isSingleSymbol,
    parseMatcherValue: parseMatcherValue,
    parseMatcherInput: parseMatcherInput,
    buildMatcherValue: buildMatcherValue,
    matcherProblem: matcherProblem,
    slotProblem: slotProblem,
    energyProblem: energyProblem,
    fluidProblem: fluidProblem,
    controllerProblem: controllerProblem,
    controllerRecipeProblem: controllerRecipeProblem,
    controllerRecipeIssues: controllerRecipeIssues,
    guiProblem: guiProblem,
    isValidGuiColor: isValidGuiColor,
    clone: clone
  };
});
