(function (root, factory) {
  var api = factory(root);
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.ModelPreview3D = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function (root) {
  "use strict";

  var FACES = ["north", "south", "east", "west", "up", "down"];
  var TEXTURE_FACE_ROWS = [
    { key: "front", direction: "north" },
    { key: "top", direction: "up" },
    { key: "down", direction: "down" },
    { key: "left", direction: "west" },
    { key: "right", direction: "east" },
    { key: "back", direction: "south" }
  ];
  var FACE_NORMALS = {
    north: [0, 0, -1],
    south: [0, 0, 1],
    east: [1, 0, 0],
    west: [-1, 0, 0],
    up: [0, 1, 0],
    down: [0, -1, 0]
  };
  var BOX_EDGES = [
    [0, 1], [1, 2], [2, 3], [3, 0],
    [4, 5], [5, 6], [6, 7], [7, 4],
    [0, 4], [1, 5], [2, 6], [3, 7]
  ];
  var FRAME_COLORS = [
    "#ff5f56", "#ffbd2e", "#27c93f", "#4ea1ff",
    "#c678dd", "#56d4dd", "#f78c6c", "#a3e635"
  ];
  var LIGHT_DIR = normalize([0.35, 0.85, 0.45]);
  var BUILTIN_MODELS = null;
  var REFERENCE_IMAGES = null;

  function isObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function toNumber(value) {
    if (typeof value === "number") {
      return Number.isFinite(value) ? value : null;
    }
    if (typeof value === "string" && value.trim() !== "") {
      var parsed = Number(value.trim());
      return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
  }

  function normalizeId(value, defaultNamespace) {
    var text = typeof value === "string" ? value.trim() : "";
    if (!text) {
      return "";
    }
    return text.indexOf(":") === -1 ? (defaultNamespace || "minecraft") + ":" + text : text;
  }

  function add(left, right) {
    return [left[0] + right[0], left[1] + right[1], left[2] + right[2]];
  }

  function sub(left, right) {
    return [left[0] - right[0], left[1] - right[1], left[2] - right[2]];
  }

  function dot(left, right) {
    return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
  }

  function cross(left, right) {
    return [
      left[1] * right[2] - left[2] * right[1],
      left[2] * right[0] - left[0] * right[2],
      left[0] * right[1] - left[1] * right[0]
    ];
  }

  function length(vector) {
    return Math.sqrt(dot(vector, vector));
  }

  function normalize(vector) {
    var size = length(vector);
    return size > 0 ? [vector[0] / size, vector[1] / size, vector[2] / size] : [0, 0, 0];
  }

  function rotateVector(vector, axis, angleDegrees) {
    var angle = angleDegrees * Math.PI / 180;
    var cosine = Math.cos(angle);
    var sine = Math.sin(angle);
    var x = vector[0];
    var y = vector[1];
    var z = vector[2];
    if (axis === "x") {
      return [x, y * cosine - z * sine, y * sine + z * cosine];
    }
    if (axis === "y") {
      return [x * cosine + z * sine, y, -x * sine + z * cosine];
    }
    return [x * cosine - y * sine, x * sine + y * cosine, z];
  }

  function rotatePoint(point, origin, axis, angleDegrees) {
    return add(rotateVector(sub(point, origin), axis, angleDegrees), origin);
  }

  function fullCube(mapping) {
    var faces = {};
    FACES.forEach(function (direction) {
      var texture = mapping[direction] || mapping.all || null;
      if (texture) {
        faces[direction] = { texture: texture };
      }
    });
    return { from: [0, 0, 0], to: [16, 16, 16], faces: faces };
  }

  function fallbackCube(textures) {
    var order = {
      down: ["down", "bottom", "side", "all", "particle"],
      up: ["up", "top", "side", "all", "particle"],
      north: ["north", "front", "side", "all", "particle"],
      south: ["south", "back", "side", "all", "particle"],
      west: ["west", "left", "side", "all", "particle"],
      east: ["east", "right", "side", "all", "particle"]
    };
    var faces = {};
    FACES.forEach(function (direction) {
      var keys = order[direction];
      for (var index = 0; index < keys.length; index += 1) {
        if (textures[keys[index]] !== undefined) {
          faces[direction] = { texture: "#" + keys[index] };
          return;
        }
      }
    });
    return { from: [0, 0, 0], to: [16, 16, 16], faces: faces };
  }

  function builtins() {
    if (BUILTIN_MODELS) {
      return BUILTIN_MODELS;
    }
    var cube = {
      parent: "minecraft:block/block",
      elements: [fullCube({
        down: "#down",
        up: "#up",
        north: "#north",
        south: "#south",
        west: "#west",
        east: "#east"
      })]
    };
    var orientable = {
      parent: "minecraft:block/block",
      textures: {
        particle: "#front",
        back: "#side"
      },
      elements: [fullCube({
        down: "#bottom",
        up: "#top",
        north: "#front",
        south: "#back",
        west: "#side",
        east: "#side"
      })]
    };
    BUILTIN_MODELS = {
      "minecraft:block/block": { textures: {}, elements: [] },
      "minecraft:block/cube": cube,
      "minecraft:block/cube_all": {
        parent: "minecraft:block/cube",
        textures: {
          particle: "#all",
          down: "#all",
          up: "#all",
          north: "#all",
          south: "#all",
          west: "#all",
          east: "#all"
        }
      },
      "minecraft:block/cube_bottom_top": {
        parent: "minecraft:block/cube",
        textures: {
          particle: "#side",
          down: "#bottom",
          up: "#top",
          north: "#side",
          south: "#side",
          west: "#side",
          east: "#side"
        }
      },
      "minecraft:block/cube_column": {
        parent: "minecraft:block/cube",
        textures: {
          particle: "#side",
          down: "#end",
          up: "#end",
          north: "#side",
          south: "#side",
          west: "#side",
          east: "#side"
        }
      },
      "minecraft:block/cube_directional": {
        parent: "minecraft:block/cube",
        textures: {
          particle: "#side",
          down: "#down",
          up: "#up",
          north: "#north",
          south: "#south",
          west: "#west",
          east: "#east"
        }
      },
      "minecraft:block/orientable_with_bottom": orientable,
      "minecraft:block/orientable": {
        parent: "minecraft:block/orientable_with_bottom",
        textures: {
          bottom: "#top"
        }
      },
      "minecraft:block/cross": {
        parent: "minecraft:block/block",
        textures: {
          particle: "#cross"
        },
        elements: [
          {
            from: [0.8, 0, 8],
            to: [15.2, 16, 8],
            faces: {
              north: { texture: "#cross" },
              south: { texture: "#cross" }
            }
          },
          {
            from: [8, 0, 0.8],
            to: [8, 16, 15.2],
            faces: {
              east: { texture: "#cross" },
              west: { texture: "#cross" }
            }
          }
        ]
      },
      "minecraft:block/tinted_cross": { parent: "minecraft:block/cross" },
      "minecraft:block/cube_mirrored": { parent: "minecraft:block/cube_all" },
      "minecraft:block/cube_mirrored_all": { parent: "minecraft:block/cube_all" }
    };
    return BUILTIN_MODELS;
  }

  function builtinLookup(identifier) {
    return builtins()[normalizeId(identifier)] || null;
  }

  function resolveTextureId(textures, value, depth, defaultNamespace) {
    if (typeof value !== "string" || !value.trim() || depth > 16) {
      return null;
    }
    var text = value.trim();
    if (text.charAt(0) === "#") {
      return resolveTextureId(textures, textures[text.slice(1)], depth + 1, defaultNamespace);
    }
    return normalizeId(text, defaultNamespace);
  }

  function resolveChain(raw, lookup) {
    var chain = [];
    var current = raw;
    var seen = {};
    var missingParent = null;
    while (current) {
      chain.push(current);
      if (!current.parent) {
        break;
      }
      var parentId = normalizeId(current.parent);
      if (!parentId || seen[parentId]) {
        break;
      }
      seen[parentId] = true;
      var parent = lookup(parentId);
      if (!parent) {
        missingParent = parentId;
        break;
      }
      current = parent;
    }
    var textures = {};
    var elements = null;
    for (var index = chain.length - 1; index >= 0; index -= 1) {
      var model = chain[index];
      if (isObject(model.textures)) {
        Object.keys(model.textures).forEach(function (key) {
          textures[key] = model.textures[key];
        });
      }
      if (Array.isArray(model.elements) && model.elements.length > 0) {
        elements = model.elements;
      }
    }
    return { textures: textures, elements: elements, missingParent: missingParent };
  }

  function hashString(value) {
    var hash = 2166136261;
    var text = String(value);
    for (var index = 0; index < text.length; index += 1) {
      hash ^= text.charCodeAt(index);
      hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
  }

  function colorFor(seed) {
    var hash = hashString(seed);
    var hue = hash % 360;
    var saturation = 55 + (hash >>> 8) % 25;
    var lightness = 42 + (hash >>> 16) % 16;
    return "hsl(" + hue + ", " + saturation + "%, " + lightness + "%)";
  }

  function normalizeElements(rawElements, textures, defaultNamespace) {
    var result = [];
    (Array.isArray(rawElements) ? rawElements : []).forEach(function (element, elementIndex) {
      if (!isObject(element) || !Array.isArray(element.from) || !Array.isArray(element.to)) {
        return;
      }
      var from = element.from.slice(0, 3).map(toNumber);
      var to = element.to.slice(0, 3).map(toNumber);
      if (from.length !== 3 || to.length !== 3
        || from.some(function (value) { return value === null; })
        || to.some(function (value) { return value === null; })) {
        return;
      }
      var faces = {};
      FACES.forEach(function (direction) {
        var face = element.faces && element.faces[direction];
        if (!isObject(face)) {
          return;
        }
        var uv = Array.isArray(face.uv) && face.uv.length === 4
          ? face.uv.slice(0, 4).map(toNumber)
          : [0, 0, 16, 16];
        if (uv.some(function (value) { return value === null; })) {
          uv = [0, 0, 16, 16];
        }
        var textureId = resolveTextureId(textures, face.texture, 0, defaultNamespace);
        faces[direction] = {
          uv: uv,
          rotation: toNumber(face.rotation) || 0,
          texture: typeof face.texture === "string" ? face.texture : null,
          textureId: textureId,
          color: colorFor(textureId || ("element:" + elementIndex + ":" + direction)),
          cullface: typeof face.cullface === "string" ? face.cullface : null
        };
      });
      var rotation = null;
      if (isObject(element.rotation)) {
        var angle = toNumber(element.rotation.angle);
        var axis = element.rotation.axis;
        if (angle !== null && angle !== 0 && (axis === "x" || axis === "y" || axis === "z")) {
          var origin = Array.isArray(element.rotation.origin) && element.rotation.origin.length === 3
            ? element.rotation.origin.slice(0, 3).map(toNumber)
            : [8, 8, 8];
          if (origin.some(function (value) { return value === null; })) {
            origin = [8, 8, 8];
          }
          rotation = { angle: angle, axis: axis, origin: origin };
        }
      }
      result.push({ from: from, to: to, rotation: rotation, faces: faces });
    });
    return result;
  }

  function elementBounds(elements) {
    var min = [Infinity, Infinity, Infinity];
    var max = [-Infinity, -Infinity, -Infinity];
    elements.forEach(function (element) {
      var corners = boxCorners(element.from, element.to);
      if (element.rotation) {
        corners = corners.map(function (point) {
          return rotatePoint(point, element.rotation.origin, element.rotation.axis, element.rotation.angle);
        });
      }
      corners.forEach(function (point) {
        for (var index = 0; index < 3; index += 1) {
          if (point[index] < min[index]) {
            min[index] = point[index];
          }
          if (point[index] > max[index]) {
            max[index] = point[index];
          }
        }
      });
    });
    if (!Number.isFinite(min[0])) {
      return { min: [0, 0, 0], max: [16, 16, 16] };
    }
    return { min: min, max: max };
  }

  function isRightAngle(angle) {
    var normalized = ((Number(angle) % 360) + 360) % 360;
    return Math.abs(normalized - Math.round(normalized / 90) * 90) < 1e-6;
  }

  function snapValue(value, precision) {
    var step = Number(precision);
    if (!Number.isFinite(step) || step <= 0) {
      return value;
    }
    return Number((Math.round(value / step) * step).toFixed(6));
  }

  function shapeBoxes(parsed, options) {
    var elements = parsed && Array.isArray(parsed.elements) ? parsed.elements : [];
    var precision = options && options.precision !== undefined ? Number(options.precision) : 0.0001;
    var limit = options && options.limit !== undefined ? Number(options.limit) : 16;
    if (!Number.isFinite(limit) || limit <= 0) {
      limit = 16;
    }
    var boxes = [];
    var seen = {};
    var skippedRotation = 0;
    var skippedFlat = 0;
    var clipped = 0;
    elements.forEach(function (element) {
      if (!element || !Array.isArray(element.from) || !Array.isArray(element.to)) {
        return;
      }
      var rotation = element.rotation;
      if (rotation && !isRightAngle(rotation.angle)) {
        skippedRotation += 1;
        return;
      }
      var corners = boxCorners(element.from, element.to);
      if (rotation) {
        corners = corners.map(function (point) {
          return rotatePoint(point, rotation.origin, rotation.axis, rotation.angle);
        });
      }
      var min = [Infinity, Infinity, Infinity];
      var max = [-Infinity, -Infinity, -Infinity];
      corners.forEach(function (point) {
        for (var index = 0; index < 3; index += 1) {
          if (point[index] < min[index]) {
            min[index] = point[index];
          }
          if (point[index] > max[index]) {
            max[index] = point[index];
          }
        }
      });
      var wasClipped = min.some(function (value) { return value < 0 || value > limit; })
        || max.some(function (value) { return value < 0 || value > limit; });
      if (wasClipped) {
        clipped += 1;
      }
      var from = min.map(function (value) {
        return snapValue(Math.max(0, Math.min(limit, value)), precision);
      });
      var to = max.map(function (value) {
        return snapValue(Math.max(0, Math.min(limit, value)), precision);
      });
      if (from[0] >= to[0] || from[1] >= to[1] || from[2] >= to[2]) {
        skippedFlat += 1;
        return;
      }
      var key = from.join(",") + "|" + to.join(",");
      if (seen[key]) {
        return;
      }
      seen[key] = true;
      boxes.push([from[0], from[1], from[2], to[0], to[1], to[2]]);
    });
    return {
      boxes: boxes,
      skippedRotation: skippedRotation,
      skippedFlat: skippedFlat,
      clipped: clipped
    };
  }

  function collectEntries(textures, defaultNamespace) {
    var byId = {};
    Object.keys(textures || {}).forEach(function (key) {
      if (key === "particle") {
        return;
      }
      var identifier = resolveTextureId(textures, "#" + key, 0, defaultNamespace);
      if (!identifier) {
        return;
      }
      if (!byId[identifier]) {
        byId[identifier] = {
          identifier: identifier,
          keys: [],
          vanilla: identifier.indexOf("minecraft:") === 0
        };
      }
      byId[identifier].keys.push(key);
    });
    return Object.keys(byId).map(function (identifier) {
      return byId[identifier];
    }).sort(function (left, right) {
      return left.identifier < right.identifier ? -1 : left.identifier > right.identifier ? 1 : 0;
    });
  }

  function textureRow(key, direction, raw, identifier) {
    return {
      key: key,
      direction: direction,
      raw: typeof raw === "string" ? raw : "",
      identifier: identifier || null,
      vanilla: Boolean(identifier) && identifier.indexOf("minecraft:") === 0
    };
  }

  function textureRows(elements, textures, defaultNamespace) {
    var rows = [];
    var source = Array.isArray(elements) ? elements : [];
    TEXTURE_FACE_ROWS.forEach(function (spec) {
      var face = null;
      source.some(function (element) {
        if (element && element.faces && element.faces[spec.direction]) {
          face = element.faces[spec.direction];
          return true;
        }
        return false;
      });
      if (!face) {
        return;
      }
      rows.push(textureRow(spec.key, spec.direction, face.texture, face.textureId));
    });

    var particleRaw = textures && textures.particle !== undefined
      ? textures.particle
      : textures && textures.front !== undefined
        ? textures.front
        : textures && textures.north !== undefined
          ? textures.north
          : textures && textures.all !== undefined
            ? textures.all
            : null;
    if (particleRaw !== null) {
      var particleIdentifier = resolveTextureId(textures, "#particle", 0, defaultNamespace);
      if (!particleIdentifier) {
        particleIdentifier = resolveTextureId(textures, "#front", 0, defaultNamespace);
      }
      if (!particleIdentifier) {
        particleIdentifier = resolveTextureId(textures, "#north", 0, defaultNamespace);
      }
      if (!particleIdentifier) {
        particleIdentifier = resolveTextureId(textures, "#all", 0, defaultNamespace);
      }
      if (particleIdentifier) {
        rows.push(textureRow("particle", null, particleRaw, particleIdentifier));
      }
    }
    return rows;
  }

  function applyTextureOverrides(parsed, overrides, options) {
    if (!isObject(parsed) || !isObject(overrides)) {
      return parsed;
    }
    var defaultNamespace = options && options.namespace
      ? String(options.namespace)
      : parsed.namespace || "minecraft";
    var textures = {};
    Object.keys(parsed.textures || {}).forEach(function (key) {
      textures[key] = parsed.textures[key];
    });
    var changed = false;
    Object.keys(overrides).forEach(function (key) {
      var value = overrides[key];
      if (typeof value !== "string" || !value.trim()) {
        return;
      }
      var identifier = normalizeId(value, defaultNamespace);
      if (!identifier) {
        return;
      }
      textures[key] = identifier;
      changed = true;
    });
    if (!changed) {
      return parsed;
    }
    if (!textures.particle && textures.front) {
      textures.particle = textures.front;
    } else if (!textures.particle && textures.north) {
      textures.particle = textures.north;
    } else if (!textures.particle && textures.all) {
      textures.particle = textures.all;
    }
    var elements = (Array.isArray(parsed.elements) ? parsed.elements : []).map(function (element) {
      var faces = {};
      FACES.forEach(function (direction) {
        var face = element.faces && element.faces[direction];
        if (!face) {
          return;
        }
        var textureId = resolveTextureId(textures, face.texture, 0, defaultNamespace);
        faces[direction] = Object.assign({}, face, {
          textureId: textureId,
          color: textureId ? colorFor(textureId) : face.color
        });
      });
      return Object.assign({}, element, { faces: faces });
    });
    return Object.assign({}, parsed, {
      textures: textures,
      elements: elements,
      entries: collectEntries(textures, defaultNamespace),
      textureRows: textureRows(elements, textures, defaultNamespace)
    });
  }

  function applyKeyOverrides(parsed, overrides) {
    if (!isObject(parsed) || !isObject(overrides)) {
      return parsed;
    }
    var changed = false;
    var elements = (Array.isArray(parsed.elements) ? parsed.elements : []).map(function (element) {
      if (!isObject(element) || !isObject(element.faces)) {
        return element;
      }
      var faces = {};
      var faceChanged = false;
      Object.keys(element.faces).forEach(function (direction) {
        var face = element.faces[direction];
        faces[direction] = face;
        if (!isObject(face) || typeof face.texture !== "string" || face.texture.charAt(0) !== "#") {
          return;
        }
        var key = face.texture.slice(1);
        var identifier = typeof overrides[key] === "string" ? overrides[key].trim() : "";
        if (!identifier || face.textureId) {
          return;
        }
        faces[direction] = Object.assign({}, face, { textureId: identifier });
        faceChanged = true;
      });
      if (!faceChanged) {
        return element;
      }
      changed = true;
      return Object.assign({}, element, { faces: faces });
    });
    if (!changed) {
      return parsed;
    }
    var rows = (Array.isArray(parsed.textureRows) ? parsed.textureRows : []).map(function (row) {
      if (!isObject(row) || typeof row.raw !== "string" || row.raw.charAt(0) !== "#") {
        return row;
      }
      var key = row.raw.slice(1);
      var identifier = typeof overrides[key] === "string" ? overrides[key].trim() : "";
      if (!identifier || row.identifier) {
        return row;
      }
      return Object.assign({}, row, {
        identifier: identifier,
        vanilla: identifier.indexOf("minecraft:") === 0
      });
    });
    return Object.assign({}, parsed, { elements: elements, textureRows: rows });
  }

  function parseModel(text, options) {
    var raw = typeof text === "string" ? JSON.parse(text) : text;
    if (!isObject(raw)) {
      throw new Error("O JSON do modelo precisa ser um objeto.");
    }
    var lookup = options && typeof options.lookup === "function" ? options.lookup : builtinLookup;
    var defaultNamespace = options && options.namespace ? String(options.namespace) : "minecraft";
    var resolved = resolveChain(raw, lookup);
    var textures = resolved.textures;
    var sourceElements = resolved.elements && resolved.elements.length > 0
      ? resolved.elements
      : [fallbackCube(textures)];
    var elements = normalizeElements(sourceElements, textures, defaultNamespace);
    return {
      raw: raw,
      textures: textures,
      namespace: defaultNamespace,
      elements: elements,
      bounds: elementBounds(elements),
      entries: collectEntries(textures, defaultNamespace),
      textureRows: textureRows(elements, textures, defaultNamespace),
      missingParent: resolved.missingParent,
      usedFallbackCube: !resolved.elements || resolved.elements.length === 0
    };
  }

  function faceCorners(from, to, direction) {
    var x1 = from[0];
    var y1 = from[1];
    var z1 = from[2];
    var x2 = to[0];
    var y2 = to[1];
    var z2 = to[2];
    if (direction === "north") {
      return [[x2, y2, z1], [x1, y2, z1], [x1, y1, z1], [x2, y1, z1]];
    }
    if (direction === "south") {
      return [[x1, y2, z2], [x2, y2, z2], [x2, y1, z2], [x1, y1, z2]];
    }
    if (direction === "east") {
      return [[x2, y2, z2], [x2, y2, z1], [x2, y1, z1], [x2, y1, z2]];
    }
    if (direction === "west") {
      return [[x1, y2, z1], [x1, y2, z2], [x1, y1, z2], [x1, y1, z1]];
    }
    if (direction === "up") {
      return [[x1, y2, z1], [x2, y2, z1], [x2, y2, z2], [x1, y2, z2]];
    }
    return [[x1, y1, z1], [x2, y1, z1], [x2, y1, z2], [x1, y1, z2]];
  }

  function boxCorners(from, to) {
    var x1 = from[0];
    var y1 = from[1];
    var z1 = from[2];
    var x2 = to[0];
    var y2 = to[1];
    var z2 = to[2];
    return [
      [x1, y1, z1], [x2, y1, z1], [x2, y1, z2], [x1, y1, z2],
      [x1, y2, z1], [x2, y2, z1], [x2, y2, z2], [x1, y2, z2]
    ];
  }

  function rotateUVPoints(points, rotation) {
    var angle = ((Number(rotation) || 0) % 360 + 360) % 360;
    if (angle === 0) {
      return points;
    }
    var centerU = (points[0][0] + points[2][0]) / 2;
    var centerV = (points[0][1] + points[2][1]) / 2;
    return points.map(function (point) {
      var du = point[0] - centerU;
      var dv = point[1] - centerV;
      if (angle === 90) {
        return [centerU + dv, centerV - du];
      }
      if (angle === 180) {
        return [centerU - du, centerV - dv];
      }
      if (angle === 270) {
        return [centerU - dv, centerV + du];
      }
      return [point[0], point[1]];
    });
  }

  function elementQuads(element) {
    var quads = [];
    FACES.forEach(function (direction) {
      var face = element.faces[direction];
      if (!face) {
        return;
      }
      var corners = faceCorners(element.from, element.to, direction);
      var normal = FACE_NORMALS[direction].slice();
      if (element.rotation) {
        corners = corners.map(function (point) {
          return rotatePoint(point, element.rotation.origin, element.rotation.axis, element.rotation.angle);
        });
        normal = rotateVector(normal, element.rotation.axis, element.rotation.angle);
      }
      var uv = face.uv;
      var uvPoints = [
        [uv[0], uv[1]],
        [uv[2], uv[1]],
        [uv[2], uv[3]],
        [uv[0], uv[3]]
      ];
      if (face.rotation) {
        uvPoints = rotateUVPoints(uvPoints, face.rotation);
      }
      quads.push({
        points: corners,
        normal: normalize(normal),
        uvPoints: uvPoints,
        textureId: face.textureId,
        color: face.color
      });
    });
    return quads;
  }

  function projectPoint(point, camera) {
    var delta = sub(point, camera.target);
    return {
      x: camera.cx + dot(delta, camera.right) * camera.scale,
      y: camera.cy - dot(delta, camera.up) * camera.scale,
      depth: dot(delta, camera.forward)
    };
  }

  function makeCamera(width, height, options, scene) {
    var yaw = toNumber(options.yaw);
    var pitch = toNumber(options.pitch);
    var zoom = toNumber(options.zoom);
    yaw = yaw === null ? 2.95 : yaw;
    pitch = pitch === null ? 0.42 : pitch;
    zoom = zoom === null ? 1 : zoom;
    pitch = Math.max(-1.45, Math.min(1.45, pitch));
    var cosine = Math.cos(pitch);
    var sine = Math.sin(pitch);
    var direction = [cosine * Math.sin(yaw), sine, cosine * Math.cos(yaw)];
    var forward = [-direction[0], -direction[1], -direction[2]];
    var right = normalize(cross(forward, [0, 1, 0]));
    var up = cross(right, forward);
    var bounds = scene && scene.bounds ? scene.bounds : { min: [0, 0, 0], max: [16, 16, 16] };
    var target = [
      (bounds.min[0] + bounds.max[0]) / 2,
      (bounds.min[1] + bounds.max[1]) / 2,
      (bounds.min[2] + bounds.max[2]) / 2
    ];
    var span = Math.max(
      bounds.max[0] - bounds.min[0],
      bounds.max[1] - bounds.min[1],
      bounds.max[2] - bounds.min[2],
      1
    );
    return {
      target: target,
      forward: forward,
      right: right,
      up: up,
      scale: Math.min(width, height) / (span * 1.7) * zoom,
      cx: width / 2,
      cy: height / 2 + height * 0.04
    };
  }

  function makeStripeImage(colorA, colorB) {
    var size = 16;
    var canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    var context = canvas.getContext("2d");
    for (var row = 0; row < size; row += 1) {
      context.fillStyle = row % 2 === 0 ? colorA : colorB;
      context.fillRect(0, row, size, 1);
    }
    return canvas;
  }

  function referenceImage(identifier) {
    if (!REFERENCE_IMAGES) {
      REFERENCE_IMAGES = {
        "ref:behind": makeStripeImage("#2f3a42", "#4b5a66"),
        "ref:left": makeStripeImage("#42322f", "#66504b")
      };
    }
    return REFERENCE_IMAGES[identifier] || null;
  }

  function resolveImage(identifier, scene, images) {
    var map = images || (scene && scene.textureImages) || {};
    if (identifier && map[identifier]) {
      return map[identifier];
    }
    if (identifier && identifier.indexOf("ref:") === 0) {
      return referenceImage(identifier);
    }
    return null;
  }

  function drawTriangle(context, image, screenA, screenB, screenC, uvA, uvB, uvC) {
    var denominator = (uvB[0] - uvA[0]) * (uvC[1] - uvA[1]) - (uvC[0] - uvA[0]) * (uvB[1] - uvA[1]);
    if (Math.abs(denominator) < 1e-6) {
      return;
    }
    var a = ((screenB.x - screenA.x) * (uvC[1] - uvA[1]) - (screenC.x - screenA.x) * (uvB[1] - uvA[1])) / denominator;
    var c = ((screenC.x - screenA.x) * (uvB[0] - uvA[0]) - (screenB.x - screenA.x) * (uvC[0] - uvA[0])) / denominator;
    var e = screenA.x - a * uvA[0] - c * uvA[1];
    var b = ((screenB.y - screenA.y) * (uvC[1] - uvA[1]) - (screenC.y - screenA.y) * (uvB[1] - uvA[1])) / denominator;
    var d = ((screenC.y - screenA.y) * (uvB[0] - uvA[0]) - (screenB.y - screenA.y) * (uvC[0] - uvA[0])) / denominator;
    var f = screenA.y - b * uvA[0] - d * uvA[1];
    context.save();
    context.beginPath();
    context.moveTo(screenA.x, screenA.y);
    context.lineTo(screenB.x, screenB.y);
    context.lineTo(screenC.x, screenC.y);
    context.closePath();
    context.clip();
    context.transform(a, b, c, d, e, f);
    context.drawImage(image, 0, 0);
    context.restore();
  }

  function drawTexturedQuad(context, image, uvPoints, screenPoints) {
    var width = image.width || 16;
    var height = image.height || 16;
    var pixels = uvPoints.map(function (point) {
      return [point[0] / 16 * width, point[1] / 16 * height];
    });
    drawTriangle(context, image, screenPoints[0], screenPoints[1], screenPoints[2], pixels[0], pixels[1], pixels[2]);
    drawTriangle(context, image, screenPoints[0], screenPoints[2], screenPoints[3], pixels[0], pixels[2], pixels[3]);
  }

  function drawFace(context, face, image) {
    var points = face.points;
    if (image) {
      drawTexturedQuad(context, image, face.uvPoints, points);
    } else {
      context.beginPath();
      context.moveTo(points[0].x, points[0].y);
      for (var index = 1; index < points.length; index += 1) {
        context.lineTo(points[index].x, points[index].y);
      }
      context.closePath();
      context.fillStyle = face.color || "#ff00ff";
      context.fill();
    }
    var light = Math.max(0, dot(face.normal, LIGHT_DIR));
    var shade = 0.34 - 0.34 * light;
    if (shade > 0.01) {
      context.beginPath();
      context.moveTo(points[0].x, points[0].y);
      for (var pointIndex = 1; pointIndex < points.length; pointIndex += 1) {
        context.lineTo(points[pointIndex].x, points[pointIndex].y);
      }
      context.closePath();
      context.fillStyle = "rgba(0,0,0," + shade.toFixed(3) + ")";
      context.fill();
    }
  }

  function drawFloor(context, camera) {
    var min = -32;
    var max = 48;
    var corners = [
      projectPoint([min, 0, min], camera),
      projectPoint([max, 0, min], camera),
      projectPoint([max, 0, max], camera),
      projectPoint([min, 0, max], camera)
    ];
    context.beginPath();
    context.moveTo(corners[0].x, corners[0].y);
    for (var index = 1; index < corners.length; index += 1) {
      context.lineTo(corners[index].x, corners[index].y);
    }
    context.closePath();
    context.fillStyle = "#3a4046";
    context.fill();
    context.strokeStyle = "#4a525a";
    context.lineWidth = 1;
    for (var line = min; line <= max; line += 16) {
      var startA = projectPoint([line, 0, min], camera);
      var endA = projectPoint([line, 0, max], camera);
      var startB = projectPoint([min, 0, line], camera);
      var endB = projectPoint([max, 0, line], camera);
      context.beginPath();
      context.moveTo(startA.x, startA.y);
      context.lineTo(endA.x, endA.y);
      context.moveTo(startB.x, startB.y);
      context.lineTo(endB.x, endB.y);
      context.stroke();
    }
  }

  function addReferenceFaces(faces, camera) {
    var boxes = [
      { from: [0, 0, 16], to: [16, 16, 32], textureId: "ref:behind" },
      { from: [16, 0, 0], to: [32, 16, 16], textureId: "ref:left" }
    ];
    boxes.forEach(function (box) {
      FACES.forEach(function (direction) {
        var normal = FACE_NORMALS[direction];
        if (dot(normal, camera.forward) >= 0) {
          return;
        }
        var points = faceCorners(box.from, box.to, direction).map(function (point) {
          return projectPoint(point, camera);
        });
        var depth = points.reduce(function (total, point) {
          return total + point.depth;
        }, 0) / points.length;
        faces.push({
          points: points,
          normal: normal,
          uvPoints: [[0, 0], [16, 0], [16, 16], [0, 16]],
          textureId: box.textureId,
          depth: depth,
          color: "#4b5a66"
        });
      });
    });
  }

  function drawWireframes(context, camera, elements) {
    context.lineWidth = 1;
    context.globalAlpha = 0.85;
    elements.forEach(function (element, index) {
      var corners = boxCorners(element.from, element.to);
      if (element.rotation) {
        corners = corners.map(function (point) {
          return rotatePoint(point, element.rotation.origin, element.rotation.axis, element.rotation.angle);
        });
      }
      var points = corners.map(function (point) {
        return projectPoint(point, camera);
      });
      context.strokeStyle = FRAME_COLORS[index % FRAME_COLORS.length];
      BOX_EDGES.forEach(function (edge) {
        context.beginPath();
        context.moveTo(points[edge[0]].x, points[edge[0]].y);
        context.lineTo(points[edge[1]].x, points[edge[1]].y);
        context.stroke();
      });
    });
    context.globalAlpha = 1;
  }

  function draw(canvas, scene, options) {
    if (!canvas) {
      return;
    }
    var rect = canvas.getBoundingClientRect();
    var width = rect.width;
    var height = rect.height;
    if (width < 2 || height < 2) {
      return;
    }
    var ratio = Math.min(root.devicePixelRatio || 1, 2);
    var pixelWidth = Math.max(1, Math.round(width * ratio));
    var pixelHeight = Math.max(1, Math.round(height * ratio));
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }
    var context = canvas.getContext("2d");
    if (!context) {
      return;
    }
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, width, height);
    context.imageSmoothingEnabled = false;

    var settings = options || {};
    var camera = makeCamera(width, height, settings, scene);
    if (settings.showFloor !== false) {
      drawFloor(context, camera);
    }

    var faces = [];
    var elements = scene && Array.isArray(scene.elements) ? scene.elements : [];
    elements.forEach(function (element, index) {
      elementQuads(element).forEach(function (quad) {
        if (dot(quad.normal, camera.forward) >= 0) {
          return;
        }
        var points = quad.points.map(function (point) {
          return projectPoint(point, camera);
        });
        var depth = points.reduce(function (total, point) {
          return total + point.depth;
        }, 0) / points.length;
        faces.push({
          points: points,
          normal: quad.normal,
          uvPoints: quad.uvPoints,
          textureId: quad.textureId,
          depth: depth,
          color: quad.color
        });
      });
    });

    if (settings.showReference !== false) {
      addReferenceFaces(faces, camera);
    }

    faces.sort(function (left, right) {
      return right.depth - left.depth;
    });

    var textureImages = settings.textureImages || (scene && scene.textureImages) || {};
    faces.forEach(function (face) {
      drawFace(context, face, resolveImage(face.textureId, scene, textureImages));
    });

    drawWireframes(context, camera, elements);
  }

  function parseTextureAnimation(meta, width, height) {
    var animation = isObject(meta) && isObject(meta.animation) ? meta.animation : null;
    if (!animation) {
      return null;
    }
    var frameWidth = Math.max(1, Math.floor(Number(width) || 0));
    var sourceHeight = Math.max(1, Math.floor(Number(height) || frameWidth));
    var frameHeight = frameWidth;
    var frameCount = Math.max(1, Math.floor(sourceHeight / frameHeight));
    var defaultTime = Math.max(1, Math.floor(Number(animation.frametime) || 1));
    var frames = [];
    if (Array.isArray(animation.frames) && animation.frames.length > 0) {
      animation.frames.forEach(function (frame) {
        var index = isObject(frame) ? Math.floor(Number(frame.index)) : Math.floor(Number(frame));
        if (!Number.isFinite(index) || index < 0 || index >= frameCount) {
          return;
        }
        var time = isObject(frame) && frame.time !== undefined
          ? Math.max(1, Math.floor(Number(frame.time) || defaultTime))
          : defaultTime;
        frames.push({ index: index, time: time });
      });
    }
    if (frames.length === 0) {
      for (var index = 0; index < frameCount; index += 1) {
        frames.push({ index: index, time: defaultTime });
      }
    }
    return {
      width: frameWidth,
      height: frameHeight,
      frameCount: frameCount,
      frametime: defaultTime,
      interpolate: Boolean(animation.interpolate),
      frames: frames
    };
  }

  return {
    FACES: FACES.slice(),
    parseModel: parseModel,
    applyTextureOverrides: applyTextureOverrides,
    applyKeyOverrides: applyKeyOverrides,
    textureRows: textureRows,
    shapeBoxes: shapeBoxes,
    resolveTextureId: resolveTextureId,
    normalizeId: normalizeId,
    parseTextureAnimation: parseTextureAnimation,
    draw: draw
  };
});
