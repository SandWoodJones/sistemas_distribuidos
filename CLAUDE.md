# Sistema Inteligente de Reserva de Salas

Final project for Sistemas Distribuídos (BCC, UTFPR-PG, Prof. Dr. Richard Ribeiro).
A multi-threaded TCP server and a Swing client for booking campus meeting/study rooms:
token authentication, CRUD over user accounts (with an ADM profile), CRUD over rooms and
reservations. The graded distributed-systems challenge is the race condition — two clients
booking the same room and slot at once must not both succeed.

Source documents in this directory, in order of authority:

1. <https://pedrosalvatori.github.io/protocolo-reserva-salas/> — the protocol owners' AsyncAPI 3.0
   spec, served from `asyncapi.yaml`, **v2.4.0 (07/10/2026)**. Now **the protocol contract**: it
   postdates the spreadsheet by four versions, carries the owners' own version history and an
   `o_que_mudar` list addressed to implementers, and is where v2.1–v2.4 landed. Wins every conflict.
   Fetch the raw `asyncapi.yaml` rather than reading the rendered page — it holds the exact regexes
   and message texts.
2. `Protocolo de troca de Mensagens.xlsx` (v2.0) — the original contract, still the baseline for
   everything v2.1–v2.4 left alone. Verified against the spec: all 21 ops and every field regex
   except `email` still match.
3. `Requisitos.pdf` — functional and non-functional requirements. Superseded on `email`.
4. `2.1-Projeto_Final-Escolha_do_Tema-SD-BCC.pdf` — theme and minimum requirements.
5. `SD-BCC_EP-1_Itens-Avaliados.pdf` — EP-1 grading rubric.

The owners also ship a conformance harness: `node scripts/testar-servidor.mjs IP PORTA` runs 70+
tests against a running server and reports what is off-contract, from
<https://github.com/pedrosalvatori/protocolo-reserva-salas>. It drives the real jar over a real
socket, which the in-process tests cannot — run it before EP-1.

## Function design principles

- **Honest functions first.** A function is "honest" when everything it reads or writes passes
  through its signature (arguments in, return value out) — this includes functions that mutate
  their inputs deterministically, not just pure ones. A function is "dishonest" if it touches
  state outside its signature (statics, `Instant.now()`, a shared RNG, sockets, JDBC, Swing).
  Dishonesty is infectious: anything calling a dishonest function becomes dishonest too. Keep the
  protocol core (validation, dispatch, every `op` handler, overlap detection) fully honest —
  request plus store in, response out — so it can be unit-tested against the spreadsheet's status
  cells without opening a port or a database. Push dishonesty (socket I/O, the wall clock, token
  generation, JDBC, the GUI) to the edges and inject it: `handle(request, store, clock, tokens)`.
- **Listeners and connection threads are "backward" functions.** A Swing `ActionListener` and the
  per-connection `run()` loop are called on someone else's schedule, not yours. Keep them as thin
  glue: read the widgets or the socket line, call into the honest core, write the result back.
  No validation, no status-code decisions, and no SQL inside a listener body.
- **Design signatures for the caller.** Bundle unwieldy parameter lists into a record, but don't
  demand a whole record when a function only needs two of its fields. Accept the loosest type that
  works (`List<String>` over `ArrayList<String>`, the store interface over the SQLite class). When
  a real invariant must hold (a validated token, a normalized username), encode it in a type so it
  can't be constructed otherwise, rather than re-checking it at every call site or documenting it
  in a comment.
- **One level of abstraction per function.** A function mixing a high-level step ("handle
  create_reservation") with low-level mechanics (regex matching, JDBC result-set walking) is
  bouncing between zoom levels and should be split. A comment introducing a section inside a
  function is a sign the function does more than one thing. Sketch a function's steps as a bullet
  list first; each bullet becomes its own function, so the top-level function reads as a short,
  obvious sequence.

## Failure diagnosis

During the evaluation no source edits are allowed and there is no AI to ask. The same applies to
interop testing against other groups: when a message is rejected, either side may be at fault, and
the only way to find out is what the program already printed. Diagnosis is therefore a feature,
designed in, not something added while debugging.

- **Two messages, never one.** `ProtocolException` carries `wireMessage()` — the exact Portuguese
  text the spreadsheet mandates — and `getMessage()`, which appends the local diagnosis (cause,
  offending field and value, raw line). **Only `wireMessage()` is ever put on the wire.** Collapsing
  them would force a choice between protocol compliance and being able to debug; keeping them apart
  means full detail costs nothing at the interop boundary.
- **Never discard a cause.** Gson reports line, column and JSON path
  (`Expected name at line 1 column 16 path $.op`). A `catch` either rethrows with the cause attached
  or logs at ERROR with the full stack trace. A caught-and-forgotten exception is a bug.
- **Echo the raw line.** Every rejected message quotes the bytes that caused it, escaped and
  truncated via `Codec.snippet`. When another group's client sends a trailing comma, the log shows
  the comma.
- **Every non-2xx says which field.** A `400` that only says "Dados em formato invalido" is useless
  at the bench. Validation carries the failing field name and the offending value into the
  diagnosis, so the wire text stays spec-exact while the log says exactly what to fix.
- **The connection boundary is the loudest place in the codebase.** The always-respond rule forces a
  catch-all there; it is also the last place a bug can hide, so it logs the full stack trace before
  replying `500`.

Logging is SLF4J + Logback, added at M4 when there is a connection to scope it to. `protocol` stays
free of it — honest code throws, it does not log. Thread-per-connection makes **MDC** the practical
equivalent of a tracing span: set `conn`, `peer` and `user` once when the connection opens and every
line logged on that thread carries them. A custom appender feeds the Swing panes, so the one system
serves both the graded message display and a rolling file that survives a failed demo.

## Protocol contract

Encoded from the spreadsheet. These are the rules that are easy to get subtly wrong:

- **Framing**: one message = one valid JSON object on a **single line**, terminated by `\n`, UTF-8.
  Max **8192 bytes including the `\n`**; oversized is discarded and answered
  `400` / `"Mensagem excede o tamanho maximo"`.
- **Every value is a string.** Numbers, booleans and dates too: `"capacity": "12"`, `"available": "true"`.
- Keys are English, `snake_case`, lowercase, unaccented. `op` is always the first key.
- **Response `op` = request `op` + `_response`.** Only exception: a parse failure replies `{"op": "error", ...}`.
- Every response carries at least `op`, `status`, `message`. `status` is the numeric code **as a string**.
- `message` is Portuguese **without accents** and must match the spreadsheet text exactly.
- **One `400` text per op — with one exception since v2.4.** The message tabs give a single 400 cell
  per operation, so a malformed token on `delete_user` goes out as `"Senha em formato invalido"`. The
  wire text can never name the field — which is why the diagnosis must. `update_user` and
  `admin_update_user` now carry a *second* 400, `"Nenhum dado para atualizar"` (rule 2.13); it is not
  a format failure, so it cannot come from the `Request` text and is thrown directly. Every other op
  still has exactly one 400 constant, and there is no per-field message table.
- **Unknown fields are ignored by the receiver.** This is what makes cross-group interop work —
  never reject a message for carrying extra keys.
- On update ops `""` means *don't change*; `null` means `400`. On create ops `""` is invalid → `400`.
- `token` is required on every request **except `register` and `login`**.
- **One active session per user.** Logging in with a live session → `409` / `"Usuario ja possui sessao ativa"`.
- Token idle validity is **30 minutes**. The TCP connection has a separate **300 s inactivity
  timeout** that closes the socket but does *not* invalidate the token. Two independent timers.
- **Login validates in a fixed order** (v2.2, rule 3.12): format `400` → credentials `401` → active
  session `409`. The `409` only appears once email and password are both correct, so it cannot reveal
  which accounts exist.
- **Checking for a live session and writing the token are one critical section** (v2.2, rule 3.11):
  two simultaneous correct logins give one `200` and one `409`. `SqliteStore.createSession` is
  `synchronized` and `sessions.user_id` is `UNIQUE` — enforced twice over.
- **A wrong confirmation password on `delete_user` is `403` / `"Senha incorreta"`** (v2.1, rule 3.9),
  not `401`, and the session survives. The old `401` made the client discard a token the server still
  held, blocking the next login with `409` for up to 30 minutes.
- **An update must change something** (v2.4, rule 2.13): `update_user` with `user` and `password` both
  empty or absent — or `admin_update_user` with all three — is `400` / `"Nenhum dado para atualizar"`,
  not a no-op `200`. A single `""` still means *don't change*.
- **Removing an account frees the name** (v2.3, rule 3.13): `delete_user` erases the registration, the
  session and *all* the user's reservations, past and future, so a fresh `register` with the same
  `user` and `email` answers `201`.
- **An unanswered request is not completed** (v2.2, rules 1.11 and 3.10). If the socket was closed by
  the 300 s timeout, the client reopens it and resends the same request with the same token, and
  discards its own token only once a `logout_response` arrives (`200`, or `401` if it was already
  dead). `ProtocolClient.ask` resends exactly once and deliberately does *not* resend on a read
  timeout, where the server may still answer and a resend would duplicate the operation.
- The server **never closes a connection without answering** a fully received request.
- The author of a request is identified **by token, never by a `user` field in the body**.
  `target_user` is only ever the object of an admin operation.
- Reservation overlap: conflict iff `new_start < existing_end && new_end > existing_start`.
  A booking starting exactly when another ends is **not** a conflict.
- **Concurrency golden rule**: the availability check and the reservation write happen in the same
  critical section, under a lock keyed by `room_id`. `check_availability` is purely informative —
  a `"true"` reserves nothing and guarantees nothing about the next request.

### Field formats (Dicionário tab)

| Field | Regex |
|---|---|
| `user` | `^[a-z]{1,30}$` |
| `password` | `^[A-Za-z0-9]{1,20}$` |
| `email` | `^[a-z0-9._-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$` |
| `token` | `^[a-f0-9]{64}$` |
| `op` | `^[a-z_]{3,30}$` |
| `status` | `^[0-9]{3}$` |
| `role` | `^(user\|admin)$` |
| `room_id`, `reservation_id` | `^[0-9]{1,10}$` |
| `name` | `^[A-Za-z0-9 -]{1,30}$` |
| `capacity` | `^[1-9][0-9]{0,3}$` |
| `location` | `^[A-Za-z0-9 ,.-]{1,60}$` |
| `room_status` | `^(active\|inactive)$` |
| `date` | `^[0-9]{4}-[0-9]{2}-[0-9]{2}$` |
| `start_time`, `end_time` | `^([01][0-9]\|2[0-3]):[0-5][0-9]$` |
| `available` | `^(true\|false)$` |
| `created_at` | `^[0-9]{4}-[0-9]{2}-[0-9]{2} ([01][0-9]\|2[0-3]):[0-5][0-9]:[0-5][0-9]$` |

Times are local to the server (America/Sao_Paulo), 24 h. `email` is the one regex v2.1 changed:
multi-level domains, hyphens in the domain, `_`/`-` before the `@`, and a TLD of 2+ letters.

### Known discrepancies between the documents

The AsyncAPI spec v2.4 is the newest document and wins; the spreadsheet is the v2.0 baseline and
`Requisitos.pdf` the oldest. Items 1–5 below are the PDF disagreeing with both. Confirm with Pedro
Silveira and Daniel Jesus (the protocol owners) before EP-1:

1. **`login` keys on `email`, not `user`.** `Requisitos.pdf` describes login against the username;
   the spreadsheet defines `{"op":"login","email":...,"password":...}` and its 400/401 messages
   read "Email ou senha…".
2. **`user` is letters-only.** `Requisitos.pdf` says `^[a-z0-9]{1,30}$`; the Dicionário tab says
   `^[a-z]{1,30}$`, notes "SEM NUMERO, SO LETRAS, SEM ESPACO", and every v2.0 example uses
   `"joao"` / `"maria"`. The AsyncAPI spec sides with the dictionary, so it is two documents to one.
3. **Nothing in the protocol creates the first admin.** Every `register` is born `role: "user"`,
   and promotion needs `admin_update_user`, which needs an admin token. The server therefore seeds
   one admin when the schema is first created: user `admin`, password `admin123`,
   email `admin@utfpr.edu.br`.
4. **Two fields have no dictionary row.** `scope` (`list_reservations`) and `min_capacity`
   (`list_rooms`) appear only in the message tabs. `Formats.SCOPE` is inferred as `^(mine|all)$`
   from the prose, and `min_capacity` reuses `Formats.CAPACITY`. The AsyncAPI spec resolves both
   the same way, which upgrades them from guesses to agreed readings.
5. **`email` on `update_user` is a `400`, which collides with "unknown fields are ignored".**
   RNF 5.c makes `email` immutable and the spreadsheet says the key's mere presence is a `400`.
   Another group's client that round-trips the whole user object is then rejected by us and accepted
   everywhere else. Built as specified; the interop risk is real and theirs to resolve.

Two rules the documents contradict each other on, decided here so the handlers are consistent.
Both are the AsyncAPI spec's own open pendências (1 and 8), so they still need a ruling before
interop testing:

6. **An absent `token` is `401`, a malformed one is `400`.** Rule 2.10 sends any missing required
   field to `400`; the *Códigos de Status* tab lists "token ausente" under `401`. Both are satisfied
   by splitting on presence, and the `400` cells say *formato*, which presumes a value. So
   `Caller.authenticate` reads the token with `Request.optional` — absent or `""` throws `401`
   / `"Token invalido ou expirado"`, present-but-unmatching falls through to the op's own `400`.
7. **Uppercase in `user` or `email` is a `400`, never silently lowercased.** Rule 2.12 says both are
   stored in lowercase, but their regexes reject uppercase outright. Rule 2.10 makes a regex failure
   a `400`, and 2.12 then holds trivially, since only lowercase can ever get in.

Resolved upstream in v2.1, recorded so they are not re-litigated:

- **`delete_user` with the wrong password** (old discrepancy 7): the sheet left `message` undefined
  and we sent the token `401` text. v2.1 defines `403` / `"Senha incorreta"` with the session intact —
  for exactly the reason this file flagged it.
- **The `email` regex** (old discrepancy 9): the 2026-10-06 `Requisitos.pdf` widened `{1,2}` to
  `{1,4}` against the Dicionário's `{1,2}`. v2.1 settles it more generously still. The PDF's `^^`
  remains a typo — we anchor with one `^`.

## Architecture

Three packages, one level deep. The boundary is load-bearing, not decorative: `protocol` compiles
without reference to sockets, JDBC or Swing, and `server` compiles without reference to `client`.
That is what lets the concurrency test hammer the dispatcher with twenty threads without binding
a port.

    src/main/java/reservas/
    ├── protocol/   Op, Role, Status, WireValue(s), Fields (key names),
    │               Formats (regexes), Codec, Request (validated reads),
    │               Responses, ProtocolException
    ├── server/     Dispatcher + Handler, AuthHandlers, AccountHandlers,
    │               Caller (token to author), User, Session, TokenGenerator,
    │               Database + Schema + SqliteStore, Bootstrap, RoomLocks,
    │               ServerSocketLoop, ServerWindow, ServerApp
    └── client/     ProtocolClient, Session, ClientWindow, ClientApp

    src/test/java/reservas/   mirrors the above

## Build and run

    nix-shell                 # or: direnv allow, once
    mvn test
    mvn compile exec:java -Dexec.mainClass=reservas.server.ServerApp
    mvn compile exec:java -Dexec.mainClass=reservas.client.ClientApp

`exec:java` resolves by plugin prefix without being declared in the pom; the "version not
specified" warning is harmless for dev runs.

`mvn package` shades two self-contained jars for handing in — they need only a JRE 17+ on the far
side, no Maven and no Nix, and the bundled SQLite natives cover Windows, Linux and macOS:

    mvn package
    java -jar target/servidor.jar 5000    # porta opcional, 5000 por omissão
    java -jar target/cliente.jar

Run `servidor.jar` from a terminal, never by double-clicking: without a console the
`recebido`/`enviado` lines go nowhere and `OBS. 3` fails. Do not ship `reservas.db` — the admin is
seeded only when the schema is first created, so a fresh machine must build its own.

## Milestones

M0 environment and docs ✅ · M1 protocol core, zero I/O ✅ · M2 persistence on SQLite ✅ ·
M3 auth + own-account handlers behind a dispatcher ✅ · M4 TCP transport + logging ✅ ·
M5 server GUI — **dropped**, see below · M6 client GUI = **EP-1 complete** ·
M7 rooms + admin ops · M8 reservations + the room lock · M9 interop hardening.

M5 is dropped because Prof. Ribeiro confirmed the server needs no GUI for EP-1: the terminal
satisfies `OBS. 3` (it prints every `recebido`/`enviado` line) and `OBS. 4` (the port comes from
`-Dexec.args`). If the final delivery revisits this, the plan was a `LogPaneAppender` extending
`AppenderBase<ILoggingEvent>` with a static subscribe, so one Logback config feeds the rolling
file and the pane alike.

There is no hand-written in-memory store and no `Store` interface. `jdbc:sqlite::memory:` gives a
fresh, fully-constrained database per test in a few milliseconds, so tests run against the code that
ships; a second implementation would only let a bug hide in the difference between them. `SqliteStore`
is concrete — extracting an interface later is mechanical, so deferring it costs nothing.

## Grading constraints (EP-1)

EP-1 is worth **2,0 points**, split 1,0 client / 1,0 server over exactly the six auth and
own-account operations — no rooms, no reservations, no `admin_*`. Those belong to the final
delivery, not to this one.

| operation | client | server |
|---|---|---|
| `register` (C) | 0,2 | 0,2 |
| `read_user` (R) | 0,2 | 0,2 |
| `login` | 0,2 | 0,2 |
| `logout` | 0,1 | 0,1 |
| `update_user` (U) | 0,2 | 0,2 |
| `delete_user` (D) | 0,1 | 0,1 |

The `OBS.` list on the rubric is not scored — those are **preconditions**, each phrased
"somente será avaliado…". Failing one means the work is not evaluated at all:

- The protocol document must be delivered **before** the evaluation, and the source sent before it.
- **Both** client and server must show the messages they send and receive (`OBS. 3`). The server's
  terminal output counts — confirmed with Prof. Ribeiro, no server GUI needed for EP-1.
- The client needs fields for **IP and port** (`OBS. 5`). `OBS. 4` asks the same of the server's
  port, and `-Dexec.args=<porta>` was accepted for it.
- Every client must interoperate with every other group's server, and vice-versa — which is why
  unknown fields are ignored and message texts match the spreadsheet byte for byte.
- **No source edits during the evaluation**, not even to fix a critical crash. Rehearse on a
  clean checkout.
- **0,5 point per day late** if the source misses the Entrega Parcial. ⚠ The rubric never states
  that date — still unconfirmed, and it is the only open item with a deadline attached.

Reading the PDFs needs poppler, which is not in `shell.nix` on purpose:
`nix-shell -p poppler-utils --run 'pdftotext -layout <file>.pdf -'`.
