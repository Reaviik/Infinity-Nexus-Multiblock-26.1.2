## <span style="color:#2DC26B;">` V: 1.1.`</span>
### <span style="color:#3598DB;"> **Add**:</span>
### <span style="color:#F1C40F;"> **Fix**:</span>
### <span style="color:#E67E23;"> **Change**:</span>
### <span style="color:#BA372A;"> **Removed**:</span>

---

## <span style="color:#2DC26B;">` V: 1.1.2`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Incremental multiblock assembly with configurable blocks per tick and failed scans per tick.
- Item collection from the player inventory and 6 adjacent containers, with consumption at the end of each tick.
- Jade assembly status with progress (`Building`, `Formed`, `Unformed`).
- Red highlights for blocked assembly positions, with configurable duration.
- Place sound while blocks are assembled.
- `Building` controller state with per-definition dynamic textures.
- `textures.building` support in the multiblock builder.
- Default `front_on_building` animated fallback model and texture.
### <span style="color:#F1C40F;"> **Fix**:</span>
- Blocked highlights now disappear when the blocking block is broken.
- `front_on_building.png.mcmeta` renamed so the texture animation plays.
### <span style="color:#E67E23;"> **Change**:</span>
- Assembly no longer happens instantly; it runs over multiple ticks and reports `assembly_in_progress` while active.
- Structure validation is suppressed during assembly, and a new click restarts a partial assembly from zero.
- Creative assembly places blocks incrementally without consuming items.
- If the player goes offline, only adjacent containers are used.
- The controller emits light while building.
- Building textures fall back to the controller idle/active textures and then to the mod default textures.
- Multiblock textures now accept an optional `building` set (schema 2 unchanged).
- Updated the builder.
### <span style="color:#BA372A;"> **Removed**:</span>

---

## <span style="color:#2DC26B;">` V: 1.1.1`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Add command /inmb reload to reload all multiblock definition and generate new multiblocks with data loaded by kubejs load server-scripts

---

## <span style="color:#2DC26B;">` V: 1.1.0`</span>
### <span style="color:#F1C40F;"> **Fix**:</span>
- Block tags now support blockstates in multiblock structure.

---

## <span style="color:#2DC26B;">` V: 1.0.9`</span>
### <span style="color:#F1C40F;"> **Fix**:</span>
- Default bars position in gui
- Controller title position in gui
### <span style="color:#E67E23;"> **Change**:</span>
- Valid range accessor to 32 blocks range to open structure gui
- Max size for multiblocks structures from 4096 to 40960

---

## <span style="color:#2DC26B;">` V: 1.0.8`</span>
### <span style="color:#F1C40F;"> **Fix**:</span>
- Jei recipes show incorrect items with components
### <span style="color:#BA372A;"> **Removed**:</span>
- Test presets

---

## <span style="color:#2DC26B;">` V: 1.0.7`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Support for controller custom textures

---

## <span style="color:#2DC26B;">` V: 1.0.6`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Multiblock preview fixed in world on attack with controller in hand
- Sound support to multiblock process and finisher recipes
### <span style="color:#F1C40F;"> **Fix**:</span>
- Multiblock preview fps reduced
### <span style="color:#E67E23;"> **Change**:</span>
- Update builder

---

## <span style="color:#2DC26B;">` V: 1.0.5`</span>
### <span style="color:#F1C40F;"> **Fix**:</span>
- Default entity output spawn position on flat multiblocks

---

## <span style="color:#2DC26B;">` V: 1.0.4`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Entities tag support to recipe in/out

---

## <span style="color:#2DC26B;">` V: 1.0.3`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Filtered inputs for fluid ports using pattern Key
### <span style="color:#F1C40F;"> **Fix**:</span>
- Fluid tags in recipes

---

## <span style="color:#2DC26B;">` V: 1.0.2`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Recipe support to entity input and output
- Entity preview in JEI recipes and multiblock controller
### <span style="color:#F1C40F;"> **Fix**:</span>
- Controller and JEI energy/fluid/progress bar
- Slots position in JEI and controller gui
### <span style="color:#E67E23;"> **Change**:</span>
- Short command alias changed from `/inm` to `/inmb` to avoid a command-root collision with Infinity Nexus Mod.
- Update Builder

---

## <span style="color:#2DC26B;">` V: 1.0.1`</span>
### <span style="color:#3598DB;"> **Add**:</span>
- Shared multiblock controller with the `infinity_nexus_multiblock:multiblock_definition` component.
- `/inm hand`, `/inm look`, `/inm list`, and `/inm give` commands.
- Controller recipe generation from the mod jar and `kubejs/data`.
- JEI multiblock recipe GUI header with the exact controller slot and definition name.
### <span style="color:#F1C40F;"> **Fix**:</span>
- JEI recipes are filtered by the multiblock definition.
- JEI structure recipes are linked to the correct controller variant.
- JEI multiblock recipes list their controller variants as crafting stations.
### <span style="color:#E67E23;"> **Change**:</span>
- Multiblock JSON schema updated to version 2 with single-controller definitions.
- Machine recipes now use `infinity_nexus_multiblock:multiblock` plus `definition`.
- All multiblock creation and management chage to Schema version 2
### <span style="color:#BA372A;"> **Removed**:</span>
- Dynamic controller registry, resource pack, and per-machine controller configuration.
- `controller` and `recipe_type` fields from multiblock definitions.

---

## <span style="color:#2DC26B;">` V: 1.0.0`</span>
### <span style="color:#E67E23;"> **Change**:</span>
- Created on 26.1.2

---

### <span style="color:#843FA1;"> **Version started on 10.0.0**</span>
