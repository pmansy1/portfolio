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
