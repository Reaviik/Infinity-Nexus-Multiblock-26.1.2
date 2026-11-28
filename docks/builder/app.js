(function () {
  "use strict";

  var V = window.MultiblockValidation;
  var STORAGE_KEY = "infinity-nexus-multiblock-builder.v2";
  var STORAGE_VERSION = 2;
  var ERASER = " ";
  var COLORS = [
    "#6bd64a",
    "#57c7ff",
    "#c792ea",
    "#f5c451",
    "#ff7a7a",
    "#4dd0b1",
    "#ff9f43",
    "#a3e635",
    "#e879f9",
    "#38bdf8",
    "#f472b6",
    "#94a3b8"
  ];
  var SLOT_ROLE_COLORS = {
    input: "#6bd64a",
    output: "#57c7ff",
    fuel: "#f5c451",
    upgrade: "#c792ea",
    component: "#ff9f43",
    storage: "#94a3b8",
    fluid: "#38bdf8"
  };
  var SLOT_ROLE_INFO = {
    input: "Participa do matching da receita; aceita itens por qualquer face e não extrai.",
    output: "Recebe os resultados da receita; não aceita inserção, mas permite extração.",
    fuel: "Reservado para combustível e automação; não tem comportamento de receita embutido.",
    upgrade: "Reservado para upgrades; não tem comportamento de receita embutido.",
    component: "Reservado para componentes e peças; não tem comportamento de receita embutido.",
    storage: "Armazenamento e automação; aceita inserção e extração por qualquer face.",
    fluid: "Slot manual de container: drena o item para o tanque selecionado ao inserir e enche pelo tanque ao retirar; sem automação por faces."
  };
  var SLOT_FACE_DIRECTIONS = ["north", "south", "east", "west", "up", "down"];
  var SLOT_FACE_LABELS = {
    north: "N",
    south: "S",
    east: "L",
    west: "O",
    up: "↑",
    down: "↓"
  };
  var STEPS = [
    { id: "identity", label: "Identidade", hint: "id, type, metadados" },
    { id: "pattern", label: "Pattern", hint: "camadas e âncora" },
    { id: "matchers", label: "Matchers", hint: "blocos e tags" },
    { id: "upgrades", label: "Boosters", hint: "key e upgrades" },
    { id: "motions", label: "Animações", hint: "keys, keyframes e pivot" },
    { id: "slots", label: "Slots", hint: "item_slots" },
    { id: "capabilities", label: "Capabilities", hint: "energia, tanques, essentia e portas" },
    { id: "controller", label: "Controller", hint: "texturas, item e receita" },
    { id: "gui", label: "GUI", hint: "layout e elementos" },
    { id: "sounds", label: "Sons", hint: "vanilla ou custom" },
    { id: "recipes", label: "Receitas", hint: "data-driven e JEI" },
    { id: "inheritance", label: "Herança", hint: "extends/family e overrides" },
    { id: "review", label: "Revisão", hint: "JSON e checklist" }
  ];
  var FUTURE_STEPS = [];
  var STEP_IDS = STEPS.map(function (step) {
    return step.id;
  });
  var STEP_LABELS = {};
  STEPS.forEach(function (step) {
    STEP_LABELS[step.id] = step.label;
  });
  var FIELD_INPUTS = {
    namespace: "namespace",
    path: "path",
    tier: "tier",
    priority: "priority",
    inventoryRows: "inventoryRows",
    anchor: "anchorSymbol",
    upgradeKey: "upgradeKey"
  };
  var FIELD_TARGETS = {
    namespace: "namespace",
    path: "path",
    tier: "tier",
    priority: "priority",
    inventoryRows: "inventoryRows",
    anchor: "anchorSymbol",
    pattern: "layerGrid"
  };
  var EXTRAS_FIELDS = ["extras", "template", "inheritance", "overrides"];

  var state = null;
  var report = null;
  var definition = null;
  var jsonText = "";
  var currentUsage = {};
  var currentColors = {};
  var importNotes = [];
  var activeStep = "identity";
  var activeLayer = 0;
  var activeSymbol = null;
  var paletteRenameSymbol = null;
  var dragging = false;
  var dragButton = 0;
  var lastPaintKey = "";
  var saveTimer = null;
  var toastTimer = null;
  var minecraftSoundData = null;
  var minecraftSoundOptionsRendered = false;
  var SOUND_FIELDS = ["processing", "finish"];
  var soundUploadState = { processing: null, finish: null };
  var soundAudioState = { processing: null, finish: null };
  var soundUploadTarget = "";
  var ModelPreview = window.ModelPreview3D;
  var MotionPreview = window.MotionPreview3D;
  var MotionPreviewWebGL = window.MotionPreview3DWebGL;
  var motionPreviewWebGLAvailable = null;
  var motionPreviewWebGLFailed = false;
  var MODEL_KEYS = ["idle", "active", "building"];
  var DEFAULT_MODEL_IDS = {
    idle: "infinity_nexus_multiblock:block/multiblock_controller",
    active: "infinity_nexus_multiblock:block/multiblock_controller_on",
    building: "infinity_nexus_multiblock:block/multiblock_controller_building"
  };
  var MOTION_DAMAGE_SOURCES = [
    "minecraft:generic",
    "minecraft:player_attack",
    "minecraft:mob_attack",
    "minecraft:mob_attack_no_aggro",
    "minecraft:in_fire",
    "minecraft:on_fire",
    "minecraft:lava",
    "minecraft:hot_floor",
    "minecraft:campfire",
    "minecraft:cactus",
    "minecraft:fall",
    "minecraft:fly_into_wall",
    "minecraft:drown",
    "minecraft:starve",
    "minecraft:magic",
    "minecraft:indirect_magic",
    "minecraft:wither",
    "minecraft:dragon_breath",
    "minecraft:freeze",
    "minecraft:lightning_bolt",
    "minecraft:out_of_world",
    "minecraft:generic_kill",
    "minecraft:outside_border",
    "minecraft:stalagmite",
    "minecraft:sweet_berry_bush",
    "minecraft:in_wall",
    "minecraft:cramming",
    "minecraft:dry_out",
    "minecraft:sonic_boom",
    "minecraft:thorns",
    "minecraft:explosion",
    "minecraft:player_explosion",
    "minecraft:mob_projectile",
    "minecraft:arrow",
    "minecraft:trident",
    "minecraft:fireball",
    "minecraft:unattributed_fireball",
    "minecraft:wither_skull",
    "minecraft:falling_block",
    "minecraft:falling_anvil",
    "minecraft:falling_stalactite",
    "minecraft:sting",
    "minecraft:spit",
    "minecraft:fireworks",
    "minecraft:thrown",
    "minecraft:wind_charge",
    "minecraft:bad_respawn_point"
  ];
  var controllerMode = "default";
  var motionMode = "none";
  var upgradeMode = "none";
  var upgradeSimulationState = {
    recipe: null,
    notes: [],
    counts: {}
  };
  var modelPreviewState = {
    parsed: null,
    baseParsed: null,
    fileName: "",
    source: "none",
    fieldId: "",
    fieldKey: "",
    previewState: "idle",
    loadingField: false,
    fieldRequest: 0,
    images: [],
    textureImages: {},
    animatedTextures: {},
    animationRaf: 0,
    localModels: {},
    importTarget: "",
    textureTarget: "",
    textureTargetId: "",
    textureTargetRow: "",
    textureTargetKey: "",
    textureLabel: "",
    previewTextureKeys: { idle: {}, active: {}, building: {} },
    error: "",
    dragging: false,
    lastX: 0,
    lastY: 0,
    view: { yaw: Math.PI * 5 / 4, pitch: 0.5, zoom: 1 },
    showFloor: true,
    showReference: true
  };
  var modelPreviewFieldTimer = null;
  var motionPreviewState = {
    playing: true,
    time: 0,
    speed: 1,
    lastFrame: 0,
    raf: null,
    selectedMotionIndex: 0,
    selectedMotionId: "",
    selectedKeyframe: 0,
    hiddenKeys: {},
    opacity: 0.65,
    dragging: false,
    lastX: 0,
    lastY: 0,
    camera: "isometric",
    view: { yaw: Math.PI / 4, pitch: 0.5, zoom: 1 },
    revision: 0
  };
  var railPreviewState = {
    time: 0,
    lastFrame: 0,
    raf: null
  };

  var MOTION_CAMERA_VIEWS = {
    isometric: { yaw: Math.PI / 4, pitch: 0.5, zoom: 1 },
    front: { yaw: 0, pitch: 0, zoom: 1 },
    back: { yaw: Math.PI, pitch: 0, zoom: 1 },
    side: { yaw: Math.PI / 2, pitch: 0, zoom: 1 },
    top: { yaw: 0, pitch: Math.PI / 2, zoom: 1 },
    bottom: { yaw: 0, pitch: -Math.PI / 2, zoom: 1 }
  };
  var RAIL_PREVIEW_VIEW = { yaw: Math.PI / 4, pitch: 0.5, zoom: 1 };

  function $(id) {
    return document.getElementById(id);
  }

  function $$(selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  }

  function closest(target, selector) {
    return target && target.closest ? target.closest(selector) : null;
  }

  function isPlainObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function trim(value) {
    return typeof value === "string" ? value.trim() : "";
  }

  function esc(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function parseIntStrict(value) {
    var text = String(value === undefined || value === null ? "" : value).trim();
    if (!/^-?\d+$/.test(text)) {
      return null;
    }
    var parsed = parseInt(text, 10);
    return Number.isSafeInteger(parsed) ? parsed : null;
  }

  function compareSymbols(left, right) {
    if (left < right) {
      return -1;
    }
    if (left > right) {
      return 1;
    }
    return 0;
  }

  function pad2(value) {
    return value < 10 ? "0" + value : String(value);
  }

  function showToast(message, tone) {
    var toast = $("toast");
    if (!toast) {
      return;
    }
    toast.textContent = message;
    toast.setAttribute("data-tone", tone === "error" ? "error" : "ok");
    toast.hidden = true;
    void toast.offsetWidth;
    toast.hidden = false;
    if (toastTimer) {
      window.clearTimeout(toastTimer);
    }
    toastTimer = window.setTimeout(function () {
      toast.hidden = true;
      toastTimer = null;
    }, 2800);
  }

  function fallbackCopy(text, resolve, reject) {
    var area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.left = "-9999px";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    try {
      var copied = document.execCommand("copy");
      document.body.removeChild(area);
      if (copied) {
        resolve();
      } else {
        reject(new Error("copy"));
      }
    } catch (error) {
      document.body.removeChild(area);
      reject(error);
    }
  }

  function copyText(text) {
    return new Promise(function (resolve, reject) {
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(resolve, function () {
          fallbackCopy(text, resolve, reject);
        });
        return;
      }
      fallbackCopy(text, resolve, reject);
    });
  }

  function filePath() {
    var namespace = trim(state.namespace) || "...";
    var path = trim(state.path) || "...";
    return "data/" + namespace + "/multiblock/" + path + ".json";
  }

  function showCopyResult(label) {
    copyText(label.text).then(function () {
      showToast(label.done);
    }, function () {
      showToast("Não foi possível copiar.", "error");
    });
  }

  function copyFilePath() {
    showCopyResult({ text: filePath(), done: "Caminho copiado." });
  }

  function copyJson() {
    if (!report || report.errors.length > 0) {
      showToast("Corrija os erros antes de copiar o JSON.", "error");
      return;
    }
    showCopyResult({ text: jsonText, done: "JSON copiado." });
  }

  function downloadJson() {
    if (!report || report.errors.length > 0) {
      showToast("Corrija os erros antes de baixar.", "error");
      return;
    }
    var path = trim(state.path) || "multiblock";
    var segments = path.split("/");
    var name = segments[segments.length - 1] || "multiblock";
    var blob = new Blob([jsonText + "\n"], { type: "application/json" });
    var url = URL.createObjectURL(blob);
    var link = document.createElement("a");
    link.href = url;
    link.download = name + ".json";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(function () {
      URL.revokeObjectURL(url);
    }, 1000);
    showToast("Baixado " + name + ".json. Salve em " + filePath() + " sem renomear.");
  }

  function saveNow() {
    if (!state) {
      return;
    }
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
        version: STORAGE_VERSION,
        state: state,
        importNotes: importNotes,
        activeStep: activeStep,
        controllerMode: controllerMode,
        motionMode: motionMode,
        upgradeMode: upgradeMode
      }));
    } catch (error) {
      return;
    }
  }

  function scheduleSave() {
    if (saveTimer) {
      window.clearTimeout(saveTimer);
    }
    saveTimer = window.setTimeout(function () {
      saveTimer = null;
      saveNow();
    }, 160);
  }

  function loadSaved() {
    var saved;
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return false;
      }
      saved = JSON.parse(raw);
    } catch (error) {
      return false;
    }
    if (!saved || saved.version !== STORAGE_VERSION || !isPlainObject(saved.state)) {
      return false;
    }
    var base = V.createDefaultState();
    state = Object.assign({}, base, saved.state);
    if (!isPlainObject(state.matchers)) {
      state.matchers = {};
    }
    if (!isPlainObject(state.extras)) {
      state.extras = {};
    }
    if (!isPlainObject(state.inheritance)) {
      state.inheritance = V.parseInheritance(state.extras, []);
    }
    state.inheritance = Object.assign(V.createInheritance(), state.inheritance);
    ["extends", "family", "template", "overrides"].forEach(function (key) {
      delete state.extras[key];
    });
    if (isPlainObject(state.inheritance.overrides) && !trim(state.inheritance.overridesRaw)) {
      state.inheritance.overridesRaw = JSON.stringify(state.inheritance.overrides, null, 2);
    }
    if (!Array.isArray(state.recipes)) {
      state.recipes = [];
    }
    state.recipes = state.recipes.filter(isPlainObject).map(function (recipe) {
      var normalized = Object.assign(V.createRecipe(), recipe);
      if (!isPlainObject(normalized.extras)) {
        normalized.extras = {};
      }
      return normalized;
    });
    state.motions = V.normalizeMotions(state.motions);
    if (!isPlainObject(state.upgrades) && isPlainObject(state.extras.upgrades)) {
      state.upgrades = V.parseUpgrades(state.extras.upgrades, []);
    }
    state.upgrades = V.normalizeUpgrades(state.upgrades);
    delete state.extras.upgrades;
    if (state.itemSlots === undefined) {
      if (Array.isArray(state.extras.item_slots)) {
        state.itemSlots = V.parseItemSlots(state.extras.item_slots, []);
      } else {
        state.itemSlots = null;
      }
    }
    if (state.itemSlots !== null && !Array.isArray(state.itemSlots)) {
      state.itemSlots = null;
    }
    delete state.extras.item_slots;
    if (state.capabilities === undefined) {
      if (isPlainObject(state.extras.capabilities)) {
        state.capabilities = V.parseCapabilities(state.extras.capabilities, []);
      } else {
        state.capabilities = null;
      }
    }
    if (state.capabilities !== null && !isPlainObject(state.capabilities)) {
      state.capabilities = null;
    }
    delete state.extras.capabilities;
    if (state.capabilityPorts === undefined) {
      if (isPlainObject(state.extras.capability_ports)) {
        state.capabilityPorts = V.parseCapabilityPorts(state.extras.capability_ports, []);
      } else {
        state.capabilityPorts = null;
      }
    }
    if (state.capabilityPorts !== null && !isPlainObject(state.capabilityPorts)) {
      state.capabilityPorts = null;
    }
    delete state.extras.capability_ports;
    if (!isPlainObject(state.textures)) {
      state.textures = isPlainObject(state.extras.textures)
        ? V.parseTextures(state.extras.textures, [])
        : V.createTextures();
    }
    state.textures.idle = Object.assign(
      V.createTextureSet(),
      isPlainObject(state.textures.idle) ? state.textures.idle : {}
    );
    state.textures.active = Object.assign(
      V.createTextureSet(),
      isPlainObject(state.textures.active) ? state.textures.active : {}
    );
    state.textures.building = Object.assign(
      V.createTextureSet(),
      isPlainObject(state.textures.building) ? state.textures.building : {}
    );
    delete state.extras.textures;
    if (!isPlainObject(state.model)) {
      state.model = isPlainObject(state.extras.model)
        ? V.parseModel(state.extras.model, [])
        : V.createModel();
    }
    state.model = Object.assign(V.createModel(), state.model);
    delete state.extras.model;
    if (!Array.isArray(state.shape)) {
      state.shape = Array.isArray(state.extras.shape)
        ? V.parseShape(state.extras.shape, [])
        : V.createShape();
    }
    delete state.extras.shape;
    delete state.controller;
    delete state.recipeType;
    delete state.extras.controller;
    delete state.extras.recipe_type;
    if (!isPlainObject(state.controllerRecipe) && isPlainObject(state.extras.recipe)) {
      state.controllerRecipe = V.parseControllerRecipe(state.extras.recipe, []);
    }
    if (state.controllerRecipe === undefined) {
      state.controllerRecipe = null;
    }
    if (state.controllerRecipe !== null && !isPlainObject(state.controllerRecipe)) {
      state.controllerRecipe = null;
    }
    delete state.extras.recipe;
    if (!isPlainObject(state.gui) && isPlainObject(state.extras.gui)) {
      state.gui = V.parseGui(state.extras.gui, []);
    }
    if (state.gui === undefined) {
      state.gui = null;
    }
    if (state.gui !== null && !isPlainObject(state.gui)) {
      state.gui = null;
    }
    if (isPlainObject(state.gui)) {
      if (!Array.isArray(state.gui.elements)) {
        state.gui.elements = [];
      }
      if (!isPlainObject(state.gui.extras)) {
        state.gui.extras = {};
      }
    }
    delete state.extras.gui;
    if (!isPlainObject(state.sounds) && isPlainObject(state.extras.sounds)) {
      state.sounds = V.parseSounds(state.extras.sounds, []);
    }
    if (!isPlainObject(state.sounds)) {
      state.sounds = V.createSounds();
    }
    if (!isPlainObject(state.sounds.extras)) {
      state.sounds.extras = {};
    }
    delete state.extras.sounds;
    if (!isPlainObject(state.patternExtras)) {
      state.patternExtras = {};
    }
    if (!Array.isArray(state.layers) || state.layers.length === 0) {
      state.layers = base.layers;
    }
    if (!V.isSingleSymbol(state.anchor) || state.anchor === " ") {
      state.anchor = base.anchor;
    }
    state.type = state.type === "recipe" ? "recipe" : "storage";
    state.schemaVersion = V.SCHEMA_VERSION;
    if (Array.isArray(saved.importNotes)) {
      importNotes = saved.importNotes.filter(function (note) {
        return note && typeof note.message === "string";
      });
    }
    activeStep = STEP_IDS.indexOf(saved.activeStep) >= 0 ? saved.activeStep : "identity";
    controllerMode = saved.controllerMode === "custom" || saved.controllerMode === "default"
      ? saved.controllerMode
      : deriveControllerMode();
    motionMode = saved.motionMode === "custom" || saved.motionMode === "none"
      ? saved.motionMode
      : deriveMotionMode();
    if (motionMode === "none" && motionsState().length > 0) {
      motionMode = "custom";
    }
    upgradeMode = saved.upgradeMode === "custom" || saved.upgradeMode === "none"
      ? saved.upgradeMode
      : deriveUpgradeMode();
    if (upgradeMode === "none" && isPlainObject(state.upgrades)) {
      upgradeMode = "custom";
    }
    if (upgradeMode === "custom" && !isPlainObject(state.upgrades)) {
      state.upgrades = V.createUpgrades();
    }
    modelPreviewState.localModels = {};
    resetPreviewTextureOverrides();
    if (controllerMode === "default") {
      state.model = V.createModel();
      state.shape = V.createShape();
    }
    return true;
  }

  function getDimensions() {
    var layers = Array.isArray(state.layers) ? state.layers : [];
    var width = 1;
    var rows = 1;
    layers.forEach(function (layer) {
      if (!Array.isArray(layer)) {
        return;
      }
      rows = Math.max(rows, layer.length);
      layer.forEach(function (row) {
        if (typeof row === "string") {
          width = Math.max(width, row.length);
        }
      });
    });
    return {
      width: width,
      rows: rows,
      height: Math.max(1, layers.length)
    };
  }

  function collectUsage() {
    var counts = {};
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
          if (symbol === " ") {
            continue;
          }
          counts[symbol] = (counts[symbol] || 0) + 1;
        }
      });
    });
    return counts;
  }

  function countSymbol(symbol) {
    return collectUsage()[symbol] || 0;
  }

  function hasMatcher(symbol) {
    return isPlainObject(state.matchers) &&
      Object.prototype.hasOwnProperty.call(state.matchers, symbol);
  }

  function matcherSummary(symbol) {
    if (!hasMatcher(symbol)) {
      return "";
    }
    var matcher = state.matchers[symbol];
    if (!isPlainObject(matcher) || matcher.kind === "invalid") {
      return "matcher inválido";
    }
    var base;
    if (matcher.kind === "tag") {
      var tag = trim(matcher.tag);
      if (!tag) {
        return "matcher inválido";
      }
      base = tag.charAt(0) === "#" ? tag : "#" + tag;
    } else {
      base = trim(matcher.block);
      if (!base) {
        return "matcher inválido";
      }
    }
    var rows = Array.isArray(matcher.state) ? matcher.state : [];
    if (rows.length > 0) {
      base += "[" + rows.map(function (row) {
        return trim(row.key) + "=" + trim(row.value);
      }).join(",") + "]";
    }
    return base;
  }

  function symbolHover(symbol) {
    if (symbol === state.anchor) {
      return symbol + " = âncora";
    }
    if (symbol === upgradeKey()) {
      return symbol + " = key de upgrade (sem matcher)";
    }
    return symbol + " = " + (matcherSummary(symbol) || "sem matcher");
  }

  function collectPaletteSymbols(usage) {
    var set = {};
    if (V.isSingleSymbol(state.anchor) && state.anchor !== " ") {
      set[state.anchor] = true;
    }
    Object.keys(state.matchers || {}).forEach(function (symbol) {
      if (V.isSingleSymbol(symbol) && symbol !== " ") {
        set[symbol] = true;
      }
    });
    var key = upgradeKey();
    if (V.isSingleSymbol(key) && key !== " ") {
      set[key] = true;
    }
    Object.keys(usage).forEach(function (symbol) {
      if (V.isSingleSymbol(symbol) && symbol !== " ") {
        set[symbol] = true;
      }
    });
    var symbols = Object.keys(set).sort(compareSymbols);
    var anchorIndex = symbols.indexOf(state.anchor);
    if (anchorIndex > 0) {
      symbols.splice(anchorIndex, 1);
      symbols.unshift(state.anchor);
    }
    return symbols;
  }

  function buildColorMap(usage) {
    var colors = {};
    var next = 1;
    collectPaletteSymbols(usage).forEach(function (symbol) {
      if (symbol === state.anchor) {
        colors[symbol] = COLORS[0];
        return;
      }
      colors[symbol] = COLORS[1 + ((next - 1) % (COLORS.length - 1))];
      next += 1;
    });
    return colors;
  }

  function getColor(symbol) {
    return currentColors[symbol] || (symbol === state.anchor ? COLORS[0] : COLORS[1]);
  }

  function ensureActiveSymbol() {
    var palette = collectPaletteSymbols(currentUsage);
    if (activeSymbol === ERASER) {
      return;
    }
    if (activeSymbol !== null &&
      V.isSingleSymbol(activeSymbol) &&
      (activeSymbol === state.anchor || hasMatcher(activeSymbol) || palette.indexOf(activeSymbol) !== -1)) {
      return;
    }
    var painted = palette.filter(function (symbol) {
      return symbol !== state.anchor;
    });
    if (painted.length > 0) {
      activeSymbol = painted[0];
    } else if (palette.indexOf(state.anchor) !== -1) {
      activeSymbol = state.anchor;
    } else {
      activeSymbol = ERASER;
    }
  }

  function missingSymbols() {
    var missing = [];
    var key = upgradeKey();
    Object.keys(currentUsage).forEach(function (symbol) {
      if (symbol !== " " && symbol !== state.anchor && symbol !== key && !hasMatcher(symbol)) {
        missing.push(symbol);
      }
    });
    return missing.sort(compareSymbols);
  }

  function cellLabel(row, column, symbol) {
    var position = "X" + column + " Z" + row + " Y" + activeLayer;
    if (symbol === " ") {
      return "Vazio em " + position;
    }
    if (symbol === state.anchor) {
      return "Âncora " + symbol + " em " + position;
    }
    if (symbol === upgradeKey()) {
      return "Key de upgrade " + symbol + " em " + position;
    }
    return symbolHover(symbol) + " em " + position;
  }

  function applyCellClasses(button, row, column, symbol) {
    var isAnchor = symbol !== " " && symbol === state.anchor;
    var isUpgrade = symbol !== " " && symbol === upgradeKey();
    var isUnmatched = symbol !== " " && !isAnchor && !isUpgrade && !hasMatcher(symbol);
    var classes = ["cell"];
    if (symbol === " ") {
      classes.push("is-empty");
    }
    if (isAnchor) {
      classes.push("is-anchor");
    }
    if (isUpgrade) {
      classes.push("is-upgrade");
    }
    if (isUnmatched) {
      classes.push("is-unmatched");
    }
    if (button.classList.contains("is-focused")) {
      classes.push("is-focused");
    }
    button.className = classes.join(" ");
    button.textContent = symbol === " " ? "" : symbol;
    if (symbol === " ") {
      button.style.removeProperty("--c");
    } else {
      button.style.setProperty("--c", getColor(symbol));
    }
    button.setAttribute("aria-label", cellLabel(row, column, symbol));
    button.title = cellLabel(row, column, symbol);
  }

  function resizePattern(axis, value) {
    var dimensions = getDimensions();
    var width = axis === "X" ? value : dimensions.width;
    var rows = axis === "Z" ? value : dimensions.rows;
    var height = axis === "Y" ? value : dimensions.height;
    var layers = [];
    for (var y = 0; y < height; y += 1) {
      var source = Array.isArray(state.layers[y]) ? state.layers[y] : [];
      var layer = [];
      for (var z = 0; z < rows; z += 1) {
        var sourceRow = typeof source[z] === "string" ? source[z] : "";
        var row = "";
        for (var x = 0; x < width; x += 1) {
          row += x < sourceRow.length ? sourceRow.charAt(x) : " ";
        }
        layer.push(row);
      }
      layers.push(layer);
    }
    state.layers = layers;
    activeLayer = clamp(activeLayer, 0, height - 1);
  }

  function syncInputs() {
    var dimensions = getDimensions();
    $("namespace").value = state.namespace || "";
    $("path").value = state.path || "";
    $("tier").value = state.tier === undefined || state.tier === null ? "" : String(state.tier);
    $("priority").value = state.priority === undefined || state.priority === null ? "" : String(state.priority);
    $("inventoryRows").value = state.inventoryRows === undefined || state.inventoryRows === null ? "" : String(state.inventoryRows);
    $("sizeX").value = String(dimensions.width);
    $("sizeZ").value = String(dimensions.rows);
    $("sizeY").value = String(dimensions.height);
    $("anchorSymbol").value = state.anchor;
    $$("input[name='multiblockType']").forEach(function (radio) {
      radio.checked = radio.value === state.type;
    });
  }

  function setFieldError(field) {
    var element = document.querySelector("[data-error-for='" + field + "']");
    if (!element) {
      return;
    }
    var found = report.issues.filter(function (entry) {
      return entry.severity === "error" && entry.field === field;
    });
    var message = found.length > 0 ? found[0].message : "";
    if (found.length > 1) {
      message += " (+" + (found.length - 1) + ")";
    }
    element.textContent = message;
  }

  function renderFieldErrors() {
    ["namespace", "path", "tier", "priority", "inventoryRows", "anchor", "pattern", "inheritanceParent",
      "soundProcessing", "soundFinish", "upgradeKey"].forEach(function (field) {
      setFieldError(field);
    });
    Object.keys(FIELD_INPUTS).forEach(function (field) {
      var input = $(FIELD_INPUTS[field]);
      if (!input) {
        return;
      }
      var hasError = report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === field;
      });
      input.classList.toggle("is-invalid", hasError);
    });
  }

  function renderHeader() {
    var errors = report.errors.length;
    var warnings = report.warnings.length;
    var pill = $("statusPill");
    pill.textContent = errors > 0 ? "Erros: " + errors : warnings > 0 ? "Avisos: " + warnings : "Pronto";
    pill.setAttribute("data-tone", errors > 0 ? "error" : warnings > 0 ? "warn" : "ok");
    pill.title = errors + " erro(s), " + warnings + " aviso(s)";
    setStepBadge("identityBadge", "identity");
    setStepBadge("patternBadge", "pattern");
    setStepBadge("matchersBadge", "matchers");
    setStepBadge("upgradesBadge", "upgrades");
    setStepBadge("slotsBadge", "slots");
    setStepBadge("capabilitiesBadge", "capabilities");
    setStepBadge("controllerBadge", "controller");
    setStepBadge("guiBadge", "gui");
    setStepBadge("soundsBadge", "sounds");
    setStepBadge("recipesBadge", "recipes");
    setStepBadge("inheritanceBadge", "inheritance");
    setStepBadge("reviewBadge", null);
    var chip = document.querySelector(".status-chip[data-for='identity']");
    if (chip) {
      var tone = stepTone("identity");
      chip.textContent = tone === "error" ? "Erro" : tone === "warn" ? "Avisos" : "OK";
      chip.setAttribute("data-tone", tone);
    }
  }

  function setStepBadge(id, step) {
    var element = $(id);
    if (!element) {
      return;
    }
    var issues = report.issues.filter(function (entry) {
      return entry.severity !== "info" && (step === null || entry.step === step);
    });
    var errors = issues.filter(function (entry) {
      return entry.severity === "error";
    }).length;
    var warnings = issues.length - errors;
    element.textContent = errors > 0 ? "Erro" : warnings > 0 ? "Avisos" : "OK";
    element.setAttribute("data-tone", errors > 0 ? "error" : warnings > 0 ? "warn" : "ok");
    element.title = errors + " erro(s), " + warnings + " aviso(s)";
  }

  function stepTone(stepId) {
    var issues = report.issues.filter(function (entry) {
      return entry.severity !== "info" && entry.step === stepId;
    });
    if (issues.some(function (entry) { return entry.severity === "error"; })) {
      return "error";
    }
    if (issues.length > 0) {
      return "warn";
    }
    return "ok";
  }

  function renderNav() {
    var passed = report.checks.filter(function (check) {
      return check.ok;
    }).length;
    var total = report.checks.length;
    var percent = total > 0 ? Math.round((passed / total) * 100) : 0;
    var html = "<div class='nav-progress'><div class='nav-progress-head'><span>Progresso</span><strong>" +
      passed + "/" + total + "</strong></div><div class='nav-progress-track'><div class='nav-progress-fill' style='width:" +
      percent + "%'></div></div></div>";
    STEPS.forEach(function (step, index) {
      var active = step.id === activeStep;
      html += "<button type='button' class='step-button" + (active ? " is-active" : "") + "' data-step='" +
        step.id + "'" + (active ? " aria-current='step'" : "") + "><span class='step-number'>" +
        pad2(index + 1) + "</span><span class='step-copy'><strong>" + esc(step.label) +
        "</strong><small>" + esc(step.hint) + "</small></span><span class='step-dot' data-tone='" +
        stepTone(step.id) + "'></span></button>";
    });
    if (FUTURE_STEPS.length > 0) {
      html += "<div class='nav-divider'></div>";
      FUTURE_STEPS.forEach(function (step, index) {
        html += "<button type='button' class='step-button is-future' disabled aria-disabled='true'><span class='step-number'>" +
          pad2(STEPS.length + index + 1) + "</span><span class='step-copy'><strong>" + esc(step.label) +
          "</strong><small>" + esc(step.hint) + "</small></span><span class='future-tag'>em breve</span></button>";
      });
    }
    $("stepNav").innerHTML = html;
  }

  function renderStepVisibility() {
    $$(".view[data-view]").forEach(function (view) {
      view.hidden = view.getAttribute("data-view") !== activeStep;
    });
    var palettePanel = $("palettePanel");
    if (palettePanel) {
      palettePanel.hidden = activeStep !== "pattern";
    }
    var patternPreviewCard = $("patternPreviewCard");
    if (patternPreviewCard) {
      patternPreviewCard.hidden = activeStep === "motions";
    }
  }

  function isPatternFullscreen() {
    return document.body.classList.contains("pattern-fullscreen");
  }

  function setPatternFullscreen(enabled) {
    var next = Boolean(enabled);
    var wasFullscreen = isPatternFullscreen();
    document.body.classList.toggle("pattern-fullscreen", next);
    var button = $("patternFullscreenBtn");
    if (button) {
      button.setAttribute("aria-pressed", next ? "true" : "false");
      button.textContent = next ? "sair da tela cheia" : "tela cheia";
      button.title = next ? "Sair da tela cheia (Esc)" : "Tela cheia do pattern";
    }
    if (next) {
      focusElement($("layerGrid"), false);
    } else if (wasFullscreen && button) {
      focusElement(button, false);
    }
    window.setTimeout(drawPreview, 40);
  }

  function togglePatternFullscreen() {
    setPatternFullscreen(!isPatternFullscreen());
  }

  function valueSummary(value) {
    if (Array.isArray(value)) {
      return "lista[" + value.length + "]";
    }
    if (isPlainObject(value)) {
      return "objeto";
    }
    if (value === null) {
      return "null";
    }
    return String(value);
  }

  function summariseExtra(key, value) {
    var preview;
    try {
      preview = JSON.stringify(value);
    } catch (error) {
      preview = String(value);
    }
    if (preview && preview.length > 160) {
      preview = preview.slice(0, 157) + "...";
    }
    return "<span class='chip'" + ((key === "template" || key === "extends" || key === "family" || key === "overrides") ? " data-tone='warn'" : "") +
      " title='" + esc(key + " = " + (preview || valueSummary(value))) + "'>" + esc(key) +
      " <small>" + esc(valueSummary(value)) + "</small></span>";
  }

  function renderIdentityMeta() {
    var extras = isPlainObject(state.extras) ? state.extras : {};
    var keys = Object.keys(extras).sort(compareSymbols);
    var extrasPanel = $("extrasPanel");
    extrasPanel.hidden = keys.length === 0;
    $("extrasChips").innerHTML = keys.map(function (key) {
      return summariseExtra(key, extras[key]);
    }).join("");
    var callout = $("namespaceCallout");
    var namespace = trim(state.namespace);
    if (namespace && V.isValidNamespace(namespace) && namespace !== "infinity_nexus_multiblock") {
      callout.hidden = false;
      callout.className = "callout callout-info";
      callout.innerHTML = "<strong>Namespace externo:</strong> a definição usa o controller compartilhado <code>infinity_nexus_multiblock:multiblock_controller</code>. Nenhum bloco novo precisa ser registrado.";
    } else {
      callout.hidden = true;
      callout.innerHTML = "";
      callout.className = "callout callout-info";
    }
  }

  function renderPattern() {
    var dimensions = getDimensions();
    activeLayer = clamp(activeLayer, 0, dimensions.height - 1);
    $("patternMeta").textContent = "X " + dimensions.width + " · Z " + dimensions.rows + " · Y " + dimensions.height;
    $("sizeX").value = String(dimensions.width);
    $("sizeZ").value = String(dimensions.rows);
    $("sizeY").value = String(dimensions.height);
    $("addLayerBtn").disabled = dimensions.height >= 32;
    $("duplicateLayerBtn").disabled = dimensions.height >= 32;
    $("removeLayerBtn").disabled = dimensions.height <= 1;

    var tabs = [];
    for (var layerIndex = 0; layerIndex < dimensions.height; layerIndex += 1) {
      var active = layerIndex === activeLayer;
      tabs.push("<button type='button' role='tab' aria-selected='" + (active ? "true" : "false") +
        "' class='layer-tab" + (active ? " is-active" : "") + "' data-layer='" + layerIndex +
        "' title='Camada Y" + layerIndex + "'><span>Y" + layerIndex + "</span><small>" +
        dimensions.width + "×" + dimensions.rows + "</small></button>");
    }
    $("layerTabs").innerHTML = tabs.join("");

    var grid = $("layerGrid");
    grid.style.gridTemplateColumns = "28px repeat(" + dimensions.width + ", var(--cell))";
    grid.setAttribute("aria-label", "Grade da camada Y" + activeLayer);
    var html = ["<span class='axis-cell axis-corner' aria-hidden='true'>Y" + activeLayer + "</span>"];
    for (var column = 0; column < dimensions.width; column += 1) {
      html.push("<span class='axis-cell axis-x' aria-hidden='true'>" + column + "</span>");
    }
    var layer = Array.isArray(state.layers[activeLayer]) ? state.layers[activeLayer] : [];
    for (var row = 0; row < dimensions.rows; row += 1) {
      html.push("<span class='axis-cell axis-z' aria-hidden='true'>" + row + "</span>");
      var source = typeof layer[row] === "string" ? layer[row] : "";
      for (var col = 0; col < dimensions.width; col += 1) {
        var symbol = col < source.length ? source.charAt(col) : " ";
        var isAnchor = symbol !== " " && symbol === state.anchor;
        var isUnmatched = symbol !== " " && !isAnchor && !hasMatcher(symbol);
        var classes = "cell" + (symbol === " " ? " is-empty" : "") +
          (isAnchor ? " is-anchor" : "") + (isUnmatched ? " is-unmatched" : "");
        html.push("<button type='button' class='" + classes + "' tabindex='-1' data-row='" + row +
          "' data-col='" + col + "'" + (symbol === " " ? "" : " style='--c:" + getColor(symbol) + "'") +
          " aria-label='" + esc(cellLabel(row, col, symbol)) + "' title='" +
          esc(cellLabel(row, col, symbol)) + "'>" + (symbol === " " ? "" : esc(symbol)) + "</button>");
      }
    }
    grid.innerHTML = html.join("");
    renderPalette();
    renderLegend();
  }

  function renderPalette() {
    var parts = [];
    collectPaletteSymbols(currentUsage).forEach(function (symbol) {
      var active = activeSymbol === symbol;
      var isAnchor = symbol === state.anchor;
      var count = currentUsage[symbol] || 0;
      var unmatched = !isAnchor && !hasMatcher(symbol);
      var descriptor = isAnchor ? "âncora" : unmatched ? "sem matcher" : count > 0 ? "×" + count : "não usado";
      parts.push("<button type='button' class='palette-chip" + (active ? " is-active" : "") +
        (unmatched ? " is-unmatched" : "") + "' data-symbol='" + esc(symbol) +
        "' aria-pressed='" + (active ? "true" : "false") + "' title='" + esc(symbolHover(symbol)) + " · " +
        esc(descriptor) + "'><span class='swatch' style='--c:" + getColor(symbol) + "'>" + esc(symbol) +
        "</span><span class='palette-count'>" + esc(descriptor) + "</span></button>");
    });
    var eraserActive = activeSymbol === ERASER;
    parts.push("<button type='button' class='palette-chip" + (eraserActive ? " is-active" : "") +
      "' data-symbol=' ' aria-pressed='" + (eraserActive ? "true" : "false") +
      "' title='Apagar célula'><span class='swatch swatch-eraser'>⌫</span><span class='palette-count'>apagar</span></button>");
    $("palette").innerHTML = parts.join("");
    updatePaletteForm();
  }

  function renderLegend() {
    var used = Object.keys(currentUsage).sort(compareSymbols);
    $("legend").innerHTML = used.map(function (symbol) {
      var descriptor = symbol === state.anchor
        ? "âncora"
        : (symbol === upgradeKey() ? "upgrade" : "×" + currentUsage[symbol]);
      return "<span class='legend-entry' style='--c:" + getColor(symbol) + "' title='" + esc(symbolHover(symbol)) +
        "'> <span class='legend-swatch'></span>" + esc(symbol) + " · " + esc(descriptor) + "</span>";
    }).join("");
  }

  function matcherFeedback(symbol, matcher) {
    if (!V.isSingleSymbol(symbol)) {
      return { tone: "is-error", text: "A chave do matcher precisa ter exatamente um caractere." };
    }
    if (symbol === upgradeKey()) {
      return { tone: "is-error", text: "Esta key é usada pelos boosters e não deve ter matcher." };
    }
    var problem = V.matcherProblem(matcher);
    if (problem) {
      return { tone: "is-error", text: problem };
    }
    if (symbol === state.anchor) {
      return { tone: "is-warning", text: "Este matcher é ignorado: " + state.anchor + " é a âncora." };
    }
    if (!currentUsage[symbol]) {
      return { tone: "is-warning", text: "O símbolo não aparece no pattern." };
    }
    if (matcher.kind === "state" && (!Array.isArray(matcher.state) || matcher.state.length === 0)) {
      return { tone: "is-warning", text: "Sem propriedades, a exportação vira um bloco simples." };
    }
    return null;
  }

  function matcherFieldHtml(symbol, matcher) {
    var labelSymbol = " do símbolo \"" + symbol + "\"";
    if (matcher.kind === "tag") {
      return "<div class='field'><span class='field-label'>Tag</span><input type='text' data-msymbol='" +
        esc(symbol) + "' data-field='tag' value='" + esc(matcher.tag || "") +
        "' placeholder='c:storage_blocks/iron' spellcheck='false' autocomplete='off' aria-label='Tag" +
        esc(labelSymbol) + "'></div>";
    }
    if (matcher.kind !== "block" && matcher.kind !== "state") {
      return "";
    }
    return "<div class='field'><span class='field-label'>Bloco</span><input type='text' data-msymbol='" +
      esc(symbol) + "' data-field='block' value='" + esc(matcher.block || "") +
      "' placeholder='minecraft:iron_block' title='Aceita o ID do bloco ou o JSON do /inmb look' " +
      "spellcheck='false' autocomplete='off' aria-label='Bloco" +
      esc(labelSymbol) + "'></div>";
  }

  function matcherStateHtml(symbol, matcher) {
    if (matcher.kind !== "state" && matcher.kind !== "tag") {
      return "";
    }
    var rows = Array.isArray(matcher.state) ? matcher.state : [];
    var html = "<div class='state-list'>";
    rows.forEach(function (row, index) {
      html += "<div class='state-row'><input type='text' data-msymbol='" + esc(symbol) +
        "' data-field='state-key' data-index='" + index + "' value='" + esc(row.key || "") +
        "' placeholder='facing' spellcheck='false' autocomplete='off' aria-label='Propriedade " +
        (index + 1) + "'><input type='text' data-msymbol='" + esc(symbol) +
        "' data-field='state-value' data-index='" + index + "' value='" + esc(row.value || "") +
        "' placeholder='north' spellcheck='false' autocomplete='off' aria-label='Valor da propriedade " +
        (index + 1) + "'><button type='button' class='icon-btn' data-action='remove-state' data-index='" +
        index + "' title='Remover propriedade'>×</button></div>";
    });
    return html + "</div><button type='button' class='btn btn-tiny' data-action='add-state'>+ propriedade</button>";
  }

  function matcherCardHtml(symbol) {
    var matcher = state.matchers[symbol];
    var count = currentUsage[symbol] || 0;
    var color = getColor(symbol);
    var detail = count > 0 ? count + (count === 1 ? " célula no pattern" : " células no pattern") : "não usado no pattern";
    if (symbol === state.anchor) {
      detail += " · âncora";
    }
    var cardClass = "matcher-card" + (V.matcherProblem(matcher) || !V.isSingleSymbol(symbol) ? " is-problem" : "");
    var remove = "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-matcher'>remover</button>";
    if (!matcher) {
      return "";
    }
    var kindOptions = [
      { value: "block", label: "Bloco" },
      { value: "tag", label: "Tag" },
      { value: "state", label: "Bloco + state" }
    ];
    var select = "<select data-msymbol='" + esc(symbol) + "' data-field='kind' aria-label='Tipo do matcher'>";
    if (matcher.kind === "invalid") {
      select += "<option value='invalid' selected disabled>Inválido</option>";
    }
    kindOptions.forEach(function (option) {
      select += "<option value='" + option.value + "'" + (matcher.kind === option.value ? " selected" : "") +
        ">" + option.label + "</option>";
    });
    select += "</select>";
    var feedback = matcherFeedback(symbol, matcher);
    var jsonValue = V.buildMatcherValue(matcher);
    var serialized = JSON.stringify(jsonValue);
    if (serialized === undefined) {
      serialized = "null";
    }
    return "<div class='" + cardClass + "' data-symbol='" + esc(symbol) + "'><div class='matcher-head'><span class='swatch' title='" + esc(symbolHover(symbol)) + "' style='--c:" +
      color + "'>" + esc(symbol) + "</span><div class='matcher-title'><strong>Símbolo \"" + esc(symbol) +
      "\"</strong><small>" + esc(detail) + (V.isSingleSymbol(symbol) ? "" : " · chave inválida") +
      "</small></div>" + remove + "</div><div class='matcher-form'><div class='field'><span class='field-label'>Tipo</span>" +
      select + "</div><div class='matcher-fields'>" + matcherFieldHtml(symbol, matcher) +
      "</div></div><p class='matcher-feedback" + (feedback ? " " + feedback.tone : "") + "'" +
      (feedback ? "" : " hidden") + ">" + (feedback ? esc(feedback.text) : "") +
      "</p><details class='card-advanced'><summary>Estado e JSON<span class='card-advanced-hint'>" +
      (matcher.kind === "state" || (matcher.kind === "tag" && Array.isArray(matcher.state) && matcher.state.length > 0)
        ? "blockstate e exportação"
        : "exportação") +
      "</span></summary><div class='card-advanced-body'>" + matcherStateHtml(symbol, matcher) +
      "<code class='matcher-json'>" + esc(serialized) + "</code></div></details></div>";
  }

  function renderMatchers() {
    var symbols = [];
    var seen = {};
    var key = upgradeKey();
    Object.keys(currentUsage).forEach(function (symbol) {
      if (symbol !== " " && symbol !== state.anchor && symbol !== key && !seen[symbol]) {
        seen[symbol] = true;
        symbols.push(symbol);
      }
    });
    Object.keys(state.matchers || {}).forEach(function (symbol) {
      if (!seen[symbol]) {
        seen[symbol] = true;
        symbols.push(symbol);
      }
    });
    symbols.sort(compareSymbols);
    var missing = missingSymbols();
    if (missing.length > 0) {
      var chips = missing.map(function (symbol) {
        return "<button type='button' class='chip' data-create-missing='" + esc(symbol) +
          "' title='" + esc(symbolHover(symbol)) + " · criar matcher'>" + esc(symbol) + "</button>";
      }).join("");
      $("missingMatchers").innerHTML = "<div class='missing-card'><span><strong>Sem matcher:</strong></span><div class='missing-symbols'>" +
        chips + "</div><button type='button' class='btn btn-tiny' data-create-all='true'>Criar todos</button></div>";
    } else {
      var usedCount = Object.keys(currentUsage).filter(function (symbol) {
        return symbol !== " " && symbol !== state.anchor && symbol !== key;
      }).length;
      $("missingMatchers").innerHTML = "<div class='missing-empty'>" +
        (usedCount > 0 ? "Todos os símbolos usados têm matcher." : "Pinte símbolos no pattern para configurar matchers.") +
        "</div>";
    }
    $("matcherList").innerHTML = symbols.map(matcherCardHtml).join("");
  }

  function upgradesState() {
    return isPlainObject(state.upgrades) ? state.upgrades : null;
  }

  function upgradeKey() {
    var upgrades = upgradesState();
    return upgrades ? trim(upgrades.key) : "";
  }

  function deriveUpgradeMode() {
    return isPlainObject(state.upgrades) ? "custom" : "none";
  }

  function upgradeModifierInfo(id) {
    var wanted = trim(id);
    var list = V.UPGRADE_MODIFIERS || [];
    for (var index = 0; index < list.length; index += 1) {
      if (list[index].id === wanted) {
        return list[index];
      }
    }
    return null;
  }

  function boosterEnabled(booster) {
    return !isPlainObject(booster) || booster.enabled !== false;
  }

  function upgradeModeSummaryText() {
    var upgrades = upgradesState();
    if (!upgrades) {
      return "Sem boosters; o JSON não inclui upgrades.";
    }
    var key = trim(upgrades.key) || "?";
    var boosters = Array.isArray(upgrades.boosters) ? upgrades.boosters : [];
    var enabledCount = boosters.filter(boosterEnabled).length;
    if (enabledCount === boosters.length) {
      return "Key " + key + " · " + boosters.length + (boosters.length === 1 ? " booster" : " boosters");
    }
    return "Key " + key + " · " + enabledCount + " ativo(s) de " + boosters.length + " booster(s)";
  }

  function updateUpgradeSummary() {
    var upgrades = upgradesState();
    var summary = $("upgradeModeSummary");
    if (summary) {
      summary.textContent = upgradeModeSummaryText();
    }
    if (!upgrades) {
      return;
    }
    var key = upgradeKey();
    var cells = key ? (currentUsage[key] || 0) : 0;
    var boosters = Array.isArray(upgrades.boosters) ? upgrades.boosters : [];
    if ($("upgradeCellsPreview")) {
      $("upgradeCellsPreview").textContent = String(cells);
    }
    var enabledCount = boosters.filter(boosterEnabled).length;
    if ($("upgradeCountPreview")) {
      $("upgradeCountPreview").textContent = String(enabledCount) + " / " + String(boosters.length);
    }
    if ($("upgradeBoostersSummary")) {
      $("upgradeBoostersSummary").textContent = boosters.length === 0
        ? "Nenhum booster."
        : enabledCount + (enabledCount === 1 ? " ativo de " : " ativos de ") +
          boosters.length + (boosters.length === 1 ? " booster." : " boosters.");
    }
    if ($("upgradeBoostersEmpty")) {
      $("upgradeBoostersEmpty").hidden = boosters.length > 0;
    }
    if ($("upgradeJson")) {
      var built = V.buildUpgrades(upgrades);
      $("upgradeJson").textContent = built ? JSON.stringify(built, null, 2) : "";
    }
  }

  var UPGRADE_SIM_EPSILON = 1.0E-9;

  function upgradeSimulationCells() {
    var key = upgradeKey();
    return key ? (currentUsage[key] || 0) : 0;
  }

  function upgradeSimulationBoosterLimit(booster, cells) {
    var maxBlocks = Math.max(1, Math.trunc(Number(booster && booster.maxBlocks) || 1));
    return Math.max(0, Math.min(maxBlocks, cells));
  }

  function upgradeSimulationBoosterCount(booster, index, cells) {
    var limit = upgradeSimulationBoosterLimit(booster, cells);
    var stored = upgradeSimulationState.counts[index];
    if (stored === undefined || stored === null || stored === "") {
      return limit;
    }
    var count = Math.trunc(Number(stored));
    if (!Number.isFinite(count)) {
      return limit;
    }
    return Math.max(0, Math.min(limit, count));
  }

  function upgradeSimBonus(modifierId) {
    var info = upgradeModifierInfo(modifierId);
    var increases = info ? info.increases === true : false;
    var entries = [];
    var upgrades = upgradesState();
    if (upgrades && Array.isArray(upgrades.boosters)) {
      var cells = upgradeSimulationCells();
      upgrades.boosters.forEach(function (booster, index) {
        if (!isPlainObject(booster) || !boosterEnabled(booster) || booster.modifier !== modifierId) {
          return;
        }
        var count = upgradeSimulationBoosterCount(booster, index, cells);
        if (count <= 0) {
          return;
        }
        var factor = Number(booster.value);
        if (!Number.isFinite(factor) || factor <= 0) {
          return;
        }

        entries.push({ count: count, factor: factor, mode: booster.mode || "additive" });
      });
    }
    var bonus = V.combineUpgradeBonus(entries, increases);
    bonus.counted = entries.reduce(function (total, entry) {
      return total + entry.count;
    }, 0);
    return bonus;
  }

  function upgradeSimScaledAmount(base, bonus) {
    return V.scaleUpgradeOutput(base, bonus);
  }

  function upgradeSimReduced(base, bonus) {
    return V.reduceUpgradeValue(base, bonus);
  }

  function upgradeSimThreads(bonus) {
    return V.upgradeThreads(bonus);
  }

  function upgradeSimDamage(base, bonus) {
    return V.scaleUpgradeDamage(base, bonus);
  }

  function upgradeSimFormat(value) {
    var number = Number(value);
    if (!Number.isFinite(number)) {
      return "0";
    }
    var rounded = Math.round(number);
    if (Math.abs(number - rounded) < 0.000001) {
      return String(rounded);
    }
    return String(Math.round(number * 100) / 100);
  }

  function upgradeSimPercent(value) {
    var number = Number(value);
    if (!Number.isFinite(number)) {
      return "0%";
    }
    return String(Math.round(number * 1000) / 10) + "%";
  }

  function upgradeSimBonusText(bonus, modifierId) {
    if (!bonus.counted) {
      return "sem booster de " + modifierId;
    }
    var parts = [];
    if (Math.abs(bonus.multiplier - 1) > UPGRADE_SIM_EPSILON) {
      parts.push("×" + upgradeSimFormat(bonus.multiplier));
    }
    if (Math.abs(bonus.flat) > UPGRADE_SIM_EPSILON) {
      parts.push((bonus.flat >= 0 ? "+" : "") + upgradeSimFormat(bonus.flat));
    }
    return modifierId + " " + (parts.length ? parts.join(" ") : "sem alteração");
  }

  function upgradeSimRecipeTime(recipe) {
    var time = Math.trunc(Number(recipe && recipe.time));
    return Number.isFinite(time) && time > 0 ? time : 0;
  }

  function upgradeSimRecipeEnergy(recipe) {
    var total = 0;
    var rows = recipe && Array.isArray(recipe.inputRows) ? recipe.inputRows : [];
    rows.forEach(function (row) {
      if (row && row.kind === "energy") {
        total += Math.max(0, Math.trunc(Number(row.amount) || 0));
      }
    });
    return total;
  }

  function upgradeSimRowAmount(row) {
    if (!row) {
      return 0;
    }
    if (row.kind === "item" || row.kind === "entity") {
      return Math.max(0, Math.trunc(Number(row.count) || 0));
    }
    return Math.max(0, Math.trunc(Number(row.amount) || 0));
  }

  function upgradeSimRowLabel(row) {
    if (!row) {
      return "?";
    }
    var kind = row.kind || "item";
    if (kind === "mana") {
      return "mana";
    }
    if (kind === "energy") {
      return "energia";
    }
    var id = trim(row.resource);
    if (!id && kind === "entity") {
      id = trim(row.spawn);
    }
    return kind + " · " + (id || "(vazio)");
  }

  function upgradeSimChanceText(row) {
    var chance = Number(row && row.chance);
    if (!Number.isFinite(chance) || chance >= 1) {
      return "";
    }
    return "chance " + upgradeSimPercent(chance);
  }

  function upgradeSimRowHtml(label, value, detail, warning) {
    return "<div class='upgrade-sim-row" + (warning ? " is-warning" : "") + "'><span>" +
      esc(label) + "</span><strong>" + esc(value) + "</strong>" +
      (detail ? "<small>" + esc(detail) + "</small>" : "") + "</div>";
  }

  function renderUpgradeSimulationCounts() {
    var countsEl = $("upgradeSimulationCounts");
    if (!countsEl) {
      return;
    }
    var upgrades = upgradesState();
    var boosters = upgrades && Array.isArray(upgrades.boosters) ? upgrades.boosters : [];
    var cells = upgradeSimulationCells();
    var active = [];
    boosters.forEach(function (booster, index) {
      if (isPlainObject(booster) && boosterEnabled(booster)) {
        active.push({ booster: booster, index: index });
      }
    });
    if (active.length === 0) {
      countsEl.innerHTML = "<p class='field-hint'>Nenhum booster ativo para simular.</p>";
      return;
    }
    countsEl.innerHTML = active.map(function (entry) {
      var limit = upgradeSimulationBoosterLimit(entry.booster, cells);
      var value = upgradeSimulationBoosterCount(entry.booster, entry.index, cells);
      return "<label class='upgrade-sim-count'><span>B" + (entry.index + 1) + " · " +
        esc(entry.booster.modifier || "output") + " · " + esc(entry.booster.mode || "additive") +
        "</span><input type='number' min='0' max='" + limit + "' step='1' value='" + value +
        "' data-sim-booster-index='" + entry.index + "' aria-label='Blocos do booster " +
        (entry.index + 1) + "'><small>blocos (0–" + limit + ")</small></label>";
    }).join("");
  }

  function renderUpgradeSimulationResults() {
    var resultsEl = $("upgradeSimulationResults");
    if (!resultsEl) {
      return;
    }
    var recipe = upgradeSimulationState.recipe;
    if (!recipe) {
      resultsEl.innerHTML = "<div class='upgrade-sim-empty'>Importe uma recipe para ver o efeito dos boosters.</div>";
      return;
    }
    var time = upgradeSimRecipeTime(recipe);
    var energy = upgradeSimRecipeEnergy(recipe);
    var inputs = Array.isArray(recipe.inputRows) ? recipe.inputRows.filter(function (row) {
      return row && row.kind !== "energy";
    }) : [];
    var outputs = Array.isArray(recipe.outputRows) ? recipe.outputRows : [];
    var speed = upgradeSimBonus("speed");
    var energyBonus = upgradeSimBonus("energy");
    var efficiency = upgradeSimBonus("efficiency");
    var output = upgradeSimBonus("output");
    var thread = upgradeSimBonus("thread");
    var damage = upgradeSimBonus("damage");
    var html = "";
    var general = "";
    general += upgradeSimRowHtml("tempo", time + " → " + upgradeSimReduced(time, speed) + " ticks",
      upgradeSimBonusText(speed, "speed"));
    general += upgradeSimRowHtml("energia", energy + " → " + upgradeSimReduced(energy, energyBonus) + " FE",
      upgradeSimBonusText(energyBonus, "energy"));
    general += upgradeSimRowHtml("threads", "1 → " + upgradeSimThreads(thread),
      upgradeSimBonusText(thread, "thread"));
    if (damage.counted) {
      general += upgradeSimRowHtml("dano das motions", "1 → " + upgradeSimFormat(upgradeSimDamage(1, damage)),
        upgradeSimBonusText(damage, "damage"));
    }
    html += "<div class='upgrade-sim-section'><strong>resultado</strong>" + general + "</div>";
    if (outputs.length > 0) {
      html += "<div class='upgrade-sim-section'><strong>outputs</strong>" + outputs.map(function (row) {
        var base = upgradeSimRowAmount(row);
        var scaled = upgradeSimScaledAmount(base, output);
        var detail = upgradeSimBonusText(output, "output");
        var chance = upgradeSimChanceText(row);
        if (chance) {
          detail += " · " + chance;
        }
        if (scaled.fraction > UPGRADE_SIM_EPSILON) {
          detail += " · +1 com " + upgradeSimPercent(scaled.fraction);
        }
        return upgradeSimRowHtml(upgradeSimRowLabel(row), base + " → " + scaled.whole, detail);
      }).join("") + "</div>";
    }
    if (inputs.length > 0) {
      html += "<div class='upgrade-sim-section'><strong>inputs</strong>" + inputs.map(function (row) {
        var base = upgradeSimRowAmount(row);
        return upgradeSimRowHtml(upgradeSimRowLabel(row), base + " → " + upgradeSimReduced(base, efficiency),
          upgradeSimBonusText(efficiency, "efficiency"));
      }).join("") + "</div>";
    }
    var notes = Array.isArray(upgradeSimulationState.notes) ? upgradeSimulationState.notes : [];
    if (notes.length > 0) {
      html += "<div class='upgrade-sim-section'><strong>avisos da importação</strong>" +
        notes.map(function (note) {
          return upgradeSimRowHtml(note.message || "Aviso", "", "", true);
        }).join("") + "</div>";
    }
    resultsEl.innerHTML = html;
  }

  function renderUpgradeSimulation() {
    var panel = $("upgradeSimulationPanel");
    if (panel) {
      panel.hidden = state.type !== "recipe";
    }
    var summaryEl = $("upgradeRecipeSummary");
    var recipe = upgradeSimulationState.recipe;
    if (summaryEl) {
      if (!recipe) {
        summaryEl.textContent = "Nenhuma recipe importada.";
      } else {
        var time = upgradeSimRecipeTime(recipe);
        var energy = upgradeSimRecipeEnergy(recipe);
        var inputs = Array.isArray(recipe.inputRows) ? recipe.inputRows.filter(function (row) {
          return row && row.kind !== "energy";
        }) : [];
        var outputs = Array.isArray(recipe.outputRows) ? recipe.outputRows : [];
        summaryEl.textContent = "time " + time + " ticks · energia " + energy + " FE · " +
          inputs.length + (inputs.length === 1 ? " input" : " inputs") + " · " +
          outputs.length + (outputs.length === 1 ? " output" : " outputs");
      }
    }
    renderUpgradeSimulationCounts();
    renderUpgradeSimulationResults();
  }

  function setUpgradeRecipeError(message) {
    var error = document.querySelector("[data-error-for='upgradeRecipeJson']");
    if (error) {
      error.textContent = message || "";
    }
    var textarea = $("upgradeRecipeJson");
    if (textarea) {
      textarea.classList.toggle("is-invalid", Boolean(message));
    }
  }

  function simulateUpgradeRecipe() {
    var textarea = $("upgradeRecipeJson");
    if (!textarea) {
      return;
    }
    var text = textarea.value.trim();
    if (!text) {
      upgradeSimulationState.recipe = null;
      upgradeSimulationState.notes = [];
      setUpgradeRecipeError("");
      renderUpgradeSimulation();
      return;
    }
    var raw;
    try {
      raw = JSON.parse(text);
    } catch (error) {
      setUpgradeRecipeError("JSON inválido: " + error.message);
      renderUpgradeSimulation();
      return;
    }
    var notes = [];
    var recipe = V.parseRecipe(raw, notes);
    upgradeSimulationState.recipe = recipe;
    upgradeSimulationState.notes = notes;
    setUpgradeRecipeError("");
    renderUpgradeSimulation();
  }

  function clearUpgradeRecipe() {
    if ($("upgradeRecipeJson")) {
      $("upgradeRecipeJson").value = "";
    }
    upgradeSimulationState.recipe = null;
    upgradeSimulationState.notes = [];
    upgradeSimulationState.counts = {};
    setUpgradeRecipeError("");
    renderUpgradeSimulation();
  }

  function onUpgradeRecipeFile(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    if (!file) {
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var textarea = $("upgradeRecipeJson");
      if (textarea) {
        textarea.value = String(reader.result || "");
      }
      input.value = "";
      simulateUpgradeRecipe();
    };
    reader.onerror = function () {
      input.value = "";
      setUpgradeRecipeError("Não foi possível ler o arquivo.");
    };
    reader.readAsText(file);
  }

  function onUpgradeSimulationCountInput(event) {
    var target = event.target;
    if (!target || target.tagName !== "INPUT") {
      return;
    }
    var index = parseInt(target.getAttribute("data-sim-booster-index"), 10);
    if (!Number.isFinite(index)) {
      return;
    }
    upgradeSimulationState.counts[index] = target.value;
    renderUpgradeSimulationResults();
  }

  function renderUpgradeMode() {
    var enabled = isPlainObject(state.upgrades);
    $$("#upgradeModeOptions input[name='upgradeMode']").forEach(function (input) {
      input.checked = input.value === (enabled ? "custom" : "none");
    });
    if ($("upgradeEditorArea")) {
      $("upgradeEditorArea").hidden = !enabled;
    }
    updateUpgradeSummary();
  }

  function setUpgradeMode(mode) {
    if (mode === "custom") {
      if (!isPlainObject(state.upgrades)) {
        state.upgrades = V.createUpgrades();
      }
      upgradeMode = "custom";
    } else {
      if (isPlainObject(state.upgrades) && Array.isArray(state.upgrades.boosters)
          && state.upgrades.boosters.length > 0
          && !window.confirm("Desativar os boosters? O campo upgrades será removido do JSON. As células da key no pattern não serão apagadas.")) {
        renderUpgradeMode();
        return;
      }
      state.upgrades = null;
      upgradeMode = "none";
    }
    refresh();
  }

  function onUpgradeModeChange(event) {
    var target = event.target;
    if (!target || target.name !== "upgradeMode" || !target.checked) {
      return;
    }
    setUpgradeMode(target.value);
  }

  function upgradeBoosterKindOptions(selected) {
    var options = [
      { value: "block", label: "Bloco" },
      { value: "tag", label: "Tag" },
      { value: "state", label: "Bloco + state" }
    ];
    return options.map(function (option) {
      return "<option value='" + option.value + "'" + (option.value === selected ? " selected" : "") +
        ">" + option.label + "</option>";
    }).join("");
  }

  function upgradeBoosterMatcherFieldHtml(booster, index) {
    var matcher = isPlainObject(booster.matcher) ? booster.matcher : null;
    var kind = matcher && matcher.kind ? matcher.kind : "block";
    if (kind === "invalid") {
      kind = "block";
    }
    if (kind === "tag") {
      return "<input type='text' data-booster-index='" + index + "' data-upgrade-field='tag' value='" +
        esc(matcher.tag || "") + "' placeholder='c:storage_blocks/gold' spellcheck='false' " +
        "autocomplete='off' aria-label='Tag do booster " + (index + 1) + "'>";
    }
    return "<input type='text' data-booster-index='" + index + "' data-upgrade-field='block' value='" +
      esc(matcher && matcher.block ? matcher.block : "") + "' placeholder='minecraft:gold_block' " +
      "title='Aceita o ID do bloco ou o JSON do /inmb look' spellcheck='false' " +
      "autocomplete='off' aria-label='Bloco do booster " + (index + 1) + "'>";
  }

  function upgradeBoosterStateHtml(booster, index) {
    var matcher = isPlainObject(booster.matcher) ? booster.matcher : null;
    if (!matcher || (matcher.kind !== "state" && matcher.kind !== "tag")) {
      return "";
    }
    var rows = Array.isArray(matcher.state) ? matcher.state : [];
    var html = "<div class='state-list'>";
    rows.forEach(function (row, rowIndex) {
      html += "<div class='state-row'><input type='text' data-booster-index='" + index +
        "' data-upgrade-field='state-key' data-index='" + rowIndex + "' value='" + esc(row.key || "") +
        "' placeholder='age' spellcheck='false' autocomplete='off' aria-label='Propriedade " +
        (rowIndex + 1) + " do booster " + (index + 1) + "'><input type='text' data-booster-index='" +
        index + "' data-upgrade-field='state-value' data-index='" + rowIndex + "' value='" +
        esc(row.value || "") + "' placeholder='7' spellcheck='false' autocomplete='off' aria-label='Valor " +
        (rowIndex + 1) + " do booster " + (index + 1) + "'><button type='button' class='icon-btn' " +
        "data-upgrade-action='remove-state' data-booster-index='" + index + "' data-index='" + rowIndex +
        "' title='Remover propriedade'>×</button></div>";
    });
    return html + "</div><button type='button' class='btn btn-tiny' data-upgrade-action='add-state' " +
      "data-booster-index='" + index + "'>+ propriedade</button>";
  }

  function upgradeBoosterCardHtml(booster, index) {
    if (!isPlainObject(booster)) {
      return "";
    }
    var info = upgradeModifierInfo(booster.modifier);
    var problem = V.upgradeBoosterProblem(booster);
    var enabled = boosterEnabled(booster);
    var matcher = isPlainObject(booster.matcher) ? booster.matcher : null;
    var kind = matcher && matcher.kind ? matcher.kind : "block";
    if (kind === "invalid") {
      kind = "block";
    }
    var allowedModifiers = (V.upgradeModifiersForType ? V.upgradeModifiersForType(state.type) : (V.UPGRADE_MODIFIERS || [])).slice();
    if (info && !allowedModifiers.some(function (entry) { return entry.id === info.id; })) {
      allowedModifiers.push(info);
    }
    var modifierOptions = allowedModifiers.map(function (entry) {
      return "<option value='" + entry.id + "'" + (entry.id === booster.modifier ? " selected" : "") +
        ">" + entry.label + " · " + entry.hint + "</option>";
    }).join("");
    var modeOptions = (V.UPGRADE_MODES || []).map(function (mode) {
      return "<option value='" + mode + "'" + (mode === booster.mode ? " selected" : "") +
        ">" + mode + "</option>";
    }).join("");
    var serialized = enabled
      ? JSON.stringify(V.buildUpgrades({ key: upgradeKey(), boosters: [booster] }).boosters[0])
      : null;
    return "<div class='matcher-card upgrade-card" + (enabled ? "" : " is-disabled") +
      (problem ? " is-problem" : "") +
      "' data-booster-index='" + index + "'><div class='matcher-head'><span class='swatch' style='--c:" +
      COLORS[3] + "'>B" + (index + 1) + "</span><div class='matcher-title'><strong>Booster " +
      (index + 1) + " · " + esc(booster.modifier || "output") + "</strong><small>" +
      esc((booster.mode || "additive") + " · max " + (booster.maxBlocks || "?") +
        (enabled ? "" : " · desativado")) + "</small></div>" +
      "<button type='button' class='btn btn-tiny' data-upgrade-action='toggle' data-booster-index='" + index +
      "' aria-pressed='" + (enabled ? "false" : "true") + "' title='" +
      (enabled ? "Desativar este booster" : "Ativar este booster") + "'>" +
      (enabled ? "desativar" : "ativar") + "</button>" +
      "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-upgrade-action='remove' " +
      "data-booster-index='" + index + "'>remover</button></div><div class='upgrade-form'>" +
      "<div class='field'><span class='field-label'>Matcher</span><select data-booster-index='" + index +
      "' data-upgrade-field='matcherKind' aria-label='Tipo do matcher do booster " + (index + 1) + "'>" +
      upgradeBoosterKindOptions(kind) + "</select></div>" +
      "<div class='field'><span class='field-label'>Bloco/Tag</span>" +
      upgradeBoosterMatcherFieldHtml(booster, index) + "</div>" +
      "<div class='field field-wide'><label>modifier</label><select data-booster-index='" + index +
      "' data-upgrade-field='modifier' aria-label='Modifier do booster " + (index + 1) + "'>" +
      modifierOptions + "</select></div>" +
      "<div class='field'><label>mode</label><select data-booster-index='" + index +
      "' data-upgrade-field='mode' aria-label='Mode do booster " + (index + 1) + "'>" +
      modeOptions + "</select></div>" +
      "<div class='field'><label for='upgradeValue" + index + "'>valor" + (info ? " (" + esc(info.field) + ")" : "") +
      "</label><input id='upgradeValue" + index + "' type='number' min='0.0001' step='any' value='" +
      esc(booster.value) + "' data-booster-index='" + index + "' data-upgrade-field='value'></div>" +
      "<div class='field'><label for='upgradeMax" + index + "'>max_blocks</label><input id='upgradeMax" + index +
      "' type='number' min='1' step='1' value='" + esc(booster.maxBlocks) + "' data-booster-index='" + index +
      "' data-upgrade-field='maxBlocks'></div></div>" +
      "<p class='matcher-feedback" + (problem ? " is-error" : "") + "'" + (problem ? "" : " hidden") +
      ">" + (problem ? esc(problem) : "") + "</p><details class='card-advanced'><summary>Estado e JSON" +
      "<span class='card-advanced-hint'>" + (kind === "state" || kind === "tag" ? "blockstate e exportação" : "exportação") +
      "</span></summary><div class='card-advanced-body'>" + upgradeBoosterStateHtml(booster, index) +
      "<code class='matcher-json'>" + esc(enabled ? (serialized === undefined ? "null" : serialized) : "Desativado: não entra no JSON.") +
      "</code></div></details></div>";
  }

  function renderUpgrades() {
    var upgrades = upgradesState();
    renderUpgradeMode();
    renderUpgradeSimulation();
    if (!upgrades) {
      if ($("upgradeKey")) {
        $("upgradeKey").value = "";
      }
      if ($("upgradeBoosterList")) {
        $("upgradeBoosterList").innerHTML = "";
      }
      return;
    }
    var key = trim(upgrades.key) || "";
    if ($("upgradeKey")) {
      $("upgradeKey").value = key;
      var keyInvalid = report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === "upgradeKey";
      });
      $("upgradeKey").classList.toggle("is-invalid", keyInvalid);
    }
    var boosters = Array.isArray(upgrades.boosters) ? upgrades.boosters : [];
    if ($("upgradeBoosterList")) {
      $("upgradeBoosterList").innerHTML = boosters.map(upgradeBoosterCardHtml).join("");
    }
    updateUpgradeSummary();
  }

  function updateUpgradeCard(index) {
    var upgrades = upgradesState();
    var booster = upgrades && Array.isArray(upgrades.boosters) ? upgrades.boosters[index] : null;
    var card = document.querySelector(".upgrade-card[data-booster-index='" + index + "']");
    if (!booster || !card) {
      return;
    }
    var problem = V.upgradeBoosterProblem(booster);
    card.classList.toggle("is-problem", Boolean(problem));
    var title = card.querySelector(".matcher-title strong");
    if (title) {
      title.textContent = "Booster " + (index + 1) + " · " + (booster.modifier || "output");
    }
    var subtitle = card.querySelector(".matcher-title small");
    if (subtitle) {
      subtitle.textContent = (booster.mode || "additive") + " · max " + (booster.maxBlocks || "?");
    }
    var code = card.querySelector(".matcher-json");
    if (code) {
      var built = V.buildUpgrades({ key: upgradeKey(), boosters: [booster] });
      code.textContent = built ? JSON.stringify(built.boosters[0]) : "";
    }
    var feedback = card.querySelector(".matcher-feedback");
    if (feedback) {
      feedback.textContent = problem || "";
      feedback.classList.toggle("is-error", Boolean(problem));
      feedback.hidden = !problem;
    }
  }

  function onUpgradeInput(event) {
    var target = event.target;
    if (!target || target.tagName !== "INPUT") {
      return;
    }
    var field = target.getAttribute("data-upgrade-field");
    if (field === "key") {
      var upgrades = upgradesState();
      if (!upgrades) {
        return;
      }
      upgrades.key = target.value;
      updateUpgradeSummary();
      refresh({ skipPattern: true, skipMatchers: true, skipUpgrades: true });
      return;
    }
    var index = parseInt(target.getAttribute("data-booster-index"), 10);
    var source = upgradesState();
    var booster = source && Array.isArray(source.boosters) ? source.boosters[index] : null;
    if (!booster || !isPlainObject(booster.matcher)) {
      return;
    }
    if (field === "block") {
      var rawBlock = target.value.trim();
      if (rawBlock.charAt(0) === "{") {
        var pastedMatcher = V.parseMatcherInput(rawBlock);
        if (pastedMatcher.kind !== "invalid") {
          booster.matcher = pastedMatcher;
          refresh();
          return;
        }
      }
      if (booster.matcher.kind !== "state") {
        booster.matcher.kind = "block";
      }
      booster.matcher.block = target.value;
    } else if (field === "tag") {
      booster.matcher.tag = target.value;
    } else if (field === "state-key" || field === "state-value") {
      var rowIndex = parseInt(target.getAttribute("data-index"), 10);
      var row = Array.isArray(booster.matcher.state) ? booster.matcher.state[rowIndex] : null;
      if (!row) {
        return;
      }
      if (field === "state-key") {
        row.key = target.value;
      } else {
        row.value = target.value;
      }
    } else if (field === "value") {
      booster.value = target.value;
    } else if (field === "maxBlocks") {
      booster.maxBlocks = target.value;
    } else {
      return;
    }
    updateUpgradeCard(index);
    updateUpgradeSummary();
    refresh({ skipPattern: true, skipMatchers: true, skipUpgrades: true });
  }

  function onUpgradeChange(event) {
    var target = event.target;
    if (!target || target.tagName !== "SELECT") {
      return;
    }
    var field = target.getAttribute("data-upgrade-field");
    var index = parseInt(target.getAttribute("data-booster-index"), 10);
    var source = upgradesState();
    var booster = source && Array.isArray(source.boosters) ? source.boosters[index] : null;
    if (!booster) {
      return;
    }
    if (field === "matcherKind") {
      booster.matcher.kind = target.value;
      if (target.value === "state" || target.value === "tag") {
        if (!Array.isArray(booster.matcher.state)) {
          booster.matcher.state = [];
        }
      }
    } else if (field === "modifier") {
      booster.modifier = target.value;
    } else if (field === "mode") {
      booster.mode = target.value;
    } else {
      return;
    }
    refresh();
    var card = document.querySelector(".upgrade-card[data-booster-index='" + index + "']");
    var next = card ? card.querySelector("[data-upgrade-field='block'], [data-upgrade-field='tag']") : null;
    if (next) {
      next.focus();
    }
  }

  function onUpgradeClick(event) {
    var action = closest(event.target, "[data-upgrade-action]");
    if (!action) {
      return;
    }
    var name = action.getAttribute("data-upgrade-action");
    var index = parseInt(action.getAttribute("data-booster-index"), 10);
    var source = upgradesState();
    if (!source || !Array.isArray(source.boosters)) {
      return;
    }
    var booster = source.boosters[index];
    if (!booster) {
      return;
    }
    if (name === "remove") {
      if (!window.confirm("Remover o booster " + (index + 1) + "?")) {
        return;
      }
      source.boosters.splice(index, 1);
      upgradeSimulationState.counts = {};
      refresh();
      return;
    }
    if (name === "toggle") {
      booster.enabled = booster.enabled === false;
      refresh();
      var toggle = document.querySelector(".upgrade-card[data-booster-index='" + index +
        "'] [data-upgrade-action='toggle']");
      if (toggle) {
        toggle.focus();
      }
      return;
    }
    if (name === "add-state") {
      if (!Array.isArray(booster.matcher.state)) {
        booster.matcher.state = [];
      }
      booster.matcher.state.push({ key: "", value: "" });
      refresh();
      var refreshed = document.querySelector(".upgrade-card[data-booster-index='" + index + "']");
      var details = refreshed ? refreshed.querySelector("details.card-advanced") : null;
      if (details) {
        details.open = true;
      }
      var keys = refreshed ? refreshed.querySelectorAll("[data-upgrade-field='state-key']") : [];
      if (keys.length > 0) {
        keys[keys.length - 1].focus();
      }
      return;
    }
    if (name === "remove-state") {
      var rowIndex = parseInt(action.getAttribute("data-index"), 10);
      if (Array.isArray(booster.matcher.state) && rowIndex >= 0 && rowIndex < booster.matcher.state.length) {
        booster.matcher.state.splice(rowIndex, 1);
        refresh();
        var updated = document.querySelector(".upgrade-card[data-booster-index='" + index + "']");
        var stateDetails = updated ? updated.querySelector("details.card-advanced") : null;
        if (stateDetails) {
          stateDetails.open = true;
        }
      }
    }
  }

  function addUpgradeBooster() {
    var upgrades = upgradesState();
    if (!upgrades) {
      return;
    }
    if (!Array.isArray(upgrades.boosters)) {
      upgrades.boosters = [];
    }
    upgrades.boosters.push(V.createUpgradeBooster(state.type));
    upgradeSimulationState.counts = {};
    refresh();
    var cards = $("upgradeBoosterList").querySelectorAll(".upgrade-card");
    var last = cards[cards.length - 1];
    if (last) {
      focusElement(last.querySelector("[data-upgrade-field='block']") || last, false);
    }
  }

  function paintUpgradeKey() {
    var key = upgradeKey();
    if (!V.isSingleSymbol(key) || key === " ") {
      showToast("Defina uma key de upgrade válida antes de pintar.", "error");
      return;
    }
    activeSymbol = key;
    setStep("pattern");
    showToast("Pinte as células \"" + key + "\" no pattern.");
  }

  function motionInt(value, fallback) {
    var number = typeof value === "number" ? value : Number(value);
    return Number.isFinite(number) ? Math.trunc(number) : fallback;
  }

  function motionNumber(value, fallback) {
    var number = typeof value === "number" ? value : Number(value);
    return Number.isFinite(number) ? number : fallback;
  }

  function motionsState() {
    if (!Array.isArray(state.motions)) {
      state.motions = [];
    }
    return state.motions;
  }

  function resetMotionPreviewSelection() {
    motionPreviewState.selectedMotionIndex = 0;
    motionPreviewState.selectedMotionId = "";
    motionPreviewState.selectedKeyframe = 0;
    motionPreviewState.time = 0;
    motionPreviewState.lastFrame = 0;
    motionPreviewState.hiddenKeys = {};
  }

  function deriveMotionMode() {
    return motionsState().length > 0 ? "custom" : "none";
  }

  function renderMotionMode() {
    $$("input[name='motionMode']").forEach(function (input) {
      input.checked = input.value === motionMode;
    });
    var summary = $("motionModeSummary");
    if (summary) {
      summary.textContent = motionMode === "custom"
        ? (state.type === "storage"
          ? "Motions sempre ativas no storage. Use fill_speed para acelerar conforme o armazenamento enche."
          : "Motions ativas durante receitas. Configure keys, pivot, keyframes, dano e NBT.")
        : "Sem motions. A máquina fica estática e o JSON não inclui animações.";
    }
    var area = $("motionEditorArea");
    if (area) {
      area.hidden = motionMode !== "custom";
    }
  }

  function setMotionMode(mode) {
    if (mode !== "none" && mode !== "custom") {
      return;
    }
    if (mode === motionMode) {
      renderMotionMode();
      return;
    }
    if (mode === "none" && motionsState().length > 0) {
      if (!window.confirm("Desativar as animações? As motions configuradas serão removidas.")) {
        renderMotionMode();
        return;
      }
      state.motions = [];
      resetMotionPreviewSelection();
    }
    motionMode = mode;
    if (mode === "none") {
      setMotionFullscreen(false);
      setMotionPlaying(false);
    }
    refresh();
  }

  function onMotionModeChange(event) {
    var input = closest(event.target, "input[name='motionMode']");
    if (!input) {
      return;
    }
    setMotionMode(input.value);
  }

  function selectedMotion() {
    var motions = motionsState();
    if (motions.length === 0) {
      motionPreviewState.selectedMotionIndex = 0;
      motionPreviewState.selectedMotionId = "";
      return null;
    }
    var index = clamp(motionPreviewState.selectedMotionIndex || 0, 0, motions.length - 1);
    motionPreviewState.selectedMotionIndex = index;
    var motion = motions[index] || null;
    motionPreviewState.selectedMotionId = motion ? motion.id : "";
    return motion;
  }

  function motionIndexById(id) {
    var motions = motionsState();
    for (var index = 0; index < motions.length; index += 1) {
      if (motions[index] && motions[index].id === id) {
        return index;
      }
    }
    return -1;
  }

  function nextMotionId() {
    var motions = motionsState();
    var index = 1;
    while (motions.some(function (motion) {
      return motion && motion.id === "motion_" + index;
    })) {
      index += 1;
    }
    return "motion_" + index;
  }

  function motionDuration(motion) {
    if (!motion || !isPlainObject(motion.animation)) {
      return 0;
    }
    return Math.max(0, motionInt(motion.animation.duration, 0));
  }

  function motionBounds(motion) {
    if (!motion || !Array.isArray(motion.keys)) {
      return null;
    }
    var keys = motion.keys;
    var bounds = {
      minX: Infinity,
      minY: Infinity,
      minZ: Infinity,
      maxX: -Infinity,
      maxY: -Infinity,
      maxZ: -Infinity
    };
    var layers = Array.isArray(state.layers) ? state.layers : [];
    layers.forEach(function (layer, y) {
      if (!Array.isArray(layer)) {
        return;
      }
      layer.forEach(function (row, z) {
        if (typeof row !== "string") {
          return;
        }
        for (var x = 0; x < row.length; x += 1) {
          if (keys.indexOf(row.charAt(x)) === -1) {
            continue;
          }
          bounds.minX = Math.min(bounds.minX, x);
          bounds.minY = Math.min(bounds.minY, y);
          bounds.minZ = Math.min(bounds.minZ, z);
          bounds.maxX = Math.max(bounds.maxX, x);
          bounds.maxY = Math.max(bounds.maxY, y);
          bounds.maxZ = Math.max(bounds.maxZ, z);
        }
      });
    });
    if (!Number.isFinite(bounds.minX)) {
      return null;
    }
    bounds.sx = bounds.maxX - bounds.minX + 1;
    bounds.sy = bounds.maxY - bounds.minY + 1;
    bounds.sz = bounds.maxZ - bounds.minZ + 1;
    return bounds;
  }

  function motionDefaultPivot(motion) {
    var bounds = motionBounds(motion);
    if (!bounds) {
      return { x: 0, y: 0, z: 0 };
    }
    return {
      x: bounds.sx / 2,
      y: bounds.sy / 2,
      z: bounds.sz / 2
    };
  }

  function motionPivotForUi(motion) {
    var animation = isPlainObject(motion.animation) ? motion.animation : {};
    if (isPlainObject(animation.pivot)) {
      return {
        value: {
          x: motionNumber(animation.pivot.x, 0),
          y: motionNumber(animation.pivot.y, 0),
          z: motionNumber(animation.pivot.z, 0)
        },
        automatic: false
      };
    }
    return {
      value: motionDefaultPivot(motion),
      automatic: true
    };
  }

  function collectMotionSymbols() {
    var set = {};
    Object.keys(currentUsage).forEach(function (symbol) {
      if (symbol !== " " && symbol !== state.anchor && currentUsage[symbol] > 0) {
        set[symbol] = true;
      }
    });
    return Object.keys(set).sort(compareSymbols);
  }

  function motionKeyOwner(symbol, motion) {
    var motions = motionsState();
    for (var index = 0; index < motions.length; index += 1) {
      var other = motions[index];
      if (!other || other === motion || !Array.isArray(other.keys)) {
        continue;
      }
      if (other.keys.indexOf(symbol) !== -1) {
        return other.id || "motion " + (index + 1);
      }
    }
    return "";
  }

  function motionFeedback(motion, index) {
    if (!report || !Array.isArray(report.issues)) {
      return null;
    }
    var prefix = "motion:" + index;
    var issues = report.issues.filter(function (entry) {
      return entry.step === "motions" && entry.field &&
        (entry.field === prefix || entry.field.indexOf(prefix + ".") === 0);
    });
    if (issues.length === 0) {
      return null;
    }
    var entry = issues.find(function (candidate) {
      return candidate.severity === "error";
    }) || issues[0];
    return {
      tone: entry.severity === "error" ? "is-error" : entry.severity === "warning" ? "is-warning" : "",
      text: entry.message
    };
  }

  function motionGlobalFeedback() {
    if (!report || !Array.isArray(report.issues)) {
      return null;
    }
    return report.issues.find(function (entry) {
      return entry.step === "motions" && entry.field === "motions";
    }) || null;
  }

  function updateMotionBadge() {
    var badge = $("motionsBadge");
    if (!badge) {
      return;
    }
    var check = report && Array.isArray(report.checks) ? report.checks.find(function (entry) {
      return entry.id === "motions";
    }) : null;
    var count = motionsState().length;
    if (check && !check.ok) {
      badge.textContent = "Erro";
      badge.setAttribute("data-tone", "error");
      badge.className = "badge badge-neutral";
    } else if (count > 0) {
      badge.textContent = count + (count === 1 ? " motion" : " motions");
      badge.setAttribute("data-tone", "ok");
      badge.className = "badge";
    } else {
      badge.textContent = "Opcional";
      badge.setAttribute("data-tone", "warn");
      badge.className = "badge badge-neutral";
    }
  }

  function updateMotionListOnly() {
    var list = $("motionList");
    if (list) {
      list.innerHTML = motionListHtml();
    }
    updateMotionBadge();
  }

  function updateMotionStageMeta() {
    var meta = $("motionStageMeta");
    if (!meta) {
      return;
    }
    var motion = selectedMotion();
    if (!motion) {
      meta.textContent = "Nenhuma motion. Use editar para montar a primeira.";
      return;
    }
    var keys = Array.isArray(motion.keys) && motion.keys.length > 0 ? motion.keys.join(", ") : "sem keys";
    var bounds = motionBounds(motion);
    var size = bounds ? bounds.sx + "×" + bounds.sy + "×" + bounds.sz : "sem blocos";
    var pivot = motionPivotForUi(motion);
    var pivotText = pivot.value.x + ", " + pivot.value.y + ", " + pivot.value.z +
      (pivot.automatic ? " (centro automático)" : "");
    meta.textContent = (trim(motion.id) || "sem id") + " · keys " + keys + " · " +
      motionDuration(motion) + " ticks · parte " + size + " · pivot " + pivotText;
  }

  function updateMotionSummary() {
    var summary = $("motionSummary");
    if (!summary) {
      return;
    }
    var motion = selectedMotion();
    var animation = motion && isPlainObject(motion.animation) ? motion.animation : null;
    var frames = animation && Array.isArray(animation.keyframes) ? animation.keyframes : [];
    if (!motion || frames.length === 0) {
      summary.hidden = true;
      summary.textContent = "";
      return;
    }
    var parts = frames.map(function (frame, index) {
      var offset = isPlainObject(frame.offset) ? frame.offset : {};
      var rotation = isPlainObject(frame.rotation) ? frame.rotation : {};
      var easing = trim(frame.easing);
      return "#" + index + " t=" + motionInt(frame.time, 0) +
        " off(" + motionNumber(offset.x, 0) + "," + motionNumber(offset.y, 0) + "," +
        motionNumber(offset.z, 0) + ")" +
        " rot(" + motionNumber(rotation.x, 0) + "," + motionNumber(rotation.y, 0) + "," +
        motionNumber(rotation.z, 0) + ")" +
        (easing ? " " + easing : "");
    });
    summary.innerHTML = "<strong>Keyframes:</strong> " + esc(parts.join(" | "));
    summary.hidden = false;
  }

  function motionEasingOptions(selected, allowInherit) {
    var parts = [];
    if (allowInherit) {
      parts.push("<option value=''" + (!selected ? " selected" : "") + ">herdar</option>");
    }
    V.EASING_VALUES.forEach(function (value) {
      parts.push("<option value='" + value + "'" + (selected === value ? " selected" : "") + ">" +
        value + "</option>");
    });
    return parts.join("");
  }

  function motionDamageSourceOptions(selected) {
    return MOTION_DAMAGE_SOURCES.map(function (id) {
      return "<option value='" + id + "'" + (id === selected ? " selected" : "") + ">" +
        esc(id) + "</option>";
    }).join("");
  }

  function motionKeysHtml(motion) {
    var symbols = collectMotionSymbols();
    var keys = Array.isArray(motion.keys) ? motion.keys : [];
    keys.forEach(function (key) {
      if (symbols.indexOf(key) === -1) {
        symbols.push(key);
      }
    });
    symbols.sort(compareSymbols);
    if (symbols.length === 0) {
      return "<p class='field-hint'>O pattern ainda não tem símbolos além da âncora. Pinte o pattern primeiro.</p>";
    }
    return "<div class='motion-key-list'>" + symbols.map(function (symbol) {
      var active = keys.indexOf(symbol) !== -1;
      var owner = motionKeyOwner(symbol, motion);
      var count = currentUsage[symbol] || 0;
      var hidden = motionPreviewState.hiddenKeys[symbol] === true;
      var classes = "motion-key-chip" + (active ? " is-active" : "") +
        (owner ? " is-taken" : "") + (count === 0 ? " is-missing" : "") +
        (hidden ? " is-hidden" : "");
      var title = symbolHover(symbol) + " · " + count + " bloco(s)" +
        (owner ? " · já usado por " + owner : "");
      return "<span class='" + classes + "'>" +
        "<button type='button' class='motion-key-main' data-motion-key='" + esc(symbol) +
        "' title='" + esc(title) + "' aria-pressed='" + (active ? "true" : "false") + "'>" +
        "<span class='motion-key-symbol' style='--c:" + getColor(symbol) + "'>" + esc(symbol) +
        "</span><small>" + count + "×" + (owner ? " · " + esc(owner) : "") + "</small></button>" +
        "<button type='button' class='motion-key-eye" + (hidden ? " is-off" : "") +
        "' data-motion-key-visibility='" + esc(symbol) + "' title='" +
        (hidden ? "Mostrar " : "Ocultar ") + esc(symbol) + " no preview' aria-pressed='" +
        (hidden ? "true" : "false") + "'>" + (hidden ? "off" : "on") + "</button></span>";
    }).join("") + "</div>";
  }

  function motionKeyframeRowsHtml(motion) {
    var animation = isPlainObject(motion.animation) ? motion.animation : {};
    var frames = Array.isArray(animation.keyframes) ? animation.keyframes : [];
    if (frames.length === 0) {
      return "<p class='field-hint'>Nenhum keyframe. A parte fica parada até você adicionar um.</p>";
    }
    return frames.map(function (frame, index) {
      var active = index === motionPreviewState.selectedKeyframe;
      var offset = isPlainObject(frame.offset) ? frame.offset : { x: 0, y: 0, z: 0 };
      var rotation = isPlainObject(frame.rotation) ? frame.rotation : { x: 0, y: 0, z: 0 };
      return "<div class='motion-keyframe-row" + (active ? " is-active" : "") +
        "' data-keyframe-row='" + index + "'>" +
        "<div class='motion-keyframe-head'><strong>#" + index + "</strong>" +
        "<button type='button' class='btn btn-tiny btn-danger' data-remove-keyframe='" + index +
        "' title='Remover keyframe'>remover</button></div>" +
        "<div class='motion-keyframe-grid'>" +
        "<label>time<input type='number' min='0' step='1' data-keyframe-field='time' data-keyframe-index='" +
        index + "' value='" + esc(motionInt(frame.time, 0)) + "'></label>" +
        ["x", "y", "z"].map(function (axis) {
          return "<label>offset " + axis + "<input type='number' step='0.1' data-keyframe-field='offset' data-keyframe-axis='" +
            axis + "' data-keyframe-index='" + index + "' value='" + esc(motionNumber(offset[axis], 0)) + "'></label>";
        }).join("") +
        ["x", "y", "z"].map(function (axis) {
          return "<label>rotação " + axis + "<input type='number' step='1' data-keyframe-field='rotation' data-keyframe-axis='" +
            axis + "' data-keyframe-index='" + index + "' value='" + esc(motionNumber(rotation[axis], 0)) + "'></label>";
        }).join("") +
        "<label>easing<select data-keyframe-field='easing' data-keyframe-index='" + index + "'>" +
        motionEasingOptions(trim(frame.easing), true) + "</select></label>" +
        "</div></div>";
    }).join("");
  }

  function motionRecipeScaleOptions() {
    var recipes = Array.isArray(state.recipes) ? state.recipes : [];
    var options = ["<option value=''>escalar para receita…</option>"];
    recipes.forEach(function (recipe, index) {
      if (!isPlainObject(recipe)) {
        return;
      }
      var time = motionInt(recipe.time, 0);
      if (time <= 0) {
        return;
      }
      var label = trim(recipe.file) || ("receita " + (index + 1));
      options.push("<option value='" + time + "'>" + esc(label) + " · " + time + " ticks</option>");
    });
    return options.join("");
  }

  function motionDetailHtml(motion) {
    if (!motion) {
      return "<div class='missing-empty'>Nenhuma motion ainda. Clique em <strong>+ motion</strong> para criar a primeira parte móvel.</div>";
    }
    var index = motionPreviewState.selectedMotionIndex;
    var feedback = motionFeedback(motion, index);
    var animation = isPlainObject(motion.animation) ? motion.animation : {};
    var pivot = motionPivotForUi(motion);
    var serialized = JSON.stringify(V.buildMotions([motion])[0], null, 2);
    var hasRecipeTime = (Array.isArray(state.recipes) ? state.recipes : []).some(function (recipe) {
      return isPlainObject(recipe) && motionInt(recipe.time, 0) > 0;
    });
    var damageSource = trim(motion.damageSource) || "minecraft:generic";
    var customDamageSource = MOTION_DAMAGE_SOURCES.indexOf(damageSource) === -1;
    return "<div class='motion-detail'>" +
      "<div class='motion-detail-actions'>" +
      "<button type='button' class='btn btn-tiny' data-motion-action='duplicate-motion'>duplicar</button>" +
      "<button type='button' class='btn btn-tiny btn-danger' data-motion-action='remove-motion'>remover motion</button>" +
      "</div>" +
      "<div class='form-grid motion-form-grid'>" +
      "<div class='field'><label for='motionIdInput'>id</label><input id='motionIdInput' type='text' data-motion-field='id' value='" +
      esc(trim(motion.id)) + "' spellcheck='false' autocomplete='off' placeholder='core_spin'></div>" +
      "<div class='field'><label>duração (ticks)</label><input type='number' min='0' step='1' data-motion-field='duration' value='" +
      esc(motionInt(animation.duration, 0)) + "'></div>" +
      "<div class='field field-wide'><label>tempo da receita</label><select data-motion-field='scale_recipe'" +
      (hasRecipeTime ? "" : " disabled") + ">" + motionRecipeScaleOptions() + "</select>" +
      "<p class='field-hint'>" + (hasRecipeTime
        ? "Reduz ou expande a duração total e recalcula o time de cada keyframe."
        : "Nenhuma receita com time definido para escalar.") + "</p></div>" +
      "<div class='field'><label>easing da animação</label><select data-motion-field='easing'>" +
      motionEasingOptions(trim(animation.easing) || "linear", false) + "</select></div>" +
      "<div class='field'><label>loop</label><label class='motion-check'><input type='checkbox' data-motion-field='loop'" +
      (animation.loop !== false ? " checked" : "") + "> repetir ao chegar em duration</label></div>" +
      (state.type === "storage"
        ? "<div class='field field-wide'><label>fill_speed</label><label class='motion-check'><input type='checkbox' data-motion-field='fill_speed'" +
          (animation.fillSpeed === true ? " checked" : "") +
          "> acelerar a animação conforme o inventário/tanques enchem (até 2x)</label></div>"
        : "") +
      "<div class='field'><label>damage</label><input type='number' min='0' step='0.1' data-motion-field='damage' value='" +
      esc(motionNumber(motion.damage, 4)) + "'></div>" +
      "<div class='field'><label>damage_interval</label><input type='number' min='1' step='1' data-motion-field='damage_interval' value='" +
      esc(motionInt(motion.damageInterval, 10)) + "'></div>" +
      "<div class='field field-wide'><label>damage_source</label>" +
      "<select data-motion-damage-select>" +
      motionDamageSourceOptions(customDamageSource ? "" : damageSource) +
      "<option value='__custom'" + (customDamageSource ? " selected" : "") + ">Personalizado...</option>" +
      "</select>" +
      "<input type='text' class='motion-damage-custom' data-motion-field='damage_source' value='" +
      esc(customDamageSource ? damageSource : "") + "' placeholder='minecraft:generic' spellcheck='false' autocomplete='off'" +
      (customDamageSource ? "" : " hidden") + ">" +
      "<p class='field-hint'>Damage type usado no dano das entidades. Se o ID não existir, cai para player_attack quando houver jogador perto; senão generic.</p></div>" +
      "<div class='field field-wide'><label>preserve_nbt</label><label class='motion-check'><input type='checkbox' data-motion-field='preserve_nbt'" +
      (motion.preserveNbt ? " checked" : "") + "> salvar e restaurar NBT dos blocos da parte</label></div>" +
      "<div class='field field-wide'><label>damage_entities</label><label class='motion-check'><input type='checkbox' data-motion-field='damage_entities'" +
      (motion.damageEntities ? " checked" : "") + "> causar dano em entidades dentro da parte</label></div>" +
      "</div>" +
      "<div class='motion-section'><div class='motion-section-head'><div><h3>Keys da parte</h3>" +
      "<p class='field-hint'>Cada símbolo pertence a no máximo uma motion. A âncora não pode se mover. Use on/off para esconder no preview.</p></div>" +
      "<div class='button-row'><button type='button' class='btn btn-tiny' data-motion-action='show-all-keys'>ver todas</button>" +
      "<button type='button' class='btn btn-tiny' data-motion-action='hide-all-keys'>ocultar todas</button></div></div>" +
      motionKeysHtml(motion) + "</div>" +
      "<div class='motion-section'><div class='motion-section-head'><div><h3>Pivot</h3>" +
      "<p class='field-hint'>Em blocos a partir do canto mínimo da parte. " +
      (pivot.automatic ? "Agora usa o centro automático da caixa." : "Pivot explícito.") + "</p></div>" +
      "<div class='button-row'><button type='button' class='btn btn-tiny' data-motion-action='center-pivot'>usar centro</button>" +
      "<button type='button' class='btn btn-tiny' data-motion-action='clear-pivot'>automático</button></div></div>" +
      "<div class='motion-pivot-grid'>" +
      ["x", "y", "z"].map(function (axis) {
        return "<label>" + axis + "<input type='number' step='0.5' data-motion-field='pivot_" + axis + "' value='" +
          esc(pivot.value[axis]) + "'></label>";
      }).join("") + "</div></div>" +
      "<div class='motion-section'><div class='motion-section-head'><div><h3>Keyframes</h3>" +
      "<p class='field-hint'>Ordenados por time (0..duration). offset em blocos, rotação em graus.</p></div>" +
      "<div class='button-row'><button type='button' class='btn btn-tiny' data-add-keyframe='true'>+ keyframe</button></div></div>" +
      "<div class='motion-keyframe-list'>" + motionKeyframeRowsHtml(motion) + "</div></div>" +
      "<p class='matcher-feedback motion-feedback " + (feedback ? feedback.tone : "") + "'" +
      (feedback ? "" : " hidden") + ">" + (feedback ? esc(feedback.text) : "") + "</p>" +
      "<details class='card-advanced'><summary>JSON da motion<span class='card-advanced-hint'>" +
      esc(trim(motion.id) || "sem id") + "</span></summary><div class='card-advanced-body'>" +
      "<code class='matcher-json motion-json'>" + esc(serialized) + "</code></div></details>" +
      "</div>";
  }

  function motionListHtml() {
    var motions = motionsState();
    if (motions.length === 0) {
      return "<div class='missing-empty'>Nenhuma motion. O multibloco fica parado; adicione uma para animar keys do pattern.</div>";
    }
    return motions.map(function (motion, index) {
      if (!isPlainObject(motion)) {
        return "";
      }
      var active = index === motionPreviewState.selectedMotionIndex;
      var feedback = motionFeedback(motion, index);
      var keys = Array.isArray(motion.keys) && motion.keys.length > 0 ? motion.keys.join(" ") : "sem keys";
      var frames = isPlainObject(motion.animation) && Array.isArray(motion.animation.keyframes)
        ? motion.animation.keyframes.length
        : 0;
      return "<button type='button' class='motion-card" + (active ? " is-active" : "") +
        (feedback ? " is-problem" : "") + "' data-motion-index='" + index + "'>" +
        "<span class='motion-card-id'>" + esc(trim(motion.id) || "sem id") + "</span>" +
        "<span class='motion-card-keys'>" + esc(keys) + "</span>" +
        "<span class='motion-card-meta'>" + motionDuration(motion) + " ticks · " +
        frames + (frames === 1 ? " keyframe" : " keyframes") +
        (isPlainObject(motion.animation) && motion.animation.fillSpeed === true ? " · fill" : "") + "</span>" +
        "</button>";
    }).join("");
  }

  function motionTimelineHtml(motion) {
    if (!motion) {
      return "<div class='motion-timeline-empty'>Selecione uma motion para ver a linha do tempo.</div>";
    }
    var duration = motionDuration(motion);
    var animation = isPlainObject(motion.animation) ? motion.animation : {};
    var frames = Array.isArray(animation.keyframes) ? animation.keyframes : [];
    var markers = frames.map(function (frame, index) {
      var time = motionNumber(frame.time, 0);
      var percent = duration > 0 ? clamp((time / duration) * 100, 0, 100) : 0;
      return "<button type='button' class='motion-marker' data-keyframe-jump='" + index +
        "' style='left:" + percent + "%' title='Keyframe #" + index + " · time " + time + "'>" +
        "<span>" + time + "</span></button>";
    }).join("");
    return "<div class='motion-timeline-track'>" +
      "<div class='motion-timeline-fill'></div>" + markers +
      "<div class='motion-playhead' id='motionPlayhead'></div></div>" +
      "<div class='motion-timeline-scale'><span>0</span><span>" + duration + " ticks</span></div>";
  }

  function updateMotionPlayhead() {
    var motion = selectedMotion();
    var duration = motionDuration(motion);
    var time = clamp(motionPreviewState.time, 0, duration);
    var range = $("motionTimeRange");
    if (range) {
      range.max = String(duration);
      if (document.activeElement !== range) {
        range.value = String(time);
      }
    }
    var label = $("motionTimeLabel");
    if (label) {
      label.textContent = Math.round(time) + " / " + duration + " ticks";
    }
    var playhead = $("motionPlayhead");
    if (playhead) {
      var percent = duration > 0 ? clamp((time / duration) * 100, 0, 100) : 0;
      playhead.style.left = percent + "%";
    }
  }

  function updateMotionTimeline() {
    var timeline = $("motionTimeline");
    if (timeline) {
      timeline.innerHTML = motionTimelineHtml(selectedMotion());
    }
    updateMotionPlayhead();
  }

  function canUseMotionPreviewWebGL() {
    if (motionPreviewWebGLAvailable === null) {
      motionPreviewWebGLAvailable = Boolean(
        MotionPreviewWebGL &&
        typeof MotionPreviewWebGL.supported === "function" &&
        MotionPreviewWebGL.supported()
      );
    }
    return motionPreviewWebGLAvailable && !motionPreviewWebGLFailed;
  }

  function replaceMotionPreviewCanvas() {
    var old = $("motionPreviewCanvas");
    if (!old || !old.parentNode) {
      return;
    }
    var fresh = document.createElement("canvas");
    fresh.id = "motionPreviewCanvas";
    fresh.setAttribute("aria-label", old.getAttribute("aria-label") || "Preview 3D das animações");
    old.parentNode.replaceChild(fresh, old);
  }

  function motionPreviewScene(time, options) {
    var settings = options || {};
    var motion = selectedMotion();
    var hiddenSymbols = settings.hiddenSymbols || motionPreviewState.hiddenKeys;
    var hiddenCount = Object.keys(hiddenSymbols).length;
    return {
      layers: state.layers,
      colors: currentColors,
      anchor: state.anchor,
      motions: motionsState(),
      time: time,
      view: settings.view || motionPreviewState.view,
      selectedMotionId: settings.selectedMotionId !== undefined
        ? settings.selectedMotionId
        : (motion ? motion.id : ""),
      hiddenSymbols: hiddenSymbols,
      opacity: settings.opacity !== undefined ? settings.opacity : motionPreviewState.opacity,
      emptyText: hiddenCount > 0 ? "Todas as keys visíveis estão ocultas. Use 'ver todas'." : "",
      fallback: COLORS[1],
      revision: motionPreviewState.revision
    };
  }

  function drawMotionPreview() {
    if (!state) {
      return;
    }
    var scene = motionPreviewScene(motionPreviewState.time);
    var canvas = $("motionPreviewCanvas");
    var drew = false;
    if (canvas && canUseMotionPreviewWebGL()) {
      try {
        drew = MotionPreviewWebGL.draw(canvas, scene) !== false;
      } catch (error) {
        if (window.console && window.console.error) {
          window.console.error("Falha no preview WebGL; usando Canvas 2D.", error);
        }
        drew = false;
      }
      if (!drew) {
        motionPreviewWebGLFailed = true;
        replaceMotionPreviewCanvas();
        canvas = $("motionPreviewCanvas");
      }
    }
    if (!drew && MotionPreview) {
      MotionPreview.draw(canvas, scene);
    }
  }

  function motionTick(timestamp) {
    if (!motionPreviewState.playing || activeStep !== "motions") {
      motionPreviewState.raf = null;
      return;
    }
    var last = motionPreviewState.lastFrame || timestamp;
    var delta = Math.min(100, Math.max(0, timestamp - last));
    motionPreviewState.lastFrame = timestamp;
    try {
      motionPreviewState.time += (delta / 1000) * 20 * motionPreviewState.speed;
      var motion = selectedMotion();
      var duration = motionDuration(motion);
      if (duration <= 0) {
        motionPreviewState.time = 0;
      } else if (motion && isPlainObject(motion.animation) && motion.animation.loop !== false) {
        motionPreviewState.time = ((motionPreviewState.time % duration) + duration) % duration;
      }
      drawMotionPreview();
      updateMotionPlayhead();
    } catch (error) {
      if (window.console && window.console.error) {
        window.console.error("Falha ao desenhar o preview de animação.", error);
      }
    }
    if (motionPreviewState.playing) {
      motionPreviewState.raf = window.requestAnimationFrame(motionTick);
    } else {
      motionPreviewState.raf = null;
    }
  }

  function railPreviewTick(timestamp) {
    if (activeStep === "motions") {
      railPreviewState.raf = null;
      return;
    }
    var last = railPreviewState.lastFrame || timestamp;
    var delta = Math.min(100, Math.max(0, timestamp - last));
    if (railPreviewState.lastFrame && delta < 50) {
      railPreviewState.raf = window.requestAnimationFrame(railPreviewTick);
      return;
    }
    railPreviewState.lastFrame = timestamp;
    railPreviewState.time += (delta / 1000) * 20;
    var motion = selectedMotion();
    var duration = motionDuration(motion);
    if (duration <= 0) {
      railPreviewState.time = 0;
    } else if (motion && isPlainObject(motion.animation) && motion.animation.loop !== false) {
      railPreviewState.time = ((railPreviewState.time % duration) + duration) % duration;
    } else {
      railPreviewState.time = Math.min(railPreviewState.time, duration);
    }
    drawPreview();
    railPreviewState.raf = window.requestAnimationFrame(railPreviewTick);
  }

  function startRailPreviewLoop() {
    if (railPreviewState.raf || activeStep === "motions") {
      return;
    }
    railPreviewState.lastFrame = 0;
    railPreviewState.raf = window.requestAnimationFrame(railPreviewTick);
  }

  function stopRailPreviewLoop() {
    if (railPreviewState.raf) {
      window.cancelAnimationFrame(railPreviewState.raf);
      railPreviewState.raf = null;
    }
  }

  function startMotionLoop() {
    if (motionPreviewState.raf || !motionPreviewState.playing) {
      return;
    }
    motionPreviewState.lastFrame = 0;
    motionPreviewState.raf = window.requestAnimationFrame(motionTick);
  }

  function stopMotionLoop() {
    if (motionPreviewState.raf) {
      window.cancelAnimationFrame(motionPreviewState.raf);
      motionPreviewState.raf = null;
    }
  }

  function setMotionPlaying(playing) {
    motionPreviewState.playing = Boolean(playing);
    var button = $("motionPlayBtn");
    if (button) {
      button.textContent = motionPreviewState.playing ? "pausar" : "play";
      button.setAttribute("aria-pressed", motionPreviewState.playing ? "true" : "false");
    }
    if (motionPreviewState.playing) {
      startMotionLoop();
    } else {
      stopMotionLoop();
    }
  }

  function setMotionTime(value, options) {
    var motion = selectedMotion();
    var duration = motionDuration(motion);
    motionPreviewState.time = clamp(motionNumber(value, 0), 0, duration);
    if (!options || !options.keepPlaying) {
      setMotionPlaying(false);
    }
    drawMotionPreview();
    updateMotionPlayhead();
  }

  function renderMotions() {
    motionPreviewState.revision += 1;
    var motion = selectedMotion();
    motionPreviewState.time = clamp(motionPreviewState.time, 0, motionDuration(motion));
    $("motionList").innerHTML = motionListHtml();
    $("motionDetail").innerHTML = motionDetailHtml(motion);
    $("motionTimeline").innerHTML = motionTimelineHtml(motion);
    updateMotionStageMeta();
    updateMotionSummary();
    updateMotionBadge();
    updateMotionPlayhead();
    var globalFeedback = motionGlobalFeedback();
    var globalElement = $("motionGlobalFeedback");
    if (globalElement) {
      globalElement.hidden = !globalFeedback;
      globalElement.className = "callout " + (globalFeedback && globalFeedback.severity === "error"
        ? "callout-warning" : "callout-info");
      globalElement.textContent = globalFeedback ? globalFeedback.message : "";
    }
    var playButton = $("motionPlayBtn");
    if (playButton) {
      playButton.textContent = motionPreviewState.playing ? "pausar" : "play";
      playButton.setAttribute("aria-pressed", motionPreviewState.playing ? "true" : "false");
    }
    var speed = $("motionSpeed");
    if (speed) {
      speed.value = String(motionPreviewState.speed);
    }
    syncMotionCameraSelect();
    var opacity = $("motionOpacity");
    if (opacity) {
      opacity.value = String(motionPreviewState.opacity);
    }
    var opacityLabel = $("motionOpacityLabel");
    if (opacityLabel) {
      opacityLabel.textContent = Math.round(motionPreviewState.opacity * 100) + "%";
    }
    var fullscreenButton = $("motionFullscreenBtn");
    if (fullscreenButton) {
      fullscreenButton.textContent = isMotionFullscreen() ? "fechar editor" : "editar";
    }
    drawMotionPreview();
    if (motionPreviewState.playing) {
      startMotionLoop();
    }
  }

  function selectMotion(index) {
    var motions = motionsState();
    if (index < 0 || index >= motions.length) {
      return;
    }
    motionPreviewState.selectedMotionIndex = index;
    motionPreviewState.selectedMotionId = motions[index] ? motions[index].id : "";
    motionPreviewState.selectedKeyframe = 0;
    motionPreviewState.time = 0;
    renderMotions();
    refresh({ skipMotions: true });
  }

  function addMotion() {
    var motion = V.createMotion();
    motion.id = nextMotionId();
    var available = collectMotionSymbols().filter(function (symbol) {
      return !motionKeyOwner(symbol, null) && (currentUsage[symbol] || 0) > 0;
    });
    if (available.length > 0) {
      motion.keys = [available[0]];
    }
    motion.animation.duration = 200;
    motion.animation.keyframes = [
      V.createMotionKeyframe(0),
      Object.assign(V.createMotionKeyframe(200), {
        rotation: { x: 0, y: 360, z: 0 }
      })
    ];
    motionsState().push(motion);
    motionPreviewState.selectedMotionIndex = motionsState().length - 1;
    motionPreviewState.selectedMotionId = motion.id;
    motionPreviewState.selectedKeyframe = 0;
    motionPreviewState.time = 0;
    renderMotions();
    refresh({ skipMotions: true });
    showToast("Motion " + motion.id + " criada.");
  }

  function removeMotion() {
    var motions = motionsState();
    var index = motionPreviewState.selectedMotionIndex;
    var motion = motions[index];
    if (!motion) {
      return;
    }
    if (!window.confirm("Remover a motion \"" + (trim(motion.id) || "sem id") + "\"?")) {
      return;
    }
    motions.splice(index, 1);
    motionPreviewState.selectedMotionIndex = clamp(index, 0, Math.max(0, motions.length - 1));
    motionPreviewState.selectedMotionId = motions[motionPreviewState.selectedMotionIndex]
      ? motions[motionPreviewState.selectedMotionIndex].id
      : "";
    motionPreviewState.selectedKeyframe = 0;
    motionPreviewState.time = 0;
    renderMotions();
    refresh({ skipMotions: true });
  }

  function duplicateMotion() {
    var motions = motionsState();
    var motion = motions[motionPreviewState.selectedMotionIndex];
    if (!motion) {
      return;
    }
    var copy = JSON.parse(JSON.stringify(motion));
    copy.id = nextMotionId();
    copy.keys = [];
    motions.push(copy);
    motionPreviewState.selectedMotionIndex = motions.length - 1;
    motionPreviewState.selectedMotionId = copy.id;
    motionPreviewState.selectedKeyframe = 0;
    motionPreviewState.time = 0;
    renderMotions();
    refresh({ skipMotions: true });
    showToast("Motion duplicada como " + copy.id + " (keys limpas para evitar conflito).");
  }

  function toggleMotionKey(symbol) {
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    if (!Array.isArray(motion.keys)) {
      motion.keys = [];
    }
    var index = motion.keys.indexOf(symbol);
    if (index === -1) {
      var owner = motionKeyOwner(symbol, motion);
      if (owner) {
        showToast("A key " + symbol + " já pertence à motion " + owner + ".", "error");
        return;
      }
      motion.keys.push(symbol);
    } else {
      motion.keys.splice(index, 1);
    }
    renderMotions();
    refresh({ skipMotions: true });
  }

  function toggleKeyVisibility(symbol) {
    if (motionPreviewState.hiddenKeys[symbol] === true) {
      delete motionPreviewState.hiddenKeys[symbol];
    } else {
      motionPreviewState.hiddenKeys[symbol] = true;
    }
    renderMotions();
  }

  function showAllKeys() {
    motionPreviewState.hiddenKeys = {};
    renderMotions();
  }

  function hideAllKeys() {
    collectMotionSymbols().forEach(function (symbol) {
      motionPreviewState.hiddenKeys[symbol] = true;
    });
    renderMotions();
  }

  function scaleMotionToDuration(motion, target) {
    if (!motion) {
      return;
    }
    var previous = motionDuration(motion);
    V.scaleMotionDuration(motion, target);
    motionPreviewState.time = previous > 0
      ? clamp(motionPreviewState.time * (motionDuration(motion) / previous), 0, motionDuration(motion))
      : 0;
    renderMotions();
    refresh({ skipMotions: true });
    showToast("Duração ajustada para " + motionDuration(motion) + " ticks; keyframes recalculados.");
  }

  function addMotionKeyframe() {
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    if (!isPlainObject(motion.animation)) {
      motion.animation = V.createMotion().animation;
    }
    if (!Array.isArray(motion.animation.keyframes)) {
      motion.animation.keyframes = [];
    }
    var duration = motionDuration(motion);
    var time = clamp(Math.round(motionPreviewState.time), 0, duration);
    var frame = V.createMotionKeyframe(time);
    motion.animation.keyframes.push(frame);
    motion.animation.keyframes.sort(function (left, right) {
      return motionNumber(left.time, 0) - motionNumber(right.time, 0);
    });
    motionPreviewState.selectedKeyframe = motion.animation.keyframes.indexOf(frame);
    renderMotions();
    refresh({ skipMotions: true });
  }

  function removeMotionKeyframe(index) {
    var motion = selectedMotion();
    if (!motion || !isPlainObject(motion.animation) || !Array.isArray(motion.animation.keyframes)) {
      return;
    }
    motion.animation.keyframes.splice(index, 1);
    motionPreviewState.selectedKeyframe = clamp(index, 0, Math.max(0, motion.animation.keyframes.length - 1));
    renderMotions();
    refresh({ skipMotions: true });
  }

  function centerMotionPivot() {
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    if (!isPlainObject(motion.animation)) {
      motion.animation = V.createMotion().animation;
    }
    motion.animation.pivot = motionDefaultPivot(motion);
    renderMotions();
    refresh({ skipMotions: true });
  }

  function clearMotionPivot() {
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    if (isPlainObject(motion.animation)) {
      motion.animation.pivot = null;
    }
    renderMotions();
    refresh({ skipMotions: true });
  }

  function onMotionListClick(event) {
    var card = closest(event.target, "[data-motion-index]");
    if (!card) {
      return;
    }
    var index = parseInt(card.getAttribute("data-motion-index"), 10);
    if (Number.isInteger(index)) {
      selectMotion(index);
    }
  }

  function onMotionDetailInput(event) {
    var target = event.target;
    if (!target || (target.tagName !== "INPUT" && target.tagName !== "SELECT")) {
      return;
    }
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    var field = target.getAttribute("data-motion-field");
    var keyframeField = target.getAttribute("data-keyframe-field");
    if (field === "scale_recipe") {
      return;
    }
    if (field) {
      if (field === "id") {
        motion.id = target.value;
        motionPreviewState.selectedMotionId = motion.id;
        updateMotionListOnly();
      } else if (field === "duration") {
        if (!isPlainObject(motion.animation)) {
          motion.animation = V.createMotion().animation;
        }
        motion.animation.duration = target.value;
        updateMotionTimeline();
      } else if (field === "easing") {
        if (!isPlainObject(motion.animation)) {
          motion.animation = V.createMotion().animation;
        }
        motion.animation.easing = target.value;
      } else if (field === "damage") {
        motion.damage = target.value;
      } else if (field === "damage_interval") {
        motion.damageInterval = target.value;
      } else if (field === "damage_source") {
        motion.damageSource = target.value;
      } else if (field === "pivot_x" || field === "pivot_y" || field === "pivot_z") {
        if (!isPlainObject(motion.animation)) {
          motion.animation = V.createMotion().animation;
        }
        var pivot = motionPivotForUi(motion).value;
        pivot[field.slice(-1)] = target.value;
        motion.animation.pivot = pivot;
      }
    } else if (keyframeField) {
      var frameIndex = parseInt(target.getAttribute("data-keyframe-index"), 10);
      var frames = isPlainObject(motion.animation) && Array.isArray(motion.animation.keyframes)
        ? motion.animation.keyframes
        : [];
      var frame = frames[frameIndex];
      if (!frame) {
        return;
      }
      if (keyframeField === "time") {
        frame.time = target.value;
        updateMotionTimeline();
      } else if (keyframeField === "offset" || keyframeField === "rotation") {
        var axis = target.getAttribute("data-keyframe-axis");
        if (axis) {
          if (!isPlainObject(frame[keyframeField])) {
            frame[keyframeField] = { x: 0, y: 0, z: 0 };
          }
          frame[keyframeField][axis] = target.value;
        }
      } else if (keyframeField === "easing") {
        frame.easing = target.value;
      }
    } else {
      return;
    }
    updateMotionStageMeta();
    updateMotionSummary();
    updateMotionBadge();
    drawMotionPreview();
    if (motionPreviewState.playing) {
      startMotionLoop();
    }
    refresh({ skipMotions: true });
  }

  function onMotionDetailChange(event) {
    var target = event.target;
    if (!target) {
      return;
    }
    var motion = selectedMotion();
    if (!motion) {
      return;
    }
    if (target.hasAttribute("data-motion-damage-select")) {
      var damageSource = target.value;
      var damageInput = $("motionDetail").querySelector(".motion-damage-custom");
      if (damageSource === "__custom") {
        if (damageInput) {
          damageInput.value = trim(motion.damageSource) || "minecraft:generic";
          damageInput.hidden = false;
          damageInput.focus();
          damageInput.select();
        }
      } else {
        motion.damageSource = damageSource;
        if (damageInput) {
          damageInput.value = damageSource;
          damageInput.hidden = true;
        }
      }
      updateMotionStageMeta();
      updateMotionSummary();
      updateMotionBadge();
      drawMotionPreview();
      refresh({ skipMotions: true });
      return;
    }
    var field = target.getAttribute("data-motion-field");
    if (!field) {
      return;
    }
    if (field === "loop") {
      if (!isPlainObject(motion.animation)) {
        motion.animation = V.createMotion().animation;
      }
      motion.animation.loop = target.checked;
    } else if (field === "fill_speed") {
      if (!isPlainObject(motion.animation)) {
        motion.animation = V.createMotion().animation;
      }
      motion.animation.fillSpeed = target.checked;
    } else if (field === "preserve_nbt") {
      motion.preserveNbt = target.checked;
    } else if (field === "damage_entities") {
      motion.damageEntities = target.checked;
    } else if (field === "easing") {
      if (!isPlainObject(motion.animation)) {
        motion.animation = V.createMotion().animation;
      }
      motion.animation.easing = target.value;
    } else if (field === "duration") {
      if (!isPlainObject(motion.animation)) {
        motion.animation = V.createMotion().animation;
      }
      motion.animation.duration = target.value;
      updateMotionTimeline();
    } else if (field === "scale_recipe") {
      var targetDuration = motionInt(target.value, 0);
      if (targetDuration > 0) {
        scaleMotionToDuration(motion, targetDuration);
      }
      return;
    } else {
      return;
    }
    updateMotionStageMeta();
    updateMotionSummary();
    updateMotionBadge();
    drawMotionPreview();
    if (motionPreviewState.playing) {
      startMotionLoop();
    }
    refresh({ skipMotions: true });
  }

  function onMotionDetailClick(event) {
    var visibilityButton = closest(event.target, "[data-motion-key-visibility]");
    if (visibilityButton) {
      toggleKeyVisibility(visibilityButton.getAttribute("data-motion-key-visibility"));
      return;
    }
    var keyButton = closest(event.target, "[data-motion-key]");
    if (keyButton) {
      toggleMotionKey(keyButton.getAttribute("data-motion-key"));
      return;
    }
    var addFrame = closest(event.target, "[data-add-keyframe]");
    if (addFrame) {
      addMotionKeyframe();
      return;
    }
    var removeFrame = closest(event.target, "[data-remove-keyframe]");
    if (removeFrame) {
      removeMotionKeyframe(parseInt(removeFrame.getAttribute("data-remove-keyframe"), 10));
      return;
    }
    var action = closest(event.target, "[data-motion-action]");
    if (!action) {
      return;
    }
    var name = action.getAttribute("data-motion-action");
    if (name === "center-pivot") {
      centerMotionPivot();
    } else if (name === "clear-pivot") {
      clearMotionPivot();
    } else if (name === "remove-motion") {
      removeMotion();
    } else if (name === "duplicate-motion") {
      duplicateMotion();
    } else if (name === "show-all-keys") {
      showAllKeys();
    } else if (name === "hide-all-keys") {
      hideAllKeys();
    }
  }

  function onMotionTimelineClick(event) {
    var marker = closest(event.target, "[data-keyframe-jump]");
    if (!marker) {
      return;
    }
    var motion = selectedMotion();
    if (!motion || !isPlainObject(motion.animation) || !Array.isArray(motion.animation.keyframes)) {
      return;
    }
    var index = parseInt(marker.getAttribute("data-keyframe-jump"), 10);
    var frame = motion.animation.keyframes[index];
    if (!frame) {
      return;
    }
    motionPreviewState.selectedKeyframe = index;
    setMotionTime(motionNumber(frame.time, 0));
    renderMotions();
  }

  function toggleMotionPlay() {
    setMotionPlaying(!motionPreviewState.playing);
    updateMotionPlayhead();
  }

  function resetMotionTime() {
    setMotionTime(0);
  }

  function onMotionTimeRangeInput(event) {
    setMotionTime(event.target.value);
  }

  function onMotionSpeedChange(event) {
    motionPreviewState.speed = clamp(motionNumber(event.target.value, 1), 0.1, 8);
    drawMotionPreview();
  }

  function motionSection() {
    return document.querySelector(".view[data-view='motions']");
  }

  function isMotionFullscreen() {
    var section = motionSection();
    return Boolean(section && section.classList.contains("is-motion-fullscreen"));
  }

  function closeMotionPanel() {
    var section = motionSection();
    if (section) {
      section.classList.remove("is-panel-open");
    }
    var button = $("motionPanelBtn");
    if (button) {
      button.setAttribute("aria-pressed", "false");
    }
  }

  function setMotionFullscreen(enabled) {
    var section = motionSection();
    if (!section) {
      return;
    }
    var active = Boolean(enabled);
    section.classList.toggle("is-motion-fullscreen", active);
    if (!active) {
      closeMotionPanel();
    }
    document.body.classList.toggle("motion-fullscreen-open", active);
    var fullscreenButton = $("motionFullscreenBtn");
    if (fullscreenButton) {
      fullscreenButton.textContent = active ? "fechar editor" : "editar";
      fullscreenButton.setAttribute("aria-pressed", active ? "true" : "false");
    }
    window.setTimeout(drawMotionPreview, 50);
  }

  function toggleMotionFullscreen() {
    setMotionFullscreen(!isMotionFullscreen());
  }

  function toggleMotionPanel() {
    var section = motionSection();
    if (!section || !section.classList.contains("is-motion-fullscreen")) {
      return;
    }
    var open = section.classList.toggle("is-panel-open");
    var button = $("motionPanelBtn");
    if (button) {
      button.setAttribute("aria-pressed", open ? "true" : "false");
    }
  }

  function onMotionOpacityInput(event) {
    motionPreviewState.opacity = clamp(motionNumber(event.target.value, 0.65), 0.15, 1);
    var label = $("motionOpacityLabel");
    if (label) {
      label.textContent = Math.round(motionPreviewState.opacity * 100) + "%";
    }
    drawMotionPreview();
  }

  function syncMotionCameraSelect() {
    var select = $("motionCamera");
    if (select) {
      select.value = motionPreviewState.camera;
    }
  }

  function applyMotionCamera(name) {
    if (name === "free") {
      motionPreviewState.camera = "free";
      syncMotionCameraSelect();
      return;
    }
    var preset = MOTION_CAMERA_VIEWS[name];
    if (!preset) {
      return;
    }
    motionPreviewState.camera = name;
    motionPreviewState.view = {
      yaw: preset.yaw,
      pitch: preset.pitch,
      zoom: preset.zoom
    };
    syncMotionCameraSelect();
    drawMotionPreview();
  }

  function onMotionCameraChange(event) {
    applyMotionCamera(event.target.value);
  }

  function resetMotionPreviewView() {
    applyMotionCamera("isometric");
  }

  function beginMotionPreviewDrag(event) {
    var surface = $("motionCanvasWrap");
    if (!surface) {
      return;
    }
    if (surface.setPointerCapture) {
      surface.setPointerCapture(event.pointerId);
    }
    surface.classList.add("is-dragging");
    motionPreviewState.dragging = true;
    motionPreviewState.lastX = event.clientX;
    motionPreviewState.lastY = event.clientY;
  }

  function moveMotionPreviewDrag(event) {
    if (!motionPreviewState.dragging) {
      return;
    }
    var dx = event.clientX - motionPreviewState.lastX;
    var dy = event.clientY - motionPreviewState.lastY;
    motionPreviewState.lastX = event.clientX;
    motionPreviewState.lastY = event.clientY;
    motionPreviewState.view.yaw += dx * 0.01;
    motionPreviewState.view.pitch = clamp(motionPreviewState.view.pitch + dy * 0.01, -1.45, 1.45);
    motionPreviewState.camera = "free";
    syncMotionCameraSelect();
    drawMotionPreview();
  }

  function endMotionPreviewDrag(event) {
    var surface = $("motionCanvasWrap");
    if (!surface) {
      return;
    }
    if (surface.hasPointerCapture && surface.hasPointerCapture(event.pointerId)) {
      surface.releasePointerCapture(event.pointerId);
    }
    surface.classList.remove("is-dragging");
    motionPreviewState.dragging = false;
  }

  function onMotionPreviewWheel(event) {
    event.preventDefault();
    motionPreviewState.view.zoom = clamp(
      motionPreviewState.view.zoom * Math.exp(-event.deltaY * 0.0015), 0.35, 4);
    drawMotionPreview();
  }

  function updateMatcherCard(symbol) {
    var card = findMatcherCard(symbol);
    var matcher = state.matchers[symbol];
    if (!card || !matcher) {
      return;
    }
    var feedback = matcherFeedback(symbol, matcher);
    var feedbackElement = card.querySelector(".matcher-feedback");
    if (feedbackElement) {
      feedbackElement.className = "matcher-feedback" + (feedback ? " " + feedback.tone : "");
      feedbackElement.textContent = feedback ? feedback.text : "";
      feedbackElement.hidden = !feedback;
    }
    var jsonElement = card.querySelector(".matcher-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildMatcherValue(matcher));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    card.classList.toggle("is-problem", Boolean(V.matcherProblem(matcher)) || !V.isSingleSymbol(symbol));
  }

  function findMatcherCard(symbol) {
    var list = $("matcherList");
    if (!list) {
      return null;
    }
    var cards = $$(".matcher-card", list);
    for (var index = 0; index < cards.length; index += 1) {
      if (cards[index].getAttribute("data-symbol") === symbol) {
        return cards[index];
      }
    }
    return null;
  }

  function findSlotCard(index) {
    var list = $("slotList");
    if (!list) {
      return null;
    }
    return list.querySelector(".slot-card[data-slot-index='" + index + "']");
  }

  function defaultSlotFaces(role) {
    var insert = role === "output" || role === "fluid"
      ? []
      : SLOT_FACE_DIRECTIONS.slice();
    var extract = role === "output" || role === "storage" ? SLOT_FACE_DIRECTIONS.slice() : [];
    return { insert: insert, extract: extract };
  }

  function effectiveSlotFaces(slot) {
    var defaults = defaultSlotFaces(trim(slot.role));
    var result = {
      insert: defaults.insert.slice(),
      extract: defaults.extract.slice()
    };
    if (isPlainObject(slot.faces)) {
      if (Array.isArray(slot.faces.insert)) {
        result.insert = slot.faces.insert.filter(function (direction) {
          return SLOT_FACE_DIRECTIONS.indexOf(direction) !== -1;
        });
      }
      if (Array.isArray(slot.faces.extract)) {
        result.extract = slot.faces.extract.filter(function (direction) {
          return SLOT_FACE_DIRECTIONS.indexOf(direction) !== -1;
        });
      }
    }
    return result;
  }

  function slotFacesAreCustom(slot) {
    return isPlainObject(slot.faces) &&
      (Array.isArray(slot.faces.insert) || Array.isArray(slot.faces.extract));
  }

  function slotFacesSummary(slot) {
    if (!slotFacesAreCustom(slot)) {
      return "faces padrão do role";
    }
    var effective = effectiveSlotFaces(slot);
    function describe(faces) {
      if (faces.length === SLOT_FACE_DIRECTIONS.length) {
        return "todas";
      }
      if (faces.length === 0) {
        return "nenhuma";
      }
      return faces.join(", ");
    }
    return "insert " + describe(effective.insert) + " · extract " + describe(effective.extract);
  }

  function slotFaceRowHtml(slot, index, scope) {
    var effective = effectiveSlotFaces(slot);
    return "<div class='face-row'><span class='face-scope'>" + esc(scope) + "</span>" +
      "<div class='face-toggles'>" + SLOT_FACE_DIRECTIONS.map(function (direction) {
        var checked = effective[scope].indexOf(direction) !== -1;
        return "<label class='face-toggle' title='" + esc(scope + " em " + direction) + "'>" +
          "<input type='checkbox' data-slot-index='" + index + "' data-face-scope='" + scope +
          "' data-face-direction='" + direction + "'" + (checked ? " checked" : "") + ">" +
          "<span>" + esc(SLOT_FACE_LABELS[direction]) + "</span></label>";
      }).join("") + "</div></div>";
  }

  function slotFacesHtml(slot, index) {
    var custom = slotFacesAreCustom(slot);
    return "<div class='slot-faces'><div class='slot-faces-head'><div><strong>Faces de automação</strong>" +
      "<small>" + esc(slotFacesSummary(slot)) + "</small></div>" +
      "<button type='button' class='btn btn-tiny' data-action='reset-slot-faces'" + (custom ? "" : " disabled") +
      ">usar padrão do role</button></div>" +
      slotFaceRowHtml(slot, index, "insert") +
      slotFaceRowHtml(slot, index, "extract") +
      "<p class='grid-help'>N/S/L/O = norte, sul, leste, oeste; ↑/↓ = cima, baixo. O jogador continua podendo retirar de slots visíveis.</p></div>";
  }

  function slotSummary(slot) {
    if (!isPlainObject(slot)) {
      return "slot inválido";
    }
    var role = trim(slot.role) || "sem role";
    var filter = trim(slot.filter) || "qualquer item";
    var limit = slot.limit === null || slot.limit === undefined ? 64 : slot.limit;
    var position = slot.x === null || slot.x === undefined || slot.y === null || slot.y === undefined
      ? "sem posição na GUI"
      : "x" + slot.x + " y" + slot.y;
    var faces = slotFacesAreCustom(slot) ? "faces customizadas" : "faces padrão";
    var parts = [role];
    if (role !== "output") {
      parts.push(filter);
    }
    parts.push("limit " + limit, position, faces);
    return parts.join(" · ");
  }

  function slotRoleOptions(slot) {
    var role = trim(slot.role);
    var html = "";
    if (!role) {
      html += "<option value='' selected>Selecione</option>";
    } else if (V.ITEM_SLOT_ROLES.indexOf(role) === -1) {
      html += "<option value='" + esc(role) + "' selected>Inválido: " + esc(role) + "</option>";
    }
    V.ITEM_SLOT_ROLES.forEach(function (value) {
      html += "<option value='" + esc(value) + "'" + (value === role ? " selected" : "") +
        " title='" + esc(SLOT_ROLE_INFO[value] || "") + "'>" + esc(value) + "</option>";
    });
    return html;
  }

  function slotRoleGuideHtml(index) {
    var items = V.ITEM_SLOT_ROLES.map(function (role) {
      var color = SLOT_ROLE_COLORS[role] || "#94a3b8";
      return "<li><span class='role-guide-badge' style='--role-color:" + color + "'>" +
        esc(role) + "</span><span>" + esc(SLOT_ROLE_INFO[role] || "") + "</span></li>";
    }).join("");
    return "<div class='role-guide' id='slotRoleHelp" + (index + 1) + "' role='tooltip'>" +
      "<strong>O que cada role faz</strong><ul>" + items + "</ul></div>";
  }

  function moveButtonHtml(action, label, dx, dy, disabled, title) {
    return "<button type='button' class='move-btn' data-action='" + esc(action) + "' data-dx='" + dx +
      "' data-dy='" + dy + "' title='" + esc(title) + "' aria-label='" + esc(title) + "'" +
      (disabled ? " disabled" : "") + ">" + label + "</button>";
  }

  function slotCardHtml(slot, index) {
    if (!isPlainObject(slot)) {
      return "<div class='slot-card is-problem' data-slot-index='" + index + "'>" +
        "<div class='matcher-head'><span class='slot-index'>" + (index + 1) + "</span>" +
        "<div class='matcher-title'><strong>slot inválido</strong><small>" +
        "Remova este slot ou importe o JSON novamente.</small></div>" +
        "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-slot'>remover</button></div>" +
        "<p class='matcher-feedback is-error'>O slot precisa ser um objeto.</p></div>";
    }
    var problem = V.slotProblem(slot);
    var serialized = JSON.stringify(V.buildItemSlot(slot));
    var slotName = trim(slot.id) || "slot " + (index + 1);
    var limit = slot.limit === null || slot.limit === undefined ? "" : String(slot.limit);
    var x = slot.x === null || slot.x === undefined ? "" : String(slot.x);
    var y = slot.y === null || slot.y === undefined ? "" : String(slot.y);
    var hasPosition = x !== "" && y !== "";
    var filterField = trim(slot.role) === "output" ? "" :
      "<div class='field'><label>filter</label><input type='text' data-slot-index='" + index +
      "' data-field='filter' value='" + esc(slot.filter || "") +
      "' placeholder='minecraft:iron_ore ou #tag' spellcheck='false' autocomplete='off'></div>";
    var limitField = "<div class='field'><label>limit</label><input type='number' data-slot-index='" + index +
      "' data-field='limit' min='1' max='64' step='1' value='" + esc(limit) + "' placeholder='64'></div>";
    var moveControls = "<div class='slot-move-controls' role='group' aria-label='Mover " + esc(slotName) + "'>" +
      moveButtonHtml("move-slot", "↑", 0, -18, !hasPosition, "Mover slot para cima") +
      moveButtonHtml("move-slot", "←", -18, 0, !hasPosition, "Mover slot para a esquerda") +
      moveButtonHtml("move-slot", "→", 18, 0, !hasPosition, "Mover slot para a direita") +
      moveButtonHtml("move-slot", "↓", 0, 18, !hasPosition, "Mover slot para baixo") +
      "</div>";
    return "<div class='slot-card" + (problem ? " is-problem" : "") + "' data-slot-index='" + index + "'>" +
      "<div class='matcher-head slot-head'><span class='slot-index'>" + (index + 1) + "</span>" +
      "<div class='matcher-title'><strong>" + esc(slotName) + "</strong><small>" +
      esc(slotSummary(slot)) + "</small></div>" + moveControls +
      "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-slot'>remover</button></div>" +
      "<div class='slot-form slot-form-primary'>" +
      "<div class='field'><label>id</label><input type='text' data-slot-index='" + index +
      "' data-field='id' value='" + esc(slot.id || "") + "' placeholder='input_0' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field role-field'><label>role <span class='role-help-mark' title='Passe o mouse para comparar os roles' aria-hidden='true'>?</span></label>" +
      "<select data-slot-index='" + index + "' data-field='role' aria-label='Role do slot " + (index + 1) +
      "' aria-describedby='slotRoleHelp" + (index + 1) + "'>" + slotRoleOptions(slot) + "</select>" +
      slotRoleGuideHtml(index) + "</div>" +
      "<div class='field'><label>x</label><input type='number' data-slot-index='" + index +
      "' data-field='x' step='1' value='" + esc(x) + "' placeholder='opcional'></div>" +
      "<div class='field'><label>y</label><input type='number' data-slot-index='" + index +
      "' data-field='y' step='1' value='" + esc(y) + "' placeholder='opcional'></div>" +
      "</div><p class='matcher-feedback slot-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") +
      "</p><details class='card-advanced'><summary>Filtro, limite, faces e JSON" +
      "<span class='card-advanced-hint'>automação</span></summary><div class='card-advanced-body'>" +
      "<div class='slot-form'>" + filterField + limitField + "</div>" + slotFacesHtml(slot, index) +
      "<code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code></div></details></div>";
  }

  function layoutSlotGuiPreview(content, dimensions) {
    var scale = Math.min(1, 176 / dimensions.width, 166 / dimensions.height);
    var left = Math.max(0, (176 - dimensions.width * scale) / 2);
    var top = Math.max(0, (166 - dimensions.height * scale) / 2);
    content.style.width = dimensions.width + "px";
    content.style.height = dimensions.height + "px";
    content.style.left = left + "px";
    content.style.top = top + "px";
    content.style.transform = "scale(" + scale + ")";
  }

  function slotGuiPreviewMarkup(slots, width, height, hidden) {
    var html = [];
    var drawn = 0;
    slots.forEach(function (slot, index) {
      if (!isPlainObject(slot)) {
        hidden.push("slot " + (index + 1));
        return;
      }
      var hasX = slot.x !== null && slot.x !== undefined;
      var hasY = slot.y !== null && slot.y !== undefined;
      var x = hasX ? parseIntStrict(slot.x) : null;
      var y = hasY ? parseIntStrict(slot.y) : null;
      var slotName = trim(slot.id) || "slot " + (index + 1);
      if (!hasX || !hasY || x === null || y === null) {
        hidden.push(slotName);
        return;
      }
      var role = trim(slot.role);
      var color = SLOT_ROLE_COLORS[role] || "#94a3b8";
      var frameX = x - 1;
      var frameY = y;
      var outside = frameX < 0 || frameY < 0 || frameX + 18 > width || frameY + 18 > height;
      html.push("<div class='gui-slot-preview" + (outside ? " is-outside" : "") +
        "' data-role='" + esc(role) + "' style='left:" + frameX + "px;top:" + frameY + "px;--slot-color:" +
        color + "' title='" + esc(slotName + " · " + (role || "sem role") + " · x" + x + " y" + y) +
        "'><span>" + (index + 1) + "</span></div>");
      drawn++;
    });
    return { html: html.join(""), drawn: drawn };
  }

  function renderSlotGuiPreview(slots) {
    var frame = $("slotGuiPreview");
    var content = $("slotGuiPreviewContent");
    if (!frame || !content) {
      return;
    }
    var source = Array.isArray(slots) ? slots : [];
    var hidden = [];
    var items = [];
    var customElements = isPlainObject(state.gui) && Array.isArray(state.gui.elements)
      ? state.gui.elements.filter(isPlainObject)
      : [];
    var customGui = isPlainObject(state.gui);
    var dimensions = customGui ? guiCanvasSize(state.gui) : {
      width: V.GUI_DEFAULT_WIDTH,
      height: V.GUI_DEFAULT_HEIGHT
    };
    var hasSlotElements = customGui && customElements.some(function (element) {
      return trim(element.type) === "slot";
    });
    layoutSlotGuiPreview(content, dimensions);
    applyGuiPreviewBackground(content, customGui ? state.gui : null);
    frame.classList.add("has-gui-texture");

    if (customGui) {
      var hasInventoryLabelText = customElements.some(isInventoryLabelText);
      items.push(customElements.map(function (element, index) {
        return guiCanvasElementMarkup(element, index, hasInventoryLabelText);
      }).join(""));
      if (!hasSlotElements) {
        var customSlots = slotGuiPreviewMarkup(source, dimensions.width, dimensions.height, hidden);
        items.push(customSlots.html);
      }
    } else {
      var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
      var energy = capabilities && isPlainObject(capabilities.energy) ? capabilities.energy : null;
      var fluids = capabilities && Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
      var barCount = (energy ? 1 : 0) + fluids.length;
      var barRows = Math.max(1, Math.ceil(barCount / 3));
      var barHeight = Math.max(8, Math.floor((64 - (barRows - 1) * 4) / barRows));

      items.push("<span class='gui-preview-title'>" + esc(trim(state.path) || "Título") + "</span>");
      items.push("<span class='gui-preview-caption'>Inventário</span>");

      var barIndex = 0;
      if (energy) {
        items.push("<i class='gui-preview-bar is-energy' style='left:" + (165 - (barIndex % 3) * 5) +
          "px;top:" + (5 + Math.floor(barIndex / 3) * (barHeight + 4)) + "px;height:" + barHeight +
          "px' title='Energia'></i>");
        barIndex++;
      }
      fluids.forEach(function (tank, index) {
        if (!isPlainObject(tank)) {
          return;
        }
        var x = 165 - (barIndex % 3) * 5;
        var y = 5 + Math.floor(barIndex / 3) * (barHeight + 4);
        items.push("<i class='gui-preview-bar is-fluid' style='left:" + x + "px;top:" + y +
          "px;height:" + barHeight + "px' title='" +
          esc(trim(tank.id) || ("Fluido " + (index + 1))) + "'></i>");
        barIndex++;
      });
      if (state.type === "recipe") {
        items.push("<i class='gui-preview-progress' style='left:171px;top:17px' title='Progresso'></i>");
      }

      var fallbackSlots = slotGuiPreviewMarkup(source, dimensions.width, dimensions.height, hidden);
      items.push(fallbackSlots.html);
      if (fallbackSlots.drawn === 0) {
        items.push("<span class='gui-preview-note'>" +
          (source.length > 0 ? "Nenhum slot com x/y" : "Sem item_slots") + "</span>");
      }
    }
    content.innerHTML = items.join("");

    var legend = $("slotPreviewLegend");
    if (legend) {
      legend.innerHTML = V.ITEM_SLOT_ROLES.map(function (role) {
        return "<span class='gui-legend-item' data-role='" + esc(role) + "'><i style='--slot-color:" +
          (SLOT_ROLE_COLORS[role] || "#94a3b8") + "'></i>" + esc(role) + "</span>";
      }).join("");
    }
    var hiddenElement = $("slotPreviewHidden");
    if (hiddenElement) {
      if (customGui) {
        if (hasSlotElements) {
          hiddenElement.textContent = "Slots posicionados por elementos slot (formato antigo).";
        } else if (!Array.isArray(slots)) {
          hiddenElement.textContent = "Sem item_slots; a GUI customizada não mostra slots de item.";
        } else {
          hiddenElement.textContent = hidden.length > 0
            ? "Sem x/y; não aparecem na GUI customizada: " + hidden.join(", ")
            : "";
        }
      } else {
        hiddenElement.textContent = Array.isArray(slots)
          ? (hidden.length > 0 ? "Sem x/y (grade automática no fallback): " + hidden.join(", ") : "")
          : "Sem item_slots; a prévia mostra só o layout fallback.";
      }
    }
  }

  var SLOT_PREVIEW_ZOOM = 1.9;

  function slotPreviewZoomNode() {
    return $("slotGuiPreviewZoom");
  }

  function onSlotPreviewMouseMove(event) {
    var frame = $("slotGuiPreview");
    var zoom = slotPreviewZoomNode();
    if (!frame || !zoom) {
      return;
    }
    var rect = frame.getBoundingClientRect();
    var width = frame.clientWidth || 176;
    var height = frame.clientHeight || 166;
    var x = event.clientX - rect.left - frame.clientLeft;
    var y = event.clientY - rect.top - frame.clientTop;
    var originX = Math.max(0, Math.min(100, (x / width) * 100));
    var originY = Math.max(0, Math.min(100, (y / height) * 100));
    zoom.style.transformOrigin = originX.toFixed(2) + "% " + originY.toFixed(2) + "%";
    zoom.style.transform = "scale(" + SLOT_PREVIEW_ZOOM + ")";
  }

  function resetSlotPreviewZoom() {
    var zoom = slotPreviewZoomNode();
    if (!zoom) {
      return;
    }
    zoom.style.transform = "scale(1)";
    zoom.style.transformOrigin = "50% 50%";
  }

  function renderSlots() {
    var slots = Array.isArray(state.itemSlots) ? state.itemSlots : null;
    var summary = $("slotsSummary");
    var empty = $("slotsEmpty");
    if (slots === null) {
      summary.textContent = "Sem item_slots; o inventário usa inventory_rows.";
      empty.hidden = false;
      empty.textContent = "Nenhum item_slots definido. Adicione um slot para desativar o inventário automático.";
      $("slotList").innerHTML = "";
      $("useInventoryRowsBtn").disabled = true;
      return;
    }
    summary.textContent = slots.length === 0
      ? "item_slots=[] definido; a máquina não terá slots."
      : slots.length + (slots.length === 1 ? " slot definido." : " slots definidos.");
    empty.hidden = slots.length > 0;
    empty.textContent = slots.length === 0 ? "Lista vazia. Adicione um slot ou volte para inventory_rows." : "";
    $("slotList").innerHTML = slots.map(slotCardHtml).join("");
    $("useInventoryRowsBtn").disabled = false;
  }

  function updateSlotCard(index) {
    var card = findSlotCard(index);
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!card || !slot) {
      return;
    }
    var problem = V.slotProblem(slot);
    var feedback = card.querySelector(".slot-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback slot-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildItemSlot(slot));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = slotSummary(slot);
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function nextSlotId() {
    var used = {};
    (Array.isArray(state.itemSlots) ? state.itemSlots : []).forEach(function (slot) {
      used[trim(slot.id)] = true;
    });
    var index = 0;
    while (used["slot_" + index]) {
      index += 1;
    }
    return "slot_" + index;
  }

  function slotLayoutColumns() {
    return 9;
  }

  function slotPositionOverlaps(x, y, used) {
    return used.some(function (position) {
      return x < position.x + 18 && x + 18 > position.x &&
        y < position.y + 18 && y + 18 > position.y;
    });
  }

  function autoSlotPosition() {
    var used = [];
    (Array.isArray(state.itemSlots) ? state.itemSlots : []).forEach(function (slot) {
      if (!isPlainObject(slot)) {
        return;
      }
      var x = parseIntStrict(slot.x);
      var y = parseIntStrict(slot.y);
      if (x !== null && y !== null) {
        used.push({ x: x, y: y });
      }
    });
    var columns = slotLayoutColumns();
    for (var row = 0; ; row++) {
      for (var column = 0; column < columns; column++) {
        var x = 8 + column * 18;
        var y = 16 + row * 18;
        if (!slotPositionOverlaps(x, y, used)) {
          return { x: x, y: y };
        }
      }
    }
  }

  function addSlot() {
    if (!Array.isArray(state.itemSlots)) {
      state.itemSlots = [];
    }
    var slot = V.createItemSlot();
    var position = autoSlotPosition();
    slot.id = nextSlotId();
    slot.x = position.x;
    slot.y = position.y;
    state.itemSlots.push(slot);
    refresh();
    var card = findSlotCard(state.itemSlots.length - 1);
    var input = card ? card.querySelector("[data-field='id']") : null;
    if (input) {
      focusElement(input, true);
    }
  }

  function setSlotFace(index, scope, direction, enabled) {
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!slot || (scope !== "insert" && scope !== "extract") ||
      SLOT_FACE_DIRECTIONS.indexOf(direction) === -1) {
      return;
    }
    var effective = effectiveSlotFaces(slot);
    effective[scope] = SLOT_FACE_DIRECTIONS.filter(function (entry) {
      if (entry === direction) {
        return enabled;
      }
      return effective[scope].indexOf(entry) !== -1;
    });
    slot.faces = {
      insert: effective.insert,
      extract: effective.extract
    };
    updateSlotCard(index);
    var card = findSlotCard(index);
    if (card) {
      var summary = card.querySelector(".slot-faces-head small");
      if (summary) {
        summary.textContent = slotFacesSummary(slot);
      }
      var reset = card.querySelector("[data-action='reset-slot-faces']");
      if (reset) {
        reset.disabled = false;
      }
    }
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true });
  }

  function resetSlotFaces(index) {
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!slot) {
      return;
    }
    slot.faces = null;
    renderSlots();
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true });
  }

  function moveSlot(index, dx, dy) {
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!isPlainObject(slot)) {
      return;
    }
    var x = parseIntStrict(slot.x);
    var y = parseIntStrict(slot.y);
    if (x === null || y === null) {
      return;
    }
    slot.x = x + dx;
    slot.y = y + dy;
    var card = findSlotCard(index);
    if (card) {
      var xInput = card.querySelector("[data-field='x']");
      var yInput = card.querySelector("[data-field='y']");
      if (xInput) {
        xInput.value = String(slot.x);
      }
      if (yInput) {
        yInput.value = String(slot.y);
      }
    }
    updateSlotCard(index);
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true });
  }

  function useInventoryRows() {
    if (!Array.isArray(state.itemSlots)) {
      return;
    }
    if (state.itemSlots.length > 0 && !window.confirm("Remover os item_slots e voltar para inventory_rows?")) {
      return;
    }
    state.itemSlots = null;
    refresh();
    showToast("Inventário automático por inventory_rows restaurado.");
  }

  function onSlotListInput(event) {
    var target = event.target;
    if (!target || target.tagName !== "INPUT") {
      return;
    }
    var index = parseInt(target.getAttribute("data-slot-index"), 10);
    var scope = target.getAttribute("data-face-scope");
    var direction = target.getAttribute("data-face-direction");
    var field = target.getAttribute("data-field");
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!slot) {
      return;
    }
    if (scope) {
      setSlotFace(index, scope, direction, target.checked);
      return;
    }
    if (["id", "filter", "limit", "x", "y"].indexOf(field) === -1) {
      return;
    }
    if (field === "filter" && trim(slot.role) === "output") {
      return;
    }
    if (field === "id" || field === "filter") {
      slot[field] = target.value;
    } else if (field === "limit") {
      slot.limit = trim(target.value) === "" ? 64 : target.value;
    } else {
      slot[field] = trim(target.value) === "" ? null : target.value;
    }
    updateSlotCard(index);
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true });
  }

  function onSlotListChange(event) {
    var target = event.target;
    if (!target || target.tagName !== "SELECT" || target.getAttribute("data-field") !== "role") {
      return;
    }
    var index = parseInt(target.getAttribute("data-slot-index"), 10);
    var slot = Array.isArray(state.itemSlots) ? state.itemSlots[index] : null;
    if (!slot) {
      return;
    }
    slot.role = target.value;
    if (trim(slot.role) === "output") {
      slot.filter = "";
    }
    renderSlots();
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true });
  }

  function onSlotListClick(event) {
    var move = closest(event.target, "[data-action='move-slot']");
    if (move) {
      var moveCard = closest(move, ".slot-card");
      var moveIndex = moveCard ? parseInt(moveCard.getAttribute("data-slot-index"), 10) : -1;
      if (moveIndex >= 0) {
        moveSlot(moveIndex, parseInt(move.getAttribute("data-dx"), 10) || 0,
          parseInt(move.getAttribute("data-dy"), 10) || 0);
      }
      return;
    }
    var reset = closest(event.target, "[data-action='reset-slot-faces']");
    if (reset) {
      var resetCard = closest(reset, ".slot-card");
      var resetIndex = resetCard ? parseInt(resetCard.getAttribute("data-slot-index"), 10) : -1;
      if (resetIndex >= 0) {
        resetSlotFaces(resetIndex);
      }
      return;
    }
    var action = closest(event.target, "[data-action='remove-slot']");
    if (!action) {
      return;
    }
    var card = closest(action, ".slot-card");
    var index = card ? parseInt(card.getAttribute("data-slot-index"), 10) : -1;
    if (!Array.isArray(state.itemSlots) || index < 0 || index >= state.itemSlots.length) {
      return;
    }
    var slotName = trim(state.itemSlots[index].id) || "slot " + (index + 1);
    if (!window.confirm("Remover o " + slotName + "?")) {
      return;
    }
    state.itemSlots.splice(index, 1);
    refresh();
  }

  function ensureCapabilities() {
    if (!isPlainObject(state.capabilities)) {
      state.capabilities = V.createCapabilities();
    }
    return state.capabilities;
  }

  function capabilityFieldValue(value) {
    return value === null || value === undefined ? "" : String(value);
  }

  function capabilitySummaryText() {
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    if (!capabilities) {
      return "Sem capabilities; a máquina não expõe energia/fluidos/chemicals/mana/essentia.";
    }
    var energy = isPlainObject(capabilities.energy) ? capabilities.energy : null;
    var fluids = Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    var chemicalTanks = isPlainObject(capabilities.chemical)
      && Array.isArray(capabilities.chemical.tanks)
      ? capabilities.chemical.tanks
      : [];
    var mana = isPlainObject(capabilities.mana) ? capabilities.mana : null;
    var essentia = isPlainObject(capabilities.essentia) ? capabilities.essentia : null;
    var energyText = energy
      ? "Energia " + (trim(energy.id) || "sem id") + " · " + capabilityFieldValue(energy.capacity) + " FE"
      : "Sem energia";
    var manaText = "Sem mana";
    if (mana) {
      manaText = "Mana " + capabilityFieldValue(mana.capacity) +
        " (R " + capabilityFieldValue(mana.maxReceive) +
        " / E " + capabilityFieldValue(mana.maxExtract) + ")" +
        (trim(mana.port) ? " · porta " + trim(mana.port) : "");
    }
    return energyText + " · " + (fluids.length === 1 ? "1 tanque" : fluids.length + " tanques") +
      " · " + (chemicalTanks.length === 1 ? "1 tanque chemical" : chemicalTanks.length + " tanques chemical") +
      " · " + manaText +
      " · " + (essentia ? "Essentia raio " + capabilityFieldValue(essentia.range) : "Sem essentia");
  }

  function updateCapabilitySummary() {
    var summary = $("capabilitiesSummary");
    if (summary) {
      summary.textContent = capabilitySummaryText();
    }
  }

  function ensureCapabilityPorts() {
    if (!isPlainObject(state.capabilityPorts)) {
      state.capabilityPorts = V.createCapabilityPorts();
    }
    return state.capabilityPorts;
  }

  function capabilityPortsSummaryText() {
    var ports = isPlainObject(state.capabilityPorts) ? state.capabilityPorts : null;
    if (!ports) {
      return "Sem filtro; todas as capabilities usam qualquer bloco formado.";
    }
    var parts = [];
    var availability = V.capabilityPortAvailability(state.capabilities);
    ["item", "energy", "fluid", "chemical", "mana", "essentia"].forEach(function (key) {
      if (!availability[key] || !Array.isArray(ports[key])) {
        return;
      }
      parts.push(key + ": " + (ports[key].length === 0 ? "nenhuma porta" : ports[key].join(", ")));
    });
    return parts.length > 0
      ? parts.join(" · ")
      : "Sem filtro; todas as capabilities usam qualquer bloco formado.";
  }

  function updateCapabilityPortsSummary() {
    var summary = $("capabilityPortsSummary");
    if (summary) {
      summary.textContent = capabilityPortsSummaryText();
    }
  }

  function updateCapabilityPortAvailability(availability) {
    var unavailable = [];
    if (!availability.energy) {
      unavailable.push("energy (adicione energia)");
    }
    if (!availability.fluid) {
      unavailable.push("fluid (adicione um tanque)");
    }
    if (!availability.chemical) {
      unavailable.push("chemical (adicione um tanque chemical)");
    }
    if (!availability.mana) {
      unavailable.push("mana (adicione um tanque de mana)");
    }
    if (!availability.essentia) {
      unavailable.push("essentia (adicione a capability essentia)");
    }
    var hint = $("capabilityPortAvailability");
    if (hint) {
      hint.textContent = unavailable.length > 0
        ? "Portas desabilitadas porque a capability não existe: " + unavailable.join(" · ") + "."
        : "";
      hint.hidden = unavailable.length === 0;
    }
  }

  function renderCapabilityPorts() {
    var ports = isPlainObject(state.capabilityPorts) ? state.capabilityPorts : null;
    var availability = V.capabilityPortAvailability(state.capabilities);
    var available = V.recipePatternSymbols(state);
    ["item", "energy", "fluid", "chemical", "mana", "essentia"].forEach(function (key) {
      var input = document.querySelector("[data-capability-port='" + key + "']");
      if (!input) {
        return;
      }
      input.disabled = !availability[key];
      input.title = availability[key] ? "" : "Adicione a capability correspondente para configurar as portas.";
      if (input !== document.activeElement) {
        input.value = ports ? V.capabilityPortText(ports[key]) : "";
      }
      var picks = document.querySelector("[data-port-picks='" + key + "']");
      if (!picks) {
        return;
      }
      if (!availability[key] || available.length === 0) {
        picks.innerHTML = "";
        return;
      }
      var selected = ports && Array.isArray(ports[key]) ? ports[key] : [];
      picks.innerHTML = available.map(function (symbol) {
        var isSelected = selected.indexOf(symbol) !== -1;
        return "<button type='button' class='port-pick" + (isSelected ? " is-active" : "") +
          "' data-port-pick='" + esc(key) + "' data-port-symbol='" + esc(symbol) +
          "' aria-pressed='" + (isSelected ? "true" : "false") + "' title='" +
          esc(symbolHover(symbol)) + (isSelected ? " · remover da porta" : " · adicionar à porta") +
          "'><span class='swatch' style='--c:" + getColor(symbol) + "'>" + esc(symbol) + "</span></button>";
      }).join("");
    });
    updateCapabilityPortAvailability(availability);
    updateCapabilityPortsSummary();
    var symbols = $("capabilityPortSymbols");
    if (symbols) {
      if (available.length > 0) {
        symbols.innerHTML = "Símbolos disponíveis no pattern: " + available.map(function (symbol) {
          return "<span class='symbol-hover' title='" + esc(symbolHover(symbol)) + "'>" + esc(symbol) + "</span>";
        }).join(", ") + ".";
      } else {
        symbols.textContent = "Sem símbolos no pattern; use IDs de bloco ou tags.";
      }
    }
  }

  function onCapabilityPortsClick(event) {
    var button = closest(event.target, "[data-port-pick]");
    if (!button || button.disabled) {
      return;
    }
    var key = button.getAttribute("data-port-pick");
    var symbol = button.getAttribute("data-port-symbol");
    if (!key || !symbol) {
      return;
    }
    var ports = ensureCapabilityPorts();
    var list = Array.isArray(ports[key]) ? ports[key].slice() : [];
    var index = list.indexOf(symbol);
    if (index === -1) {
      list.push(symbol);
    } else {
      list.splice(index, 1);
    }
    ports[key] = list;
    renderCapabilityPorts();
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
    var nextButton = null;
    var buttons = document.querySelectorAll("[data-port-pick='" + key + "']");
    for (var buttonIndex = 0; buttonIndex < buttons.length; buttonIndex += 1) {
      if (buttons[buttonIndex].getAttribute("data-port-symbol") === symbol) {
        nextButton = buttons[buttonIndex];
        break;
      }
    }
    if (nextButton) {
      nextButton.focus();
    }
  }

  function onCapabilityPortsInput(event) {
    var target = event.target;
    if (target.disabled) {
      return;
    }
    var key = target.getAttribute("data-capability-port");
    if (!key) {
      return;
    }
    var ports = ensureCapabilityPorts();
    ports[key] = V.parseCapabilityPortText(target.value);
    renderCapabilityPorts();
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
  }

  function energyCardHtml(energy) {
    var problem = V.energyProblem(energy);
    var serialized = JSON.stringify(V.buildEnergy(energy));
    var summary = (trim(energy.id) || "sem id") + " · " + capabilityFieldValue(energy.capacity) +
      " FE · recebe " + capabilityFieldValue(energy.maxReceive) + " · extrai " + capabilityFieldValue(energy.maxExtract);
    return "<div class='slot-card capability-card" + (problem ? " is-problem" : "") +
      "' data-capability='energy'><div class='matcher-head'><span class='slot-index'>FE</span>" +
      "<div class='matcher-title'><strong>Energia</strong><small>" + esc(summary) + "</small></div>" +
      "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-energy'>remover</button></div>" +
      "<div class='capability-form'>" +
      "<div class='field'><label>id</label><input type='text' data-energy-field='id' value='" + esc(energy.id || "") +
      "' placeholder='energy' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field'><label>capacity</label><input type='number' data-energy-field='capacity' min='1' step='1' value='" +
      esc(capabilityFieldValue(energy.capacity)) + "' placeholder='100000'></div>" +
      "<div class='field'><label>max_receive</label><input type='number' data-energy-field='maxReceive' min='0' step='1' value='" +
      esc(capabilityFieldValue(energy.maxReceive)) + "' placeholder='0'></div>" +
      "<div class='field'><label>max_extract</label><input type='number' data-energy-field='maxExtract' min='0' step='1' value='" +
      esc(capabilityFieldValue(energy.maxExtract)) + "' placeholder='0'></div>" +
      "</div><code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback capability-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
  }

  function fluidCardHtml(fluid, index) {
    var problem = V.fluidProblem(fluid);
    var serialized = JSON.stringify(V.buildFluidTank(fluid));
    var filter = trim(fluid.filter) || "qualquer fluido";
    var port = trim(fluid.port) ? " · porta " + trim(fluid.port) : "";
    var summary = (trim(fluid.id) || "sem id") + " · " + capabilityFieldValue(fluid.capacity) +
      " mB · " + filter + port;
    return "<div class='slot-card capability-card" + (problem ? " is-problem" : "") +
      "' data-fluid-index='" + index + "'><div class='matcher-head'><span class='slot-index'>F" + (index + 1) +
      "</span><div class='matcher-title'><strong>Tanque de fluido</strong><small>" + esc(summary) +
      "</small></div><button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-fluid'>remover</button></div>" +
      "<div class='capability-form'>" +
      "<div class='field'><label>id</label><input type='text' data-fluid-index='" + index +
      "' data-fluid-field='id' value='" + esc(fluid.id || "") + "' placeholder='water_tank' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field'><label>capacity</label><input type='number' data-fluid-index='" + index +
      "' data-fluid-field='capacity' min='1' step='1' value='" + esc(capabilityFieldValue(fluid.capacity)) +
      "' placeholder='16000'></div>" +
      "<div class='field'><label>max_receive</label><input type='number' data-fluid-index='" + index +
      "' data-fluid-field='maxReceive' min='0' step='1' value='" + esc(capabilityFieldValue(fluid.maxReceive)) +
      "' placeholder='0'></div>" +
      "<div class='field'><label>max_extract</label><input type='number' data-fluid-index='" + index +
      "' data-fluid-field='maxExtract' min='0' step='1' value='" + esc(capabilityFieldValue(fluid.maxExtract)) +
      "' placeholder='0'></div>" +
      "<div class='field'><label>filter</label><input type='text' data-fluid-index='" + index +
      "' data-fluid-field='filter' value='" + esc(fluid.filter || "") +
      "' placeholder='minecraft:water ou #tag' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field'><label>port</label><input type='text' data-fluid-index='" + index +
      "' data-fluid-field='port' value='" + esc(fluid.port || "") +
      "' placeholder='key do pattern (ex.: F)' spellcheck='false' autocomplete='off'></div>" +
      "</div><code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback capability-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
  }

  function chemicalCardHtml(chemical, index) {
    var problem = V.chemicalProblem(chemical);
    var serialized = JSON.stringify(V.buildChemicalTank(chemical));
    var filter = trim(chemical.filter) || "qualquer chemical";
    var summary = (trim(chemical.id) || "sem id") + " · " + capabilityFieldValue(chemical.capacity) +
      " · " + filter;
    return "<div class='slot-card capability-card" + (problem ? " is-problem" : "") +
      "' data-chemical-index='" + index + "'><div class='matcher-head'><span class='slot-index'>C" + (index + 1) +
      "</span><div class='matcher-title'><strong>Tanque chemical</strong><small>" + esc(summary) +
      "</small></div><button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-chemical'>remover</button></div>" +
      "<div class='capability-form'>" +
      "<div class='field'><label>id</label><input type='text' data-chemical-index='" + index +
      "' data-chemical-field='id' value='" + esc(chemical.id || "") + "' placeholder='input' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field'><label>capacity</label><input type='number' data-chemical-index='" + index +
      "' data-chemical-field='capacity' min='1' step='1' value='" + esc(capabilityFieldValue(chemical.capacity)) +
      "' placeholder='16000'></div>" +
      "<div class='field'><label>max_receive</label><input type='number' data-chemical-index='" + index +
      "' data-chemical-field='maxReceive' min='0' step='1' value='" + esc(capabilityFieldValue(chemical.maxReceive)) +
      "' placeholder='0'></div>" +
      "<div class='field'><label>max_extract</label><input type='number' data-chemical-index='" + index +
      "' data-chemical-field='maxExtract' min='0' step='1' value='" + esc(capabilityFieldValue(chemical.maxExtract)) +
      "' placeholder='0'></div>" +
      "<div class='field'><label>filter</label><input type='text' data-chemical-index='" + index +
      "' data-chemical-field='filter' value='" + esc(chemical.filter || "") +
      "' placeholder='mekanism:hydrogen ou #tag' spellcheck='false' autocomplete='off'></div>" +
      "</div><code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback capability-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
  }

  function manaCardHtml(mana) {
    var problem = V.manaProblem(mana);
    var serialized = JSON.stringify(V.buildMana(mana));
    var summary = capabilityFieldValue(mana.capacity) + " de capacidade · recebe " +
      capabilityFieldValue(mana.maxReceive) + " · extrai " + capabilityFieldValue(mana.maxExtract) +
      (trim(mana.port) ? " · porta " + trim(mana.port) : "");
    return "<div class='slot-card capability-card" + (problem ? " is-problem" : "") +
      "' data-capability='mana'><div class='matcher-head'><span class='slot-index'>M</span>" +
      "<div class='matcher-title'><strong>Tanque de mana</strong><small>" + esc(summary) +
      "</small></div><button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-mana'>remover</button></div>" +
      "<div class='capability-form'>" +
      "<div class='field'><label>capacity</label><input type='number' data-mana-field='capacity' min='1' step='1' value='" +
      esc(capabilityFieldValue(mana.capacity)) + "' placeholder='1000000'></div>" +
      "<div class='field'><label>max_receive</label><input type='number' data-mana-field='maxReceive' min='0' step='1' value='" +
      esc(capabilityFieldValue(mana.maxReceive)) + "' placeholder='1000000'></div>" +
      "<div class='field'><label>max_extract</label><input type='number' data-mana-field='maxExtract' min='0' step='1' value='" +
      esc(capabilityFieldValue(mana.maxExtract)) + "' placeholder='1000000'></div>" +
      "<div class='field'><label>port</label><input type='text' data-mana-field='port' value='" +
      esc(mana.port || "") + "' placeholder='key do pattern (ex.: M)' spellcheck='false' autocomplete='off'></div>" +
      "</div><code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback capability-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
  }

  function essentiaCardHtml(essentia) {
    var problem = V.essentiaProblem(essentia);
    var serialized = JSON.stringify(V.buildEssentia(essentia));
    var summary = "raio " + (capabilityFieldValue(essentia.range) || "12") + " blocos";
    return "<div class='slot-card capability-card" + (problem ? " is-problem" : "") +
      "' data-capability='essentia'><div class='matcher-head'><span class='slot-index'>E</span>" +
      "<div class='matcher-title'><strong>Essentia</strong><small>" + esc(summary) +
      "</small></div><button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-essentia'>remover</button></div>" +
      "<div class='capability-form'>" +
      "<div class='field'><label>range</label><input type='number' data-essentia-field='range' min='1' max='32' step='1' value='" +
      esc(capabilityFieldValue(essentia.range)) + "' placeholder='12'></div>" +
      "</div><code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback capability-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
  }

  function renderCapabilities() {
    renderCapabilityPorts();
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var summary = $("capabilitiesSummary");
    var empty = $("capabilitiesEmpty");
    var list = $("capabilityList");
    var removeButton = $("removeCapabilitiesBtn");
    if (!capabilities) {
      summary.textContent = capabilitySummaryText();
      empty.hidden = false;
      empty.textContent = "Nenhuma capability definida. Adicione energia, tanque ou essentia para expor FE/fluidos/chemicals/mana/essentia.";
      list.innerHTML = "";
      removeButton.disabled = true;
      $("addEnergyBtn").disabled = false;
      $("addFluidBtn").disabled = false;
      $("addChemicalBtn").disabled = false;
      $("addManaBtn").disabled = false;
      $("addEssentiaBtn").disabled = false;
      return;
    }
    var energy = isPlainObject(capabilities.energy) ? capabilities.energy : null;
    var fluids = Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    var chemicalTanks = isPlainObject(capabilities.chemical)
      && Array.isArray(capabilities.chemical.tanks)
      ? capabilities.chemical.tanks
      : [];
    var mana = isPlainObject(capabilities.mana) ? capabilities.mana : null;
    var essentia = isPlainObject(capabilities.essentia) ? capabilities.essentia : null;
    var parts = [];
    if (energy) {
      parts.push(energyCardHtml(energy));
    }
    fluids.forEach(function (fluid, index) {
      if (isPlainObject(fluid)) {
        parts.push(fluidCardHtml(fluid, index));
      }
    });
    chemicalTanks.forEach(function (chemical, index) {
      if (isPlainObject(chemical)) {
        parts.push(chemicalCardHtml(chemical, index));
      }
    });
    if (mana) {
      parts.push(manaCardHtml(mana));
    }
    if (essentia) {
      parts.push(essentiaCardHtml(essentia));
    }
    summary.textContent = capabilitySummaryText();
    empty.hidden = parts.length > 0;
    empty.textContent = parts.length === 0
      ? "Capabilities ativas, mas sem energia ou tanques. Use + energia, + tanque, + chemical, + mana ou + essentia."
      : "";
    list.innerHTML = parts.join("");
    removeButton.disabled = false;
    $("addEnergyBtn").disabled = Boolean(energy);
    $("addFluidBtn").disabled = false;
    $("addChemicalBtn").disabled = false;
    $("addManaBtn").disabled = Boolean(mana);
    $("addEssentiaBtn").disabled = Boolean(essentia);
  }

  function energyCardElement() {
    return document.querySelector(".capability-card[data-capability='energy']");
  }

  function fluidCardElement(index) {
    return document.querySelector(".capability-card[data-fluid-index='" + index + "']");
  }

  function chemicalCardElement(index) {
    return document.querySelector(".capability-card[data-chemical-index='" + index + "']");
  }

  function manaCardElement() {
    return document.querySelector(".capability-card[data-capability='mana']");
  }

  function essentiaCardElement() {
    return document.querySelector(".capability-card[data-capability='essentia']");
  }

  function updateEnergyCard() {
    var card = energyCardElement();
    var energy = isPlainObject(state.capabilities) ? state.capabilities.energy : null;
    if (!card || !isPlainObject(energy)) {
      return;
    }
    var problem = V.energyProblem(energy);
    var feedback = card.querySelector(".capability-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback capability-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildEnergy(energy));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = (trim(energy.id) || "sem id") + " · " + capabilityFieldValue(energy.capacity) +
        " FE · recebe " + capabilityFieldValue(energy.maxReceive) + " · extrai " +
        capabilityFieldValue(energy.maxExtract);
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function updateFluidCard(index) {
    var card = fluidCardElement(index);
    var fluid = isPlainObject(state.capabilities) && Array.isArray(state.capabilities.fluids)
      ? state.capabilities.fluids[index]
      : null;
    if (!card || !isPlainObject(fluid)) {
      return;
    }
    var problem = V.fluidProblem(fluid);
    var feedback = card.querySelector(".capability-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback capability-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildFluidTank(fluid));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = (trim(fluid.id) || "sem id") + " · " + capabilityFieldValue(fluid.capacity) +
        " mB · " + (trim(fluid.filter) || "qualquer fluido") +
        (trim(fluid.port) ? " · porta " + trim(fluid.port) : "");
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function updateChemicalCard(index) {
    var card = chemicalCardElement(index);
    var chemical = isPlainObject(state.capabilities)
      && isPlainObject(state.capabilities.chemical)
      && Array.isArray(state.capabilities.chemical.tanks)
      ? state.capabilities.chemical.tanks[index]
      : null;
    if (!card || !isPlainObject(chemical)) {
      return;
    }
    var problem = V.chemicalProblem(chemical);
    var feedback = card.querySelector(".capability-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback capability-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildChemicalTank(chemical));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = (trim(chemical.id) || "sem id") + " · " +
        capabilityFieldValue(chemical.capacity) + " · " +
        (trim(chemical.filter) || "qualquer chemical");
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function updateManaCard() {
    var card = manaCardElement();
    var mana = isPlainObject(state.capabilities) ? state.capabilities.mana : null;
    if (!card || !isPlainObject(mana)) {
      return;
    }
    var problem = V.manaProblem(mana);
    var feedback = card.querySelector(".capability-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback capability-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildMana(mana));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = capabilityFieldValue(mana.capacity) + " de capacidade · recebe " +
        capabilityFieldValue(mana.maxReceive) + " · extrai " + capabilityFieldValue(mana.maxExtract) +
        (trim(mana.port) ? " · porta " + trim(mana.port) : "");
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function updateEssentiaCard() {
    var card = essentiaCardElement();
    var essentia = isPlainObject(state.capabilities) ? state.capabilities.essentia : null;
    if (!card || !isPlainObject(essentia)) {
      return;
    }
    var problem = V.essentiaProblem(essentia);
    var feedback = card.querySelector(".capability-feedback");
    if (feedback) {
      feedback.className = "matcher-feedback capability-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    var jsonElement = card.querySelector(".slot-json");
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildEssentia(essentia));
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
    var title = card.querySelector(".matcher-title small");
    if (title) {
      title.textContent = "raio " + (capabilityFieldValue(essentia.range) || "12") + " blocos";
    }
    card.classList.toggle("is-problem", Boolean(problem));
  }

  function nextChemicalId() {
    var used = {};
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var tanks = capabilities && isPlainObject(capabilities.chemical)
      && Array.isArray(capabilities.chemical.tanks)
      ? capabilities.chemical.tanks
      : [];
    tanks.forEach(function (tank) {
      used[trim(tank.id)] = true;
    });
    var index = 0;
    while (used["chemical_" + index]) {
      index += 1;
    }
    return "chemical_" + index;
  }

  function nextFluidId() {
    var used = {};
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var fluids = capabilities && Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    fluids.forEach(function (fluid) {
      used[trim(fluid.id)] = true;
    });
    var index = 0;
    while (used["fluid_" + index]) {
      index += 1;
    }
    return "fluid_" + index;
  }

  function addEnergy() {
    var capabilities = ensureCapabilities();
    if (isPlainObject(capabilities.energy)) {
      showToast("A energia já está definida. Remova a atual para adicionar outra.", "error");
      return;
    }
    capabilities.energy = V.createEnergy();
    refresh();
    var card = energyCardElement();
    var input = card ? card.querySelector("[data-energy-field='id']") : null;
    focusElement(input || card, false);
  }

  function addFluidTank() {
    var capabilities = ensureCapabilities();
    if (!Array.isArray(capabilities.fluids)) {
      capabilities.fluids = [];
    }
    var fluid = V.createFluidTank();
    fluid.id = nextFluidId();
    capabilities.fluids.push(fluid);
    refresh();
    var card = fluidCardElement(capabilities.fluids.length - 1);
    var input = card ? card.querySelector("[data-fluid-field='id']") : null;
    focusElement(input || card, false);
  }

  function addChemicalTank() {
    var capabilities = ensureCapabilities();
    if (!isPlainObject(capabilities.chemical)) {
      capabilities.chemical = { tanks: [], extras: {} };
    }
    if (!Array.isArray(capabilities.chemical.tanks)) {
      capabilities.chemical.tanks = [];
    }
    var chemical = V.createChemicalTank();
    chemical.id = nextChemicalId();
    capabilities.chemical.tanks.push(chemical);
    refresh();
    var card = chemicalCardElement(capabilities.chemical.tanks.length - 1);
    var input = card ? card.querySelector("[data-chemical-field='id']") : null;
    focusElement(input || card, false);
  }

  function addMana() {
    var capabilities = ensureCapabilities();
    if (isPlainObject(capabilities.mana)) {
      showToast("O tanque de mana ja esta definido. Remova o atual para adicionar outro.", "error");
      return;
    }
    capabilities.mana = V.createMana();
    refresh();
    var card = manaCardElement();
    var input = card ? card.querySelector("[data-mana-field='capacity']") : null;
    focusElement(input || card, false);
  }

  function removeMana() {
    if (!isPlainObject(state.capabilities) || !isPlainObject(state.capabilities.mana)) {
      return;
    }
    if (!window.confirm("Remover o tanque de mana da maquina?")) {
      return;
    }
    state.capabilities.mana = null;
    refresh();
  }

  function addEssentia() {
    var capabilities = ensureCapabilities();
    if (isPlainObject(capabilities.essentia)) {
      showToast("A capability essentia ja esta definida. Remova a atual para adicionar outra.", "error");
      return;
    }
    capabilities.essentia = V.createEssentia();
    refresh();
    var card = essentiaCardElement();
    var input = card ? card.querySelector("[data-essentia-field='range']") : null;
    focusElement(input || card, false);
  }

  function removeEssentia() {
    if (!isPlainObject(state.capabilities) || !isPlainObject(state.capabilities.essentia)) {
      return;
    }
    if (!window.confirm("Remover a capability essentia da maquina?")) {
      return;
    }
    state.capabilities.essentia = null;
    refresh();
  }

  function removeEnergy() {
    if (!isPlainObject(state.capabilities) || !isPlainObject(state.capabilities.energy)) {
      return;
    }
    if (!window.confirm("Remover a energia da máquina?")) {
      return;
    }
    state.capabilities.energy = null;
    refresh();
  }

  function removeFluidTank(index) {
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    if (!capabilities || !Array.isArray(capabilities.fluids) ||
      index < 0 || index >= capabilities.fluids.length) {
      return;
    }
    var name = trim(capabilities.fluids[index].id) || "tanque " + (index + 1);
    if (!window.confirm("Remover o " + name + "?")) {
      return;
    }
    capabilities.fluids.splice(index, 1);
    refresh();
  }

  function removeChemicalTank(index) {
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var tanks = capabilities && isPlainObject(capabilities.chemical)
      && Array.isArray(capabilities.chemical.tanks)
      ? capabilities.chemical.tanks
      : null;
    if (!tanks || index < 0 || index >= tanks.length) {
      return;
    }
    var name = trim(tanks[index].id) || "tanque chemical " + (index + 1);
    if (!window.confirm("Remover o " + name + "?")) {
      return;
    }
    tanks.splice(index, 1);
    refresh();
  }

  function removeCapabilities() {
    if (!isPlainObject(state.capabilities)) {
      return;
    }
    if (!window.confirm("Remover todas as capabilities da máquina? Portas e receitas chemical/mana/essentia existentes serão ignoradas.")) {
      return;
    }
    state.capabilities = null;
    refresh();
    showToast("Capabilities removidas.");
  }

  function onCapabilityListInput(event) {
    var target = event.target;
    if (!target || target.tagName !== "INPUT") {
      return;
    }
    var energyField = target.getAttribute("data-energy-field");
    if (energyField) {
      var energy = isPlainObject(state.capabilities) ? state.capabilities.energy : null;
      if (!isPlainObject(energy) || ["id", "capacity", "maxReceive", "maxExtract"].indexOf(energyField) === -1) {
        return;
      }
      energy[energyField] = target.value;
      updateEnergyCard();
      updateCapabilitySummary();
      refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
      return;
    }
    var manaField = target.getAttribute("data-mana-field");
    var mana = isPlainObject(state.capabilities) ? state.capabilities.mana : null;
    if (manaField && isPlainObject(mana)
        && ["capacity", "maxReceive", "maxExtract", "port"].indexOf(manaField) !== -1) {
      mana[manaField] = target.value;
      updateManaCard();
      updateCapabilitySummary();
      refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
      return;
    }
    var essentiaField = target.getAttribute("data-essentia-field");
    var essentia = isPlainObject(state.capabilities) ? state.capabilities.essentia : null;
    if (essentiaField && isPlainObject(essentia) && essentiaField === "range") {
      essentia.range = target.value;
      updateEssentiaCard();
      updateCapabilitySummary();
      refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
      return;
    }
    var chemicalIndex = parseInt(target.getAttribute("data-chemical-index"), 10);
    var chemicalField = target.getAttribute("data-chemical-field");
    var chemicalTank = isPlainObject(state.capabilities)
      && isPlainObject(state.capabilities.chemical)
      && Array.isArray(state.capabilities.chemical.tanks)
      ? state.capabilities.chemical.tanks[chemicalIndex]
      : null;
    if (chemicalField && isPlainObject(chemicalTank)
        && ["id", "capacity", "maxReceive", "maxExtract", "filter"].indexOf(chemicalField) !== -1) {
      chemicalTank[chemicalField] = target.value;
      updateChemicalCard(chemicalIndex);
      updateCapabilitySummary();
      refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
      return;
    }
    var index = parseInt(target.getAttribute("data-fluid-index"), 10);
    var field = target.getAttribute("data-fluid-field");
    var fluid = isPlainObject(state.capabilities) && Array.isArray(state.capabilities.fluids)
      ? state.capabilities.fluids[index]
      : null;
    if (!isPlainObject(fluid) || ["id", "capacity", "maxReceive", "maxExtract", "filter", "port"].indexOf(field) === -1) {
      return;
    }
    fluid[field] = target.value;
    updateFluidCard(index);
    updateCapabilitySummary();
    refresh({ skipPattern: true, skipMatchers: true, skipSlots: true, skipCapabilities: true });
  }

  function onCapabilityListClick(event) {
    if (closest(event.target, "[data-action='remove-energy']")) {
      removeEnergy();
      return;
    }
    if (closest(event.target, "[data-action='remove-mana']")) {
      removeMana();
      return;
    }
    if (closest(event.target, "[data-action='remove-essentia']")) {
      removeEssentia();
      return;
    }
    var chemicalAction = closest(event.target, "[data-action='remove-chemical']");
    if (chemicalAction) {
      var chemicalCard = closest(chemicalAction, ".capability-card");
      var chemicalIndex = chemicalCard ? parseInt(chemicalCard.getAttribute("data-chemical-index"), 10) : -1;
      removeChemicalTank(chemicalIndex);
      return;
    }
    var action = closest(event.target, "[data-action='remove-fluid']");
    if (!action) {
      return;
    }
    var card = closest(action, ".capability-card");
    var index = card ? parseInt(card.getAttribute("data-fluid-index"), 10) : -1;
    removeFluidTank(index);
  }

  var CONTROLLER_RECIPE_SYMBOLS = ["A", "B", "C", "D", "E", "F", "G", "H", "I"];
  var activeControllerRecipeSymbol = "A";

  function controllerRecipeGrid(recipe) {
    var grid = [
      [" ", " ", " "],
      [" ", " ", " "],
      [" ", " ", " "]
    ];
    if (!isPlainObject(recipe) || !Array.isArray(recipe.pattern)) {
      return grid;
    }
    for (var row = 0; row < 3; row += 1) {
      var source = row < recipe.pattern.length && typeof recipe.pattern[row] === "string"
        ? recipe.pattern[row]
        : "";
      for (var column = 0; column < 3; column += 1) {
        if (column < source.length) {
          grid[row][column] = source.charAt(column);
        }
      }
    }
    return grid;
  }

  function controllerRecipePatternFromGrid(grid) {
    return grid.map(function (row) {
      return row.join("");
    });
  }

  function controllerRecipeUsedSymbols(recipe) {
    var used = {};
    controllerRecipeGrid(recipe).forEach(function (row) {
      row.forEach(function (symbol) {
        if (symbol !== " ") {
          used[symbol] = true;
        }
      });
    });
    return Object.keys(used).sort(compareSymbols);
  }

  function syncControllerRecipeKeys(recipe) {
    if (!isPlainObject(recipe)) {
      return;
    }
    var existing = {};
    if (Array.isArray(recipe.key)) {
      recipe.key.forEach(function (row) {
        if (isPlainObject(row) && trim(row.symbol)) {
          existing[trim(row.symbol)] = trim(row.item);
        }
      });
    }
    recipe.key = controllerRecipeUsedSymbols(recipe).map(function (symbol) {
      return {
        symbol: symbol,
        item: existing[symbol] || ""
      };
    });
  }

  function controllerRecipeKeyListHtml(recipe) {
    var symbols = controllerRecipeUsedSymbols(recipe);
    if (symbols.length === 0) {
      return "<p class='field-hint'>Pinte ao menos um slot no pattern para gerar a key.</p>";
    }
    var items = {};
    if (Array.isArray(recipe.key)) {
      recipe.key.forEach(function (row) {
        if (isPlainObject(row)) {
          items[trim(row.symbol)] = trim(row.item);
        }
      });
    }
    return symbols.map(function (symbol) {
      return "<div class='controller-recipe-key-row'><span class='controller-recipe-key-symbol'>" +
        esc(symbol) + "</span><input type='text' data-controller-recipe-key-symbol='" +
        esc(symbol) + "' spellcheck='false' autocomplete='off' placeholder='minecraft:item' value='" +
        esc(items[symbol] || "") + "'></div>";
    }).join("");
  }

  function controllerRecipeSummaryText() {
    var recipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    if (!recipe) {
      return "Sem receita shaped para o controller.";
    }
    var rows = Array.isArray(recipe.pattern) ? recipe.pattern : [];
    var width = 0;
    rows.forEach(function (row) {
      width = Math.max(width, trim(row).length);
    });
    return "Shaped " + rows.length + "x" + width + " -> infinity_nexus_multiblock:multiblock_controller";
  }

  function deriveControllerMode() {
    if (V.hasModel && V.hasModel(state.model)) {
      return "custom";
    }
    if (Array.isArray(state.shape) && state.shape.length > 0) {
      return "custom";
    }
    return "default";
  }

  function controllerModeSummaryText() {
    if (controllerMode === "custom") {
      return "Controller custom: model e shape por definição; texturas do modelo com overrides opcionais.";
    }
    return "Controller padrão: modelo embutido com fallback; texturas custom são opcionais.";
  }

  function renderControllerMode() {
    $$("input[name='controllerMode']").forEach(function (input) {
      input.checked = input.value === controllerMode;
    });
    var summary = $("controllerModeSummary");
    if (summary) {
      summary.textContent = controllerModeSummaryText();
    }
    var modelPanel = $("modelPanel");
    var shapePanel = $("shapePanel");
    if (modelPanel) {
      modelPanel.hidden = controllerMode !== "custom";
    }
    if (shapePanel) {
      shapePanel.hidden = controllerMode !== "custom";
    }
  }

  function setControllerMode(mode) {
    if (mode !== "custom" && mode !== "default") {
      return;
    }
    if (mode === controllerMode) {
      return;
    }
    controllerMode = mode;
    modelPreviewState.localModels = {};
    resetPreviewTextureOverrides();
    if (mode === "default") {
      state.model = V.createModel();
      state.shape = V.createShape();
      modelPreviewState.previewState = "idle";
    }
    renderControllerMode();
    refresh({ skipControllerRecipe: true });
    loadModelPreviewFromField(true);
    showToast(mode === "custom"
      ? "Controller custom: configure model e shape; texturas do preview são só visuais."
      : "Controller padrão: model e shape removidos; texturas importadas vão para o JSON.");
  }

  function onControllerModeChange(event) {
    var input = closest(event.target, "input[name='controllerMode']");
    if (!input) {
      return;
    }
    setControllerMode(input.value);
  }

  function modelSummaryText() {
    var model = isPlainObject(state.model) ? state.model : null;
    var parts = model ? ["idle", "active", "building"].filter(function (key) {
      return Boolean(trim(model[key]));
    }) : [];
    if (parts.length === 0) {
      return "Sem modelo por definição; usa o modelo padrão do controller.";
    }
    return parts.join(" + ") + " configurado(s).";
  }

  function updateModelCard() {
    var model = isPlainObject(state.model) ? state.model : V.createModel();
    var summary = $("modelSummary");
    var feedback = $("modelFeedback");
    var jsonElement = $("modelJson");
    if (summary) {
      summary.textContent = modelSummaryText();
    }
    if (feedback) {
      var problem = V.modelProblem(model);
      feedback.textContent = problem;
      feedback.hidden = !problem;
      feedback.className = "matcher-feedback controller-feedback" + (problem ? " is-error" : "");
    }
    if (jsonElement) {
      jsonElement.textContent = JSON.stringify(V.buildModel(model), null, 2);
    }
  }

  function renderModel() {
    var model = isPlainObject(state.model) ? state.model : V.createModel();
    $$("[data-model-field]").forEach(function (input) {
      var field = input.getAttribute("data-model-field");
      input.value = trim(model[field]);
      input.classList.remove("is-invalid");
    });
    updateModelCard();
  }

  function onModelInput(event) {
    var input = closest(event.target, "[data-model-field]");
    if (!input) {
      return;
    }
    var field = input.getAttribute("data-model-field");
    if (!isPlainObject(state.model)) {
      state.model = V.createModel();
    }
    state.model[field] = input.value;
    delete modelPreviewState.localModels[field];
    modelPreviewState.previewState = MODEL_KEYS.indexOf(field) >= 0 ? field : "idle";
    updateModelCard();
    refresh({ skipModel: true, skipTextures: true, skipControllerRecipe: true });
    scheduleModelPreviewFieldLoad();
  }

  function currentNamespace() {
    var namespace = trim(state.namespace);
    return namespace && V.isValidNamespace(namespace) ? namespace : "minecraft";
  }

  function namespaceFromPath(path) {
    var text = String(path || "").replace(/\\/g, "/");
    var match = text.match(/(?:^|\/)assets\/([a-z0-9_.-]+)\/models\//i);
    return match ? match[1] : "";
  }

  function modelImportId(field, file) {
    var relative = String(file && (file.webkitRelativePath || file.relativePath) || "")
      .replace(/\\/g, "/");
    var namespace = namespaceFromPath(relative) || currentNamespace();
    var pathMatch = relative.match(/(?:^|\/)assets\/[a-z0-9_.-]+\/models\/(.+)\.json$/i);
    if (pathMatch) {
      return namespace + ":" + pathMatch[1];
    }
    var base = String(file && file.name || field).replace(/\.json$/i, "").trim().toLowerCase();
    base = base.replace(/[^a-z0-9_.-]+/g, "_").replace(/^_+|_+$/g, "");
    if (!base) {
      base = field;
    }
    return namespace + ":block/" + base;
  }

  function onModelImportClick(event) {
    var button = closest(event.target, "[data-model-import]");
    if (!button) {
      return;
    }
    var field = button.getAttribute("data-model-import");
    if (MODEL_KEYS.indexOf(field) < 0) {
      return;
    }
    modelPreviewState.importTarget = field;
    var input = $("modelImportInput");
    if (input) {
      input.value = "";
      input.click();
    }
  }

  function onModelImportChange(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    var field = modelPreviewState.importTarget;
    input.value = "";
    modelPreviewState.importTarget = "";
    if (!file || MODEL_KEYS.indexOf(field) < 0) {
      return;
    }
    if (!ModelPreview) {
      showToast("O preview 3D não carregou (modelpreview3d.js). " +
        "Confira se o arquivo foi publicado junto do builder.", "error");
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var suggested = modelImportId(field, file);
        var parts = splitPreviewIdentifier(suggested);
        var parsed = ModelPreview.parseModel(String(reader.result || ""), {
          namespace: parts ? parts.namespace : currentNamespace()
        });
        if (!isPlainObject(state.model)) {
          state.model = V.createModel();
        }
        state.model[field] = suggested;
        modelPreviewState.localModels[field] = parsed;
        resetPreviewTextureOverrides();
        modelPreviewState.previewState = field;
        refresh({ skipTextures: true, skipControllerRecipe: true, skipModelPreview: true });
        loadModelPreviewFromField(true);
        showToast("Modelo " + field + " importado: " + suggested + ".");
      } catch (error) {
        showToast("Modelo inválido: " + (error && error.message ? error.message : error), "error");
      }
    };
    reader.onerror = function () {
      showToast("Falha ao ler o modelo.", "error");
    };
    reader.readAsText(file);
  }

  function resetModel() {
    state.model = V.createModel();
    modelPreviewState.localModels = {};
    resetPreviewTextureOverrides();
    modelPreviewState.previewState = "idle";
    refresh({ skipTextures: true, skipControllerRecipe: true });
    loadModelPreviewFromField(true);
    showToast("Modelo por definição removido.");
  }

  function shapeSummaryText() {
    var shape = Array.isArray(state.shape) ? state.shape : [];
    if (shape.length === 0) {
      return "Sem shape por definição; colisão e seleção usam o cubo completo.";
    }
    return shape.length + (shape.length === 1 ? " caixa" : " caixas") +
      " de colisão/seleção configurada(s).";
  }

  function updateShapeCard() {
    var shape = Array.isArray(state.shape) ? state.shape : V.createShape();
    var summary = $("shapeSummary");
    var feedback = $("shapeFeedback");
    var jsonElement = $("shapeJson");
    var codeHint = $("shapeCodeHint");
    if (summary) {
      summary.textContent = shapeSummaryText();
    }
    if (codeHint) {
      codeHint.textContent = shape.length + (shape.length === 1 ? " caixa" : " caixas");
    }
    if (feedback) {
      var problem = V.shapeProblem(shape);
      feedback.textContent = problem;
      feedback.hidden = !problem;
      feedback.className = "matcher-feedback controller-feedback" + (problem ? " is-error" : "");
    }
    if (jsonElement) {
      jsonElement.textContent = JSON.stringify(V.buildShape(shape), null, 2);
    }
  }

  function shapeInputValue(box, fieldIndex) {
    if (!Array.isArray(box)) {
      return "";
    }
    var value = box[fieldIndex];
    return value === undefined || value === null ? "" : String(value);
  }

  function renderShape() {
    var shape = Array.isArray(state.shape) ? state.shape : V.createShape();
    var list = $("shapeList");
    if (!list) {
      return;
    }
    var labels = ["min x", "min y", "min z", "max x", "max y", "max z"];
    if (shape.length === 0) {
      list.innerHTML = "<p class='field-hint'>Nenhuma caixa. Sem shape, a colisão e a seleção usam o cubo completo.</p>";
    } else {
      list.innerHTML = shape.map(function (box, index) {
        var fields = labels.map(function (label, fieldIndex) {
          return "<div class='field'><label for='shape" + index + "Field" + fieldIndex + "'>" + label +
            "</label><input id='shape" + index + "Field" + fieldIndex +
            "' type='number' min='0' max='16' step='0.5' data-shape-index='" + index +
            "' data-shape-field='" + fieldIndex + "' value='" + esc(shapeInputValue(box, fieldIndex)) + "'></div>";
        }).join("");
        return "<details class='card-advanced shape-box'" + (index === 0 ? " open" : "") +
          "><summary><span>Caixa " + (index + 1) + "</span><span class='card-advanced-hint'>6 campos</span></summary>" +
          "<div class='card-advanced-body'><div class='shape-box-head'><span>min/max em pixels</span>" +
          "<button type='button' class='btn btn-tiny btn-danger' data-shape-remove='" + index +
          "'>remover</button></div><div class='shape-fields'>" + fields + "</div></div></details>";
      }).join("");
    }
    updateShapeCard();
  }

  function shapeInputNumber(value) {
    var text = String(value === undefined || value === null ? "" : value).trim();
    if (text === "") {
      return "";
    }
    var parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : text;
  }

  function onShapeInput(event) {
    var input = closest(event.target, "[data-shape-field]");
    if (!input) {
      return;
    }
    var index = Number.parseInt(input.getAttribute("data-shape-index"), 10);
    var fieldIndex = Number.parseInt(input.getAttribute("data-shape-field"), 10);
    if (!Array.isArray(state.shape) || !Array.isArray(state.shape[index])) {
      return;
    }
    state.shape[index][fieldIndex] = shapeInputNumber(input.value);
    updateShapeCard();
    refresh({ skipShape: true, skipModel: true, skipTextures: true, skipControllerRecipe: true });
  }

  function addShapeBox() {
    if (!Array.isArray(state.shape)) {
      state.shape = V.createShape();
    }
    state.shape.push([0, 0, 0, 16, 16, 16]);
    refresh({ skipModel: true, skipTextures: true, skipControllerRecipe: true });
  }

  function resetShape() {
    state.shape = V.createShape();
    refresh({ skipModel: true, skipTextures: true, skipControllerRecipe: true });
    showToast("Shape por definição removido.");
  }

  function applyShapeFromModel(parsed) {
    if (!ModelPreview || typeof ModelPreview.shapeBoxes !== "function") {
      showToast("Preview do modelo indisponível para gerar o shape.");
      return;
    }
    var result = ModelPreview.shapeBoxes(parsed, { precision: 0.0001, limit: 16 });
    if (!result.boxes.length) {
      var reason = result.skippedRotation > 0
        ? " Nenhum elemento com rotação de 90° ou sem rotação."
        : "";
      showToast("Nenhuma caixa gerada do modelo." + reason);
      return;
    }
    state.shape = result.boxes;
    refresh({ skipModel: true, skipTextures: true, skipControllerRecipe: true });
    var message = result.boxes.length + (result.boxes.length === 1 ? " caixa gerada" : " caixas geradas");
    if (result.skippedRotation > 0) {
      message += "; " + result.skippedRotation + " ignorada(s) por rotação fora de 90°";
    }
    if (result.skippedFlat > 0) {
      message += "; " + result.skippedFlat + " sem volume";
    }
    if (result.clipped > 0) {
      message += "; " + result.clipped + " ajustada(s) ao limite 0–16";
    }
    showToast(message + ".");
  }

  function useShapeFromModel() {
    if (!ModelPreview) {
      showToast("Preview do modelo indisponível.");
      return;
    }
    if (modelPreviewState.previewState === "idle" && modelPreviewState.parsed) {
      applyShapeFromModel(modelPreviewState.parsed);
      return;
    }
    var local = modelPreviewState.localModels.idle;
    if (local) {
      applyShapeFromModel(applyPreviewTextureOverrides(local));
      return;
    }
    var entry = previewModelEntry("idle");
    if (!entry) {
      showToast("Preencha model.idle para gerar o shape.");
      return;
    }
    var parts = splitPreviewIdentifier(entry.id);
    if (!parts || typeof fetch !== "function") {
      showToast("Não foi possível carregar o modelo do campo. Confira model e o servidor HTTP.");
      return;
    }
    var url = modelPreviewAssetUrl(entry.id, "models", ".json");
    if (!url) {
      showToast("Identificador de modelo inválido no campo " + entry.key + ".");
      return;
    }
    showToast("Carregando modelo do campo " + entry.key + "...");
    fetch(url, { cache: "no-store" }).then(function (response) {
      if (!response.ok) {
        throw new Error("HTTP " + response.status);
      }
      return response.text();
    }).then(function (text) {
      applyShapeFromModel(ModelPreview.parseModel(text, { namespace: parts.namespace }));
    }).catch(function (error) {
      var reason = error && error.message ? error.message : String(error);
      if (/HTTP 404|Not Found/i.test(reason)) {
        showToast("Modelo " + entry.id +
          " não está publicado no site. Use importar no campo " + entry.key +
          " para carregar o JSON do seu computador.", "error");
        return;
      }
      showToast("Não foi possível carregar " + entry.id + ": " + reason +
        ". Confira o campo model.", "error");
    });
  }

  function onShapeListClick(event) {
    var button = closest(event.target, "[data-shape-remove]");
    if (!button) {
      return;
    }
    var index = Number.parseInt(button.getAttribute("data-shape-remove"), 10);
    if (!Array.isArray(state.shape) || index < 0 || index >= state.shape.length) {
      return;
    }
    state.shape.splice(index, 1);
    refresh({ skipModel: true, skipTextures: true, skipControllerRecipe: true });
  }

  function updateControllerRecipeCard() {
    var recipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    var summary = $("controllerRecipeSummary");
    var feedback = $("controllerRecipeFeedback");
    var jsonElement = $("controllerRecipeJson");
    if (!summary) {
      return;
    }
    summary.textContent = controllerRecipeSummaryText();
    if (!recipe) {
      if (feedback) {
        feedback.hidden = true;
        feedback.textContent = "";
      }
      if (jsonElement) {
        jsonElement.textContent = "";
      }
      return;
    }
    var problem = V.controllerRecipeProblem(recipe);
    if (feedback) {
      feedback.className = "matcher-feedback controller-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildControllerRecipe(recipe), null, 2);
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
  }

  function renderControllerRecipe() {
    var recipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    var summary = $("controllerRecipeSummary");
    var empty = $("controllerRecipeEmpty");
    var form = $("controllerRecipeForm");
    var addButton = $("addControllerRecipeBtn");
    var removeButton = $("removeControllerRecipeBtn");
    if (!summary || !empty || !form) {
      return;
    }
    if (!recipe) {
      summary.textContent = controllerRecipeSummaryText();
      empty.hidden = false;
      form.hidden = true;
      if (addButton) {
        addButton.disabled = false;
      }
      if (removeButton) {
        removeButton.disabled = true;
      }
      updateControllerRecipeCard();
      return;
    }

    syncControllerRecipeKeys(recipe);
    if (CONTROLLER_RECIPE_SYMBOLS.indexOf(activeControllerRecipeSymbol) === -1) {
      activeControllerRecipeSymbol = "A";
    }
    renderControllerRecipePalette(recipe);
    renderControllerRecipeGrid(recipe);
    var keyList = $("controllerRecipeKeyList");
    if (keyList) {
      keyList.innerHTML = controllerRecipeKeyListHtml(recipe);
    }
    empty.hidden = true;
    form.hidden = false;
    if (addButton) {
      addButton.disabled = true;
    }
    if (removeButton) {
      removeButton.disabled = false;
    }
    updateControllerRecipeCard();
  }

  function renderControllerRecipePalette(recipe) {
    var palette = $("controllerRecipePalette");
    if (!palette) {
      return;
    }
    var used = {};
    controllerRecipeUsedSymbols(recipe).forEach(function (symbol) {
      used[symbol] = true;
    });
    palette.innerHTML = CONTROLLER_RECIPE_SYMBOLS.map(function (symbol) {
      return "<button type='button' class='controller-recipe-symbol" +
        (symbol === activeControllerRecipeSymbol ? " is-active" : "") +
        (used[symbol] ? " is-used" : "") +
        "' data-controller-recipe-symbol='" + symbol + "' aria-pressed='" +
        (symbol === activeControllerRecipeSymbol ? "true" : "false") + "'>" + symbol + "</button>";
    }).join("");
  }

  function renderControllerRecipeGrid(recipe) {
    var gridElement = $("controllerRecipeGrid");
    if (!gridElement) {
      return;
    }
    var grid = controllerRecipeGrid(recipe);
    var html = [];
    for (var row = 0; row < 3; row += 1) {
      for (var column = 0; column < 3; column += 1) {
        var symbol = grid[row][column];
        var label = "Linha " + (row + 1) + ", coluna " + (column + 1) + ": " +
          (symbol === " " ? "vazio" : symbol);
        html.push("<button type='button' class='controller-recipe-slot" +
          (symbol === " " ? "" : " is-filled") + "' data-controller-recipe-row='" + row +
          "' data-controller-recipe-column='" + column + "' title='" + esc(label) +
          "' aria-label='" + esc(label) + "'>" + (symbol === " " ? "" : esc(symbol)) + "</button>");
      }
    }
    gridElement.innerHTML = html.join("");
  }

  function addControllerRecipe() {
    if (!isPlainObject(state.controllerRecipe)) {
      state.controllerRecipe = V.createControllerRecipe();
    }
    activeControllerRecipeSymbol = "A";
    refresh();
    focusElement($("controllerRecipePalette"), false);
  }

  function removeControllerRecipe() {
    if (!isPlainObject(state.controllerRecipe)) {
      return;
    }
    if (!window.confirm("Remover a receita shaped do controller?")) {
      return;
    }
    state.controllerRecipe = null;
    refresh();
    showToast("Receita do controller removida.");
  }

  function onControllerRecipePaletteClick(event) {
    var button = closest(event.target, "[data-controller-recipe-symbol]");
    if (!button) {
      return;
    }
    activeControllerRecipeSymbol = button.getAttribute("data-controller-recipe-symbol");
    renderControllerRecipe();
  }

  function onControllerRecipeGridClick(event) {
    var slot = closest(event.target, "[data-controller-recipe-row]");
    var recipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    if (!slot || !recipe) {
      return;
    }
    if (event.type === "contextmenu") {
      event.preventDefault();
    }
    var row = parseInt(slot.getAttribute("data-controller-recipe-row"), 10);
    var column = parseInt(slot.getAttribute("data-controller-recipe-column"), 10);
    if (!Number.isInteger(row) || !Number.isInteger(column)) {
      return;
    }
    var grid = controllerRecipeGrid(recipe);
    grid[row][column] = event.type === "contextmenu" || event.button === 2
      ? " "
      : activeControllerRecipeSymbol;
    recipe.pattern = controllerRecipePatternFromGrid(grid);
    syncControllerRecipeKeys(recipe);
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true
    });
  }

  function onControllerRecipeKeyInput(event) {
    var target = event.target;
    var symbol = target ? target.getAttribute("data-controller-recipe-key-symbol") : "";
    var recipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    if (!symbol || !recipe) {
      return;
    }
    if (!Array.isArray(recipe.key)) {
      recipe.key = [];
    }
    var row = recipe.key.find(function (entry) {
      return isPlainObject(entry) && trim(entry.symbol) === symbol;
    });
    if (!row) {
      row = { symbol: symbol, item: "" };
      recipe.key.push(row);
    }
    row.item = target.value;
    updateControllerRecipeCard();
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipControllerRecipe: true
    });
  }

  var GUI_ELEMENT_LABELS = {
    title: "Machine Name",
    text: "Inventory Label",
    player_inventory: "Inventário do jogador",
    slot: "Slot",
    energy_bar: "Barra de Energia",
    fluid_bar: "Barra de Fluido",
    chemical_bar: "Barra de Gás (Mekanism)",
    mana_bar: "Barra de Mana (Botania)",
    resource_list: "Lista de Essentias (Thaumaturge)",
    progress_bar: "Barra de Progresso",
    arrow: "Seta",
    entity: "Entidade(s)",
    booster_button: "Botão de Boosters"
  };

  function guiLabel(type) {
    return GUI_ELEMENT_LABELS[type] || type;
  }

  function guiElementUsesSize(type) {
    return ["energy_bar", "fluid_bar", "chemical_bar", "mana_bar", "resource_list", "progress_bar", "arrow", "entity", "booster_button"].indexOf(type) !== -1;
  }

  function guiElementUsesText(type) {
    return ["title", "text", "player_inventory", "booster_button"].indexOf(type) !== -1;
  }

  function guiElementUsesDirection(type) {
    return ["energy_bar", "fluid_bar", "chemical_bar", "mana_bar", "progress_bar", "arrow"].indexOf(type) !== -1;
  }

  function guiElementUsesTexture(type) {
    return ["progress_bar", "arrow"].indexOf(type) !== -1;
  }

  function guiSlotHasCoordinate(slot) {
    return isPlainObject(slot) &&
      (parseIntStrict(slot.x) !== null || parseIntStrict(slot.y) !== null);
  }

  function guiElementAvailability() {
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var fluids = capabilities && Array.isArray(capabilities.fluids)
      ? capabilities.fluids.filter(isPlainObject)
      : [];
    var chemicalTanks = capabilities && isPlainObject(capabilities.chemical)
      && Array.isArray(capabilities.chemical.tanks)
      ? capabilities.chemical.tanks.filter(isPlainObject)
      : [];
    var slots = Array.isArray(state.itemSlots) ? state.itemSlots : [];
    var hasSlotCoordinates = slots.some(guiSlotHasCoordinate);
    var hasRecipe = state.type === "recipe";
    var upgrades = upgradesState();
    var hasBoosters = Boolean(upgrades && Array.isArray(upgrades.boosters) &&
      upgrades.boosters.filter(boosterEnabled).length > 0);
    return {
      title: true,
      text: true,
      player_inventory: true,
      slot: slots.some(isPlainObject) && !hasSlotCoordinates,
      energy_bar: Boolean(capabilities && isPlainObject(capabilities.energy)),
      fluid_bar: fluids.length > 0,
      chemical_bar: chemicalTanks.length > 0,
      mana_bar: Boolean(capabilities && isPlainObject(capabilities.mana)),
      resource_list: Boolean(capabilities && isPlainObject(capabilities.essentia)),
      progress_bar: hasRecipe,
      arrow: hasRecipe,
      entity: hasRecipe,
      booster_button: hasBoosters
    };
  }

  function guiElementUnavailableReason(type) {
    if (type === "energy_bar") {
      return "Adicione energia na etapa 05 para liberar energy_bar.";
    }
    if (type === "fluid_bar") {
      return "Adicione um tanque na etapa 05 para liberar fluid_bar.";
    }
    if (type === "chemical_bar") {
      return "Adicione um tanque chemical na etapa 05 para liberar chemical_bar.";
    }
    if (type === "mana_bar") {
      return "Adicione um tanque de mana na etapa 05 para liberar mana_bar.";
    }
    if (type === "resource_list") {
      return "Adicione a capability essentia na etapa 05 para liberar resource_list.";
    }
    if (type === "progress_bar" || type === "arrow" || type === "entity") {
      return "Use type=recipe para liberar progress_bar, arrow e entity.";
    }
    if (type === "slot") {
      return "slot exige item_slots sem x/y (formato antigo).";
    }
    if (type === "booster_button") {
      return "Adicione ao menos um booster ativo na etapa 04 para liberar booster_button.";
    }
    return "Elemento indisponível para a configuração atual.";
  }

  function guiElementAvailabilityHint(availability) {
    var missing = [];
    if (!availability.energy_bar) {
      missing.push("energy_bar (energia)");
    }
    if (!availability.fluid_bar) {
      missing.push("fluid_bar (tanque)");
    }
    if (!availability.chemical_bar) {
      missing.push("chemical_bar (tanque chemical)");
    }
    if (!availability.mana_bar) {
      missing.push("mana_bar (tanque de mana)");
    }
    if (!availability.resource_list) {
      missing.push("resource_list (essentia)");
    }
    if (!availability.progress_bar) {
      missing.push("progress_bar/arrow/entity (recipe)");
    }
    if (!availability.slot) {
      missing.push("slot (item_slots sem x/y)");
    }
    if (!availability.booster_button) {
      missing.push("booster_button (upgrades)");
    }
    return missing.length > 0 ? "Indisponível: " + missing.join(" · ") + "." : "";
  }

  function updateGuiElementOptions() {
    var select = $("guiElementType");
    if (!select) {
      return;
    }
    var availability = guiElementAvailability();
    Array.prototype.forEach.call(select.options, function (option) {
      var allowed = availability[option.value] !== false;
      option.hidden = !allowed;
      option.disabled = !allowed;
      option.title = allowed
        ? (GUI_ELEMENT_HELP[option.value] || "")
        : guiElementUnavailableReason(option.value);
    });
    var selected = select.options[select.selectedIndex];
    if (selected && selected.disabled) {
      var first = Array.prototype.find.call(select.options, function (option) {
        return !option.disabled && !option.hidden;
      });
      if (first) {
        select.value = first.value;
      }
    }
    var hint = $("guiElementHint");
    if (hint) {
      var text = guiElementAvailabilityHint(availability);
      hint.hidden = text === "";
      hint.textContent = text;
    }
  }

  function guiValue(value) {
    return value === null || value === undefined ? "" : String(value);
  }

  function guiBaseValue(value, fallback) {
    return value === null || value === undefined || value === "" ? String(fallback) : String(value);
  }

  function guiPreviewColor(value) {
    if (typeof value === "number" && Number.isInteger(value)) {
      return "#" + ("000000" + ((value >>> 0) & 0xFFFFFF).toString(16)).slice(-6);
    }
    var text = trim(value);
    if (!text) {
      return "";
    }
    if (text.charAt(0) === "#") {
      text = text.slice(1);
    } else if (text.indexOf("0x") === 0 || text.indexOf("0X") === 0) {
      text = text.slice(2);
    } else if (/^-?\d+$/.test(text)) {
      var parsed = Number(text);
      if (!Number.isSafeInteger(parsed)) {
        return "";
      }
      return "#" + ("000000" + ((parsed >>> 0) & 0xFFFFFF).toString(16)).slice(-6);
    }
    if (text.length === 6) {
      return "#" + text;
    }
    if (text.length === 8) {
      return "#" + text.slice(2);
    }
    return "";
  }

  function guiSummaryText() {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    if (!gui) {
      return "Sem GUI customizada; o loader usa o layout fallback 176x166.";
    }
    var elements = Array.isArray(gui.elements) ? gui.elements : [];
    var texture = gui.customTexture ? "textura custom" : trim(gui.texture) ? "textura própria" : "sem textura";
    return guiBaseValue(gui.width, V.GUI_DEFAULT_WIDTH) + "x" +
      guiBaseValue(gui.height, V.GUI_DEFAULT_HEIGHT) + " - " +
      elements.length + (elements.length === 1 ? " elemento - " : " elementos - ") + texture;
  }

  function guiElementCardElement(index) {
    return document.querySelector(".gui-element-card[data-gui-element-index='" + index + "']");
  }

  function guiElementField(field) {
    return document.querySelector("[data-gui-field='" + field + "']");
  }

  var GUI_ELEMENT_HELP = {
    title: "Nome da máquina exibido no topo da GUI.",
    text: "Texto livre; por padrão é o rótulo do inventário em 8,71.",
    player_inventory: "Área dos slots do jogador; o rótulo padrão é o elemento text.",
    slot: "Slot de item ligado a um item_slots (formato antigo).",
    energy_bar: "Barra de energia (FE) da máquina.",
    fluid_bar: "Barra de um tanque de fluido existente.",
    chemical_bar: "Barra de um tanque chemical.",
    mana_bar: "Barra de mana.",
    resource_list: "Grade de recursos (essentia) em células 18x18.",
    progress_bar: "Barra de progresso da receita.",
    arrow: "Seta de progresso da receita, com fundo e overlay animado.",
    entity: "Renderiza entidades de input/output da receita.",
    booster_button: "Botão B que destaca os boosters no mundo."
  };

  function guiTypeOptions(selected) {
    var availability = guiElementAvailability();
    return V.GUI_ELEMENT_TYPES.map(function (type) {
      var allowed = type === selected || availability[type] !== false;
      return "<option value='" + esc(type) + "'" + (type === selected ? " selected" : "") +
        " title='" + esc(GUI_ELEMENT_HELP[type] || "") + "'" +
        (allowed ? "" : " hidden disabled") + ">" + esc(type) + "</option>";
    }).join("");
  }

  function guiDirectionOptions(selected) {
    return V.GUI_DIRECTIONS.map(function (direction) {
      return "<option value='" + esc(direction) + "'" + (direction === selected ? " selected" : "") + ">" +
        esc(direction) + "</option>";
    }).join("");
  }

  function guiInputField(field, label, value, type, step, placeholder) {
    return "<div class='field'><label>" + esc(label) + "</label><input type='" + (type || "text") +
      "' data-gui-element-field='" + esc(field) + "' value='" + esc(guiValue(value)) +
      "'" + (step === undefined ? "" : " step='" + esc(step) + "'") +
      (placeholder ? " placeholder='" + esc(placeholder) + "'" : "") +
      " spellcheck='false' autocomplete='off'></div>";
  }

  function guiElementPrimaryForm(element) {
    var type = element.type;
    var html = "<div class='controller-form gui-element-form'>";
    html += "<div class='field'><label>type</label><select data-gui-element-field='type'>" +
      guiTypeOptions(type) + "</select></div>";
    html += guiInputField("x", "x", element.x, "number", "1");
    html += guiInputField("y", "y", element.y, "number", "1");
    if (type === "slot") {
      html += guiInputField("slot", "slot", element.slot, "text", undefined, "item_slots.id");
    } else if (guiElementUsesText(type)) {
      html += guiInputField("text", "text", element.text, "text", undefined, "chave de tradução");
    } else if (type === "energy_bar" || type === "fluid_bar") {
      html += guiInputField("capability", "capability", element.capability, "text", undefined,
        type === "energy_bar" ? "energy.id" : "fluids[].id");
    } else if (type === "chemical_bar") {
      html += guiInputField("capability", "capability", element.capability, "text", undefined, "chemical");
      html += guiInputField("slot", "slot", element.slot, "text", undefined, "id do tanque chemical");
    } else if (type === "mana_bar") {
      html += guiInputField("capability", "capability", element.capability || "mana", "text", undefined, "mana");
      html += guiInputField("slot", "slot", element.slot || "mana", "text", undefined, "mana");
    } else if (type === "resource_list") {
      html += guiInputField("capability", "capability", element.capability || "essentia", "text", undefined, "essentia");
    }
    return html + "</div>";
  }

  function guiElementAdvancedForm(element) {
    var type = element.type;
    var html = "<div class='controller-form gui-element-form'>";
    var hasFields = false;
    if (guiElementUsesSize(type)) {
      html += guiInputField("width", "width", element.width, "number", "1");
      html += guiInputField("height", "height", element.height, "number", "1");
      hasFields = true;
    }
    if (type === "title" || type === "text" || type === "player_inventory" ||
        type === "energy_bar" || type === "progress_bar" || type === "booster_button") {
      html += guiInputField("color", "color", element.color, "text", undefined, "vazio = default");
      hasFields = true;
    }
    if (type === "title" || type === "text" || type === "player_inventory") {
      html += "<div class='field gui-check-field'><label><input type='checkbox' data-gui-element-field='shadow'" +
        (element.shadow ? " checked" : "") + "> shadow</label></div>";
      hasFields = true;
    }
    if (guiElementUsesDirection(type)) {
      html += "<div class='field'><label>direction</label><select data-gui-element-field='direction'>" +
        guiDirectionOptions(element.direction) + "</select></div>";
      hasFields = true;
    }
    if (guiElementUsesTexture(type)) {
      html += guiInputField("u", "u", element.u, "number", "1");
      html += guiInputField("v", "v", element.v, "number", "1");
      hasFields = true;
    }
    return hasFields ? html + "</div>" : "";
  }

  function guiElementMarkup(element, index) {
    var type = trim(element.type) || "text";
    var meta = [];
    if (trim(element.slot)) {
      meta.push("slot " + trim(element.slot));
    }
    if (trim(element.capability)) {
      meta.push(trim(element.capability));
    }
    if (trim(element.text)) {
      meta.push(trim(element.text));
    }
    var moveControls = type === "slot"
      ? "<div class='slot-move-controls' role='group' aria-label='Mover elemento'>" +
        moveButtonHtml("move-gui-element", "↑", 0, -18, false, "Mover elemento para cima") +
        moveButtonHtml("move-gui-element", "←", -18, 0, false, "Mover elemento para a esquerda") +
        moveButtonHtml("move-gui-element", "→", 18, 0, false, "Mover elemento para a direita") +
        moveButtonHtml("move-gui-element", "↓", 0, 18, false, "Mover elemento para baixo") +
        "</div>"
      : "";
    var advanced = guiElementAdvancedForm(element);
    return "<details class='gui-element-card' data-gui-element-index='" + index + "'>" +
      "<summary class='gui-element-summary'>" +
      "<span class='gui-element-title'>" + (index + 1) + ". " + esc(guiLabel(type)) + "</span>" +
      "<span class='gui-element-type'>" + esc(type) + "</span>" +
      "<span class='gui-element-meta'>" + esc(meta.join(" - ")) + "</span>" +
      moveControls +
      "<button type='button' class='btn btn-tiny btn-danger' data-action='remove-gui-element'>remover</button>" +
      "</summary>" +
      "<div class='gui-element-body'>" + guiElementPrimaryForm(element) +
      (advanced ? "<details class='card-advanced'><summary>Propriedades avançadas" +
        "<span class='card-advanced-hint'>tamanho, cor e textura</span></summary>" +
        "<div class='card-advanced-body'>" + advanced + "</div></details>" : "") +
      "</div></details>";
  }

  var GUI_PREVIEW_ASSET_BASE = "assets/gui/";
  var GUI_PREVIEW_DEFAULT_TEXTURE = GUI_PREVIEW_ASSET_BASE + "gui.png";
  var GUI_PREVIEW_SLOT_TEXTURE = GUI_PREVIEW_ASSET_BASE + "slot.png";
  var GUI_PREVIEW_ARROW_BASE = GUI_PREVIEW_ASSET_BASE + "seta_fundo.png";
  var GUI_PREVIEW_ARROW_OVERLAY = GUI_PREVIEW_ASSET_BASE + "seta_overlay.png";

  function guiPreviewTexture(gui) {
    return isPlainObject(gui) && trim(gui.previewTexture)
      ? trim(gui.previewTexture)
      : GUI_PREVIEW_DEFAULT_TEXTURE;
  }

  function applyGuiPreviewBackground(node, gui) {
    if (!node) {
      return;
    }
    var texture = guiPreviewTexture(gui);
    node.style.backgroundImage = "url('" + texture + "')";
    node.style.backgroundSize = "100% 100%";
    node.style.backgroundPosition = "top left";
    node.style.backgroundRepeat = "no-repeat";
    node.classList.add("has-gui-texture");
  }

  function guiCanvasSize(gui) {
    var width = parseIntStrict(gui.width);
    var height = parseIntStrict(gui.height);
    return {
      width: width !== null && width > 0 ? width : V.GUI_DEFAULT_WIDTH,
      height: height !== null && height > 0 ? height : V.GUI_DEFAULT_HEIGHT
    };
  }

  function guiCanvasDisplaySize(type, width, height) {
    var safeWidth = width !== null && width > 0 ? width : null;
    var safeHeight = height !== null && height > 0 ? height : null;
    if (type === "player_inventory") {
      return { width: 160, height: 74 };
    }
    if (type === "title" || type === "text") {
      return { width: safeWidth || 80, height: safeHeight || 10 };
    }
    if (type === "slot") {
      return { width: 18, height: 18 };
    }
    if (type === "energy_bar" || type === "fluid_bar" || type === "chemical_bar" || type === "mana_bar") {
      return { width: safeWidth || 12, height: safeHeight || 52 };
    }
    if (type === "resource_list") {
      return { width: safeWidth || 36, height: safeHeight || 54 };
    }
    if (type === "progress_bar") {
      return { width: safeWidth || 57, height: safeHeight || 3 };
    }
    if (type === "arrow") {
      return { width: safeWidth || 22, height: safeHeight || 16 };
    }
    if (type === "entity") {
      return { width: safeWidth || 36, height: safeHeight || 36 };
    }
    if (type === "booster_button") {
      return { width: safeWidth || 8, height: safeHeight || 8 };
    }
    return { width: safeWidth || 16, height: safeHeight || 16 };
  }

  function guiCanvasDragSize(type, width, height) {
    if (type === "player_inventory") {
      return { width: 162, height: 76 };
    }
    return guiCanvasDisplaySize(type, width, height);
  }

  function guiCanvasLabel(element, type) {
    if (type === "title" || type === "text") {
      return trim(element.text) || guiLabel(type);
    }
    if (type === "player_inventory") {
      return trim(element.text) || "inventário";
    }
    if (type === "slot") {
      return trim(element.slot) || "slot";
    }
    if (type === "energy_bar") {
      return "FE";
    }
    if (type === "fluid_bar") {
      return trim(element.capability) || "fluido";
    }
    if (type === "chemical_bar") {
      return trim(element.slot) || "chemical";
    }
    if (type === "mana_bar") {
      return trim(element.slot) || "mana";
    }
    if (type === "resource_list") {
      return trim(element.capability) || "essentia";
    }
    if (type === "progress_bar") {
      return "progresso";
    }
    if (type === "arrow") {
      return "→";
    }
    if (type === "entity") {
      return "entidade";
    }
    if (type === "booster_button") {
      return trim(element.text) || "B";
    }
    return guiLabel(type);
  }

  function isInventoryLabelText(element) {
    if (!isPlainObject(element) || trim(element.type) !== "text" || !trim(element.text)) {
      return false;
    }
    var x = parseIntStrict(element.x);
    var y = parseIntStrict(element.y);
    return x !== null && y !== null && Math.abs(x - 8) <= 2 && Math.abs(y - 71) <= 4;
  }

  function guiCanvasElementMarkup(element, index, hasInventoryLabelText) {
    var type = trim(element.type) || "text";
    var x = parseIntStrict(element.x);
    var y = parseIntStrict(element.y);
    var width = parseIntStrict(element.width);
    var height = parseIntStrict(element.height);
    var size = guiCanvasDisplaySize(type, width, height);
    var offsetX = type === "slot" || type === "title" || type === "text" ? -1 : 0;
    var offsetY = 0;
    var style = "left:" + (x === null ? 0 : x + offsetX) + "px;top:" + (y === null ? 0 : y + offsetY) +
      "px;width:" + size.width + "px;height:" + size.height + "px;";
    var isBar = ["energy_bar", "fluid_bar", "chemical_bar", "mana_bar", "progress_bar"].indexOf(type) !== -1;
    var textColor = guiPreviewColor(element.color);
    if (textColor && (type === "title" || type === "text" || type === "player_inventory")) {
      style += "--element-text:" + textColor + ";";
    }
    var fill = type === "fluid_bar" ? 42 : type === "energy_bar" ? 64 : 55;
    if (isBar) {
      style += "--fill:" + fill + "%;";
      var barColor = guiPreviewColor(element.color);
      if (!barColor && type === "energy_bar") {
        barColor = "#ff0000";
      } else if (!barColor && type === "mana_bar") {
        barColor = "#00aaff";
      } else if (!barColor && type === "progress_bar") {
        barColor = "#00aa00";
      }
      if (barColor) {
        style += "--element-color:" + barColor + ";";
      }
    }
    var classes = "gui-canvas-element";
    if (isBar) {
      classes += " gui-canvas-bar";
    } else if (type === "booster_button") {
      classes += " gui-canvas-button";
    }
    var title = guiLabel(type) + " • x=" + (x === null ? 0 : x) + " y=" + (y === null ? 0 : y);
    if (trim(element.capability)) {
      title += " • " + trim(element.capability);
    }
    var showLabel = !(type === "player_inventory" && hasInventoryLabelText) &&
      (!isBar || (size.width >= 18 && size.height >= 14));
    return "<div class='" + classes + "' data-gui-preview-index='" + index +
      "' data-gui-preview-type='" + esc(type) + "' data-direction='" + esc(trim(element.direction) || "up") +
      "' data-shadow='" + (element.shadow === false ? "false" : "true") +
      "' style='" + style + "' title='" + esc(title) + "'>" +
      (showLabel ? "<span>" + esc(guiCanvasLabel(element, type)) + "</span>" : "") + "</div>";
  }

  function renderGuiCanvas(gui) {
    var canvas = $("guiCanvas");
    var wrap = $("guiCanvasWrap");
    if (!canvas || !wrap) {
      return;
    }
    if (!gui) {
      wrap.hidden = true;
      canvas.innerHTML = "";
      return;
    }
    var dimensions = guiCanvasSize(gui);
    var elements = Array.isArray(gui.elements) ? gui.elements : [];
    var parentWidth = wrap.parentElement ? wrap.parentElement.clientWidth : 0;
    var available = parentWidth > 0 ? Math.max(240, parentWidth - 2) : 700;
    var scale = Math.min(2, available / dimensions.width, 420 / dimensions.height);
    scale = Math.max(0.2, scale);
    wrap.hidden = false;
    wrap.style.width = Math.ceil(dimensions.width * scale) + "px";
    wrap.style.height = Math.ceil(dimensions.height * scale) + "px";
    canvas.style.width = dimensions.width + "px";
    canvas.style.height = dimensions.height + "px";
    canvas.style.transform = "scale(" + scale + ")";
    applyGuiPreviewBackground(canvas, gui);
    canvas.setAttribute("data-scale", String(scale));
    canvas.setAttribute("data-gui-width", String(dimensions.width));
    canvas.setAttribute("data-gui-height", String(dimensions.height));
    var hasInventoryLabelText = elements.some(isInventoryLabelText);
    var parts = elements.map(function (element, index) {
      return guiCanvasElementMarkup(element, index, hasInventoryLabelText);
    });
    var hasSlotElements = elements.some(function (element) {
      return trim(element.type) === "slot";
    });
    var slots = Array.isArray(state.itemSlots) ? state.itemSlots : [];
    if (!hasSlotElements && slots.length > 0) {
      var hidden = [];
      var slotsMarkup = slotGuiPreviewMarkup(slots, dimensions.width, dimensions.height, hidden);
      if (slotsMarkup.html) {
        parts.push(slotsMarkup.html);
      }
    }
    if (parts.length === 0) {
      canvas.innerHTML = "<div class='gui-canvas-empty'>Sem elementos ou slots posicionados. Adicione elementos abaixo ou defina x/y nos slots.</div>";
      return;
    }
    canvas.innerHTML = parts.join("");
  }

  function setGuiElementCoordinate(index, field, value) {
    var card = guiElementCardElement(index);
    var input = card ? card.querySelector("[data-gui-element-field='" + field + "']") : null;
    if (input) {
      input.value = String(value);
    }
  }

  function updateGuiCard() {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var summary = $("guiSummary");
    var feedback = $("guiFeedback");
    var jsonElement = $("guiJson");
    if (!summary) {
      return;
    }
    summary.textContent = guiSummaryText();
    if (!gui) {
      renderGuiCanvas(null);
      if (feedback) {
        feedback.hidden = true;
        feedback.textContent = "";
      }
      if (jsonElement) {
        jsonElement.textContent = "";
      }
      return;
    }
    renderGuiCanvas(gui);
    updateGuiElementOptions();
    if (jsonElement) {
      jsonElement.textContent = JSON.stringify(V.buildGui(gui), null, 2);
    }
    var problem = V.guiProblem(gui, state);
    if (feedback) {
      feedback.hidden = !problem;
      feedback.textContent = problem || "";
      feedback.setAttribute("data-tone", problem ? "error" : "ok");
    }
    $$(".gui-element-card").forEach(function (card) {
      var index = parseInt(card.getAttribute("data-gui-element-index"), 10);
      var hasError = report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === "guiElement:" + index;
      });
      card.classList.toggle("is-invalid", hasError);
    });
    ["guiTexture", "guiWidth", "guiHeight", "guiTextureWidth", "guiTextureHeight"].forEach(function (id) {
      var input = $(id);
      if (!input) {
        return;
      }
      input.classList.toggle("is-invalid", report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === id;
      }));
    });
  }

  function renderGui() {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var empty = $("guiEmpty");
    var form = $("guiForm");
    var addButton = $("addGuiBtn");
    var removeButton = $("removeGuiBtn");
    if (!gui) {
      if (empty) {
        empty.textContent = "Nenhuma GUI customizada. O loader usa o layout fallback 176x166 com inventário do jogador.";
        empty.hidden = false;
      }
      if (form) {
        form.hidden = true;
      }
      if (addButton) {
        addButton.disabled = false;
      }
      if (removeButton) {
        removeButton.disabled = true;
      }
      updateGuiCard();
      return;
    }
    if (empty) {
      empty.hidden = true;
    }
    if (form) {
      form.hidden = false;
    }
    if (addButton) {
      addButton.disabled = true;
    }
    if (removeButton) {
      removeButton.disabled = false;
    }
    var textureInput = guiElementField("texture");
    var widthInput = guiElementField("width");
    var heightInput = guiElementField("height");
    var textureWidthInput = guiElementField("textureWidth");
    var textureHeightInput = guiElementField("textureHeight");
    if (textureInput) {
      textureInput.value = trim(gui.texture);
    }
    if (widthInput) {
      widthInput.value = guiBaseValue(gui.width, V.GUI_DEFAULT_WIDTH);
    }
    if (heightInput) {
      heightInput.value = guiBaseValue(gui.height, V.GUI_DEFAULT_HEIGHT);
    }
    if (textureWidthInput) {
      textureWidthInput.value = guiBaseValue(gui.textureWidth, V.GUI_DEFAULT_TEXTURE_SIZE);
    }
    if (textureHeightInput) {
      textureHeightInput.value = guiBaseValue(gui.textureHeight, V.GUI_DEFAULT_TEXTURE_SIZE);
    }
    var customTexture = Boolean(gui.customTexture);
    [widthInput, heightInput, textureWidthInput, textureHeightInput].forEach(function (input) {
      if (input) {
        input.disabled = customTexture;
      }
    });
    var resetButton = $("guiTextureResetBtn");
    if (resetButton) {
      resetButton.hidden = !customTexture;
    }
    var textureHint = $("guiTextureHint");
    if (textureHint) {
      textureHint.textContent = customTexture
        ? "Textura custom " + guiBaseValue(gui.textureWidth, V.GUI_DEFAULT_TEXTURE_SIZE) + "x" +
          guiBaseValue(gui.textureHeight, V.GUI_DEFAULT_TEXTURE_SIZE) +
          ": width/height e texture_width/texture_height bloqueados. Ajuste o campo texture para o caminho no resource pack."
        : "Sem textura custom: a prévia usa gui.png 176x166 como fundo.";
    }
    [["guiTexture", textureInput], ["guiWidth", widthInput], ["guiHeight", heightInput],
      ["guiTextureWidth", textureWidthInput], ["guiTextureHeight", textureHeightInput]].forEach(function (pair) {
      if (!pair[1]) {
        return;
      }
      pair[1].classList.toggle("is-invalid", report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === pair[0];
      }));
    });
    var list = $("guiElementList");
    if (list) {
      var elements = Array.isArray(gui.elements) ? gui.elements : [];
      list.innerHTML = elements.length > 0
        ? elements.map(guiElementMarkup).join("")
        : "<div class='missing-empty'>Nenhum elemento ainda. Adicione title, player_inventory, barras, entidade ou slots.</div>";
      $$(".gui-element-card", list).forEach(function (card) {
        var index = parseInt(card.getAttribute("data-gui-element-index"), 10);
        var hasError = report.issues.some(function (entry) {
          return entry.severity === "error" && entry.field === "guiElement:" + index;
        });
        card.classList.toggle("is-invalid", hasError);
      });
    }
    updateGuiCard();
  }

  function soundsSummaryText() {
    var sounds = isPlainObject(state.sounds) ? state.sounds : null;
    var parts = [];
    if (sounds && trim(sounds.processing)) {
      parts.push("processing");
    }
    if (sounds && trim(sounds.finish)) {
      parts.push("finish");
    }
    if (parts.length === 0) {
      return "Sem sons; a máquina permanece silenciosa.";
    }
    if (parts.length === 2) {
      return "Sons definidos: processing e finish.";
    }
    return "Som definido: " + parts[0] + ".";
  }

  function minecraftSoundCatalog() {
    if (minecraftSoundData) {
      return minecraftSoundData;
    }
    var data = window.MINECRAFT_SOUNDS;
    if (isPlainObject(data) && Array.isArray(data.sounds)) {
      minecraftSoundData = data;
    } else if (Array.isArray(data)) {
      minecraftSoundData = { version: "", sounds: data };
    }
    return minecraftSoundData;
  }

  function renderMinecraftSoundOptions() {
    var list = $("minecraftSoundList");
    var status = $("minecraftSoundStatus");
    if (!list || !status) {
      return;
    }
    var catalog = minecraftSoundCatalog();
    if (!catalog) {
      status.textContent = "Lista de sons vanilla indisponível; digite o identifier manualmente.";
      return;
    }
    if (!minecraftSoundOptionsRendered) {
      var fragment = document.createDocumentFragment();
      catalog.sounds.forEach(function (soundId) {
        if (typeof soundId !== "string") {
          return;
        }
        var option = document.createElement("option");
        option.value = soundId;
        fragment.appendChild(option);
      });
      list.appendChild(fragment);
      minecraftSoundOptionsRendered = true;
    }
    status.textContent = catalog.sounds.length + " sons vanilla" +
      (catalog.version ? " (" + catalog.version + ")" : "") +
      ". Escolha na lista ou digite um ID próprio.";
  }

  function updateSoundsCard() {
    var summary = $("soundsSummary");
    if (summary) {
      summary.textContent = soundsSummaryText();
    }
    [["soundProcessing", "processing"], ["soundFinish", "finish"]].forEach(function (pair) {
      var input = $(pair[0]);
      if (!input) {
        return;
      }
      var hasError = report.issues.some(function (entry) {
        return entry.severity === "error" && entry.field === pair[0];
      });
      input.classList.toggle("is-invalid", hasError);
    });
  }

  function renderSounds() {
    var sounds = isPlainObject(state.sounds) ? state.sounds : V.createSounds();
    state.sounds = sounds;
    if (!isPlainObject(sounds.extras)) {
      sounds.extras = {};
    }
    var processingInput = $("soundProcessing");
    var finishInput = $("soundFinish");
    if (processingInput) {
      processingInput.value = trim(sounds.processing);
    }
    if (finishInput) {
      finishInput.value = trim(sounds.finish);
    }
    syncSoundUploadsWithState();
    renderMinecraftSoundOptions();
    updateSoundsCard();
    renderSoundUploadControls();
  }

  function refreshSoundsOnly() {
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true,
      skipSounds: true,
      skipRecipes: true,
      skipInheritance: true
    });
    updateSoundsCard();
  }

  function onSoundInput(event) {
    var target = event.target;
    var field = target ? target.getAttribute("data-sound-field") : "";
    if (!field) {
      return;
    }
    if (!isPlainObject(state.sounds)) {
      state.sounds = V.createSounds();
    }
    state.sounds[field] = target.value;
    refreshSoundsOnly();
    var upload = soundUploadState[field];
    if (upload && trim(target.value) !== upload.id) {
      clearSoundUpload(field, true);
    }
    renderSoundUploadControls();
  }

  function soundInputId(field) {
    return field === "processing" ? "soundProcessing" : "soundFinish";
  }

  function soundPathFromFile(file) {
    return V.soundPathFromName(file && file.name);
  }

  function soundIdFromFile(file) {
    return currentNamespace() + ":" + soundPathFromFile(file);
  }

  function soundAssetPath(upload) {
    if (!upload) {
      return "";
    }
    var namespace = upload.namespace || currentNamespace();
    return "assets/" + namespace + "/sounds/" + upload.path + ".ogg";
  }

  function soundJsonEntry(upload) {
    if (!upload) {
      return "";
    }
    var entry = {};
    entry[upload.path] = { sounds: [upload.path] };
    return JSON.stringify(entry, null, 2);
  }

  function stopSoundAudio(field) {
    var entry = soundAudioState[field];
    if (!entry) {
      return;
    }
    if (entry.audio) {
      try {
        entry.audio.pause();
        entry.audio.currentTime = 0;
      } catch (error) {
        // ignore
      }
    }
    soundAudioState[field] = null;
  }

  function stopOtherSoundAudio(field) {
    SOUND_FIELDS.forEach(function (other) {
      if (other !== field) {
        stopSoundAudio(other);
      }
    });
  }

  function clearSoundUpload(field, silent) {
    var upload = soundUploadState[field];
    if (!upload) {
      return;
    }
    stopSoundAudio(field);
    if (upload.url) {
      URL.revokeObjectURL(upload.url);
    }
    soundUploadState[field] = null;
    if (!silent) {
      renderSoundUploadControls();
    }
  }

  function syncSoundUploadsWithState() {
    SOUND_FIELDS.forEach(function (field) {
      var upload = soundUploadState[field];
      if (!upload) {
        return;
      }
      var current = isPlainObject(state.sounds) ? trim(state.sounds[field]) : "";
      if (current !== upload.id) {
        clearSoundUpload(field, true);
      }
    });
  }

  function renderSoundUploadControls() {
    SOUND_FIELDS.forEach(function (field) {
      var upload = soundUploadState[field];
      var row = document.querySelector("[data-sound-upload-row='" + field + "']");
      var label = document.querySelector("[data-sound-file-label='" + field + "']");
      var playButton = document.querySelector("[data-sound-play='" + field + "']");
      var copyButton = document.querySelector("[data-sound-copy='" + field + "']");
      var clearButton = document.querySelector("[data-sound-clear='" + field + "']");
      if (row) {
        row.hidden = !upload;
      }
      if (label) {
        label.textContent = upload
          ? upload.name + " · " + soundAssetPath(upload)
          : "";
      }
      var audio = soundAudioState[field] && soundAudioState[field].audio;
      var playing = Boolean(audio && !audio.paused);
      if (playButton) {
        playButton.disabled = !upload;
        playButton.textContent = playing ? "parar" : "play";
        playButton.setAttribute("aria-pressed", playing ? "true" : "false");
      }
      if (copyButton) {
        copyButton.disabled = !upload;
      }
      if (clearButton) {
        clearButton.disabled = !upload;
      }
    });
  }

  function onSoundUploadClick(event) {
    var button = closest(event.target, "[data-sound-upload]");
    if (!button) {
      return;
    }
    soundUploadTarget = button.getAttribute("data-sound-upload");
    var input = $("soundUploadInput");
    if (!input) {
      return;
    }
    input.value = "";
    input.click();
  }

  function onSoundUploadChange(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    var field = soundUploadTarget;
    soundUploadTarget = "";
    input.value = "";
    if (!file || !field) {
      return;
    }
    if (!/\.ogg$/i.test(file.name) && !/audio\/ogg/i.test(file.type)) {
      showToast("Selecione um arquivo .ogg.", "error");
      return;
    }
    var url;
    try {
      url = URL.createObjectURL(file);
    } catch (error) {
      showToast("Não foi possível preparar o áudio.", "error");
      return;
    }
    clearSoundUpload(field, true);
    var upload = {
      name: file.name,
      id: soundIdFromFile(file),
      path: soundPathFromFile(file),
      namespace: currentNamespace(),
      url: url
    };
    soundUploadState[field] = upload;
    if (!isPlainObject(state.sounds)) {
      state.sounds = V.createSounds();
    }
    state.sounds[field] = upload.id;
    var textInput = $(soundInputId(field));
    if (textInput) {
      textInput.value = upload.id;
    }
    refreshSoundsOnly();
    renderSoundUploadControls();
    showToast("Som carregado: " + upload.id + ". Arquivo esperado: " + soundAssetPath(upload));
  }

  function onSoundPlayClick(event) {
    var button = closest(event.target, "[data-sound-play]");
    if (!button) {
      return;
    }
    var field = button.getAttribute("data-sound-play");
    var upload = soundUploadState[field];
    if (!upload) {
      return;
    }
    var current = soundAudioState[field];
    if (current && current.audio && !current.audio.paused) {
      stopSoundAudio(field);
      renderSoundUploadControls();
      return;
    }
    stopOtherSoundAudio(field);
    stopSoundAudio(field);
    var audio;
    try {
      audio = new Audio(upload.url);
    } catch (error) {
      showToast("Este navegador não conseguiu abrir o .ogg.", "error");
      return;
    }
    soundAudioState[field] = { audio: audio };
    audio.addEventListener("ended", function () {
      if (soundAudioState[field] && soundAudioState[field].audio === audio) {
        soundAudioState[field] = null;
        renderSoundUploadControls();
      }
    });
    audio.addEventListener("error", function () {
      if (soundAudioState[field] && soundAudioState[field].audio === audio) {
        soundAudioState[field] = null;
        renderSoundUploadControls();
      }
      showToast("Não foi possível tocar o .ogg.", "error");
    });
    var playResult = audio.play();
    if (playResult && typeof playResult.catch === "function") {
      playResult.catch(function () {
        if (soundAudioState[field] && soundAudioState[field].audio === audio) {
          soundAudioState[field] = null;
          renderSoundUploadControls();
        }
        showToast("O navegador bloqueou o play; clique em play novamente.", "error");
      });
    }
    renderSoundUploadControls();
  }

  function onSoundCopyClick(event) {
    var button = closest(event.target, "[data-sound-copy]");
    if (!button) {
      return;
    }
    var upload = soundUploadState[button.getAttribute("data-sound-copy")];
    if (!upload) {
      return;
    }
    copyText(soundJsonEntry(upload)).then(function () {
      showToast("Entrada sounds.json copiada.");
    }, function () {
      showToast("Não foi possível copiar.", "error");
    });
  }

  function onSoundClearClick(event) {
    var button = closest(event.target, "[data-sound-clear]");
    if (!button) {
      return;
    }
    clearSoundUpload(button.getAttribute("data-sound-clear"), false);
  }

  function onSoundsPanelClick(event) {
    if (closest(event.target, "[data-sound-upload]")) {
      onSoundUploadClick(event);
    } else if (closest(event.target, "[data-sound-play]")) {
      onSoundPlayClick(event);
    } else if (closest(event.target, "[data-sound-copy]")) {
      onSoundCopyClick(event);
    } else if (closest(event.target, "[data-sound-clear]")) {
      onSoundClearClick(event);
    }
  }

  function ensureGui() {
    if (!isPlainObject(state.gui)) {
      state.gui = V.createGui();
      state.gui.elements.push(V.createGuiElement("title"));
      var inventoryLabel = V.createGuiElement("text");
      inventoryLabel.text = "Inventário";
      state.gui.elements.push(inventoryLabel);
      state.gui.elements.push(V.createGuiElement("player_inventory"));
    }
    return state.gui;
  }

  function addGui() {
    ensureGui();
    refresh();
    focusElement($("guiPanel"), false);
  }

  function removeGui() {
    if (!isPlainObject(state.gui)) {
      return;
    }
    if (!window.confirm("Remover a GUI customizada? O loader voltará ao layout fallback 176x166.")) {
      return;
    }
    state.gui = null;
    refresh();
    showToast("GUI customizada removida.");
  }

  function addGuiElement() {
    var select = $("guiElementType");
    var type = select ? select.value : "title";
    var availability = guiElementAvailability();
    if (availability[type] === false) {
      showToast(guiElementUnavailableReason(type), "error");
      updateGuiElementOptions();
      return;
    }
    var gui = ensureGui();
    var element = V.createGuiElement(type);
    var capabilities = isPlainObject(state.capabilities) ? state.capabilities : null;
    var firstFluid = capabilities && Array.isArray(capabilities.fluids)
      ? capabilities.fluids.find(isPlainObject)
      : null;
    var firstSlot = Array.isArray(state.itemSlots) ? state.itemSlots.find(isPlainObject) : null;
    if (type === "energy_bar" && capabilities && isPlainObject(capabilities.energy)) {
      element.capability = trim(capabilities.energy.id);
    } else if (type === "fluid_bar" && firstFluid) {
      element.capability = trim(firstFluid.id);
    } else if (type === "slot" && firstSlot) {
      element.slot = trim(firstSlot.id);
    }
    gui.elements.unshift(element);
    refresh();
    var card = guiElementCardElement(0);
    var input = card ? card.querySelector("input, select") : null;
    focusElement(input || card, false);
  }

  function removeGuiElement(index) {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    if (!gui || !Array.isArray(gui.elements) || index < 0 || index >= gui.elements.length) {
      return;
    }
    var type = trim(gui.elements[index].type);
    if (!window.confirm("Remover o elemento " + (index + 1) + " (" + guiLabel(type) + ")?")) {
      return;
    }
    gui.elements.splice(index, 1);
    refresh();
  }

  function moveGuiElement(index, dx, dy) {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var element = gui && Array.isArray(gui.elements) ? gui.elements[index] : null;
    if (!isPlainObject(element)) {
      return;
    }
    var dimensions = guiCanvasSize(gui);
    var size = guiCanvasDisplaySize(trim(element.type), parseIntStrict(element.width),
      parseIntStrict(element.height));
    var x = parseIntStrict(element.x);
    var y = parseIntStrict(element.y);
    element.x = clamp((x === null ? 0 : x) + dx, 0, Math.max(0, dimensions.width - size.width));
    element.y = clamp((y === null ? 0 : y) + dy, 0, Math.max(0, dimensions.height - size.height));
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true
    });
    setGuiElementCoordinate(index, "x", element.x);
    setGuiElementCoordinate(index, "y", element.y);
    updateGuiCard();
  }

  function onGuiFormInput(event) {
    var target = event.target;
    var field = target ? target.getAttribute("data-gui-field") : "";
    var gui = isPlainObject(state.gui) ? state.gui : null;
    if (!field || !gui || ["texture", "width", "height", "textureWidth", "textureHeight"].indexOf(field) === -1) {
      return;
    }
    gui[field] = target.value;
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true
    });
    updateGuiCard();
  }

  function onGuiTextureUpload(event) {
    var input = event.target;
    var file = input && input.files ? input.files[0] : null;
    if (!file) {
      return;
    }
    if (!/^image\/png$/i.test(file.type) && !/\.png$/i.test(file.name)) {
      showToast("Escolha um arquivo PNG.", "error");
      input.value = "";
      return;
    }
    var gui = ensureGui();
    var reader = new FileReader();
    reader.onload = function () {
      var dataUrl = String(reader.result || "");
      var image = new Image();
      image.onload = function () {
        gui.previewTexture = dataUrl;
        gui.customTexture = true;
        gui.width = image.naturalWidth;
        gui.height = image.naturalHeight;
        gui.textureWidth = image.naturalWidth;
        gui.textureHeight = image.naturalHeight;
        refresh();
        updateGuiCard();
        showToast("Textura importada: " + image.naturalWidth + "x" + image.naturalHeight + ".");
      };
      image.onerror = function () {
        showToast("Não foi possível ler o PNG.", "error");
      };
      image.src = dataUrl;
    };
    reader.onerror = function () {
      showToast("Não foi possível abrir o arquivo.", "error");
    };
    reader.readAsDataURL(file);
    input.value = "";
  }

  function resetGuiTexture() {
    var gui = isPlainObject(state.gui) ? state.gui : null;
    if (!gui) {
      return;
    }
    delete gui.previewTexture;
    delete gui.customTexture;
    gui.texture = "";
    gui.width = V.GUI_DEFAULT_WIDTH;
    gui.height = V.GUI_DEFAULT_HEIGHT;
    gui.textureWidth = V.GUI_DEFAULT_TEXTURE_SIZE;
    gui.textureHeight = V.GUI_DEFAULT_TEXTURE_SIZE;
    refresh();
    updateGuiCard();
    showToast("Textura custom removida; tamanho padrão restaurado.");
  }

  function onGuiElementInput(event) {
    var target = event.target;
    if (!target || target.tagName === "SELECT" || target.type === "checkbox") {
      return;
    }
    var card = closest(target, ".gui-element-card[data-gui-element-index]");
    var index = card ? parseInt(card.getAttribute("data-gui-element-index"), 10) : -1;
    var field = target.getAttribute("data-gui-element-field");
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var element = gui && Array.isArray(gui.elements) ? gui.elements[index] : null;
    if (!field || !isPlainObject(element)) {
      return;
    }
    element[field] = target.value;
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true
    });
    updateGuiCard();
  }

  function onGuiElementChange(event) {
    var target = event.target;
    if (!target || (target.tagName !== "SELECT" && target.type !== "checkbox")) {
      return;
    }
    var card = closest(target, ".gui-element-card[data-gui-element-index]");
    var index = card ? parseInt(card.getAttribute("data-gui-element-index"), 10) : -1;
    var field = target.getAttribute("data-gui-element-field");
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var element = gui && Array.isArray(gui.elements) ? gui.elements[index] : null;
    if (!field || !isPlainObject(element)) {
      return;
    }
    element[field] = field === "shadow" ? target.checked : target.value;
    if (field === "type") {
      refresh();
      return;
    }
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true
    });
    updateGuiCard();
  }

  function onGuiElementClick(event) {
    var move = closest(event.target, "[data-action='move-gui-element']");
    if (move) {
      event.preventDefault();
      event.stopPropagation();
      var moveCard = closest(move, ".gui-element-card[data-gui-element-index]");
      var moveIndex = moveCard ? parseInt(moveCard.getAttribute("data-gui-element-index"), 10) : -1;
      if (moveIndex >= 0) {
        moveGuiElement(moveIndex, parseInt(move.getAttribute("data-dx"), 10) || 0,
          parseInt(move.getAttribute("data-dy"), 10) || 0);
      }
      return;
    }
    var action = closest(event.target, "[data-action='remove-gui-element']");
    if (!action) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    var card = closest(action, ".gui-element-card[data-gui-element-index]");
    var index = card ? parseInt(card.getAttribute("data-gui-element-index"), 10) : -1;
    removeGuiElement(index);
  }

  var guiCanvasDrag = null;

  function beginGuiCanvasDrag(event) {
    var preview = closest(event.target, "[data-gui-preview-index]");
    var canvas = $("guiCanvas");
    var gui = isPlainObject(state.gui) ? state.gui : null;
    if (!preview || !canvas || !gui || event.button !== 0) {
      return;
    }
    var index = parseInt(preview.getAttribute("data-gui-preview-index"), 10);
    var element = Array.isArray(gui.elements) ? gui.elements[index] : null;
    if (!isPlainObject(element)) {
      return;
    }
    var dimensions = guiCanvasSize(gui);
    var size = guiCanvasDragSize(trim(element.type), parseIntStrict(element.width), parseIntStrict(element.height));
    var x = parseIntStrict(element.x);
    var y = parseIntStrict(element.y);
    var scale = parseFloat(canvas.getAttribute("data-scale"));
    guiCanvasDrag = {
      pointerId: event.pointerId,
      index: index,
      scale: scale > 0 ? scale : 1,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startX: x === null ? 0 : x,
      startY: y === null ? 0 : y,
      width: size.width,
      height: size.height,
      guiWidth: dimensions.width,
      guiHeight: dimensions.height,
      offsetX: trim(element.type) === "slot" || trim(element.type) === "title" ||
        trim(element.type) === "text" ? -1 : 0,
      offsetY: 0,
      target: preview,
      moved: false
    };
    preview.classList.add("is-dragging");
    try {
      canvas.setPointerCapture(event.pointerId);
    } catch (error) {
      guiCanvasDrag = null;
      preview.classList.remove("is-dragging");
      return;
    }
    event.preventDefault();
  }

  function moveGuiCanvasDrag(event) {
    var drag = guiCanvasDrag;
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var element = gui && Array.isArray(gui.elements) ? gui.elements[drag.index] : null;
    if (!isPlainObject(element)) {
      return;
    }
    var dx = (event.clientX - drag.startClientX) / drag.scale;
    var dy = (event.clientY - drag.startClientY) / drag.scale;
    if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
      drag.moved = true;
    }
    var maxX = Math.max(0, drag.guiWidth - drag.width);
    var maxY = Math.max(0, drag.guiHeight - drag.height);
    var x = clamp(Math.round(drag.startX + dx), 0, maxX);
    var y = clamp(Math.round(drag.startY + dy), 0, maxY);
    element.x = x;
    element.y = y;
    drag.target.style.left = (x + drag.offsetX) + "px";
    drag.target.style.top = (y + drag.offsetY) + "px";
    setGuiElementCoordinate(drag.index, "x", x);
    setGuiElementCoordinate(drag.index, "y", y);
    event.preventDefault();
  }

  function endGuiCanvasDrag(event) {
    var drag = guiCanvasDrag;
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }
    guiCanvasDrag = null;
    drag.target.classList.remove("is-dragging");
    var canvas = $("guiCanvas");
    if (canvas && canvas.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
    if (drag.moved) {
      refresh({
        skipPattern: true,
        skipMatchers: true,
        skipSlots: true,
        skipCapabilities: true,
        skipController: true,
        skipGui: true
      });
      updateGuiCard();
      return;
    }
    var card = guiElementCardElement(drag.index);
    var input = card ? card.querySelector("input, select") : null;
    focusElement(input || card, false);
  }

  function highlightJson(text) {
    var pattern = /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"\s*:?|\b(?:true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+\-]?\d+)?)/g;
    var output = "";
    var lastIndex = 0;
    var match = pattern.exec(text);
    while (match) {
      output += esc(text.slice(lastIndex, match.index));
      var token = match[0];
      var className;
      if (token.charAt(0) === "\"") {
        className = /:\s*$/.test(token) ? "tok-key" : "tok-string";
      } else if (/^(?:true|false|null)$/.test(token)) {
        className = "tok-bool";
      } else {
        className = "tok-number";
      }
      output += "<span class='" + className + "'>" + esc(token) + "</span>";
      lastIndex = match.index + token.length;
      match = pattern.exec(text);
    }
    return output + esc(text.slice(lastIndex));
  }

  function inheritanceFragment() {
    return V.buildInheritance(state.inheritance);
  }

  function inheritanceSummaryText() {
    var inheritance = isPlainObject(state.inheritance) ? state.inheritance : V.createInheritance();
    var parts = [];
    if (inheritance.mode === "extends" || inheritance.mode === "family") {
      parts.push(inheritance.mode + " " + (trim(inheritance.parent) || "sem pai"));
    }
    if (inheritance.template) {
      parts.push("template");
    }
    if (isPlainObject(inheritance.overrides) && Object.keys(inheritance.overrides).length > 0) {
      parts.push(Object.keys(inheritance.overrides).length + " override(s)");
    }
    return parts.length > 0 ? parts.join(" · ") : "Sem herança; a definição é autocontida.";
  }

  function updateInheritancePreview() {
    var fragment = inheritanceFragment();
    var json = $("inheritanceJson");
    if (json) {
      json.textContent = JSON.stringify(fragment, null, 2);
    }
    var summary = $("inheritanceSummary");
    if (summary) {
      summary.textContent = inheritanceSummaryText();
    }
    var feedback = $("inheritanceOverridesFeedback");
    var error = isPlainObject(state.inheritance) ? trim(state.inheritance.overridesError) : "";
    if (feedback) {
      feedback.className = "matcher-feedback" + (error ? " is-error" : "");
      feedback.textContent = error;
      feedback.hidden = !error;
    }
    var inheritanceFeedback = $("inheritanceFeedback");
    if (inheritanceFeedback) {
      var issue = report && report.issues ? report.issues.find(function (entry) {
        return entry.step === "inheritance" && entry.severity === "error";
      }) : null;
      inheritanceFeedback.className = "matcher-feedback inheritance-feedback" + (issue ? " is-error" : "");
      inheritanceFeedback.textContent = issue ? issue.message : "";
      inheritanceFeedback.hidden = !issue;
    }
  }

  function renderInheritance() {
    var inheritance = isPlainObject(state.inheritance) ? state.inheritance : V.createInheritance();
    $("inheritanceMode").value = inheritance.mode || "none";
    $("inheritanceParent").value = inheritance.parent || "";
    $("inheritanceParent").disabled = inheritance.mode === "none";
    $("inheritanceTemplate").checked = inheritance.template === true;
    $("inheritanceOverrides").value = inheritance.overridesRaw || "";
    updateInheritancePreview();
  }

  function onInheritanceInput(event) {
    var target = event.target;
    if (!target || !isPlainObject(state.inheritance)) {
      return;
    }
    if (target.id === "inheritanceParent") {
      state.inheritance.parent = target.value;
    } else if (target.id === "inheritanceOverrides") {
      state.inheritance.overridesRaw = target.value;
      if (!trim(target.value)) {
        state.inheritance.overrides = null;
        state.inheritance.overridesError = "";
      } else {
        try {
          var parsed = JSON.parse(target.value);
          if (!isPlainObject(parsed)) {
            state.inheritance.overrides = null;
            state.inheritance.overridesError = "overrides precisa ser um objeto JSON.";
          } else {
            state.inheritance.overrides = parsed;
            state.inheritance.overridesError = "";
          }
        } catch (error) {
          state.inheritance.overrides = null;
          state.inheritance.overridesError = "JSON inválido em overrides: " + error.message;
        }
      }
    } else {
      return;
    }
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true,
      skipInheritance: true
    });
    updateInheritancePreview();
  }

  function onInheritanceChange(event) {
    var target = event.target;
    if (!target || !isPlainObject(state.inheritance)) {
      return;
    }
    if (target.id === "inheritanceMode") {
      state.inheritance.mode = target.value;
    } else if (target.id === "inheritanceTemplate") {
      state.inheritance.template = target.checked;
    } else {
      return;
    }
    refresh();
  }

  function recipeFile(recipe) {
    return isPlainObject(recipe) ? trim(recipe.file).replace(/\.json$/i, "") : "";
  }

  function recipePath(recipe) {
    var file = recipeFile(recipe) || "<arquivo>";
    return "data/" + (trim(state.namespace) || "namespace") + "/recipe/" + file + ".json";
  }

  function recipeAvailability() {
    return V.recipeAvailability(state);
  }

  function recipeJson(recipe) {
    var options = recipeAvailability();
    options.definitionId = (trim(state.namespace) || "namespace") + ":" + (trim(state.path) || "path");
    return JSON.stringify(V.buildRecipe(recipe, options), null, 2);
  }

  function recipeErrors(index) {
    if (!report || !Array.isArray(report.errors)) {
      return [];
    }
    var prefix = "recipe:" + index + ":";
    return report.errors.filter(function (entry) {
      return entry.field.indexOf(prefix) === 0;
    });
  }

  function recipeInputRows(recipe) {
    if (!isPlainObject(recipe)) {
      return [];
    }
    if (!Array.isArray(recipe.inputRows)) {
      recipe.inputRows = [];
    }
    return recipe.inputRows;
  }

  function recipeOutputRows(recipe) {
    if (!isPlainObject(recipe)) {
      return [];
    }
    if (!Array.isArray(recipe.outputRows)) {
      recipe.outputRows = [];
    }
    return recipe.outputRows;
  }

  function recipeRowTypes(isOutput) {
    return isOutput
      ? ["item", "fluid", "chemical", "mana", "essentia", "entity", "energy"]
      : ["item", "fluid", "chemical", "mana", "essentia", "entity", "energy"];
  }

  function recipeRowTypeAvailable(type, availability, isOutput) {
    if (type === "item") {
      return isOutput ? availability.itemOutput : availability.itemInput;
    }
    if (type === "fluid") {
      return availability.fluid;
    }
    if (type === "chemical") {
      return availability.chemical;
    }
    if (type === "mana") {
      return availability.mana;
    }
    if (type === "essentia") {
      return availability.essentia;
    }
    if (type === "energy") {
      return availability.energy;
    }
    return true;
  }

  function recipeTypeOptions(current, availability, isOutput) {
    return recipeRowTypes(isOutput).map(function (type) {
      var available = recipeRowTypeAvailable(type, availability, isOutput);
      return "<option value='" + esc(type) + "'" +
        (type === current ? " selected" : "") +
        (available ? "" : " disabled") + ">" + esc(type) +
        (available ? "" : " (indisponivel)") + "</option>";
    }).join("");
  }

  function recipeAddOptions(availability, isOutput) {
    return recipeRowTypes(isOutput).filter(function (type) {
      return recipeRowTypeAvailable(type, availability, isOutput);
    }).map(function (type) {
      return "<option value='" + esc(type) + "'>" + esc(type) + "</option>";
    }).join("");
  }

  function recipeRowHtml(row, rowIndex, isOutput, availability) {
    var kind = V.recipeRowKind(row && row.kind);
    var section = isOutput ? "output" : "input";
    var field = "data-recipe-" + section + "-field";
    var typeField = "<label class='field'><span>tipo</span><select " + field + "='kind'>" +
      recipeTypeOptions(kind, availability, isOutput) + "</select></label>";
    var resourceField = "";
    var amountField = "";
    var componentsField = "";
    var spawnField = "";
    var tankField = "";
    var chanceField = "";
    if (kind === "item") {
      resourceField = "<label class='field'><span>id / tag</span>" +
        "<input " + field + "='resource' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.resource) + "' placeholder='minecraft:iron_ore'></label>";
      amountField = "<label class='field'><span>quantidade</span>" +
        "<input " + field + "='count' type='number' min='1' max='99' step='1' value='" +
        esc(row.count) + "'></label>";
      componentsField = "<details class='card-advanced'><summary>components (JSON)" +
        "<span class='card-advanced-hint'>opcional</span></summary>" +
        "<div class='card-advanced-body'><label class='field'><span>components</span>" +
        "<textarea " + field + "='componentsRaw' rows='3' spellcheck='false' placeholder='{}'>" +
        esc(row.componentsRaw) + "</textarea></label></div></details>";
    } else if (kind === "fluid") {
      resourceField = "<label class='field'><span>fluido / tag</span>" +
        "<input " + field + "='resource' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.resource) + "' placeholder='minecraft:water'></label>";
      amountField = "<label class='field'><span>quantidade (mB)</span>" +
        "<input " + field + "='amount' type='number' min='1' step='1' value='" +
        esc(row.amount) + "'></label>";
    } else if (kind === "chemical") {
      resourceField = "<label class='field'><span>chemical / tag</span>" +
        "<input " + field + "='resource' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.resource) + "' placeholder='mekanism:hydrogen'></label>";
      amountField = "<label class='field'><span>quantidade</span>" +
        "<input " + field + "='amount' type='number' min='1' step='1' value='" +
        esc(row.amount) + "'></label>";
      if (isOutput) {
        tankField = "<label class='field'><span>tank</span>" +
          "<input " + field + "='tank' type='text' spellcheck='false' autocomplete='off' " +
          "value='" + esc(row.tank) + "' placeholder='id do tanque chemical'></label>";
      }
    } else if (kind === "mana") {
      amountField = "<label class='field'><span>mana</span>" +
        "<input " + field + "='amount' type='number' min='1' step='1' value='" +
        esc(row.amount) + "'></label>";
    } else if (kind === "essentia") {
      resourceField = "<label class='field'><span>aspecto</span>" +
        "<input " + field + "='resource' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.resource) + "' placeholder='thaumaturge:aer'></label>";
      amountField = "<label class='field'><span>quantidade</span>" +
        "<input " + field + "='amount' type='number' min='1' step='1' value='" +
        esc(row.amount) + "'></label>";
      if (isOutput) {
        tankField = "<label class='field'><span>tank</span>" +
          "<input " + field + "='tank' type='text' spellcheck='false' autocomplete='off' " +
          "value='" + esc(row.tank) + "' placeholder='jars'></label>";
      }
    } else if (kind === "entity") {
      resourceField = "<label class='field'><span>id / tag</span>" +
        "<input " + field + "='resource' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.resource) + "' placeholder='minecraft:pig ou #c:chickens'></label>";
      amountField = "<label class='field'><span>quantidade</span>" +
        "<input " + field + "='count' type='number' min='1' max='99' step='1' value='" +
        esc(row.count) + "'></label>";
      spawnField = "<label class='field'><span>spawn (opcional)</span>" +
        "<input " + field + "='spawn' type='text' spellcheck='false' autocomplete='off' " +
        "value='" + esc(row.spawn) + "' placeholder='C, minecraft:iron_block ou vazio'></label>";
    } else {
      amountField = "<label class='field'><span>energia</span>" +
        "<input " + field + "='amount' type='number' min='1' step='1' value='" +
        esc(row.amount) + "'></label>";
    }
    if (isOutput) {
      chanceField = "<label class='field'><span>chance (0-1)</span>" +
        "<input " + field + "='chance' type='number' min='0' max='1' step='0.01' value='" +
        esc(row.chance) + "'></label>";
    }
    return "<div class='recipe-entry' data-recipe-" + section + "-index='" + rowIndex +
      "' data-recipe-" + section + "-kind='" + kind + "'>" +
      "<div class='recipe-entry-head'><div><strong>" + (rowIndex + 1) + ". " + esc(kind) +
      "</strong><small>" + section + "</small></div>" +
      "<button type='button' class='btn btn-tiny btn-danger' data-action='remove-" + section +
      "'>remover</button></div>" +
      "<div class='recipe-entry-grid'>" + typeField + resourceField + amountField +
      spawnField + tankField + chanceField + "</div>" + componentsField + "</div>";
  }

  function recipeCardElement(index) {
    return document.querySelector(".recipe-card[data-recipe-index='" + index + "']");
  }

  function recipeCardHtml(recipe, index) {
    var file = recipeFile(recipe);
    var inputRows = recipeInputRows(recipe);
    var outputRows = recipeOutputRows(recipe);
    var errors = recipeErrors(index);
    var feedback = errors.length > 0
      ? errors[0].message + (errors.length > 1 ? " (+" + (errors.length - 1) + ")" : "")
      : "";
    var disabled = errors.length > 0 ? " disabled" : "";
    var availability = recipeAvailability();
    var notices = [];
    if (!availability.itemInput && inputRows.some(function (row) {
      return V.recipeRowKind(row && row.kind) === "item";
    })) {
      notices.push("Inputs de item ignorados: esta maquina nao tem item_slot de input.");
    }
    if (!availability.itemOutput && outputRows.some(function (row) {
      return V.recipeRowKind(row && row.kind) === "item";
    })) {
      notices.push("Outputs de item ignorados: esta maquina nao tem item_slot de output.");
    }
    if (!availability.fluid && inputRows.concat(outputRows).some(function (row) {
      return V.recipeRowKind(row && row.kind) === "fluid";
    })) {
      notices.push("Fluidos ignorados: esta maquina nao tem tanque.");
    }
    if (!availability.chemical && inputRows.concat(outputRows).some(function (row) {
      return V.recipeRowKind(row && row.kind) === "chemical";
    })) {
      notices.push("Chemicals ignorados: esta maquina nao tem tanque chemical.");
    }
    if (!availability.mana && inputRows.concat(outputRows).some(function (row) {
      return V.recipeRowKind(row && row.kind) === "mana";
    })) {
      notices.push("Mana ignorada: esta maquina nao tem capabilities.mana.");
    }
    if (!availability.essentia && inputRows.concat(outputRows).some(function (row) {
      return V.recipeRowKind(row && row.kind) === "essentia";
    })) {
      notices.push("Essentia ignorada: esta maquina nao tem capabilities.essentia.");
    }
    if (!availability.energy && inputRows.concat(outputRows).some(function (row) {
      return V.recipeRowKind(row && row.kind) === "energy";
    })) {
      notices.push("Energia ignorada: esta maquina nao tem capabilities.energy.");
    }
    var noticesHtml = notices.length > 0
      ? "<div class='recipe-notes'>" + notices.map(function (message) {
        return "<p class='field-hint'>" + esc(message) + "</p>";
      }).join("") + "</div>"
      : "";
    var inputRowsHtml = inputRows.length > 0
      ? inputRows.map(function (row, rowIndex) {
        return recipeRowHtml(row, rowIndex, false, availability);
      }).join("")
      : "<div class='recipe-entry-empty'>Nenhum input. Adicione item, fluido, chemical, mana, essentia, entidade ou energia.</div>";
    var outputRowsHtml = outputRows.length > 0
      ? outputRows.map(function (row, rowIndex) {
        return recipeRowHtml(row, rowIndex, true, availability);
      }).join("")
      : "<div class='recipe-entry-empty'>Nenhum output. Adicione item, fluido, chemical, mana, essentia, entidade ou energia.</div>";
    var inputAddHtml = "<div class='recipe-add-row'>" +
      "<select data-recipe-add-input aria-label='Tipo de input'>" +
      recipeAddOptions(availability, false) + "</select>" +
      "<button type='button' class='btn btn-tiny' data-action='add-input'>+ input</button></div>";
    var outputAddHtml = "<div class='recipe-add-row'>" +
      "<select data-recipe-add-output aria-label='Tipo de output'>" +
      recipeAddOptions(availability, true) + "</select>" +
      "<button type='button' class='btn btn-tiny' data-action='add-output'>+ output</button></div>";
    var inputSection = "<div class='recipe-section'>" +
      "<div class='recipe-section-head'><div><h4>Inputs</h4>" +
      "<p class='field-hint'>Cole o JSON do /inmb hand no id/tag. Entidades sao consumidas ao completar.</p></div>" +
      inputAddHtml + "</div><div class='recipe-entry-list'>" + inputRowsHtml + "</div></div>";
    var outputSection = "<div class='recipe-section'>" +
      "<div class='recipe-section-head'><div><h4>Outputs</h4>" +
      "<p class='field-hint'>Cada output tem chance individual. 0 nunca gera, 1 sempre gera.</p></div>" +
      outputAddHtml + "</div><div class='recipe-entry-list'>" + outputRowsHtml + "</div></div>";
    return "<article class='recipe-card" + (errors.length > 0 ? " is-invalid" : "") +
      "' data-recipe-index='" + index + "'>" +
      "<div class='recipe-card-head'><div><span class='recipe-kicker'>receita " + pad2(index + 1) +
      "</span><h3 data-recipe-title>" + esc(file || "sem arquivo") +
      "</h3><code class='recipe-path' data-recipe-path>" + esc(recipePath(recipe)) +
      "</code></div><button type='button' class='btn btn-tiny btn-danger' data-action='remove-recipe'>remover</button></div>" +
      noticesHtml +
      "<div class='recipe-grid'>" +
      "<label class='field'><span>arquivo</span><input data-recipe-field='file' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.file) + "' placeholder='gold_ingot_from_ore'></label>" +
      "<label class='field'><span>time</span><input data-recipe-field='time' type='number' min='1' value='" + esc(recipe.time) + "'></label>" +
      "</div>" +
      inputSection +
      outputSection +
      "<div class='recipe-actions'><button type='button' class='btn btn-tiny' data-action='copy-recipe'" + disabled + ">Copiar JSON</button><button type='button' class='btn btn-tiny' data-action='download-recipe'" + disabled + ">Baixar .json</button></div>" +
      "<code class='matcher-json recipe-json' data-recipe-json>" + esc(recipeJson(recipe)) + "</code>" +
      "<p class='matcher-feedback recipe-feedback' data-recipe-feedback" + (feedback ? "" : " hidden") + ">" + esc(feedback) + "</p>" +
      "</article>";
  }

  function updateRecipeCard(index) {
    var card = recipeCardElement(index);
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!card || !isPlainObject(recipe)) {
      return;
    }
    var errors = recipeErrors(index);
    var feedback = card.querySelector("[data-recipe-feedback]");
    var json = card.querySelector("[data-recipe-json]");
    var title = card.querySelector("[data-recipe-title]");
    var path = card.querySelector("[data-recipe-path]");
    if (json) {
      json.textContent = recipeJson(recipe);
    }
    if (title) {
      title.textContent = recipeFile(recipe) || "sem arquivo";
    }
    if (path) {
      path.textContent = recipePath(recipe);
    }
    card.classList.toggle("is-invalid", errors.length > 0);
    if (feedback) {
      feedback.textContent = errors.length > 0
        ? errors[0].message + (errors.length > 1 ? " (+" + (errors.length - 1) + ")" : "")
        : "";
      feedback.hidden = errors.length === 0;
    }
    $$("[data-action='copy-recipe'], [data-action='download-recipe']", card).forEach(function (button) {
      button.disabled = errors.length > 0;
    });
  }

  function updateRecipeCards() {
    var recipes = Array.isArray(state.recipes) ? state.recipes : [];
    for (var index = 0; index < recipes.length; index += 1) {
      updateRecipeCard(index);
    }
  }

  function updateRecipeSummary() {
    var recipes = Array.isArray(state.recipes) ? state.recipes : [];
    var summary = $("recipeSummary");
    var empty = $("recipeEmpty");
    var note = $("recipeNote");
    if (note) {
      note.hidden = state.type === "recipe";
    }
    if (summary) {
      summary.textContent = recipes.length === 0
        ? "Nenhuma receita adicionada."
        : recipes.length + (recipes.length === 1 ? " receita" : " receitas") + " em data/" +
          (trim(state.namespace) || "namespace") + "/recipe/";
    }
    if (empty) {
      empty.hidden = recipes.length > 0;
    }
  }

  function renderRecipes() {
    var recipes = Array.isArray(state.recipes) ? state.recipes : [];
    $("recipeList").innerHTML = recipes.map(recipeCardHtml).join("");
    updateRecipeSummary();
    updateRecipeCards();
  }

  function refreshRecipeReport() {
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipGui: true,
      skipRecipes: true,
      skipInheritance: true
    });
    updateRecipeSummary();
    updateRecipeCards();
  }

  function parseRecipePastedJson(text) {
    var raw = trim(text);
    if (!raw || raw.charAt(0) !== "{") {
      return null;
    }
    var parsed;
    try {
      parsed = JSON.parse(raw);
    } catch (error) {
      return null;
    }
    if (!isPlainObject(parsed)) {
      return null;
    }
    var type = trim(parsed.type).toLowerCase();
    if (["item", "fluid", "chemical", "mana", "essentia", "entity", "energy"].indexOf(type) === -1) {
      type = "";
    }
    var components = parsed.components;
    var hasComponents = components !== undefined;
    var componentsRaw = hasComponents ? JSON.stringify(components, null, 2) : "";
    var resource = "";
    var source = "";
    if (parsed.ingredient !== undefined) {
      source = "ingredient";
      if (typeof parsed.ingredient === "string") {
        resource = trim(parsed.ingredient);
      } else if (isPlainObject(parsed.ingredient) &&
          typeof parsed.ingredient.items === "string") {
        resource = trim(parsed.ingredient.items);
        if (parsed.ingredient.components !== undefined) {
          hasComponents = true;
          componentsRaw = JSON.stringify(parsed.ingredient.components, null, 2);
        }
      } else {
        resource = V.parseRecipeResource(parsed.ingredient);
      }
    } else if (typeof parsed.items === "string" && trim(parsed.items)) {
      source = "ingredient";
      resource = trim(parsed.items);
    } else if (typeof parsed.aspect === "string" && trim(parsed.aspect)) {
      source = "entry";
      resource = trim(parsed.aspect);
    } else if (typeof parsed.id === "string" && trim(parsed.id)) {
      source = "entry";
      resource = trim(parsed.id);
    } else if (typeof parsed.tag === "string" && trim(parsed.tag)) {
      source = "entry";
      var pastedTag = trim(parsed.tag);
      resource = pastedTag.charAt(0) === "#" ? pastedTag : "#" + pastedTag;
    }
    if (!resource) {
      return null;
    }
    return {
      source: source,
      type: type || (source === "ingredient" ? "item" : "item"),
      resource: resource,
      componentsRaw: componentsRaw,
      hasComponents: hasComponents,
      count: parsed.count,
      amount: parsed.amount,
      spawn: typeof parsed.spawn === "string" ? trim(parsed.spawn) : "",
      tank: typeof parsed.tank === "string" ? trim(parsed.tank) : "",
      chance: parsed.chance
    };
  }

  function applyParsedRecipeRow(row, parsed, isOutput) {
    var kind = parsed.type || V.recipeRowKind(row.kind);
    row.kind = kind;
    row.resource = parsed.resource || "";
    if (kind === "item") {
      if (parsed.hasComponents) {
        row.componentsRaw = parsed.componentsRaw;
      } else if (row.componentsRaw === undefined) {
        row.componentsRaw = "";
      }
      var count = parseIntStrict(parsed.count);
      if (count !== null && count > 0) {
        row.count = String(count);
      } else if (!row.count) {
        row.count = "1";
      }
    } else if (kind === "fluid") {
      var amount = parseIntStrict(parsed.amount);
      if (amount === null) {
        amount = parseIntStrict(parsed.count);
      }
      if (amount !== null && amount > 0) {
        row.amount = String(amount);
      } else if (!row.amount) {
        row.amount = "1000";
      }
    } else if (kind === "chemical" || kind === "mana" || kind === "essentia") {
      var resourceAmount = parseIntStrict(parsed.amount);
      if (resourceAmount === null) {
        resourceAmount = parseIntStrict(parsed.count);
      }
      if (resourceAmount !== null && resourceAmount > 0) {
        row.amount = String(resourceAmount);
      } else if (!row.amount) {
        row.amount = kind === "essentia" ? "8" : "1000";
      }
      if (kind === "essentia" && isOutput && parsed.tank) {
        row.tank = parsed.tank;
      }
    } else if (kind === "entity") {
      row.spawn = parsed.spawn || row.spawn || "";
      var entityCount = parseIntStrict(parsed.count);
      if (entityCount !== null && entityCount > 0) {
        row.count = String(entityCount);
      } else if (!row.count) {
        row.count = "1";
      }
    } else {
      var energy = parseIntStrict(parsed.amount);
      if (energy !== null && energy > 0) {
        row.amount = String(energy);
      } else if (!row.amount) {
        row.amount = "1";
      }
    }
    if (isOutput) {
      var chance = parsed.chance;
      if (chance === undefined || chance === null || chance === "") {
        if (!row.chance) {
          row.chance = "1";
        }
      } else {
        row.chance = String(chance);
      }
    }
  }

  function onRecipeInput(event) {
    var target = event.target;
    var card = closest(target, ".recipe-card[data-recipe-index]");
    var index = card ? parseInt(card.getAttribute("data-recipe-index"), 10) : -1;
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!isPlainObject(recipe)) {
      return;
    }
    var rowElement = closest(target, "[data-recipe-input-index], [data-recipe-output-index]");
    if (rowElement) {
      var isOutput = rowElement.hasAttribute("data-recipe-output-index");
      var section = isOutput ? "output" : "input";
      var field = target.getAttribute("data-recipe-" + section + "-field");
      if (!field) {
        return;
      }
      var rowIndex = parseInt(rowElement.getAttribute("data-recipe-" + section + "-index"), 10);
      var rows = isOutput ? recipeOutputRows(recipe) : recipeInputRows(recipe);
      var row = rows[rowIndex];
      if (!isPlainObject(row)) {
        return;
      }
      if (field === "kind") {
        row.kind = V.recipeRowKind(target.value);
        var fresh = isOutput ? V.createRecipeOutputRow(row.kind) : V.createRecipeInputRow(row.kind);
        Object.keys(fresh).forEach(function (key) {
          if (row[key] === undefined) {
            row[key] = fresh[key];
          }
        });
        refreshRecipeReport();
        renderRecipes();
        var updatedCard = recipeCardElement(index);
        var changed = updatedCard
          ? updatedCard.querySelector("[data-recipe-" + section + "-index='" + rowIndex +
            "'] [data-recipe-" + section + "-field='resource'], [data-recipe-" + section +
            "-index='" + rowIndex + "'] [data-recipe-" + section + "-field='amount']")
          : null;
        focusElement(changed || updatedCard, false);
        return;
      }
      if (field === "resource") {
        var parsed = parseRecipePastedJson(target.value);
        if (parsed) {
          var previousKind = row.kind;
          applyParsedRecipeRow(row, parsed, isOutput);
          refreshRecipeReport();
          if (previousKind !== row.kind) {
            renderRecipes();
          }
          var updated = recipeCardElement(index);
          var focusTarget = updated
            ? updated.querySelector("[data-recipe-" + section + "-index='" + rowIndex +
              "'] [data-recipe-" + section + "-field='resource']")
            : null;
          focusElement(focusTarget || updated, false);
          return;
        }
      }
      row[field] = target.value;
      var componentsInput = field === "componentsRaw" ? closest(target, "details.card-advanced") : null;
      if (componentsInput && trim(target.value)) {
        componentsInput.open = true;
      }
      refreshRecipeReport();
      return;
    }
    var recipeField = target.getAttribute("data-recipe-field");
    if (!recipeField) {
      return;
    }
    recipe[recipeField] = target.value;
    refreshRecipeReport();
  }

  function downloadRecipe(index) {
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!isPlainObject(recipe)) {
      return;
    }
    if (recipeErrors(index).length > 0) {
      showToast("Corrija os erros desta receita antes de baixar.", "error");
      return;
    }
    var file = recipeFile(recipe);
    if (!file) {
      showToast("Informe o nome do arquivo da receita.", "error");
      return;
    }
    var segments = file.split("/");
    var name = segments[segments.length - 1] || "recipe";
    var blob = new Blob([recipeJson(recipe) + "\n"], { type: "application/json" });
    var url = URL.createObjectURL(blob);
    var link = document.createElement("a");
    link.href = url;
    link.download = name + ".json";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(function () {
      URL.revokeObjectURL(url);
    }, 1000);
    showToast("Baixado " + name + ".json. Salve em " + recipePath(recipe) + " sem renomear.");
  }

  function addRecipe() {
    if (!Array.isArray(state.recipes)) {
      state.recipes = [];
    }
    var used = new Set(state.recipes.map(recipeFile));
    var number = state.recipes.length + 1;
    var file = "recipe_" + number;
    while (used.has(file)) {
      number += 1;
      file = "recipe_" + number;
    }
    var recipe = V.createRecipe();
    recipe.file = file;
    state.recipes.push(recipe);
    refresh();
    var card = recipeCardElement(state.recipes.length - 1);
    var input = card ? card.querySelector("[data-recipe-field='file']") : null;
    focusElement(input || card, false);
  }

  function removeRecipe(index) {
    if (!Array.isArray(state.recipes) || index < 0 || index >= state.recipes.length) {
      return;
    }
    if (!window.confirm("Remover a receita " + (index + 1) + "?")) {
      return;
    }
    state.recipes.splice(index, 1);
    refresh();
  }

  function addRecipeRow(index, isOutput) {
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!isPlainObject(recipe)) {
      return;
    }
    var card = recipeCardElement(index);
    var selector = isOutput ? "[data-recipe-add-output]" : "[data-recipe-add-input]";
    var select = card ? card.querySelector(selector) : null;
    var kind = V.recipeRowKind(select ? select.value : (isOutput ? "item" : "item"));
    var rows = isOutput ? recipeOutputRows(recipe) : recipeInputRows(recipe);
    var row = isOutput ? V.createRecipeOutputRow(kind) : V.createRecipeInputRow(kind);
    rows.push(row);
    refresh();
    var newCard = recipeCardElement(index);
    var section = isOutput ? "output" : "input";
    var input = newCard
      ? newCard.querySelector("[data-recipe-" + section + "-index='" + (rows.length - 1) +
        "'] [data-recipe-" + section + "-field='resource'], " +
        "[data-recipe-" + section + "-index='" + (rows.length - 1) +
        "'] [data-recipe-" + section + "-field='amount']")
      : null;
    focusElement(input || newCard, false);
  }

  function removeRecipeRow(index, rowIndex, isOutput) {
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!isPlainObject(recipe)) {
      return;
    }
    var rows = isOutput ? recipeOutputRows(recipe) : recipeInputRows(recipe);
    if (!isPlainObject(rows[rowIndex])) {
      return;
    }
    rows.splice(rowIndex, 1);
    refresh();
  }

  function onRecipeAction(event) {
    var action = closest(event.target, "[data-action]");
    if (!action) {
      return;
    }
    var holder = closest(action, "[data-recipe-index]");
    var index = holder ? parseInt(holder.getAttribute("data-recipe-index"), 10) : -1;
    var name = action.getAttribute("data-action");
    if (name === "add-input") {
      addRecipeRow(index, false);
    } else if (name === "add-output") {
      addRecipeRow(index, true);
    } else if (name === "remove-input") {
      var inputRow = closest(action, "[data-recipe-input-index]");
      removeRecipeRow(index, inputRow
        ? parseInt(inputRow.getAttribute("data-recipe-input-index"), 10)
        : -1, false);
    } else if (name === "remove-output") {
      var outputRow = closest(action, "[data-recipe-output-index]");
      removeRecipeRow(index, outputRow
        ? parseInt(outputRow.getAttribute("data-recipe-output-index"), 10)
        : -1, true);
    } else if (name === "remove-recipe") {
      removeRecipe(index);
    } else if (name === "download-recipe") {
      downloadRecipe(index);
    } else if (name === "copy-recipe") {
      if (recipeErrors(index).length > 0) {
        showToast("Corrija os erros desta receita antes de copiar.", "error");
        return;
      }
      showCopyResult({ text: recipeJson(state.recipes[index]), done: "Receita copiada." });
    }
  }

  function textureAssetPath(texture) {
    var text = trim(texture);
    if (!text) {
      return "";
    }
    var separator = text.indexOf(":");
    var namespace = separator === -1 ? state.namespace : text.slice(0, separator);
    var path = separator === -1 ? text : text.slice(separator + 1);
    path = path.replace(/^textures\//, "");
    if (!/\.png$/i.test(path)) {
      path += ".png";
    }
    return "assets/" + namespace + "/textures/" + path;
  }

  function soundAssetPathFromId(id) {
    var text = trim(id);
    if (!text) {
      return "";
    }
    var separator = text.indexOf(":");
    var namespace = separator === -1 ? (trim(state.namespace) || "namespace") : text.slice(0, separator);
    var path = separator === -1 ? text : text.slice(separator + 1);
    return "assets/" + namespace + "/sounds/" + path + ".ogg";
  }

  function assetRows() {
    var rows = [];
    function add(label, path) {
      rows.push({ label: label, path: path });
    }
    function addSound(label, id) {
      var text = trim(id);
      if (!text || text.indexOf("minecraft:") === 0) {
        return;
      }
      add(label, soundAssetPathFromId(text));
    }
    add("definição", filePath());
    (Array.isArray(state.recipes) ? state.recipes : []).forEach(function (recipe, index) {
      add("receita " + pad2(index + 1), recipePath(recipe));
    });
    if (isPlainObject(state.sounds)) {
      addSound("som processing", state.sounds.processing);
      addSound("som finish", state.sounds.finish);
    }
    rows.push({
      note: "Controller compartilhado: infinity_nexus_multiblock:multiblock_controller. Nenhum blockstate ou textura por definição."
    });
    if (isPlainObject(state.controllerRecipe)) {
      add("receita do controller", "data/" + (trim(state.namespace) || "namespace") +
        "/recipe/controller/" + (trim(state.path) || "path") + ".json");
      rows.push({
        note: "O resultado da receita shaped deve ser infinity_nexus_multiblock:multiblock_controller."
      });
    }
    return rows;
  }

  function renderAssets() {
    var list = $("assetList");
    if (!list) {
      return;
    }
    list.innerHTML = assetRows().map(function (row) {
      if (row.note) {
        return "<p class='asset-note'>" + esc(row.note) + "</p>";
      }
      var path = row.path || "";
      return "<div class='asset-row'><span class='asset-label'>" + esc(row.label) +
        "</span><code class='asset-path'>" + esc(path || "caminho não definido") +
        "</code><button type='button' class='btn btn-tiny' data-asset-path='" + esc(path) +
        "'" + (path ? "" : " disabled") + ">copiar</button></div>";
    }).join("");
  }

  function onAssetClick(event) {
    var button = closest(event.target, "[data-asset-path]");
    if (!button) {
      return;
    }
    showCopyResult({ text: button.getAttribute("data-asset-path"), done: "Caminho copiado." });
  }

  function renderReview() {
    var dimensions = getDimensions();
    var matcherCount = Object.keys(state.matchers || {}).length;
    var usedCount = Object.keys(currentUsage).filter(function (symbol) {
      return symbol !== " " && symbol !== state.anchor && symbol !== upgradeKey();
    }).length;
    var upgrades = upgradesState();
    var boosterCount = upgrades && Array.isArray(upgrades.boosters) ? upgrades.boosters.length : 0;
    var upgradesValue = upgrades ? String(boosterCount) : "nenhum";
    var upgradesTitle = upgrades
      ? "key " + (upgradeKey() || "?") + " · " + boosterCount + (boosterCount === 1 ? " booster" : " boosters")
      : "sem upgrades";
    function card(label, value, title) {
      return "<div class='summary-card'><span>" + esc(label) + "</span><strong title='" +
        esc(title || value) + "'>" + esc(value) + "</strong></div>";
    }
    var typeTitle = state.type === "recipe" ? "recipe · " + V.RECIPE_TYPE : "storage";
    var slots = Array.isArray(state.itemSlots) ? state.itemSlots : null;
    var slotTitle = slots === null
      ? "inventory_rows=" + (state.inventoryRows || 3)
      : slots.length + (slots.length === 1 ? " slot em item_slots" : " slots em item_slots");
    var slotValue = slots === null ? "inventory_rows" : String(slots.length);
    var inputs = slots === null ? 0 : slots.filter(function (slot) {
      return isPlainObject(slot) && trim(slot.role) === "input";
    }).length;
    var outputs = slots === null ? 0 : slots.filter(function (slot) {
      return isPlainObject(slot) && trim(slot.role) === "output";
    }).length;
    var capabilityState = isPlainObject(state.capabilities) ? state.capabilities : null;
    var energy = capabilityState && isPlainObject(capabilityState.energy) ? capabilityState.energy : null;
    var capabilityFluids = capabilityState && Array.isArray(capabilityState.fluids) ? capabilityState.fluids : [];
    var capabilityValue = capabilityState ? "sim" : "não";
    var capabilityTitle = capabilityState
      ? (energy ? "energia " + (trim(energy.id) || "sem id") + " · " + capabilityFieldValue(energy.capacity) + " FE" : "sem energia") +
        " · " + (capabilityFluids.length === 1 ? "1 tanque" : capabilityFluids.length + " tanques")
      : "sem capabilities";
    var controllerValue = "único";
    var controllerTitle = "infinity_nexus_multiblock:multiblock_controller";
    var controllerRecipe = isPlainObject(state.controllerRecipe) ? state.controllerRecipe : null;
    var controllerRecipeValue = controllerRecipe ? "shaped" : "nenhuma";
    var controllerRecipeTitle = controllerRecipe
      ? controllerRecipeSummaryText()
      : "nenhuma receita de controller";
    var gui = isPlainObject(state.gui) ? state.gui : null;
    var guiValue = gui ? (Array.isArray(gui.elements) ? gui.elements.length + " elem" : "sim") : "fallback";
    var guiTitle = gui
      ? guiBaseValue(gui.width, V.GUI_DEFAULT_WIDTH) + "x" + guiBaseValue(gui.height, V.GUI_DEFAULT_HEIGHT) +
        " - " + (trim(gui.texture) || "sem textura")
      : "layout 176x166";
    var inheritance = inheritanceFragment();
    var inheritanceValue = inheritance.template ? "template" : inheritance.extends || inheritance.family ? "herança" : "nenhuma";
    var inheritanceTitle = inheritanceSummaryText();
    var recipes = Array.isArray(state.recipes) ? state.recipes : [];
    var recipeValue = recipes.length > 0 ? String(recipes.length) : "nenhuma";
    var recipeTitle = recipes.length > 0
      ? recipes.length + (recipes.length === 1 ? " arquivo em " : " arquivos em ") +
        "data/" + (trim(state.namespace) || "namespace") + "/recipe/"
      : "nenhuma receita adicionada";
    $("summaryGrid").innerHTML = [
      card("ID", (trim(state.namespace) || "namespace") + ":" + (trim(state.path) || "path")),
      card("Tipo", state.type, typeTitle),
      card("Pattern", dimensions.width + " × " + dimensions.rows + " × " + dimensions.height, "X × Z × Y"),
      card("Matchers", String(matcherCount), matcherCount + " matcher(s) para " + usedCount + " símbolo(s) de bloco"),
      card("Boosters", upgradesValue, upgradesTitle),
      card("Slots", slotValue, slotTitle + (slots === null ? "" : " · " + inputs + " input, " + outputs + " output")),
      card("Capabilities", capabilityValue, capabilityTitle),
      card("Controller", controllerValue, controllerTitle),
      card("Receita controller", controllerRecipeValue, controllerRecipeTitle),
      card("GUI", guiValue, guiTitle),
      card("Receitas", recipeValue, recipeTitle),
      card("Herança", inheritanceValue, inheritanceTitle)
    ].join("");
    var reviewRecipesPanel = $("reviewRecipesPanel");
    if (reviewRecipesPanel) {
      reviewRecipesPanel.hidden = recipes.length === 0;
      $("reviewRecipeList").innerHTML = recipes.map(function (recipe, index) {
        var errors = recipeErrors(index);
        var disabled = errors.length > 0 ? " disabled" : "";
        return "<div class='review-recipe' data-recipe-index='" + index + "'><div><strong>" +
          esc(recipeFile(recipe) || "sem arquivo") + "</strong><code>" + esc(recipePath(recipe)) +
          "</code></div><div class='button-row'><button type='button' class='btn btn-tiny' data-action='copy-recipe'" +
          disabled + ">Copiar</button><button type='button' class='btn btn-tiny' data-action='download-recipe'" +
          disabled + ">Baixar</button></div></div>";
      }).join("");
    }
    $("jsonPreview").innerHTML = highlightJson(jsonText);
    $("reviewChecklist").innerHTML = report.checks.map(function (check) {
      return "<li class='check-item " + (check.ok ? "is-ok" : "is-bad") + "'><span class='check-icon'>" +
        (check.ok ? "✓" : "✕") + "</span><span>" + esc(check.label) + "</span></li>";
    }).join("");
    $("reviewFilePath").textContent = filePath();
    renderAssets();
  }

  function renderExport() {
    var path = filePath();
    $("filePathPreview").textContent = path;
    $("filePath").textContent = path;
    $("filePath").title = path;
    $("reviewFilePath").textContent = path;
    $("idPreview").textContent = (trim(state.namespace) || "namespace") + ":" + (trim(state.path) || "path");
    var errors = report.errors.length;
    var warnings = report.warnings.length;
    var hint = $("exportHint");
    if (errors > 0) {
      hint.textContent = errors + (errors === 1 ? " erro bloqueia" : " erros bloqueiam") + " a exportação";
      hint.setAttribute("data-tone", "error");
    } else if (warnings > 0) {
      hint.textContent = warnings + (warnings === 1 ? " aviso" : " avisos") + " · exportação liberada";
      hint.removeAttribute("data-tone");
    } else {
      hint.textContent = "JSON pronto para exportar";
      hint.removeAttribute("data-tone");
    }
    ["copyJsonBtn", "copyJsonInlineBtn", "downloadBtn", "downloadInlineBtn"].forEach(function (id) {
      var button = $(id);
      if (!button) {
        return;
      }
      button.disabled = errors > 0;
      button.title = errors > 0 ? "Corrija os erros antes de exportar." : "";
    });
  }

  function renderInspector() {
    var errors = report.errors.length;
    var warnings = report.warnings.length;
    var badge = $("issueCount");
    badge.textContent = String(report.issues.length);
    badge.setAttribute("data-tone", errors > 0 ? "error" : warnings > 0 ? "warn" : "ok");
    var statusCard = $("statusCard");
    var statusTitle = $("statusTitle");
    var statusMessage = $("statusMessage");
    if (errors > 0) {
      statusCard.setAttribute("data-tone", "error");
      statusTitle.textContent = "Exportação bloqueada";
      statusMessage.textContent = errors + (errors === 1 ? " erro precisa" : " erros precisam") + " ser corrigido(s).";
    } else if (warnings > 0) {
      statusCard.setAttribute("data-tone", "warn");
      statusTitle.textContent = "Pronto com avisos";
      statusMessage.textContent = warnings + (warnings === 1 ? " aviso" : " avisos") + " para revisar antes de salvar.";
    } else {
      statusCard.setAttribute("data-tone", "ok");
      statusTitle.textContent = "Pronto para exportar";
      statusMessage.textContent = "Nenhum erro encontrado no esqueleto.";
    }
    if (report.issues.length === 0) {
      $("issueList").innerHTML = "<div class='empty-issues'>Nenhum problema encontrado. O esqueleto está pronto.</div>";
    } else {
      $("issueList").innerHTML = report.issues.map(function (entry) {
        var icon = entry.severity === "error" ? "!" : entry.severity === "warning" ? "?" : "i";
        var label = STEP_LABELS[entry.step] || entry.step;
        return "<button type='button' class='issue-button' data-severity='" + entry.severity +
          "' data-step='" + entry.step + "' data-field='" + esc(entry.field) +
          "'><span class='issue-icon'>" + icon + "</span><span class='issue-copy'><strong>" +
          esc(entry.message) + "</strong><small>" + esc(label) +
          (entry.field ? " · " + esc(entry.field) : "") + "</small></span></button>";
      }).join("");
    }
    $("checkList").innerHTML = report.checks.map(function (check) {
      return "<li class='check-item " + (check.ok ? "is-ok" : "is-bad") + "'><span class='check-icon'>" +
        (check.ok ? "✓" : "✕") + "</span><span>" + esc(check.label) + "</span></li>";
    }).join("");
  }

  function renderImportNotes() {
    var banner = $("importBanner");
    if (!importNotes || importNotes.length === 0) {
      banner.hidden = true;
      banner.innerHTML = "";
      banner.removeAttribute("data-tone");
      return;
    }
    var labels = { error: "Erro", warning: "Aviso", info: "Nota" };
    var worst = "info";
    if (importNotes.some(function (note) { return note.severity === "error"; })) {
      worst = "error";
    } else if (importNotes.some(function (note) { return note.severity === "warning"; })) {
      worst = "warning";
    } else {
      worst = "";
    }
    if (worst) {
      banner.setAttribute("data-tone", worst);
    } else {
      banner.removeAttribute("data-tone");
    }
    banner.innerHTML = "<div><strong>JSON importado.</strong><ul>" + importNotes.map(function (note) {
      var label = labels[note.severity] || "Nota";
      return "<li>" + esc(label) + ": " + esc(note.message) + "</li>";
    }).join("") + "</ul></div><button type='button' class='btn btn-tiny' data-dismiss-import='true'>fechar</button>";
    banner.hidden = false;
  }

  function previewModelEntry(key) {
    var requested = MODEL_KEYS.indexOf(key) >= 0 ? key : "idle";
    if (controllerMode !== "custom") {
      return { key: requested, id: DEFAULT_MODEL_IDS[requested] };
    }
    var model = isPlainObject(state.model) ? state.model : null;
    if (!model) {
      return null;
    }
    var order = requested === "building"
      ? ["building", "active", "idle"]
      : requested === "active" ? ["active", "idle"] : ["idle"];
    for (var index = 0; index < order.length; index += 1) {
      var id = trim(model[order[index]]);
      if (id) {
        return { key: order[index], id: id };
      }
    }
    return null;
  }

  function mergePreviewTextureSet(target, set) {
    if (!isPlainObject(set)) {
      return;
    }
    ["all", "front", "back", "left", "right", "top", "bottom", "side", "particle"].forEach(function (key) {
      var value = trim(set[key]);
      if (value) {
        target[key] = value;
      }
    });
    var side = trim(set.side);
    if (side) {
      if (!trim(set.back)) {
        target.back = side;
      }
      if (!trim(set.left)) {
        target.left = side;
      }
      if (!trim(set.right)) {
        target.right = side;
      }
    }
  }

  function previewTextureOverrides() {
    var textures = isPlainObject(state.textures) ? state.textures : null;
    if (!textures) {
      return {};
    }
    var result = {};
    mergePreviewTextureSet(result, textures.idle);
    if (modelPreviewState.previewState !== "idle") {
      mergePreviewTextureSet(result, textures.active);
    }
    if (modelPreviewState.previewState === "building") {
      mergePreviewTextureSet(result, textures.building);
    }
    if (!result.particle) {
      if (result.front) {
        result.particle = result.front;
      } else if (result.all) {
        result.particle = result.all;
      }
    }
    var overrides = {};
    if (result.all) {
      overrides.all = result.all;
    }
    if (result.front) {
      overrides.front = result.front;
      overrides.north = result.front;
    }
    var back = result.back || result.side;
    if (back) {
      overrides.back = back;
      overrides.south = back;
    }
    var left = result.left || result.side;
    if (left) {
      overrides.left = left;
      overrides.west = left;
    }
    var right = result.right || result.side;
    if (right) {
      overrides.right = right;
      overrides.east = right;
    }
    if (result.top) {
      overrides.top = result.top;
      overrides.up = result.top;
    }
    if (result.bottom) {
      overrides.bottom = result.bottom;
      overrides.down = result.bottom;
    }
    if (result.side) {
      overrides.side = result.side;
    }
    if (result.particle) {
      overrides.particle = result.particle;
    }
    return overrides;
  }

  function applyPreviewKeyTextures(parsed) {
    if (!ModelPreview || typeof ModelPreview.applyKeyOverrides !== "function") {
      return parsed;
    }
    return ModelPreview.applyKeyOverrides(parsed, currentPreviewTextureKeys());
  }

  function applyPreviewTextureOverrides(parsed) {
    if (!parsed || !ModelPreview || typeof ModelPreview.applyTextureOverrides !== "function") {
      return parsed;
    }
    var applied = ModelPreview.applyTextureOverrides(parsed, previewTextureOverrides(), {
      namespace: parsed.namespace || "minecraft"
    });
    if (controllerMode === "custom") {
      return applyPreviewKeyTextures(applied);
    }
    return applied;
  }

  function syncModelPreviewTextures() {
    if (!modelPreviewState.baseParsed) {
      return;
    }
    modelPreviewState.fieldRequest += 1;
    var request = modelPreviewState.fieldRequest;
    modelPreviewState.loadingField = false;
    modelPreviewState.parsed = applyPreviewTextureOverrides(modelPreviewState.baseParsed);
    loadModelPreviewAutoTextures(modelPreviewState.parsed, request);
    renderModelPreview();
  }

  function previewTextureNamespace() {
    return currentNamespace();
  }

  function previewTextureIdentifier(face, file, namespace) {
    var base = String(file && file.name || face)
      .replace(/\.(png|json)$/i, "")
      .trim()
      .toLowerCase();
    base = base.replace(/[^a-z0-9_.-]+/g, "_").replace(/^_+|_+$/g, "");
    if (!base) {
      base = face;
    }
    var ns = trim(namespace) || previewTextureNamespace();
    return ns + ":block/" + base;
  }

  function currentPreviewTextureSet() {
    var textures = isPlainObject(state.textures) ? state.textures : null;
    var setName = modelPreviewState.previewState;
    return textures && isPlainObject(textures[setName]) ? textures[setName] : {};
  }

  var PREVIEW_TEXTURE_SLOT_MAP = { down: "bottom" };
  var PREVIEW_DIRECTION_SLOT_MAP = {
    north: "front",
    south: "back",
    west: "left",
    east: "right",
    up: "top",
    down: "bottom"
  };
  function previewTextureSlot(key) {
    if (["all", "front", "back", "left", "right", "top", "bottom", "side", "particle"].indexOf(key) >= 0) {
      return key;
    }
    return PREVIEW_TEXTURE_SLOT_MAP[key] || "";
  }

  function previewTextureRowSlot(row) {
    if (!row) {
      return "";
    }
    if (row.key === "particle") {
      return "particle";
    }
    var raw = trim(row.raw);
    if (raw.charAt(0) === "#") {
      var key = raw.slice(1);
      var slot = previewTextureSlot(key);
      if (slot) {
        return slot;
      }
      return PREVIEW_DIRECTION_SLOT_MAP[key] || "";
    }
    if (raw) {
      return "";
    }
    return previewTextureSlot(row.key);
  }

  function currentPreviewTextureKeys() {
    var key = modelPreviewState.previewState;
    if (!isPlainObject(modelPreviewState.previewTextureKeys[key])) {
      modelPreviewState.previewTextureKeys[key] = {};
    }
    return modelPreviewState.previewTextureKeys[key];
  }

  function imageAppliesToPreviewState(image) {
    return !image.previewOnly || image.previewState === modelPreviewState.previewState;
  }

  function customPreviewTextureRows(parsed) {
    if (!parsed || !isPlainObject(parsed.textures) || !ModelPreview
      || typeof ModelPreview.resolveTextureId !== "function") {
      return [];
    }
    var namespace = parsed.namespace || "minecraft";
    var keys = [];
    var seen = {};
    function addKey(key) {
      if (typeof key !== "string" || !key || seen[key]) {
        return;
      }
      seen[key] = true;
      keys.push(key);
    }
    var source = parsed.raw && isPlainObject(parsed.raw.textures)
      ? parsed.raw.textures
      : parsed.textures;
    Object.keys(source).forEach(addKey);
    if (keys.length > 0) {
      (Array.isArray(parsed.elements) ? parsed.elements : []).forEach(function (element) {
        var faces = element && element.faces;
        if (!isPlainObject(faces)) {
          return;
        }
        Object.keys(faces).forEach(function (direction) {
          var face = faces[direction];
          var raw = face && typeof face.texture === "string" ? face.texture : "";
          if (raw.charAt(0) === "#") {
            addKey(raw.slice(1));
          }
        });
      });
    }
    if (keys.length === 0) {
      Object.keys(parsed.textures).forEach(addKey);
    }
    return keys.map(function (key) {
      var identifier = ModelPreview.resolveTextureId(parsed.textures, "#" + key, 0, namespace);
      if (!identifier) {
        identifier = trim(currentPreviewTextureKeys()[key]) || null;
      }
      return {
        key: "#" + key,
        textureKey: key,
        raw: typeof parsed.textures[key] === "string" ? parsed.textures[key] : "",
        identifier: identifier || null,
        vanilla: Boolean(identifier) && identifier.indexOf("minecraft:") === 0
      };
    });
  }

  function previewOnlyImage(identifier) {
    var id = trim(identifier);
    if (!id) {
      return null;
    }
    for (var index = 0; index < modelPreviewState.images.length; index += 1) {
      var entry = modelPreviewState.images[index];
      if (entry.manual && entry.previewOnly && entry.identifier === id
        && entry.previewState === modelPreviewState.previewState) {
        return entry;
      }
    }
    return null;
  }

  function previewTextureRowOverride(row) {
    if (controllerMode === "custom") {
      return previewOnlyImage(row.identifier) ? row.identifier : "";
    }
    var slot = previewTextureRowSlot(row);
    if (!slot) {
      return "";
    }
    return trim(currentPreviewTextureSet()[slot]);
  }

  function onModelPreviewTextureImportClick(event) {
    var button = closest(event.target, "[data-preview-texture-import]");
    if (!button) {
      return;
    }
    var slot = button.getAttribute("data-preview-texture-slot");
    if (!slot && controllerMode !== "custom") {
      return;
    }
    modelPreviewState.textureTarget = slot;
    modelPreviewState.textureTargetId = button.getAttribute("data-preview-texture-id") || "";
    modelPreviewState.textureTargetRow = button.getAttribute("data-preview-texture-import") || "";
    modelPreviewState.textureTargetKey = button.getAttribute("data-preview-texture-key") || "";
    modelPreviewState.textureLabel = button.getAttribute("data-preview-texture-import") || slot;
    var input = $("modelPreviewTextureInput");
    if (input) {
      input.value = "";
      input.click();
    }
  }

  function modelPreviewAnimationNow() {
    return window.performance && window.performance.now
      ? window.performance.now()
      : Date.now();
  }

  function previewTextureTargetIdentifier(slot, targetId, targetKey) {
    if (controllerMode === "custom") {
      var custom = trim(targetId);
      if (custom) {
        return custom;
      }
      var key = trim(targetKey);
      return key ? trim(currentPreviewTextureKeys()[key]) : "";
    }
    var setName = modelPreviewState.previewState;
    if (isPlainObject(state.textures) && isPlainObject(state.textures[setName])) {
      return trim(state.textures[setName][slot]);
    }
    return "";
  }

  function stopModelPreviewAnimation() {
    if (modelPreviewState.animationRaf) {
      window.cancelAnimationFrame(modelPreviewState.animationRaf);
      modelPreviewState.animationRaf = 0;
    }
  }

  function clearPreviewTextureAnimation(identifier) {
    var entry = modelPreviewState.animatedTextures[identifier];
    if (!entry) {
      return;
    }
    delete modelPreviewState.animatedTextures[identifier];
    if (modelPreviewState.textureImages[identifier] === entry.canvas) {
      modelPreviewState.textureImages[identifier] = entry.source;
    }
    if (Object.keys(modelPreviewState.animatedTextures).length === 0) {
      stopModelPreviewAnimation();
    }
  }

  function resetPreviewTextureAnimations() {
    stopModelPreviewAnimation();
    Object.keys(modelPreviewState.animatedTextures).forEach(function (identifier) {
      var entry = modelPreviewState.animatedTextures[identifier];
      if (entry && modelPreviewState.textureImages[identifier] === entry.canvas) {
        modelPreviewState.textureImages[identifier] = entry.source;
      }
    });
    modelPreviewState.animatedTextures = {};
  }

  function drawPreviewAnimationFrame(entry, now, force) {
    var animation = entry.animation;
    var totalTicks = 0;
    var index;
    for (index = 0; index < animation.frames.length; index += 1) {
      totalTicks += animation.frames[index].time;
    }
    if (totalTicks <= 0) {
      return false;
    }
    var elapsed = ((now - entry.startedAt) / 50) % totalTicks;
    var frame = animation.frames[0];
    var next = null;
    var progress = 0;
    var cursor = 0;
    for (index = 0; index < animation.frames.length; index += 1) {
      var item = animation.frames[index];
      if (elapsed < cursor + item.time || index === animation.frames.length - 1) {
        frame = item;
        next = animation.frames[(index + 1) % animation.frames.length];
        progress = item.time > 0 ? (elapsed - cursor) / item.time : 0;
        if (index === animation.frames.length - 1 && elapsed >= cursor + item.time) {
          progress = 1;
        }
        break;
      }
      cursor += item.time;
    }
    var changed = force || entry.frameIndex !== frame.index ||
      (animation.interpolate && entry.frameProgress !== progress);
    if (!changed) {
      return false;
    }
    entry.frameIndex = frame.index;
    entry.frameProgress = progress;
    var context = entry.context;
    context.clearRect(0, 0, animation.width, animation.height);
    context.globalAlpha = 1;
    context.drawImage(
      entry.source,
      0,
      frame.index * animation.height,
      animation.width,
      animation.height,
      0,
      0,
      animation.width,
      animation.height
    );
    if (animation.interpolate && next && next.index !== frame.index && progress > 0 && progress < 1) {
      context.globalAlpha = progress;
      context.drawImage(
        entry.source,
        0,
        next.index * animation.height,
        animation.width,
        animation.height,
        0,
        0,
        animation.width,
        animation.height
      );
      context.globalAlpha = 1;
    }
    return true;
  }

  function startModelPreviewAnimation() {
    if (modelPreviewState.animationRaf || Object.keys(modelPreviewState.animatedTextures).length === 0) {
      return;
    }
    function tick(now) {
      if (Object.keys(modelPreviewState.animatedTextures).length === 0) {
        modelPreviewState.animationRaf = 0;
        return;
      }
      var stage = $("modelPreviewStage");
      if (!stage || stage.offsetParent === null) {
        modelPreviewState.animationRaf = 0;
        return;
      }
      var changed = false;
      Object.keys(modelPreviewState.animatedTextures).forEach(function (identifier) {
        var entry = modelPreviewState.animatedTextures[identifier];
        if (entry && drawPreviewAnimationFrame(entry, now, false)) {
          changed = true;
        }
      });
      if (changed) {
        drawModelPreview();
      }
      modelPreviewState.animationRaf = window.requestAnimationFrame(tick);
    }
    modelPreviewState.animationRaf = window.requestAnimationFrame(tick);
  }

  function applyPreviewTextureAnimation(identifier, source, animation) {
    clearPreviewTextureAnimation(identifier);
    var canvas = document.createElement("canvas");
    canvas.width = animation.width;
    canvas.height = animation.height;
    var context = canvas.getContext("2d");
    context.imageSmoothingEnabled = false;
    var entry = {
      source: source,
      canvas: canvas,
      context: context,
      animation: animation,
      startedAt: modelPreviewAnimationNow(),
      frameIndex: -1,
      frameProgress: -1
    };
    modelPreviewState.animatedTextures[identifier] = entry;
    modelPreviewState.textureImages[identifier] = canvas;
    drawPreviewAnimationFrame(entry, entry.startedAt, true);
    startModelPreviewAnimation();
    renderModelPreviewTextures();
    drawModelPreview();
  }

  function onModelPreviewMcmetaClick(event) {
    var button = closest(event.target, "[data-preview-texture-mcmeta]");
    if (!button) {
      return;
    }
    var slot = button.getAttribute("data-preview-texture-slot");
    if (!slot && controllerMode !== "custom") {
      return;
    }
    modelPreviewState.textureTarget = slot;
    modelPreviewState.textureTargetId = button.getAttribute("data-preview-texture-id") || "";
    modelPreviewState.textureTargetRow = button.getAttribute("data-preview-texture-mcmeta") || "";
    modelPreviewState.textureTargetKey = button.getAttribute("data-preview-texture-key") || "";
    modelPreviewState.textureLabel = button.getAttribute("data-preview-texture-mcmeta") || slot;
    var input = $("modelPreviewMcmetaInput");
    if (input) {
      input.value = "";
      input.click();
    }
  }

  function onModelPreviewMcmetaInputChange(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    var slot = modelPreviewState.textureTarget;
    var targetId = trim(modelPreviewState.textureTargetId);
    var targetKey = trim(modelPreviewState.textureTargetKey);
    var label = modelPreviewState.textureLabel || slot || targetId;
    input.value = "";
    modelPreviewState.textureTarget = "";
    modelPreviewState.textureTargetId = "";
    modelPreviewState.textureTargetRow = "";
    modelPreviewState.textureTargetKey = "";
    modelPreviewState.textureLabel = "";
    if (!file) {
      return;
    }
    if (!/\.mcmeta$/i.test(file.name) && !/json/i.test(file.type)) {
      showToast("Selecione um arquivo .png.mcmeta ou JSON.", "error");
      return;
    }
    if (!ModelPreview || typeof ModelPreview.parseTextureAnimation !== "function") {
      showToast("O preview 3D não carregou; não é possível ler o .mcmeta.", "error");
      return;
    }
    var identifier = previewTextureTargetIdentifier(slot, targetId, targetKey);
    if (!identifier) {
      showToast("Importe o .png antes do .mcmeta.", "error");
      return;
    }
    var current = modelPreviewState.animatedTextures[identifier];
    var source = current ? current.source : modelPreviewState.textureImages[identifier];
    if (!source || !source.width || !source.height) {
      showToast("Importe o .png antes do .mcmeta.", "error");
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var meta;
      try {
        meta = JSON.parse(String(reader.result || "").replace(/^\uFEFF/, ""));
      } catch (error) {
        showToast("Não foi possível ler o .mcmeta: JSON inválido.", "error");
        return;
      }
      var animation = ModelPreview.parseTextureAnimation(meta, source.width, source.height);
      if (!animation) {
        showToast("O .mcmeta não contém uma seção animation válida.", "error");
        return;
      }
      applyPreviewTextureAnimation(identifier, source, animation);
      showToast("Textura " + label + " animada no preview (" + animation.frames.length + " frames).");
    };
    reader.onerror = function () {
      showToast("Falha ao ler o .mcmeta.", "error");
    };
    reader.readAsText(file);
  }

  function onModelPreviewTextureInputChange(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    var slot = modelPreviewState.textureTarget;
    var targetId = trim(modelPreviewState.textureTargetId);
    var targetRow = trim(modelPreviewState.textureTargetRow);
    var targetKey = trim(modelPreviewState.textureTargetKey);
    var label = modelPreviewState.textureLabel || slot || targetRow;
    var custom = controllerMode === "custom";
    input.value = "";
    modelPreviewState.textureTarget = "";
    modelPreviewState.textureTargetId = "";
    modelPreviewState.textureTargetRow = "";
    modelPreviewState.textureTargetKey = "";
    modelPreviewState.textureLabel = "";
    if (!file || (!slot && !targetRow)) {
      return;
    }
    if (!/^image\//i.test(file.type) && !/\.png$/i.test(file.name)) {
      showToast("Selecione um arquivo PNG.", "error");
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var dataUrl = String(reader.result || "");
      var image = new Image();
      image.onload = function () {
        var identifier = custom
          ? (targetId || previewTextureIdentifier(label, file, "minecraft"))
          : previewTextureIdentifier(label, file);
        clearPreviewTextureAnimation(identifier);
        var setName = modelPreviewState.previewState;
        if (custom) {
          if (!targetId && targetKey) {
            currentPreviewTextureKeys()[targetKey] = identifier;
          }
        } else {
          if (!isPlainObject(state.textures)) {
            state.textures = V.createTextures();
          }
          if (!isPlainObject(state.textures[setName])) {
            state.textures[setName] = V.createTextureSet();
          }
          state.textures[setName][slot] = identifier;
        }
        modelPreviewState.images = modelPreviewState.images.filter(function (entry) {
          if (!entry.manual || entry.identifier !== identifier) {
            return true;
          }
          if (!custom) {
            return false;
          }
          return !(entry.previewOnly && entry.previewState === setName);
        });
        modelPreviewState.images.push({
          name: file.name,
          path: file.name,
          dataUrl: dataUrl,
          image: image,
          manual: true,
          previewOnly: custom,
          previewState: setName,
          identifier: identifier
        });
        modelPreviewState.textureImages[identifier] = image;
        if (custom) {
          syncModelPreviewTextures();
          showToast("Textura " + label + " (preview, não vai para o JSON): " + identifier + ".");
        } else {
          refresh({ skipControllerRecipe: true, skipModelPreview: true });
          syncModelPreviewTextures();
          showToast("Textura " + label + (slot !== label ? " (slot " + slot + ")" : "") +
            " (" + setName + "): " + identifier + ".");
        }
      };
      image.onerror = function () {
        showToast("Não foi possível ler a textura.", "error");
      };
      image.src = dataUrl;
    };
    reader.onerror = function () {
      showToast("Falha ao ler a textura.", "error");
    };
    reader.readAsDataURL(file);
  }

  function restoreManualPreviewImage(identifier, onlyPreviewOnly, previewState) {
    if (!identifier) {
      return;
    }
    clearPreviewTextureAnimation(identifier);
    modelPreviewState.images = modelPreviewState.images.filter(function (entry) {
      if (!entry.manual || entry.identifier !== identifier) {
        return true;
      }
      if (previewState && entry.previewOnly && entry.previewState !== previewState) {
        return true;
      }
      return onlyPreviewOnly && !entry.previewOnly;
    });
    var remaining = null;
    var auto = null;
    for (var index = 0; index < modelPreviewState.images.length; index += 1) {
      var entry = modelPreviewState.images[index];
      if (entry.identifier !== identifier) {
        continue;
      }
      if (previewState && entry.previewOnly && entry.previewState !== previewState) {
        continue;
      }
      if (entry.manual && !remaining) {
        remaining = entry;
      } else if (entry.auto && !auto) {
        auto = entry;
      }
    }
    if (remaining) {
      modelPreviewState.textureImages[identifier] = remaining.image;
    } else if (auto) {
      modelPreviewState.textureImages[identifier] = auto.image;
    } else {
      delete modelPreviewState.textureImages[identifier];
    }
  }

  function onModelPreviewTextureClearClick(event) {
    var button = closest(event.target, "[data-preview-texture-clear]");
    if (!button) {
      return;
    }
    var label = button.getAttribute("data-preview-texture-clear");
    var slot = button.getAttribute("data-preview-texture-slot") || previewTextureSlot(label);
    var custom = controllerMode === "custom";
    if (custom) {
      var identifier = trim(button.getAttribute("data-preview-texture-id"));
      var previewState = modelPreviewState.previewState;
      var keys = currentPreviewTextureKeys();
      if (identifier) {
        clearPreviewTextureAnimation(identifier);
        Object.keys(keys).forEach(function (key) {
          if (trim(keys[key]) === identifier) {
            delete keys[key];
          }
        });
      }
      restoreManualPreviewImage(identifier, true, previewState);
      syncModelPreviewTextures();
      showToast("Textura " + label + " restaurada para o padrão do modelo.");
      return;
    }
    if (!slot) {
      return;
    }
    var setName = modelPreviewState.previewState;
    if (!isPlainObject(state.textures) || !isPlainObject(state.textures[setName])) {
      return;
    }
    var identifier = trim(state.textures[setName][slot]);
    clearPreviewTextureAnimation(identifier);
    state.textures[setName][slot] = "";
    restoreManualPreviewImage(identifier);
    refresh({ skipControllerRecipe: true, skipModelPreview: true });
    syncModelPreviewTextures();
    showToast("Textura " + label + (slot !== label ? " (slot " + slot + ")" : "") +
      " restaurada para o padrão.");
  }

  function splitPreviewIdentifier(identifier) {
    var text = trim(identifier);
    var separator = text.indexOf(":");
    if (separator <= 0 || separator === text.length - 1) {
      return null;
    }
    var namespace = text.slice(0, separator);
    var path = text.slice(separator + 1);
    if (!/^[a-z0-9_.-]+$/i.test(namespace) || !/^[a-z0-9_./-]+$/i.test(path)) {
      return null;
    }
    var segments = path.split("/");
    for (var index = 0; index < segments.length; index += 1) {
      if (!segments[index] || segments[index] === "." || segments[index] === "..") {
        return null;
      }
    }
    return { namespace: namespace, path: path };
  }

  function modelPreviewAssetUrl(identifier, folder, extension) {
    var parts = splitPreviewIdentifier(identifier);
    if (!parts) {
      return "";
    }
    return "../../src/main/resources/assets/" + parts.namespace + "/" + folder + "/" + parts.path + extension;
  }

  function modelPreviewNamespace() {
    var entry = previewModelEntry(modelPreviewState.previewState);
    var parts = entry ? splitPreviewIdentifier(entry.id) : null;
    return parts ? parts.namespace : "minecraft";
  }

  function modelPreviewSummaryText() {
    var requested = modelPreviewState.previewState;
    var entry = previewModelEntry(requested);
    if (modelPreviewState.loadingField) {
      return "Carregando " + modelPreviewState.fieldId + " (" + requested + ")...";
    }
    if (!entry) {
      return "Preencha model.idle para visualizar o controller custom.";
    }
    var label = controllerMode === "custom" ? "model" : "controller padrão";
    if (modelPreviewState.source === "local") {
      label += " (arquivo local)";
    }
    var stateLabel = requested + (entry.key !== requested ? " → " + entry.key : "");
    var parsed = modelPreviewState.parsed;
    if (!parsed) {
      return label + " · " + stateLabel + ": " + entry.id;
    }
    var elementCount = parsed.elements.length;
    var textureCount = Array.isArray(parsed.textureRows)
      ? parsed.textureRows.length
      : parsed.entries.length;
    return label + " · " + stateLabel + ": " + entry.id + " · " +
      elementCount + (elementCount === 1 ? " elemento" : " elementos") + " · " +
      textureCount + (textureCount === 1 ? " textura" : " texturas") + ".";
  }

  function clearAutoPreviewImages() {
    modelPreviewState.images = modelPreviewState.images.filter(function (image) {
      return !image.auto;
    });
    modelPreviewState.textureImages = {};
    modelPreviewState.images.forEach(function (image) {
      if (image.manual && image.identifier && image.image && imageAppliesToPreviewState(image)) {
        modelPreviewState.textureImages[image.identifier] = image.image;
      }
    });
  }

  function loadModelPreviewAutoTextures(parsed, request) {
    var pending = 0;
    var finish = function () {
      pending -= 1;
      if (pending <= 0 && request === modelPreviewState.fieldRequest) {
        modelPreviewState.loadingField = false;
        renderModelPreview();
      }
    };
    parsed.entries.forEach(function (entry) {
      if (entry.vanilla || modelPreviewState.textureImages[entry.identifier]) {
        return;
      }
      if (modelPreviewState.images.some(function (image) {
        return image.auto && image.identifier === entry.identifier;
      })) {
        return;
      }
      var parts = splitPreviewIdentifier(entry.identifier);
      var url = modelPreviewAssetUrl(entry.identifier, "textures", ".png");
      if (!parts || !url) {
        return;
      }
      pending += 1;
      var image = new Image();
      image.onload = function () {
        if (request === modelPreviewState.fieldRequest) {
          modelPreviewState.images.push({
            name: parts.path.split("/").pop() + ".png",
            path: parts.path + ".png",
            dataUrl: url,
            image: image,
            auto: true,
            identifier: entry.identifier
          });
          modelPreviewState.textureImages[entry.identifier] = image;
        }
        finish();
      };
      image.onerror = finish;
      image.src = url;
    });
    if (pending === 0) {
      modelPreviewState.loadingField = false;
      renderModelPreview();
    }
  }

  function loadModelPreviewFromField(force) {
    var entry = previewModelEntry(modelPreviewState.previewState);
    if (!entry) {
      clearModelPreview(true);
      if (controllerMode === "custom") {
        modelPreviewState.error = "Preencha model.idle para visualizar o controller custom.";
        renderModelPreview();
      }
      return;
    }
    if (!force && modelPreviewState.source === "field" && modelPreviewState.fieldId === entry.id) {
      return;
    }
    var local = modelPreviewState.localModels[entry.key];
    if (local) {
      if (!force && modelPreviewState.source === "local" && modelPreviewState.fieldId === entry.id) {
        return;
      }
      var localRequest = modelPreviewState.fieldRequest + 1;
      modelPreviewState.fieldRequest = localRequest;
      modelPreviewState.source = "local";
      modelPreviewState.fieldId = entry.id;
      modelPreviewState.fieldKey = entry.key;
      modelPreviewState.loadingField = false;
      modelPreviewState.fileName = entry.id;
      modelPreviewState.error = "";
      modelPreviewState.baseParsed = local;
      modelPreviewState.parsed = applyPreviewTextureOverrides(local);
      clearAutoPreviewImages();
      autoMatchModelPreviewTextures();
      loadModelPreviewAutoTextures(modelPreviewState.parsed, localRequest);
      renderModelPreview();
      return;
    }
    var parts = splitPreviewIdentifier(entry.id);
    if (!parts || typeof fetch !== "function") {
      modelPreviewState.error = typeof fetch === "function"
        ? "Modelo inválido no campo " + entry.key + "."
        : "Carregamento automático indisponível; sirva o builder por HTTP.";
      modelPreviewState.parsed = null;
      modelPreviewState.baseParsed = null;
      modelPreviewState.source = "none";
      renderModelPreview();
      return;
    }
    var request = modelPreviewState.fieldRequest + 1;
    modelPreviewState.fieldRequest = request;
    modelPreviewState.source = "field";
    modelPreviewState.fieldId = entry.id;
    modelPreviewState.fieldKey = entry.key;
    modelPreviewState.loadingField = true;
    modelPreviewState.error = "";
    renderModelPreview();
    var url = modelPreviewAssetUrl(entry.id, "models", ".json");
    fetch(url, { cache: "no-store" }).then(function (response) {
      if (!response.ok) {
        throw new Error("HTTP " + response.status);
      }
      return response.text();
    }).then(function (text) {
      if (request !== modelPreviewState.fieldRequest) {
        return;
      }
      var parsed = ModelPreview.parseModel(text, { namespace: parts.namespace });
      modelPreviewState.baseParsed = parsed;
      modelPreviewState.parsed = applyPreviewTextureOverrides(parsed);
      modelPreviewState.fileName = entry.id;
      modelPreviewState.error = "";
      clearAutoPreviewImages();
      autoMatchModelPreviewTextures();
      loadModelPreviewAutoTextures(modelPreviewState.parsed, request);
      renderModelPreview();
    }).catch(function (error) {
      if (request !== modelPreviewState.fieldRequest) {
        return;
      }
      modelPreviewState.loadingField = false;
      modelPreviewState.parsed = null;
      modelPreviewState.baseParsed = null;
      modelPreviewState.source = "none";
      modelPreviewState.fieldId = "";
      modelPreviewState.fieldKey = "";
      var reason = error && error.message ? error.message : String(error);
      modelPreviewState.error = /HTTP 404|Not Found/i.test(reason)
        ? "Modelo " + entry.id +
          " não está publicado no site. Use importar no campo " + entry.key +
          " para carregar o JSON do seu computador."
        : "Não foi possível carregar " + entry.id + " (" + reason + ").";
      renderModelPreview();
    });
  }

  function cancelModelPreviewFieldLoad() {
    if (modelPreviewFieldTimer) {
      window.clearTimeout(modelPreviewFieldTimer);
      modelPreviewFieldTimer = null;
    }
  }

  function scheduleModelPreviewFieldLoad() {
    cancelModelPreviewFieldLoad();
    modelPreviewFieldTimer = window.setTimeout(function () {
      modelPreviewFieldTimer = null;
      loadModelPreviewFromField(false);
    }, 350);
  }

  function drawModelPreview() {
    var canvas = $("modelPreviewCanvas");
    if (!canvas || !ModelPreview) {
      return;
    }
    var stage = $("modelPreviewStage");
    if (stage && stage.offsetParent === null) {
      return;
    }
    ModelPreview.draw(canvas, modelPreviewState.parsed, {
      yaw: modelPreviewState.view.yaw,
      pitch: modelPreviewState.view.pitch,
      zoom: modelPreviewState.view.zoom,
      showFloor: modelPreviewState.showFloor,
      showReference: modelPreviewState.showReference,
      textureImages: modelPreviewState.textureImages
    });
    var empty = $("modelPreviewEmpty");
    if (empty) {
      empty.hidden = Boolean(modelPreviewState.parsed);
    }
    startModelPreviewAnimation();
  }

  function renderModelPreviewTextures() {
    var list = $("modelPreviewTextureList");
    if (!list) {
      return;
    }
    var parsed = modelPreviewState.parsed;
    if (!parsed) {
      list.innerHTML = "";
      return;
    }
    var custom = controllerMode === "custom";
    var rows = custom
      ? customPreviewTextureRows(parsed)
      : (Array.isArray(parsed.textureRows) ? parsed.textureRows : []);
    if (rows.length === 0) {
      if (custom) {
        list.innerHTML = "<p class='field-hint'>O modelo não declara texturas em <code>textures</code>.</p>";
        return;
      }
      rows = parsed.entries.map(function (entry) {
        return {
          key: entry.keys.join(", "),
          raw: "",
          identifier: entry.identifier,
          vanilla: entry.vanilla
        };
      });
    }
    if (rows.length === 0) {
      list.innerHTML = "<p class='field-hint'>O modelo não declara texturas próprias; as faces usam placeholders.</p>";
      return;
    }
    list.innerHTML = rows.map(function (row) {
      var image = row.identifier ? modelPreviewState.textureImages[row.identifier] : null;
      var animationEntry = row.identifier ? modelPreviewState.animatedTextures[row.identifier] : null;
      var animated = Boolean(animationEntry);
      var sourceImage = animationEntry ? animationEntry.source : image;
      var imageEntry = null;
      if (sourceImage) {
        for (var index = 0; index < modelPreviewState.images.length; index += 1) {
          if (modelPreviewState.images[index].image === sourceImage) {
            imageEntry = modelPreviewState.images[index];
            break;
          }
        }
      }
      var matched = Boolean(image);
      var tone = row.vanilla ? "ok" : matched ? "ok" : "error";
      var toneAttribute = tone ? " data-tone='" + tone + "'" : "";
      var label = animated ? "animada" : row.vanilla ? "vanilla" : matched ? "resolvida" : "sem png";
      var thumbnail = imageEntry && imageEntry.dataUrl
        ? "<img class='model-preview-thumb' alt='' src='" + esc(imageEntry.dataUrl) + "'>"
        : "<span class='model-preview-thumb is-empty'></span>";
      var reference = row.raw && row.raw !== row.identifier
        ? "<span class='model-preview-texture-ref'>" + esc(row.raw) + "</span>"
        : "";
      var slot = custom ? "" : previewTextureRowSlot(row);
      var canImport = custom || Boolean(slot);
      var override = canImport ? previewTextureRowOverride(row) : "";
      var textureKey = row.textureKey || "";
      var actions = canImport
        ? "<div class='model-preview-texture-actions'>" +
          "<button type='button' class='btn btn-tiny' data-preview-texture-import='" +
          esc(row.key) + "' data-preview-texture-slot='" + esc(slot) +
          "' data-preview-texture-id='" + esc(row.identifier || "") +
          "' data-preview-texture-key='" + esc(textureKey) + "'>importar .png</button>" +
          "<button type='button' class='btn btn-tiny' data-preview-texture-mcmeta='" +
          esc(row.key) + "' data-preview-texture-slot='" + esc(slot) +
          "' data-preview-texture-id='" + esc(row.identifier || "") +
          "' data-preview-texture-key='" + esc(textureKey) + "'>.mcmeta</button>" +
          (override
            ? "<button type='button' class='btn btn-tiny btn-danger' data-preview-texture-clear='" +
              esc(row.key) + "' data-preview-texture-slot='" + esc(slot) +
              "' data-preview-texture-id='" + esc(row.identifier || "") +
              "' data-preview-texture-key='" + esc(textureKey) + "'>limpar</button>"
            : "") +
          "</div>"
        : "";
      return "<div class='model-preview-texture-row'>" +
        thumbnail +
        "<div class='model-preview-texture-info'>" +
        "<span class='model-preview-texture-keys'>" + esc(row.key) + reference + "</span>" +
        "<code>" + esc(row.identifier || "sem textura") + "</code>" +
        "</div>" +
        "<div class='model-preview-texture-side'>" +
        "<span class='badge'" + toneAttribute + ">" + label + "</span>" +
        actions +
        "</div>" +
        "</div>";
    }).join("");
  }

  function renderModelPreviewStateButtons() {
    $$("[data-preview-state]").forEach(function (button) {
      var key = button.getAttribute("data-preview-state");
      var entry = previewModelEntry(key);
      button.classList.toggle("is-active", key === modelPreviewState.previewState);
      button.disabled = !entry;
      button.title = entry && entry.key !== key ? "Usa " + entry.key : "";
    });
  }

  function onModelPreviewStateClick(event) {
    var button = closest(event.target, "[data-preview-state]");
    if (!button || button.disabled) {
      return;
    }
    var key = button.getAttribute("data-preview-state");
    if (MODEL_KEYS.indexOf(key) < 0 || key === modelPreviewState.previewState) {
      return;
    }
    modelPreviewState.previewState = key;
    loadModelPreviewFromField(true);
  }

  function renderModelPreview() {
    var feedback = $("modelPreviewFeedback");
    if (!ModelPreview) {
      if (feedback) {
        feedback.textContent = "O preview 3D não carregou (modelpreview3d.js). " +
          "Confira se o arquivo foi publicado junto do builder.";
        feedback.hidden = false;
      }
      return;
    }
    var summary = $("modelPreviewSummary");
    if (summary) {
      summary.textContent = modelPreviewSummaryText();
    }
    var problem = modelPreviewState.error;
    if (!problem && modelPreviewState.parsed && modelPreviewState.parsed.missingParent) {
      problem = "Parent não encontrado: " + modelPreviewState.parsed.missingParent +
        ". Usei o cubo completo como fallback.";
    }
    if (!problem && modelPreviewState.parsed && modelPreviewState.parsed.usedFallbackCube) {
      problem = "Sem elements no modelo ou no parent; usei um cubo completo.";
    }
    if (feedback) {
      feedback.textContent = problem;
      feedback.hidden = !problem;
      feedback.className = "matcher-feedback controller-feedback" +
        (modelPreviewState.error ? " is-error" : "");
    }
    var floor = $("modelPreviewFloor");
    if (floor) {
      floor.checked = modelPreviewState.showFloor;
    }
    var reference = $("modelPreviewReference");
    if (reference) {
      reference.checked = modelPreviewState.showReference;
    }
    renderModelPreviewStateButtons();
    renderModelPreviewTextures();
    drawModelPreview();
  }

  function matchPreviewImage(entry) {
    var identifier = entry.identifier;
    var pathPart = identifier.slice(identifier.indexOf(":") + 1);
    var normalizedPath = pathPart.toLowerCase();
    var baseName = normalizedPath.split("/").pop();
    var best = null;
    modelPreviewState.images.forEach(function (image) {
      if (image.previewOnly) {
        return;
      }
      var name = String(image.name || "").toLowerCase();
      var imagePath = String(image.path || "").replace(/\\/g, "/").toLowerCase();
      var suffix = "/textures/" + normalizedPath + ".png";
      if (imagePath === suffix.slice(1) || imagePath.slice(-suffix.length) === suffix) {
        best = image;
        return;
      }
      if (!best && name.replace(/\.png$/, "") === baseName) {
        best = image;
      }
    });
    return best;
  }

  function autoMatchModelPreviewTextures() {
    var parsed = modelPreviewState.parsed;
    if (!parsed) {
      return;
    }
    parsed.entries.forEach(function (entry) {
      if (modelPreviewState.textureImages[entry.identifier]) {
        return;
      }
      var match = matchPreviewImage(entry);
      if (match) {
        modelPreviewState.textureImages[entry.identifier] = match.image;
      }
    });
  }

  function resetPreviewTextureOverrides() {
    resetPreviewTextureAnimations();
    var identifiers = {};
    modelPreviewState.images.forEach(function (entry) {
      if (entry.manual && entry.previewOnly) {
        identifiers[entry.identifier] = true;
      }
    });
    modelPreviewState.images = modelPreviewState.images.filter(function (entry) {
      return !(entry.manual && entry.previewOnly);
    });
    Object.keys(identifiers).forEach(function (identifier) {
      var remaining = null;
      var auto = null;
      modelPreviewState.images.forEach(function (entry) {
        if (entry.identifier !== identifier) {
          return;
        }
        if (entry.manual && !remaining) {
          remaining = entry;
        } else if (entry.auto && !auto) {
          auto = entry;
        }
      });
      if (remaining) {
        modelPreviewState.textureImages[identifier] = remaining.image;
      } else if (auto) {
        modelPreviewState.textureImages[identifier] = auto.image;
      } else {
        delete modelPreviewState.textureImages[identifier];
      }
    });
    modelPreviewState.previewTextureKeys = { idle: {}, active: {}, building: {} };
    modelPreviewState.textureTarget = "";
    modelPreviewState.textureTargetId = "";
    modelPreviewState.textureTargetRow = "";
    modelPreviewState.textureTargetKey = "";
    modelPreviewState.textureLabel = "";
  }

  function clearModelPreview(silent) {
    cancelModelPreviewFieldLoad();
    resetPreviewTextureOverrides();
    modelPreviewState.fieldRequest += 1;
    modelPreviewState.loadingField = false;
    modelPreviewState.parsed = null;
    modelPreviewState.baseParsed = null;
    modelPreviewState.fileName = "";
    modelPreviewState.source = "none";
    modelPreviewState.fieldId = "";
    modelPreviewState.fieldKey = "";
    modelPreviewState.images = [];
    modelPreviewState.textureImages = {};
    modelPreviewState.error = "";
    renderModelPreview();
    if (!silent) {
      showToast("Preview do modelo limpo.");
    }
  }

  function resetModelPreviewView() {
    modelPreviewState.view = { yaw: Math.PI * 5 / 4, pitch: 0.5, zoom: 1 };
    drawModelPreview();
  }

  function onModelPreviewOptionChange() {
    var floor = $("modelPreviewFloor");
    var reference = $("modelPreviewReference");
    modelPreviewState.showFloor = floor ? floor.checked : true;
    modelPreviewState.showReference = reference ? reference.checked : true;
    drawModelPreview();
  }

  function beginModelPreviewDrag(event) {
    var canvas = $("modelPreviewCanvas");
    if (!canvas || event.button !== 0) {
      return;
    }
    if (canvas.setPointerCapture) {
      canvas.setPointerCapture(event.pointerId);
    }
    canvas.classList.add("is-dragging");
    modelPreviewState.dragging = true;
    modelPreviewState.lastX = event.clientX;
    modelPreviewState.lastY = event.clientY;
  }

  function moveModelPreviewDrag(event) {
    if (!modelPreviewState.dragging) {
      return;
    }
    var dx = event.clientX - modelPreviewState.lastX;
    var dy = event.clientY - modelPreviewState.lastY;
    modelPreviewState.lastX = event.clientX;
    modelPreviewState.lastY = event.clientY;
    modelPreviewState.view.yaw += dx * 0.01;
    modelPreviewState.view.pitch = clamp(modelPreviewState.view.pitch + dy * 0.01, -1.45, 1.45);
    drawModelPreview();
  }

  function endModelPreviewDrag(event) {
    var canvas = $("modelPreviewCanvas");
    if (!canvas) {
      return;
    }
    if (canvas.hasPointerCapture && canvas.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
    canvas.classList.remove("is-dragging");
    modelPreviewState.dragging = false;
  }

  function onModelPreviewWheel(event) {
    event.preventDefault();
    modelPreviewState.view.zoom = clamp(
      modelPreviewState.view.zoom * Math.exp(-event.deltaY * 0.0015), 0.35, 4);
    drawModelPreview();
  }

  function drawPreview() {
    if (!MotionPreview || !state) {
      return;
    }
    MotionPreview.draw($("patternPreview3d"), motionPreviewScene(railPreviewState.time, {
      view: RAIL_PREVIEW_VIEW,
      selectedMotionId: "",
      hiddenSymbols: {},
      opacity: 1
    }));
  }

  function refresh(options) {
    if (!state) {
      return;
    }
    var settings = options || {};
    report = V.validateState(state);
    definition = V.buildDefinition(state);
    jsonText = JSON.stringify(definition, null, 2);
    currentUsage = collectUsage();
    currentColors = buildColorMap(currentUsage);
    ensureActiveSymbol();
    renderHeader();
    renderNav();
    renderStepVisibility();
    renderIdentityMeta();
    renderFieldErrors();
    renderExport();
    renderInspector();
    if (activeStep === "pattern" && !settings.skipPattern) {
      renderPattern();
    }
    if (activeStep === "matchers" && !settings.skipMatchers) {
      renderMatchers();
    }
    if (activeStep === "upgrades" && !settings.skipUpgrades) {
      renderUpgrades();
    }
    if (activeStep === "motions" && !settings.skipMotions) {
      renderMotionMode();
      renderMotions();
    }
    if (activeStep === "slots" && !settings.skipSlots) {
      renderSlots();
    }
    if (activeStep === "capabilities" && !settings.skipCapabilities) {
      renderCapabilities();
    }
    if (activeStep === "controller" && !settings.skipControllerMode) {
      renderControllerMode();
    }
    if (activeStep === "controller" && !settings.skipModel) {
      renderModel();
    }
    if (activeStep === "controller" && !settings.skipShape) {
      renderShape();
    }
    if (activeStep === "controller" && !settings.skipModelPreview) {
      renderModelPreview();
    }
    if (activeStep === "controller" && !settings.skipControllerRecipe) {
      renderControllerRecipe();
    }
    if (activeStep === "gui" && !settings.skipGui) {
      renderGui();
    }
    if (activeStep === "sounds" && !settings.skipSounds) {
      renderSounds();
    }
    if (activeStep === "recipes" && !settings.skipRecipes) {
      renderRecipes();
    }
    if (activeStep === "inheritance" && !settings.skipInheritance) {
      renderInheritance();
    }
    if (activeStep === "review") {
      renderReview();
    }
    renderSlotGuiPreview(Array.isArray(state.itemSlots) ? state.itemSlots : null);
    drawPreview();
    scheduleSave();
  }

  function scrollToTop(smooth) {
    try {
      window.scrollTo({ top: 0, behavior: smooth ? "smooth" : "auto" });
    } catch (error) {
      window.scrollTo(0, 0);
    }
  }

  function setStep(stepId, options) {
    if (STEP_IDS.indexOf(stepId) === -1) {
      return;
    }
    if (stepId !== "pattern" && isPatternFullscreen()) {
      setPatternFullscreen(false);
    }
    if (activeStep === "motions" && stepId !== "motions") {
      setMotionFullscreen(false);
    }
    activeStep = stepId;
    refresh();
    if (stepId === "motions") {
      stopRailPreviewLoop();
      startMotionLoop();
    } else {
      stopMotionLoop();
      startRailPreviewLoop();
    }
    if (stepId === "controller" && modelPreviewState.source === "none") {
      scheduleModelPreviewFieldLoad();
    }
    if (!options || !options.preserveScroll) {
      scrollToTop(true);
    }
  }

  function focusElement(element, select) {
    if (!element) {
      return;
    }
    element.scrollIntoView({ block: "center", behavior: "smooth" });
    try {
      element.focus({ preventScroll: true });
    } catch (error) {
      element.focus();
    }
    if (select && element.tagName === "INPUT" && typeof element.select === "function") {
      element.select();
    }
  }

  function focusField(field) {
    if (!field) {
      return;
    }
    if (field.indexOf("matcher:") === 0) {
      var symbol = field.slice("matcher:".length);
      var card = findMatcherCard(symbol);
      if (card) {
        var input = card.querySelector("input, select");
        focusElement(input || card, false);
      }
      return;
    }
    if (field.indexOf("slot:") === 0) {
      var slotIndex = parseInt(field.slice("slot:".length), 10);
      var slotCard = findSlotCard(slotIndex);
      if (slotCard) {
        var slotInput = slotCard.querySelector("[data-field='id']");
        focusElement(slotInput || slotCard, false);
      }
      return;
    }
    if (field === "itemSlots") {
      focusElement($("slotsPanel"), false);
      return;
    }
    if (field === "upgrades") {
      focusElement($("upgradeModePanel"), false);
      return;
    }
    if (field === "upgradeKey") {
      focusElement($("upgradeKey"), true);
      return;
    }
    if (field.indexOf("upgradeBooster:") === 0) {
      var boosterIndex = parseInt(field.slice("upgradeBooster:".length), 10);
      var boosterCard = document.querySelector(".upgrade-card[data-booster-index='" + boosterIndex + "']");
      if (boosterCard) {
        focusElement(boosterCard.querySelector("input, select") || boosterCard, false);
      }
      return;
    }
    if (field.indexOf("recipe:") === 0) {
      var recipeParts = field.slice("recipe:".length).split(":");
      var recipeIndex = parseInt(recipeParts[0], 10);
      var recipeField = recipeParts.slice(1).join(":");
      var recipeCard = recipeCardElement(recipeIndex);
      var recipeInput = null;
      if (recipeCard && (recipeParts[1] === "input" || recipeParts[1] === "output")) {
        var recipeSection = recipeParts[1];
        var recipeRowIndex = parseInt(recipeParts[2], 10);
        var recipeRowField = recipeParts.slice(3).join(":") || "resource";
        recipeInput = recipeCard.querySelector(
          "[data-recipe-" + recipeSection + "-index='" + recipeRowIndex + "'] " +
          "[data-recipe-" + recipeSection + "-field='" + recipeRowField + "']");
        if (!recipeInput) {
          recipeInput = recipeCard.querySelector(
            "[data-recipe-" + recipeSection + "-index='" + recipeRowIndex + "']");
        }
      } else if (recipeField === "inputs") {
        recipeInput = recipeCard ? recipeCard.querySelector("[data-recipe-add-input]") : null;
      } else if (recipeField === "outputs") {
        recipeInput = recipeCard ? recipeCard.querySelector("[data-recipe-add-output]") : null;
      } else if (recipeCard && recipeField) {
        recipeInput = recipeCard.querySelector("[data-recipe-field='" + recipeField + "']");
      }
      if (!recipeInput && recipeCard) {
        recipeInput = recipeCard;
      }
      var recipeDetails = recipeInput ? closest(recipeInput, "details.card-advanced") : null;
      if (recipeDetails) {
        recipeDetails.open = true;
      }
      focusElement(recipeInput || $("recipeList"), false);
      return;
    }
    if (field === "controller") {
      focusElement($("controllerModePanel"), false);
      return;
    }
    if (field === "controllerRecipe") {
      focusElement($("controllerRecipePanel"), false);
      return;
    }
    if (field === "controllerRecipePattern") {
      focusElement($("controllerRecipeGrid"), false);
      return;
    }
    if (field === "controllerRecipeKey") {
      focusElement($("controllerRecipeKeyList"), false);
      return;
    }
    if (field === "gui") {
      focusElement($("guiPanel"), false);
      return;
    }
    if (field === "sounds") {
      focusElement($("soundsPanel"), false);
      return;
    }
    if (field === "soundProcessing") {
      focusElement($("soundProcessing"), true);
      return;
    }
    if (field === "soundFinish") {
      focusElement($("soundFinish"), true);
      return;
    }
    if (field === "guiTexture") {
      focusElement(guiElementField("texture"), true);
      return;
    }
    if (field === "guiWidth" || field === "guiHeight" ||
      field === "guiTextureWidth" || field === "guiTextureHeight") {
      focusElement(guiElementField(field.slice("gui".length).charAt(0).toLowerCase() +
        field.slice("gui".length + 1)), true);
      return;
    }
    if (field.indexOf("guiElement:") === 0) {
      var guiElementIndex = parseInt(field.slice("guiElement:".length), 10);
      var guiCard = guiElementCardElement(guiElementIndex);
      var guiInput = guiCard ? guiCard.querySelector("input, select") : null;
      focusElement(guiInput || guiCard, false);
      return;
    }
    if (field === "capabilities") {
      focusElement($("capabilitiesPanel"), false);
      return;
    }
    if (field === "capabilityPorts") {
      focusElement($("capabilityPortsPanel"), false);
      return;
    }
    if (field.indexOf("capabilityPorts:") === 0) {
      var portKey = field.slice("capabilityPorts:".length);
      focusElement(document.querySelector("[data-capability-port='" + portKey + "']") ||
        $("capabilityPortsPanel"), true);
      return;
    }
    if (field === "energy") {
      var energyCard = energyCardElement();
      var energyInput = energyCard ? energyCard.querySelector("[data-energy-field='id']") : null;
      focusElement(energyInput || energyCard, false);
      return;
    }
    if (field.indexOf("fluid:") === 0) {
      var fluidIndex = parseInt(field.slice("fluid:".length), 10);
      var fluidCard = fluidCardElement(fluidIndex);
      var fluidInput = fluidCard ? fluidCard.querySelector("[data-fluid-field='id']") : null;
      focusElement(fluidInput || fluidCard, false);
      return;
    }
    if (field === "inheritanceParent") {
      focusElement($("inheritanceParent"), true);
      return;
    }
    if (field === "inheritanceMode") {
      focusElement($("inheritanceMode"), false);
      return;
    }
    if (field === "inheritanceTemplate") {
      focusElement($("inheritanceTemplate"), false);
      return;
    }
    if (field === "inheritanceOverrides") {
      focusElement($("inheritanceOverrides"), false);
      return;
    }
    if (EXTRAS_FIELDS.indexOf(field) !== -1) {
      focusElement($("extrasPanel"), false);
      return;
    }
    var target = FIELD_TARGETS[field];
    if (target) {
      focusElement($(target), field !== "pattern" && field !== "anchor");
    }
  }

  function addLayer() {
    if (getDimensions().height >= 32) {
      showToast("O limite desta interface é 32 camadas.", "error");
      return;
    }
    var dimensions = getDimensions();
    var layer = [];
    for (var row = 0; row < dimensions.rows; row += 1) {
      layer.push(" ".repeat(dimensions.width));
    }
    state.layers.push(layer);
    activeLayer = state.layers.length - 1;
    syncInputs();
    refresh();
  }

  function duplicateLayer() {
    if (getDimensions().height >= 32) {
      showToast("O limite desta interface é 32 camadas.", "error");
      return;
    }
    var source = state.layers[activeLayer];
    var copy = Array.isArray(source) ? source.slice() : [];
    state.layers.splice(activeLayer + 1, 0, copy);
    activeLayer += 1;
    syncInputs();
    refresh();
  }

  function removeLayer() {
    if (state.layers.length <= 1) {
      return;
    }
    var layer = Array.isArray(state.layers[activeLayer]) ? state.layers[activeLayer] : [];
    var hasContent = layer.some(function (row) {
      return typeof row === "string" && /[^ ]/.test(row);
    });
    if (hasContent && !window.confirm("Remover a camada Y" + activeLayer + "? O conteúdo dela será perdido.")) {
      return;
    }
    state.layers.splice(activeLayer, 1);
    activeLayer = clamp(activeLayer, 0, state.layers.length - 1);
    syncInputs();
    refresh();
  }

  function onSizeChange(axis) {
    var input = axis === "X" ? $("sizeX") : axis === "Z" ? $("sizeZ") : $("sizeY");
    var dimensions = getDimensions();
    var fallback = axis === "X" ? dimensions.width : axis === "Z" ? dimensions.rows : dimensions.height;
    var value = parseIntStrict(input.value);
    var maximum = axis === "Y" ? 32 : 64;
    if (value === null) {
      input.value = String(fallback);
      return;
    }
    value = clamp(value, 1, maximum);
    if (value === fallback) {
      input.value = String(value);
      return;
    }
    resizePattern(axis, value);
    syncInputs();
    refresh();
  }

  function onAnchorChange() {
    var input = $("anchorSymbol");
    var value = input.value.trim();
    if (!V.isSingleSymbol(value) || value === " ") {
      showToast("A âncora precisa ser um único caractere diferente de espaço.", "error");
      input.value = state.anchor;
      return;
    }
    if (value !== state.anchor) {
      state.anchor = value;
      refresh();
    }
  }

  function paintCell(row, column, symbol) {
    var dimensions = getDimensions();
    if (row < 0 || column < 0 || row >= dimensions.rows || column >= dimensions.width) {
      return;
    }
    var layer = state.layers[activeLayer];
    if (!Array.isArray(layer)) {
      return;
    }
    var current = typeof layer[row] === "string" ? layer[row] : "";
    if (current.length < dimensions.width) {
      current += " ".repeat(dimensions.width - current.length);
    }
    var existing = current.charAt(column);
    if (existing === symbol) {
      return;
    }
    if (symbol === state.anchor && existing !== state.anchor && countSymbol(state.anchor) > 0) {
      showToast("Já existe uma âncora. Apague a atual antes de posicionar outra.", "error");
      return;
    }
    layer[row] = current.slice(0, column) + symbol + current.slice(column + 1);
    var grid = $("layerGrid");
    var button = grid ? grid.querySelector(".cell[data-row='" + row + "'][data-col='" + column + "']") : null;
    if (button) {
      applyCellClasses(button, row, column, symbol);
    }
    refresh({ skipPattern: true, skipMatchers: true });
    renderPalette();
    renderLegend();
    drawPreview();
  }

  function paintFromCell(cell, button) {
    var row = parseInt(cell.getAttribute("data-row"), 10);
    var column = parseInt(cell.getAttribute("data-col"), 10);
    var symbol = button === 2 || activeSymbol === ERASER ? ERASER : activeSymbol;
    if (symbol === null) {
      return;
    }
    paintCell(row, column, symbol);
  }

  function endPaint() {
    dragging = false;
    dragButton = 0;
    lastPaintKey = "";
  }

  function onGridPointerDown(event) {
    var cell = closest(event.target, ".cell");
    if (!cell || (event.button !== 0 && event.button !== 2)) {
      return;
    }
    event.preventDefault();
    dragging = true;
    dragButton = event.button;
    lastPaintKey = cell.getAttribute("data-row") + ":" + cell.getAttribute("data-col");
    try {
      cell.focus({ preventScroll: true });
    } catch (error) {
      cell.focus();
    }
    paintFromCell(cell, dragButton);
  }

  function onGridPointerMove(event) {
    if (!dragging) {
      return;
    }
    if (event.pointerType !== "mouse" && event.cancelable) {
      event.preventDefault();
    }
    var element = document.elementFromPoint(event.clientX, event.clientY);
    var cell = element && element.closest ? element.closest(".cell") : null;
    if (!cell) {
      return;
    }
    var key = cell.getAttribute("data-row") + ":" + cell.getAttribute("data-col");
    if (key === lastPaintKey) {
      return;
    }
    lastPaintKey = key;
    paintFromCell(cell, dragButton);
  }

  function onGridKeyDown(event) {
    if (event.altKey || event.ctrlKey || event.metaKey) {
      return;
    }
    var cell = closest(event.target, ".cell");
    var dimensions = getDimensions();
    var row = cell ? parseInt(cell.getAttribute("data-row"), 10) : 0;
    var column = cell ? parseInt(cell.getAttribute("data-col"), 10) : 0;
    var nextRow = row;
    var nextColumn = column;
    if (event.key === "ArrowUp") {
      nextRow = Math.max(0, row - 1);
    } else if (event.key === "ArrowDown") {
      nextRow = Math.min(dimensions.rows - 1, row + 1);
    } else if (event.key === "ArrowLeft") {
      nextColumn = Math.max(0, column - 1);
    } else if (event.key === "ArrowRight") {
      nextColumn = Math.min(dimensions.width - 1, column + 1);
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (cell) {
        paintCell(row, column, activeSymbol);
      } else {
        focusCell(0, 0);
      }
      return;
    } else if (event.key === "Backspace" || event.key === "Delete") {
      event.preventDefault();
      if (cell) {
        paintCell(row, column, ERASER);
      }
      return;
    } else {
      return;
    }
    event.preventDefault();
    focusCell(nextRow, nextColumn);
  }

  function focusCell(row, column) {
    var grid = $("layerGrid");
    if (!grid) {
      return;
    }
    var cell = grid.querySelector(".cell[data-row='" + row + "'][data-col='" + column + "']");
    if (!cell) {
      return;
    }
    try {
      cell.focus({ preventScroll: true });
    } catch (error) {
      cell.focus();
    }
  }

  function onGridFocusIn(event) {
    var cell = closest(event.target, ".cell");
    $$(".cell.is-focused").forEach(function (entry) {
      entry.classList.remove("is-focused");
    });
    if (cell) {
      cell.classList.add("is-focused");
    }
  }

  function onGridFocusOut() {
    window.setTimeout(function () {
      var grid = $("layerGrid");
      if (!grid || grid.contains(document.activeElement)) {
        return;
      }
      $$(".cell.is-focused").forEach(function (entry) {
        entry.classList.remove("is-focused");
      });
    }, 0);
  }

  function createMatcher(symbol) {
    if (!symbol || hasMatcher(symbol)) {
      return;
    }
    state.matchers[symbol] = V.createMatcher("block");
  }

  function createMissingMatchers() {
    var missing = missingSymbols();
    missing.forEach(createMatcher);
    return missing;
  }

  function onMatcherListInput(event) {
    var target = event.target;
    if (!target || target.tagName !== "INPUT") {
      return;
    }
    var symbol = target.getAttribute("data-msymbol");
    var field = target.getAttribute("data-field");
    var matcher = symbol && state.matchers ? state.matchers[symbol] : null;
    if (!matcher) {
      return;
    }
    if (field === "block") {
      var rawBlock = target.value.trim();
      if (rawBlock.charAt(0) === "{") {
        var pastedMatcher = V.parseMatcherInput(rawBlock);
        if (pastedMatcher.kind !== "invalid") {
          matcher.block = pastedMatcher.block || "";
          matcher.tag = pastedMatcher.tag || "";
          matcher.kind = pastedMatcher.kind;
          matcher.state = pastedMatcher.state || [];
          refresh();
          return;
        }
      }
      matcher.block = target.value;
    } else if (field === "tag") {
      matcher.tag = target.value;
    } else if (field === "state-key" || field === "state-value") {
      var index = parseInt(target.getAttribute("data-index"), 10);
      var row = Array.isArray(matcher.state) ? matcher.state[index] : null;
      if (!row) {
        return;
      }
      if (field === "state-key") {
        row.key = target.value;
      } else {
        row.value = target.value;
      }
    } else {
      return;
    }
    updateMatcherCard(symbol);
    refresh({ skipPattern: true, skipMatchers: true });
  }

  function onMatcherListChange(event) {
    var target = event.target;
    if (!target || target.tagName !== "SELECT" || target.getAttribute("data-field") !== "kind") {
      return;
    }
    var symbol = target.getAttribute("data-msymbol");
    var matcher = symbol && state.matchers ? state.matchers[symbol] : null;
    if (!matcher) {
      return;
    }
    matcher.kind = target.value;
    if (matcher.kind === "state" && !Array.isArray(matcher.state)) {
      matcher.state = [];
    }
    refresh();
    var card = findMatcherCard(symbol);
    var next = card ? card.querySelector("[data-field='block'], [data-field='tag']") : null;
    if (next) {
      next.focus();
    }
  }

  function onMatcherListClick(event) {
    var action = closest(event.target, "[data-action]");
    if (!action) {
      return;
    }
    var card = closest(action, ".matcher-card");
    var symbol = card ? card.getAttribute("data-symbol") : "";
    var name = action.getAttribute("data-action");
    if (name === "remove-matcher") {
      if (!symbol || !hasMatcher(symbol)) {
        return;
      }
      if (!window.confirm("Remover o matcher do símbolo \"" + symbol + "\"?")) {
        return;
      }
      delete state.matchers[symbol];
      refresh();
      return;
    }
    if (!symbol || !hasMatcher(symbol)) {
      return;
    }
    var matcher = state.matchers[symbol];
    if (name === "create-matcher") {
      createMatcher(symbol);
      refresh();
      var created = findMatcherCard(symbol);
      var block = created ? created.querySelector("[data-field='block']") : null;
      if (block) {
        block.focus();
      }
      return;
    }
    if (name === "add-state") {
      if (!Array.isArray(matcher.state)) {
        matcher.state = [];
      }
      matcher.state.push({ key: "", value: "" });
      refresh();
      var refreshed = findMatcherCard(symbol);
      var details = refreshed ? refreshed.querySelector("details.card-advanced") : null;
      if (details) {
        details.open = true;
      }
      var keys = refreshed ? refreshed.querySelectorAll("[data-field='state-key']") : [];
      if (keys.length > 0) {
        keys[keys.length - 1].focus();
      }
      return;
    }
    if (name === "remove-state") {
      var index = parseInt(action.getAttribute("data-index"), 10);
      if (Array.isArray(matcher.state) && index >= 0 && index < matcher.state.length) {
        matcher.state.splice(index, 1);
        refresh();
        var refreshed = findMatcherCard(symbol);
        var details = refreshed ? refreshed.querySelector("details.card-advanced") : null;
        if (details) {
          details.open = true;
        }
      }
    }
  }

  function onMissingMatchersClick(event) {
    var all = closest(event.target, "[data-create-all]");
    if (all) {
      var created = createMissingMatchers();
      if (created.length > 0) {
        refresh();
        showToast(created.length + (created.length === 1 ? " matcher criado." : " matchers criados."));
      }
      return;
    }
    var chip = closest(event.target, "[data-create-missing]");
    if (!chip) {
      return;
    }
    var symbol = chip.getAttribute("data-create-missing");
    if (!symbol || hasMatcher(symbol)) {
      return;
    }
    createMatcher(symbol);
    refresh();
    var card = findMatcherCard(symbol);
    var block = card ? card.querySelector("[data-field='block']") : null;
    if (block) {
      block.focus();
    }
  }

  function onPaletteClick(event) {
    var chip = closest(event.target, ".palette-chip");
    if (!chip) {
      return;
    }
    selectPaletteSymbol(chip.getAttribute("data-symbol"));
  }

  function onLayerTabsClick(event) {
    var tab = closest(event.target, "[data-layer]");
    if (!tab) {
      return;
    }
    var index = parseInt(tab.getAttribute("data-layer"), 10);
    if (Number.isInteger(index) && index >= 0 && index < state.layers.length) {
      activeLayer = index;
      renderPattern();
      drawPreview();
    }
  }

  function updatePaletteForm() {
    var button = $("addSymbolSubmitBtn");
    var hint = $("paletteFormHint");
    if (paletteRenameSymbol) {
      if (button) {
        button.textContent = "Renomear";
        button.title = "Renomear o símbolo " + paletteRenameSymbol;
      }
      if (hint) {
        hint.innerHTML = "Renomeando <code>" + esc(paletteRenameSymbol) +
          "</code>. Edite o caractere e confirme; pattern, matchers, boosters, motions e portas acompanham.";
      }
    } else {
      if (button) {
        button.textContent = "Adicionar";
        button.title = "Adicionar símbolo à paleta";
      }
      if (hint) {
        hint.innerHTML = "A âncora <code>" + esc(state ? state.anchor : "C") +
          "</code> é única. Selecione um símbolo para renomeá-lo. Símbolos usados sem matcher ficam marcados em vermelho.";
      }
    }
  }

  function resetPaletteForm() {
    paletteRenameSymbol = null;
    var input = $("newSymbolInput");
    if (input) {
      input.value = "";
    }
    updatePaletteForm();
  }

  function selectPaletteSymbol(symbol) {
    activeSymbol = symbol;
    if (symbol === ERASER || !symbol) {
      resetPaletteForm();
    } else {
      paletteRenameSymbol = symbol;
      var input = $("newSymbolInput");
      if (input) {
        input.value = symbol;
      }
      updatePaletteForm();
    }
    renderPalette();
  }

  function eachPortList(callback) {
    var ports = state.capabilityPorts;
    if (!isPlainObject(ports)) {
      return;
    }
    Object.keys(ports).forEach(function (key) {
      if (key === "extras") {
        return;
      }
      if (Array.isArray(ports[key])) {
        callback(ports[key], key, ports);
      }
    });
    if (isPlainObject(ports.extras)) {
      Object.keys(ports.extras).forEach(function (key) {
        if (Array.isArray(ports.extras[key])) {
          callback(ports.extras[key], key, ports.extras);
        }
      });
    }
  }

  function portSymbolUsed(symbol) {
    var found = false;
    eachPortList(function (list) {
      if (list.indexOf(symbol) !== -1) {
        found = true;
      }
    });
    return found;
  }

  function renamePortSymbols(oldSymbol, next) {
    eachPortList(function (list, key, owner) {
      owner[key] = list.map(function (entry) {
        return entry === oldSymbol ? next : entry;
      });
    });
  }

  function capabilityPortFieldUses(symbol) {
    var caps = state.capabilities;
    if (!isPlainObject(caps)) {
      return false;
    }
    if (Array.isArray(caps.fluids)) {
      for (var fluidIndex = 0; fluidIndex < caps.fluids.length; fluidIndex += 1) {
        var fluid = caps.fluids[fluidIndex];
        if (isPlainObject(fluid) && trim(fluid.port) === symbol) {
          return true;
        }
      }
    }
    if (isPlainObject(caps.mana) && trim(caps.mana.port) === symbol) {
      return true;
    }
    if (isPlainObject(caps.chemical) && Array.isArray(caps.chemical.tanks)) {
      for (var tankIndex = 0; tankIndex < caps.chemical.tanks.length; tankIndex += 1) {
        var tank = caps.chemical.tanks[tankIndex];
        if (isPlainObject(tank) && trim(tank.port) === symbol) {
          return true;
        }
      }
    }
    return false;
  }

  function renameCapabilityPortFields(oldSymbol, next) {
    var caps = state.capabilities;
    if (!isPlainObject(caps)) {
      return;
    }
    if (Array.isArray(caps.fluids)) {
      caps.fluids.forEach(function (fluid) {
        if (isPlainObject(fluid) && trim(fluid.port) === oldSymbol) {
          fluid.port = next;
        }
      });
    }
    if (isPlainObject(caps.mana) && trim(caps.mana.port) === oldSymbol) {
      caps.mana.port = next;
    }
    if (isPlainObject(caps.chemical) && Array.isArray(caps.chemical.tanks)) {
      caps.chemical.tanks.forEach(function (tank) {
        if (isPlainObject(tank) && trim(tank.port) === oldSymbol) {
          tank.port = next;
        }
      });
    }
  }

  function symbolExistsInState(symbol) {
    return symbol === state.anchor ||
      symbol === upgradeKey() ||
      hasMatcher(symbol) ||
      (currentUsage[symbol] || 0) > 0 ||
      portSymbolUsed(symbol) ||
      capabilityPortFieldUses(symbol);
  }

  function renamePaletteSymbol(oldSymbol, value) {
    var next = String(value === undefined || value === null ? "" : value);
    if (next.length !== 1 || next === " ") {
      showToast("Informe um único caractere diferente de espaço.", "error");
      return false;
    }
    if (next === oldSymbol) {
      resetPaletteForm();
      showToast("O símbolo continua " + oldSymbol + ".");
      return false;
    }
    if (!symbolExistsInState(oldSymbol)) {
      resetPaletteForm();
      showToast("O símbolo " + oldSymbol + " não existe mais na paleta.", "error");
      return false;
    }
    if (next === state.anchor && oldSymbol !== state.anchor) {
      showToast(next + " já é a âncora.", "error");
      return false;
    }
    var key = upgradeKey();
    if (key && next === key && oldSymbol !== key) {
      showToast(next + " já é a key de upgrade.", "error");
      return false;
    }
    if (symbolExistsInState(next) && next !== oldSymbol) {
      showToast("Símbolo " + next + " já está em uso.", "error");
      return false;
    }
    state.layers = state.layers.map(function (layer) {
      if (!Array.isArray(layer)) {
        return layer;
      }
      return layer.map(function (row) {
        return typeof row === "string" ? row.split(oldSymbol).join(next) : row;
      });
    });
    if (state.anchor === oldSymbol) {
      state.anchor = next;
    }
    var upgrades = upgradesState();
    if (upgrades && trim(upgrades.key) === oldSymbol) {
      upgrades.key = next;
    }
    if (hasMatcher(oldSymbol)) {
      state.matchers[next] = state.matchers[oldSymbol];
      delete state.matchers[oldSymbol];
    }
    motionsState().forEach(function (motion) {
      if (isPlainObject(motion) && Array.isArray(motion.keys)) {
        motion.keys = motion.keys.map(function (entry) {
          return entry === oldSymbol ? next : entry;
        });
      }
    });
    renamePortSymbols(oldSymbol, next);
    renameCapabilityPortFields(oldSymbol, next);
    activeSymbol = next;
    resetPaletteForm();
    refresh();
    showToast("Símbolo " + oldSymbol + " renomeado para " + next + ".");
    return true;
  }

  function addNewSymbol(value) {
    var symbol = String(value === undefined || value === null ? "" : value);
    if (symbol.length !== 1 || symbol === " ") {
      showToast("Informe um único caractere diferente de espaço.", "error");
      return;
    }
    if (symbol === state.anchor) {
      showToast(symbol + " já é a âncora.", "error");
      return;
    }
    if (hasMatcher(symbol)) {
      selectPaletteSymbol(symbol);
      showToast("Símbolo " + symbol + " já existe na paleta.");
      return;
    }
    createMatcher(symbol);
    activeSymbol = symbol;
    resetPaletteForm();
    refresh();
    showToast("Símbolo " + symbol + " adicionado. Configure o matcher na etapa 03.");
  }

  function onImportChange(event) {
    var input = event.target;
    var file = input.files && input.files[0];
    input.value = "";
    if (!file) {
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var raw = JSON.parse(String(reader.result));
        var imported = V.importDefinition(raw);
        setPatternFullscreen(false);
        state = imported.state;
        state.recipes = [];
        motionMode = deriveMotionMode();
        upgradeMode = deriveUpgradeMode();
        resetMotionPreviewSelection();
        modelPreviewState.localModels = {};
        resetPreviewTextureOverrides();
        controllerMode = deriveControllerMode();
        if (controllerMode === "default") {
          state.model = V.createModel();
          state.shape = V.createShape();
        }
        modelPreviewState.previewState = "idle";
        importNotes = imported.notes || [];
        if (V.hasTextures(state.textures) && controllerMode === "custom") {
          importNotes.push({
            severity: "info",
            message: "textures será aplicado sobre o model custom quando as faces usarem front/back/left/right/top/bottom/side ou as direções correspondentes."
          });
        }
        var importedBase = String(file.name || "").replace(/\.json$/i, "");
        var pathBase = trim(state.path).split("/").pop();
        if (importedBase && pathBase && importedBase !== pathBase) {
          importNotes.push({
            severity: "warning",
            message: "O nome do arquivo importado (" + file.name + ") não bate com o path do id (" + state.path + "). O jogo usa o caminho como id; ajuste o path no builder."
          });
        }
        activeStep = "identity";
        activeLayer = 0;
        activeSymbol = null;
        resetPaletteForm();
        syncInputs();
        renderImportNotes();
        refresh();
        scrollToTop(false);
        var hasErrors = importNotes.some(function (note) {
          return note.severity === "error";
        });
        showToast(hasErrors ? "JSON importado, mas há erros." : "JSON importado.", hasErrors ? "error" : "");
      } catch (error) {
        showToast("JSON inválido: " + (error && error.message ? error.message : "erro de leitura"), "error");
      }
    };
    reader.onerror = function () {
      showToast("Não foi possível ler o arquivo.", "error");
    };
    reader.readAsText(file);
  }

  function loadExample() {
    setPatternFullscreen(false);
    state = V.createExampleState();
    importNotes = [];
    motionMode = deriveMotionMode();
    upgradeMode = deriveUpgradeMode();
    resetMotionPreviewSelection();
    activeStep = "identity";
    activeLayer = 0;
    activeSymbol = null;
    resetPaletteForm();
    syncInputs();
    renderImportNotes();
    refresh();
    scrollToTop(false);
    showToast("Exemplo carregado.");
  }

  function resetAll() {
    if (!window.confirm("Começar um novo esqueleto? O estado atual será descartado.")) {
      return;
    }
    setPatternFullscreen(false);
    state = V.createDefaultState();
    importNotes = [];
    motionMode = deriveMotionMode();
    upgradeMode = deriveUpgradeMode();
    resetMotionPreviewSelection();
    activeStep = "identity";
    activeLayer = 0;
    activeSymbol = null;
    resetPaletteForm();
    syncInputs();
    renderImportNotes();
    refresh();
    scrollToTop(false);
    showToast("Novo esqueleto criado.");
  }

  function bindEvents() {
    $("loadExampleBtn").addEventListener("click", loadExample);
    $("resetBtn").addEventListener("click", resetAll);
    $("importBtn").addEventListener("click", function () {
      $("importInput").click();
    });
    $("importInput").addEventListener("change", onImportChange);
    $("stepNav").addEventListener("click", function (event) {
      var button = closest(event.target, ".step-button[data-step]");
      if (!button || button.disabled) {
        return;
      }
      setStep(button.getAttribute("data-step"));
    });
    $("importBanner").addEventListener("click", function (event) {
      if (closest(event.target, "[data-dismiss-import]")) {
        importNotes = [];
        renderImportNotes();
        scheduleSave();
      }
    });
    $$("[data-bind]").forEach(function (element) {
      var bind = element.getAttribute("data-bind");
      if (element.type === "radio") {
        element.addEventListener("change", function () {
          if (!element.checked) {
            return;
          }
          state[bind] = element.value;
          refresh({ skipPattern: true, skipMatchers: true });
        });
      } else {
        element.addEventListener("input", function () {
          state[bind] = element.value;
          refresh({ skipPattern: true, skipMatchers: true });
        });
      }
    });
    $("copyPathInlineBtn").addEventListener("click", copyFilePath);
    $("copyPathBtn").addEventListener("click", copyFilePath);
    $("copyJsonBtn").addEventListener("click", copyJson);
    $("copyJsonInlineBtn").addEventListener("click", copyJson);
    $("downloadBtn").addEventListener("click", downloadJson);
    $("downloadInlineBtn").addEventListener("click", downloadJson);
    $("sizeX").addEventListener("change", function () { onSizeChange("X"); });
    $("sizeZ").addEventListener("change", function () { onSizeChange("Z"); });
    $("sizeY").addEventListener("change", function () { onSizeChange("Y"); });
    $("patternFullscreenBtn").addEventListener("click", togglePatternFullscreen);
    $("anchorSymbol").addEventListener("change", onAnchorChange);
    $("addLayerBtn").addEventListener("click", addLayer);
    $("duplicateLayerBtn").addEventListener("click", duplicateLayer);
    $("removeLayerBtn").addEventListener("click", removeLayer);
    $("layerTabs").addEventListener("click", onLayerTabsClick);
    $("palette").addEventListener("click", onPaletteClick);
    $("focusNewSymbolBtn").addEventListener("click", function () {
      resetPaletteForm();
      var input = $("newSymbolInput");
      input.focus();
      input.select();
    });
    $("addSymbolForm").addEventListener("submit", function (event) {
      event.preventDefault();
      var value = $("newSymbolInput").value;
      if (paletteRenameSymbol) {
        renamePaletteSymbol(paletteRenameSymbol, value);
      } else {
        addNewSymbol(value);
      }
    });
    $("layerGrid").addEventListener("pointerdown", onGridPointerDown);
    $("layerGrid").addEventListener("pointermove", onGridPointerMove);
    $("layerGrid").addEventListener("contextmenu", function (event) {
      if (closest(event.target, ".cell")) {
        event.preventDefault();
      }
    });
    $("layerGrid").addEventListener("keydown", onGridKeyDown);
    $("layerGrid").addEventListener("focusin", onGridFocusIn);
    $("layerGrid").addEventListener("focusout", onGridFocusOut);
    $("missingMatchers").addEventListener("click", onMissingMatchersClick);
    $("matcherList").addEventListener("input", onMatcherListInput);
    $("matcherList").addEventListener("change", onMatcherListChange);
    $("matcherList").addEventListener("click", onMatcherListClick);
    $("upgradeModeOptions").addEventListener("change", onUpgradeModeChange);
    $("upgradeEditorArea").addEventListener("input", onUpgradeInput);
    $("upgradeEditorArea").addEventListener("change", onUpgradeChange);
    $("upgradeEditorArea").addEventListener("click", onUpgradeClick);
    $("addUpgradeBoosterBtn").addEventListener("click", addUpgradeBooster);
    $("upgradePaintKeyBtn").addEventListener("click", paintUpgradeKey);
    $("upgradeRecipeImportBtn").addEventListener("click", function () {
      $("upgradeRecipeFile").click();
    });
    $("upgradeRecipeFile").addEventListener("change", onUpgradeRecipeFile);
    $("upgradeRecipeSimulateBtn").addEventListener("click", simulateUpgradeRecipe);
    $("upgradeRecipeClearBtn").addEventListener("click", clearUpgradeRecipe);
    $("upgradeRecipeJson").addEventListener("change", simulateUpgradeRecipe);
    $("upgradeSimulationCounts").addEventListener("input", onUpgradeSimulationCountInput);
    $("motionModeOptions").addEventListener("change", onMotionModeChange);
    $("addMotionBtn").addEventListener("click", addMotion);
    $("motionList").addEventListener("click", onMotionListClick);
    $("motionDetail").addEventListener("input", onMotionDetailInput);
    $("motionDetail").addEventListener("change", onMotionDetailChange);
    $("motionDetail").addEventListener("click", onMotionDetailClick);
    $("motionPlayBtn").addEventListener("click", toggleMotionPlay);
    $("motionResetTimeBtn").addEventListener("click", resetMotionTime);
    $("motionResetViewBtn").addEventListener("click", resetMotionPreviewView);
    $("motionTimeRange").addEventListener("input", onMotionTimeRangeInput);
    $("motionSpeed").addEventListener("change", onMotionSpeedChange);
    $("motionCamera").addEventListener("change", onMotionCameraChange);
    $("motionOpacity").addEventListener("input", onMotionOpacityInput);
    $("motionFullscreenBtn").addEventListener("click", toggleMotionFullscreen);
    $("motionEditorCloseBtn").addEventListener("click", function () {
      setMotionFullscreen(false);
    });
    $("motionPanelBtn").addEventListener("click", toggleMotionPanel);
    $("motionTimeline").addEventListener("click", onMotionTimelineClick);
    $("motionCanvasWrap").addEventListener("pointerdown", beginMotionPreviewDrag);
    $("motionCanvasWrap").addEventListener("pointermove", moveMotionPreviewDrag);
    $("motionCanvasWrap").addEventListener("pointerup", endMotionPreviewDrag);
    $("motionCanvasWrap").addEventListener("pointercancel", endMotionPreviewDrag);
    $("motionCanvasWrap").addEventListener("wheel", onMotionPreviewWheel, { passive: false });
    $("addSlotBtn").addEventListener("click", addSlot);
    $("useInventoryRowsBtn").addEventListener("click", useInventoryRows);
    $("slotList").addEventListener("input", onSlotListInput);
    $("slotList").addEventListener("change", onSlotListChange);
    $("slotList").addEventListener("click", onSlotListClick);
    $("addEnergyBtn").addEventListener("click", addEnergy);
    $("addFluidBtn").addEventListener("click", addFluidTank);
    $("addChemicalBtn").addEventListener("click", addChemicalTank);
    $("addManaBtn").addEventListener("click", addMana);
    $("addEssentiaBtn").addEventListener("click", addEssentia);
    $("removeCapabilitiesBtn").addEventListener("click", removeCapabilities);
    $("capabilityList").addEventListener("input", onCapabilityListInput);
    $("capabilityList").addEventListener("click", onCapabilityListClick);
    $("capabilityPortsPanel").addEventListener("input", onCapabilityPortsInput);
    $("capabilityPortsPanel").addEventListener("click", onCapabilityPortsClick);
    $("controllerModeOptions").addEventListener("change", onControllerModeChange);
    $("modelForm").addEventListener("input", onModelInput);
    $("modelForm").addEventListener("click", onModelImportClick);
    $("modelImportInput").addEventListener("change", onModelImportChange);
    $("resetModelBtn").addEventListener("click", resetModel);
    $("shapeFromModelBtn").addEventListener("click", useShapeFromModel);
    $("addShapeBoxBtn").addEventListener("click", addShapeBox);
    $("resetShapeBtn").addEventListener("click", resetShape);
    $("shapeList").addEventListener("input", onShapeInput);
    $("shapeList").addEventListener("click", onShapeListClick);
    $("modelPreviewStateButtons").addEventListener("click", onModelPreviewStateClick);
    $("modelPreviewTextureList").addEventListener("click", onModelPreviewTextureImportClick);
    $("modelPreviewTextureList").addEventListener("click", onModelPreviewTextureClearClick);
    $("modelPreviewTextureList").addEventListener("click", onModelPreviewMcmetaClick);
    $("modelPreviewTextureInput").addEventListener("change", onModelPreviewTextureInputChange);
    $("modelPreviewMcmetaInput").addEventListener("change", onModelPreviewMcmetaInputChange);
    $("modelPreviewResetViewBtn").addEventListener("click", resetModelPreviewView);
    $("modelPreviewFloor").addEventListener("change", onModelPreviewOptionChange);
    $("modelPreviewReference").addEventListener("change", onModelPreviewOptionChange);
    $("modelPreviewCanvas").addEventListener("pointerdown", beginModelPreviewDrag);
    $("modelPreviewCanvas").addEventListener("pointermove", moveModelPreviewDrag);
    $("modelPreviewCanvas").addEventListener("pointerup", endModelPreviewDrag);
    $("modelPreviewCanvas").addEventListener("pointercancel", endModelPreviewDrag);
    $("modelPreviewCanvas").addEventListener("wheel", onModelPreviewWheel, { passive: false });
    $("addControllerRecipeBtn").addEventListener("click", addControllerRecipe);
    $("removeControllerRecipeBtn").addEventListener("click", removeControllerRecipe);
    $("controllerRecipePalette").addEventListener("click", onControllerRecipePaletteClick);
    $("controllerRecipeGrid").addEventListener("click", onControllerRecipeGridClick);
    $("controllerRecipeGrid").addEventListener("contextmenu", onControllerRecipeGridClick);
    $("controllerRecipeKeyList").addEventListener("input", onControllerRecipeKeyInput);
    $("addGuiBtn").addEventListener("click", addGui);
    $("removeGuiBtn").addEventListener("click", removeGui);
    $("addGuiElementBtn").addEventListener("click", addGuiElement);
    $("guiTextureUploadBtn").addEventListener("click", function () {
      var input = $("guiTextureUpload");
      if (input) {
        input.click();
      }
    });
    $("guiTextureUpload").addEventListener("change", onGuiTextureUpload);
    $("guiTextureResetBtn").addEventListener("click", resetGuiTexture);
    $("guiForm").addEventListener("input", onGuiFormInput);
    $("guiElementList").addEventListener("input", onGuiElementInput);
    $("guiElementList").addEventListener("change", onGuiElementChange);
    $("guiElementList").addEventListener("click", onGuiElementClick);
    $("guiCanvas").addEventListener("pointerdown", beginGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointermove", moveGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointerup", endGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointercancel", endGuiCanvasDrag);
    $("slotGuiPreview").addEventListener("mouseenter", onSlotPreviewMouseMove);
    $("slotGuiPreview").addEventListener("mousemove", onSlotPreviewMouseMove);
    $("slotGuiPreview").addEventListener("mouseleave", resetSlotPreviewZoom);
    $("soundsPanel").addEventListener("input", onSoundInput);
    $("soundsPanel").addEventListener("click", onSoundsPanelClick);
    $("soundUploadInput").addEventListener("change", onSoundUploadChange);
    $("inheritanceMode").addEventListener("change", onInheritanceChange);
    $("inheritanceTemplate").addEventListener("change", onInheritanceChange);
    $("inheritanceParent").addEventListener("input", onInheritanceInput);
    $("inheritanceOverrides").addEventListener("input", onInheritanceInput);
    $("addRecipeBtn").addEventListener("click", addRecipe);
    $("recipeList").addEventListener("input", onRecipeInput);
    $("recipeList").addEventListener("click", onRecipeAction);
    $("reviewRecipeList").addEventListener("click", onRecipeAction);
    $("assetList").addEventListener("click", onAssetClick);
    $("issueList").addEventListener("click", function (event) {
      var button = closest(event.target, ".issue-button");
      if (!button) {
        return;
      }
      var step = button.getAttribute("data-step");
      var field = button.getAttribute("data-field");
      setStep(step, { preserveScroll: true });
      window.setTimeout(function () {
        focusField(field);
      }, 40);
    });
    document.addEventListener("pointerup", endPaint);
    document.addEventListener("pointercancel", endPaint);
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && isPatternFullscreen()) {
        event.preventDefault();
        setPatternFullscreen(false);
      } else if (event.key === "Escape" && isMotionFullscreen()) {
        event.preventDefault();
        var motionView = motionSection();
        if (motionView && motionView.classList.contains("is-panel-open")) {
          closeMotionPanel();
        } else {
          setMotionFullscreen(false);
        }
      }
    });
    window.addEventListener("resize", function () {
      if (activeStep === "motions") {
        window.setTimeout(drawMotionPreview, 30);
      } else {
        window.setTimeout(drawPreview, 30);
      }
      if (activeStep === "controller") {
        window.setTimeout(drawModelPreview, 30);
      }
    });
    window.addEventListener("beforeunload", saveNow);
  }

  function init() {
    if (!V) {
      $("statusTitle").textContent = "Falha ao carregar validation.js";
      return;
    }
    if (!loadSaved()) {
      state = V.createDefaultState();
      importNotes = [];
      activeStep = "identity";
    }
    activeLayer = 0;
    activeSymbol = null;
    resetPaletteForm();
    bindEvents();
    syncInputs();
    renderImportNotes();
    refresh();
    if (activeStep === "motions") {
      startMotionLoop();
    } else {
      startRailPreviewLoop();
    }
    if (activeStep === "controller" && modelPreviewState.source === "none") {
      scheduleModelPreviewFieldLoad();
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
