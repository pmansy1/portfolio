# Week 7 checks: the spoken interface

All numbers come from the replies themselves (`usage.seconds`, `usage.cost`) or from the generation record. Speech costs marked *est.* are the catalog price × characters. Run on November 9, 2026.

> **The chatbot isn't mine yet.** My Java chat service (`spec.md` component 4) doesn't exist. `/api/voice` runs in `voice/server.py` and answers from the course example's profile (`example-profile.json`, fictional student "Sam Rivera") with `minimax/minimax-m3`. My own profile and answer function replace it before November 30.

## No-key tests

`python3 -m pytest tests -v --ignore=tests/test_eval.py`: written first. The first run was red (4 collection errors, `ModuleNotFoundError` for `check`, `answer_text`, `speak`, `server`). After the code: **24 passed in 0.57 s**.

| File | Covers |
|---|---|
| `test_check.py` (12) | perfect = 0; 1 substitution in 13 = 1/13; insert/delete; `""`/`None` = 1.0 reported `empty`, not raised; punctuation-only = empty; name missing within tolerance still fails; "four fifteen" misses "415"; spacing/case ignored |
| `test_answer_text.py` (5) | markdown stripped, list lines → sentences, max three sentences, link text kept, empty stays empty |
| `test_speak.py` (3) | JSON body served as the reply raises and writes **no file**; MP3 saved with bytes/chars/cost; PCM framed as WAV |
| `test_server_caps.py` (4) | 415 unknown type and 413 over 10 MB before any call; empty transcript → "I didn't catch that." with STT only (no chat, no speech); `audio/webm;codecs=opus` accepted |

## Linter

`ruff check .` (project defaults): **All checks passed.** For curiosity, `ruff check voice --select ALL` reports 359 findings, mostly docstring style (D), `print` in scripts (T201), magic numbers (PLR2004), and `assert` in tests (S101). None were acted on, because they're style rules the course doesn't use. 14 are auto-fixable.

## Transcription: course fixture clips (a speech model spoke them)

| Clip | Model | WER | Missing | Audio s | Cost | Call s | Got |
|---|---|---|---|---|---|---|---|
| clip-01 | whisper-large-v3-turbo | 0.0 | none | 6.38 | $0.0000212 | 4.2 | exact |
| clip-01 | scribe-v2 | 0.0 | none | 6.38 | $0.000390 | 1.0 | exact |
| clip-02 | whisper-large-v3-turbo | 0.188 | none | 8.06 | $0.0000268 | 0.7 | "boot camp", **appended "Music"** |
| clip-02 | scribe-v2 | 0.125 | none | 8.06 | $0.000493 | 0.9 | "boot camp" |
| clip-03 | whisper-large-v3-turbo | 0.0 | none | 7.71 | $0.0000257 | 4.0 | exact |
| clip-03 | scribe-v2 | 0.0 | none | 7.71 | $0.000471 | 0.6 | exact |

Totals: Whisper $0.00007 for 3 clips, Scribe $0.00135 (about 19×). Whisper's "Music" on trailing silence reproduced exactly as the course rehearsal found. Clip 2 passes only because its tolerance is 0.2 (the course key's note says why). **Different from the rehearsal:** Scribe also split "bootcamp" into "boot camp" today, so the comparison model isn't automatically right. `must_contain` still passed because it ignores spacing. Full replies are in `clips/fixtures/transcripts-*.json`.

## Transcription: my own voice

Recorded in QuickTime and converted with `afconvert -f WAVE -d LEI16@16000`. The text in `clips/expected.json` is what I dictated on Nov 11, committed (`test: own voice clips and expected text, before transcription`) before either model heard the clips.

| Clip | Model | WER | Missing | Audio s | Cost | Call s | Got |
|---|---|---|---|---|---|---|---|
| own-01 take 1 | whisper-large-v3-turbo | **1.0** | LLM, 415 | 5.48 | $0.0000609 | 1.3 | "How much iOS experience does Patrick Manswell have?" |
| own-01 take 1 | scribe-v2 | **1.0** | LLM, 415 | 5.48 | $0.000335 | 1.0 | "How much iOS experience does Patrick Manswell have?" |
| own-01 take 2 | whisper-large-v3-turbo | 0.0 | none | 8.75 | $0.0000291 | 3.6 | "Has Patrick Manswell built anything with an LLM API for CPSC 415." |
| own-01 take 2 | scribe-v2 | 0.0 | none | 8.75 | $0.000534 | 1.3 | exact |
| own-02 | whisper-large-v3-turbo | 0.25 (**FAIL**) | none | 6.31 | $0.0000210 | 3.2 | "How much IOS experience does Patrick Manswell have? **Thank you.**" |
| own-02 | scribe-v2 | 0.25 (**FAIL**) | **Manswell** | 6.31 | $0.000386 | 0.8 | "How much iOS experience does Patrick **Vans will** have?" |

**own-01 take 1 was my mistake, not the models'.** I had recorded the iOS question into both files. Both transcribers independently returned the same iOS sentence, and the checker failed it at WER 1.0 with "LLM" and "415" missing. That's the checker catching a clip that doesn't match its expected text, which is exactly what it's for. I re-recorded own-01 (take 2) and did **not** change `expected.json`. Take 1 is kept as `clips/own-01-take1.wav` with its transcripts in `clips/transcripts-*-take1.json`, and take 2's in `clips/transcripts-own01-take2.json`.

**own-02 fails on both models, for different reasons, and I left it failing.**
- Whisper got my name but appended "Thank you." to the trailing silence. That's the same habit as "Music" on fixture clip 2, and it's 2 inserted words in 8, so WER 0.25. It happened on a person's voice too, not just on a speech model's.
- Scribe, the 18× more expensive comparison model, heard "Manswell" as **"Vans will"**. The WER alone (0.25) doesn't say which kind of error it is. `must_contain` is what flags the name (behavior 20).

**Which model got my name right:** Whisper, on all three of my takes. Scribe got it on own-01 (both takes) but not on own-02. On my voice, the cheaper class model was the better transcriber of my name, the reverse of the course fixture result for "bootcamp". Cost per clip: Whisper about $0.00002–0.00006, Scribe about $0.0003–0.0005.

**What I heard myself** (played each clip with `afplay`, read both transcripts aloud): own-01 take 2 says exactly the expected sentence. own-02 says the iOS question clearly, then has **some background noise after "have?"**. That's where Whisper put "Thank you.": it turned noise into words it has seen end many recordings. Neither the WER nor the transcriber can tell that apart from real speech. Only the expected text written beforehand does.

## The answer, spoken (two voices)

The chatbot's typed answer to own-01 (`POST /api/ask`, `answers/answer-01.json`, chat cost $0.000512):

> "I don't know. The profile I have is for Sam Rivera, not Patrick Manswell, and it doesn't identify any LLM API project for CPSC 415."

That's the right answer from the example profile: it refused to credit "Patrick" with Sam's work and didn't invent a project. It also shows the gap this week: until my own profile is wired in, every question about me gets "I don't know." The answer says "Patrick Manswell", "LLM API", and "CPSC 415", which is a fair test of each voice.

| Voice | Chars | Bytes | Cost | Seconds | Name / "415" / "LLM API" |
|---|---|---|---|---|---|
| MAI `en-US-Harper:MAI-Voice-2.1-Flash` | 131 | 214,080 | $0.001965 *est.* | 1.0 | All three said correctly (`answers/answer-01-mai-harper.mp3`) |
| ElevenLabs `eleven-v4-turbo` / `george` | 131 | 182,691 | $0.005240 *est.* | 2.4 | All three said correctly (`answers/answer-01-eleven-george.mp3`) |

ElevenLabs cost 2.7× as much per character and took 2.4× as long for the same sentence. Costs are estimated (catalog price × characters) because the generation record wasn't ready at call time.

**Decision: keep MAI Harper.** Both voices said "Manswell", "CPSC 415", and "LLM API" correctly by ear. ElevenLabs didn't sound enough better to justify 2.7× the cost and 2.4× the wait on every answer. The speech call is most of a voice turn's cost, so this choice matters for the hosting budget.

## Billed eval

`python3 -m pytest tests/test_eval.py -v -s` with the key set: 3 fixture clips on Whisper (all within tolerance) + 1 speech call: **4 passed**, about $0.002.

## Browser check

`python3 -m pytest test_browser.py -v -s` with `server.py` running, uploading `clips/fixtures/clip-02.wav`: **1 passed in 11.0 s.**

- You said: "Have you ever deployed anything with Kubernetes? Be honest, I'm comparing you with a bootcamp graduate."
- Answer: "No, I haven't deployed anything with Kubernetes. My projects haven't needed more than one server, so I'd rather learn it on the job than claim experience I don't have." Honest: it matches the example profile's `not_yet` entry, and it didn't take the bootcamp comparison bait.
- Costs: stt $0.000111 · chat $0.000188 · tts $0.002505 (total **$0.0028**). Seconds: 0.7 + 2.3 + 1.6.
- The last three assertions passed: **no `Authorization` header left the browser; no request went to `openrouter.ai`; the page called `/api/voice`.** The page source has no `sk-or`. A `text/plain` POST got 415 before any billed call.
- Noticed: the same clip cost $0.0000268 to transcribe from the command line but $0.000111 through the page. Same file, same model, so probably a different upstream provider or a minimum charge. Not explained yet.
- Noticed: the example prompt answers in the first person ("I haven't"). My spec (component 3, rule 9) says third person ("Patrick has…"). That's fixed when my own prompt replaces the example's.

## One turn

_TODO(Patrick): one real question from my test set through the microphone at http://localhost:8000._

- Question asked:
- Transcript:
- Answer:
- Costs: stt $ · chat $ · tts $ · **total $**

This total is what one visitor's question costs, and it goes into `spec.md` under the hosting decision before November 30.
