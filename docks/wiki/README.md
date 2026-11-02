# Wiki do Desenvolvedor

Guia prático para criar máquinas multibloco com o **Infinity Nexus Multiblock**.

- Minecraft: `26.1.2`
- NeoForge: `26.1.2.121`
- Java: `25`
- Idioma dos exemplos: português
- Schema atual: `schema_version: 1`

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
9. [Controllers dinâmicos e Java](#9-controllers-dinâmicos-e-java)
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

1. **Um bloco controller.**
   Para JSONs empacotados no mod, o bloco, o block entity e o item são criados automaticamente a partir da definição. Para outros namespaces ou blocos com comportamento próprio, registre um `MultiblockControllerBlock` e um `MultiblockControllerBlockEntity` em Java como API de extensão.

2. **Uma definição JSON.**
   Ela diz qual é o pattern, quais blocos formam a estrutura, quais slots existem, qual GUI usar, quais capabilities existem e qual receita processar.

3. **Um pattern formado no mundo.**
   O jogador coloca o controller virado para a direção correta e clica nele para montar. Quando todas as células batem, o controller fica `formed=true`, abre a GUI e passa a processar receitas.

Fluxo resumido:

```text
Startup (mod jar): lê JSONs empacotados e registra blocos + block entities + itens + recipe types
JSON: pattern + slots + capabilities + GUI + recipe_type
Mundo: controller é colocado e clicado
Loader: valida o JSON e cria a definição
Estrutura: pattern é validado com rotação
Máquina: inventário, energia, fluidos, GUI e receita funcionam
```

O `controller` do JSON é opcional. Em um JSON empacotado no mod, `controller.block` define o ID do bloco a criar e `controller.textures` alimenta o modelo gerado; cada textura ausente usa o fallback da mesma face. Sem `controller`, o bloco usa o próprio `id` e todas as texturas fallback. O JSON só cria blocos quando está dentro do jar do mod e usa o namespace `infinity_nexus_multiblock`. Datapacks externos carregam definições, mas não registram blocos; nesse caso `controller.block` precisa apontar para um bloco Java já registrado.

No JEI, cada controller dinâmico gera uma categoria de **estrutura** e um catalisador. Controllers `type=recipe` também geram uma categoria de **receita**, filtrada pelo `recipe_type` da definição. Definições sem controller (carregadas só por datapack) não geram categoria.

A categoria de receita do JEI usa o mesmo `MultiblockMenuLayout` da GUI da máquina para as posições de `item_slots` x/y e dos elementos `slot`, `fluid_bar`, `energy_bar`, `arrow`, `entity` e `title` de `gui.elements`; o fundo é `textures/gui/jei_base.png`. Fallbacks só são usados quando a posição não existe. A categoria de estrutura usa um painel próprio e não usa esse layout.

Na GUI da máquina, clicar na seta ou na barra de progresso abre a categoria de receitas daquele controller no JEI. A área clicável é registrada pelo plugin JEI a partir do layout sincronizado, então funciona com qualquer posição definida no JSON.

Na categoria JEI, o botão de transferência (seta) move os ingredientes de item para os slots `input` compatíveis. Ingredientes fluidos não são transferidos pelo JEI; insira-os manualmente nos tanques.

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

O campo `type` da receita precisa ser um `recipe_type` registrado pelo mod; veja a seção 10.1.

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

Datapacks podem adicionar ou sobrescrever definições e receitas, e resource packs podem adicionar traduções/assets, mas **nenhum dos dois pode criar blocos controller novos**: o registro de blocos acontece antes dos datapacks. Para um controller novo, empacote o JSON no mod no namespace `infinity_nexus_multiblock` e reinicie, ou registre o bloco em Java e aponte `controller.block` para ele.

## 3. Estrutura completa do JSON

### 3.1 Campos de nível raiz

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `schema_version` | sim | inteiro | — | Hoje só aceita `1`. |
| `id` | sim | identifier | — | Precisa ser igual ao caminho do arquivo. |
| `type` | sim | string | — | `recipe` ou `storage`. |
| `recipe_type` | depende | identifier | — | Obrigatório em `type=recipe`; proibido em `type=storage`. Para processar receitas, o tipo precisa ter sido registrado no startup; o mod registra um tipo para cada `recipe_type` declarado por um controller empacotado no namespace `infinity_nexus_multiblock`. Não há lista fixa: se nenhum controller empacotado declarar um tipo, ele não existe. O carregamento da definição não valida o registro. Datapacks não registram tipos novos. |
| `priority` | não | inteiro | `0` | Usado para resolver sobreposição entre estruturas. Pode ser negativo. |
| `tier` | não | inteiro | `1` | Mínimo `1`. Hoje é apenas metadado; não altera comportamento. |
| `controller` | não | objeto | ausente | Em JSON empacotado, define o bloco e as texturas geradas; em datapack, valida um bloco Java existente. Veja a seção 9. |
| `recipe` | não | objeto | ausente | Receita shaped de crafting table do item do controller, lida apenas de JSONs empacotados (não faz parte do codec de datapack). Veja a seção 9.3.1. |
| `inventory_rows` | não | inteiro | `3` | De `1` a `6`. Usado só quando `item_slots` está vazio. |
| `item_slots` | não | lista | `[]` | Define os slots do inventário. Veja a seção 6. |
| `capabilities` | não | objeto | vazio | Energia e fluidos. Veja a seção 7. |
| `capability_ports` | não | objeto | ausente | Restringe quais blocos membros expõem item, energia e fluido. Veja a seção 7.3. |
| `gui` | não | objeto | ausente | Layout padrão ou customizado. Veja a seção 8. |
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
  "schema_version": 1,
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

Esse JSON já carrega como definição. Se estiver empacotado no mod com o namespace `infinity_nexus_multiblock`, o bloco controller é criado automaticamente com o ID do arquivo e texturas fallback. Se estiver em um datapack ou usar outro namespace, ele não cria bloco: registre um controller Java cujo `definitionId` seja `minhamod:minha_maquina` (seção 9). Não há controllers dinâmicos embutidos para reutilizar nesta build.

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
| `fluid` | todas | nenhuma | Slot de item; não é um tanque de fluido. |

Apenas `input` e `output` têm comportamento automático no processamento de receitas. Os outros roles são úteis para automação, filtros, limites e GUIs; outros mods podem acessá-los pelo inventário.

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
      "filter": "minecraft:water"
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
- O `energy.id` e os `fluids[].id` são usados pela GUI para localizar as barras. Eles não mudam as portas.

### 7.5 Energia em receitas

Se a receita tem `energy > 0`, a máquina precisa ter `capabilities.energy`. Sem armazenamento de energia, o progresso não avança.

O custo é distribuído ao longo dos ticks. Uma receita com `energy: 1000` e `time: 100` consome aproximadamente 10 FE por tick. Se a energia acabar, o progresso pausa.

### 7.6 Fluidos em receitas

Se a receita tem `fluid_ingredients`, a máquina precisa ter tanques suficientes. Cada ingrediente de fluido procura um tanque que aceite o fluido e tenha a quantidade necessária. Cada tanque satisfaz no máximo um ingrediente por craft.

### 7.7 Reload

`/reload` atualiza a definição, mas **não recria nem reconfigura** capabilities de energia/fluidos já existentes. Se você mudar `capacity`, `max_receive`, `max_extract` ou `filter`, quebre e coloque o controller de novo, ou reinicie o mundo, para criar as capabilities com os novos valores.

## 8. GUI

### 8.1 Layout padrão

Sem `gui`, ou com `gui.elements` vazio, o layout é automático:

- slots do inventário em grade automática (9 colunas, ou 8 quando há barras/progresso); posições `x`/`y` que caibam em `176x166` são respeitadas;
- título no topo;
- uma barra de energia, se existir;
- uma barra para cada tanque de fluido, se existirem;
- uma barra de progresso à direita, se `type=recipe` e `recipe_type` existirem;
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
- `title` e `text` não têm validação de limites; cuidado para não desenhar fora da tela.
- Em `title`/`text`, `x/y` representam o canto superior esquerdo da linha de texto, não a baseline; a tela compensa 1 px para alinhar o desenho ao referencial do builder.
- No builder, novos elementos começam com o layout do `spawner`: `title` em `8,4`, `text` em `8,75`, `player_inventory` em `8,84`, `energy_bar`/`fluid_bar`/`progress_bar` em `158/148/138,10` com `8x64`, e `entity` em `96,18` com `32x32`.
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
| `width` | não | inteiro | `16` | De `0` a `4096`. Barras e `entity` precisam de valor positivo. |
| `height` | não | inteiro | `16` | De `0` a `4096`. Barras e `entity` precisam de valor positivo. |
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
| `entity` | Área que renderiza a entidade do `output` da receita. | `x`, `y`, `width`, `height` |

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
- `width`/`height` precisam ser positivos e caber na GUI.
- A entidade vem de `output.id` da receita exibida; o elemento não tem campo de ID.
- O fundo é preto e a borda é cinza, no mesmo estilo das barras.
- Renderiza no JEI e no controller; no controller, a entidade é a do `activeRecipe` sincronizado.
- Entidades vivas usam o renderer de inventário; entidades não-vivas usam o render state genérico.

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

## 9. Controllers dinâmicos e Java

### 9.1 Criação dinâmica a partir do JSON

O mod lê os JSONs empacotados no próprio jar no startup, antes do congelamento dos registries:

```text
data/infinity_nexus_multiblock/multiblock/<arquivo>.json
```

Para cada definição válida nesse namespace, o scanner registra:

- um `MultiblockControllerBlock`;
- um `BlockItem`;
- o block entity compartilhado `dynamic_controller`.

Regras:

- só JSONs dentro do jar do mod e no namespace `infinity_nexus_multiblock` criam blocos;
- o arquivo precisa ter `pattern`, `schema_version: 1`, `id` igual ao caminho e `type` válido;
- `template: true` é ignorado;
- com `controller`, o bloco é criado em `controller.block` e usa `definitionId = id`;
- sem `controller`, o bloco é criado no próprio `id` e usa as texturas fallback da pasta `fallback`;
- o fallback da mesma face usa `_off` quando parado e `_on` quando `processing=true`;
- com `controller`, cada textura declarada que não existir usa o fallback da mesma face;
- IDs de bloco duplicados são ignorados com aviso no log.

O repositório não inclui JSONs em `src/main/resources/data`, então o scanner registra 0 controllers por padrão. Para testar os dois casos, adicione seus próprios JSONs:

- com `controller`: o bloco é criado em `controller.block` e usa as texturas declaradas;
- sem `controller`: o bloco é criado no próprio `id` e usa as texturas fallback.

Adicionar ou remover um controller dinâmico exige recompilar o mod (quando o JSON está no jar) e reiniciar o jogo/servidor. `/reload` recarrega definições, mas não registra nem remove blocos.

### 9.2 Assets gerados automaticamente

Para cada controller dinâmico, o cliente recebe um resource pack interno, hidden, no topo da pilha:

- blockstate com 16 variantes `facing` × `formed` × `processing`;
- modelo de bloco `minecraft:block/cube` com as seis texturas;
- modelo de item;
- item definition `minecraft:model`;
- lang `en_us` e `pt_br` com as chaves `block.<namespace>.<path>` e `gui.<namespace>.<path>` para o bloco e para a definição, mesclada com a lang base do mod.

O pack é montado em memória; nenhum arquivo é gravado em disco. O pack gerado tem prioridade sobre assets estáticos do mesmo ID.

Não são gerados:

- texturas: não são geradas; as declaradas precisam existir em `assets/<namespace>/textures/block/...`, e cada face ausente usa `fallback`;
- loot table: `getDrops` retorna o próprio item;
- textura de GUI;
- traduções além de `en_us` e `pt_br`.

Para controllers manuais (seção 9.5), o pack não gera nada; todos os assets precisam existir.

### 9.3 JSON `controller`

```json
"controller": {
  "block": "minhamod:minha_maquina",
  "textures": {
    "front": "minhamod:block/minha_maquina/front",
    "back": "minhamod:block/minha_maquina/back",
    "left": "minhamod:block/minha_maquina/left",
    "right": "minhamod:block/minha_maquina/right",
    "top": "minhamod:block/minha_maquina/top",
    "bottom": "minhamod:block/minha_maquina/bottom"
  }
}
```

| Campo | Obrigatório | Tipo | Regras |
|---|---:|---|---|
| `block` | sim | identifier | Bloco registrado que seja `MultiblockControllerBlock` e cujo `definitionId` seja igual ao `id` do JSON. |
| `textures` | sim | objeto | Seis identificadores: `front`, `back`, `left`, `right`, `top`, `bottom`. |
| `formed_textures` | não | objeto | As mesmas seis chaves, todas opcionais; sobrescreve a textura da face quando a máquina está formada. |
| `particle` | não | identifier | Identificador de partícula. Usa `front` quando omitido ou quando a textura não existe. |
| `light_level` | não | inteiro | `0` a `15`; luz emitida enquanto `processing=true` (0 caso contrário). |

`controller` é opcional. Se existir, `textures` é obrigatório.

Em um JSON empacotado no namespace do mod, o scanner cria o bloco em `controller.block` com `definitionId` igual ao `id`. O path do bloco pode ser diferente do `id` (por exemplo, `id=foo` e `controller.block=bar`), mas o `definitionId` continua `foo`; o loader valida essa relação. Para ser criado dinamicamente, `controller.block` precisa estar no namespace `infinity_nexus_multiblock`.

Em um datapack externo, `controller.block` não cria bloco: ele precisa apontar para um `MultiblockControllerBlock` já registrado em Java, com `definitionId` igual ao `id`.

As texturas e a partícula:

- em controller dinâmico, geram o blockstate/modelo do bloco; `particle` usa `front` quando omitido ou quando a textura não existe;
- o fallback de cada face usa o sufixo `_off` fora de processamento e `_on` durante o processamento;
- em controller Java manual, continuam validadas, mas a aparência vem do blockstate/modelo próprio do bloco.

Sem `controller`:

- JSON empacotado: bloco criado no `id` com texturas fallback da pasta `fallback`;
- datapack: nenhum bloco é criado; é preciso um controller Java existente para o `id`.

### 9.3.1 Receita shaped do controller

O campo opcional `recipe` no nível raiz define uma receita shaped de crafting table cujo resultado é o próprio item do controller:

```json
"recipe": {
  "pattern": [
    "III",
    "ICI",
    "III"
  ],
  "key": {
    "I": "minecraft:iron_ingot",
    "C": "#c:ingots/copper"
  }
}
```

| Campo | Obrigatório | Tipo | Regras |
|---|---:|---|---|
| `pattern` | sim | lista de strings | De 1 a 3 linhas, todas com a mesma largura de 1 a 3; espaço é slot vazio. |
| `key` | sim | objeto | Cada chave tem um caractere; cada valor é um item ou tag. Todos os símbolos do pattern precisam existir na key, e a key não pode ter símbolos sem uso. |

O mod gera `data/<namespace>/recipe/controller/<path>.json` com `type: minecraft:crafting_shaped` e `result.id` igual ao bloco controller. A receita é gerada no startup a partir dos JSONs empacotados no mod; ela não é criada para definições carregadas apenas por datapack. O builder monta o pattern em uma grade 3x3 e cria automaticamente uma linha de key para cada símbolo usado.

### 9.4 Regra de validação

O bloco em `controller.block`:

- precisa existir;
- precisa ser um `MultiblockControllerBlock`;
- precisa ter `definitionId` igual ao `id` da definição.

Exemplo: se o JSON tem `"id": "minhamod:fornalha"`, o bloco controller precisa ter sido criado com `Identifier.fromNamespaceAndPath("minhamod", "fornalha")`. O nome de registro do bloco pode ser outro, mas o `definitionId` precisa bater. Em um controller dinâmico, o scanner já cria o bloco com `definitionId = id`, mesmo que `controller.block` use outro path.

### 9.5 Registro manual em Java

Use o registro manual apenas como API de extensão, quando o bloco estiver em outro namespace ou precisar de um block entity/capabilities próprios. O mod não registra controllers manualmente; para JSONs empacotados no namespace do mod, use sempre a criação dinâmica.

```java
package minhamod;

import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

public final class MinhaMaquina {
    public static final String MOD_ID = "minhamod";
    public static final Identifier DEFINITION_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "minha_maquina");

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<MultiblockControllerBlock> CONTROLLER =
            BLOCKS.registerBlock(
                    "minha_maquina",
                    properties -> new MultiblockControllerBlock(
                            DEFINITION_ID,
                            () -> BLOCK_ENTITY.get(),
                            properties),
                    properties -> properties
                            .mapColor(MapColor.METAL)
                            .strength(4.0F)
                            .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem("minha_maquina", CONTROLLER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("minha_maquina", () -> new BlockEntityType<>(
                    (pos, state) -> new MultiblockControllerBlockEntity(BLOCK_ENTITY.get(), pos, state),
                    CONTROLLER.get()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MinhaMaquina::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                BLOCK_ENTITY.get(),
                (blockEntity, side) -> side == null
                        ? VanillaContainerWrapper.of(blockEntity.getInternalItemHandler())
                        : new WorldlyContainerWrapper(blockEntity.getInternalItemHandler(), side));

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getEnergyHandler(side));

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side));
    }
}
```

Chame `MinhaMaquina.register(modEventBus)` no construtor do seu mod.

A capability de item nos blocos membros já é registrada globalmente pelo Infinity Nexus Multiblock e respeita `capability_ports`. Ainda assim, registre a capability de item no **block entity do controller** para que o próprio controller exponha o inventário quando a porta de item permitir.

### 9.6 Assets para controller manual

Para um controller manual de addon, o JSON não gera modelos, blockstates, loot tables ou traduções. Você precisa criar:

```text
assets/minhamod/blockstates/minha_maquina.json
assets/minhamod/models/block/minha_maquina.json
assets/minhamod/models/item/minha_maquina.json
assets/minhamod/textures/block/minha_maquina.png
assets/minhamod/lang/pt_br.json
data/minhamod/loot_table/blocks/minha_maquina.json
```

Para controllers dinâmicos empacotados, blockstate, modelo, modelo de item, item definition e lang `en_us`/`pt_br` são gerados automaticamente (seção 9.2); ainda é necessário fornecer as texturas.

O controller tem três propriedades de blockstate:

- `facing`: `north`, `south`, `east` ou `west`;
- `formed`: `true` ou `false`;
- `processing`: `true` ou `false`.

O blockstate precisa cobrir as 16 combinações:

```json
{
  "variants": {
    "facing=north,formed=false,processing=false": { "model": "minhamod:block/minha_maquina" },
    "facing=north,formed=false,processing=true":  { "model": "minhamod:block/minha_maquina" },
    "facing=north,formed=true,processing=false":  { "model": "minhamod:block/minha_maquina" },
    "facing=north,formed=true,processing=true":   { "model": "minhamod:block/minha_maquina" },
    "facing=south,formed=false,processing=false": { "model": "minhamod:block/minha_maquina" },
    "facing=south,formed=false,processing=true":  { "model": "minhamod:block/minha_maquina" },
    "facing=south,formed=true,processing=false":  { "model": "minhamod:block/minha_maquina" },
    "facing=south,formed=true,processing=true":   { "model": "minhamod:block/minha_maquina" },
    "facing=east,formed=false,processing=false":  { "model": "minhamod:block/minha_maquina" },
    "facing=east,formed=false,processing=true":   { "model": "minhamod:block/minha_maquina" },
    "facing=east,formed=true,processing=false":   { "model": "minhamod:block/minha_maquina" },
    "facing=east,formed=true,processing=true":    { "model": "minhamod:block/minha_maquina" },
    "facing=west,formed=false,processing=false":  { "model": "minhamod:block/minha_maquina" },
    "facing=west,formed=false,processing=true":   { "model": "minhamod:block/minha_maquina" },
    "facing=west,formed=true,processing=false":   { "model": "minhamod:block/minha_maquina" },
    "facing=west,formed=true,processing=true":    { "model": "minhamod:block/minha_maquina" }
  }
}
```

Modelo simples:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "minhamod:block/minha_maquina"
  }
}
```

Modelo de item:

```json
{
  "parent": "minhamod:block/minha_maquina"
}
```

Loot table:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minhamod:minha_maquina"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```

Tradução:

```json
{
  "block.minhamod.minha_maquina": "Minha Máquina",
  "gui.minhamod.minha_maquina.title": "Minha Máquina"
}
```

A loot table acima é opcional: `MultiblockControllerBlock.getDrops` retorna o próprio item, então controllers manuais e dinâmicos dropam a si mesmos sem uma loot table customizada.

### 9.7 Comportamento do controller

- `FACING` é horizontal e é definido pela direção oposta à do jogador ao colocar.
- `FORMED` é atualizado pelo block entity.
- Clicar com a mão vazia em um controller não formado tenta montar a estrutura.
- Clicar em um controller formado abre a GUI.
- `shift+clique` com a mão vazia no criativo exporta a estrutura para o clipboard.
- Agachar evita a montagem automática.
- Clicar em qualquer membro formado também abre a GUI.
- Quebrar um membro invalida a estrutura.

## 10. Receitas

### 10.1 Arquivo

```text
data/<namespace>/recipe/<arquivo>.json
```

A definição da máquina precisa ter:

```json
"type": "recipe",
"recipe_type": "infinity_nexus_multiblock:exemplo_smelting"
```

A receita precisa ter exatamente o mesmo ID:

```json
"type": "infinity_nexus_multiblock:exemplo_smelting"
```

`exemplo_smelting` é apenas um nome de exemplo: ele só é registrado se um controller empacotado declarar esse `recipe_type` (veja a seção 15.1).

O mod registra no startup um `RecipeType` e um `RecipeSerializer` para cada `recipe_type` único usado por controllers empacotados no namespace `infinity_nexus_multiblock`. O ID do tipo e o ID do serializer são iguais, então `type` da receita e `recipe_type` da definição usam o mesmo valor. Não há lista fixa: nesta build, sem JSONs empacotados, nenhum tipo é registrado. Para criar um tipo, adicione um controller empacotado que declare o novo `recipe_type` e reinicie o jogo/servidor (sem alterar Java). Datapacks podem reutilizar um tipo registrado, mas não podem criar tipos novos.

### 10.2 Campos da receita

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `type` | sim | string | — | ID de um `recipe_type` registrado pelo mod (veja a seção 10.1); não há tipos embutidos nesta build. |
| `ingredients` | não | lista | `[]` | Lista de `Ingredient`. |
| `fluid_ingredients` | não | lista | `[]` | Lista de `SizedFluidIngredient`. |
| `output` | sim | objeto | — | Saída tipada `item`, `fluid` ou `entity`; `result` legado ainda é aceito para item. |
| `energy` | não | inteiro | `0` | FE totais do craft, mínimo `0`. |
| `time` | não | inteiro | `100` | Duração em ticks, mínimo `1`. |

A receita precisa ter pelo menos um ingrediente de item ou de fluido. Uma receita sem ingredientes nunca combina.

### 10.3 `ingredients`

Formatos comuns:

```json
"ingredients": [
  "minecraft:raw_iron"
]
```

```json
"ingredients": [
  "#c:raw_materials/iron"
]
```

```json
"ingredients": [
  ["minecraft:raw_iron", "minecraft:raw_copper"]
]
```

A lista interna é uma alternativa: o ingrediente aceita qualquer item dela. Para exigir dois itens diferentes, use duas entradas separadas:

```json
"ingredients": [
  "minecraft:raw_iron",
  "minecraft:coal"
]
```

Cada ingrediente consome **1 item** de um slot de input. Não existe campo de quantidade no ingrediente. Para exigir 2 itens iguais, repita o ingrediente e tenha slots de input suficientes.

O codec também aceita ingredientes customizados do NeoForge com `neoforge:ingredient_type`; para a maioria das máquinas, IDs, tags e listas resolvem. Para itens com data components, use `/inm hand` para gerar a entrada exata (veja [10.11](#1011-gerar-json-com-o-comando)).

### 10.4 `fluid_ingredients`

```json
"fluid_ingredients": [
  {
    "ingredient": "minecraft:water",
    "amount": 500
  }
]
```

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `ingredient` | sim | string/objeto | — | ID de fluido, tag com `#` ou ingrediente de fluido do NeoForge. |
| `amount` | não | inteiro | `1000` | Positivo, em mB. |

Cada ingrediente de fluido procura um tanque que aceite o fluido e tenha a quantidade. Cada tanque satisfaz no máximo um ingrediente por craft.

### 10.5 `output`

Saída de item:

```json
"output": {
  "type": "item",
  "id": "minecraft:iron_ingot",
  "count": 2
}
```

Saída de fluido:

```json
"output": {
  "type": "fluid",
  "id": "minecraft:water",
  "amount": 1000
}
```

Saída de entidade:

```json
"output": {
  "type": "entity",
  "id": "minecraft:pig",
  "spawn": "C"
}
```

| Campo | Obrigatório | Tipo | Padrão | Regras |
|---|---:|---|---|---|
| `type` | sim | string | — | `item`, `fluid` ou `entity`. |
| `id` | sim | identifier | — | Item, fluido ou tipo de entidade de saída. |
| `count` | não | inteiro | `1` | Apenas `item`; de `1` a `99`. Na prática, o slot de saída limita a `64`, então valores acima disso não cabem. |
| `components` | não | objeto | vazio | Apenas `item`; patch de data components. |
| `amount` | sim | inteiro | — | Apenas `fluid`; positivo, em mB. |
| `spawn` | sim | string | — | Apenas `entity`; símbolo do pattern, ID de bloco ou `#tag`. |

A saída de item usa o primeiro slot `output` que aceite o item e tenha espaço. A saída de fluido usa o primeiro tanque que aceite o fluido e tenha capacidade. A entidade spawna acima do bloco indicado por `spawn`. O campo legado `result` continua aceito como saída de item. Para gerar o objeto `output` a partir do item na mão, use `/inm hand` (veja [10.11](#1011-gerar-json-com-o-comando)).

### 10.6 Matching

O matching de itens é **shapeless**:

- cada ingrediente procura qualquer slot com role `input`;
- a ordem de `item_slots` não importa;
- cada slot de input satisfaz no máximo um ingrediente por craft;
- filtros de slot continuam valendo;
- a saída de item é inserida no primeiro `output` compatível com espaço;
- a saída de fluido usa o primeiro tanque compatível com capacidade;
- a saída de entidade spawna acima do bloco indicado por `spawn`.

Consequências:

- uma receita com 3 ingredientes precisa de 3 slots de input com os itens certos;
- dois ingredientes iguais precisam de dois slots de input;
- um slot de input com 64 itens ainda fornece apenas 1 item por craft.

### 10.7 Energia e tempo

O custo total de energia é dividido ao longo dos ticks. A cada tick, a máquina consome a diferença inteira da fração acumulada. Se não houver energia suficiente, o progresso pausa.

Exemplo:

```json
"energy": 1000,
"time": 100
```

Isso consome cerca de 10 FE por tick. O craft termina após 100 ticks (5 segundos).

### 10.8 Quando o craft reinicia

O progresso é resetado quando:

- a estrutura deixa de estar formada;
- um ingrediente necessário some;
- a saída deixa de caber (slot cheio, tanque sem capacidade ou `spawn` inválido);
- a receita ativa deixa de existir;
- a definição muda de forma incompatível.

O progresso é persistido no NBT do controller e continua após recarregar o chunk/servidor.

### 10.9 Limitações de receita

- existe apenas uma saída por receita: item, fluido ou entidade;
- não há subprodutos, chance, energia como saída ou múltiplas saídas simultâneas;
- não há prioridade de receita; o recipe manager escolhe a primeira receita compatível;
- receitas vanilla ainda não são suportadas.

### 10.10 Exemplo completo

Definição: [`exemplos/exemplo_recipe.json`](exemplos/exemplo_recipe.json)

Receita: [`exemplos/exemplo_recipe_iron.json`](exemplos/exemplo_recipe_iron.json)

A máquina tem dois slots de input (`raw_iron` e `coal`), um output (`iron_ingot`), energia e um tanque de água. A receita consome 1 raw iron, 1 coal, 500 mB de água, 1000 FE e leva 100 ticks.

### 10.11 Gerar JSON com o comando

O mod registra `/inm` (alias `/infinity_nexus_multiblock`) com dois subcomandos que geram o JSON no formato aceito pelas receitas. As linhas enviadas no chat são clicáveis e copiam o texto. Os comandos são exclusivos para jogadores.

**`/inm hand`** lê o item na mão principal e envia:

- **Input**: uma entrada pronta para `ingredients`;
- **Output**: um objeto pronto para `output`.

Item sem data components:

```json
"minecraft:iron_ingot"
```

```json
{"type":"item","id":"minecraft:iron_ingot","count":1}
```

Item com data components:

```json
{
  "neoforge:ingredient_type": "neoforge:components",
  "items": "minecraft:diamond_sword",
  "components": {
    "minecraft:damage": 10
  }
}
```

```json
{
  "type": "item",
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:damage": 10
  }
}
```

O input não inclui `count`: cada ingrediente consome 1 item. O output usa o `count` atual da stack. O ingrediente de componentes é não estrito (`strict: false`); ele exige os componentes listados, mas aceita componentes extras. Para exigir a stack exata, adicione `"strict": true`.

O **Input** serve apenas para `ingredients`; o **Output** serve apenas para `output`. No builder, os campos de saída são separados: preencha `id`, `count` e `components` em vez de colar o objeto inteiro em um único campo. O objeto de output também pode ser colado diretamente no JSON da receita.

**`/inm look`** faz um raycast de até 20 blocos e envia três linhas quando o bloco tem propriedades:

1. o ID puro do bloco, para o campo `block` do builder;
2. o matcher completo, para o JSON;
3. o objeto `state` separado.

ID puro:

```text
minecraft:oak_stairs
```

Matcher completo:

```json
{"block":"minecraft:oak_stairs","state":{"facing":"east","half":"bottom","shape":"straight","waterlogged":"false"}}
```

State:

```json
{"facing":"east","half":"bottom","shape":"straight","waterlogged":"false"}
```

Se o bloco não tiver propriedades, a linha de `state` não é enviada. Os valores refletem o state atual do bloco. No builder, o campo `Bloco` aceita tanto o ID puro quanto o matcher completo; ao colar o matcher, o campo e as propriedades de `state` são preenchidos automaticamente. Em uma definição, o state é relativo à orientação canônica `SOUTH`; se necessário, ajuste `facing`/`axis` para a orientação em que a máquina será construída.

## 11. Herança e templates

### 11.1 Pai e filho

```json
{
  "schema_version": 1,
  "id": "minhamod:fornalha_base",
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

O filho herda `type`, `recipe_type` e `capabilities.energy`, mas troca `capacity` e `max_receive` via `overrides`. O `recipe_type` usado é ilustrativo: ele só existe se um controller empacotado o declarar (seção 10.1).

### 11.2 Regras de merge

- Objetos são mesclados recursivamente.
- Listas substituem a lista inteira.
- Campos do filho são aplicados antes de `overrides`.
- Se o mesmo campo aparecer no filho e em `overrides`, `overrides` vence.
- `extends`, `family`, `overrides` e `template` não são herdados.
- O filho precisa do próprio `id`.
- O `controller.block` final é validado contra o `id` do filho.
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
  "schema_version": 1,
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

Ele **não** contém `item_slots`, `capabilities`, `gui`, `controller`, receita ou prioridade. Use como ponto de partida para o pattern.

### 13.3 Normalização

A exportação normaliza posições e states para a orientação canônica `SOUTH`. Se você exportou uma máquina virada para norte, o JSON ainda descreve a máquina olhando para sul. Isso permite reutilizar o pattern com qualquer `FACING`.

### 13.4 Transformar em definição

1. Salve o JSON em `data/<namespace>/multiblock/<arquivo>.json`.
2. Troque o `id` para `<namespace>:<arquivo>`.
3. Troque `type` para `recipe` se a máquina processar receitas.
4. Adicione `recipe_type` se for `recipe`.
5. Adicione `item_slots`, `capabilities`, `gui` e `controller` conforme necessário.
6. Rode `/reload` para recarregar a definição. Se o JSON for criar um controller dinâmico novo, ele precisa estar empacotado no mod e o jogo/servidor precisa ser reiniciado; `/reload` sozinho não registra o bloco.

Não há um exemplo exportado no repositório; use o JSON do clipboard como ponto de partida.

### 13.5 Limitações da exportação

- limite configurável em `export.pattern_scan_limit` (padrão 256; máximo 4096);
- flood-fill pode incluir blocos vizinhos que não fazem parte da máquina;
- não exporta capabilities, slots, GUI ou receita;
- o `id` precisa ser ajustado;
- o `type` exportado é sempre `storage`.

## 14. Reload e ciclo de vida

Use `/reload` no servidor.

O que acontece:

- as definições são recarregadas;
- blocos controller dinâmicos e o pack de assets gerado são criados apenas no startup; `/reload` não registra, remove nem regenera blocos/modelos;
- controllers formados são revalidados;
- se a definição não existir mais, a estrutura é invalidada;
- inventário e layout da GUI são reconfigurados quando a definição muda;
- stacks que não cabem no novo inventário são descartados;
- capabilities de energia/fluidos já existentes **não** são recriadas nem reconfiguradas;
- capabilities ausentes podem ser criadas quando a definição fica disponível;
- as definições resolvidas são enviadas aos clientes para a preview;
- o layout da GUI é persistido no block entity para sobreviver ao carregamento.

Se você mudou `capacity`, `max_receive`, `max_extract` ou `filter` de uma capability existente, quebre e coloque o controller de novo, ou reinicie o mundo.

Para adicionar ou remover um controller dinâmico, recompile o mod quando o JSON estiver no jar e reinicie o jogo/servidor. Um datapack externo não cria blocos; ele só pode adicionar/sobrescrever definições de blocos que já existem.

## 15. Exemplos completos

Os arquivos ficam em [`exemplos/`](exemplos/).

| Arquivo | O que mostra |
|---|---|
| [`exemplo_storage.json`](exemplos/exemplo_storage.json) | `type=storage`, item slots com filtros/limites/faces, energia, fluido e GUI customizada com slots via `x`/`y`. |
| [`exemplo_recipe.json`](exemplos/exemplo_recipe.json) | `type=recipe`, dois inputs, um output, energia, fluido e GUI padrão com barra de progresso. |
| [`exemplo_recipe_iron.json`](exemplos/exemplo_recipe_iron.json) | Receita de exemplo (tipo `exemplo_smelting`) com dois ingredientes de item, um de fluido, energia e tempo. |

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

Os exemplos não têm `controller` no JSON. Em um datapack, isso significa que eles **não criam blocos**: registre um controller Java (seção 9.5) ou empacote os exemplos no jar do mod. Se você adicionar os exemplos ao jar em `data/infinity_nexus_multiblock/multiblock`, eles passam a criar controllers dinâmicos no startup e o `recipe_type` `infinity_nexus_multiblock:exemplo_smelting` é registrado; nesse caso, recompile e reinicie.

### 15.2 Sobrescrever uma definição existente

Não há controller dinâmico embutido para sobrescrever nesta build. Se você já adicionou um JSON ao jar e reiniciou o jogo, pode sobrescrever a definição em um datapack usando o mesmo caminho:

```text
data/infinity_nexus_multiblock/multiblock/<arquivo>.json
```

Como o bloco já existe, basta `/reload`; não é preciso reiniciar. Para um controller novo, use a criação dinâmica (seção 9.1) ou registre em Java como API de extensão (seção 9.5).

### 15.3 Controllers dinâmicos embutidos

Esta build não inclui controllers dinâmicos empacotados. Para adicionar um, coloque o JSON em `src/main/resources/data/infinity_nexus_multiblock/multiblock/`, recompile e reinicie. Um datapack sozinho não cria o bloco.

## 16. Erros comuns

| Mensagem / sintoma | Causa | Correção |
|---|---|---|
| `Definition id ... does not match file id` | `id` diferente do caminho. | Use `<namespace>:<arquivo>`. |
| `Unsupported schema_version` | Versão diferente de `1`. | Use `"schema_version": 1`. |
| `Unknown multiblock type` | `type` inválido. | Use `recipe` ou `storage`. |
| `Recipe multiblocks need a recipe_type` | `type=recipe` sem `recipe_type`. | Adicione `recipe_type`. |
| `Storage multiblocks cannot define a recipe_type` | `type=storage` com `recipe_type`. | Remova `recipe_type`. |
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
| `Unknown controller block` | `controller.block` aponta para um bloco que não existe; um datapack não pode criar o bloco. | Use um controller dinâmico já empacotado, empacote o JSON no mod ou registre o bloco em Java. |
| `Discovered 0 dynamic multiblock controllers` | Nenhum JSON válido foi encontrado no jar do mod. | Confira o namespace, o caminho `data/infinity_nexus_multiblock/multiblock` e o log do startup. |
| `Skipping controller block ... because it is not in this namespace` | `controller.block` usa namespace diferente de `infinity_nexus_multiblock`. | Use o namespace do mod ou registre o bloco em Java. |
| `Duplicate dynamic controller block` | Duas definições usam o mesmo `controller.block`. | Dê um ID de bloco diferente para cada definição. |
| `Dynamic controller definition ... has no pattern` | JSON empacotado sem `pattern` e sem ser template referenciado. | Adicione `pattern` ou marque como `template`. |
| `Dynamic controller definition ... declares id ...` | `id` não corresponde ao caminho do arquivo. | Alinhe o `id` ao caminho. |
| Receita não carrega | Caminho, `type` ou IDs errados. | Confira `data/<namespace>/recipe`, `type` e ingredientes. |
| Definição não carrega | Caminho/ID/schema errados. | Confira o log do servidor. |

O log fica em `logs/latest.log`.

Se a definição está em um datapack e o log mostra `Unknown controller block`, o datapack não pode criar esse bloco. Empacote o JSON no jar do mod ou registre o bloco em Java.

## 17. Checklist final

Antes de testar, confirme:

- [ ] O JSON está em `data/<namespace>/multiblock/<arquivo>.json`.
- [ ] `id` é `<namespace>:<arquivo>`.
- [ ] `schema_version` é `1`.
- [ ] `type` é `recipe` ou `storage`.
- [ ] `recipe_type` existe só em `recipe`.
- [ ] O pattern tem exatamente uma âncora.
- [ ] Todas as camadas têm o mesmo número de linhas.
- [ ] Todas as linhas têm a mesma largura.
- [ ] Todo símbolo não-âncora tem matcher.
- [ ] Para controller dinâmico: o JSON está empacotado no jar, no namespace `infinity_nexus_multiblock`.
- [ ] Para controller Java: o bloco tem `definitionId` igual ao `id`.
- [ ] As texturas do controller existem em `assets/<namespace>/textures/block/...`.
- [ ] Para controller manual: o blockstate cobre `facing`, `formed` e `processing`, e modelo/loot/lang existem.
- [ ] Para controller dinâmico: blockstate, modelo, item e lang `en_us`/`pt_br` são gerados; não é preciso criá-los.
- [ ] Se adicionou/removeu um controller dinâmico, reiniciou o jogo/servidor; `/reload` não registra blocos.
- [ ] `item_slots` tem IDs únicos e filtros válidos.
- [ ] `x`/`y` aparecem juntos.
- [ ] `capabilities` tem IDs únicos e `energy.id` diferente dos tanques.
- [ ] Se usar `capability_ports`, os símbolos existem no pattern e cada capability lista as portas desejadas.
- [ ] A GUI tem exatamente um `player_inventory`.
- [ ] Barras de energia/fluido apontam para IDs existentes.
- [ ] `progress_bar`/`arrow`/`entity` só existem em `recipe`.
- [ ] A receita está em `data/<namespace>/recipe/<arquivo>.json`.
- [ ] A receita tem pelo menos um ingrediente.
- [ ] A máquina tem slot `output` para saída de item, tanque para saída de fluido e um `spawn` válido para entidade.
- [ ] Se a receita usa energia/fluido, a máquina tem as capabilities.
- [ ] Você rodou `/reload` e conferiu `logs/latest.log`.

## 18. Limitações atuais

### 18.1 Controllers e registro

- A criação dinâmica de controllers só ocorre no startup e apenas para JSONs dentro do jar do mod, no namespace `infinity_nexus_multiblock`; datapacks externos não criam blocos.
- O mod não registra blocos controller manualmente; controllers dinâmicos são criados a partir de JSONs empacotados (esta build não inclui nenhum).
- O antigo bloco estático `nexus_core` foi removido. Mundos criados com versões anteriores podem perder esses blocos e gerar avisos de registry ao carregar; faça backup antes de atualizar.
- Adicionar ou remover um controller dinâmico exige recompilar o mod (quando o JSON está no jar) e reiniciar o jogo/servidor; `/reload` não registra nem remove blocos.
- Controllers dinâmicos usam o block entity compartilhado `dynamic_controller`; comportamento ou capabilities Java específicos exigem um controller manual.

### 18.2 Máquinas, receitas e automação

- `type=recipe` processa receitas cujo `recipe_type` seja um tipo registrado pelo mod. O registro é feito no startup a partir dos controllers empacotados; não há tipos embutidos nesta build. Tipos não registrados são ignorados, e datapacks não podem criar tipos novos.
- `type=storage` já funciona como inventário/capabilities/GUI, mas não tem comportamento especializado de armazenamento dedicado. `tier` é apenas metadado, sem efeito de processamento.
- Receitas produzem uma única saída `item`, `fluid` ou `entity`; não há energia como saída, subproduto, múltiplas saídas simultâneas ou chance.
- Não há prioridade entre receitas; a primeira receita compatível encontrada é usada.
- Energia e fluidos não têm configuração por lado; as capabilities do controller e dos membros selecionados aceitam todas as faces. `capability_ports` escolhe blocos, não faces.
- As faces de `item_slots` são direções do mundo e não giram com o controller/estrutura.
- Matchers aceitam bloco, tag e `state`; não aceitam NBT, listas de alternativas ou matchers opcionais.

### 18.3 Assets e reload

- Em controllers dinâmicos empacotados, blockstates, modelos de bloco, modelos de item, item definitions e lang `en_us`/`pt_br` são gerados em memória. Texturas, loot tables (o drop usa `getDrops`), texturas de GUI e traduções além de `en_us`/`pt_br` não são geradas.
- Em controllers Java manuais, `controller.textures` e `controller.particle` são validados pelo schema, mas não geram modelo; a aparência vem do blockstate/modelo próprio do bloco.
- `/reload` recarrega definições, mas não registra/remove blocos dinâmicos nem regenera o pack de assets; para isso, reinicie o jogo/servidor (e recompile o mod quando o JSON estiver no jar).

### 18.4 Exportação

- A exportação é limitada por `export.pattern_scan_limit` na config comum do mod (`config/infinity_nexus_multiblock-common.toml`): padrão `256`, máximo `4096`.
- O JSON exportado contém apenas `schema_version`, `id`, `type` e `pattern` (blocos e states); não inclui `item_slots`, `capabilities`, `gui`, `controller`, receita ou prioridade, e o `type` exportado é sempre `storage`.

### 18.5 Metas ainda não atendidas

- Comportamento especializado de `type=storage` além do básico já implementado (inventário, capabilities e GUI): regras dedicadas de armazenamento de item, fluido e energia.
- Entidades como entrada de receitas dentro da área do multibloco; itens, fluidos e energia já são aceitos.
- Saída de energia em receitas; fluidos e entidades já são suportados.
- Tipos de receita independentes por máquina e adaptadores para receitas vanilla.
