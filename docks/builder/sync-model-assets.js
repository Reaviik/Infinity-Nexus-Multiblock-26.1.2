const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..", "..");
const assets = path.join(root, "src", "main", "resources", "assets", "infinity_nexus_multiblock");
const targetAssets = path.join(root, "docks", "src", "main", "resources", "assets", "infinity_nexus_multiblock");

const defaultModels = [
  "multiblock_controller.json",
  "multiblock_controller_on.json",
  "multiblock_controller_building.json"
];

function copyDirectory(from, to) {
  fs.mkdirSync(to, { recursive: true });
  fs.readdirSync(from, { withFileTypes: true }).forEach(function (entry) {
    const sourcePath = path.join(from, entry.name);
    const targetPath = path.join(to, entry.name);
    if (entry.isDirectory()) {
      copyDirectory(sourcePath, targetPath);
    } else if (entry.isFile()) {
      fs.copyFileSync(sourcePath, targetPath);
    }
  });
}

if (!fs.existsSync(assets)) {
  console.error("Builder asset sync: assets not found:", assets);
  process.exit(1);
}

fs.rmSync(targetAssets, { recursive: true, force: true });

defaultModels.forEach(function (name) {
  const from = path.join(assets, "models", "block", name);
  if (!fs.existsSync(from)) {
    console.error("Builder asset sync: default model not found:", from);
    process.exit(1);
  }
  const to = path.join(targetAssets, "models", "block", name);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
});

const fallbackFrom = path.join(assets, "textures", "block", "fallback");
if (fs.existsSync(fallbackFrom)) {
  copyDirectory(fallbackFrom, path.join(targetAssets, "textures", "block", "fallback"));
}

console.log(
  "Builder asset sync: default controller models and fallback textures copied to",
  path.relative(root, targetAssets)
);
