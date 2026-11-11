# Project conventions

<!-- The agent reads this at the start of every session. Keep it short and current. -->

## What this repository is
Patrick Manswell's CPSC 415 portfolio: a profile site with a chatbot that answers
"should we work together?" only from a profile he wrote, and refuses to fabricate.
See [`intent/portfolio.md`](./intent/portfolio.md) and [`spec.md`](./spec.md).

## Commands
```bash
# Week 7 spoken interface (Python, from voice/)
cd voice
python3 -m pytest tests -v --ignore=tests/test_eval.py   # no-key tests, under a second
python3 -m pytest tests/test_eval.py -v -s        # billed; skipped without OPENROUTER_API_KEY
python3 transcribe.py --set clips/expected.json   # own clips, class transcriber
python3 server.py                                 # http://localhost:8000
python3 -m pytest test_browser.py -v              # with server.py running; three billed calls
ruff check .
```

## Working rules

- Libraries and build tools are allowed. Name each dependency in spec.md
  under Dependencies, with the reason, before installing it; a hook blocks
  installs that spec.md does not list.
- No API key in the repository. Keys come from `OPENROUTER_API_KEY` in the
  environment locally and from the host's secret store when deployed.
- Week 7: the spoken interface lives in voice/. Stages assigned: intent, spec,
  plan, tests. Commit to main.
- Transcription and speech calls are billed. One transcription per clip, one
  speech call per answer, never a model call inside a loop of tests. Fixture
  clips are WAV; expected.json is written by a person before the model hears it.
- The spoken answer is the chatbot's answer: the refuse-to-fabricate rule
  applies to what is said aloud exactly as to what is typed.

## Common mistakes
- Never edit `expected.json` to make a transcript pass. If the key looks wrong, say so and stop.
- Never save a non-audio reply (a JSON error body) as an audio file.
- Never put a model call in a no-key test.
