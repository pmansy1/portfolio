# Project conventions

<!-- The agent reads this at the start of every session. Keep it short and current. -->

## What this repository is
Patrick Manswell's CPSC 415 portfolio: a profile site with a chatbot that answers
"should we work together?" only from a profile he wrote, and refuses to fabricate.
See [`intent/portfolio.md`](./intent/portfolio.md) and [`spec.md`](./spec.md).

## Commands
```bash
# Chat service and tool loop (Java, from api/)
cd api && ./mvnw test                  # no-key tests; billed eval skipped without a key
cd api && ./mvnw spring-boot:run       # http://localhost:8080/api/chat

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

- Week 8: the chatbot's tool loop lives in the Java service
  (api/src/main/java/edu/trincoll/manswell/portfolio/tools/), because the answer
  function the typed and voice paths share is there; tools/CHECKS.md holds the
  eval and findings. Stages assigned: intent, spec, plan, tests. Commit to main.
- The model never runs anything. Every tool request is validated and run (or
  refused) by our code; every problem goes back to the model as a result, not
  an exception. Tools with side effects are never run on the model's request
  alone. The loop has a step cap.
- No model call inside a test that runs without a key; the billed eval is
  separate and skipped when OPENROUTER_API_KEY is unset.

## Common mistakes
- Never edit `expected.json` to make a transcript pass. If the key looks wrong, say so and stop.
- Never save a non-audio reply (a JSON error body) as an audio file.
- Never put a model call in a no-key test.
