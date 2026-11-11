# Intent: tools

## Goal
The portfolio chatbot can answer "what has Patrick been working on lately?" with live facts: the most recent commits in one of the repositories behind my profile, fetched from GitHub when the model asks for them. The model only asks; my server checks every request, runs it or refuses it, and hands every problem back to the model as a result it can read. The chatbot never claims a commit, a date, or a repository that the tool did not return.

## Who it is for
- **Recruiters and collaborators** who want to know whether my projects are alive and what I did recently. Today the profile is a snapshot; it cannot say what changed this week.
- **Me and Prof. Kousen**, who need evidence that the program, not the model, decides what runs, and that each misuse case is tested before the code that handles it.

## Constraints
- **One tool: `recent_commits(repo, count)`.** Read-only, from GitHub's public REST API, no GitHub key needed (60 requests an hour per address; `GITHUB_TOKEN` raises it).
- **Only my profile's repositories:** `fhir-lite-patient-explorer`, `ehr-reconciliation`, `Cloud-Native-Dev-Project`, and `portfolio`, all under `pmansy1`. The model names the repository; the code adds the owner and refuses anything else.
- **Team work stays team work:** each commit comes back with its author, because most commits in `Cloud-Native-Dev-Project` are my teammates'. The chatbot must not credit me with theirs.
- **Language:** Java, inside the existing Spring Boot chat service, because the answer function that the typed path and the voice path both use is there.
- **Models (OpenRouter, both with tool support):** `openai/gpt-4o-mini` (my chatbot's model) and `minimax/minimax-m3` (the class model) as the comparison.
- **The loop has a cap of four steps.** If the model never stops asking, the answer is the program's sentence, not the model's.
- **No key in the repository.** The OpenRouter key stays on the server.
- **Deadline:** Week 8 lab, Monday, November 16, 2026, 1:30 PM, tag `week08-submitted`.

## Not in scope
- Tools with side effects (sending mail, writing anywhere). The side-effect rule is still built and tested, with a test-only tool, so it is ready when one arrives.
- Repositories that aren't in my profile, private repositories, and other people's repositories.
- Reading code or diffs: commit messages, authors, and dates only.
- Caching GitHub responses (revisit at deployment if the rate limit bites).

## Success looks like
1. Asked "What has Patrick committed to his portfolio lately?", the chatbot names real recent commits, and the trace shows one `recent_commits` call and its result.
2. Asked about a repository that isn't in my profile, the chatbot says it can't look that up, and the trace shows the refusal coming from my code, not from GitHub.
3. Six no-key tests with a scripted model (single shot, bad arguments, unknown tool, endless caller, side effect held, unsupported claim) fail before the code and pass after it in the commit history.

## Open questions
- Will GitHub's unauthenticated rate limit be enough once the site is deployed, or does the host need a `GITHUB_TOKEN` secret? To decide with the hosting choice, before November 30.

**Approved by:** Patrick Manswell, November 11, 2026
