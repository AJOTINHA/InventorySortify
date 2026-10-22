# Inventory Sortify

Organização de inventário e baús para **Minecraft 26.3 / NeoForge 26.3.0.95**, sem dependências.

## Recursos

| Recurso | Como usar |
| --- | --- |
| **Ordenar** | Botão acima do inventário ou do baú, tecla **R** (configurável em Controles) ou **botão do meio** num slot (fora do criativo). |
| **Regras de ordenação** | `config/inventorysortify/rules.txt`: posições fixas para itens no inventário (`D1 sword`, `A food`, `D LOCKED`...). |
| **Filtro** | Botão de funil na tela do inventário. Aba **Por item** (o tipo fica no inventário em qualquer slot) e aba **Por slot** (o que estiver no slot fica). Slots filtrados mostram um `*` no inventário e na hotbar. |
| **Setas de transferência** | Em baús, barris, shulkers e armazenamentos de outros mods: **seta para cima** manda do inventário para o baú (menos o filtro) e **seta para baixo** traz do baú para o inventário. |
| **Reposição automática** | Ferramenta quebrada ou pilha acabada na mão é reposta com o mesmo item do inventário. |
| **Repor antes de quebrar** | Troca a ferramenta quando a durabilidade chega ao limite, para ainda poder consertá-la. Sem outra igual, toca um aviso e guarda a ferramenta num slot vazio. |
| **Configurações** | Botão de engrenagem na tela do inventário, ou Mods → Inventory Sortify. |

- A ordenação é feita pelo servidor, que confere se os itens batem antes de aplicar; precisa do mod **nos dois lados**
  (no single player isso é automático).
- As setas e a reposição automática usam cliques normais do jogo e funcionam em qualquer servidor.

## Configuração (`inventorysortify-client.toml`)

| Opção | Padrão | O que faz |
| --- | --- | --- |
| `middleClickSorting` | ligado | Ordena com o botão do meio. |
| `sortButtons` | ligado | Mostra os botões acima do inventário e dos baús. |
| `filterMarks` | ligado | Mostra o `*` nos slots do filtro. |
| `depositOnlyMatching` | desligado | Seta para cima: só manda itens que o baú já tem. |
| `takeOnlyMatching` | desligado | Seta para baixo: só pega itens que você já tem. |
| `autoRefill` | ligado | Reposição automática. |
| `refillBeforeBreak` | desligado | Troca/guarda a ferramenta antes de quebrar. |
| `refillDurabilityThreshold` | 5 | Durabilidade em que a ferramenta é trocada. |
| `sounds` | ligado | Sons ao ordenar e repor. |

## Arquivos editáveis (`config/inventorysortify/`)

- `rules.txt`: regras de ordenação do inventário. A grade tem as linhas A–C (inventário) e D (hotbar), colunas 1–9.
  Palavras-chave vêm do `itemtree.txt`, ou podem ser um id (`minecraft:torch`) ou uma tag (`#c:ingots`).
- `itemtree.txt`: categorias e ordem dos itens, com tags (funciona com itens de outros mods).
- `keep.txt`: o filtro (um id de item por linha, ou `slot D1`).

Os arquivos são recarregados sozinhos na próxima ordenação. Apague um deles para voltar ao padrão.

## Licença

MIT (`LICENSE`).

## Desenvolvimento

```bash
./gradlew runClient          # abre o jogo com o mod
./gradlew build              # gera build/libs/inventorysortify-<versão>.jar
```

Para copiar o jar para uma instância do Prism Launcher a cada `build`, crie um `local.properties` (fora do git) com:

```properties
sortify_prism_mods_dir=C:/Users/<você>/AppData/Roaming/PrismLauncher/instances/<instância>/minecraft/mods
```
