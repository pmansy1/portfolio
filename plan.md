# Plan

## Week 7: spoken interface

Implements the "Week 7: spoken interface" section of [`spec.md`](./spec.md) (components 7–10, behaviors 17–24).

### Files under `voice/`
| File | Component / behavior |
|---|---|
| `check.py` | 9: `words`, `wer`, `compare` (17, 19, 20) |
| `answer_text.py` | 21: `for_speech`, which strips markdown and keeps at most three sentences |
| `transcribe.py` | 7: one call per clip, `--set` runs the checker per clip |
| `speak.py` | 8: refuses non-audio replies (18), estimated cost when the record isn't ready |
| `server.py`, `index.html` | 10: `/api/voice`, `/api/ask`, 10 MB / 415 caps (22, 23) |
| `tests/test_check.py`, `tests/test_answer_text.py`, `tests/test_speak.py`, `tests/test_server_caps.py` | no-key tests |
| `tests/test_eval.py` | billed eval, skipped without a key (24) |
| `test_browser.py` | Playwright check against the running server (22) |
| `clips/` | `fixtures/clip-01..03.wav` with the course's `expected.json` + `own-01.wav`, `own-02.wav` (my voice), `expected.json` |
| `CHECKS.md` | every measured number, failures included |

### Order of work
1. Tests for the checker and for `for_speech`, run red. Then the code, run green. Commit.
2. Transcriber and speaker, adapted from `examples/voice-loop/` (request shapes only). No-key tests for the non-audio refusal (behavior 18) with a fake client.
3. **I** record two clips (5–10 s each) and write `clips/expected.json` **before** any model hears them. The agent may not write or edit that file.
4. Billed run: both transcribers on all five clips, two speech calls on the answer to my first clip. Record in `CHECKS.md`. Commit.
5. Server route and page, no-key tests for the caps, then the browser check and one real microphone turn (the **One turn** section). Commit, tag `week07-submitted`.

### Risks
- **My name is the likeliest miss.** "Manswell" can come back as "Mansell", "Man's well", or "Maxwell". The WER is 1/13 and passes the tolerance, so only `must_contain` notices (behavior 20, test `test_names_that_must_survive_are_checked_separately`). What the chatbot does with a misheard name: the transcript is shown on the page ("You said: …"), so the visitor sees the mishearing. The answer still comes only from the profile, so a wrong name can't make it claim anything new. At worst it answers a slightly different question.
- **The chatbot isn't mine yet.** The answer step uses the course example's profile, so answers this week are about "Sam Rivera". Stated in `CHECKS.md`. Swapping in mine is a two-line change once `/api/chat` exists.
- **A visitor holding the button for a minute.** Whisper bills per second (≈ $0.0002 for 60 s), and the chat and speech costs depend on the answer, not the audio, so it's the transcription that grows. The 10 MB cap bounds the upload (about 10 minutes of webm/opus). A tighter cap, about 30 s of audio, belongs in the deployed route with the key's credit limit (hosting decision, before Nov 30).
- **A format the model rejects.** The server answers 415 for an unknown `Content-Type` before any billed call. If the provider rejects a known type anyway, the provider's message goes back as 502 without the key, and nothing is spoken.
- **Hidden reasoning eats `max_tokens`.** minimax-m3 can return an empty answer at a low limit. `max_tokens=1500`, and an empty answer is said as a failure, not spoken as silence.

### Proof
**Runs without a key (free, under a second, `python3 -m pytest tests -v --ignore=tests/test_eval.py` from `voice/`; without the ignore, a set key also runs the billed eval):**
- Checker: perfect transcript = 0; one substitution in thirteen = 1/13; `""` and `None` score 1.0 and are reported `empty`, not raised; a name in `must_contain` is reported missing even within tolerance; "four fifteen" misses "415"; spacing and case don't count. *Proves* behaviors 17, 19, 20 without trusting any model.
- `for_speech`: markdown stripped, at most three sentences, empty stays empty. *Proves* 21.
- Speaker refuses a JSON body served as the reply and writes no file (fake client). *Proves* 18.
- Server: 415 for an unknown type, 413 over 10 MB, empty transcript → "I didn't catch that." with no chat call (fakes). *Proves* 17, 23.

**Billed eval (needs `OPENROUTER_API_KEY`, skipped otherwise):**
- Five clips × two transcription models = 10 calls. Whisper ≈ $0.00002 per clip; Scribe ≈ $0.0005 per clip, so ≈ **$0.003**. *Proves* each transcriber meets the tolerance and keeps the named words, or records where it doesn't.
- Two speech calls on one ≈200-character answer: MAI ≈ $0.003, ElevenLabs ≈ $0.008, so ≈ **$0.011**.
- Browser check: one voice turn (STT + chat + TTS) ≈ **$0.005**. One real microphone turn ≈ **$0.005**.
- **Total for the week ≈ $0.025**, well inside the $5 ceiling.

### Questions asked of this plan
- *"If the transcriber turns my name into a different word, what does the chatbot do with the question, and which test notices?"* Answered under Risks. `test_names_that_must_survive_are_checked_separately` and the per-clip `must_contain` in the billed run notice it. The page shows the transcript so a visitor notices too.
- *"What does one voice turn cost on the class models, and what happens if a visitor holds the button down for a minute?"* About half a cent; see Risks and Proof. The real number goes in `CHECKS.md` → **One turn**, then into the hosting decision.
- *"What does the server do with a recording in a format the model rejects?"* 415 before billing for unknown types, 502 with the provider's message for a known type the model refuses. Added to Risks.

**Corrections to the draft:** the fixture paths, the missing `test_speak.py`, and the no-key command (which ran the billed eval when the key was set).

**Approved by:** Patrick Manswell, November 11, 2026

## Profile, grounding prompt, and chat service (catch-up for Oct 19 / Nov 2)

Implements `spec.md` components 1–4 and behaviors 1–8. Week 8's tool attaches to this service.

### Files
| File | Component / behavior |
|---|---|
| `profile/profile.json` | 1, 2: facts drafted from the three repos' READMEs and git history; **every annotation, the "not yet" reasons, `learning`, and Cloud-Native `my_role` are written or confirmed by me** |
| `prompts/system.md` | 3: the nine rules, with `{{PROFILE}}` where the JSON goes |
| `api/` (Maven, Spring Boot 3, Java 21) | 4: `POST /api/chat`, `GET /api/profile` |
| `api/src/test/.../ProfileSchemaTest.java` | behavior 1: the five schema rules, plus "no `TODO(` left" |
| `api/src/test/.../PromptBuilderTest.java` | behavior 2: profile between `<profile>` tags, rules present |
| `api/src/test/.../ChatControllerTest.java` | behaviors 3–6: MockMvc with a stubbed model client, no network |
| `api/src/test/.../OpenRouterClientTest.java` | behavior 7: request body has `max_tokens` ≤ 400, `temperature` ≤ 0.3 |

### Order of work
1. Profile skeleton with `TODO(Patrick)` markers. `ProfileSchemaTest` fails until they're gone, on purpose.
2. Grounding prompt.
3. Spring Boot project from start.spring.io (Web, Validation, Test) with the Maven wrapper, since Maven isn't installed.
4. Tests first for prompt builder, controller, and client, run red. Then the code, run green.
5. One real question through `/api/chat` on gpt-4o-mini to prove the wiring; the adversarial set comes later (Nov 30 milestone).

### Risks
- **The agent's draft facts are wrong.** Every fact in the profile came from a repo (README, `package.json`, tests, `git log`), and I check `my_role` against my memory of the team.
- **A profile with TODOs gets deployed.** `ProfileSchemaTest` fails on any `TODO(`, so `mvn test` is red until the profile is mine.
- **CORS and Render cold starts** are deployment concerns, so they're deferred to the hosting milestone. `ALLOWED_ORIGIN` defaults to `http://localhost:5173`.

### Proof
- No key: `./mvnw test` covers behaviors 1–8 with a stubbed model client. Free.
- Billed: one `curl` to `/api/chat` with a real key, ≈ $0.0007 on gpt-4o-mini.

**Approved by:** Patrick Manswell, November 11, 2026 (approved after the code was written; see the commit history)

## Week 8: one tool

Implements the "Week 8: one tool" section of `spec.md` (components 11–12, behaviors 25–33).

### Files
| File | Component / behavior |
|---|---|
| `api/.../tools/Tool.java`, `ToolRegistry.java` | the schema list, `validate` and `run` (25–27, 29) |
| `api/.../tools/RecentCommitsTool.java` | 11, 30 |
| `api/.../tools/ToolLoop.java` | the loop, the cap, the trace (28, 32, 33) |
| `api/.../tools/ClaimChecker.java` | 31 |
| `api/.../OpenRouterClient.java` | gains a tool-capable call; the per-model `max_tokens` table |
| `api/.../ChatController.java` | `/api/chat` answers through `ToolLoop`; the response gains `trace`, `stoppedBy`, `pending`, `check` |
| `api/src/test/.../tools/ToolLoopTest.java` | the six misuse cases with a scripted model |
| `api/src/test/.../tools/ToolRegistryTest.java`, `RecentCommitsToolTest.java`, `ClaimCheckerTest.java` | validation, GitHub errors (mock HTTP server), claim check |
| `api/src/test/.../tools/ToolEvalIT.java` | billed eval, skipped without `OPENROUTER_API_KEY` |
| `tools/CHECKS.md` | eval table, totals, findings, linter |

### Order of work
1. Tests for the registry and validation, then the six loop misuse tests with a scripted `ChatModel`. Run them red, write the code, run them green, commit.
2. `RecentCommitsTool` with a mock GitHub server (404, 403, success, timeout), test first.
3. `ClaimChecker`, test first.
4. Wire `ToolLoop` into `ChatController` (update `ChatControllerTest` first). The voice route needs no change: it already posts to `/api/chat`.
5. Billed eval: five questions × two models. Record in `tools/CHECKS.md`. Then findings, tag `week08-submitted`.

### Risks
- **The model never calls the tool.** The description is an instruction ("call this before saying anything about recent work"), not a label. The eval's question 1 notices.
- **The model calls the tool on every question,** including ones the profile answers. That costs an extra chat call each time, and eval question 3 measures it.
- **Team commits credited to me.** The result carries `author`. The claim check can't tell "Patrick committed X" from "the repository got X", so this goes under "What the check cannot catch" and gets a prompt rule.
- **minimax-m3's hidden reasoning** can use up `max_tokens` and return nothing. The per-model token table gives it 1500, and an empty answer becomes the fallback sentence.
- **GitHub's rate limit (60/hour)** in the eval. 10 runs fit; `GITHUB_TOKEN` is read if set.

### Proof
**No key (free, `./mvnw test`):**
- Single shot: a scripted tool call gets its result back as a `tool` message with the same `tool_call_id`, and the next reply is the answer. *Proves* the loop's message shapes.
- Bad arguments (wrong name, wrong type, outside `enum`, not JSON): an error result, the loop continues, the model's second reply is used. *Proves* 26.
- Unknown tool: an error result listing `recent_commits`. *Proves* 27.
- Endless caller: a model that always asks is stopped at 4, and the answer is the program's sentence. *Proves* 28.
- Side effect held: a test-only side-effect tool ends up in `pending`, and its counter stays 0. *Proves* 29.
- Unsupported claim: an answer naming a SHA no result contained is flagged. *Proves* 31.
- GitHub 404, 403, and success against a mock server. *Proves* 30.

**Billed eval (`OPENROUTER_API_KEY` set; ≈ 10 GitHub calls, under the limit):**
- Five questions × 2 models. gpt-4o-mini: about 3,000 prompt tokens per step, so ≈ $0.0005 per step, ≈ $0.001 for a tool question. m3: about 2× the price, plus reasoning tokens, ≈ $0.003 per tool question. **Total ≈ $0.02.** *Proves* whether each model calls the tool when it should and not when it shouldn't, and whether its answers pass the claim check.

### Questions asked of this plan
- *"If the model asks for the tool with the wrong argument name, what exactly goes back to it, and what does the second test assert?"* The tool message content is `{"error": "unexpected argument 'repository'; expected repo, count"}`. The test asserts that the second request to the model contains that `tool` message with the same `tool_call_id`, and that the final answer is the model's second reply.
- *"What stops a model that asks for the same tool ten times, and what does the visitor see when it does?"* The step cap of 4 in `ToolLoop`. The visitor sees "I couldn't finish looking that up. Please try a narrower question.", and the trace shows the four calls.
- *"If the model answers about a repository the tool said does not exist, which line of code notices?"* `ClaimChecker` flags an answer that describes recent commits for a repository whose only tool result was an error (behavior 31). The test is `unsupportedClaimIsFlagged`.

**Approved by:** Patrick Manswell, November 11, 2026
