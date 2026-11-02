# Infinity Nexus Multiblock

Multiblocks data-driven para NeoForge 26.1.2.

O objetivo do mod é permitir que datapacks definam padrões de multibloco, capabilities, GUI, tipos de máquina e receitas, sem recompilar o mod. Isso vale para definições, receitas e dados de blocos já existentes; novos blocos controller dinâmicos e os `recipe_type` declarados por eles só são registrados no startup a partir de JSONs empacotados no próprio mod.

## Estado atual

Implementado:

- definições de pattern carregadas de datapacks;
- `schema_version`, `id`, `type`, `recipe_type` e `tier` lidos e validados;
- controllers dinâmicos criados no startup a partir de JSONs empacotados no mod, com bloco, item e block entity automáticos;
- `controller` opcional: com ele, o bloco usa o ID e as texturas declaradas; texturas ausentes caem para o fallback por face; sem ele, usa o próprio `id` e texturas fallback;
- nenhum bloco controller registrado manualmente pelo mod; controllers dinâmicos vêm de JSON empacotado no jar (esta build não inclui nenhum);
- seis texturas semânticas por face (`front`, `back`, `left`, `right`, `top`, `bottom`), usadas no modelo gerado dos controllers dinâmicos;
- formação, rotação, invalidação e persistência da estrutura;
- prioridade para resolver sobreposição entre multiblocos e revalidação no primeiro tick após carregar;
- inventário do controller usando `RestrictedItemStackHandler` da core;
- `item_slots` data-driven com roles, filtros, limites e faces;
- herança data-driven com `extends`/`family`, `overrides` e templates;
- menu e GUI data-driven, com slots posicionáveis e barras de energia/fluidos;
- capabilities data-driven de energia e fluidos, com filtros de porta por bloco, tag ou símbolo;
- `type=storage` funcional como inventário/capabilities/GUI, sem processamento de receitas;
- capability de item exposta em qualquer bloco membro formado (ou restrita por `capability_ports`), para automação por hopper em qualquer face;
- processamento de receitas próprias; cada `recipe_type` único declarado por um controller empacotado ganha `RecipeType`/serializer no startup e é processado pelo mesmo `MultiblockRecipe` genérico, com duração, consumo de energia, progresso e persistência;
- barra de progresso na GUI padrão e em GUIs customizadas;
- abertura do controller ao clicar em um membro;
- preview do pattern ao segurar um controller ou ao mirar em um controller não formado;
- montagem automática ao clicar em um controller não formado, usando blocos compatíveis do inventário;
- exportação da estrutura conectada para JSON no clipboard com `shift+click` no criativo, limitada ao `export.pattern_scan_limit` (padrão 256);
- matchers com blockstate específico, além de bloco e tag;
- assets gerados em memória para controllers dinâmicos: blockstate, modelo de bloco, modelo de item, item definition e lang `en_us`/`pt_br`;
- JEI cria uma categoria de estrutura e um catalisador por controller dinâmico; controllers `type=recipe` também geram uma categoria de receitas filtrada pelo `recipe_type`, e definições sem controller não geram nada;
- a categoria de receita usa as posições de `item_slots` x/y e os elementos `slot`, `fluid_bar`, `energy_bar`, `arrow` e `title` de `gui.elements`, com fundo `textures/gui/jei_base.png`; a categoria de estrutura usa um painel próprio;
- clicar na seta ou na barra de progresso da GUI abre a categoria de receitas daquele controller no JEI;
- transferência de itens do JEI para os slots `input` compatíveis; fluidos precisam ser inseridos manualmente;
- controllers dropam o próprio item via `getDrops`, sem exigir loot table;
- recarregamento de definições com `/reload`;
- sincronização dos membros e das definições para o cliente.

Implementado apenas como metadados (sem comportamento de máquina):

- `tier`.

Ainda planejado:

- comportamento especializado de `type=storage` além do inventário/capabilities/GUI atual;
- adaptadores para receitas vanilla;
- geração de assets para controllers dinâmicos em outros idiomas além de `en_us` e `pt_br`.

A especificação completa fica em [`docks/dsign.md`](docks/dsign.md). O guia passo a passo para criadores de datapacks, com todos os campos e exemplos completos, fica em [`docks/wiki/README.md`](docks/wiki/README.md). As seções abaixo documentam apenas o formato atualmente implementado.

## Requisitos

- Minecraft 26.1.2
- NeoForge 26.1.2.121
- Java 25

## Desenvolvimento

```powershell
.\gradlew clean compileJava
.\gradlew runServer
.\gradlew runClient
```

Para testar datapacks em desenvolvimento, use o mundo em `run/world` ou coloque o datapack em `run/world/datapacks/`.

## Builder visual

Abra `docks/builder/index.html` no navegador. O estado fica no `localStorage`; a importação de uma definição preserva campos desconhecidos em `extras`. As etapas são:

1. identidade e metadados;
2. pattern e âncora;
3. matchers;
4. `item_slots`, incluindo filtros, limites e `faces` de automação;
5. capabilities, incluindo os filtros de `capability_ports`;
6. controller;
7. GUI customizada;
8. receitas data-driven;
9. herança/templates;
10. revisão, checklist e exportação.

A etapa 08 edita receitas separadas em `data/<namespace>/recipe/<arquivo>.json`. Ingredientes usam uma linha por item/tag; fluidos usam `fluido quantidade`. O `type` da receita precisa ser o mesmo `recipe_type` da máquina. O JEI é gerado pelo runtime a partir do controller, não existe JSON extra de JEI.

A etapa 09 edita `extends`/`family`, `template` e `overrides`; o pai é resolvido pelo loader, não pelo builder. A revisão mostra os caminhos de definição, receitas e assets esperados, mas o builder não cria PNGs, modelos, blockstates ou classes Java para controllers externos.

A exportação da definição não inclui receitas. Baixe cada receita no card correspondente ou na revisão; os nomes dos arquivos precisam bater com o caminho mostrado.

## Referência para datapackers

### Criação dinâmica de controllers

O mod lê os JSONs empacotados no próprio jar no startup:

```text
data/infinity_nexus_multiblock/multiblock/<path>.json
```

Para cada definição válida nesse namespace, ele registra automaticamente um bloco controller, um item e o block entity compartilhado `dynamic_controller`.

- `controller` é opcional. Com ele, o bloco é criado em `controller.block` e usa `definitionId = id`; sem ele, o bloco é criado no próprio `id`.
- `controller.textures` alimenta o modelo gerado; cada textura ausente usa o fallback correspondente da pasta `fallback`. Sem `controller`, todas usam fallback. O fallback usa `_off` fora de processamento e `_on` durante o processamento (`front_off`, `front_on`, etc.).
- esta build não inclui controllers dinâmicos; não há bloco controller estático.
- Apenas JSONs dentro do jar do mod e no namespace `infinity_nexus_multiblock` criam blocos. Datapacks externos carregam definições, mas não registram blocos novos.
- Adicionar/remover um controller dinâmico exige recompilar o mod quando o JSON estiver no jar e reiniciar o jogo/servidor; `/reload` não registra blocos.

### Localização

Cada definição fica em:

```text
data/<namespace>/multiblock/<path>.json
```

O ID da definição também é declarado no JSON e deve corresponder ao caminho:

```text
data/mymod/multiblock/alloy_smelter.json
```

```json
"id": "mymod:alloy_smelter"
```

Se o `id` não corresponder ao caminho do arquivo, a definição é rejeitada.

### Estrutura do datapack

```text
my_datapack/
  pack.mcmeta
  data/
    mymod/
      multiblock/
        alloy_smelter.json
```

`pack.mcmeta` para 26.1.2:

```json
{
  "pack": {
    "description": "Meus multiblocos",
    "min_format": [101, 1],
    "max_format": 101
  }
}
```

### Formato atual

```json
{
  "schema_version": 1,
  "id": "mymod:alloy_smelter",
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:exemplo_smelting",
  "tier": 1,
  "controller": {
    "block": "mymod:alloy_smelter",
    "textures": {
      "front": "mymod:block/alloy_smelter/front",
      "back": "mymod:block/alloy_smelter/back",
      "left": "mymod:block/alloy_smelter/left",
      "right": "mymod:block/alloy_smelter/right",
      "top": "mymod:block/alloy_smelter/top",
      "bottom": "mymod:block/alloy_smelter/bottom"
    }
  },
  "inventory_rows": 3,
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
  }
}
```

Este exemplo é uma definição de datapack: ele **não** cria o bloco `mymod:alloy_smelter`. Para um controller dinâmico, use o namespace `infinity_nexus_multiblock` e empacote o JSON no mod; para datapack, registre o bloco em Java ou aponte `controller.block` para um controller dinâmico já existente.

Campos:

- `schema_version`: obrigatório; atualmente somente `1` é aceito.
- `id`: obrigatório; deve ser igual a `<namespace>:<path>` do arquivo.
- `type`: obrigatório; `recipe` ou `storage`.
- `recipe_type`: obrigatório para `type=recipe`; deve ser omitido para `type=storage`. Precisa ser um tipo já registrado pelo mod (veja a seção de receitas); datapacks não registram tipos novos.
- `tier`: opcional, mínimo `1`, padrão `1`.
- `priority`: opcional, padrão `0`; em sobreposição, maior prioridade vence e empate é decidido pela posição do controller.
- `extends` ou `family`: opcional; ID de outra definição usada como pai. Use apenas um dos dois. O pai é resolvido recursivamente e o filho herda os campos ausentes.
- `overrides`: opcional; objeto aplicado depois dos campos do próprio filho, útil para sobrescrever campos aninhados sem repetir o pai.
- `template`: opcional, padrão `false`; quando `true`, a definição é usada apenas como base e não é registrada como máquina.
- `controller`: opcional. Em JSON empacotado no namespace do mod, `block` define o ID do bloco dinâmico e `textures` alimenta o modelo gerado; em datapack, `block` precisa apontar para um `MultiblockControllerBlock` já registrado em Java cuja definição seja este `id`. `textures` exige as seis faces semânticas.
- `inventory_rows`: quantidade de linhas do inventário, de `1` a `6`. O padrão é `3`. Usado apenas quando `item_slots` está ausente; não afeta posições `x`/`y`.
- `pattern.layers`: lista de camadas, de baixo para cima.
- `pattern.anchor`: símbolo que representa o controller. O padrão é `C`.
- `pattern.matchers`: mapa de símbolo para bloco ou tag.

`type=recipe` executa receitas cujo `type` seja igual ao `recipe_type` da definição. O mod registra um `RecipeType` e um serializer por `recipe_type` único encontrado nos controllers empacotados; esta build não inclui controllers empacotados, então nenhum tipo é registrado por padrão. Datapacks podem reutilizar um tipo registrado, mas não criar tipos novos. `type=storage` e `tier` continuam apenas como metadados. As receitas vanilla estão adiadas.

### Herança e templates (`extends`/`family`)

Uma definição pode herdar outra com `extends` (ou `family`, como alias). O pai é resolvido recursivamente e o filho sobrescreve apenas os campos que declarar. O resultado final é validado como se fosse um único JSON.

Template compartilhado:

```json
{
  "schema_version": 1,
  "id": "mymod:alloy_smelter_template",
  "template": true,
  "type": "recipe",
  "recipe_type": "infinity_nexus_multiblock:exemplo_smelting",
  "capabilities": {
    "energy": {
      "id": "energy",
      "capacity": 100000,
      "max_receive": 1000,
      "max_extract": 0
    }
  }
}
```

Filho:

```json
{
  "id": "mymod:alloy_smelter_advanced",
  "extends": "mymod:alloy_smelter_template",
  "tier": 2,
  "controller": {
    "block": "mymod:alloy_smelter_advanced",
    "textures": {
      "front": "mymod:block/alloy_smelter_advanced/front",
      "back": "mymod:block/alloy_smelter_advanced/back",
      "left": "mymod:block/alloy_smelter_advanced/left",
      "right": "mymod:block/alloy_smelter_advanced/right",
      "top": "mymod:block/alloy_smelter_advanced/top",
      "bottom": "mymod:block/alloy_smelter_advanced/bottom"
    }
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

Regras de merge:

- objetos são mesclados recursivamente, então o filho pode sobrescrever apenas `capabilities.energy.capacity` e manter `id`/`max_extract` do pai;
- arrays substituem o valor inteiro; para mudar `item_slots`, `pattern.layers`, `capabilities.fluids` ou `gui.elements`, declare a lista completa no filho;
- campos diretos do filho são aplicados antes de `overrides`; se o mesmo campo aparecer nos dois, `overrides` vence;
- `extends`, `family`, `overrides` e `template` não são herdados como campos normais;
- o filho precisa ter seu próprio `id`; o `controller.block` final é validado contra esse `id`;
- ciclos de herança, pai inexistente, `extends` e `family` juntos ou `overrides` que não seja objeto geram erro no log.

Um arquivo com `"template": true` nunca é registrado como máquina, mesmo que tenha pattern. Um arquivo sem `pattern` também é tratado como template quando é referenciado por outro; se não for referenciado, continua sendo erro de definição.

### Slots de itens (`item_slots`)

`item_slots` é opcional. Quando tiver entradas, o inventário tem exatamente a quantidade de entradas do array; `inventory_rows` é usado apenas quando `item_slots` está ausente e não reposiciona slots com `x`/`y`. No layout padrão, cada slot com `x`/`y` válido usa a posição informada e os demais entram na grade automática com espaçamento de 18 px. Com `gui.elements`, slots com `x`/`y` são desenhados automaticamente na posição indicada; slots sem coordenadas não aparecem na GUI e ficam disponíveis apenas para automação. O formato antigo com elementos `slot` continua aceito quando nenhum `item_slots[]` define `x`/`y`.

```json
"item_slots": [
  {
    "id": "input_0",
    "role": "input",
    "filter": "minecraft:iron_ingot",
    "limit": 16,
    "x": 8,
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

Campos:

- `id`: obrigatório, único e não vazio.
- `role`: obrigatório; `input`, `output`, `fuel`, `upgrade`, `component`, `storage` ou `fluid`.
- `filter`: opcional; ID de item (`minecraft:iron_ingot`) ou tag (`#c:ingots`).
- `limit`: opcional, de `1` a `64`; padrão `64`.
- `x`/`y`: opcionais; posição do slot na GUI customizada. Informe os dois juntos; slots sem eles ficam ocultos no layout customizado.
- `faces`: opcional; sobrescreve o acesso padrão do role.
  - `insert`: lista de direções que podem inserir no slot.
  - `extract`: lista de direções que podem extrair do slot.
  - Direções: `north`, `south`, `east`, `west`, `up`, `down`.
  - Lista vazia desativa a operação para o slot.

Acesso padrão por role:

- `input`, `fuel`, `upgrade`, `component`, `fluid`: inserem por todas as faces; não extraem.
- `output`: não insere; extrai por todas as faces.
- `storage`: insere e extrai por todas as faces.

O filtro e o limite valem também para o menu do jogador. As restrições de face valem para automação (capability/hopper); o jogador pode retirar de qualquer slot visível. Sem `item_slots`, o controller usa o inventário genérico com todas as faces acessando todos os slots.

Em uma estrutura formada, a capability de item também é exposta em qualquer bloco membro, sujeita a `capability_ports`. Um hopper adjacente a qualquer membro acessa o mesmo inventário do controller, respeitando as faces configuradas.

`/reload` atualiza a definição e reconfigura o inventário de controllers já criados quando a definição muda; stacks que não cabem no novo tamanho são descartados.

### Energia e fluidos (`capabilities`)

`capabilities` é opcional e aceita `energy` e `fluids`. `item_slots` fica no nível raiz, fora de `capabilities`.

```json
"capabilities": {
  "energy": {
    "id": "energy",
    "capacity": 100000,
    "max_receive": 1000,
    "max_extract": 250
  },
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
      "capacity": 8000,
      "max_receive": 0,
      "max_extract": 500
    }
  ]
}
```

- `energy.id`: obrigatório, único e não vazio.
- `energy.capacity`: capacidade máxima em FE, mínimo `1`.
- `energy.max_receive`: FE aceitos por operação de inserção. Padrão `0` quando omitido.
- `energy.max_extract`: FE liberados por operação de extração. Padrão `0` quando omitido.
- `fluids[].id`: obrigatório, único entre todos os tanks e diferente do `energy.id`.
- `fluids[].capacity`: capacidade do tank em mB, mínimo `1`.
- `fluids[].max_receive`: mB aceitos por inserção. Padrão `0` quando omitido.
- `fluids[].max_extract`: mB liberados por extração. Padrão `0` quando omitido.
- `fluids[].filter`: opcional; ID de fluido (`minecraft:water`) ou tag (`#c:water`). Sem filtro, o tank aceita qualquer fluido.

Quando presentes, as capabilities são expostas em todas as faces do controller, sujeitas a `capability_ports`. O `energy.id` e os `fluids[].id` são usados pela GUI para localizar as barras, mas ainda não alteram o comportamento por lado. Energia e fluidos são persistidos no NBT do controller. `/reload` atualiza a definição, cria capabilities que passaram a existir e remove as que deixaram de existir; armazenamentos de energia/fluidos já existentes não são reconfigurados. Novos controllers usam a nova definição.

### GUI (`gui`)

`gui` é opcional. Sem `gui` ou sem `gui.elements`, o controller usa um layout padrão com:

- a quantidade de `item_slots` (ou `inventory_rows * 9` quando ausente), respeitando `x`/`y` explícitos que caibam em `176x166` e auto-posicionando os demais em grade de 9 colunas (ou 8 quando há barras de energia/fluido ou barra de progresso), com espaçamento de 18 px;
- inventário do jogador abaixo dos slots;
- título no topo;
- uma barra para `energy` e uma para cada tank de fluido, à esquerda dos slots;
- uma barra de progresso à direita dos slots quando `type=recipe` e `recipe_type` estiver definido;
- largura fixa `176` e altura calculada para caber tudo (mínimo `166`).

Com `gui.elements` não vazio, o layout é customizado. Nesse caso:

- `item_slots` só é obrigatório quando houver elementos `slot` ou posições `x`/`y`; sem slots de item, a GUI pode conter apenas barras e o inventário do jogador;
- slots visíveis são os `item_slots[]` com `x`/`y`; slots sem coordenadas ficam apenas para automação;
- não misture `x`/`y` em `item_slots` com elementos `slot` no mesmo layout; o formato antigo com elementos `slot` continua aceito quando nenhum slot tem coordenadas;
- no formato antigo, cada elemento `slot` precisa referenciar um `item_slots[].id` existente, sem IDs desconhecidos ou repetidos;
- exatamente um `player_inventory` é obrigatório;
- `energy_bar.capability` precisa ser igual a `energy.id`;
- `fluid_bar.capability` precisa ser igual a um `fluids[].id`;
- `progress_bar` e `arrow` só são aceitos em `type=recipe`;
- slots, player inventory e barras precisam caber em `width`/`height`.

```json
"item_slots": [
  { "id": "input_0", "role": "input", "x": 44, "y": 17 },
  { "id": "output_0", "role": "output", "x": 116, "y": 35 }
],
"gui": {
  "width": 176,
  "height": 166,
  "texture_width": 256,
  "texture_height": 256,
  "elements": [
    { "type": "title", "text": "gui.mymod.alloy_smelter", "x": 8, "y": 6, "color": "0xFF404040" },
    { "type": "energy_bar", "capability": "energy", "x": 8, "y": 17, "width": 12, "height": 52, "direction": "up" },
    { "type": "fluid_bar", "capability": "input_tank", "x": 26, "y": 17, "width": 12, "height": 52 },
    { "type": "player_inventory", "x": 8, "y": 84, "text": "container.inventory" }
  ]
}
```

O trecho acima é resumido e não define `texture`, então a tela usa o painel cinza. Ele mostra apenas dois slots via `x`/`y`; os demais `item_slots` sem coordenadas ficam apenas para automação.

- `texture`: opcional; sem textura, a tela desenha um painel cinza.
- `width`/`height`: área desenhada, padrão `176x166`.
- `texture_width`/`texture_height`: tamanho da textura, padrão `256x256`.
- Campos comuns dos elementos: `type`, `x`, `y`, `width`, `height`, `text`, `capability`, `color`, `shadow`, `direction`, `u` e `v`. `slot` existe apenas no formato antigo de elemento `slot`.
- `x`/`y`: padrão `0`. `width`/`height`: padrão `16`. `shadow`: padrão `true`. `direction`: `up`, `down`, `left` ou `right`; padrão `up`.
- `u`/`v`: origem do sprite na textura da GUI; padrão é o próprio `x`/`y` do elemento. Use quando o sprite estiver em outra posição da textura.
- `color`: ARGB; aceita inteiro decimal (ex.: `4285257290`) ou string hexadecimal (ex.: `"0xFF6BD64A"` ou `"#6BD64A"`); alpha `0` é trocado por `0xFF`.
- `text` é uma chave de tradução. Em `title`, vazio usa o nome do bloco; em `player_inventory`, vazio usa `container.inventory`.
- Slots têm `18x18`; o player inventory ocupa `162x76` (3 linhas + hotbar) e desenha o título 11 pixels acima.
- `energy_bar`: `capability` igual a `energy.id`; `direction` define o preenchimento; tooltip mostra energia/capacidade em FE.
- `fluid_bar`: `capability` igual a `fluids[].id`; usa `FluidTankRenderer` e sempre preenche de baixo para cima (ignora `direction`).
- `progress_bar`: só em `type=recipe`; desenha primeiro o sprite em `u`/`v` como fundo e depois o preenchimento; `direction` define o sentido; tooltip mostra a porcentagem do craft atual.
- `arrow`: só em `type=recipe`; desenha apenas a seta recortada pelo progresso, como a seta da fornalha, usando `u`/`v` como origem do sprite. Não desenha fundo.
- `progress_bar` e `arrow` podem ser usados juntos ou separadamente; ambos representam o mesmo progresso.
- Tipos implementados: `title`, `text`, `player_inventory`, `energy_bar`, `fluid_bar`, `progress_bar`, `arrow`; `slot` permanece apenas para compatibilidade com layouts antigos.
- Campos antigos como `id`, `texture` em elemento, `title`, `align`, `tooltip`, `progress`, `background`, `fill` e `inventory_title` não são usados.

O servidor cria o layout e o envia no buffer de abertura do menu junto com a posição do controller; o cliente não precisa carregar o datapack. `/reload` atualiza a definição e reconfigura o layout de controllers existentes quando a definição muda; o layout também é persistido no block entity para sobreviver ao carregamento antes da sincronização.

### Receitas

Receitas próprias ficam em:

```text
data/<namespace>/recipe/<arquivo>.json
```

O `recipe_type` da definição precisa corresponder a um tipo registrado pelo mod, e o `type` da receita precisa usar exatamente esse ID. No startup, o mod lê os controllers empacotados e registra um `RecipeType` + `RecipeSerializer` para cada `recipe_type` único no namespace `infinity_nexus_multiblock`, sem exigir Java extra. Esta build não inclui controllers empacotados, então nenhum tipo é registrado por padrão; os exemplos abaixo usam `exemplo_smelting` apenas como nome ilustrativo. Para adicionar um tipo, inclua um controller empacotado que declare o novo `recipe_type` e reinicie o jogo/servidor. Datapacks podem reutilizar um tipo registrado, mas não criar tipos novos.

```json
{
  "type": "infinity_nexus_multiblock:exemplo_smelting",
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

- `type`: obrigatório; use o mesmo ID de um `recipe_type` registrado pelo mod (o exemplo usa `exemplo_smelting`, que só existe se um controller empacotado o declarar).
- `ingredients`: lista de `Ingredient`; use IDs de item, tags `#namespace:tag` ou listas aceitas pelo codec vanilla.
- `fluid_ingredients`: opcional; lista de `SizedFluidIngredient` com `ingredient` (ID ou tag de fluido) e `amount` em mB. A máquina procura cada fluido nos tanques da definição e consome no fim do craft.
- `output`: saída tipada. Use `type: "item"` com `id`, `count` opcional (padrão `1`) e `components` opcional; `type: "fluid"` com `id` e `amount` em mB; ou `type: "entity"` com `id` e `spawn` (símbolo do pattern, ID de bloco ou tag). O campo legado `result` continua aceito como saída de item.
- `energy`: total de FE consumido pelo craft; opcional, padrão `0`.
- `time`: duração em ticks; opcional, padrão `100`, mínimo `1`.

Exemplo com fluidos:

```json
{
  "type": "infinity_nexus_multiblock:exemplo_smelting",
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

A definição precisa de slots de entrada para ingredientes de item e de um slot `output` para saída de item. Receitas que só usam fluidos ou que geram fluido/entidade não exigem slot de saída de item, mas exigem o tanque ou seletor de spawn correspondente.

Mapeamento de slots:

- o matching é shapeless: cada ingrediente procura qualquer slot com role `input` que contenha um item aceito, sem depender da ordem de `item_slots`;
- cada slot de input satisfaz no máximo um ingrediente por craft, então a receita precisa de slots suficientes com os itens correspondentes;
- filtros de `item_slots` continuam limitando o que pode entrar em cada slot; o matching considera apenas os itens presentes;
- a saída de item é inserida no primeiro slot `output` que aceite o item e tenha espaço, respeitando filtro e `limit`;
- a saída de fluido usa o primeiro tanque que aceite o fluido e tenha capacidade;
- a saída de entidade spawna acima do bloco indicado por `spawn` (símbolo do pattern, ID de bloco ou `#tag`).

Comportamento:

- cada craft consome `1` de cada ingrediente;
- o custo de energia é distribuído ao longo dos ticks; sem energia suficiente, o progresso fica pausado;
- o progresso é persistido no NBT do controller e retomado após recarregar o chunk/servidor;
- quebrar a estrutura, remover um ingrediente, encher os outputs ou remover a receita do datapack reseta o craft;
- a barra de progresso aparece no layout padrão quando a definição é `type=recipe` e tem `recipe_type`; em GUI customizada, adicione um elemento `progress_bar` e/ou `arrow`.

### Receitas vanilla (adiado)

O objetivo futuro é aceitar receitas vanilla de fornalha, grindstone, crafting e tipos semelhantes. A máquina precisará cumprir os requisitos de slot e função da receita. Até lá, apenas receitas dos tipos registrados pelo mod são processadas.

### Pattern

Regras atuais:

- todas as camadas devem ter a mesma quantidade de linhas;
- todas as linhas devem ter a mesma largura;
- espaços são ignorados, mas continuam contando para a largura da linha;
- deve existir exatamente um `anchor`;
- cada símbolo usado, exceto o anchor, precisa ter um matcher;
- o anchor é removido da lista de membros;
- a estrutura é definida olhando para SOUTH e é rotacionada conforme o `FACING` do controller.

Exemplo com espaços para manter a largura:

```json
"layers": [
  [
    "III",
    "I I",
    "III"
  ]
]
```

### Matchers

Bloco específico:

```json
"matchers": {
  "I": "minecraft:iron_block"
}
```

Tag de bloco:

```json
"matchers": {
  "I": "#c:storage_blocks/iron"
}
```

A tag usa o prefixo `#` e é resolvida no registro de blocos.

Blockstate específico:

```json
"matchers": {
  "I": {
    "block": "minecraft:oak_stairs",
    "state": {
      "facing": "north",
      "half": "bottom",
      "shape": "straight",
      "waterlogged": "false"
    }
  }
}
```

O objeto aceita `block` ou `tag` (exatamente um) e um mapa opcional `state`. Apenas as propriedades informadas em `state` são verificadas; as omitidas são ignoradas. Objeto sem `state` aceita qualquer state do bloco. A tag pode ser escrita com ou sem `#`.

Os states são relativos à orientação canônica do pattern, com o controller virado para `south`. Ao validar, montar ou pré-visualizar com o controller em outra direção, o state esperado é rotacionado junto com as posições.

Ainda não há suporte a:

- NBT;
- listas de alternativas (`any`);
- matchers opcionais.

### Controllers dinâmicos empacotados

O mod não registra blocos controller manualmente. Controllers dinâmicos são criados no startup a partir de JSONs empacotados no namespace `infinity_nexus_multiblock`; esta build não inclui nenhum, então não há controller jogável por padrão.

Para adicionar um controller, crie o JSON em `src/main/resources/data/infinity_nexus_multiblock/multiblock/<path>.json`, recompile o mod e reinicie o jogo/servidor. Para testar outro pattern com um bloco já existente, sobrescreva a definição correspondente em um datapack e use `/reload`.

Criar um JSON novo em um datapack **não** cria um bloco controller: use um controller existente ou registre um bloco manual em Java como API de extensão.

### Preview e montagem automática

Com um controller na mão, o cliente desenha o pattern no local onde o jogador está mirando. A preview usa o mesmo `FACING` que o bloco teria ao ser colocado; posições e states direcionais são rotacionados conforme esse `FACING`.

Ao mirar em um controller ainda não formado, a preview é ancorada na posição e rotação do próprio controller. As células usam o modelo real do bloco esperado:

- modelo translúcido: posição substituível; o modelo vem do item compatível no inventário ou de um bloco representativo;
- contorno apenas: posição já correta ou bloqueada por um bloco não substituível;
- modelo do controller: célula do controller.

Clicar em um controller não formado monta o máximo possível usando blocos do inventário principal e da mão secundária. Blocos já corretos são mantidos, posições bloqueadas ou sem item são puladas e itens são consumidos apenas para os blocos realmente colocados. A estrutura permanece parcial se ainda faltar algo e forma assim que todas as células estiverem corretas. Agachar evita a montagem automática e mantém o comportamento normal de colocação.

### Exportação no criativo

No modo criativo, `shift+click` com o botão direito em qualquer controller copia para o clipboard um JSON da estrutura conectada. O scan usa flood-fill em seis direções, ignora ar, inclui o controller e para no limite configurado em `export.pattern_scan_limit` (padrão 256); se houver mais blocos conectados, a actionbar avisa que a exportação foi truncada.

O JSON exportado contém apenas o necessário para carregar a definição:

- `schema_version`, `id` e `type=storage`;
- `pattern` com camadas, `anchor` e matchers por bloco + blockstate.

O `id` usa as coordenadas do controller (`infinity_nexus_multiblock:exported_x_y_z`). Para usar o JSON como definição, ajuste o `id` e o caminho do arquivo para que coincidam. A actionbar confirma a exportação e toca um som de confirmação.

A exportação normaliza posições e states para a orientação canônica (controller virado para `south`), então o JSON pode ser reutilizado com qualquer `FACING` do controller.

### Recarregamento

Use `/reload` no servidor para recarregar as definições.

- controllers formados são revalidados;
- blocos controller dinâmicos e o pack de assets gerado são criados apenas no startup; `/reload` não registra, remove nem regenera blocos/modelos;
- se a definição não existir mais, a estrutura é invalidada;
- inventário e layout da GUI são reconfigurados quando a definição fica disponível ou muda; capabilities ausentes são criadas e as removidas são descartadas, mas armazenamentos de energia/fluidos já existentes não são reconfigurados (veja a seção de capabilities);
- o layout da GUI também é persistido no block entity para sobreviver ao carregamento antes da sincronização;
- os membros são persistidos no block entity e sincronizados via NBT;
- as definições resolvidas são enviadas aos clientes para a preview.

Para adicionar ou remover um controller dinâmico, recompile o mod quando o JSON estiver no jar e reinicie o jogo/servidor. Datapacks externos não criam blocos; eles só podem adicionar ou sobrescrever definições de blocos já registrados.

### Erros comuns

- `Duplicate fluid tank id`: cada `fluids[].id` precisa ser único.
- `Energy capability id conflicts with a fluid tank id`: o `energy.id` deve ser diferente de todos os `fluids[].id`.
- `Unknown fluid`: o ID do fluido no filtro não existe.
- `Definition id ... does not match file id`: o campo `id` não corresponde ao caminho do JSON.
- `Unsupported schema_version`: use `schema_version: 1`.
- `Unknown multiblock type`: use `recipe` ou `storage`.
- `Recipe multiblocks need a recipe_type`: adicione `recipe_type` quando `type` for `recipe`.
- `Storage multiblocks cannot define a recipe_type`: remova `recipe_type` quando `type` for `storage`.
- `Unknown parent multiblock`: o `extends`/`family` aponta para um ID que não foi carregado.
- `Circular extends involving`: a cadeia de herança voltou para uma definição já em resolução.
- `Use either extends or family, not both`: escolha apenas um dos campos de herança.
- `overrides must be an object`: `overrides` precisa ser um objeto JSON.
- `template must be a boolean`: use `true` ou `false` em `template`.
- `Unknown block`: o ID do bloco no matcher não existe.
- `No matcher registered for pattern symbol`: falta um matcher para um símbolo usado.
- `needs exactly one anchor`: não existe exatamente um `C`.
- `same number of rows` / `same width`: as camadas ou linhas estão inconsistentes.
- `Duplicate item slot id`: cada `item_slots[].id` precisa ser único.
- `Unknown item`: o ID do item no filtro não existe.
- `Item slot ... must define both x and y`: informe `x` e `y` juntos ou remova ambos.
- `Slot position cannot be negative`: use coordenadas `x`/`y` maiores ou iguais a zero.
- `Define slot positions in item_slots x/y or gui slot elements, not both`: use apenas o formato novo (`item_slots` com `x`/`y`) ou o antigo (elementos `slot`).
- `Custom gui elements with slot entries require item_slots`: adicione `item_slots` ao usar `gui.elements`.
- `Unknown item slot id in gui`: o `slot` do elemento não existe em `item_slots` (formato antigo).
- `Duplicate slot element for item slot`: cada `item_slots[].id` pode aparecer em apenas um elemento `slot` (formato antigo).
- `Custom gui elements require a player_inventory element`: adicione exatamente um `player_inventory`.
- `gui has an energy_bar but the multiblock has no energy capability`: adicione `capabilities.energy` ou remova a barra.
- `Unknown energy capability in gui` / `Unknown fluid tank id in gui`: ajuste `capability` para o ID existente.
- `gui has a progress_bar/arrow but the multiblock is not a recipe multiblock`: use `type=recipe`.
- `exceeds gui bounds`: aumente `gui.width`/`gui.height` ou mova o elemento.
- `Unknown controller block`: `controller.block` não existe; um datapack não pode criar o bloco. Use um controller dinâmico já empacotado, empacote o JSON no mod ou registre o bloco em Java.
- `Discovered 0 dynamic multiblock controllers`: nenhum JSON válido foi encontrado no jar; confira namespace, caminho e startup.
- `Duplicate dynamic controller block`: duas definições usam o mesmo `controller.block`; use IDs de bloco diferentes.
- `Skipping controller block ... because it is not in this namespace`: o controller dinâmico precisa usar o namespace `infinity_nexus_multiblock`.
- Receita não carregada: confira `data/<namespace>/recipe/<arquivo>.json`, o campo `type` e os IDs em `ingredients`/`output` (ou `result` legado).
- Definição não carregada: confira o caminho `data/<namespace>/multiblock/<id>.json` e o log do servidor.

## Estrutura do projeto

- `src/main/java/com/Infinity/Nexus/Multiblock/api`: pattern, definição, GUI, layout de menu, tipo de máquina, matchers, item slots, energia e filtros de fluido.
- `src/main/java/com/Infinity/Nexus/Multiblock/block`: controller, block entity, handler de itens e storages de energia/fluidos.
- `src/main/java/com/Infinity/Nexus/Multiblock/menu`: `MultiblockMenu` e slots com limite.
- `src/main/java/com/Infinity/Nexus/Multiblock/recipe`: receita própria e `RecipeInput`.
- `src/main/java/com/Infinity/Nexus/Multiblock/client`: tela, cache e eventos de cliente.
- `src/main/java/com/Infinity/Nexus/Multiblock/data`: loader, definições e saved data.
- `src/main/java/com/Infinity/Nexus/Multiblock/event`: eventos de interação e quebra.
- `src/main/java/com/Infinity/Nexus/Multiblock/registry`: scanner de controllers dinâmicos, blocks, itens, menus, receitas e tipos de receita.
- `src/main/java/com/Infinity/Nexus/Multiblock/resource`: resource pack em memória que gera assets dos controllers dinâmicos.
- `src/main/resources/assets/infinity_nexus_multiblock`: texturas, lang base e assets estáticos.
- `src/main/resources/data/infinity_nexus_multiblock`: esta build não inclui definições nem receitas embutidas; para adicionar controllers/receitas, crie os JSONs aqui e recompile.

## Licença

All Rights Reserved.
