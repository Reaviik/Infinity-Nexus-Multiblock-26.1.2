(function (root) {
  "use strict";

  var THREE = root.THREE;
  if (!THREE) {
    root.MotionPreview3DWebGL = null;
    return;
  }

  var FALLBACK_COLOR = "#57c7ff";
  var ANCHOR_COLOR = "#8df06c";
  var KEY_COLOR = "#f5c451";
  var PIVOT_COLOR = "#ff7a7a";
  var MAX_CELLS = 60000;

  var BOX = new THREE.BoxGeometry(1, 1, 1);
  var BOX_EDGES = new THREE.EdgesGeometry(BOX);
  var FACE = new THREE.PlaneGeometry(1, 1);
  var FACE_DIRECTIONS = [
    { x: 1, y: 0, z: 0 },
    { x: -1, y: 0, z: 0 },
    { x: 0, y: 1, z: 0 },
    { x: 0, y: -1, z: 0 },
    { x: 0, y: 0, z: 1 },
    { x: 0, y: 0, z: -1 }
  ];
  var FACE_QUATERNIONS = FACE_DIRECTIONS.map(function (normal) {
    var quaternion = new THREE.Quaternion();
    quaternion.setFromUnitVectors(
      new THREE.Vector3(0, 0, 1),
      new THREE.Vector3(normal.x, normal.y, normal.z)
    );
    return quaternion;
  });
  var MATERIAL = new THREE.MeshLambertMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0.65,
    side: THREE.DoubleSide
  });
  var dummy = new THREE.Object3D();

  var state = {
    failed: false,
    renderer: null,
    canvas: null,
    scene: null,
    camera: null,
    light: null,
    staticMesh: null,
    movingMesh: null,
    movingFaces: [],
    motions: [],
    motionBounds: new Map(),
    selectedId: "",
    bounds: null,
    center: new THREE.Vector3(),
    radius: 8,
    revision: -1,
    width: 0,
    height: 0,
    anchorMarker: null,
    pivotMarker: null,
    selectionBox: null,
    notice: null,
    noticeTexture: null,
    noticeText: "",
    noticeCenter: new THREE.Vector3(),
    noticeScale: 1
  };

  function supportsWebGL() {
    try {
      var canvas = document.createElement("canvas");
      return Boolean(root.WebGLRenderingContext &&
        (canvas.getContext("webgl2") || canvas.getContext("webgl") || canvas.getContext("experimental-webgl")));
    } catch (error) {
      return false;
    }
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

  function cellKey(x, y, z) {
    return x + "," + y + "," + z;
  }

  function sameMotionGroup(left, right) {
    if (!left.motion && !right.motion) {
      return true;
    }
    return Boolean(left.motion && left.motion === right.motion);
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

  function rotationMatrix(rotation) {
    var x = rotation.x * Math.PI / 180;
    var y = rotation.y * Math.PI / 180;
    var z = rotation.z * Math.PI / 180;
    var mx = new THREE.Matrix4().makeRotationX(x);
    var my = new THREE.Matrix4().makeRotationY(y);
    var mz = new THREE.Matrix4().makeRotationZ(z);
    var matrix = new THREE.Matrix4();
    matrix.multiplyMatrices(mx, my).multiply(mz);
    return matrix;
  }

  function disposeMesh(mesh) {
    if (!mesh) {
      return;
    }
    if (mesh.parent) {
      mesh.parent.remove(mesh);
    }
    mesh.dispose();
  }

  function resetRendererState() {
    state.staticMesh = null;
    state.movingMesh = null;
    state.movingFaces = [];
    state.motions = [];
    state.motionBounds = new Map();
    state.selectedId = "";
    state.bounds = null;
    state.revision = -1;
    state.width = 0;
    state.height = 0;
    state.lastTransforms = null;
    state.notice = null;
    state.noticeTexture = null;
    state.noticeText = "";
  }

  function ensureRenderer(canvas) {
    if (state.failed) {
      return false;
    }
    if (state.renderer && state.canvas === canvas) {
      return true;
    }
    if (state.renderer) {
      try {
        state.renderer.dispose();
      } catch (error) {
        state.failed = true;
        return false;
      }
    }
    resetRendererState();
    try {
      state.renderer = new THREE.WebGLRenderer({
        canvas: canvas,
        antialias: true,
        alpha: true,
        powerPreference: "high-performance"
      });
    } catch (error) {
      state.failed = true;
      state.renderer = null;
      return false;
    }
    state.canvas = canvas;
    state.renderer.setPixelRatio(Math.min(root.devicePixelRatio || 1, 2));
    state.renderer.setClearColor(0x000000, 0);
    if (THREE.sRGBEncoding !== undefined) {
      state.renderer.outputEncoding = THREE.sRGBEncoding;
    }
    state.scene = new THREE.Scene();
    state.camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 4000);
    state.camera.up.set(0, 1, 0);

    var ambient = new THREE.AmbientLight(0xffffff, 0.72);
    state.scene.add(ambient);
    state.light = new THREE.DirectionalLight(0xffffff, 0.95);
    state.scene.add(state.light);
    state.scene.add(state.light.target);

    state.anchorMarker = new THREE.Mesh(
      new THREE.SphereGeometry(1, 12, 8),
      new THREE.MeshBasicMaterial({ color: ANCHOR_COLOR, depthTest: false, transparent: true })
    );
    state.anchorMarker.renderOrder = 20;
    state.scene.add(state.anchorMarker);

    var pivotGeometry = new THREE.BufferGeometry();
    pivotGeometry.setAttribute("position", new THREE.Float32BufferAttribute([
      -1, 0, 0, 1, 0, 0,
      0, -1, 0, 0, 1, 0,
      0, 0, -1, 0, 0, 1
    ], 3));
    state.pivotMarker = new THREE.LineSegments(
      pivotGeometry,
      new THREE.LineBasicMaterial({ color: PIVOT_COLOR, depthTest: false, transparent: true })
    );
    state.pivotMarker.renderOrder = 21;
    state.scene.add(state.pivotMarker);

    state.selectionBox = new THREE.LineSegments(
      BOX_EDGES,
      new THREE.LineBasicMaterial({ color: KEY_COLOR, depthTest: false, transparent: true, opacity: 0.95 })
    );
    state.selectionBox.renderOrder = 22;
    state.scene.add(state.selectionBox);

    canvas.addEventListener("webglcontextlost", function (event) {
      event.preventDefault();
      state.failed = true;
    }, false);
    return true;
  }

  function markerSize() {
    return Math.max(0.12, state.radius * 0.025);
  }

  function ensureNotice() {
    if (state.notice) {
      return;
    }
    var canvas = document.createElement("canvas");
    canvas.width = 512;
    canvas.height = 128;
    state.noticeTexture = new THREE.CanvasTexture(canvas);
    if (THREE.sRGBEncoding !== undefined) {
      state.noticeTexture.encoding = THREE.sRGBEncoding;
    }
    state.notice = new THREE.Sprite(new THREE.SpriteMaterial({
      map: state.noticeTexture,
      transparent: true,
      depthTest: false,
      depthWrite: false
    }));
    state.notice.renderOrder = 30;
    state.scene.add(state.notice);
  }

  function showNotice(text, center, radius) {
    ensureNotice();
    if (state.noticeText !== text) {
      var canvas = state.noticeTexture.image;
      var context = canvas.getContext("2d");
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = "rgba(142, 163, 173, 0.92)";
      context.font = "600 22px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      var lines = String(text).split("\n");
      lines.forEach(function (line, index) {
        context.fillText(line, canvas.width / 2, canvas.height / 2 + (index - (lines.length - 1) / 2) * 28);
      });
      state.noticeTexture.needsUpdate = true;
      state.noticeText = text;
    }
    state.noticeCenter.copy(center);
    state.noticeScale = Math.max(2, radius * 1.8);
    state.notice.position.copy(center);
    state.notice.scale.set(state.noticeScale, state.noticeScale * 0.25, 1);
    state.notice.visible = true;
  }

  function hideNotice() {
    if (state.notice) {
      state.notice.visible = false;
    }
  }

  function rebuild(settings, cells, allCellCount) {
    if (state.staticMesh) {
      disposeMesh(state.staticMesh);
      state.staticMesh = null;
    }
    if (state.movingMesh) {
      disposeMesh(state.movingMesh);
      state.movingMesh = null;
    }
    state.movingFaces = [];
    state.motions = Array.isArray(settings.motions) ? settings.motions : [];
    state.selectedId = settings.selectedMotionId || "";
    state.motionBounds = new Map();

    if (cells.length === 0) {
      state.bounds = null;
      state.center.set(0, 0, 0);
      state.radius = 8;
      if (state.anchorMarker) state.anchorMarker.visible = false;
      if (state.pivotMarker) state.pivotMarker.visible = false;
      if (state.selectionBox) state.selectionBox.visible = false;
      showNotice(allCellCount === 0
        ? "Pinte o pattern para ver o multibloco animado."
        : (settings.emptyText || "Todas as keys estão ocultas. Use 'ver todas'."),
      state.center, state.radius);
      return;
    }

    hideNotice();
    var bounds = boundsOf(cells);
    state.bounds = bounds;
    state.center.set(
      (bounds.minX + bounds.maxX + 1) / 2,
      (bounds.minY + bounds.maxY + 1) / 2,
      (bounds.minZ + bounds.maxZ + 1) / 2
    );
    state.radius = Math.max(1, Math.sqrt(bounds.sx * bounds.sx + bounds.sy * bounds.sy + bounds.sz * bounds.sz) / 2);

    var symbolMotion = {};
    state.motions.forEach(function (motion) {
      if (!motion || !Array.isArray(motion.keys)) {
        return;
      }
      var motionCells = cells.filter(function (cell) {
        return motion.keys.indexOf(cell.symbol) !== -1;
      });
      if (motionCells.length === 0) {
        return;
      }
      state.motionBounds.set(motion, boundsOf(motionCells));
      motion.keys.forEach(function (symbol) {
        if (!symbolMotion[symbol]) {
          symbolMotion[symbol] = motion;
        }
      });
    });

    var entries = cells.map(function (cell) {
      var motion = symbolMotion[cell.symbol] || null;
      return {
        cell: cell,
        motion: motion,
        bounds: motion ? state.motionBounds.get(motion) : null
      };
    });
    var entryByKey = Object.create(null);
    entries.forEach(function (entry) {
      entryByKey[cellKey(entry.cell.x, entry.cell.y, entry.cell.z)] = entry;
    });

    var staticFaces = [];
    var movingFaces = [];
    entries.forEach(function (entry) {
      var cell = entry.cell;
      FACE_DIRECTIONS.forEach(function (normal, normalIndex) {
        var neighbor = entryByKey[cellKey(cell.x + normal.x, cell.y + normal.y, cell.z + normal.z)];
        if (neighbor && sameMotionGroup(entry, neighbor)) {
          return;
        }
        var face = {
          cell: cell,
          normalIndex: normalIndex,
          motion: entry.motion,
          bounds: entry.bounds
        };
        if (entry.motion) {
          face.local = {
            x: cell.x + 0.5 - entry.bounds.minX + normal.x * 0.5,
            y: cell.y + 0.5 - entry.bounds.minY + normal.y * 0.5,
            z: cell.z + 0.5 - entry.bounds.minZ + normal.z * 0.5
          };
          movingFaces.push(face);
        } else {
          staticFaces.push(face);
        }
      });
    });

    if (staticFaces.length > 0) {
      var staticMesh = new THREE.InstancedMesh(FACE, MATERIAL, staticFaces.length);
      staticMesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
      staticMesh.frustumCulled = false;
      staticFaces.forEach(function (face, index) {
        var normal = FACE_DIRECTIONS[face.normalIndex];
        dummy.position.set(
          face.cell.x + 0.5 + normal.x * 0.5,
          face.cell.y + 0.5 + normal.y * 0.5,
          face.cell.z + 0.5 + normal.z * 0.5
        );
        dummy.quaternion.copy(FACE_QUATERNIONS[face.normalIndex]);
        dummy.scale.set(1, 1, 1);
        dummy.updateMatrix();
        staticMesh.setMatrixAt(index, dummy.matrix);
        staticMesh.setColorAt(index, new THREE.Color(face.cell.color));
      });
      staticMesh.instanceMatrix.needsUpdate = true;
      if (staticMesh.instanceColor) {
        staticMesh.instanceColor.needsUpdate = true;
      }
      state.scene.add(staticMesh);
      state.staticMesh = staticMesh;
    }

    if (movingFaces.length > 0) {
      var movingMesh = new THREE.InstancedMesh(FACE, MATERIAL, movingFaces.length);
      movingMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
      movingMesh.frustumCulled = false;
      movingFaces.forEach(function (face, index) {
        movingMesh.setColorAt(index, new THREE.Color(face.cell.color));
      });
      if (movingMesh.instanceColor) {
        movingMesh.instanceColor.needsUpdate = true;
      }
      state.scene.add(movingMesh);
      state.movingMesh = movingMesh;
      state.movingFaces = movingFaces;
    }

    var anchorCell = null;
    if (settings.anchor) {
      anchorCell = cells.find(function (cell) {
        return cell.symbol === settings.anchor;
      }) || null;
    }
    if (anchorCell) {
      state.anchorMarker.visible = true;
      state.anchorMarker.position.set(anchorCell.x + 0.5, anchorCell.y + 1, anchorCell.z + 0.5);
      state.anchorMarker.scale.setScalar(markerSize());
    } else {
      state.anchorMarker.visible = false;
    }

    state.revision = settings.revision === undefined ? 0 : settings.revision;
  }

  function updateMoving(time) {
    var transforms = new Map();
    state.motions.forEach(function (motion) {
      var bounds = state.motionBounds.get(motion);
      if (!bounds) {
        return;
      }
      var sample = sampleMotion(motion, time);
      var pivot = sample.pivot || {
        x: bounds.sx / 2,
        y: bounds.sy / 2,
        z: bounds.sz / 2
      };
      var matrix = rotationMatrix(sample.rotation);
      transforms.set(motion, {
        offset: sample.offset,
        rotation: sample.rotation,
        pivot: pivot,
        matrix: matrix,
        quaternion: new THREE.Quaternion().setFromRotationMatrix(matrix)
      });
    });
    state.lastTransforms = transforms;

    if (!state.movingMesh || state.movingFaces.length === 0) {
      return;
    }
    state.movingFaces.forEach(function (face, index) {
      var transform = transforms.get(face.motion);
      if (!transform) {
        return;
      }
      var rotated = rotatePoint(face.local, transform.pivot, transform.rotation);
      dummy.position.set(
        face.bounds.minX + rotated.x + transform.offset.x,
        face.bounds.minY + rotated.y + transform.offset.y,
        face.bounds.minZ + rotated.z + transform.offset.z
      );
      dummy.quaternion.copy(transform.quaternion).multiply(FACE_QUATERNIONS[face.normalIndex]);
      dummy.scale.set(1, 1, 1);
      dummy.updateMatrix();
      state.movingMesh.setMatrixAt(index, dummy.matrix);
    });
    state.movingMesh.instanceMatrix.needsUpdate = true;
  }

  function updateSelection() {
    if (!state.selectionBox) {
      return;
    }
    var motion = null;
    if (state.selectedId) {
      motion = state.motions.find(function (entry) {
        return entry && entry.id === state.selectedId;
      }) || null;
    }
    var partBounds = motion ? state.motionBounds.get(motion) : null;
    if (!motion || !partBounds || !state.lastTransforms || !state.lastTransforms.has(motion)) {
      state.selectionBox.visible = false;
      if (state.pivotMarker) state.pivotMarker.visible = false;
      return;
    }
    var transform = state.lastTransforms.get(motion);
    var localCenter = {
      x: partBounds.sx / 2,
      y: partBounds.sy / 2,
      z: partBounds.sz / 2
    };
    var rotated = rotatePoint(localCenter, transform.pivot, transform.rotation);
    state.selectionBox.visible = true;
    state.selectionBox.position.set(
      partBounds.minX + rotated.x + transform.offset.x,
      partBounds.minY + rotated.y + transform.offset.y,
      partBounds.minZ + rotated.z + transform.offset.z
    );
    state.selectionBox.quaternion.setFromRotationMatrix(transform.matrix);
    state.selectionBox.scale.set(partBounds.sx, partBounds.sy, partBounds.sz);

    if (state.pivotMarker) {
      state.pivotMarker.visible = true;
      state.pivotMarker.position.set(
        partBounds.minX + transform.pivot.x,
        partBounds.minY + transform.pivot.y,
        partBounds.minZ + transform.pivot.z
      );
      state.pivotMarker.scale.setScalar(markerSize());
    }
  }

  function updateCamera(view) {
    var camera = state.camera;
    var aspect = state.width / state.height;
    var half = state.radius * 1.15;
    var halfWidth = half * Math.max(1, aspect);
    var halfHeight = half * Math.max(1, 1 / aspect);
    var yaw = toNumber(view && view.yaw, Math.PI / 4);
    var pitch = clamp(toNumber(view && view.pitch, 0.5), -1.45, 1.45);
    var zoom = clamp(toNumber(view && view.zoom, 1), 0.2, 4);
    var distance = state.radius * 4 + 20;

    camera.left = -halfWidth;
    camera.right = halfWidth;
    camera.top = halfHeight;
    camera.bottom = -halfHeight;
    camera.near = 0.1;
    camera.far = distance + state.radius * 8 + 100;
    camera.zoom = zoom;
    camera.updateProjectionMatrix();

    var cosPitch = Math.cos(pitch);
    camera.position.set(
      state.center.x - Math.sin(yaw) * cosPitch * distance,
      state.center.y + Math.sin(pitch) * distance,
      state.center.z + Math.cos(yaw) * cosPitch * distance
    );
    camera.lookAt(state.center);

    if (state.light) {
      state.light.position.copy(camera.position);
      state.light.target.position.copy(state.center);
      state.light.target.updateMatrixWorld();
    }
  }

  function draw(canvas, scene) {
    if (!canvas || state.failed) {
      return false;
    }
    if (!ensureRenderer(canvas)) {
      return false;
    }
    var settings = scene || {};
    var rect = canvas.getBoundingClientRect();
    var width = Math.max(1, Math.round(rect.width));
    var height = Math.max(1, Math.round(rect.height));
    if (width < 1 || height < 1) {
      return true;
    }
    if (state.width !== width || state.height !== height) {
      state.width = width;
      state.height = height;
      state.renderer.setSize(width, height, false);
    }

    var revision = settings.revision === undefined ? 0 : settings.revision;
    if (revision !== state.revision) {
      var allCells = collectCells(settings.layers, settings.colors, settings.fallback || FALLBACK_COLOR);
      var hidden = settings.hiddenSymbols && typeof settings.hiddenSymbols === "object"
        ? settings.hiddenSymbols
        : {};
      var cells = allCells.filter(function (cell) {
        return hidden[cell.symbol] !== true;
      });
      if (cells.length > MAX_CELLS) {
        cells = cells.filter(function (cell, index) {
          return index % Math.ceil(cells.length / MAX_CELLS) === 0;
        });
      }
      rebuild(settings, cells, allCells.length);
    }

    var opacity = clamp(toNumber(settings.opacity, 0.65), 0.15, 1);
    MATERIAL.opacity = opacity;
    MATERIAL.depthWrite = opacity >= 0.999;

    var time = toNumber(settings.time, 0);
    updateMoving(time);
    updateSelection();
    updateCamera(settings.view);
    state.renderer.render(state.scene, state.camera);
    return true;
  }

  root.MotionPreview3DWebGL = {
    supported: supportsWebGL,
    draw: draw
  };
})(window);
