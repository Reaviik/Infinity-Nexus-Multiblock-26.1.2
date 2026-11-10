# Wiki do Desenvolvedor

Guia prático para criar máquinas multibloco com o **Infinity Nexus Multiblock**.

- Minecraft: `26.1.2`
- NeoForge: `26.1.2.121`
- Java: `25`
- Idioma dos exemplos: português
- Schema atual: `schema_version: 2`

Este guia documenta **tudo o que o JSON aceita hoje** e mostra exemplos completos. O objetivo é que você consiga sair de uma ideia e chegar a uma máquina formável, com inventário, energia, fluidos, GUI e receita.

Para montar e validar a definição visualmente, abra o [Builder de multiblocos](../builder/index.html). Ele exporta o mesmo JSON descrito neste guia.

## Índice

1. [Como uma máquina funciona](#1-como-uma-máquina-funciona)
2. [Arquivos, pasta e ID](#2-arquivos-pasta-e-id)
3. [Estrutura completa do JSON](#3-estrutura-completa-do-json)
4. [Pattern](#4-pattern)
5. [Matchers](#5-matchers)
6. [Slots de item](#6-slots-de-item)
7. [Energia e fluidos](#7-energia-e-fluidos)
8. [GUI](#8-gui)
9. [Controller único e receita](#9-controller-único-e-receita)
10. [Receitas](#10-receitas)
11. [Herança e templates](#11-herança-e-templates)
12. [Priority, tier e sobreposição](#12-priority-tier-e-sobreposição)
13. [Exportar e importar](#13-exportar-e-importar)
14. [Reload e ciclo de vida](#14-reload-e-ciclo-de-vida)
15. [Exemplos completos](#15-exemplos-completos)
16. [Erros comuns](#16-erros-comuns)
17. [Checklist final](#17-checklist-final)
18. [Limitações atuais](#18-limitações-atuais)

## 1. Como uma máquina funciona

Uma máquina é a combinação de três partes:

1. **O controller único do mod.**
   O bloco, o block entity e o item são registrados uma única vez como `infinity_nexus_multiblock:multiblock_controller`. Toda definição usa esse mesmo controller; não existe bloco controller por máquina. Texturas por definição são opcionais no campo top-level `textures`.

2. **Uma definição JSON.**
   Ela diz qual é o pattern, quais blocos formam a estrutura, quais slots existem, qual GUI usar, quais capabilities existem e qual receita processar. O vínculo com o controller é feito pelo componente `infinity_nexus_multiblock:multiblock_definition`, cujo valor é o `id` da definição.

3. **Um pattern formado no mundo.**
   O jogador coloca o controller virado para a direção correta e clica nele para montar. Quando todas as células batem, o controller fica `formed=true`, abre a GUI e passa a processar receitas.

Fluxo resumido:

```text
Startup: registra o controller, o item e o tipo de receita
JSON: schema_version 2 + pattern + slots + capabilities + GUI + recipe opcional
Mundo: controller é colocado com o componente de definição e clicado
Loader: valida o JSON e cria a definição
Estrutura: pattern é validado com rotação
Máquina: inventário, energia, fluidos, GUI e receita funcionam
```

Não há mais `controller` nem `recipe_type` na definição. O controller compartilhado exibe o nome da definição via `MultiblockDefinition#displayName()`; a chave de tradução é `multiblock.<namespace>.<path>` e o fallback é o path formatado, como `fabrica_de_voxel_cube` -> `Fabrica De Voxel Cube`. O campo opcional `textures` permite especializar as texturas do controller por definição; veja a seção 9.2.

No JEI, a categoria de **receita** é filtrada pelo componente de definição do controller. A categoria de **estrutura** ainda usa o controller genérico como catalisador e pode listar todas as estruturas; trate isso como limitação conhecida até o filtro por definição ser implementado.

A categoria de receita do JEI usa o mesmo layout da GUI da máquina para `item_slots` x/y e elementos `slot`, `fluid_bar`, `energy_bar`, `arrow`, `entity` e `title`; o fundo é `textures/gui/jei_base.png`. A categoria de estrutura usa um painel próprio.

Na GUI da máquina, clicar na seta ou na barra de progresso abre as receitas daquela definição no JEI. Na categoria JEI, a transferência move ingredientes de item para slots `input` compatíveis; fluidos são inseridos manualmente.

## 2. Arquivos, pasta e ID

### 2.1 Definição de multibloco

```text
data/<namespace>/multiblock/<arquivo>.json
```

O campo `id` precisa ser exatamente `<namespace>:<arquivo>`.

Exemplo:

```text
data/minhamod/multiblock/fornalha.json
```

```json
"id": "minhamod:fornalha"
```

Se o `id` não bater com o caminho, a definição é rejeitada com:

```text
Definition id ... does not match file id ...
```

### 2.2 Receita

```text
data/<namespace>/recipe/<arquivo>.json
```

Toda receita de máquina usa `"type": "infinity_nexus_multiblock:multiblock"` e declara `"definition": "<namespace>:<path>"`. Receitas do controller são geradas pelo mod em `data/<namespace>/recipe/controller/<path>.json`; veja a seção 9.

### 2.3 Datapack

```text
meu_datapack/
  pack.mcmeta
  data/
    minhamod/
      multiblock/
        fornalha.json
      recipe/
        ferro.json
```

Exemplo de `pack.mcmeta` para 26.1.2:

```json
{
  "pack": {
    "description": "Minhas máquinas",
    "min_format": [101, 1],
    "max_format": 101
  }
}
```

Depois de colocar o datapack em `world/datapacks`, use `/reload` ou reinicie o servidor.

Datapacks e KubeJS podem adicionar ou sobrescrever definições e receitas. O controller é único e já registrado pelo mod, então qualquer namespace pode usar a mesma estrutura. A receita shaped opcional do controller é gerada a partir do JSON empacotado no jar do mod ou de `kubejs/data`.

## 3. Estrutura completa do JSON

### 3.1 Campos de nível raiz

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `schema_version` | sim | inteiro | — | Hoje só aceita `2`. |
| `id` | sim | identifier | — | Precisa ser igual ao caminho do arquivo. |
| `type` | sim | string | — | `recipe` ou `storage`. |
| `priority` | não | inteiro | `0` | Usado para resolver sobreposição entre estruturas. Pode ser negativo. |
| `tier` | não | inteiro | `1` | Mínimo `1`. Hoje é apenas metadado; não altera comportamento. |
| `recipe` | não | objeto | ausente | Receita shaped de crafting table do controller, lida do jar do mod e de `kubejs/data`; não é lida de datapacks comuns. Veja a seção 9. |
| `inventory_rows` | não | inteiro | `3` | De `1` a `6`. Usado só quando `item_slots` está vazio. |
| `item_slots` | não | lista | `[]` | Define os slots do inventário. Veja a seção 6. |
| `capabilities` | não | objeto | vazio | Energia e fluidos. Veja a seção 7. |
| `capability_ports` | não | objeto | ausente | Restringe quais blocos membros expõem item, energia e fluido. Veja a seção 7.3. |
| `textures` | não | objeto | ausente | Sobrescreve as texturas do controller por definição. Aceita `front`, `side`, `top`, `bottom`, `particle` e `active`. Veja a seção 9.2. |
| `gui` | não | objeto | ausente | Layout padrão ou customizado. Veja a seção 8. |
| `sounds` | não | objeto | ausente | Sons de processamento e conclusão de receitas. Veja a seção 10.11. |
| `pattern` | sim para máquina | objeto | — | Obrigatório para uma definição registrada. Templates podem omitir. |

### 3.2 Campos de herança

Esses campos são processados **antes** do schema normal:

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `extends` | não | identifier | — | ID do pai. Não use junto com `family`. |
| `family` | não | identifier | — | Alias de `extends`. Não use junto com `extends`. |
| `overrides` | não | objeto | — | Aplicado depois dos campos do filho; vence em caso de conflito. |
| `template` | não | booleano | `false` | Se `true`, o arquivo não é registrado como máquina. |

Regras:

- `extends` e `family` são a mesma coisa; use apenas um.
- O pai é resolvido recursivamente.
- Objetos são mesclados recursivamente.
- Listas substituem a lista inteira.
- `overrides` não pode definir `extends`, `family`, `overrides` ou `template`.
- Um template com `template: true` é sempre ignorado como máquina, mesmo tendo pattern.
- Um arquivo sem `pattern` é tratado como template quando é referenciado por outro. Se não for referenciado, é erro de definição.

### 3.3 Exemplo mínimo

```json
{
  "schema_version": 2,
  "id": "minhamod:minha_maquina",
  "type": "storage",
  "inventory_rows": 3,
  "pattern": {
    "anchor": "C",
    "layers": [
      [
        "III",
        "III",
        "III"
      ],
      [
        "III",
        "III",
        "ICI"
      ],
      [
        "III",
        "III",
        "III"
      ]
    ],
    "matchers": {
      "I": "minecraft:iron_block"
    }
  }
}
```

Esse JSON já carrega como definição em qualquer namespace. O item controller recebe o componente de definição com `minhamod:minha_maquina`; ao colocar o bloco, o block entity usa esse ID. Não é necessário criar bloco por máquina; texturas por definição são opcionais via `textures`.

### 3.4 Migrar do schema 1

1. Troque `"schema_version": 1` por `"schema_version": 2`.
2. Remova o objeto `controller`; o bloco e o block entity agora são compartilhados. Se quiser texturas por máquina, use o novo campo top-level `textures`.
3. Remova `recipe_type` da definição; toda receita de máquina usa `infinity_nexus_multiblock:multiblock`.
4. Em cada receita, troque o tipo antigo por `"type": "infinity_nexus_multiblock:multiblock"` e adicione `"definition": "<id da definição>"`.
5. Mantenha o campo top-level `recipe` apenas se quiser gerar a receita shaped do controller.
6. Gere o controller com `/inmb give <id>` ou pela aba criativa e rode `/reload`.

## 4. Pattern

### 4.1 Regras

- `layers` é uma lista de camadas, da **base para o topo**.
- Cada camada é uma lista de linhas.
- Todas as camadas precisam ter a mesma quantidade de linhas.
- Todas as linhas precisam ter a mesma largura.
- Espaços (`" "`) são ignorados como células, mas continuam contando para a largura.
- `anchor` é opcional e usa `C` por padrão.
- Deve existir **exatamente um** símbolo de âncora.
- Cada símbolo usado, exceto a âncora, precisa ter um matcher.
- A célula da âncora é o controller. Ela não entra na lista de membros e não é testada pelo matcher.
- O matcher da âncora é opcional e, se existir, é ignorado.
- A orientação canônica é com o controller virado para **SOUTH**.
- Ao validar, montar ou pré-visualizar com o controller em outra direção, posições e states direcionais são rotacionados.

### 4.2 Ordem das linhas e colunas

Na orientação canônica (`SOUTH`):

- a camada `0` é a mais baixa;
- a linha `0` fica no norte;
- linhas seguintes avançam para o sul;
- a coluna `0` fica no oeste;
- colunas seguintes avançam para o leste;
- a âncora `C` é a posição do controller.

Exemplo de uma camada 3x3:

```json
[
  "III",
  "I I",
  "III"
]
```

A linha do meio tem um espaço no centro. O espaço não é uma célula, mas a linha continua com largura 3.

### 4.3 Rotação

| `FACING` do controller | Rotação aplicada |
|---|---|
| `SOUTH` | nenhuma |
| `NORTH` | 180 graus |
| `EAST` | 90 graus anti-horário |
| `WEST` | 90 graus horário |

O JSON sempre descreve a máquina olhando para `SOUTH`. Se você escrever `facing: "north"` em um matcher, esse valor será rotacionado junto com a estrutura quando o controller estiver em outra direção.

### 4.4 Exemplo de pattern 3x3x3

```json
"pattern": {
  "anchor": "C",
  "layers": [
    [
      "III",
      "III",
      "III"
    ],
    [
      "III",
      "III",
      "ICI"
    ],
    [
      "III",
      "III",
      "III"
    ]
  ],
  "matchers": {
    "I": "minecraft:iron_block"
  }
}
```

O controller fica na face sul da camada do meio. O resto do cubo é `minecraft:iron_block`.

### 4.5 Tamanho

O loader não impõe um limite de tamanho para o pattern. O limite configurável em `export.pattern_scan_limit` (padrão 256 blocos) vale para a **exportação** no criativo, não para a definição.

## 5. Matchers

`pattern.matchers` é um objeto cujo **nome da chave é um único caractere** e cujo valor pode ser:

1. uma string com ID de bloco;
2. uma string com tag de bloco, começando com `#`;
3. um objeto com `block` ou `tag` e, opcionalmente, `state`.

### 5.1 String de bloco

```json
"matchers": {
  "I": "minecraft:iron_block"
}
```

O bloco precisa existir no registro.

### 5.2 String de tag

```json
"matchers": {
  "I": "#c:storage_blocks/iron"
}
```

O `#` é obrigatório nesse formato. A tag é resolvida no registro de blocos.

### 5.3 Objeto de matcher

| Campo | Obrigatório | Tipo | Regras |
|---|---:|---|---|
| `block` | um dos dois | string | ID de bloco. Não use junto com `tag`. |
| `tag` | um dos dois | string | ID de tag. Pode ter `#` ou não. Não use junto com `block`. |
| `state` | não | objeto | Mapa de propriedade para valor, ambos strings. Só pode ser usado com `block`. |

Exatamente um entre `block` e `tag` precisa existir.

Exemplo com bloco e state:

```json
"matchers": {
  "S": {
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

Exemplo com tag:

```json
"matchers": {
  "S": {
    "tag": "#minecraft:stairs"
  }
}
```

### 5.4 Como o state funciona

- Apenas as propriedades listadas em `state` são verificadas.
- Propriedades omitidas são ignoradas.
- Um objeto sem `state` aceita qualquer state do bloco.
- Um objeto com `state` aceita qualquer combinação que bata com as propriedades informadas.
- O nome da propriedade e o valor precisam existir no bloco.
- Valores são strings: `"true"`, `"false"`, `"north"`, `"1"`, etc.
- `state` com `tag` é inválido.
- O state é relativo à orientação canônica `SOUTH` e é rotacionado junto com a estrutura.

Exemplo de matcher parcial para escada:

```json
"matchers": {
  "S": {
    "block": "minecraft:oak_stairs",
    "state": {
      "facing": "south",
      "half": "bottom"
    }
  }
}
```

Esse matcher aceita a escada virada para sul e na metade de baixo, independentemente de `shape` ou `waterlogged`.

### 5.5 O que não existe

- NBT;
- listas de alternativas (`any`);
- matchers opcionais;
- expressões ou lógica.

Se precisar de alternativas, use uma tag de bloco.

## 6. Slots de item

### 6.1 Tamanho do inventário

- Se `item_slots` tem entradas, o inventário tem exatamente a quantidade de entradas.
- Se `item_slots` está vazio ou ausente, o inventário tem `inventory_rows * 9` slots.
- `inventory_rows` é validado mesmo quando ignorado, então use de `1` a `6`.

Exemplos:

| Configuração | Tamanho |
|---|---:|
| sem `item_slots`, `inventory_rows: 3` | 27 |
| sem `item_slots`, `inventory_rows: 6` | 54 |
| 4 entradas em `item_slots` | 4 |

### 6.2 Campos de `item_slots[]`

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `id` | sim | string | — | Único e não vazio. |
| `role` | sim | string | — | `input`, `output`, `fuel`, `upgrade`, `component`, `storage` ou `fluid`. |
| `filter` | não | string | sem filtro | ID de item ou tag com `#`. |
| `limit` | não | inteiro | `64` | De `1` a `64`. O limite efetivo também considera o tamanho máximo do stack. |
| `faces` | não | objeto | padrão do role | `insert` e/ou `extract`, cada um com uma lista de direções. |
| `x` | não | inteiro | ausente | Posição horizontal na GUI customizada. Precisa de `y`. Não pode ser negativo. |
| `y` | não | inteiro | ausente | Posição vertical na GUI customizada. Precisa de `x`. Não pode ser negativo. |

`faces`:

```json
"faces": {
  "insert": ["north", "south"],
  "extract": []
}
```

- `insert`: faces que podem inserir no slot.
- `extract`: faces que podem extrair do slot.
- Se um dos campos for omitido, vale o padrão do role.
- Lista vazia desativa a operação.
- Direções: `north`, `south`, `east`, `west`, `up`, `down`.

### 6.3 Faces padrão por role

| Role | Insere por | Extrai por | Uso atual |
|---|---|---|---|
| `input` | todas | nenhuma | Participa do matching de receita. |
| `output` | nenhuma | todas | Recebe o resultado da receita. |
| `fuel` | todas | nenhuma | Sem comportamento de receita embutido. |
| `upgrade` | todas | nenhuma | Sem comportamento de receita embutido. |
| `component` | todas | nenhuma | Sem comportamento de receita embutido. |
| `storage` | todas | todas | Armazenamento/automação. |
| `fluid` | nenhuma | nenhuma | Slot manual: drena o container inserido para o tanque selecionado e enche o container retirado a partir dele. |

Apenas `input` e `output` têm comportamento automático no processamento de receitas. Os outros roles são úteis para automação, filtros, limites e GUIs; outros mods podem acessá-los pelo inventário.

O role `fluid` tem comportamento manual: ao inserir um container com fluido ele é drenado para o tanque selecionado; ao retirar um container vazio, ele é preenchido a partir do tanque. O pino azul no slot, exibido quando há mais de um tanque, alterna o tanque usado. Sem faces de automação, o slot não participa de inserção/extração por hopper.

### 6.4 Filtro e limite

- `filter` aceita ID de item (`minecraft:iron_ingot`) ou tag (`#c:ingots/iron`).
- O filtro vale para o jogador e para automação.
- O limite vale para o slot e também para o resultado.
- O jogador pode retirar de qualquer slot visível, mesmo que a face não permita extração por automação.

### 6.5 Faces e rotação

As faces de `item_slots` são direções **do mundo**. Elas não são rotacionadas junto com o `FACING` do controller.

Exemplo: `"insert": ["north"]` continua significando norte do mundo, mesmo que a máquina esteja virada para leste. Para evitar confusão, use faces amplas ou documente a orientação da sua máquina.

### 6.6 Slots e GUI

Há dois jeitos de posicionar slots na GUI customizada:

**Formato atual:** `item_slots[]` com `x`/`y`.

```json
"item_slots": [
  {
    "id": "input_0",
    "role": "input",
    "x": 44,
    "y": 17
  },
  {
    "id": "output_0",
    "role": "output",
    "x": 116,
    "y": 26
  }
]
```

- Slots com `x`/`y` aparecem na GUI.
- Slots sem `x`/`y` ficam ocultos, mas continuam existindo para automação e receitas.
- Todos os slots visíveis precisam caber em `gui.width`/`gui.height`.

**Formato antigo:** elementos `slot` dentro de `gui.elements`.

```json
{
  "type": "slot",
  "slot": "input_0",
  "x": 44,
  "y": 17
}
```

- O `slot` precisa existir em `item_slots[]`.
- Cada slot pode aparecer uma vez.
- Não misture elementos `slot` com `x`/`y` em `item_slots[]`.
- O tamanho do slot é fixo em `18x18`; `width`/`height` do elemento são ignorados.

No layout padrão (sem `gui.elements`), slots com `x`/`y` que caibam em `176x166` são respeitados; os demais são distribuídos automaticamente. A grade usa 9 colunas, ou 8 quando há barras de energia/fluido ou barra de progresso.

### 6.7 Exemplo de `item_slots`

```json
"item_slots": [
  {
    "id": "input_0",
    "role": "input",
    "filter": "minecraft:raw_iron",
    "limit": 16
  },
  {
    "id": "input_1",
    "role": "input",
    "filter": "minecraft:coal"
  },
  {
    "id": "output_0",
    "role": "output",
    "filter": "minecraft:iron_ingot"
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

## 7. Energia e fluidos

`capabilities` é opcional e fica no nível raiz. `item_slots` **não** fica dentro de `capabilities`.

### 7.1 Energia

```json
"capabilities": {
  "energy": {
    "id": "energy",
    "capacity": 100000,
    "max_receive": 1000,
    "max_extract": 0
  }
}
```

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `id` | sim | string | — | Não vazio. Usado pela GUI e para identificar a capability. |
| `capacity` | sim | inteiro | — | Mínimo `1`, em FE. |
| `max_receive` | não | inteiro | `0` | FE aceitos por operação de inserção. `0` desativa a inserção. |
| `max_extract` | não | inteiro | `0` | FE liberados por operação de extração. `0` desativa a extração. |

### 7.2 Fluidos

```json
"capabilities": {
  "fluids": [
    {
      "id": "water_tank",
      "capacity": 10000,
      "max_receive": 1000,
      "max_extract": 0,
      "filter": "minecraft:water",
      "port": "F"
    }
  ]
}
```

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `id` | sim | string | — | Único entre os tanques e diferente do `energy.id`. |
| `capacity` | sim | inteiro | — | Mínimo `1`, em mB. |
| `max_receive` | não | inteiro | `0` | mB aceitos por inserção. `0` desativa. |
| `max_extract` | não | inteiro | `0` | mB liberados por extração. `0` desativa. |
| `filter` | não | string | sem filtro | ID de fluido ou tag com `#`. |
| `port` | não | string | qualquer porta | Key do pattern, ID de bloco ou tag que dá acesso ao tanque. |

O `port` é opcional. Quando definido, o tanque só é exposto nos blocos de fluido que casarem com esse seletor; a key precisa existir no `pattern` e, quando `capability_ports.fluid` existe, também precisa estar selecionada lá. Tanques sem `port` continuam acessíveis por qualquer porta de fluido. Isso permite, por exemplo, um tanque de água acessível só pela key `F` e um de lava só pela key `L`.

As portas respeitam `max_receive`/`max_extract` e `filter` do tanque. O slot `fluid` usa os tanques como origem/destino manual: ele ignora `max_receive` e `max_extract` (limites de automação), mas respeita `capacity` e `filter`. A troca é all-or-nothing por stack: N baldes exigem N*1000 mB livres no tanque.

### 7.3 Portas de capability (`capability_ports`)

`capability_ports` é opcional e fica no nível raiz, irmão de `capabilities`.

```json
"capability_ports": {
  "item": ["I"],
  "energy": ["C"],
  "fluid": ["F", "#minecraft:glass"]
}
```

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---|---:|---|---|
| `item` | não | lista | todos | Blocos que expõem a capability de item. |
| `energy` | não | lista | todos | Blocos que expõem a capability de energia. |
| `fluid` | não | lista | todos | Blocos que expõem as capabilities de fluido. |

Cada item da lista aceita:

- um símbolo de 1 caractere do `pattern` (`C`, `I`, `F`...);
- um ID de bloco (`namespace:path`);
- uma tag de bloco (`#namespace:path`).

Regras:

- As três chaves são independentes. Omitir uma chave expõe aquela capability em **todos** os blocos formados; lista vazia (`[]`) não expõe em nenhum.
- O controller só expõe uma capability se ele próprio for selecionado (pelo símbolo `C` ou pelo ID do bloco controller).
- Um símbolo que não existe no `pattern` falha o carregamento da definição.
- A whitelist controla apenas as capabilities do multiblock; capabilities nativas de um bloco membro continuam disponíveis.
- Não há configuração por face: um bloco selecionado expõe a capability em todas as suas faces.
- `item` não é uma chave de `capabilities`: todo multiblock tem inventário (`item_slots` ou `inventory_rows`) e expõe item por padrão. Use `capability_ports.item` para limitar as portas ou `[]` para não expor item.
- `energy` e `fluid` só têm efeito quando a máquina define `capabilities.energy` ou pelo menos um tanque em `capabilities.fluids`. Um tanque somente de fluido é possível com `capabilities.fluids` e, se quiser esconder o inventário, `"capability_ports": {"item": []}`. A GUI customizada pode ter só `fluid_bar` e `player_inventory`, sem `item_slots`.

As portas são recalculadas na próxima validação da estrutura (a cada 20 ticks ou quando um vizinho muda). `/reload` atualiza a definição e agenda essa revalidação; não é preciso quebrar e montar de novo.

No builder, os campos de `capability_ports` ficam na etapa **Capabilities**. Campo vazio omite a chave e libera a capability em qualquer bloco; `[]` não expõe nenhum bloco; símbolos, IDs e tags podem ser separados por vírgula ou espaço. As portas `energy`/`fluid` ficam desabilitadas enquanto a capability correspondente não existir.

### 7.4 Onde as capabilities aparecem

- **Padrão:** todos os blocos membros formados e o controller expõem as capabilities configuradas.
- **Com `capability_ports`:** apenas os blocos selecionados para cada capability.
- **Itens:** um hopper encostado em qualquer porta de item acessa o mesmo inventário.
- **Energia e fluidos:** normalmente expostos só no controller, incluindo `C` na lista correspondente.
- **Tanques com `port`:** só aparecem nas portas de fluido que casarem com a key/bloco/tag configurada; as demais portas veem apenas os tanques sem `port` (e os que casarem com elas).
- O `energy.id` e os `fluids[].id` são usados pela GUI para localizar as barras. Eles não mudam as portas.

### 7.5 Energia em receitas

Um input de energia usa `{ "type": "energy", "amount": 1000 }`. A maquina precisa ter `capabilities.energy`; sem armazenamento, a receita nem inicia.

O custo e distribuido ao longo dos ticks. Uma receita com `amount: 1000` e `time: 100` consome aproximadamente 10 FE por tick. Se a energia acabar, o progresso pausa. Um output de energia e inserido no storage ao completar.

### 7.6 Fluidos em receitas

Se a receita tem `inputs` do tipo `fluid`, a maquina precisa ter tanques suficientes. Cada ingrediente de fluido procura um tanque que aceite o fluido e tenha a quantidade necessaria. Cada tanque satisfaz no maximo um ingrediente por craft.

### 7.7 Reload

`/reload` atualiza a definição, mas **não recria nem reconfigura** capabilities de energia/fluidos já existentes. Se você mudar `capacity`, `max_receive`, `max_extract` ou `filter`, quebre e coloque o controller de novo, ou reinicie o mundo, para criar as capabilities com os novos valores.

## 8. GUI

### 8.1 Layout padrão

Sem `gui`, ou com `gui.elements` vazio, o layout é automático:

- slots do inventário em grade automática (9 colunas, ou 8 quando há barras/progresso); posições `x`/`y` que caibam em `176x166` são respeitadas;
- título no topo;
- uma barra de energia, se existir;
- uma barra para cada tanque de fluido, se existirem;
- uma barra de progresso à direita, se `type=recipe`;
- inventário do jogador abaixo dos slots;
- largura fixa em `176` e altura calculada para caber tudo (mínimo `166`).

Se você definir `gui` sem `elements`, ainda pode definir `texture`, `width`, `height`, `texture_width` e `texture_height`; o layout continua automático, mas usa esses valores.

### 8.2 Layout customizado

Com `gui.elements` não vazio:

- `item_slots` só é obrigatório quando houver elementos `slot` ou posições `x`/`y`; uma GUI somente de fluidos pode omitir slots de item;
- os slots visíveis são os que têm `x`/`y` em `item_slots[]`, ou os elementos `slot` no formato antigo;
- slots sem posição ficam ocultos, mas funcionam para automação/receita;
- não misture `x`/`y` em `item_slots[]` com elementos `slot`;
- exatamente um `player_inventory` é obrigatório;
- `energy_bar.capability` precisa ser igual a `energy.id`;
- `fluid_bar.capability` precisa ser igual a um `fluids[].id`;
- `progress_bar`, `arrow` e `entity` só são aceitos em `type=recipe`;
- slots, player inventory, barras e `entity` precisam caber em `width`/`height`;
- `entity.width`/`entity.height` precisam ser múltiplos de `18` e no mínimo `18`;
- `title` e `text` não têm validação de limites; cuidado para não desenhar fora da tela.
- Em `title`/`text`, `x/y` representam o canto superior esquerdo da linha de texto, não a baseline; a tela compensa 1 px para alinhar o desenho ao referencial do builder.
- No builder, novos elementos começam com o layout do `spawner`: `title` em `8,4`, `text` em `8,75`, `player_inventory` em `8,84`, `energy_bar`/`fluid_bar`/`progress_bar` em `158/148/138,10` com `8x64`, e `entity` em `96,18` com `36x36`.
- Slots novos começam em `8,16` e avançam `18` px por coluna, com 9 colunas por linha (ou 8 quando há barras de energia/fluido ou barra de progresso) antes de descer para a próxima linha.

### 8.3 Campos de `gui`

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `texture` | não | identifier | sem textura | Caminho completo do PNG, por exemplo `minhamod:textures/gui/minha_maquina.png`. Sem textura, a tela desenha um painel cinza. |
| `width` | não | inteiro | `176` | De `1` a `4096`. Área visível da GUI. |
| `height` | não | inteiro | `166` | De `1` a `4096`. Área visível da GUI. |
| `texture_width` | não | inteiro | `256` | De `1` a `4096`. Largura da textura. |
| `texture_height` | não | inteiro | `256` | De `1` a `4096`. Altura da textura. |
| `elements` | não | lista | `[]` | Se vazio, usa o layout padrão. |

### 8.4 Campos de `gui.elements[]`

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `type` | sim | string | — | Veja a tabela de tipos abaixo. |
| `x` | não | inteiro | `0` | Posição horizontal. |
| `y` | não | inteiro | `0` | Posição vertical. |
| `width` | não | inteiro | `16` | De `0` a `4096`. Barras precisam de valor positivo; `entity` precisa de múltiplo de `18`, mínimo `18`. |
| `height` | não | inteiro | `16` | De `0` a `4096`. Barras precisam de valor positivo; `entity` precisa de múltiplo de `18`, mínimo `18`. |
| `text` | não | string | `""` | Chave de tradução ou texto literal. |
| `slot` | não | string | `""` | Só para o tipo `slot`; ID de `item_slots[]`. |
| `capability` | não | string | `""` | ID de energia ou tanque para barras. |
| `color` | não | inteiro ou string | depende do tipo | ARGB em inteiro decimal ou hexadecimal. Padrões: `0xFF404040` (texto), `0xFFFF0000` (`energy_bar`) e `0xFF00AA00` (`progress_bar`). Alpha `0` vira `0xFF`. |
| `shadow` | não | booleano | `true` | Sombra do texto. |
| `direction` | não | string | `up` | `up`, `down`, `left` ou `right`. |
| `u` | não | inteiro | `-1` | Origem horizontal do sprite. Se `-1`, usa `x`. |
| `v` | não | inteiro | `-1` | Origem vertical do sprite. Se `-1`, usa `y`. |

O campo `color` é ARGB e aceita inteiro decimal ou string hexadecimal. Exemplos equivalentes: `4285257290`, `"0xFF6BD64A"`, `"#6BD64A"` ou `"#FF6BD64A"`.

### 8.5 Tipos de elemento

| Tipo | O que faz | Campos importantes |
|---|---|---|
| `title` | Desenha o título. Se `text` estiver vazio, usa o nome do bloco. | `x`, `y`, `text`, `color`, `shadow` |
| `text` | Desenha um texto. Se `text` estiver vazio, não desenha. | `x`, `y`, `text`, `color`, `shadow` |
| `player_inventory` | Desenha o rótulo do inventário do jogador e posiciona os slots do jogador. Exatamente um. | `x`, `y`, `text`, `color`, `shadow` |
| `slot` | Formato antigo para posicionar um slot. Tamanho fixo `18x18`. | `slot`, `x`, `y` |
| `energy_bar` | Barra de energia. | `capability`, `x`, `y`, `width`, `height`, `direction`, `color` |
| `fluid_bar` | Barra de fluido. | `capability`, `x`, `y`, `width`, `height` |
| `progress_bar` | Barra de progresso da receita. | `x`, `y`, `width`, `height`, `direction`, `color`, `u`, `v` |
| `arrow` | Seta de progresso, como a da fornalha. | `x`, `y`, `width`, `height`, `direction`, `u`, `v` |
| `entity` | Área que renderiza as entidades de `inputs` e `outputs` da receita, com quantidade no tooltip do JEI, e no controller os mobs vivos da área, com paginação automática. | `x`, `y`, `width`, `height` |

### 8.6 Detalhes por tipo

**`title` e `text`**

- `text` é uma chave de tradução ou um texto literal.
- `title` vazio usa o nome do bloco.
- Se não houver nenhum `title`, a tela desenha o título padrão em `8,6`.
- Não há validação de limites para `title`/`text`.

**`player_inventory`**

- Ocupa `162x76` (3 linhas + hotbar).
- O rótulo é desenhado 11 pixels acima de `y`.
- `text` vazio usa `container.inventory`.
- Precisa caber em `width`/`height`.

**`slot`**

- `slot` precisa ser o ID de um `item_slots[]`.
- O slot é desenhado em `x,y` com `18x18`.
- `width`/`height`, `text` e `capability` são ignorados.
- Cada slot pode aparecer uma vez.

**`energy_bar`**

- `capability` precisa ser igual ao `energy.id`.
- `width`/`height` precisam ser positivos.
- `direction` define o sentido do preenchimento: `up`, `down`, `left` ou `right`.
- `color` define a cor do preenchimento.
- Tooltip mostra energia/capacidade em FE.
- Sem textura, desenha um fundo escuro. Com textura, a textura precisa incluir o fundo.

**`fluid_bar`**

- `capability` precisa ser igual a um `fluids[].id`.
- `width`/`height` precisam ser positivos.
- Sempre preenche de baixo para cima; `direction` e `color` são ignorados.
- Tooltip mostra o fluido e a quantidade.
- Sem textura, desenha um fundo escuro.

**`progress_bar`**

- Só em `type=recipe`.
- `width`/`height` precisam ser positivos.
- `direction` define o sentido do preenchimento.
- `color` define a cor do preenchimento.
- Com textura, desenha o sprite em `u`/`v` como fundo e depois o preenchimento.
- Sem textura, desenha o fundo escuro.
- Tooltip mostra a porcentagem do craft atual.

**`arrow`**

- Só em `type=recipe`.
- `width`/`height` precisam ser positivos.
- `direction` define o sentido do recorte.
- Usa `u`/`v` como origem do sprite.
- **Precisa de textura**; sem textura, a seta não é desenhada.
- Não desenha fundo.
- Tooltip mostra a porcentagem do craft atual.

**`entity`**

- Só em `type=recipe`.
- `width`/`height` precisam ser múltiplos de `18`, no mínimo `18`, e caber na GUI.
- A área é dividida em células de `18x18`: com uma entidade, ela ocupa a área inteira; com duas ou mais, cada célula mostra uma entidade em ordem de linha.
- A capacidade é `(width / 18) * (height / 18)` entidades por página; quando não couberem, surgem páginas com indicador `atual/total`.
- No controller, a roda do mouse sobre o elemento troca de página. No JEI, as páginas alternam automaticamente a cada 2 segundos.
- No JEI, as entidades vêm dos `inputs` e `outputs` do tipo `entity` da receita exibida, incluindo a quantidade pedida/gerada (`count`); o elemento não tem campo de ID.
- No controller, entidades vivas dentro da área do multibloco são exibidas mesmo sem receita ativa; entidades de input/output da receita ativa completam a lista.
- O fundo é preto e a borda é cinza, no mesmo estilo das barras.
- O tooltip do JEI identifica entrada/saída, a quantidade e a chance da entidade na página exibida; no controller, mostra o nome e a contagem da entidade.
- Renderiza no JEI e no controller.
- Entidades vivas usam o renderer de inventário; entidades não-vivas usam o render state genérico. A escala é ajustada ao tamanho da entidade para caber na célula.

### 8.7 Exemplo de GUI customizada

O arquivo [`exemplos/exemplo_storage.json`](exemplos/exemplo_storage.json) usa `item_slots[]` com `x`/`y`, barras e `player_inventory`.

Trecho:

```json
"gui": {
  "width": 176,
  "height": 166,
  "texture_width": 256,
  "texture_height": 256,
  "elements": [
    {
      "type": "title",
      "text": "gui.minhamod.minha_maquina.title",
      "x": 8,
      "y": 6
    },
    {
      "type": "energy_bar",
      "capability": "energy",
      "x": 8,
      "y": 17,
      "width": 12,
      "height": 52,
      "direction": "up"
    },
    {
      "type": "fluid_bar",
      "capability": "water_tank",
      "x": 26,
      "y": 17,
      "width": 12,
      "height": 52
    },
    {
      "type": "player_inventory",
      "x": 8,
      "y": 84,
      "text": "container.inventory"
    }
  ]
}
```

### 8.8 Exemplo com `slot` antigo e progresso

```json
"gui": {
  "width": 176,
  "height": 166,
  "elements": [
    { "type": "title", "x": 8, "y": 6 },
    { "type": "energy_bar", "capability": "energy", "x": 8, "y": 17, "width": 12, "height": 52 },
    { "type": "slot", "slot": "input_0", "x": 44, "y": 17 },
    { "type": "slot", "slot": "input_1", "x": 44, "y": 35 },
    { "type": "slot", "slot": "output_0", "x": 116, "y": 26 },
    { "type": "progress_bar", "x": 80, "y": 35, "width": 12, "height": 20, "direction": "right" },
    { "type": "player_inventory", "x": 8, "y": 84 }
  ]
}
```

Nesse formato, os `item_slots[]` não podem ter `x`/`y`.

## 9. Controller único e receita

### 9.1 O controller compartilhado

Todas as definições usam o mesmo bloco, block entity e item:

```text
infinity_nexus_multiblock:multiblock_controller
```

O vínculo com a definição é o componente:

```text
infinity_nexus_multiblock:multiblock_definition = <namespace>:<path>
```

A aba criativa gera uma variante do controller para cada definição carregada. Colocar um controller sem esse componente é bloqueado. Ao quebrar, clonar ou dropar o bloco, o componente é preservado.

Para obter uma variante:

```text
/inmb give minhamod:minha_maquina
```

`/inmb list` lista as definições e cria links clicáveis para o comando `give`.

### 9.2 Texturas por definição

O campo top-level `textures` é opcional e sobrescreve as texturas do modelo compartilhado para uma definição. O bloco e o item continuam os mesmos; o cliente re-bakeia o modelo do controller quando a definição ou o estado `processing` muda.

```json
{
  "textures": {
    "front": "minhamod:block/fornalha_front",
    "side": "minhamod:block/fornalha_side",
    "top": "minhamod:block/fornalha_top",
    "bottom": "minhamod:block/fornalha_top",
    "particle": "minhamod:block/fornalha_front",
    "active": {
      "front": "minhamod:block/fornalha_front_on",
      "side": "minhamod:block/fornalha_side_on"
    }
  }
}
```

Regras:

- Faces aceitas: `front`, `side`, `top`, `bottom` e `particle`.
- Os valores são identifiers completos, como `minecraft:block/stone`, e a textura precisa existir em `assets/<namespace>/textures/block/...` para entrar no atlas de blocos.
- Faces omitidas usam o modelo/textura padrão do controller.
- `active` é opcional e é aplicado enquanto o controller está processando. Faces omitidas em `active` herdam a textura idle correspondente.
- `particle` vazio usa `front`; sem `front`, usa a partícula padrão do modelo.
- Como os demais objetos, `textures` é mesclado recursivamente por `extends`, `family` e `overrides`; o objeto `active` é mesclado face a face.
- Se uma textura não estiver no atlas de blocos do cliente, o controller volta ao modelo padrão e registra um aviso no log; o JSON continua carregando no servidor.
- A troca idle/active não exige quebrar o bloco; ela acompanha o estado `processing` do controller.

### 9.3 Como o vínculo é aplicado

1. O item possui o componente com o `id` da definição.
2. Ao colocar o bloco, o componente é copiado para o block entity.
3. O block entity carrega a definição pelo `id` e configura inventário, capabilities, GUI e receitas.
4. Ao clicar com o bloco ainda não formado, a estrutura é validada.
5. Se o pattern bater, o controller passa a `formed=true` e processa receitas.

O mesmo controller pode representar qualquer máquina; o bloco é compartilhado e as texturas podem ser especializadas por definição via `textures`.

### 9.4 Receita shaped opcional do controller

O campo top-level `recipe` descreve a receita de crafting table que produz o controller já com o componente daquela definição:

```json
{
  "recipe": {
    "pattern": [
      "ICI",
      "CGC",
      "ICI"
    ],
    "key": {
      "I": "minecraft:iron_ingot",
      "C": "minecraft:copper_ingot",
      "G": "minecraft:glass"
    }
  }
}
```

Regras:

- `pattern` é uma lista de 1 a 3 strings de crafting shaped.
- `key` mapeia cada símbolo a um ingredient.
- A receita gerada produz `infinity_nexus_multiblock:multiblock_controller` com `infinity_nexus_multiblock:multiblock_definition` igual ao `id` do arquivo.
- A receita é gerada automaticamente pelo pack embutido `infinity_nexus_multiblock:controller_recipes`.
- Definições são lidas do jar do mod e de `kubejs/data`; datapacks comuns carregam a máquina, mas não geram a receita de crafting do controller.
- Templates e definições sem `pattern` não geram receita.
- Uma definição precisa estar em `schema_version: 2` para gerar receita.

O arquivo gerado aparece como:

```text
data/<namespace>/recipe/controller/<path>.json
```

Exemplo de resultado:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": ["ICI", "CGC", "ICI"],
  "key": {
    "I": "minecraft:iron_ingot",
    "C": "minecraft:copper_ingot",
    "G": "minecraft:glass"
  },
  "result": {
    "id": "infinity_nexus_multiblock:multiblock_controller",
    "count": 1,
    "components": {
      "infinity_nexus_multiblock:multiblock_definition": "minhamod:minha_maquina"
    }
  }
}
```

### 9.5 API Java

Para integrações em Java, use:

- `ModBlocks.controllerWithDefinition(Identifier)` para criar o item com o componente.
- `MultiblockControllerBlockEntity#definitionId()` para ler o ID configurado.
- `MultiblockControllerBlockEntity#definition()` para obter a definição carregada.
- `MultiblockDefinitions#get(Identifier)` e `MultiblockDefinitions#all()` para consultar definições.

Não é necessário registrar um `MultiblockControllerItem`, bloco ou block entity por máquina.

## 10. Receitas

### 10.1 Arquivo

```text
data/<namespace>/recipe/<arquivo>.json
```

```json
{
  "type": "infinity_nexus_multiblock:multiblock",
  "definition": "infinity_nexus_multiblock:exemplo_recipe"
}
```

O tipo `infinity_nexus_multiblock:multiblock` e unico e registrado pelo mod. A receita so e usada pela maquina cuja definicao tem o mesmo `id` indicado em `definition`. O campo antigo `recipe_type` nao existe mais na definicao.

### 10.2 Campos da receita

| Campo | Obrigatorio | Tipo | Padrao | Regras |
|---|---:|---|---|---|
| `type` | sim | string | - | Sempre `infinity_nexus_multiblock:multiblock`. |
| `definition` | sim | identifier | - | `id` da definicao que processa esta receita. |
| `inputs` | sim | lista | `[]` | Lista tipada de itens, fluidos, entidades e energia. |
| `outputs` | sim | lista | `[]` | Resultados; cada entrada tem `chance` opcional. |
| `time` | sim | inteiro | `100` | Duracao em ticks, de 1 a 72000. |

O codec ainda aceita os campos antigos `ingredients`, `fluid_ingredients`, `output`, `energy` e `result` para migracao, mas o builder exporta somente `inputs` e `outputs`. Receitas novas devem usar o schema tipado.

### 10.3 `inputs`

```json
"inputs": [
  { "type": "item", "ingredient": "minecraft:raw_iron", "count": 2 },
  { "type": "item", "ingredient": "#c:raw_materials/copper" },
  { "type": "fluid", "ingredient": "minecraft:water", "amount": 1000 },
  { "type": "entity", "id": "minecraft:pig", "count": 2, "spawn": "C" },
  { "type": "energy", "amount": 500 }
]
```

- `item`: `ingredient` aceita id, tag `#`, JSON de ingrediente ou objeto com `components`; `count` e opcional e vai de 1 a 99.
- `fluid`: `ingredient` aceita id ou tag; `amount` e obrigatorio e limitado pela maior capacidade de tanque.
- `entity`: `id` (entity type) ou `tag` (tag de entity type, com `#`), exatamente um; `count` e opcional (1 a 99, padrao 1) e indica quantas entidades desse tipo precisam existir na area; `spawn` e opcional e aceita simbolo do pattern, bloco ou tag. As entidades precisam estar dentro da area do multibloco ou perto do seletor e sao removidas ao completar.
- `energy`: `amount` e o custo total em FE, consumido aos poucos durante o progresso.
- Pelo menos um input e obrigatorio; a maquina tambem procura receitas que tenham somente energia ou entidade, sem item/fluido.

### 10.4 `outputs`

```json
"outputs": [
  {
    "type": "item",
    "id": "minecraft:gold_ingot",
    "count": 2,
    "components": { "minecraft:custom_name": "Ouro" },
    "chance": 0.5
  },
  { "type": "fluid", "id": "minecraft:water", "amount": 500, "chance": 1.0 },
  { "type": "entity", "id": "minecraft:pig", "count": 2, "spawn": "C", "chance": 0.25 },
  { "type": "energy", "amount": 1000, "chance": 1.0 }
]
```

- `chance` e um numero de 0 a 1; default 1.0. Cada output rola separadamente ao completar.
- Chance 0 nunca gera o resultado, mas os inputs ainda sao consumidos.
- Itens usam `count` de 1 a 99 e podem ter `components`; fluidos usam `amount`; entidades usam `id` ou `tag` (exatamente um), `count` de 1 a 99 (padrão 1) e `spawn` opcional (sem seletor, as entidades de saída surgem dentro da área do multibloco); energia vai para o storage do controller.
- O runtime valida todos os outputs antes de iniciar o craft, mesmo os que tem chance menor que 1.

### 10.5 Matching e validacao

- Itens de input sao distribuidos por slots `input` com busca em profundidade; um slot so pode satisfazer uma entrada por craft.
- Fluidos seguem a mesma regra nos tanques, respeitando capacidade e filtro.
- Entidades de input sao procuradas no AABB do multibloco inflado em 1 bloco; com `spawn` baseado em simbolo/bloco/tag, a busca fica perto da key correspondente.
- Entidades de output usam o seletor `spawn` quando informado; sem seletor, o controller procura um piso com dois blocos livres dentro da area do multibloco.
- Energia de input e consumida aos poucos durante o progresso; sem energia no storage o craft pausa.
- Antes de completar, o controller simula o consumo dos inputs de fluido e depois encaixa todos os outputs em copias do inventario e dos tanques. Se nao houver espaco para todos, o progresso fica parado.
- Itens e fluidos gerados sao distribuidos entre slots/tanks disponiveis; tanques usados por inputs podem ser reaproveitados para outputs depois do consumo. Energia gerada fica no storage interno do controller e pode ser extraida pelas portas de energia.

### 10.6 Execucao e chance

Ao completar, os inputs sao consumidos e cada output rola sua `chance` de forma independente. Uma falha de chance nao devolve os inputs. Se o estado da maquina mudou e os outputs nao cabem mais, o craft nao completa e os inputs nao sao consumidos (a energia ja gasta nao volta); o planner revalida o espaco antes de cada avanco.

### 10.7 Energia e tempo

Um input `energy` com `amount: 1000` e `time: 100` consome cerca de 10 FE por tick. Se a energia acabar, o progresso pausa. Um output `energy` e adicionado ao storage ao completar e respeita a capacidade maxima.

### 10.8 Limites

- Numero de inputs de item nao pode passar do numero de slots `input`.
- Soma dos `count` de inputs nao pode passar da soma dos `limit` dos slots.
- Cada input de item precisa caber em um unico slot, e cada input de fluido precisa caber em um unico tanque.
- O mesmo vale para outputs e para a quantidade/capacidade dos tanques.
- `amount` de energia nao pode passar da capacidade de `capabilities.energy`.
- O builder bloqueia exportacao quando qualquer limite e violado; o runtime apenas nao inicia/completa o craft.

### 10.9 Exemplo completo

Definicao: [`exemplos/exemplo_recipe.json`](exemplos/exemplo_recipe.json)

Receita: [`exemplos/exemplo_recipe_iron.json`](exemplos/exemplo_recipe_iron.json)

A maquina tem dois slots de input, um output, energia e um tanque de agua. A receita consome raw iron, coal, 500 mB de agua e 1000 FE, e leva 100 ticks.

### 10.10 Gerar JSON com o comando

O mod registra `/infinity_nexus_multiblock` (alias `/inmb`) com os subcomandos `hand`, `look`, `list` e `give`. Os comandos exigem nivel de permissao de gamemaster ou modo criativo; as linhas de JSON enviadas no chat sao clicaveis e copiam o texto.

`/inmb hand` le o item na mao principal e envia uma entrada pronta para `inputs` e um objeto pronto para `outputs`. No builder, cole o JSON no campo id/tag: o parser separa `id`/`items`, `count`, `components` e `chance`.

Item sem data components:

```json
"minecraft:iron_ingot"
```

```json
{"type":"item","id":"minecraft:iron_ingot","count":1,"chance":1.0}
```

Item com data components:

```json
{
  "neoforge:ingredient_type": "neoforge:components",
  "items": "minecraft:diamond_sword",
  "components": { "minecraft:damage": 10 }
}
```

```json
{
  "type": "item",
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": { "minecraft:damage": 10 },
  "chance": 1.0
}
```

`/inmb look` faz um raycast de ate 20 blocos e envia o ID puro, o matcher completo e o objeto `state` separado.

### 10.11 Sons (`sounds`)

O objeto `sounds` e opcional e so tem efeito em maquinas `type=recipe`. Cada valor e um identifier `namespace:path` que aponta para uma entrada em `assets/<namespace>/sounds.json`, por exemplo `minhamod:block.minha_maquina.processing`.

```json
"sounds": {
  "processing": "minhamod:block.minha_maquina.processing",
  "finish": "minhamod:block.minha_maquina.finish"
}
```

| Campo | Obrigatorio | Tipo | Regras |
|---|---|---|---|
| `processing` | nao | identifier | Tocado no controller a cada 20 ticks enquanto a maquina avanca uma receita. |
| `finish` | nao | identifier | Tocado uma vez quando a receita completa com sucesso. |

O som e tocado no bloco do controller, no canal `blocks`, com volume e pitch `1.0`. Nao e preciso registrar o `SoundEvent` em Java: basta declarar o evento no `sounds.json` do resource pack do seu mod. Se apenas um dos campos for declarado, o outro tipo simplesmente nao toca.

No builder, a etapa 08 oferece um seletor com os 1902 sound events vanilla do Minecraft 26.1.2, gerados em `docks/builder/data/minecraft_sounds.json`, e tambem aceita qualquer identifier custom digitado manualmente.

## 11. Herança e templates

### 11.1 Pai e filho

```json
{
  "schema_version": 2,
  "id": "minhamod:fornalha_base",
  "template": true,
  "type": "recipe",
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

```json
{
  "id": "minhamod:fornalha_avancada",
  "extends": "minhamod:fornalha_base",
  "tier": 2,
  "item_slots": [
    { "id": "input_0", "role": "input" },
    { "id": "output_0", "role": "output" }
  ],
  "pattern": {
    "anchor": "C",
    "layers": [
      [
        "III",
        "III",
        "III"
      ],
      [
        "III",
        "III",
        "ICI"
      ],
      [
        "III",
        "III",
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

O filho herda `type` e `capabilities.energy`, mas troca `capacity` e `max_receive` via `overrides`. As receitas da máquina usam `definition: "minhamod:fornalha_avancada"`.

### 11.2 Regras de merge

- Objetos são mesclados recursivamente.
- Listas substituem a lista inteira.
- Campos do filho são aplicados antes de `overrides`.
- Se o mesmo campo aparecer no filho e em `overrides`, `overrides` vence.
- `extends`, `family`, `overrides` e `template` não são herdados.
- O filho precisa do próprio `id`.
- A receita aponta para o `id` final do filho em `definition`.
- Ciclos, pai inexistente, `extends` + `family` juntos e `overrides` que não seja objeto geram erro.

### 11.3 Templates

- `template: true` nunca registra a máquina.
- Um arquivo sem `pattern` é tratado como template quando referenciado.
- Se um arquivo sem `pattern` não for referenciado, ele falha ao carregar.

## 12. Priority, tier e sobreposição

### 12.1 `priority`

Quando duas estruturas formadas disputam o mesmo bloco:

- a maior `priority` vence;
- a estrutura perdedora é desfeita;
- em empate, vence o controller com o menor `BlockPos.asLong()`;
- o controller perdedor não forma a estrutura.

Para evitar surpresas, use prioridades diferentes em máquinas que podem se sobrepor.

### 12.2 `tier`

`tier` é apenas metadado. Ele não altera velocidade, consumo, slots ou receitas. Use para exibir ou para outros mods que leiam a definição.

## 13. Exportar e importar

### 13.1 Exportar

No modo criativo:

1. Segure `shift`.
2. Clique com o botão direito em um controller.
3. O JSON da estrutura conectada vai para o clipboard.
4. A actionbar confirma e toca um som.

O scan:

- começa no controller;
- anda em seis direções;
- ignora ar;
- inclui o controller;
- para no limite configurado em `export.pattern_scan_limit` (padrão 256; máximo 4096);
- se houver mais, exporta até o limite e avisa na actionbar.

### 13.2 O que o JSON exportado contém

```json
{
  "schema_version": 2,
  "id": "infinity_nexus_multiblock:exported_0_64_0",
  "type": "storage",
  "pattern": {
    "layers": [],
    "anchor": "C",
    "matchers": {}
  }
}
```

Ele contém apenas:

- `schema_version`;
- `id` baseado nas coordenadas do controller;
- `type: "storage"`;
- `pattern` com `layers`, `anchor` e `matchers` de bloco + state.

Ele **não** contém `item_slots`, `capabilities`, `textures`, `gui`, receita ou prioridade. Use como ponto de partida para o pattern.

### 13.3 Normalização

A exportação normaliza posições e states para a orientação canônica `SOUTH`. Se você exportou uma máquina virada para norte, o JSON ainda descreve a máquina olhando para sul. Isso permite reutilizar o pattern com qualquer `FACING`.

### 13.4 Transformar em definição

1. Salve o JSON em `data/<namespace>/multiblock/<arquivo>.json`.
2. Troque o `id` para `<namespace>:<arquivo>`.
3. Troque `type` para `recipe` se a máquina processar receitas.
4. Adicione `item_slots`, `capabilities`, `textures` e `gui` conforme necessário.
5. Crie as receitas com `type: infinity_nexus_multiblock:multiblock` e `definition` igual ao `id`.
6. Rode `/reload`; o controller compartilhado já existe, então não é preciso reiniciar.

Não há um exemplo exportado no repositório; use o JSON do clipboard como ponto de partida.

### 13.5 Limitações da exportação

- limite configurável em `export.pattern_scan_limit` (padrão 256; máximo 4096);
- flood-fill pode incluir blocos vizinhos que não fazem parte da máquina;
- não exporta capabilities, slots, texturas, GUI, receita ou vínculo de controller;
- o `id` precisa ser ajustado;
- o `type` exportado é sempre `storage`.

## 14. Reload e ciclo de vida

Use `/reload` no servidor.

O que acontece:

- as definições são recarregadas e revalidadas;
- controllers formados usam a definição mais recente do mesmo `id`;
- se a definição não existir mais, a estrutura é invalidada;
- inventário e layout da GUI são reconfigurados quando a definição muda;
- stacks que não cabem no novo inventário são descartados;
- capabilities de energia/fluidos já existentes **não** são recriadas nem reconfiguradas;
- capabilities ausentes podem ser criadas quando a definição fica disponível;
- as definições resolvidas são enviadas aos clientes para a preview;
- receitas de controller são regeneradas a partir do jar do mod e de `kubejs/data`;
- o layout da GUI é persistido no block entity para sobreviver ao carregamento.

Se você mudou `capacity`, `max_receive`, `max_extract` ou `filter` de uma capability existente, quebre e coloque o controller de novo, ou reinicie o mundo.

Para adicionar ou remover uma definição, basta `/reload`; o bloco controller é compartilhado e já está registrado.

## 15. Exemplos completos

Os arquivos ficam em [`exemplos/`](exemplos/).

| Arquivo | O que mostra |
|---|---|
| [`exemplo_storage.json`](exemplos/exemplo_storage.json) | `type=storage`, item slots com filtros/limites/faces, energia, fluido e GUI customizada com slots via `x`/`y`. |
| [`exemplo_recipe.json`](exemplos/exemplo_recipe.json) | `type=recipe`, dois inputs, um output, energia, fluido e GUI padrão com barra de progresso. |
| [`exemplo_recipe_iron.json`](exemplos/exemplo_recipe_iron.json) | Receita com tipo `infinity_nexus_multiblock:multiblock`, `definition` apontando para o exemplo e ingredientes de item/fluido. |

### 15.1 Como testar

1. Copie os arquivos para um datapack:

```text
data/infinity_nexus_multiblock/multiblock/exemplo_storage.json
data/infinity_nexus_multiblock/multiblock/exemplo_recipe.json
data/infinity_nexus_multiblock/recipe/exemplo_recipe_iron.json
```

2. Os `id` já batem com os nomes dos arquivos.
3. Coloque o datapack em `world/datapacks`.
4. Rode `/reload`.
5. Obtenha o controller com `/inmb give infinity_nexus_multiblock:exemplo_recipe` ou `/inmb give infinity_nexus_multiblock:exemplo_storage`.

O controller é compartilhado. Os exemplos não precisam declarar `controller`, `recipe_type` nem texturas. A receita de crafting do controller só é gerada automaticamente quando a definição está no jar do mod ou em `kubejs/data`; em um datapack comum, use `/inmb give`.

### 15.2 Sobrescrever uma definição existente

Use o mesmo caminho e o mesmo `id` da definição original:

```text
data/infinity_nexus_multiblock/multiblock/<arquivo>.json
```

Depois rode `/reload`. O controller já existe, então não é preciso reiniciar o jogo. Se a definição estiver em um datapack e você quiser gerar a receita de crafting do controller, mova o JSON para `kubejs/data` ou para o jar do mod.

### 15.3 Receita shaped do controller

Para que uma definição gere uma receita de crafting do controller, adicione o campo top-level `recipe`:

```json
{
  "schema_version": 2,
  "id": "minhamod:minha_maquina",
  "type": "recipe",
  "recipe": {
    "pattern": ["ICI", "CGC", "ICI"],
    "key": {
      "I": "minecraft:iron_ingot",
      "C": "minecraft:copper_ingot",
      "G": "minecraft:glass"
    }
  },
  "pattern": {
    "anchor": "C",
    "layers": [["III", "III", "III"], ["III", "III", "ICI"], ["III", "III", "III"]],
    "matchers": {
      "I": "minecraft:iron_block"
    }
  }
}
```

A receita gerada produz o controller com o componente `infinity_nexus_multiblock:multiblock_definition` igual a `minhamod:minha_maquina`. Veja a seção 9.4 para o formato completo.

## 16. Erros comuns

| Mensagem / sintoma | Causa | Correção |
|---|---|---|
| `Definition id ... does not match file id` | `id` diferente do caminho. | Use `<namespace>:<arquivo>`. |
| `Unsupported schema_version` | Versão diferente de `2`. | Use `"schema_version": 2`. |
| `Unknown multiblock type` | `type` inválido. | Use `recipe` ou `storage`. |
| `Unknown parent multiblock` | `extends`/`family` aponta para ID inexistente. | Confira o ID e o caminho do pai. |
| `Circular extends involving` | Herança em ciclo. | Remova o ciclo. |
| `Use either extends or family, not both` | Os dois campos juntos. | Use só um. |
| `overrides must be an object` | `overrides` não é objeto. | Use `{ ... }`. |
| `template must be a boolean` | `template` não é booleano. | Use `true` ou `false`. |
| `Unknown block` | ID de bloco inexistente. | Confira o registro. |
| `Unknown block state property` | Propriedade não existe no bloco. | Confira o blockstate do bloco. |
| `Invalid value ... for block state property` | Valor inválido para a propriedade. | Use um valor válido. |
| `Block state properties are only supported for block matchers` | `state` junto com `tag`. | Remova `state` ou use `block`. |
| `No matcher registered for pattern symbol` | Símbolo sem matcher. | Adicione o matcher. |
| `needs exactly one anchor` | Nenhuma ou mais de uma âncora. | Deixe exatamente um `C`. |
| `same number of rows` | Camadas com quantidades diferentes de linhas. | Alinhe as camadas. |
| `same width` | Linhas com larguras diferentes. | Alinhe as larguras. |
| `Duplicate item slot id` | IDs de slot repetidos. | Use IDs únicos. |
| `Item slot id cannot be blank` | `id` vazio. | Use um ID não vazio. |
| `Item slot ... must define both x and y` | Só um dos dois. | Informe os dois ou nenhum. |
| `Slot position cannot be negative` | `x`/`y` negativo. | Use valores `>= 0`. |
| `Capability port symbol 'X' is not used by the pattern` | Símbolo de `capability_ports` não existe no pattern. | Use um símbolo presente ou ajuste o pattern. |
| `Unknown item` | ID de item no filtro inexistente. | Confira o registro. |
| `Duplicate fluid tank id` | Tanques com mesmo ID. | Use IDs únicos. |
| `Energy capability id conflicts with a fluid tank id` | `energy.id` igual a um tanque. | Troque um dos IDs. |
| `Unknown fluid` | ID de fluido no filtro inexistente. | Confira o registro. |
| `Custom gui elements with slot entries require item_slots` | `gui.elements` sem `item_slots`. | Adicione `item_slots`. |
| `Define slot positions in item_slots x/y or gui slot elements, not both` | Misturou os dois formatos. | Escolha um. |
| `Unknown item slot id in gui` | Elemento `slot` aponta para ID inexistente. | Confira o ID. |
| `Duplicate slot element for item slot` | Slot repetido em elementos. | Use uma vez. |
| `Custom gui elements require a player_inventory element` | Falta `player_inventory`. | Adicione exatamente um. |
| `gui has an energy_bar but the multiblock has no energy capability` | Barra sem energia. | Adicione `capabilities.energy` ou remova a barra. |
| `Unknown energy capability in gui` | `capability` diferente de `energy.id`. | Corrija o ID. |
| `Unknown fluid tank id in gui` | `capability` de tanque inexistente. | Corrija o ID. |
| `gui has a progress_bar/arrow but the multiblock is not a recipe multiblock` | Barra/seta em `storage`. | Use `type=recipe`. |
| `gui has an entity element but the multiblock is not a recipe multiblock` | `entity` em `storage`. | Use `type=recipe`. |
| `exceeds gui bounds` | Elemento fora da GUI. | Aumente `width`/`height` ou mova. |
| Receita não carrega | Caminho, `type` ou IDs errados. | Confira `data/<namespace>/recipe`, `type` e ingredientes. |
| Receita não aparece na máquina | `definition` diferente do `id` da definição. | Use exatamente o mesmo identifier. |
| Definição não carrega | Caminho/ID/schema errados. | Confira o log do servidor. |
| Controller não pode ser colocado | Item sem o componente `infinity_nexus_multiblock:multiblock_definition`. | Use `/inmb give <definition>` ou a aba criativa. |

O log fica em `logs/latest.log`.

Se o controller não puder ser colocado, confira se o item tem o componente de definição. Use `/inmb give <definition>` para obter a variante correta.

## 17. Checklist final

Antes de testar, confirme:

- [ ] O JSON está em `data/<namespace>/multiblock/<arquivo>.json`.
- [ ] `id` é `<namespace>:<arquivo>`.
- [ ] `schema_version` é `2`.
- [ ] `type` é `recipe` ou `storage`.
- [ ] O pattern tem exatamente uma âncora.
- [ ] Todas as camadas têm o mesmo número de linhas.
- [ ] Todas as linhas têm a mesma largura.
- [ ] Todo símbolo não-âncora tem matcher.
- [ ] A receita de máquina usa `type: infinity_nexus_multiblock:multiblock`.
- [ ] O campo `definition` da receita é igual ao `id` da definição.
- [ ] O controller é obtido por `/inmb give <definition>` ou pela aba criativa.
- [ ] `item_slots` tem IDs únicos e filtros válidos.
- [ ] `x`/`y` aparecem juntos.
- [ ] `capabilities` tem IDs únicos e `energy.id` diferente dos tanques.
- [ ] Se usar `capability_ports`, os símbolos existem no pattern e cada capability lista as portas desejadas.
- [ ] A GUI tem exatamente um `player_inventory`.
- [ ] Barras de energia/fluido apontam para IDs existentes.
- [ ] `progress_bar`/`arrow`/`entity` só existem em `recipe`.
- [ ] A receita está em `data/<namespace>/recipe/<arquivo>.json`.
- [ ] A receita tem pelo menos um ingrediente.
- [ ] A máquina tem slot `output` para saída de item, tanque para saída de fluido e `spawn` válido para entidade quando o seletor for informado.
- [ ] Se a receita usa energia/fluido, a máquina tem as capabilities.
- [ ] Você rodou `/reload` e conferiu `logs/latest.log`.

## 18. Limitações atuais

### 18.1 Controller único

- Existe um único bloco/item controller: `infinity_nexus_multiblock:multiblock_controller`.
- O vínculo com a máquina vem do componente `infinity_nexus_multiblock:multiblock_definition`; não há bloco ou item por definição, mas o modelo do controller pode ser especializado com `textures`.
- A aba criativa mostra uma variante do controller para cada definição carregada.
- `/reload` atualiza definições e receitas; não é necessário reiniciar para adicionar, remover ou sobrescrever uma definição.
- O antigo bloco `nexus_core` e os controllers dinâmicos foram removidos. Mundos antigos podem gerar avisos de registry; faça backup antes de atualizar.

### 18.2 Máquinas, receitas e automação

- Toda receita de máquina usa `type: infinity_nexus_multiblock:multiblock` e `definition: <id>`. Não há tipo independente por máquina.
- `type=storage` já funciona como inventário/capabilities/GUI, mas não tem comportamento especializado de armazenamento dedicado. `tier` é apenas metadado.
- Receitas produzem uma única saída `item`, `fluid` ou `entity`; não há energia como saída, subproduto, múltiplas saídas simultâneas ou chance.
- Não há prioridade entre receitas; a primeira receita compatível encontrada é usada.
- Energia e fluidos não têm configuração por lado; as capabilities do controller e dos membros selecionados aceitam todas as faces. `capability_ports` escolhe blocos, não faces.
- As faces de `item_slots` são direções do mundo e não giram com o controller/estrutura.
- Matchers aceitam bloco, tag e `state`; não aceitam NBT, listas de alternativas ou matchers opcionais.

### 18.3 Assets, JEI e reload

- O controller compartilhado tem blockstate, modelo base, loot e traduções próprios; texturas por definição vêm de `textures` e são aplicadas em runtime.
- O nome exibido usa `multiblock.<namespace>.<path>` e cai no path formatado quando não há tradução.
- A categoria JEI de receitas é filtrada pela definição do controller.
- A categoria JEI de estruturas ainda usa um catalisador genérico e pode listar todas as estruturas; o filtro por definição é uma melhoria pendente.
- A transferência JEI de itens existe, mas ainda não foi validada visualmente no cliente; fluidos continuam manuais.
- `/reload` recarrega definições, receitas de controller e o pack de dados; não recria capabilities já existentes.

### 18.4 Exportação

- A exportação é limitada por `export.pattern_scan_limit` na config comum do mod (`config/infinity_nexus_multiblock-common.toml`): padrão `256`, máximo `4096`.
- O JSON exportado contém apenas `schema_version`, `id`, `type` e `pattern` (blocos e states); não inclui `item_slots`, `capabilities`, `textures`, `gui`, receita ou prioridade, e o `type` exportado é sempre `storage`.

### 18.5 Metas ainda não atendidas

- Comportamento especializado de `type=storage` além do básico já implementado (inventário, capabilities e GUI): regras dedicadas de armazenamento de item, fluido e energia.
- Entidades como entrada de receitas dentro da área do multibloco; itens, fluidos e energia já são aceitos.
- Saída de energia em receitas; fluidos e entidades já são suportados.
- Filtro da categoria JEI de estruturas por definição e validação visual da transferência JEI.
- Adaptadores para receitas vanilla.
