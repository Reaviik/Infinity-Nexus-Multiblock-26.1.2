(function (root) {
  "use strict";

  var FALLBACK_COLOR = "#57c7ff";
  var ANCHOR_COLOR = "#8df06c";
  var KEY_COLOR = "#f5c451";
  var PIVOT_COLOR = "#ff7a7a";
  var MAX_CELLS = 16000;

  var CUBE_VERTICES = [
    [-0.5, -0.5, -0.5],
    [0.5, -0.5, -0.5],
    [0.5, -0.5, 0.5],
    [-0.5, -0.5, 0.5],
    [-0.5, 0.5, -0.5],
    [0.5, 0.5, -0.5],
    [0.5, 0.5, 0.5],
    [-0.5, 0.5, 0.5]
  ];

  var CUBE_FACES = [
    [0, 1, 2, 3],
    [7, 6, 5, 4],
    [0, 4, 5, 1],
    [1, 5, 6, 2],
    [2, 6, 7, 3],
    [3, 7, 4, 0]
  ];

  var FACE_NEIGHBORS = [
    { x: 0, y: -1, z: 0 },
    { x: 0, y: 1, z: 0 },
    { x: -1, y: 0, z: 0 },
    { x: 1, y: 0, z: 0 },
    { x: 0, y: 0, z: 1 },
    { x: 0, y: 0, z: -1 }
  ];

  function parseHex(value) {
    var hex = typeof value === "string" ? value.trim() : "";
    if (/^#[0-9a-fA-F]{3}$/.test(hex)) {
      hex = "#" + hex.charAt(1) + hex.charAt(1) +
        hex.charAt(2) + hex.charAt(2) +
        hex.charAt(3) + hex.charAt(3);
    }
    if (!/^#[0-9a-fA-F]{6}$/.test(hex)) {
      hex = FALLBACK_COLOR;
    }
    var number = parseInt(hex.slice(1), 16);
    return {
      r: (number >> 16) & 255,
      g: (number >> 8) & 255,
      b: number & 255
    };
  }

  function shade(value, factor, alpha) {
    var color = parseHex(value);
    function channel(part) {
      return Math.max(0, Math.min(255, Math.round(part * factor)));
    }
    var opacity = alpha === undefined ? 1 : clamp(alpha, 0, 1);
    return "rgba(" + channel(color.r) + ", " + channel(color.g) + ", " +
      channel(color.b) + ", " + opacity + ")";
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function toNumber(value, fallback) {
    var number = typeof value === "number" ? value : Number(value);
    return Number.isFinite(number) ? number : fallback;
  }

  function vec3(value, fallback) {
    if (!value || typeof value !== "object") {
      return { x: fallback, y: fallback, z: fallback };
    }
    return {
      x: toNumber(value.x, fallback),
      y: toNumber(value.y, fallback),
      z: toNumber(value.z, fallback)
    };
  }

  function easing(name, value) {
    var t = clamp(value, 0, 1);
    switch (name) {
      case "step":
        return t < 1 ? 0 : 1;
      case "ease_in":
        return t * t;
      case "ease_out":
        return 1 - (1 - t) * (1 - t);
      case "ease_in_out":
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
      default:
        return t;
    }
  }

  function lerp(from, to, delta) {
    return {
      x: from.x + (to.x - from.x) * delta,
      y: from.y + (to.y - from.y) * delta,
      z: from.z + (to.z - from.z) * delta
    };
  }

  function collectCells(layers, colors, fallback) {
    var source = Array.isArray(layers) ? layers : [];
    var cells = [];
    source.forEach(function (layer, y) {
      if (!Array.isArray(layer)) {
        return;
      }
      layer.forEach(function (row, z) {
        if (typeof row !== "string") {
          return;
        }
        for (var x = 0; x < row.length; x += 1) {
          var symbol = row.charAt(x);
          if (symbol === " ") {
            continue;
          }
          cells.push({
            x: x,
            y: y,
            z: z,
            symbol: symbol,
            color: colors && colors[symbol] ? colors[symbol] : fallback
          });
        }
      });
    });
    return cells;
  }

  function cellKey(x, y, z) {
    return x + "," + y + "," + z;
  }

  function boundsOf(cells) {
    var bounds = {
      minX: Infinity,
      minY: Infinity,
      minZ: Infinity,
      maxX: -Infinity,
      maxY: -Infinity,
      maxZ: -Infinity
    };
    cells.forEach(function (cell) {
      bounds.minX = Math.min(bounds.minX, cell.x);
      bounds.minY = Math.min(bounds.minY, cell.y);
      bounds.minZ = Math.min(bounds.minZ, cell.z);
      bounds.maxX = Math.max(bounds.maxX, cell.x);
      bounds.maxY = Math.max(bounds.maxY, cell.y);
      bounds.maxZ = Math.max(bounds.maxZ, cell.z);
    });
    if (!Number.isFinite(bounds.minX)) {
      return null;
    }
    bounds.sx = bounds.maxX - bounds.minX + 1;
    bounds.sy = bounds.maxY - bounds.minY + 1;
    bounds.sz = bounds.maxZ - bounds.minZ + 1;
    return bounds;
  }

  function sampleMotion(motion, elapsedTicks) {
    var animation = motion && typeof motion.animation === "object" ? motion.animation : {};
    var frames = Array.isArray(animation.keyframes)
      ? animation.keyframes.filter(Boolean).slice().sort(function (left, right) {
        return toNumber(left.time, 0) - toNumber(right.time, 0);
      })
      : [];
    var duration = Math.max(0, Math.trunc(toNumber(animation.duration, 0)));
    var zero = { x: 0, y: 0, z: 0 };
    var pivot = animation.pivot && typeof animation.pivot === "object"
      ? vec3(animation.pivot, 0)
      : null;
    if (duration <= 0 || frames.length === 0) {
      return { offset: zero, rotation: zero, pivot: pivot };
    }
    var time = elapsedTicks;
    if (animation.loop !== false) {
      time = ((time % duration) + duration) % duration;
    } else {
      time = Math.min(Math.max(time, 0), duration);
    }

    var previous = frames[0];
    var next = null;
    for (var index = 1; index < frames.length; index += 1) {
      var frame = frames[index];
      if (time < toNumber(frame.time, 0)) {
        next = frame;
        break;
      }
      previous = frame;
    }
    if (!next) {
      return {
        offset: vec3(previous.offset, 0),
        rotation: vec3(previous.rotation, 0),
        pivot: pivot
      };
    }
    var span = toNumber(next.time, 0) - toNumber(previous.time, 0);
    var t = span <= 0 ? 1 : (time - toNumber(previous.time, 0)) / span;
    var frameEasing = typeof next.easing === "string" && next.easing ? next.easing : animation.easing;
    var eased = easing(frameEasing, t);
    return {
      offset: lerp(vec3(previous.offset, 0), vec3(next.offset, 0), eased),
      rotation: lerp(vec3(previous.rotation, 0), vec3(next.rotation, 0), eased),
      pivot: pivot
    };
  }

  function rotatePoint(point, pivot, rotation) {
    var radiansX = rotation.x * Math.PI / 180;
    var radiansY = rotation.y * Math.PI / 180;
    var radiansZ = rotation.z * Math.PI / 180;
    var x = point.x - pivot.x;
    var y = point.y - pivot.y;
    var z = point.z - pivot.z;

    var cosZ = Math.cos(radiansZ);
    var sinZ = Math.sin(radiansZ);
    var x1 = x * cosZ - y * sinZ;
    var y1 = x * sinZ + y * cosZ;
    var z1 = z;

    var cosY = Math.cos(radiansY);
    var sinY = Math.sin(radiansY);
    var x2 = x1 * cosY + z1 * sinY;
    var y2 = y1;
    var z2 = -x1 * sinY + z1 * cosY;

    var cosX = Math.cos(radiansX);
    var sinX = Math.sin(radiansX);
    var x3 = x2;
    var y3 = y2 * cosX - z2 * sinX;
    var z3 = y2 * sinX + z2 * cosX;

    return {
      x: x3 + pivot.x,
      y: y3 + pivot.y,
      z: z3 + pivot.z
    };
  }

  function cameraPoint(point, center, view) {
    var x = point.x - center.x;
    var y = point.y - center.y;
    var z = point.z - center.z;

    var cosY = Math.cos(view.yaw);
    var sinY = Math.sin(view.yaw);
    var rotatedX = x * cosY - z * sinY;
    var rotatedZ = x * sinY + z * cosY;

    var cosX = Math.cos(view.pitch);
    var sinX = Math.sin(view.pitch);
    var rotatedY = y * cosX - rotatedZ * sinX;
    var depth = y * sinX + rotatedZ * cosX;

    return { x: rotatedX, y: rotatedY, z: depth };
  }

  function screenPoint(point, scale, width, height) {
    return {
      x: width / 2 + point.x * scale,
      y: height / 2 - point.y * scale,
      depth: point.z
    };
  }

  function projectBounds(bounds, center, view) {
    var minX = Infinity;
    var minY = Infinity;
    var maxX = -Infinity;
    var maxY = -Infinity;
    var corners = [
      [bounds.minX, bounds.minY, bounds.minZ],
      [bounds.maxX + 1, bounds.minY, bounds.minZ],
      [bounds.maxX + 1, bounds.maxY + 1, bounds.minZ],
      [bounds.minX, bounds.maxY + 1, bounds.minZ],
      [bounds.minX, bounds.minY, bounds.maxZ + 1],
      [bounds.maxX + 1, bounds.minY, bounds.maxZ + 1],
      [bounds.maxX + 1, bounds.maxY + 1, bounds.maxZ + 1],
      [bounds.minX, bounds.maxY + 1, bounds.maxZ + 1]
    ];
    corners.forEach(function (corner) {
      var point = cameraPoint({ x: corner[0], y: corner[1], z: corner[2] }, center, view);
      minX = Math.min(minX, point.x);
      minY = Math.min(minY, point.y);
      maxX = Math.max(maxX, point.x);
      maxY = Math.max(maxY, point.y);
    });
    return { minX: minX, minY: minY, maxX: maxX, maxY: maxY };
  }

  function faceNormal(points) {
    var ax = points[1].x - points[0].x;
    var ay = points[1].y - points[0].y;
    var az = points[1].z - points[0].z;
    var bx = points[2].x - points[0].x;
    var by = points[2].y - points[0].y;
    var bz = points[2].z - points[0].z;
    var nx = ay * bz - az * by;
    var ny = az * bx - ax * bz;
    var nz = ax * by - ay * bx;
    var length = Math.sqrt(nx * nx + ny * ny + nz * nz) || 1;
    return { x: nx / length, y: ny / length, z: nz / length };
  }

  function screenArea(points) {
    var area = 0;
    for (var index = 0; index < points.length; index += 1) {
      var next = points[(index + 1) % points.length];
      area += points[index].x * next.y - next.x * points[index].y;
    }
    return area / 2;
  }

  function drawFace(context, points, fill, stroke, lineWidth) {
    context.beginPath();
    context.moveTo(points[0].x, points[0].y);
    for (var index = 1; index < points.length; index += 1) {
      context.lineTo(points[index].x, points[index].y);
    }
    context.closePath();
    context.fillStyle = fill;
    context.fill();
    if (stroke) {
      context.strokeStyle = stroke;
      context.lineWidth = lineWidth;
      context.stroke();
    }
  }

  function drawOutline(context, points, color, lineWidth) {
    context.beginPath();
    context.moveTo(points[0].x, points[0].y);
    for (var index = 1; index < points.length; index += 1) {
      context.lineTo(points[index].x, points[index].y);
    }
    context.closePath();
    context.strokeStyle = color;
    context.lineWidth = lineWidth;
    context.stroke();
  }

  function drawPivot(context, pivot, center, view, scale, width, height) {
    if (!pivot) {
      return;
    }
    var point = screenPoint(cameraPoint(pivot, center, view), scale, width, height);
    var size = Math.max(4, Math.min(9, scale * 0.08));
    context.save();
    context.strokeStyle = PIVOT_COLOR;
    context.lineWidth = 1.6;
    context.beginPath();
    context.moveTo(point.x - size, point.y);
    context.lineTo(point.x + size, point.y);
    context.moveTo(point.x, point.y - size);
    context.lineTo(point.x, point.y + size);
    context.stroke();
    context.fillStyle = PIVOT_COLOR;
    context.beginPath();
    context.arc(point.x, point.y, 2, 0, Math.PI * 2);
    context.fill();
    context.restore();
  }

  function draw(canvas, scene) {
    if (!canvas) {
      return;
    }
    var settings = scene || {};
    var view = settings.view || { yaw: Math.PI / 4, pitch: 0.5, zoom: 1 };
    var rect = canvas.getBoundingClientRect();
    var cssWidth = rect.width;
    var cssHeight = rect.height;
    if (cssWidth < 1 || cssHeight < 1) {
      return;
    }

    var ratio = Math.min(root.devicePixelRatio || 1, 2);
    var pixelWidth = Math.max(1, Math.round(cssWidth * ratio));
    var pixelHeight = Math.max(1, Math.round(cssHeight * ratio));
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }

    var context = canvas.getContext("2d");
    if (!context) {
      return;
    }
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, cssWidth, cssHeight);

    var allCells = collectCells(settings.layers, settings.colors, settings.fallback || FALLBACK_COLOR);
    if (allCells.length === 0) {
      context.fillStyle = "rgba(142, 163, 173, 0.75)";
      context.font = "13px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText("Pinte o pattern para ver o multibloco animado.", cssWidth / 2, cssHeight / 2);
      return;
    }
    var hidden = settings.hiddenSymbols && typeof settings.hiddenSymbols === "object"
      ? settings.hiddenSymbols
      : {};
    var cells = allCells.filter(function (cell) {
      return hidden[cell.symbol] !== true;
    });
    if (cells.length === 0) {
      context.fillStyle = "rgba(142, 163, 173, 0.75)";
      context.font = "13px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText(settings.emptyText || "Todas as keys estão ocultas. Use 'ver todas'.",
        cssWidth / 2, cssHeight / 2);
      return;
    }
    if (cells.length > MAX_CELLS) {
      cells = cells.filter(function (cell, index) {
        return index % Math.ceil(cells.length / MAX_CELLS) === 0;
      });
    }

    var bounds = boundsOf(cells);
    var center = {
      x: (bounds.minX + bounds.maxX + 1) / 2,
      y: (bounds.minY + bounds.maxY + 1) / 2,
      z: (bounds.minZ + bounds.maxZ + 1) / 2
    };
    var projected = projectBounds(bounds, center, view);
    var contentWidth = Math.max(1, projected.maxX - projected.minX);
    var contentHeight = Math.max(1, projected.maxY - projected.minY);
    var fitScale = Math.min((cssWidth - 48) / contentWidth, (cssHeight - 48) / contentHeight);
    var scale = Math.max(2, Math.min(90, fitScale)) * clamp(view.zoom || 1, 0.2, 4);

    var occupancy = {};
    cells.forEach(function (cell) {
      occupancy[cellKey(cell.x, cell.y, cell.z)] = cell.symbol;
    });

    var motions = Array.isArray(settings.motions) ? settings.motions : [];
    var symbolMotion = {};
    var motionBounds = new Map();
    var selectedPivots = new Map();
    motions.forEach(function (motion) {
      if (!motion || !Array.isArray(motion.keys)) {
        return;
      }
      var motionCells = cells.filter(function (cell) {
        return motion.keys.indexOf(cell.symbol) !== -1;
      });
      if (motionCells.length === 0) {
        return;
      }
      motionBounds.set(motion, boundsOf(motionCells));
      motion.keys.forEach(function (symbol) {
        if (!symbolMotion[symbol]) {
          symbolMotion[symbol] = motion;
        }
      });
    });

    var elapsed = toNumber(settings.time, 0);
    var opacity = clamp(toNumber(settings.opacity, 0.65), 0.15, 1);
    var faces = [];
    var anchorMarks = [];
    var selectedId = settings.selectedMotionId || "";

    cells.forEach(function (cell) {
      var motion = symbolMotion[cell.symbol];
      var transform = null;
      var partBounds = null;
      var isMoving = false;
      if (motion) {
        partBounds = motionBounds.get(motion) || null;
        if (partBounds) {
          transform = sampleMotion(motion, elapsed);
          var pivot = transform.pivot;
          if (!pivot) {
            pivot = {
              x: partBounds.sx / 2,
              y: partBounds.sy / 2,
              z: partBounds.sz / 2
            };
          }
          transform.pivot = pivot;
          isMoving = transform.offset.x !== 0 || transform.offset.y !== 0 || transform.offset.z !== 0 ||
            transform.rotation.x !== 0 || transform.rotation.y !== 0 || transform.rotation.z !== 0;
          if (selectedId && motion.id === selectedId) {
            selectedPivots.set(motion, {
              x: partBounds.minX + pivot.x,
              y: partBounds.minY + pivot.y,
              z: partBounds.minZ + pivot.z
            });
          }
        }
      }

      var centerPoint = {
        x: cell.x + 0.5,
        y: cell.y + 0.5,
        z: cell.z + 0.5
      };
      var vertices = CUBE_VERTICES.map(function (offset) {
        var point = {
          x: centerPoint.x + offset[0],
          y: centerPoint.y + offset[1],
          z: centerPoint.z + offset[2]
        };
        if (transform && partBounds) {
          var local = {
            x: point.x - partBounds.minX,
            y: point.y - partBounds.minY,
            z: point.z - partBounds.minZ
          };
          var rotated = rotatePoint(local, transform.pivot, transform.rotation);
          point = {
            x: partBounds.minX + rotated.x + transform.offset.x,
            y: partBounds.minY + rotated.y + transform.offset.y,
            z: partBounds.minZ + rotated.z + transform.offset.z
          };
        }
        return screenPoint(cameraPoint(point, center, view), scale, cssWidth, cssHeight);
      });

      var isAnchor = cell.symbol === settings.anchor;
      var isSelected = selectedId && motion && motion.id === selectedId;
      var baseColor = cell.color;
      if (isAnchor) {
        anchorMarks.push(screenPoint(cameraPoint({
          x: centerPoint.x,
          y: cell.y + 1,
          z: centerPoint.z
        }, center, view), scale, cssWidth, cssHeight));
      }
      for (var faceIndex = 0; faceIndex < CUBE_FACES.length; faceIndex += 1) {
        var face = CUBE_FACES[faceIndex];
        if (!isMoving) {
          var neighbor = FACE_NEIGHBORS[faceIndex];
          var neighborSymbol = occupancy[cellKey(cell.x + neighbor.x, cell.y + neighbor.y, cell.z + neighbor.z)];
          if (neighborSymbol && !symbolMotion[neighborSymbol]) {
            continue;
          }
        }
        var points = face.map(function (vertexIndex) {
          return vertices[vertexIndex];
        });
        if (Math.abs(screenArea(points)) < 0.5) {
          continue;
        }
        var normal = faceNormal(points);
        var isTop = normal.y > 0.55;
        var brightness = isTop ? 1.16 : (normal.x - normal.z >= 0 ? 0.84 : 0.62);
        var fill = shade(baseColor, brightness, opacity);
        var depth = (points[0].depth + points[1].depth + points[2].depth + points[3].depth) / 4;
        faces.push({
          points: points,
          fill: fill,
          depth: depth,
          stroke: isTop ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.24)",
          lineWidth: Math.max(0.35, 0.7 / scale),
          outline: isSelected && isTop ? KEY_COLOR : "",
          outlineWidth: Math.max(0.8, 1.2 / scale)
        });
      }
    });

    faces.sort(function (left, right) {
      return right.depth - left.depth;
    });

    context.lineJoin = "round";
    faces.forEach(function (face) {
      drawFace(context, face.points, face.fill, face.stroke, face.lineWidth);
      if (face.outline) {
        drawOutline(context, face.points, face.outline, face.outlineWidth);
      }
    });

    selectedPivots.forEach(function (pivot) {
      drawPivot(context, pivot, center, view, scale, cssWidth, cssHeight);
    });

    anchorMarks.forEach(function (point) {
      var radius = Math.max(7, Math.min(16, scale * 0.2));
      context.save();
      context.shadowColor = ANCHOR_COLOR;
      context.shadowBlur = 10;
      context.strokeStyle = ANCHOR_COLOR;
      context.lineWidth = Math.max(1.1, 1.6 / scale);
      context.beginPath();
      context.moveTo(point.x, point.y - radius);
      context.lineTo(point.x + radius, point.y);
      context.lineTo(point.x, point.y + radius);
      context.lineTo(point.x - radius, point.y);
      context.closePath();
      context.stroke();
      context.shadowBlur = 0;
      context.fillStyle = "rgba(8, 23, 12, 0.92)";
      context.font = "700 " + Math.max(9, Math.min(13, radius * 1.15)) + "px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText(settings.anchor || "C", point.x, point.y + 0.5);
      context.restore();
    });
  }

  root.MotionPreview3D = {
    draw: draw
  };
})(typeof window !== "undefined" ? window : this);
