(function () {
  "use strict";

  var V = window.MultiblockValidation;
  var Preview = window.MultiblockPreview;
  var STORAGE_KEY = "infinity-nexus-multiblock-builder.v1";
  var STORAGE_VERSION = 1;
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
    { id: "slots", label: "Slots", hint: "item_slots" },
    { id: "capabilities", label: "Capabilities", hint: "energia, fluidos e portas" },
    { id: "controller", label: "Controller", hint: "bloco e texturas" },
    { id: "gui", label: "GUI", hint: "layout e elementos" },
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
    recipeType: "recipeType",
    tier: "tier",
    priority: "priority",
    inventoryRows: "inventoryRows",
    anchor: "anchorSymbol"
  };
  var FIELD_TARGETS = {
    namespace: "namespace",
    path: "path",
    recipeType: "recipeType",
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
  var dragging = false;
  var dragButton = 0;
  var lastPaintKey = "";
  var saveTimer = null;
  var toastTimer = null;

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
        activeStep: activeStep
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
    if (!isPlainObject(state.controller) && isPlainObject(state.extras.controller)) {
      state.controller = V.parseController(state.extras.controller, []);
    }
    if (state.controller === undefined) {
      state.controller = null;
    }
    if (state.controller !== null && !isPlainObject(state.controller)) {
      state.controller = null;
    }
    delete state.extras.controller;
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
    Object.keys(currentUsage).forEach(function (symbol) {
      if (symbol !== " " && symbol !== state.anchor && !hasMatcher(symbol)) {
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
    return "Símbolo " + symbol + " em " + position +
      (hasMatcher(symbol) ? "" : ", sem matcher");
  }

  function applyCellClasses(button, row, column, symbol) {
    var isAnchor = symbol !== " " && symbol === state.anchor;
    var isUnmatched = symbol !== " " && !isAnchor && !hasMatcher(symbol);
    var classes = ["cell"];
    if (symbol === " ") {
      classes.push("is-empty");
    }
    if (isAnchor) {
      classes.push("is-anchor");
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
    $("schemaVersion").value = String(V.SCHEMA_VERSION);
    $("namespace").value = state.namespace || "";
    $("path").value = state.path || "";
    $("recipeType").value = state.recipeType || "";
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
    ["namespace", "path", "recipeType", "tier", "priority", "inventoryRows", "anchor", "pattern", "inheritanceParent"].forEach(function (field) {
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
    setStepBadge("slotsBadge", "slots");
    setStepBadge("capabilitiesBadge", "capabilities");
    setStepBadge("controllerBadge", "controller");
    setStepBadge("guiBadge", "gui");
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
    var recipeField = $("recipeTypeField");
    recipeField.hidden = state.type !== "recipe";
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
      var hasController = isPlainObject(state.controller) && V.isValidIdentifier(state.controller.block);
      callout.hidden = false;
      callout.className = "callout callout-warning";
      callout.innerHTML = hasController
        ? "<strong>Namespace externo:</strong> só funciona se o controller já existir em Java; o download preserva esse controller."
        : "<strong>Namespace externo:</strong> não cria bloco controller dinâmico. A exportação fica bloqueada; use <code>infinity_nexus_multiblock</code>.";
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
        "' aria-pressed='" + (active ? "true" : "false") + "' title='Símbolo " + esc(symbol) + ": " +
        esc(descriptor) + "'><span class='swatch' style='--c:" + getColor(symbol) + "'>" + esc(symbol) +
        "</span><span class='palette-count'>" + esc(descriptor) + "</span></button>");
    });
    var eraserActive = activeSymbol === ERASER;
    parts.push("<button type='button' class='palette-chip" + (eraserActive ? " is-active" : "") +
      "' data-symbol=' ' aria-pressed='" + (eraserActive ? "true" : "false") +
      "' title='Apagar célula'><span class='swatch swatch-eraser'>⌫</span><span class='palette-count'>apagar</span></button>");
    $("palette").innerHTML = parts.join("");
  }

  function renderLegend() {
    var used = Object.keys(currentUsage).sort(compareSymbols);
    $("legend").innerHTML = used.map(function (symbol) {
      var descriptor = symbol === state.anchor ? "âncora" : "×" + currentUsage[symbol];
      return "<span class='legend-entry' style='--c:" + getColor(symbol) + "' title='Símbolo " + esc(symbol) +
        "'> <span class='legend-swatch'></span>" + esc(symbol) + " · " + esc(descriptor) + "</span>";
    }).join("");
  }

  function matcherFeedback(symbol, matcher) {
    if (!V.isSingleSymbol(symbol)) {
      return { tone: "is-error", text: "A chave do matcher precisa ter exatamente um caractere." };
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
    var html = "<div class='field'><span class='field-label'>Bloco</span><input type='text' data-msymbol='" +
      esc(symbol) + "' data-field='block' value='" + esc(matcher.block || "") +
      "' placeholder='minecraft:iron_block' title='Aceita o ID do bloco ou o JSON do /inm look' " +
      "spellcheck='false' autocomplete='off' aria-label='Bloco" +
      esc(labelSymbol) + "'></div>";
    if (matcher.kind === "state") {
      var rows = Array.isArray(matcher.state) ? matcher.state : [];
      html += "<div class='state-list'>";
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
      html += "</div><button type='button' class='btn btn-tiny' data-action='add-state'>+ propriedade</button>";
    }
    return html;
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
    return "<div class='" + cardClass + "' data-symbol='" + esc(symbol) + "'><div class='matcher-head'><span class='swatch' style='--c:" +
      color + "'>" + esc(symbol) + "</span><div class='matcher-title'><strong>Símbolo \"" + esc(symbol) +
      "\"</strong><small>" + esc(detail) + (V.isSingleSymbol(symbol) ? "" : " · chave inválida") +
      "</small></div>" + remove + "</div><div class='matcher-form'><div class='field'><span class='field-label'>Tipo</span>" +
      select + "</div><div class='matcher-fields'>" + matcherFieldHtml(symbol, matcher) +
      "<code class='matcher-json'>" + esc(serialized) + "</code><p class='matcher-feedback" +
      (feedback ? " " + feedback.tone : "") + "'" + (feedback ? "" : " hidden") + ">" +
      (feedback ? esc(feedback.text) : "") + "</p></div></div></div>";
  }

  function renderMatchers() {
    var symbols = [];
    var seen = {};
    Object.keys(currentUsage).forEach(function (symbol) {
      if (symbol !== " " && symbol !== state.anchor && !seen[symbol]) {
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
          "' title='Criar matcher para " + esc(symbol) + "'>" + esc(symbol) + "</button>";
      }).join("");
      $("missingMatchers").innerHTML = "<div class='missing-card'><span><strong>Sem matcher:</strong></span><div class='missing-symbols'>" +
        chips + "</div><button type='button' class='btn btn-tiny' data-create-all='true'>Criar todos</button></div>";
    } else {
      var usedCount = Object.keys(currentUsage).filter(function (symbol) {
        return symbol !== " " && symbol !== state.anchor;
      }).length;
      $("missingMatchers").innerHTML = "<div class='missing-empty'>" +
        (usedCount > 0 ? "Todos os símbolos usados têm matcher." : "Pinte símbolos no pattern para configurar matchers.") +
        "</div>";
    }
    $("matcherList").innerHTML = symbols.map(matcherCardHtml).join("");
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
    var insert = role === "output" ? [] : SLOT_FACE_DIRECTIONS.slice();
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
        ">" + esc(value) + "</option>";
    });
    return html;
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
    var filterField = trim(slot.role) === "output" ? "" :
      "<div class='field'><label>filter</label><input type='text' data-slot-index='" + index +
      "' data-field='filter' value='" + esc(slot.filter || "") +
      "' placeholder='minecraft:iron_ore ou #tag' spellcheck='false' autocomplete='off'></div>";
    return "<div class='slot-card" + (problem ? " is-problem" : "") + "' data-slot-index='" + index + "'>" +
      "<div class='matcher-head'><span class='slot-index'>" + (index + 1) + "</span>" +
      "<div class='matcher-title'><strong>" + esc(slotName) + "</strong><small>" +
      esc(slotSummary(slot)) + "</small></div>" +
      "<button type='button' class='btn btn-tiny btn-danger matcher-remove' data-action='remove-slot'>remover</button></div>" +
      "<div class='slot-form'>" +
      "<div class='field'><label>id</label><input type='text' data-slot-index='" + index +
      "' data-field='id' value='" + esc(slot.id || "") + "' placeholder='input_0' spellcheck='false' autocomplete='off'></div>" +
      "<div class='field'><label>role</label><select data-slot-index='" + index +
      "' data-field='role' aria-label='Role do slot " + (index + 1) + "'>" + slotRoleOptions(slot) + "</select></div>" +
      filterField +
      "<div class='field'><label>limit</label><input type='number' data-slot-index='" + index +
      "' data-field='limit' min='1' max='64' step='1' value='" + esc(limit) + "' placeholder='64'></div>" +
      "<div class='field'><label>x</label><input type='number' data-slot-index='" + index +
      "' data-field='x' step='1' value='" + esc(x) + "' placeholder='opcional'></div>" +
      "<div class='field'><label>y</label><input type='number' data-slot-index='" + index +
      "' data-field='y' step='1' value='" + esc(y) + "' placeholder='opcional'></div>" +
      "</div>" + slotFacesHtml(slot, index) +
      "<code class='matcher-json slot-json'>" + esc(serialized === undefined ? "null" : serialized) +
      "</code><p class='matcher-feedback slot-feedback" + (problem ? " is-error" : "") + "'" +
      (problem ? "" : " hidden") + ">" + esc(problem || "") + "</p></div>";
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
      var outside = x < 0 || y < 0 || x + 18 > width || y + 18 > height;
      html.push("<div class='gui-slot-preview" + (outside ? " is-outside" : "") +
        "' data-role='" + esc(role) + "' style='left:" + x + "px;top:" + y + "px;--slot-color:" +
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
    var customGui = customElements.length > 0;
    var dimensions = customGui ? guiCanvasSize(state.gui) : {
      width: V.GUI_DEFAULT_WIDTH,
      height: V.GUI_DEFAULT_HEIGHT
    };
    var hasSlotElements = customGui && customElements.some(function (element) {
      return trim(element.type) === "slot";
    });
    layoutSlotGuiPreview(content, dimensions);

    if (customGui) {
      items.push(customElements.map(guiCanvasElementMarkup).join(""));
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
      for (var row = 0; row < 3; row++) {
        for (var column = 0; column < 9; column++) {
          items.push("<i class='gui-preview-ghost' style='left:" + (8 + column * 18) +
            "px;top:" + (84 + row * 18) + "px'></i>");
        }
      }
      for (var hotbar = 0; hotbar < 9; hotbar++) {
        items.push("<i class='gui-preview-ghost' style='left:" + (8 + hotbar * 18) +
          "px;top:142px'></i>");
      }

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
      if (state.type === "recipe" && trim(state.recipeType)) {
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
      return "Sem capabilities; a máquina não expõe energia/fluidos.";
    }
    var energy = isPlainObject(capabilities.energy) ? capabilities.energy : null;
    var fluids = Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    var energyText = energy
      ? "Energia " + (trim(energy.id) || "sem id") + " · " + capabilityFieldValue(energy.capacity) + " FE"
      : "Sem energia";
    return energyText + " · " + (fluids.length === 1 ? "1 tanque" : fluids.length + " tanques");
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
    ["item", "energy", "fluid"].forEach(function (key) {
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
    ["item", "energy", "fluid"].forEach(function (key) {
      var input = document.querySelector("[data-capability-port='" + key + "']");
      if (!input) {
        return;
      }
      input.disabled = !availability[key];
      input.title = availability[key] ? "" : "Adicione a capability correspondente para configurar as portas.";
      if (input !== document.activeElement) {
        input.value = ports ? V.capabilityPortText(ports[key]) : "";
      }
    });
    updateCapabilityPortAvailability(availability);
    updateCapabilityPortsSummary();
    var symbols = $("capabilityPortSymbols");
    if (symbols) {
      var available = V.recipePatternSymbols(state);
      symbols.textContent = available.length > 0
        ? "Símbolos disponíveis no pattern: " + available.join(", ") + "."
        : "Sem símbolos no pattern; use IDs de bloco ou tags.";
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
    updateCapabilityPortsSummary();
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
    var summary = (trim(fluid.id) || "sem id") + " · " + capabilityFieldValue(fluid.capacity) +
      " mB · " + filter;
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
      empty.textContent = "Nenhuma capability definida. Adicione energia ou um tanque para expor FE/fluidos.";
      list.innerHTML = "";
      removeButton.disabled = true;
      $("addEnergyBtn").disabled = false;
      $("addFluidBtn").disabled = false;
      return;
    }
    var energy = isPlainObject(capabilities.energy) ? capabilities.energy : null;
    var fluids = Array.isArray(capabilities.fluids) ? capabilities.fluids : [];
    var parts = [];
    if (energy) {
      parts.push(energyCardHtml(energy));
    }
    fluids.forEach(function (fluid, index) {
      if (isPlainObject(fluid)) {
        parts.push(fluidCardHtml(fluid, index));
      }
    });
    summary.textContent = capabilitySummaryText();
    empty.hidden = parts.length > 0;
    empty.textContent = parts.length === 0
      ? "Capabilities ativas, mas sem energia ou tanques. Use + energia ou + tanque."
      : "";
    list.innerHTML = parts.join("");
    removeButton.disabled = false;
    $("addEnergyBtn").disabled = Boolean(energy);
    $("addFluidBtn").disabled = false;
  }

  function energyCardElement() {
    return document.querySelector(".capability-card[data-capability='energy']");
  }

  function fluidCardElement(index) {
    return document.querySelector(".capability-card[data-fluid-index='" + index + "']");
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
        " mB · " + (trim(fluid.filter) || "qualquer fluido");
    }
    card.classList.toggle("is-problem", Boolean(problem));
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

  function removeCapabilities() {
    if (!isPlainObject(state.capabilities)) {
      return;
    }
    if (!window.confirm("Remover todas as capabilities da máquina?")) {
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
    var index = parseInt(target.getAttribute("data-fluid-index"), 10);
    var field = target.getAttribute("data-fluid-field");
    var fluid = isPlainObject(state.capabilities) && Array.isArray(state.capabilities.fluids)
      ? state.capabilities.fluids[index]
      : null;
    if (!isPlainObject(fluid) || ["id", "capacity", "maxReceive", "maxExtract", "filter"].indexOf(field) === -1) {
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
    var action = closest(event.target, "[data-action='remove-fluid']");
    if (!action) {
      return;
    }
    var card = closest(action, ".capability-card");
    var index = card ? parseInt(card.getAttribute("data-fluid-index"), 10) : -1;
    removeFluidTank(index);
  }

  function controllerBlockId() {
    return (trim(state.namespace) || "namespace") + ":" + (trim(state.path) || "path");
  }

  function controllerFallbackTextures() {
    var textures = V.createController().textures;
    V.CONTROLLER_FACES.forEach(function (face) {
      textures[face] = "infinity_nexus_multiblock:block/fallback/" + face;
    });
    return textures;
  }

  function ensureController() {
    if (!isPlainObject(state.controller)) {
      state.controller = V.createController();
      state.controller.block = controllerBlockId();
      state.controller.textures = controllerFallbackTextures();
    }
    return state.controller;
  }

  function syncControllerBlock() {
    if (isPlainObject(state.controller) && state.namespace === "infinity_nexus_multiblock") {
      state.controller.block = controllerBlockId();
    }
  }

  function controllerTextureCount(controller) {
    if (!isPlainObject(controller) || !isPlainObject(controller.textures)) {
      return 0;
    }
    return V.CONTROLLER_FACES.filter(function (face) {
      return Boolean(trim(controller.textures[face]));
    }).length;
  }

  function controllerSummaryText() {
    var controller = isPlainObject(state.controller) ? state.controller : null;
    if (!controller) {
      return "Sem controller; o loader usa o id e texturas fallback.";
    }
    var block = trim(controller.block);
    if (!block && state.namespace === "infinity_nexus_multiblock") {
      block = controllerBlockId();
    }
    var light = controller.lightLevel === null || controller.lightLevel === undefined
      ? 0
      : controller.lightLevel;
    return (block || "sem bloco") + " - " + controllerTextureCount(controller) + "/6 texturas - light " + light;
  }

  function controllerFieldElement(field) {
    return document.querySelector("[data-controller-field='" + field + "']");
  }

  function updateControllerCard() {
    var controller = isPlainObject(state.controller) ? state.controller : null;
    var summary = $("controllerSummary");
    var feedback = $("controllerFeedback");
    var jsonElement = $("controllerJson");
    if (!summary) {
      return;
    }
    summary.textContent = controllerSummaryText();
    if (!controller) {
      if (feedback) {
        feedback.hidden = true;
        feedback.textContent = "";
      }
      if (jsonElement) {
        jsonElement.textContent = "";
      }
      return;
    }
    var problem = V.controllerProblem(controller);
    if (feedback) {
      feedback.className = "matcher-feedback controller-feedback" + (problem ? " is-error" : "");
      feedback.textContent = problem || "";
      feedback.hidden = !problem;
    }
    if (jsonElement) {
      var serialized = JSON.stringify(V.buildController(controller), null, 2);
      jsonElement.textContent = serialized === undefined ? "null" : serialized;
    }
  }

  function renderController() {
    var controller = isPlainObject(state.controller) ? state.controller : null;
    var summary = $("controllerSummary");
    var empty = $("controllerEmpty");
    var form = $("controllerForm");
    var addButton = $("addControllerBtn");
    var removeButton = $("removeControllerBtn");
    var fallbackButton = $("controllerFallbackBtn");
    if (!summary || !empty || !form) {
      return;
    }
    if (!controller) {
      summary.textContent = controllerSummaryText();
      empty.hidden = false;
      empty.textContent = state.namespace === "infinity_nexus_multiblock"
        ? "Sem controller. O bloco dinâmico ainda é criado com o id e texturas fallback."
        : "Sem controller. Um datapack externo precisa de um controller já registrado em Java.";
      form.hidden = true;
      if (addButton) {
        addButton.disabled = false;
      }
      if (removeButton) {
        removeButton.disabled = true;
      }
      if (fallbackButton) {
        fallbackButton.disabled = true;
      }
      updateControllerCard();
      return;
    }

    var textures = isPlainObject(controller.textures) ? controller.textures : {};
    var formed = isPlainObject(controller.formedTextures) ? controller.formedTextures : {};
    var blockInput = $("controllerBlock");
    var particleInput = $("controllerParticle");
    var lightInput = $("controllerLightLevel");
    if (blockInput) {
      blockInput.value = trim(controller.block) ||
        (state.namespace === "infinity_nexus_multiblock" ? controllerBlockId() : "");
      blockInput.readOnly = state.namespace === "infinity_nexus_multiblock";
    }
    var blockHint = $("controllerBlockHint");
    if (blockHint) {
      blockHint.textContent = state.namespace === "infinity_nexus_multiblock"
        ? "Sincronizado com o id da definição; o bloco dinâmico é criado no startup."
        : "Precisa apontar para um MultiblockControllerBlock já registrado em Java.";
    }
    V.CONTROLLER_FACES.forEach(function (face) {
      var textureInput = controllerFieldElement("texture:" + face);
      if (textureInput) {
        textureInput.value = trim(textures[face]);
      }
      var formedInput = controllerFieldElement("formed:" + face);
      if (formedInput) {
        formedInput.value = trim(formed[face]);
      }
    });
    if (particleInput) {
      particleInput.value = trim(controller.particle);
    }
    if (lightInput) {
      lightInput.value = controller.lightLevel === null || controller.lightLevel === undefined
        ? "0"
        : String(controller.lightLevel);
    }
    empty.hidden = true;
    form.hidden = false;
    if (addButton) {
      addButton.disabled = true;
    }
    if (removeButton) {
      removeButton.disabled = false;
    }
    if (fallbackButton) {
      fallbackButton.disabled = false;
    }
    updateControllerCard();
  }

  function addController() {
    ensureController();
    refresh();
    var blockInput = $("controllerBlock");
    var firstTexture = controllerFieldElement("texture:front");
    focusElement(blockInput && !blockInput.readOnly ? blockInput : firstTexture, false);
  }

  function removeController() {
    if (!isPlainObject(state.controller)) {
      return;
    }
    if (!window.confirm("Remover o controller customizado? O loader voltará a usar o id e as texturas fallback.")) {
      return;
    }
    state.controller = null;
    refresh();
    showToast("Controller removido.");
  }

  function fillControllerFallback() {
    var controller = ensureController();
    controller.textures = controllerFallbackTextures();
    refresh();
    showToast("Texturas fallback aplicadas.");
  }

  function onControllerFormInput(event) {
    var target = event.target;
    var field = target ? target.getAttribute("data-controller-field") : "";
    var controller = isPlainObject(state.controller) ? state.controller : null;
    if (!field || !controller) {
      return;
    }
    if (field === "block") {
      controller.block = target.value;
    } else if (field === "particle") {
      controller.particle = target.value;
    } else if (field === "lightLevel") {
      controller.lightLevel = target.value === "" ? 0 : target.value;
    } else if (field.indexOf("texture:") === 0) {
      if (!isPlainObject(controller.textures)) {
        controller.textures = V.createFormedTextures();
      }
      controller.textures[field.slice("texture:".length)] = target.value;
    } else if (field.indexOf("formed:") === 0) {
      if (!isPlainObject(controller.formedTextures)) {
        controller.formedTextures = V.createFormedTextures();
      }
      controller.formedTextures[field.slice("formed:".length)] = target.value;
    } else {
      return;
    }
    updateControllerCard();
    refresh({
      skipPattern: true,
      skipMatchers: true,
      skipSlots: true,
      skipCapabilities: true,
      skipController: true,
      skipControllerRecipe: true
    });
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
    var result = isPlainObject(state.controller) && trim(state.controller.block)
      ? trim(state.controller.block)
      : controllerBlockId();
    return "Shaped " + rows.length + "x" + width + " -> " + result;
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
    title: "title",
    text: "texto",
    player_inventory: "inventário do jogador",
    slot: "slot",
    energy_bar: "barra de energia",
    fluid_bar: "barra de fluido",
    progress_bar: "barra de progresso",
    arrow: "seta",
    entity: "entidade"
  };

  function guiLabel(type) {
    return GUI_ELEMENT_LABELS[type] || type;
  }

  function guiElementUsesSize(type) {
    return ["energy_bar", "fluid_bar", "progress_bar", "arrow", "entity"].indexOf(type) !== -1;
  }

  function guiElementUsesText(type) {
    return ["title", "text", "player_inventory"].indexOf(type) !== -1;
  }

  function guiElementUsesDirection(type) {
    return ["energy_bar", "fluid_bar", "progress_bar", "arrow"].indexOf(type) !== -1;
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
    var slots = Array.isArray(state.itemSlots) ? state.itemSlots : [];
    var hasSlotCoordinates = slots.some(guiSlotHasCoordinate);
    var hasRecipe = state.type === "recipe" && trim(state.recipeType) !== "";
    return {
      title: true,
      text: true,
      player_inventory: true,
      slot: slots.some(isPlainObject) && !hasSlotCoordinates,
      energy_bar: Boolean(capabilities && isPlainObject(capabilities.energy)),
      fluid_bar: fluids.length > 0,
      progress_bar: hasRecipe,
      arrow: hasRecipe,
      entity: hasRecipe
    };
  }

  function guiElementUnavailableReason(type) {
    if (type === "energy_bar") {
      return "Adicione energia na etapa 05 para liberar energy_bar.";
    }
    if (type === "fluid_bar") {
      return "Adicione um tanque na etapa 05 para liberar fluid_bar.";
    }
    if (type === "progress_bar" || type === "arrow" || type === "entity") {
      return "Use type=recipe com recipe_type para liberar progress_bar, arrow e entity.";
    }
    if (type === "slot") {
      return "slot exige item_slots sem x/y (formato antigo).";
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
    if (!availability.progress_bar) {
      missing.push("progress_bar/arrow/entity (recipe)");
    }
    if (!availability.slot) {
      missing.push("slot (item_slots sem x/y)");
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
      option.title = allowed ? "" : guiElementUnavailableReason(option.value);
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
    var texture = trim(gui.texture) ? "textura própria" : "sem textura";
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

  function guiTypeOptions(selected) {
    var availability = guiElementAvailability();
    return V.GUI_ELEMENT_TYPES.map(function (type) {
      var allowed = type === selected || availability[type] !== false;
      return "<option value='" + esc(type) + "'" + (type === selected ? " selected" : "") +
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

  function guiElementForm(element) {
    var type = element.type;
    var html = "<div class='controller-form gui-element-form'>";
    html += "<div class='field'><label>type</label><select data-gui-element-field='type'>" +
      guiTypeOptions(type) + "</select></div>";
    html += guiInputField("x", "x", element.x, "number", "1");
    html += guiInputField("y", "y", element.y, "number", "1");
    if (guiElementUsesSize(type)) {
      html += guiInputField("width", "width", element.width, "number", "1");
      html += guiInputField("height", "height", element.height, "number", "1");
    }
    if (guiElementUsesText(type)) {
      html += guiInputField("text", "text", element.text, "text", undefined, "chave de tradução");
    }
    if (type === "slot") {
      html += guiInputField("slot", "slot", element.slot, "text", undefined, "item_slots.id");
    }
    if (type === "energy_bar" || type === "fluid_bar") {
      html += guiInputField("capability", "capability", element.capability, "text", undefined,
        type === "energy_bar" ? "energy.id" : "fluids[].id");
    }
    if (type === "title" || type === "text" || type === "player_inventory" ||
        type === "energy_bar" || type === "progress_bar") {
      html += guiInputField("color", "color", element.color, "text", undefined, "vazio = default");
    }
    if (type === "title" || type === "text" || type === "player_inventory") {
      html += "<div class='field gui-check-field'><label><input type='checkbox' data-gui-element-field='shadow'" +
        (element.shadow ? " checked" : "") + "> shadow</label></div>";
    }
    if (guiElementUsesDirection(type)) {
      html += "<div class='field'><label>direction</label><select data-gui-element-field='direction'>" +
        guiDirectionOptions(element.direction) + "</select></div>";
    }
    if (guiElementUsesTexture(type)) {
      html += guiInputField("u", "u", element.u, "number", "1");
      html += guiInputField("v", "v", element.v, "number", "1");
    }
    return html + "</div>";
  }

  function guiElementMarkup(element, index) {
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
    return "<div class='gui-element-card' data-gui-element-index='" + index + "'>" +
      "<div class='gui-element-head'><div><strong>" + (index + 1) + ". " + esc(guiLabel(element.type)) +
      "</strong><small>" + esc(meta.join(" - ") || element.type) + "</small></div>" +
      "<button type='button' class='btn btn-tiny btn-danger' data-action='remove-gui-element'>remover</button></div>" +
      guiElementForm(element) + "</div>";
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
      return { width: 162, height: 76 };
    }
    if (type === "title" || type === "text") {
      return { width: safeWidth || 80, height: safeHeight || 10 };
    }
    if (type === "slot") {
      return { width: 18, height: 18 };
    }
    if (type === "energy_bar" || type === "fluid_bar") {
      return { width: safeWidth || 12, height: safeHeight || 52 };
    }
    if (type === "progress_bar") {
      return { width: safeWidth || 57, height: safeHeight || 3 };
    }
    if (type === "arrow") {
      return { width: safeWidth || 22, height: safeHeight || 16 };
    }
    if (type === "entity") {
      return { width: safeWidth || 32, height: safeHeight || 32 };
    }
    return { width: safeWidth || 16, height: safeHeight || 16 };
  }

  function guiCanvasLabel(element, type) {
    if (type === "title" || type === "text") {
      return trim(element.text) || guiLabel(type);
    }
    if (type === "player_inventory") {
      return "inventário";
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
    if (type === "progress_bar") {
      return "progresso";
    }
    if (type === "arrow") {
      return "→";
    }
    if (type === "entity") {
      return "entidade";
    }
    return guiLabel(type);
  }

  function guiCanvasElementMarkup(element, index) {
    var type = trim(element.type) || "text";
    var x = parseIntStrict(element.x);
    var y = parseIntStrict(element.y);
    var width = parseIntStrict(element.width);
    var height = parseIntStrict(element.height);
    var size = guiCanvasDisplaySize(type, width, height);
    var style = "left:" + (x === null ? 0 : x) + "px;top:" + (y === null ? 0 : y) +
      "px;width:" + size.width + "px;height:" + size.height + "px;";
    var isBar = ["energy_bar", "fluid_bar", "progress_bar"].indexOf(type) !== -1;
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
    }
    var title = guiLabel(type) + " • x=" + (x === null ? 0 : x) + " y=" + (y === null ? 0 : y);
    if (trim(element.capability)) {
      title += " • " + trim(element.capability);
    }
    var showLabel = !isBar || (size.width >= 18 && size.height >= 14);
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
    canvas.setAttribute("data-scale", String(scale));
    canvas.setAttribute("data-gui-width", String(dimensions.width));
    canvas.setAttribute("data-gui-height", String(dimensions.height));
    var parts = elements.map(guiCanvasElementMarkup);
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

  function ensureGui() {
    if (!isPlainObject(state.gui)) {
      state.gui = V.createGui();
      if (Array.isArray(state.itemSlots) && state.itemSlots.length > 0) {
        state.gui.elements.push(V.createGuiElement("player_inventory"));
      }
    }
    return state.gui;
  }

  function addGui() {
    ensureGui();
    refresh();
    focusElement(guiElementField("texture") || $("guiPanel"), false);
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
    gui.elements.push(element);
    refresh();
    var card = guiElementCardElement(gui.elements.length - 1);
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
    var action = closest(event.target, "[data-action='remove-gui-element']");
    if (!action) {
      return;
    }
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
    var size = guiCanvasDisplaySize(trim(element.type), parseIntStrict(element.width), parseIntStrict(element.height));
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
    drag.target.style.left = x + "px";
    drag.target.style.top = y + "px";
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
    return JSON.stringify(V.buildRecipe(recipe, recipeAvailability()), null, 2);
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

  function recipeCardElement(index) {
    return document.querySelector(".recipe-card[data-recipe-index='" + index + "']");
  }

  function recipeCardHtml(recipe, index) {
    var file = recipeFile(recipe);
    var errors = recipeErrors(index);
    var feedback = errors.length > 0
      ? errors[0].message + (errors.length > 1 ? " (+" + (errors.length - 1) + ")" : "")
      : "";
    var disabled = errors.length > 0 ? " disabled" : "";
    var availability = recipeAvailability();
    var currentType = trim(recipe.outputType) || "item";
    var outputTypes = [];
    if (availability.itemOutput) {
      outputTypes.push("item");
    }
    if (availability.fluid) {
      outputTypes.push("fluid");
    }
    outputTypes.push("entity");
    if (outputTypes.indexOf(currentType) === -1) {
      outputTypes.unshift(currentType);
    }
    var outputTypeOptions = outputTypes.map(function (type) {
      var unavailable = (type === "item" && !availability.itemOutput) ||
        (type === "fluid" && !availability.fluid);
      return "<option value='" + esc(type) + "'" +
        (type === currentType ? " selected" : "") +
        (unavailable ? " disabled" : "") + ">" + esc(type) +
        (unavailable ? " (indisponível)" : "") + "</option>";
    }).join("");
    var outputFields = "";
    if (currentType === "fluid") {
      outputFields = "<label class='field'><span>output.id</span><input data-recipe-field='resultId' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.resultId) + "' placeholder='minecraft:water'></label>" +
        "<label class='field'><span>output.amount</span><input data-recipe-field='resultAmount' type='number' min='1' value='" + esc(recipe.resultAmount) + "'></label>";
    } else if (currentType === "entity") {
      outputFields = "<label class='field'><span>output.id</span><input data-recipe-field='resultId' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.resultId) + "' placeholder='minecraft:pig'></label>" +
        "<label class='field'><span>output.spawn</span><input data-recipe-field='resultSpawn' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.resultSpawn) + "' placeholder='C ou minecraft:iron_block'></label>";
    } else {
      outputFields = "<label class='field'><span>output.id</span><input data-recipe-field='resultId' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.resultId) + "' placeholder='minecraft:gold_ingot'></label>" +
        "<label class='field'><span>output.count</span><input data-recipe-field='resultCount' type='number' min='1' max='99' value='" + esc(recipe.resultCount) + "'></label>";
    }
    var notices = [];
    if (!availability.energy && (Number(recipe.energy) || 0) !== 0) {
      notices.push("Energia ignorada: esta máquina não tem capabilities.energy.");
    }
    if (!availability.fluid && trim(recipe.fluidsRaw)) {
      notices.push("Fluidos ignorados: esta máquina não tem tanque.");
    }
    if (currentType === "item" && !availability.itemOutput) {
      notices.push("Esta máquina não tem item_slot de saída.");
    }
    if (currentType === "fluid" && !availability.fluid) {
      notices.push("Esta máquina não tem tanque para saída de fluido.");
    }
    var noticesHtml = notices.length > 0
      ? "<div class='recipe-notes'>" + notices.map(function (message) {
        return "<p class='field-hint'>" + esc(message) + "</p>";
      }).join("") + "</div>"
      : "";
    var fluidField = availability.fluid
      ? "<label class='field'><span>fluid_ingredients</span><textarea data-recipe-field='fluidsRaw' rows='3' spellcheck='false' placeholder='minecraft:water 50'>" + esc(recipe.fluidsRaw) + "</textarea></label>"
      : "";
    var componentsField = currentType === "item"
      ? "<label class='field'><span>output.components (JSON)</span><textarea data-recipe-field='componentsRaw' rows='3' spellcheck='false' placeholder='{}'>" + esc(recipe.componentsRaw) + "</textarea></label>"
      : "";
    var energyField = availability.energy
      ? "<label class='field'><span>energy</span><input data-recipe-field='energy' type='number' min='0' value='" + esc(recipe.energy) + "'></label>"
      : "";
    return "<article class='recipe-card" + (errors.length > 0 ? " is-invalid" : "") +
      "' data-recipe-index='" + index + "'>" +
      "<div class='recipe-card-head'><div><span class='recipe-kicker'>receita " + pad2(index + 1) +
      "</span><h3 data-recipe-title>" + esc(file || "sem arquivo") +
      "</h3><code class='recipe-path' data-recipe-path>" + esc(recipePath(recipe)) +
      "</code></div><button type='button' class='btn btn-tiny btn-danger' data-action='remove-recipe'>remover</button></div>" +
      noticesHtml +
      "<div class='recipe-grid'>" +
      "<label class='field'><span>arquivo</span><input data-recipe-field='file' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.file) + "' placeholder='gold_ingot_from_ore'></label>" +
      "<label class='field'><span>type</span><input data-recipe-field='type' type='text' spellcheck='false' autocomplete='off' value='" + esc(recipe.type) + "' placeholder='infinity_nexus_multiblock:exemplo_smelting'></label>" +
      "<label class='field'><span>output.type</span><select data-recipe-field='outputType'>" + outputTypeOptions + "</select></label>" +
      outputFields +
      energyField +
      "<label class='field'><span>time</span><input data-recipe-field='time' type='number' min='1' value='" + esc(recipe.time) + "'></label>" +
      "</div>" +
      "<div class='recipe-textareas'>" +
      "<label class='field'><span>ingredients</span><textarea data-recipe-field='ingredientsRaw' rows='4' spellcheck='false' placeholder='minecraft:iron_ore&#10;#minecraft:coals'>" + esc(recipe.ingredientsRaw) + "</textarea></label>" +
      fluidField +
      componentsField +
      "</div>" +
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
    var note = $("recipeTypeNote");
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

  function onRecipeInput(event) {
    var target = event.target;
    var card = closest(target, ".recipe-card[data-recipe-index]");
    var field = target ? target.getAttribute("data-recipe-field") : "";
    var index = card ? parseInt(card.getAttribute("data-recipe-index"), 10) : -1;
    var recipe = Array.isArray(state.recipes) ? state.recipes[index] : null;
    if (!field || !isPlainObject(recipe)) {
      return;
    }
    recipe[field] = target.value;
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
    if (field === "outputType") {
      renderRecipes();
    } else {
      updateRecipeSummary();
      updateRecipeCards();
    }
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
    recipe.type = state.type === "recipe" ? trim(state.recipeType) : "";
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

  function onRecipeAction(event) {
    var action = closest(event.target, "[data-action]");
    if (!action) {
      return;
    }
    var holder = closest(action, "[data-recipe-index]");
    var index = holder ? parseInt(holder.getAttribute("data-recipe-index"), 10) : -1;
    var name = action.getAttribute("data-action");
    if (name === "remove-recipe") {
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

  function assetRows() {
    var rows = [];
    function add(label, path) {
      rows.push({ label: label, path: path });
    }
    add("definição", filePath());
    (Array.isArray(state.recipes) ? state.recipes : []).forEach(function (recipe, index) {
      add("receita " + pad2(index + 1), recipePath(recipe));
    });
    var controller = isPlainObject(state.controller) ? state.controller : null;
    if (controller) {
      add("controller.block", trim(controller.block) || controllerBlockId());
      var textures = isPlainObject(controller.textures) ? controller.textures : {};
      V.CONTROLLER_FACES.forEach(function (face) {
        add("textura " + face, textureAssetPath(textures[face]));
      });
      if (isPlainObject(controller.formedTextures)) {
        V.CONTROLLER_FACES.forEach(function (face) {
          var path = textureAssetPath(controller.formedTextures[face]);
          if (path) {
            add("formada " + face, path);
          }
        });
      }
      rows.push({
        note: state.namespace === "infinity_nexus_multiblock"
          ? "Controller dinâmico: blockstate e modelo são gerados em memória pelo mod."
          : "Namespace externo: o bloco controller precisa existir e estar registrado em Java."
      });
    } else {
      rows.push({ note: "Sem controller customizado: o mod usa id e texturas fallback." });
    }
    if (isPlainObject(state.controllerRecipe)) {
      rows.push({
        note: "Receita shaped do controller: o mod gera data/" +
          (trim(state.namespace) || "namespace") + "/recipe/controller/" +
          (trim(state.path) || "path") + ".json com resultado no item do controller."
      });
    }
    if (state.namespace !== "infinity_nexus_multiblock") {
      add("blockstate manual", "assets/" + state.namespace + "/blockstates/" + state.path + ".json");
      add("modelo do bloco", "assets/" + state.namespace + "/models/block/" + state.path + ".json");
      add("modelo do item", "assets/" + state.namespace + "/models/item/" + state.path + ".json");
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
      return symbol !== " " && symbol !== state.anchor;
    }).length;
    function card(label, value, title) {
      return "<div class='summary-card'><span>" + esc(label) + "</span><strong title='" +
        esc(title || value) + "'>" + esc(value) + "</strong></div>";
    }
    var typeTitle = state.type === "recipe" ? "recipe · " + (state.recipeType || "sem recipe_type") : "storage";
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
    var controller = isPlainObject(state.controller) ? state.controller : null;
    var controllerValue = controller ? "custom" : "fallback";
    var controllerBlock = controller ? trim(controller.block) : "";
    if (!controllerBlock && controller && state.namespace === "infinity_nexus_multiblock") {
      controllerBlock = controllerBlockId();
    }
    var controllerTitle = controller
      ? (controllerBlock || "sem bloco") + " - " + controllerTextureCount(controller) + "/6 texturas"
      : "id e texturas fallback";
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

  function drawPreview() {
    if (!Preview || !state) {
      return;
    }
    Preview.draw($("preview3d"), state.layers, state.anchor, activeLayer, currentColors, COLORS[1]);
  }

  function refresh(options) {
    if (!state) {
      return;
    }
    var settings = options || {};
    syncControllerBlock();
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
    if (activeStep === "slots" && !settings.skipSlots) {
      renderSlots();
    }
    if (activeStep === "capabilities" && !settings.skipCapabilities) {
      renderCapabilities();
    }
    if (activeStep === "controller" && !settings.skipController) {
      renderController();
    }
    if (activeStep === "controller" && !settings.skipControllerRecipe) {
      renderControllerRecipe();
    }
    if (activeStep === "gui" && !settings.skipGui) {
      renderGui();
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
    activeStep = stepId;
    refresh();
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
    if (field.indexOf("recipe:") === 0) {
      var recipeParts = field.slice("recipe:".length).split(":");
      var recipeIndex = parseInt(recipeParts[0], 10);
      var recipeField = recipeParts.slice(1).join(":");
      var recipeTargets = {
        ingredients: "ingredientsRaw",
        fluids: "fluidsRaw",
        components: "componentsRaw"
      };
      recipeField = recipeTargets[recipeField] || recipeField;
      var recipeCard = recipeCardElement(recipeIndex);
      var recipeInput = recipeCard && recipeField
        ? recipeCard.querySelector("[data-recipe-field='" + recipeField + "']")
        : null;
      focusElement(recipeInput || recipeCard || $("recipeList"), false);
      return;
    }
    if (field === "controller") {
      focusElement($("controllerPanel"), false);
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
    if (field === "block") {
      focusElement($("controllerBlock"), true);
      return;
    }
    if (field === "textures") {
      focusElement(controllerFieldElement("texture:front") || $("controllerPanel"), true);
      return;
    }
    if (field.indexOf("texture:") === 0) {
      focusElement(controllerFieldElement(field), true);
      return;
    }
    if (field === "formedTextures") {
      focusElement(controllerFieldElement("formed:front") || $("controllerPanel"), true);
      return;
    }
    if (field.indexOf("formed:") === 0) {
      focusElement(controllerFieldElement(field), true);
      return;
    }
    if (field === "particle") {
      focusElement($("controllerParticle"), true);
      return;
    }
    if (field === "lightLevel") {
      focusElement($("controllerLightLevel"), true);
      return;
    }
    if (field === "gui") {
      focusElement($("guiPanel"), false);
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
    activeSymbol = chip.getAttribute("data-symbol");
    renderPalette();
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
      activeSymbol = symbol;
      renderPalette();
      showToast("Símbolo " + symbol + " já existe na paleta.");
      return;
    }
    createMatcher(symbol);
    activeSymbol = symbol;
    $("newSymbolInput").value = "";
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
        state = imported.state;
        state.recipes = [];
        importNotes = imported.notes || [];
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
    state = V.createExampleState();
    importNotes = [];
    activeStep = "identity";
    activeLayer = 0;
    activeSymbol = null;
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
    state = V.createDefaultState();
    importNotes = [];
    activeStep = "identity";
    activeLayer = 0;
    activeSymbol = null;
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
    $("anchorSymbol").addEventListener("change", onAnchorChange);
    $("addLayerBtn").addEventListener("click", addLayer);
    $("duplicateLayerBtn").addEventListener("click", duplicateLayer);
    $("removeLayerBtn").addEventListener("click", removeLayer);
    $("layerTabs").addEventListener("click", onLayerTabsClick);
    $("palette").addEventListener("click", onPaletteClick);
    $("focusNewSymbolBtn").addEventListener("click", function () {
      var input = $("newSymbolInput");
      input.focus();
      input.select();
    });
    $("addSymbolForm").addEventListener("submit", function (event) {
      event.preventDefault();
      addNewSymbol($("newSymbolInput").value);
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
    $("addSlotBtn").addEventListener("click", addSlot);
    $("useInventoryRowsBtn").addEventListener("click", useInventoryRows);
    $("slotList").addEventListener("input", onSlotListInput);
    $("slotList").addEventListener("change", onSlotListChange);
    $("slotList").addEventListener("click", onSlotListClick);
    $("addEnergyBtn").addEventListener("click", addEnergy);
    $("addFluidBtn").addEventListener("click", addFluidTank);
    $("removeCapabilitiesBtn").addEventListener("click", removeCapabilities);
    $("capabilityList").addEventListener("input", onCapabilityListInput);
    $("capabilityList").addEventListener("click", onCapabilityListClick);
    $("capabilityPortsPanel").addEventListener("input", onCapabilityPortsInput);
    $("addControllerBtn").addEventListener("click", addController);
    $("controllerFallbackBtn").addEventListener("click", fillControllerFallback);
    $("removeControllerBtn").addEventListener("click", removeController);
    $("controllerForm").addEventListener("input", onControllerFormInput);
    $("addControllerRecipeBtn").addEventListener("click", addControllerRecipe);
    $("removeControllerRecipeBtn").addEventListener("click", removeControllerRecipe);
    $("controllerRecipePalette").addEventListener("click", onControllerRecipePaletteClick);
    $("controllerRecipeGrid").addEventListener("click", onControllerRecipeGridClick);
    $("controllerRecipeGrid").addEventListener("contextmenu", onControllerRecipeGridClick);
    $("controllerRecipeKeyList").addEventListener("input", onControllerRecipeKeyInput);
    $("addGuiBtn").addEventListener("click", addGui);
    $("removeGuiBtn").addEventListener("click", removeGui);
    $("addGuiElementBtn").addEventListener("click", addGuiElement);
    $("guiForm").addEventListener("input", onGuiFormInput);
    $("guiElementList").addEventListener("input", onGuiElementInput);
    $("guiElementList").addEventListener("change", onGuiElementChange);
    $("guiElementList").addEventListener("click", onGuiElementClick);
    $("guiCanvas").addEventListener("pointerdown", beginGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointermove", moveGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointerup", endGuiCanvasDrag);
    $("guiCanvas").addEventListener("pointercancel", endGuiCanvasDrag);
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
    window.addEventListener("resize", function () {
      if (activeStep === "pattern") {
        window.setTimeout(drawPreview, 30);
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
    bindEvents();
    syncInputs();
    renderImportNotes();
    refresh();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
