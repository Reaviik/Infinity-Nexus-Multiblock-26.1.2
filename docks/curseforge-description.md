# Infinity Nexus Multiblock

**Data-driven multiblock machines for NeoForge.**

Build complex structures and turn patterns into functional machines with inventory, energy, fluids, GUI, and recipes — all defined in JSON. This mod is made for modpack and datapack creators who want to prototype and publish multiblocks without writing a Java class for every machine.

## Highlights

- **Data-driven patterns:** define layers, anchor, rotation, and matchers by block, tag, or blockstate.
- **Dynamic controllers:** blocks, items, and block entities are generated automatically from JSONs bundled with the mod.
- **Configurable slots:** input, output, fuel, and upgrade roles with filters, limits, and automation faces.
- **Slot locking:** lock an input to a specific item or to an empty slot, ideal for automation and exact recipes.
- **Energy and fluids:** configurable capabilities with per-face automation ports.
- **Data-driven GUI:** placeable slots, energy and fluid bars, progress, text, and player inventory.
- **Custom recipes:** duration, energy consumption, progress, and persistence.
- **JEI integration:** per-controller category, structure preview, and ingredient transfer to the correct slots.
- **In-game tools:** structure preview, automatic assembly from inventory, and structure export to JSON.

## For datapack creators

Patterns, slots, capabilities, GUI, recipes, inheritance, and templates are loaded from JSON and can be updated with `/reload`. Dynamic controllers bundled with the mod are registered at startup; to add a new controller block, the JSON must be in the mod namespace and the game must be restarted.

## Visual builder

Build the pattern, slots, capabilities, GUI, and recipes in a visual interface and export datapack-ready JSON.

**[INSERT BUILDER WEBSITE LINK HERE]**

## Requirements

- Minecraft **26.1.2**
- NeoForge **26.1.2.121+**
- Java **25**
- **Infinity Nexus Core 8.0.4+** — required
- **JEI** — optional, for recipe categories and transfer

> This is a framework mod: it provides the multiblock system and working examples, not a complete machine progression.
