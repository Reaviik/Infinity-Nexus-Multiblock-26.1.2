const assert = require("node:assert/strict");
const P = require("./modelpreview3d.js");

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

test("modelo simples preserva elementos e resolve texturas", () => {
  const parsed = P.parseModel({
    textures: {
      all: "example:block/panel",
      particle: "#all"
    },
    elements: [
      {
        from: [1, 2, 3],
        to: [15, 14, 13],
        faces: {
          north: { uv: [0, 0, 16, 16], texture: "#all" },
          south: { uv: [0, 0, 8, 8], texture: "#all", rotation: 90 }
        }
      }
    ]
  });
  assert.equal(parsed.elements.length, 1);
  assert.deepEqual(parsed.elements[0].from, [1, 2, 3]);
  assert.equal(parsed.elements[0].faces.north.textureId, "example:block/panel");
  assert.equal(parsed.elements[0].faces.south.rotation, 90);
  assert.equal(parsed.usedFallbackCube, false);
});

test("parent vanilla e resolvido e as texturas do filho vencem", () => {
  const parsed = P.parseModel({
    parent: "minecraft:block/orientable_with_bottom",
    textures: {
      top: "example:block/top",
      bottom: "example:block/bottom",
      side: "example:block/side",
      front: "example:block/front"
    }
  });
  assert.equal(parsed.missingParent, null);
  assert.equal(parsed.elements.length, 1);
  assert.equal(parsed.elements[0].faces.north.textureId, "example:block/front");
  assert.equal(parsed.elements[0].faces.down.textureId, "example:block/bottom");
  assert.equal(parsed.elements[0].faces.up.textureId, "example:block/top");
  assert.equal(parsed.elements[0].faces.south.textureId, "example:block/side");
  assert.equal(parsed.entries.length, 4);
  assert.deepEqual(parsed.textureRows.map((row) => row.key), [
    "front", "top", "down", "left", "right", "back", "particle"
  ]);
  assert.equal(parsed.textureRows.find((row) => row.key === "left").identifier, "example:block/side");
  assert.equal(parsed.textureRows.find((row) => row.key === "right").identifier, "example:block/side");
  assert.equal(parsed.textureRows.find((row) => row.key === "back").identifier, "example:block/side");
  assert.equal(parsed.textureRows.find((row) => row.key === "down").identifier, "example:block/bottom");
});

test("overrides trocam faces e mantem as texturas padrao no restante", () => {
  const parsed = P.parseModel({
    parent: "minecraft:block/orientable_with_bottom",
    textures: {
      top: "example:block/top",
      bottom: "example:block/bottom",
      side: "example:block/side",
      front: "example:block/front"
    }
  });
  const applied = P.applyTextureOverrides(parsed, {
    front: "example:block/custom_front",
    top: ""
  });
  assert.notEqual(applied, parsed);
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/custom_front");
  assert.equal(applied.elements[0].faces.south.textureId, "example:block/side");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/top");
  assert.equal(applied.textures.particle, "#front");
  assert.equal(applied.textureRows.find((row) => row.key === "particle").identifier,
    "example:block/custom_front");
  assert.ok(applied.entries.some((entry) => entry.identifier === "example:block/custom_front"));
});

test("overrides por direcao atualizam as seis faces e o particle", () => {
  const parsed = P.parseModel({
    parent: "minecraft:block/cube",
    textures: {
      up: "example:block/top",
      down: "example:block/bottom",
      north: "example:block/front",
      south: "example:block/back",
      west: "example:block/left",
      east: "example:block/right",
      particle: "#north"
    }
  });
  assert.deepEqual(parsed.textureRows.map((row) => row.key), [
    "front", "top", "down", "left", "right", "back", "particle"
  ]);
  const applied = P.applyTextureOverrides(parsed, {
    north: "example:block/custom_front",
    west: "example:block/custom_left",
    east: "example:block/custom_right",
    south: "example:block/custom_back",
    down: "example:block/custom_bottom",
    particle: "example:block/custom_particle"
  });
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/custom_front");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/top");
  assert.equal(applied.elements[0].faces.down.textureId, "example:block/custom_bottom");
  assert.equal(applied.elements[0].faces.west.textureId, "example:block/custom_left");
  assert.equal(applied.elements[0].faces.east.textureId, "example:block/custom_right");
  assert.equal(applied.elements[0].faces.south.textureId, "example:block/custom_back");
  assert.equal(applied.textureRows.find((row) => row.key === "particle").identifier,
    "example:block/custom_particle");
});

test("overrides semanticos atualizam modelos custom com chaves front/back/left/right", () => {
  const parsed = P.parseModel({
    textures: {
      front: "example:block/front",
      back: "example:block/back",
      left: "example:block/left",
      right: "example:block/right",
      top: "example:block/top",
      bottom: "example:block/bottom",
      particle: "#front"
    },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          north: { texture: "#front" },
          south: { texture: "#back" },
          west: { texture: "#left" },
          east: { texture: "#right" },
          up: { texture: "#top" },
          down: { texture: "#bottom" }
        }
      }
    ]
  });
  const applied = P.applyTextureOverrides(parsed, {
    front: "example:block/custom_front",
    back: "example:block/custom_back",
    left: "example:block/custom_left",
    right: "example:block/custom_right",
    top: "example:block/custom_top",
    bottom: "example:block/custom_bottom",
    particle: "example:block/custom_particle"
  });
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/custom_front");
  assert.equal(applied.elements[0].faces.south.textureId, "example:block/custom_back");
  assert.equal(applied.elements[0].faces.west.textureId, "example:block/custom_left");
  assert.equal(applied.elements[0].faces.east.textureId, "example:block/custom_right");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/custom_top");
  assert.equal(applied.elements[0].faces.down.textureId, "example:block/custom_bottom");
  assert.equal(applied.textureRows.find((row) => row.key === "particle").identifier,
    "example:block/custom_particle");
});

test("override all atualiza faces e particle que referenciam all", () => {
  const parsed = P.parseModel({
    textures: {
      all: "example:block/panel",
      particle: "#all"
    },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          north: { texture: "#all" },
          up: { texture: "#all" }
        }
      }
    ]
  });
  const applied = P.applyTextureOverrides(parsed, {
    all: "example:block/custom_all"
  });
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/custom_all");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/custom_all");
  assert.equal(applied.textureRows.find((row) => row.key === "particle").identifier,
    "example:block/custom_all");
});

test("override side atualiza faces que referenciam side", () => {
  const parsed = P.parseModel({
    parent: "minecraft:block/orientable_with_bottom",
    textures: {
      top: "example:block/top",
      bottom: "example:block/bottom",
      side: "example:block/side",
      front: "example:block/front"
    }
  });
  const applied = P.applyTextureOverrides(parsed, {
    side: "example:block/custom_side"
  });
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/front");
  assert.equal(applied.elements[0].faces.south.textureId, "example:block/custom_side");
  assert.equal(applied.elements[0].faces.west.textureId, "example:block/custom_side");
  assert.equal(applied.elements[0].faces.east.textureId, "example:block/custom_side");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/top");
  assert.equal(applied.elements[0].faces.down.textureId, "example:block/bottom");
});

test("key override aplica a textura somente nas faces que usam a chave", () => {
  const parsed = P.parseModel({
    textures: {
      side: "#missing",
      top: "example:block/top"
    },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          north: { texture: "#side" },
          south: { texture: "#side" },
          up: { texture: "#top" }
        }
      }
    ]
  });
  assert.equal(parsed.elements[0].faces.north.textureId, null);
  assert.equal(parsed.elements[0].faces.south.textureId, null);
  const applied = P.applyKeyOverrides(parsed, {
    side: "minecraft:block/custom_side"
  });
  assert.equal(applied.elements[0].faces.north.textureId, "minecraft:block/custom_side");
  assert.equal(applied.elements[0].faces.south.textureId, "minecraft:block/custom_side");
  assert.equal(applied.elements[0].faces.up.textureId, "example:block/top");
  assert.equal(applied.textureRows.find((row) => row.raw === "#side").identifier,
    "minecraft:block/custom_side");
});

test("key override nao troca identifier existente", () => {
  const parsed = P.parseModel({
    textures: { all: "example:block/panel" },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          north: { texture: "#all" },
          south: { texture: "#all" }
        }
      }
    ]
  });
  const applied = P.applyKeyOverrides(parsed, { all: "minecraft:block/other" });
  assert.equal(applied, parsed);
  assert.equal(applied.elements[0].faces.north.textureId, "example:block/panel");
  assert.equal(applied.elements[0].faces.south.textureId, "example:block/panel");
});

test("overrides vazios preservam o modelo parseado", () => {
  const parsed = P.parseModel({
    textures: { all: "example:block/panel" }
  });
  assert.equal(P.applyTextureOverrides(parsed, {}), parsed);
});

test("parent ausente cai no cubo completo com as texturas disponiveis", () => {
  const parsed = P.parseModel({
    parent: "example:block/missing",
    textures: { all: "example:block/all" }
  });
  assert.equal(parsed.missingParent, "example:block/missing");
  assert.equal(parsed.usedFallbackCube, true);
  assert.equal(parsed.elements.length, 1);
  assert.equal(parsed.elements[0].faces.north.textureId, "example:block/all");
  assert.equal(parsed.elements[0].faces.down.textureId, "example:block/all");
});

test("entradas agrupam chaves que apontam para a mesma textura", () => {
  const parsed = P.parseModel({
    textures: {
      side: "example:block/side",
      back: "#side",
      particle: "#side"
    }
  });
  assert.equal(parsed.entries.length, 1);
  assert.equal(parsed.entries[0].identifier, "example:block/side");
  assert.deepEqual(parsed.entries[0].keys.sort(), ["back", "side"]);
  assert.equal(parsed.entries[0].vanilla, false);
});

test("texturas vanilla sao marcadas e ids sem namespace sao normalizados", () => {
  const parsed = P.parseModel({
    textures: {
      all: "block/stone",
      extra: "minecraft:block/dirt"
    }
  });
  assert.equal(parsed.entries.length, 2);
  assert.equal(parsed.entries[0].identifier, "minecraft:block/dirt");
  assert.equal(parsed.entries[0].vanilla, true);
  assert.equal(parsed.entries[1].identifier, "minecraft:block/stone");
  assert.equal(P.normalizeId("block/stone"), "minecraft:block/stone");
  assert.equal(P.normalizeId("example:block/stone"), "example:block/stone");
});

test("namespace do modelo e usado em texturas sem identifier", () => {
  const parsed = P.parseModel(
    { textures: { all: "block/panel" } },
    { namespace: "example" }
  );
  assert.equal(parsed.entries.length, 1);
  assert.equal(parsed.entries[0].identifier, "example:block/panel");
  assert.equal(parsed.entries[0].vanilla, false);
});

test("faces sem textura recebem cor solida estavel e agrupada", () => {
  const source = {
    textures: { a: "example:block/panel", b: "#a" },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          north: { texture: "#a" },
          south: { texture: "#b" },
          east: {},
          west: {}
        }
      }
    ]
  };
  const parsed = P.parseModel(source);
  const again = P.parseModel(source);
  const north = parsed.elements[0].faces.north.color;
  const south = parsed.elements[0].faces.south.color;
  const east = parsed.elements[0].faces.east.color;
  assert.match(north, /^hsl\(/);
  assert.equal(north, south);
  assert.notEqual(north, east);
  assert.equal(again.elements[0].faces.north.color, north);
});

test("bounds acompanham elementos fora do cubo 0..16", () => {
  const parsed = P.parseModel({
    elements: [
      { from: [0, 0, 0], to: [32, 8, 8], faces: {} }
    ]
  });
  assert.deepEqual(parsed.bounds.min, [0, 0, 0]);
  assert.deepEqual(parsed.bounds.max, [32, 8, 8]);
});

test("referencia #missing vira textura nula e JSON invalido lanca erro", () => {
  const parsed = P.parseModel({
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: { north: { texture: "#missing" } }
      }
    ]
  });
  assert.equal(parsed.elements[0].faces.north.textureId, null);
  assert.throws(() => P.parseModel("{"), /JSON/);
  assert.throws(() => P.parseModel("[]"), /objeto/);
});

test("shapeBoxes preserva coordenadas fracionarias e avisa recorte", () => {
  const parsed = P.parseModel({
    elements: [
      { from: [1.25, 0.25, 2.75], to: [14.75, 8.25, 13.25], faces: {} },
      { from: [-0.5, 0, 0], to: [16.5, 4, 4], faces: {} }
    ]
  });
  const result = P.shapeBoxes(parsed);
  assert.deepEqual(result.boxes[0], [1.25, 0.25, 2.75, 14.75, 8.25, 13.25]);
  assert.deepEqual(result.boxes[1], [0, 0, 0, 16, 4, 4]);
  assert.equal(result.clipped, 1);
});

test("shapeBoxes converte elementos e ignora rotacoes fora de 90", () => {
  const parsed = P.parseModel({
    elements: [
      { from: [0, 0, 0], to: [4, 4, 4], faces: {} },
      { from: [0, 0, 0], to: [4, 4, 4], faces: {} },
      { from: [4, 4, 4], to: [12, 12, 12], rotation: { origin: [8, 8, 8], axis: "y", angle: 45 }, faces: {} },
      { from: [0, 0, 0], to: [16, 16, 0], faces: {} },
      { from: [0, 0, 0], to: [4, 4, 4], rotation: { origin: [8, 8, 8], axis: "y", angle: 90 }, faces: {} }
    ]
  });
  const result = P.shapeBoxes(parsed);
  assert.deepEqual(result.boxes, [
    [0, 0, 0, 4, 4, 4],
    [0, 0, 12, 4, 4, 16]
  ]);
  assert.equal(result.skippedRotation, 1);
  assert.equal(result.skippedFlat, 1);
});

function fakeContext() {
  return new Proxy({}, {
    get(target, property) {
      if (property in target) {
        return target[property];
      }
      return function () {};
    },
    set(target, property, value) {
      target[property] = value;
      return true;
    }
  });
}

function fakeCanvas() {
  return {
    width: 0,
    height: 0,
    getContext() {
      return fakeContext();
    },
    getBoundingClientRect() {
      return { width: 420, height: 300 };
    }
  };
}

test("renderer desenha modelo, piso e referencias sem lancar erro", () => {
  const previousDocument = global.document;
  global.document = {
    createElement() {
      return fakeCanvas();
    }
  };
  try {
    const parsed = P.parseModel({
      textures: { all: "example:block/panel" },
      elements: [
        {
          from: [0, 0, 0],
          to: [16, 16, 16],
          faces: {
            north: { texture: "#all" },
            south: { texture: "#all" },
            east: { texture: "#all" },
            west: { texture: "#all" },
            up: { texture: "#all" },
            down: { texture: "#all" }
          }
        }
      ]
    });
    assert.doesNotThrow(() => P.draw(fakeCanvas(), parsed, { showFloor: true, showReference: true }));
    assert.doesNotThrow(() => P.draw(fakeCanvas(), parsed, { showFloor: false, showReference: false }));
    assert.doesNotThrow(() => P.draw(fakeCanvas(), null, { showFloor: true, showReference: true }));
  } finally {
    if (previousDocument === undefined) {
      delete global.document;
    } else {
      global.document = previousDocument;
    }
  }
});

test("mcmeta sem animation e ignorado", () => {
  assert.equal(P.parseTextureAnimation({}, 16, 32), null);
  assert.equal(P.parseTextureAnimation(null, 16, 32), null);
  assert.equal(P.parseTextureAnimation({ animation: null }, 16, 32), null);
});

test("mcmeta sequencial calcula frames pela altura do png", () => {
  const animation = P.parseTextureAnimation({ animation: { frametime: 3 } }, 16, 48);
  assert.equal(animation.width, 16);
  assert.equal(animation.height, 16);
  assert.equal(animation.frameCount, 3);
  assert.equal(animation.frametime, 3);
  assert.deepEqual(animation.frames, [
    { index: 0, time: 3 },
    { index: 1, time: 3 },
    { index: 2, time: 3 }
  ]);
});

test("mcmeta aceita frames com index, time e interpolate", () => {
  const animation = P.parseTextureAnimation({
    animation: {
      frametime: 2,
      interpolate: true,
      frames: [0, { index: 2, time: 5 }, 1]
    }
  }, 16, 64);
  assert.equal(animation.interpolate, true);
  assert.equal(animation.frameCount, 4);
  assert.deepEqual(animation.frames, [
    { index: 0, time: 2 },
    { index: 2, time: 5 },
    { index: 1, time: 2 }
  ]);
});

test("mcmeta descarta frames fora da altura e mantem os validos", () => {
  const animation = P.parseTextureAnimation({
    animation: { frames: [0, 9, -1] }
  }, 16, 32);
  assert.deepEqual(animation.frames, [
    { index: 0, time: 1 }
  ]);
});

console.log(passed + " testes passaram.");
