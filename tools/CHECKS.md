# Week 8 checks: one real tool

The tool is `recent_commits(repo, count)`, which reads the latest commits of one of my profile's four repositories from GitHub's public API. The loop, registry, claim check, and tool are Java, in `api/src/main/java/edu/trincoll/manswell/portfolio/tools/`. They live there because `/api/chat` is the answer function that the typed path and the voice route (which posts to `/api/chat`) share. Run on November 11, 2026. Raw results, with full traces: `tools/eval-2026-11-11.json`.

## No-key tests

`cd api && ./mvnw test`: **58 passed, 0 failed**. No key or network needed; GitHub and OpenRouter are mock servers or scripted fakes. The commit history shows each group failing before its code:

| Red commit | Green commit |
|---|---|
| `test: tool registry validation and the six loop misuse cases (red)` (didn't compile: no classes) | `feat: tool registry, loop with step cap, and claim check` (the first run had 2 failures, fixed by checking unknown argument names before missing ones) |
| `test: recent_commits against a mock GitHub, and the claim check on repositories (red)` | `feat: recent_commits tool for my profile's repositories` |
| `test: /api/chat answers through the tool loop with its trace; tool-capable model call (red)` | `feat: /api/chat answers through the tool loop with its trace; prompt rule for recent_commits` |

## Eval: five questions on two models

`./mvnw test -Dtest=ToolEvalIT -Dsurefire.failIfNoSpecifiedTests=false` (billed; skipped without a key, and not part of `./mvnw test`). "Code" is the eval's automatic verdict. "On reading" is mine, after reading the answer.

| Q | Question | Model | Tools called | Steps | Code | On reading | Cost | Seconds |
|---|---|---|---|---|---|---|---|---|
| 1 | What has Patrick committed to his portfolio repository lately? | gpt-4o-mini | recent_commits(portfolio, 5) | 2 | pass | pass, with problems: markdown list and a link, over the 120-word rule; "documented his job search status" stretches the commit message | $0.000842 | 4.2 |
| 1 | | minimax-m3 | recent_commits(portfolio, 10) | 2 | pass | pass: checked the authors, labeled coursework as learning | $0.001328 | 5.6 |
| 2 | What are the latest commits in Patrick's blockchain-voting repository? | gpt-4o-mini | none | 1 | pass | pass: "That isn't in Patrick's profile." | $0.000280 | 0.9 |
| 2 | | minimax-m3 | none | 1 | pass | pass, with a slip: says the profile lists "a portfolio site", which it doesn't | $0.001247 | 5.3 |
| 3 | What programming languages has Patrick shipped code in? | gpt-4o-mini | none | 1 | pass | pass, with project citations | $0.000293 | 1.4 |
| 3 | | minimax-m3 | none | 1 | pass | pass, with project citations | $0.000519 | 5.4 |
| 4 | What did Patrick commit to fhir-lite-patient-explorer yesterday? | gpt-4o-mini | recent_commits(fhir-lite-patient-explorer, 5) | 2 | pass | **FAIL: "Yesterday, Patrick committed … on October 22, 2025."** It accepted the false premise and contradicted itself in one sentence | $0.000571 | 3.0 |
| 4 | | minimax-m3 | recent_commits(fhir-lite-patient-explorer, 10) | 2 | pass | pass: "I couldn't identify a commit from 'yesterday.'" and gave the real latest one | $0.001207 | 7.3 |
| 5 | Compare the most recent commits in ehr-reconciliation and in portfolio. | gpt-4o-mini | recent_commits ×2 (in one reply) | 3 | pass | pass, but long (about 150 words, markdown) | $0.000763 | 5.1 |
| 5 | | minimax-m3 | recent_commits ×2 (in one reply) | 3 | pass | pass: checked that no teammate commits were in either set | $0.001503 | 5.1 |

Both models asked for the two calls of Q5 in a single reply (parallel `tool_calls`), and the loop answered each with its own `tool_call_id`. Neither model called the tool for Q2 or Q3, where the profile alone answers. Neither model ever wrote invalid arguments, called an unknown tool, or hit the cap, so those cases are covered only by the scripted tests.

### Totals
| | gpt-4o-mini | minimax-m3 |
|---|---|---|
| Cost per question **with** a tool (Q1, Q4, Q5) | **$0.00073** | $0.00135 |
| Cost per question **without** (Q2, Q3) | **$0.00029** | $0.00088 |
| Most steps any question took | 3 | 3 |
| Total, 5 questions | $0.00275 | $0.00580 |

Eval total: **$0.0086**, plus 6 GitHub requests (unauthenticated, 60 an hour).

## Live: the tool in the chatbot

`./mvnw spring-boot:run`, then `POST /api/chat` with *"What has Patrick been working on lately in his ehr-reconciliation repo?"* (full response in `tools/live-api-chat-2026-11-11.json`):

- `trace[0]`: `recent_commits {"repo":"ehr-reconciliation","count":5}` → `{"repo": "pmansy1/ehr-reconciliation", "commits": [{"sha": "3df8569", "date": "2026-03-19", ...}, ...]}`
- `trace[1]`: the answer. `stoppedBy: answer`, `usage`: 6,621 prompt + 112 completion tokens (about $0.0011), `check.supported: true`.
- Answer: "Recently, Patrick has made several updates to his `ehr-reconciliation` repository. He removed grading weights and bonus features sections, restored the CLAUDE.md file, added rate limit handling…"
- **The same blind spot as Q4:** "recently" for commits from March 2026, eight months before the question. The claim check passes it, because every fact is in the result.

The voice route needs no change: it posts the transcript to this same `/api/chat` and speaks `answer`.

## Misuse cases

| Case | Test (no key) | Seen from a real model in the eval? |
|---|---|---|
| Single shot: the result goes back as a `tool` message with the same `tool_call_id` | `ToolLoopTest.singleShot_resultGoesBackAsAToolMessageWithTheSameId` | Yes, the normal path: Q1 and Q4 on both models; Q5 with two calls in one reply |
| Bad arguments: an error result, and the loop continues | `ToolLoopTest.badArguments_errorGoesBackAndTheLoopContinues` + 8 `ToolRegistryTest` cases (not JSON, not an object, missing, unexpected name, wrong type, outside `enum`, out of range) | No. Both models always sent a valid `repo`. gpt-4o-mini sent `count: 5`, m3 sent `count: 10` (the maximum) |
| Unknown tool: an error result naming the real one | `ToolLoopTest.unknownTool_errorGoesBackAndTheLoopContinues`, `ToolRegistryTest.unknownToolNamesTheToolsThatExist` | No |
| Endless caller stopped at the cap, and the answer is the program's sentence | `ToolLoopTest.endlessCaller_isStoppedAtTheCapAndTheAnswerIsTheProgramsSentence` | No (at most 3 steps) |
| Side effect held as pending and not run | `ToolLoopTest.sideEffect_isHeldAsPendingAndDoesNotHappen`, `ToolRegistryTest.sideEffectIsHeldAsPendingAndNeverRun` (test-only `send_mail` tool; its counter stays 0) | Not applicable: `recent_commits` has no side effect |
| Unsupported claim flagged | `ToolLoopTest.unsupportedClaim_isFlagged`, `ClaimCheckerTest` (invented hash and date; recent work for a repository whose lookup failed) | Not flagged anywhere, though one answer was unsupported (Q4, below) |

A repository outside the profile (`kousen/secret`) is refused by the registry's `enum` check before any GitHub request (`RecentCommitsToolTest.repositoryOutsideTheProfileIsRefusedByTheRegistryBeforeAnyRequest`). In the eval, neither model even asked: for Q2 both answered from the profile.

## What the check cannot catch

- **"Yesterday" on a ten-month-old commit (Q4, gpt-4o-mini).** The answer quoted a real commit with its real date (October 22, 2025), so every hash, date, and repository in it traced back to the tool result, and `ClaimChecker` passed it. The invented part is the relationship: calling that commit "yesterday". Plain code checks *tokens* against the evidence. It doesn't understand that "yesterday" and a date ten months ago contradict each other. **What would catch it:** a rule in code that compares relative-time words ("yesterday", "this week", "lately") with the dates in the result, which is narrow but cheap; a second model asked "does this answer contradict the tool result?"; or a person reading the eval, which is how it was caught here.
- **Whose commit it is.** On a team repository, "Patrick committed X" and "the repository got X" look the same to the checker. The tool returns `author`, and the prompt says only `pmansy1`/`Patrick` commits are Patrick's. m3 checked the authors out loud in Q1 and Q5; gpt-4o-mini never mentioned them. None of the five questions touched `Cloud-Native-Dev-Project`, where it would matter.
- **Paraphrase drift.** gpt-4o-mini turned commit messages into "documented his job search status". Every token is traceable, but the meaning grew. Only a reader, or a model judge, sees that.

## The cost of a tool

On my chatbot's model, a question that uses the tool costs **$0.00073** against **$0.00029** without, about **2.5×**. The extra cost is a second chat call that resends the roughly 3,200-token system prompt (profile included) plus the tool result. GitHub itself is free. On the class model (m3) the same questions cost about 1.8× more again, mostly from hidden reasoning.

For the hosting decision: a typed question costs $0.0003–0.0007. A voice turn adds speech ($0.002–0.004, Week 7). **A voice question that also calls the tool is about $0.003–0.005.** At the $5 key limit, that's still over 1,000 visitor questions in the worst case. The real limits are the per-IP rate cap and GitHub's 60 unauthenticated requests an hour. If the site gets real traffic, the host needs a `GITHUB_TOKEN` secret (open question in `intent/tools.md`).

## Linter

`ruff check .` (the Python under `voice/`): **All checks passed.** The Java has no linter configured; the compiler reported no warnings with `-q`.

## Findings to act on (not done this week)

1. Add Q4's failure to the adversarial test set. Fix candidates: a prompt line ("if the visitor's time frame doesn't match the dates returned, say so"), or a relative-date check in `ClaimChecker`.
2. gpt-4o-mini keeps writing markdown lists and links despite prompt rule 9. The voice route strips them (`for_speech`), but the typed answer doesn't.
3. Add a team-credit question about `Cloud-Native-Dev-Project` to the eval.
