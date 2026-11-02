# Data-Driven Multiblock

## Estado atual

- O carregamento data-driven do `pattern` já está implementado.
- Local: `data/<namespace>/multiblock/<arquivo>.json`.
- Matchers aceitam ID de bloco (`minecraft:iron_block`), tag (`#namespace:tag`) ou objeto com `block`/`tag` e `state` para blockstates específicos.
- No criativo, `shift+click` em um controller exporta a estrutura conectada (limite configurável em `export.pattern_scan_limit`, padrão 256 blocos) como JSON no clipboard, contendo apenas `schema_version`, `id`, `type` e `pattern` com blockstates normalizados para a orientação canônica (controller virado para `south`).
- `schema_version`, `id`, `type`, `recipe_type` e `tier` já são lidos e validados.
- `type=recipe` processa receitas próprias de qualquer `recipe_type` registrado pelo mod, com duração, energia e progresso persistido. O mod registra um `RecipeType` + serializer por `recipe_type` único dos controllers empacotados; atualmente `infinity_nexus_multiblock:nexus_smelting`. Tipos não registrados são ignorados.
- GUI data-driven e menu customizado já estão implementados, incluindo `progress_bar` e `arrow`; o `controller` e as seis texturas por face são carregados do JSON e usados no modelo gerado dos controllers dinâmicos; controllers manuais usam assets próprios.
- Receitas vanilla estão adiadas; veja “Receitas vanilla (adiado)”.
- O inventário usa `RestrictedItemStackHandler` e a capability de item é exposta no controller e em qualquer bloco membro formado.
- `item_slots` data-driven já aplica roles, filtros, limites e faces no handler; a GUI posiciona os slots conforme `x`/`y` do próprio slot.
- O controller usa `MultiblockMenu`/`MultiblockScreen`; sem `gui.elements`, um layout padrão é gerado.
- Capabilities genéricas de energia e fluidos já são criadas a partir de `capabilities`, expostas em todas as faces e persistidas no NBT do controller.
- `capabilities` não é reconfigurado em controllers existentes por `/reload`; as capabilities são criadas junto com o block entity.

## Objetivo

Permitir definir um multibloco/máquina por JSON, incluindo:

- estrutura física;
- texturas do bloco controller;
- GUI;
- energia, fluidos e slots;
- elementos visuais;
- comportamento de formação e validação.

O JSON descreve a máquina, mas não implementa sozinho o processamento. Receitas dos tipos registrados pelo mod, ticks e progresso já têm implementação genérica no `BlockEntity`; energia, fluidos, GUI e menu customizado também. Adaptadores para tipos vanilla e o comportamento específico de `type=storage` continuam pendentes.

## Arquivo e identificação

Caminho recomendado:

```text
src/main/resources/data/<namespace>/multiblock/<path>.json
```

O ID da definição deve ser explícito no JSON:

```json
"id": "infinity_nexus_multiblock:example_controller"
```

Regras:

- `id` é obrigatório na versão 1 do schema.
- O `id` deve corresponder a `<namespace>:<path>` do arquivo.
- IDs duplicados devem ser rejeitados.
- `schema_version` é obrigatório e, no código atual, somente `1` é aceito.
- `type` é obrigatório: `recipe` ou `storage`.
- `recipe_type` é obrigatório para `type=recipe` e deve ser omitido para `type=storage`.
- `tier` é opcional, mínimo `1`, padrão `1`.
- O bloco controller pode ser criado dinamicamente a partir de JSONs empacotados no namespace do mod ou registrado em Java; JSONs de datapack externo apenas referenciam blocos já registrados.

## Estrutura geral

```json
{
  "schema_version": 1,
  "id": "infinity_nexus_multiblock:example_controller",
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:nexus_smelting",
  "tier": 1,
  "inventory_rows": 3,
  "controller": {
    "block": "infinity_nexus_multiblock:example_controller",
    "textures": {
      "front": "infinity_nexus_multiblock:block/assembler/front",
      "back": "infinity_nexus_multiblock:block/assembler/back",
      "left": "infinity_nexus_multiblock:block/assembler/left",
      "right": "infinity_nexus_multiblock:block/assembler/right",
      "top": "infinity_nexus_multiblock:block/assembler/top",
      "bottom": "infinity_nexus_multiblock:block/assembler/bottom"
    }
  },
  "capabilities": {
    "energy": {
      "id": "energy",
      "capacity": 100000,
      "max_receive": 1000,
      "max_extract": 1000
    },
    "fluids": [
      {
        "id": "input_tank",
        "capacity": 10000,
        "max_receive": 1000,
        "max_extract": 0,
        "filter": "#c:water"
      }
    ]
  },
  "pattern": {
    "anchor": "C",
    "rotatable": true,
    "empty": "ignore",
    "layers": [
      [
        "III",
        "ICI",
        "III"
      ],
      [
        "GGG",
        "G G",
        "GGG"
      ],
      [
        "GGG",
        "G G",
        "GGG"
      ]
    ],
    "matchers": {
      "I": "minecraft:iron_block",
      "G": "minecraft:glass"
    }
  }
}
```

O exemplo acima omite `item_slots` e `gui`; ambos são opcionais. Sem `gui.elements`, o controller usa o layout padrão descrito abaixo.

## Controller

`controller` é opcional no schema atual. Em JSONs empacotados no namespace do mod, o scanner cria o bloco em `controller.block` ou no próprio `id` e usa as texturas declaradas/fallback para gerar o modelo. Em datapacks externos, o bloco precisa já existir; o JSON referencia esse bloco, valida que ele pertence à definição e carrega as seis texturas semânticas. Para controllers manuais de addons, modelo e blockstate precisam existir em `assets`; para dinâmicos, são gerados em memória.

### Campo `controller`

```json
"controller": {
  "block": "infinity_nexus_multiblock:example_controller",
  "textures": {
    "front": "infinity_nexus_multiblock:block/assembler/front",
    "back": "infinity_nexus_multiblock:block/assembler/back",
    "left": "infinity_nexus_multiblock:block/assembler/left",
    "right": "infinity_nexus_multiblock:block/assembler/right",
    "top": "infinity_nexus_multiblock:block/assembler/top",
    "bottom": "infinity_nexus_multiblock:block/assembler/bottom"
  }
}
```

- `block` referencia o bloco controller; em JSON empacotado no namespace do mod ele é criado dinamicamente, e em datapack/manual precisa estar registrado em Java. O bloco precisa ser um `MultiblockControllerBlock` cuja `definitionId` seja igual ao `id` da definição.
- `textures` define as seis faces do modelo e é obrigatório quando `controller` está presente.
- As faces são semânticas: `front` acompanha o `FACING` do bloco.
- `back`, `left`, `right`, `top` e `bottom` são relativos ao `front`.
- Para blocos que não rotacionam, pode ser usada uma variante absoluta com `north`, `south`, `east`, `west`, `up` e `down`; não misturar as duas convenções.
- Caminhos de blocos: `namespace:block/caminho` corresponde a `assets/<namespace>/textures/block/caminho.png`.
- Opcional: `particle` para a partícula do bloco.
- Opcional futuro: `formed_textures` com as mesmas seis chaves para mudar a aparência quando formado.

Para controllers dinâmicos empacotados, as texturas do JSON alimentam o blockstate e o modelo gerados em memória pelo loader. O mod não possui controller estático embutido; controllers manuais de addons usam o blockstate/modelo próprios em `assets`.

## GUI

A GUI é montada em `MultiblockMenuLayout`, criado no servidor a partir da definição e enviado ao cliente no buffer de abertura do menu. O cliente não carrega definições nem precisa receber o layout em cada update do block entity.

### Campo `gui`

`gui` é opcional. Sem `gui` ou sem `gui.elements`, o layout padrão é gerado automaticamente.

```json
"gui": {
  "texture": "infinity_nexus_multiblock:textures/gui/assembler.png",
  "width": 176,
  "height": 166,
  "texture_width": 256,
  "texture_height": 256,
  "elements": []
}
```

- `texture`: opcional; caminho completo da textura (por exemplo, `namespace:textures/gui/arquivo.png`). Sem textura, a tela desenha um painel cinza simples.
- `width`/`height`: tamanho da área desenhada, de `1` a `4096`; padrão `176x166`.
- `texture_width`/`texture_height`: tamanho da textura para calcular `u`/`v`; padrão `256x256`.
- `elements`: lista de elementos visuais. Se estiver ausente ou vazia, o layout padrão é usado. Se tiver ao menos um elemento, o layout é customizado e precisa cumprir as regras abaixo.
- Coordenadas são em pixels, com origem no canto superior esquerdo.
- Slots têm `18x18`; o inventário do jogador ocupa `162x76` (3 linhas + hotbar).
- Barras são desenhadas antes dos slots; textos e títulos são desenhados depois.

### Layout padrão

Usado quando não há `gui.elements`:

- gera a quantidade de `item_slots` (ou `inventory_rows * 9` quando `item_slots` está ausente);
- posiciona os slots em uma grade de 9 colunas;
- coloca o inventário do jogador abaixo dos slots;
- adiciona o título no topo;
- adiciona uma barra para `energy` e uma para cada tank de fluido, à esquerda dos slots;
- adiciona uma barra de progresso à direita dos slots quando `type=recipe` e `recipe_type` está definido;
- aumenta `width`/`height` se as barras ou o inventário não couberem nas dimensões de `gui`.

### Layout customizado

Com `gui.elements` não vazio:

- `item_slots` só é obrigatório quando houver elementos `slot` ou posições `x`/`y`; sem slots de item, a GUI pode conter apenas barras e o inventário do jogador;
- slots visíveis são os `item_slots[]` com `x`/`y`; slots sem coordenadas ficam apenas para automação;
- não misture `x`/`y` em `item_slots` com elementos `slot`; o formato antigo continua aceito quando nenhum slot tem coordenadas;
- no formato antigo, todo elemento `slot` precisa referenciar um ID existente e não pode repetir outro slot;
- é obrigatório exatamente um elemento `player_inventory`;
- `energy_bar` exige capability de energia e `capability` igual a `energy.id`;
- `fluid_bar` exige `capability` igual a um `fluids[].id`;
- `progress_bar` e `arrow` só são aceitos em `type=recipe`;
- slots com `x`/`y`, inventário do jogador e barras precisam caber em `width`/`height`;
- slots sem coordenadas não são reposicionados automaticamente: ficam ocultos e disponíveis apenas para automação.

`/reload` atualiza a definição e o layout de novos controllers; um controller já criado mantém o layout e as capabilities atuais.

## Capabilities

As capabilities guardam dados e comportamento. Posições de slots ficam no próprio `item_slots` (`x`/`y`); barras e textos ficam em `elements`.

No código atual, `energy` e `fluids` já são carregados, criados no block entity, expostos via `Capabilities.Energy.BLOCK`/`Capabilities.Fluid.BLOCK` e persistidos no NBT. `capabilities` fica no nível raiz; `item_slots` também fica no nível raiz. A GUI usa `energy.id` e `fluids[].id` para localizar as barras; os IDs ainda não configuram acesso por lado.

### Energia

Implementado com `SimpleEnergyHandler` (capacidade e limites separados de inserção/extração). Se `max_receive` ou `max_extract` forem omitidos, o padrão é `0`; um limite `0` desativa aquela operação.

```json
"energy": {
  "id": "energy",
  "capacity": 100000,
  "max_receive": 1000,
  "max_extract": 1000
}
```

- `capacity`: armazenamento máximo em FE.
- `max_receive`: FE aceitos por operação de inserção.
- `max_extract`: FE liberados por operação de extração.
- `transfer` sozinho é ambíguo; usar `max_receive` e `max_extract`.
- `id` permite que elementos de GUI e APIs referenciem essa capability.

### Fluidos

Implementado com `FluidStacksResourceHandler`: cada tank tem capacidade e limites próprios, o filtro usa ID ou tag e o handler é exposto em todas as faces. Assim como energia, limites omitidos assumem `0`.

```json
"fluids": [
  {
    "id": "input_tank",
    "capacity": 10000,
    "max_receive": 1000,
    "max_extract": 0,
    "filter": "#c:water"
  },
  {
    "id": "output_tank",
    "capacity": 10000,
    "max_receive": 0,
    "max_extract": 1000
  }
]
```

- Cada tank precisa de um `id` único e diferente do `energy.id`.
- `capacity` em mB.
- `filter` aceita ID de fluido ou tag.
- `max_receive`/`max_extract` definem entrada e saída.
- Elementos `fluid_bar` referenciam o tank pelo `id`.
- Futuro: filtros múltiplos, lista de fluidos permitidos e configuração por lado.

### Slots de itens

O schema `item_slots` já é aplicado pelo `MultiblockItemHandler`. Quando tiver entradas, o inventário tem exatamente a quantidade de entradas do array; `inventory_rows` só é usado quando `item_slots` está ausente. Cada slot pode definir `x`/`y` para ser desenhado na GUI customizada; sem coordenadas, fica oculto e disponível apenas para automação.

```json
"item_slots": [
  {
    "id": "input_0",
    "role": "input",
    "filter": "#c:ingots",
    "limit": 64,
    "x": 44,
    "y": 17
  },
  {
    "id": "output_0",
    "role": "output",
    "x": 116,
    "y": 35
  },
  {
    "id": "upgrade_0",
    "role": "upgrade",
    "faces": {
      "insert": ["north", "south"],
      "extract": []
    }
  }
]
```

- `id`: identificador único do slot.
- `role`: `input`, `output`, `fuel`, `upgrade`, `component`, `storage` ou `fluid`.
- `filter`: item (`minecraft:iron_ingot`) ou tag (`#c:ingots`) aceita.
- `limit`: quantidade máxima por slot, de `1` a `64`; padrão `64`.
- `x`/`y`: opcionais; posição do slot na GUI customizada. Informe os dois juntos.
- `faces.insert`: direções que podem inserir no slot.
- `faces.extract`: direções que podem extrair do slot.
- Lista de faces vazia desativa a operação para o slot.
- Sem `faces`, o padrão é definido pelo role:
  - `input`, `fuel`, `upgrade`, `component`, `fluid`: inserem por todas as faces, não extraem.
  - `output`: não insere, extrai por todas as faces.
  - `storage`: insere e extrai por todas as faces.
- O filtro e o limite valem para o menu do jogador; as faces valem para automação.
- O menu customizado posiciona os slots conforme `x`/`y` do próprio `item_slots[]`; slots sem coordenadas ficam apenas para automação. Sem `gui.elements`, cada slot com `x/y` válido mantém a posição no layout padrão e os demais são auto-posicionados em grade de 18 px.
- O formato antigo com elementos `slot` em `gui.elements` continua aceito quando nenhum slot define `x`/`y`; não misture os dois formatos.
- Retirada manual por face continua bloqueada apenas para automação; o jogador pode retirar de qualquer slot visível.
- `/reload` atualiza a definição e reconfigura layout, inventário e índices de slots dos controllers já criados; capabilities ausentes são criadas quando a definição fica disponível.
- Posições `x`/`y` ficam no `item_slot`, não na capability.
- O inventário do jogador é um elemento especial (`player_inventory`), não uma capability.

## Elements

Elementos implementados ficam em `gui.elements`. Campos comuns:

```json
{
  "type": "text",
  "x": 0,
  "y": 0,
  "width": 16,
  "height": 16,
  "text": "gui.example",
  "capability": "",
  "color": 4282400832,
  "shadow": true,
  "direction": "up",
  "u": -1,
  "v": -1
}
```

- `type`: obrigatório; um dos tipos abaixo.
- `x`/`y`: posição relativa à GUI; padrão `0`.
- `width`/`height`: de `0` a `4096`; padrão `16`.
- `text`: chave de tradução; padrão vazio.
- `slot`: referência a `item_slots[].id`; usado apenas no formato antigo de elemento `slot`.
- `capability`: referência a `energy.id` ou `fluids[].id`; padrão vazio.
- `color`: inteiro ARGB; padrão `0xFF404040`. Se o alpha for `0`, ele é trocado por `0xFF`. JSON não aceita `#RRGGBB`; use o inteiro.
- `shadow`: sombra do texto; padrão `true`.
- `direction`: `up`, `down`, `left` ou `right`; padrão `up`. Tem efeito em `energy_bar`, `progress_bar` e `arrow`; `fluid_bar` ignora.
- `u`/`v`: origem do sprite na textura da GUI; padrão `-1`, que usa `x`/`y` do elemento.
- Campos antigos como `id`, `texture`, `title`, `align`, `tooltip`, `progress`, `background` e `fill` não são implementados e são ignorados.

### `title` e `text`

```json
{
  "type": "title",
  "text": "gui.infinity_nexus_multiblock.assembler",
  "x": 8,
  "y": 6,
  "color": 4282400832,
  "shadow": false
}
```

- `text` é uma chave de tradução.
- Em `title`, `text` vazio usa o nome do bloco/menu.
- Em `text`, `text` vazio não desenha nada.
- Os textos são desenhados depois das barras e dos slots.

### `player_inventory`

```json
{
  "type": "player_inventory",
  "x": 8,
  "y": 84,
  "text": "container.inventory",
  "color": 4282400832,
  "shadow": false
}
```

- Desenha os 27 slots principais e a hotbar do jogador.
- A área fixa é `162x76`; `width`/`height` não alteram esse tamanho.
- `text` é o título do inventário, desenhado 11 pixels acima de `y`; vazio usa `container.inventory`.
- Em layout customizado, exatamente um `player_inventory` é obrigatório.

### `slot` (formato antigo)

```json
{
  "type": "slot",
  "slot": "input_0",
  "x": 44,
  "y": 17
}
```

- Prefira definir `x`/`y` no próprio `item_slots[]`; este elemento existe por compatibilidade.
- Só é aceito quando nenhum `item_slots[]` define `x`/`y`; não misture os dois formatos.
- `slot` referencia um `item_slots[].id`.
- O tamanho visual é sempre `18x18`.
- Em layout customizado, o ID é obrigatório, não pode repetir e precisa existir em `item_slots`.
- `item_slots` sem posição não aparecem na GUI e ficam apenas para automação.
- O limite do item vem de `item_slots[].limit`; filtros continuam validados no servidor.

### `energy_bar`

```json
{
  "type": "energy_bar",
  "capability": "energy",
  "x": 8,
  "y": 17,
  "width": 12,
  "height": 52,
  "direction": "up",
  "color": 4294901760
}
```

- `capability` precisa ser igual a `energy.id`.
- `width`/`height` precisam ser positivos.
- `direction` define o sentido do preenchimento: `up` (baixo para cima), `down` (cima para baixo), `left` (direita para esquerda) ou `right` (esquerda para direita).
- `color` é o ARGB do preenchimento.
- O tooltip padrão mostra `Energy: atual / capacidade FE`; a chave é `gui.infinity_nexus_multiblock.energy`.

### `fluid_bar`

```json
{
  "type": "fluid_bar",
  "capability": "input_tank",
  "x": 28,
  "y": 17,
  "width": 12,
  "height": 52
}
```

- `capability` precisa ser igual a um `fluids[].id`.
- `width`/`height` precisam ser positivos.
- O fluido é renderizado com `FluidTankRenderer`, sempre preenchendo de baixo para cima; `direction` é ignorado.
- O tooltip usa o nome, a quantidade e a capacidade do tank.

### `progress_bar`

```json
{
  "type": "progress_bar",
  "x": 174,
  "y": 17,
  "width": 12,
  "height": 54,
  "direction": "left",
  "color": 4278251008
}
```

- Só é aceito quando a definição é `type=recipe`.
- `width`/`height` precisam ser positivos.
- `direction` define o sentido do preenchimento: `up`, `down`, `left` ou `right`.
- `u`/`v` definem o sprite de fundo; sem eles, usa `x`/`y`. O fundo é desenhado antes do preenchimento.
- `color` é o ARGB do preenchimento; o padrão é `0xFF00AA00`.
- O tooltip mostra a porcentagem do craft atual; a chave é `gui.infinity_nexus_multiblock.progress`.
- O progresso é sincronizado pelo `ContainerData` do menu, não pelo update tag do block entity.

### `arrow`

```json
{
  "type": "arrow",
  "x": 79,
  "y": 35,
  "width": 22,
  "height": 16,
  "u": 176,
  "v": 0,
  "direction": "right"
}
```

- Só é aceito quando a definição é `type=recipe`.
- Desenha somente a seta recortada pelo progresso, sem fundo, como a seta da fornalha.
- `u`/`v` apontam para o sprite na textura da GUI; sem eles, usa `x`/`y`.
- `direction` define o sentido do recorte (`right` cresce da esquerda para a direita).
- `progress_bar` e `arrow` são independentes e podem ser usados juntos; ambos leem o mesmo progresso.
- O tooltip mostra a porcentagem do craft atual.

### Outros elementos planejados

- `image`: textura decorativa.
- `button`: botões de controle.
- `tooltip`: área de tooltip.
- textos dinâmicos.
- `bar`: barras genéricas.
- `energy_text`/`fluid_text`: valores formatados.

## Pattern

```json
"pattern": {
  "anchor": "C",
  "rotatable": true,
  "empty": "ignore",
  "layers": [
    ["III", "ICI", "III"],
    ["GGG", "G G", "GGG"],
    ["GGG", "G G", "GGG"]
  ],
  "matchers": {
    "I": "minecraft:iron_block",
    "G": "minecraft:glass"
  }
}
```

- `anchor`: símbolo do bloco controller; exatamente um por pattern.
- `layers`: ordem de baixo para cima.
- Dentro de cada layer, as linhas avançam em Z e os caracteres em X.
- `space` vazio é ignorado por padrão.
- `empty`: `ignore` ou `air`; define se espaços exigem ar.
- `rotatable`: permite as quatro rotações.
- `inventory_rows` não pertence ao `pattern`; fica no nível superior do JSON. Define apenas a contagem do inventário genérico quando não há `item_slots` e não altera posições `x`/`y`. Em uma GUI customizada, o tamanho real vem dos `item_slots`.
- `matchers` deve conter todos os símbolos usados.

Formatos de matcher:

```json
"I": "minecraft:iron_block"
```

```json
"G": "#c:glass_blocks"
```

```json
"B": {
  "block": "minecraft:oak_stairs",
  "state": {
    "facing": "north",
    "half": "bottom"
  }
}
```

O objeto aceita `block` ou `tag` (exatamente um). `state` só é válido com `block`; apenas as propriedades informadas são verificadas e as omitidas são ignoradas. Objeto sem `state` aceita qualquer state do bloco. A tag pode ser escrita com ou sem `#`.

Os states são relativos à orientação canônica do pattern, com o controller virado para `south`. Ao validar, montar ou pré-visualizar com o controller em outra direção, o state esperado é rotacionado junto com as posições.

Planejado:

- NBT;
- matchers opcionais;
- alternativas por lista.

## Tipos de máquina, tiers e receitas compartilhadas

### Decisão de arquitetura

Cada tier terá seu próprio `id` de definição, mas todos os tiers da mesma máquina compartilharão o mesmo `recipe_type`. O `tier` não será usado para localizar receitas; ele apenas altera estatísticas e capacidades. Para evitar duplicação de GUI, capabilities e elementos, a definição usa `extends`/`family` apontando para um template da library core.

### IDs separados por responsabilidade

Não usar o `id` do tier para localizar receitas. Separar:

- `id`: identifica a definição/bloco/tier específico.
- `type`: identifica o comportamento da máquina, como `recipe` ou `storage`.
- `recipe_type`: identifica a família/categoria de receitas compartilhada.
- `tier`: identifica a versão da máquina; é usado para estatísticas, não para localizar receitas.
- `family` ou `extends`: opcional, para compartilhar GUI, capabilities e elementos entre tiers.

Exemplo com três tiers:

```json
{
  "id": "mymod:alloy_smelter_basic",
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:nexus_smelting",
  "tier": 1
}
```

```json
{
  "id": "mymod:alloy_smelter_advanced",
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:nexus_smelting",
  "tier": 2
}
```

```json
{
  "id": "mymod:alloy_smelter_elite",
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:nexus_smelting",
  "tier": 3
}
```

Os três IDs são diferentes, mas o `recipe_type` é o mesmo. O mod registra esse tipo automaticamente no startup a partir dos controllers empacotados, e as receitas continuam existindo uma única vez:

```json
{
  "type": "infinity_nexus_multiblock:nexus_smelting",
  "ingredients": [
    "minecraft:iron_ingot",
    "minecraft:copper_ingot"
  ],
  "output": {
    "type": "item",
    "id": "mymod:bronze_ingot",
    "count": 1
  },
  "energy": 200,
  "time": 120
}
```

O `type` dentro do JSON de receita é o serializer da receita; neste mod ele usa o mesmo ID do `recipe_type` da definição. O `recipe_type` é a chave de categoria consultada no `RecipeManager`. São registros diferentes, mas pareados:

```java
RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.getValue(definition.recipeType());
RecipeMap recipes = level.recipeAccess().recipeMap();
```

O mod registra um `RecipeType` e um `RecipeSerializer` para cada `recipe_type` único dos controllers empacotados no namespace `infinity_nexus_multiblock`. Datapacks podem usar esses tipos, mas não podem registrar tipos novos.

Todos os tiers consultam o mesmo `RecipeType`; o `tier` só altera:

- capacidade de energia/fluido;
- velocidade de processamento;
- consumo de energia;
- paralelismo;
- quantidade de slots;
- limites de automação;
- multiplicadores aplicados ao resultado.

Se uma receita for exclusiva de tier, isso deve ser uma condição da receita, por exemplo `min_tier`/`max_tier`, ou um filtro na definição. Não criar um `recipe_type` diferente para cada tier só por causa de estatísticas.

### Receitas próprias (tipos registrados dinamicamente)

Implementado para receitas em `data/<namespace>/recipe/<arquivo>.json` com `type` igual a um `recipe_type` registrado pelo mod. No startup, cada `recipe_type` único declarado por um controller empacotado no namespace `infinity_nexus_multiblock` gera um `RecipeType` e um `RecipeSerializer` com o mesmo ID; atualmente o tipo embutido é `infinity_nexus_multiblock:nexus_smelting`:

```json
{
  "type": "infinity_nexus_multiblock:nexus_smelting",
  "ingredients": [
    "minecraft:iron_ore"
  ],
  "output": {
    "type": "item",
    "id": "minecraft:iron_ingot",
    "count": 1
  },
  "energy": 100,
  "time": 100
}
```

- `ingredients`: lista de `Ingredient`; use IDs de item, tags ou listas aceitas pelo codec vanilla.
- `fluid_ingredients`: opcional; lista de `SizedFluidIngredient` (`ingredient` e `amount` em mB). Cada fluido é procurado nos tanques da definição e consumido no fim do craft.
- `output`: saída tipada `item` (`id`, `count`, `components`), `fluid` (`id`, `amount`) ou `entity` (`id`, `spawn`). `spawn` aceita símbolo do pattern, ID de bloco ou `#tag`. O campo legado `result` continua aceito como saída de item.
- `energy`: total de FE do craft; opcional, padrão `0`.
- `time`: duração em ticks; opcional, padrão `100`, mínimo `1`.

Exemplo com fluidos:

```json
{
  "type": "infinity_nexus_multiblock:nexus_smelting",
  "ingredients": [
    "minecraft:iron_ore",
    "minecraft:gold_ore",
    "minecraft:coal"
  ],
  "fluid_ingredients": [
    { "ingredient": "minecraft:water", "amount": 500 },
    { "ingredient": "minecraft:lava", "amount": 500 }
  ],
  "output": {
    "type": "item",
    "id": "minecraft:gold_ingot",
    "count": 2
  },
  "energy": 300,
  "time": 120
}
```

Exemplo embutido: `data/infinity_nexus_multiblock/recipe/iron_ingot_from_iron_ore.json`.

A definição precisa de slots `input` para ingredientes de item e de um slot `output` para saída de item. Saídas de fluido exigem tanque e saídas de entidade exigem um `spawn` válido no pattern; esses casos não exigem slot de saída de item. O matching é shapeless: cada ingrediente procura qualquer slot de role `input` que contenha um item aceito, sem ordem fixa, e cada slot satisfaz no máximo um ingrediente por craft. Filtros de `item_slots` continuam limitando o que pode entrar em cada slot. A saída de item vai para o primeiro slot `output` compatível, a de fluido para o primeiro tanque compatível e a entidade spawna acima do bloco indicado. Cada craft consome uma unidade de cada ingrediente. Se houver `fluid_ingredients`, a receita só inicia com todos os fluidos disponíveis e cada craft consome `amount` mB do tanque que contém o fluido. O custo de energia é distribuído por tick e o craft pausa sem energia suficiente.

O progresso é persistido no NBT (`Progress`, `MaxProgress`, `Recipe`) e retomado após reload/restart. Quebrar a estrutura, alterar os inputs, encher os outputs ou remover a receita reseta o craft. A receita ativa é resolvida pelo ID salvo em `RecipeManager.byKey`.

Todos os `recipe_type` registrados pelo mod são processados pelo mesmo `MultiblockRecipe`. Tipos não registrados são ignorados (a máquina reseta) para não misturar `RecipeInput` próprio com tipos vanilla incompatíveis. Datapacks não podem registrar tipos novos; eles precisam reutilizar um tipo já criado por um controller empacotado.

### Receitas vanilla (adiado)

O objetivo futuro é aceitar receitas vanilla de:

- fornalha (`minecraft:smelting`, `minecraft:blasting`, `minecraft:smoking`, `minecraft:campfire_cooking`);
- grindstone;
- crafting;
- outros tipos vanilla que possam ser adaptados.

A dificuldade é que a máquina precisa cumprir os requisitos de slots e função da receita. Uma receita vanilla não declara apenas “um input”: ela assume tipos de slot, combustível, ferramentas, duração, categorias e, no caso de crafting, uma grade com formato. O adaptador precisa mapear:

- slots declarados na definição (`role`, `filter`, capacidade);
- ingredientes e tags aceitos pela receita;
- energia/fluidos exigidos pela máquina;
- resultados e subprodutos;
- condições específicas do tipo de receita.

Isso não será implementado agora. Antes, a máquina precisa ter capabilities e slots data-driven funcionando. Quando essa base existir, o adapter vanilla será avaliado por tipo de receita, começando por fornalha. O `recipe_type` continua compartilhado entre tiers; a máquina só aceita a receita se conseguir satisfazer os requisitos de slot/função.

### Compartilhar GUI e capabilities entre tiers

Para três tiers da mesma máquina, evitar repetir GUI/capabilities/elementos. Duas opções:

**Opção A: cada tier é independente**

Cada JSON repete `gui`, `capabilities` e `recipe_type`. É simples, mas gera duplicação.

**Opção B: família/template**

Um arquivo base contém o comportamento e a aparência compartilhados:

```json
{
  "schema_version": 1,
  "id": "mymod:alloy_smelter_template",
  "template": true,
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:nexus_smelting",
  "gui": {
    "elements": []
  },
  "capabilities": {}
}
```

Cada tier referencia a base e sobrescreve apenas o que muda:

```json
{
  "id": "mymod:alloy_smelter_advanced",
  "extends": "mymod:alloy_smelter_template",
  "tier": 2,
  "controller": {
    "block": "mymod:alloy_smelter_advanced"
  },
  "pattern": {
    "anchor": "C",
    "layers": [
      [
        "III",
        "ICI",
        "III"
      ]
    ],
    "matchers": {
      "I": "minecraft:iron_block"
    }
  },
  "overrides": {
    "capabilities": {
      "energy": {
        "capacity": 250000,
        "max_receive": 2500
      }
    }
  }
}
```

A herança implementada:

- aplica valores do pai, depois os campos diretos do filho e por último `overrides`;
- permite sobrescrever pattern, texturas, capacidades e elementos;
- mescla objetos recursivamente; arrays são substituídos inteiros;
- não herda `extends`, `family`, `overrides` nem `template`;
- detecta ciclos e pais inexistentes;
- valida o resultado final como se fosse um JSON único.

`template: true` impede o registro da definição como máquina, mesmo com pattern. Um arquivo sem pattern também é tratado como template quando é referenciado por outro; sem referência, continua sendo erro. `family` é alias de `extends`; usar os dois no mesmo arquivo é erro.

Se a library core já fornece definições de render e restrições de slots, o `extends` pode apontar para esses templates. Assim os tiers compartilham a mesma GUI e as mesmas regras de slots, enquanto cada um mantém seu próprio `id`, `tier` e `pattern`.

### Tipo `storage`

Máquinas de armazenamento não precisam de `recipe_type`:

```json
{
  "id": "mymod:warehouse_basic",
  "type": "storage",
  "tier": 1,
  "capabilities": {
    "item_slots": []
  }
}
```

O handler de `type=storage` apenas expõe inventário/capabilities; o handler de `type=recipe` resolve a família de receitas e executa o processamento.

## Comportamento e sincronização

O JSON não substitui a lógica da máquina. No estado atual, inventário, energia, fluidos e progresso já são persistidos; receitas dos tipos registrados pelo mod já são processadas no servidor. A implementação fornece:

- processamento de receitas dos tipos registrados dinamicamente;
- duração, consumo de energia e ticks por operação;
- entradas e saídas de itens;
- sincronização de progresso para o cliente via `ContainerData`;
- persistência de inventário, energia, fluidos e progresso;
- validação server-side;
- invalidação e revalidação ao quebrar blocos ou recarregar datapacks.

A GUI não exige definições no cliente: o servidor cria `MultiblockMenuLayout` e o envia no buffer de abertura do menu, junto com a posição do controller. Energia e fluidos são sincronizados pelo update tag do block entity, de forma coalescida a cada 5 ticks; o progresso usa os data slots do menu.

## Validação

Já implementado: `schema_version`, `id`, `type`, `recipe_type`, `tier`, pattern, `item_slots`, IDs únicos de tanks, conflito entre `energy.id` e tanks, validação completa de `gui.elements`, codec da receita, exigência de `type=recipe` para `progress_bar`/`arrow`, validação de `controller.block` contra o bloco registrado, criação dinâmica de controllers a partir de JSONs empacotados e registro dinâmico de `RecipeType`/serializer para cada `recipe_type` empacotado. Ainda pendente: existência de texturas de GUI em tempo de carregamento.

O builder visual cobre as mesmas regras da definição, além de `item_slots.faces`, herança/templates e receitas separadas. A validação de receitas do builder confere arquivo, `type`, ingredientes, fluidos, resultado, energy e time; o servidor continua sendo a autoridade final.

O loader valida:

- `schema_version` suportado;
- `id` presente e igual ao caminho do arquivo;
- exatamente um `anchor`;
- layers com dimensões consistentes;
- todos os símbolos com matcher;
- IDs de energia/fluidos/slots únicos;
- `item_slots` com IDs únicos e quantidade coerente com o layout;
- filtros de item/tag existentes;
- `limit` entre `1` e `64`;
- direções de face válidas;
- layout customizado com `item_slots`, posições `x`/`y` válidas, exatamente um `player_inventory` e referências de capability válidas;
- coordenadas e tamanhos dos elementos dentro de `gui.width`/`gui.height`;
- erros registrados no log sem derrubar o servidor inteiro.

## Checklist de implementação

- [x] Ler e validar `schema_version`, `id`, `type`, `recipe_type` e `tier`.
- [x] Integrar `RestrictedItemStackHandler` e expor capability de item no controller e nos membros formados.
- [x] Implementar `item_slots` com roles, filtros, limites e faces.
- [x] Adicionar `controller` data-driven.
- [x] Criar blocos controller dinâmicos a partir de JSONs empacotados, com assets gerados em memória.
- [x] Adicionar `family`/`extends` compartilhado entre tiers.
- [x] Adicionar herança ou template para GUI/capabilities.
- [x] Adicionar as seis texturas do controller.
- [x] Carregar definições no servidor e enviar o layout necessário ao cliente.
- [x] Separar capabilities de elementos visuais.
- [x] Expor capabilities de energia/fluidos e persistir no NBT.
- [x] Implementar energy e fluid genéricos.
- [x] Criar menu customizado e tela.
- [x] Adicionar `player_inventory`, slots e títulos.
- [x] Adicionar receita própria `nexus_smelting` e registro dinâmico de tipos a partir dos controllers empacotados.
- [x] Adicionar progresso, energia por tick e sincronização.
- [x] Adicionar `progress_bar`.
- [x] Adicionar `arrow` com sprite e `u`/`v` opcionais.
- [x] Validar e registrar erros de datapack.
- [x] Testar `/reload`, formação, quebra e persistência.
- [x] Builder visual com faces, herança e exportação de receitas data-driven.
