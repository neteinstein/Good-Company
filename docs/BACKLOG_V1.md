# Backlog V1 – o que ainda falta (14-11-2026)

> Base: `origin/main` @ `86e8fd1` (depois dos PRs #28–#33). Fonte: as decisões datadas em
> [`PRODUCT_BRIEF.md`](PRODUCT_BRIEF.md) (D1–D8, C1–C2, F1–F20 e as de 14-11) e a
> [análise de lacunas](GAP_ANALYSIS_V1.md). C2 diz que a V1 inclui tudo, sem fases.
> Já feito: perfil completo (#20), timeline e "Log" (#21), círculos (#22), atalhos do ícone (#23),
> partilha com anexos (#24), lista de contactos com checkboxes (#25), SQLite (#14), fotos (#15).

Cada item é uma tarefa para um PR. A ordem é a ordem sugerida para correr; os marcados com ⛔ estão bloqueados
por uma decisão em aberto e **não devem ser feitos** sem resposta do dono.

## A. Pequenos e independentes (bons para começar)

| # | Item | Fonte | O que está feito | O que fazer / pronto quando |
|---|---|---|---|---|
| A1 | **"Pick from contacts" com escolha múltipla** | pedido do dono, 14-11 | O menu de adicionar (`AddFriendOption.PICK_FROM_CONTACTS` em `FriendsScreen`) chama `rememberContactPicker`, que só é múltiplo no Android 17+ (`ACTION_PICK_CONTACTS`); nas versões anteriores abre o seletor de um contacto de cada vez. A lista com checkboxes (`ChooseContactsScreen`, D2) já permite vários. | **Decidido (dono, 14-11): a lista com checkboxes.** No iOS e na build sideload, "Pick from contacts" abre a `ChooseContactsScreen` (pede a permissão de contactos; se for recusada, cai no seletor do sistema). Na build Play fica o seletor do sistema até a política de `READ_CONTACTS` estar confirmada (decisão de 14-11 "Import · where the list ships"). Registar a decisão na tabela do brief no mesmo PR. |
| A2 | **F10 · Lembretes de aniversário ligados por omissão** | F10 | `Friend.notifyBirthday = false` por omissão (`Relative` já está a `true`). | Omissão `true` para todos com aniversário; migração `.sqm` que liga os existentes; o interruptor por pessoa mantém-se. |
| A3 | **F1 · IA escondida por omissão** | F1 | A IA só falha com `MissingApiKeyException` quando não há chave. | Interruptor "IA" em Definições; sem ele, o separador Ideas sai da barra e "Things to talk about" (e todo o resto que depende de IA) fica invisível. |
| A4 | **F4 · Aniversário sem ano (UI)** | F4 | O modelo já aceita `year = null`. | Confirmar que o editor permite dia+mês sem ano e que a idade só aparece com ano; corrigir o que faltar, com testes. |
| A5 | **F19 · Editar o nome** | F19 | Não existe. | Nome editável; para pessoas dos contactos o nome local tem precedência (a sync não o sobrepõe); opção "repor o nome do contacto". |
| A6 | **F14 · Recortar, remover e substituir a foto** | F14 | Upload só quando não há foto. | Editor de zoom/recorte na forma do avatar; remover; substituir; foto local tem precedência sobre a do contacto (que volta ao remover). |
| A7 | **F3 · Lista de atividades configurável** | F3, 14-11 "Timeline · one Log sheet" | Ícones e "seis mais usados + More" feitos. | Em Definições, escolher/ordenar as atividades disponíveis. |
| A8 | **F15 · Ligar uma pessoa manual a um contacto** | F15 | Pessoas manuais existem. | Na página, "Ligar a um contacto" (seletor) sem perder nada do que está registado; merge pelo `ContactMerger`. |
| A9 | **D8 · Desligar a verificação de atualizações** | D8 | Sempre ativa no sideload. | Interruptor em Definições → App updates. |
| A10 | **F2 · "We met" abre um painel pequeno** | F2, F3 | Os botões "We met" da Home, da Agenda e dos círculos registam hoje sem perguntar; só o "+" da página abre a folha "Log" (ecrã inteiro). | Todos os "We met" na app abrem um painel pequeno: Hoje pré-selecionado e Guardar à mão (dois toques), Ontem, escolher data, ícones de atividade, nota opcional, "Mais detalhes" para a folha completa. Widget e notificação continuam a registar logo. Issue I13. |

## B. Modelo de pessoas e relações (por esta ordem: dependem uns dos outros)

| # | Item | Fonte | Notas |
|---|---|---|---|
| B1 | **F16 · Etiquetas partilhadas** | F16 | Lista única de etiquetas para gostos/não gostos; sugestões ao escrever; gestão em Definições (renomear, juntar duplicados, apagar). |
| B2 | **Ligações entre pessoas** | spec #12, F6, F18 | Hoje `Relative` é texto dentro do `Friend`. Criar ligação pessoa↔pessoa com tipo, recíproca. Inclui **F18** (namorado/a, marido/mulher, parceiro/a, ex; várias ao mesmo tempo; datas de início/fim, aniversário de namoro) e **F6** (relação comigo: irmã, filho…; círculo "Família" automático). |
| B3 | **F9 · Filhos são pessoas** | F9 | Migrar `Kid` para pessoas ligadas ao pai/mãe (B2); "Mostrar na minha lista" (sim para família, não para filhos de amigos); linha por baixo do nome ("Sobrinha · filha do teu irmão"). |
| B4 | **F7 · Parentesco automático** | F7 | Deduzir parentesco das ligações (proposta confirmada com um toque; casos ambíguos); pesquisa "os meus sobrinhos". |
| B5 | **F8 · Listas de prendas por ocasião** | F8, F17 | Listas ("Natal 2026"), pessoas por círculo ou pesquisa (B4), idade, ideias, prendas anteriores, IA; checklist comprar → comprado → dado; orçamento; duplicar a do ano passado. As ideias vindas da partilha (F17) aparecem aqui. |

## C. Registos, planos e lugares

| # | Item | Fonte | Notas |
|---|---|---|---|
| C1 | **F20 · Planos com um círculo e recorrentes** | F20 | Plano com círculo inteiro; repetição configurável; cada ocorrência vai para o calendário; "Did you meet?" cria um check-in de grupo com a atividade (F3) e os membros **por marcar**. |
| C2 | **F11 · Lugares com nome (só GPS)** | F11 | Guardar só coordenadas; "Que lugar é este?" num sítio novo; check-ins a ≤100 m herdam o nome; sugerir as pessoas habituais desse lugar. Tudo no dispositivo. |
| C3 | **D3 · Sugestões a partir do calendário** | D3, F2 | Opt-in, só os calendários escolhidos, só no dispositivo. Ler eventos (Android `READ_CALENDAR`, iOS EventKit acesso total + `PrivacyInfo`), corresponder por email (já há `FriendEmail`) e nome, sugerir registar; olhar 1–2 meses para trás (F2); sim/não/nunca; notificação via `NotificationPlanner`. Web: não suportado. |
| C4 | **Notas por voz com transcrição no dispositivo** | spec #26 | Android `SpeechRecognizer` on-device (`EXTRA_PREFER_OFFLINE`), iOS `SFSpeechRecognizer` com `requiresOnDeviceRecognition`; web desativado (Web Speech envia áudio). Usado no Log e nas notas. |

## D. Backups e segurança

| # | Item | Fonte | Notas |
|---|---|---|---|
| D1 | **Chave API no Keystore/Keychain** | spec #33, AGENTS.md §23 | `expect`/`actual`; migrar e apagar a chave antiga; web fica em localStorage com aviso. |
| D2 | **D4 · Backups encriptados** | D4 | Todos (ficheiro manual e Drive): palavra-passe do utilizador + KDF no dispositivo; chave de recuperação mostrada uma vez; aviso claro. Restaurar backups antigos em claro continua a funcionar. |
| D3 | **D5 · Backups automáticos na pasta privada do Drive** | D5, spec #36 | Automáticos em `drive.appdata` (WorkManager no Android; ao abrir no iOS/web); a exportação manual continua visível e encriptada; migrar a cópia visível atual. Depende de D2. |
| D4 | **"O que vai ser enviado" antes de cada pedido à IA** | spec #34 | Pré-visualização do conteúdo por pedido. |
| D5 | Backup incremental | spec #37 | Fica para o fim; sobre SQLite pode ser por snapshot + delta. |

## E. Vistas e IA

| # | Item | Fonte | Notas |
|---|---|---|---|
| E1 | **Mapa visual da rede** | spec #13, F6 | Depois de B2. Layout force-directed próprio em `Canvas`, eu ao centro. |
| E2 | **Painel da rede** | spec #28 | Warmth por círculo, tendências, quem está a arrefecer. |
| E3 | **IA: sugestões de mensagens além do aniversário** | spec #30 | Ex.: seguimento depois de um evento ou de um "What's new". Respeita F1. |

## F. Polimento pedido pelo dono (14-11-2026)

| # | Item | O que está feito | O que fazer / pronto quando |
|---|---|---|---|
| F1 | **Tirar o FAB "Add friend" e pô-lo no menu ⋮** | `ExtendedFloatingActionButton` em `FriendsScreen` chama `openAdd`; o menu ⋮ já existe no cabeçalho. | Primeiro item do ⋮ ("Add friend"), abre a mesma folha (na web, direto ao diálogo do nome); sem FAB nem os 88 dp de espaço em baixo; o estado vazio mantém o botão. Capturas atualizadas. O FAB "New reminder" da Agenda fica como está (dono, 14-11). |
| F2 | **As snackbars desaparecem sempre** | `showSnackbar(..., actionLabel = …)` sem `duration` é `Indefinite` no Material 3: os Undo (página do amigo, Agenda, Timeline) nunca fecham, e o `onMessageShown()` seguinte fica preso atrás deles. `SnackbarHost` simples não fecha ao tocar. | Um `SnackbarHost` comum em `:core:ui` (fecha ao tocar e ao deslizar) usado nos 10 ecrãs; Undo com duração fixa (`Long`); teste que nenhuma é `Indefinite`. |
| F3 | **Autosave nos popups de escolha** | `FamilySheet` (parentesco) tem o Save no fim do scroll; `LocalDatePickerDialog` (aniversários e outras datas) precisa de OK. | Diálogos só de escolha gravam e fecham ao escolher; folhas com texto gravam ao fechar (se o nome estiver preenchido) com Save fixo no topo; snackbar com Undo. Inventariar todos os popups de escolha no PR. Fazer com/depois de I2 (#36, aniversário sem ano mexe no mesmo seletor). |

## G. Lacunas do brief verbatim (por escolher)

| # | Ponto do brief | Estado | Proposta |
|---|---|---|---|
| G1 | "Once a week run the news…" | Android corre em fundo (`WeeklyIdeasWorker`); iOS só quando a app abre. | `BGTaskScheduler` no iOS (já em AGENTS.md §23). |
| G2 | "…match that with SMS, Calls, Whatsapp messages etc." | SMS não (decisão); chamadas só sideload; WhatsApp por exportação manual. | Importar exportações de Messenger, Telegram e Signal como a do WhatsApp (`CHAT_IMPORT`). |
| G3 | "Match tastes with news and conversations" | Notícias e temas feitos; conversas só via chats importados e notas. | Usar mais os chats importados (ex.: temas a partir do último chat), respeitando F1. |

## H. Novo layout (14-11-2026)

Plano em [`issues/V1_LAYOUT.md`](issues/V1_LAYOUT.md), mockups em [`mockups/v1-layout/`](mockups/v1-layout/index.html).
N1 separadores Hoje · Pessoas · Círculos · Planos e Definições no cabeçalho · N2 Pessoas · N3 Círculos e Planos ·
N4 cartão "Quando estiveste com…?" · N5 quatro níveis de proximidade · N6 círculos na página da pessoa · N7 a tua foto.

## ⛔ Bloqueados (não correr sem decisão)

- **F13 · Mapas de encontros**: fornecedor de mapas por decidir (MapLibre + OSM é a proposta).
- **D2 na build Play**: a lista com checkboxes só entra na Play depois de confirmar a política de `READ_CONTACTS`.
- **D8 · Firebase Analytics**: em discussão; não remover código.
