# Intent: portfolio

## Goal
A personal website with a chatbot that answers one question for a visitor: should we work together? The chatbot answers only from a profile I wrote. When asked about something I have not shipped, it says so, or says I'm still learning it. It never makes up experience.

## Who it is for
- **Recruiters, internship coordinators, and possible collaborators** who want a quick, honest read on what I can do. Today they skim my GitHub README, résumé, or LinkedIn page, which can't answer follow-up questions and can't tell finished work from something I only started.
- **Prof. Kousen**, who grades the profile, the grounding prompt, and the adversarial test set, and asks the four questions about it in the Comprehension Defense.

## Constraints
- **Honesty is the core requirement.** The profile only lists work I've shipped. Anything I'm aiming for goes in the "not yet" inventory. The chatbot must refuse to fabricate, and that's graded.
- **Projects in the profile:** only my three pinned GitHub projects: `fhir-lite-patient-explorer`, `ehr-reconciliation`, and `Cloud-Native-Dev-Project`.
- **Language:** Java for the chatbot function, React and JavaScript for the web app.
- **AI source:** OpenRouter, the same as my Week 2 chat client.
- **Model:** `openai/gpt-4o-mini`. A stronger model is run on the same test set only for comparison.
- **Hosting:** Vercel.
- **No API key in the repository.** The deployed key lives in the host's secret store. Locally it comes from `OPENROUTER_API_KEY`.
- **Cost ceiling:** under $5 for the whole semester, including development, tests, and visitor traffic. Free hosting tiers only.
- **Deadline:** December 14, 2026, 1:30 PM (tag `portfolio-final` plus the deployed URL). Milestones are on Oct 5, Oct 19, Nov 2, and Nov 30.

## Not in scope
- Accounts, logins, or a database. The profile is a file in the repo.
- Retrieval over documents I didn't write (no RAG over GitHub, LinkedIn, or the web). The profile is small enough to fit in the prompt.
- Visitor conversations persisting between page loads.
- A CMS or editing the profile through the site.
- Projects that aren't pinned on my GitHub, including the CPSC 415 weekly labs.
- Spoken interface and tool use. Those come in the Week 7 and Week 8 labs and will get their own intent files.

## Success looks like
1. A visitor opens the deployed URL, asks "Has Patrick built anything with an LLM API?", and gets an answer that names `ehr-reconciliation` (Claude API) and nothing that isn't in the profile.
2. A visitor asks about a skill I don't have (e.g. "How much iOS/Swift experience does he have?"). The chatbot says it isn't in my shipped work and, if relevant, points to the "not yet" inventory. It doesn't invent experience.
3. The adversarial test set (at least 10 questions covering: a missing skill, a comparison, flattery bait, a false premise, and an out-of-scope personal question) has been run against `gpt-4o-mini` and a stronger model, with results recorded, including failures.

## Open questions
- Vercel has no official Java runtime. `spec.md` hosts the React app on Vercel and the Java chat service on Render, with Vercel forwarding `/api/*` to it. Confirm this split works once a test service is deployed (by Oct 19).
- Contact links the chatbot may share: the ones already public on my GitHub README (GitHub, LinkedIn, email). Confirm before the profile is drafted.

## Corrections made to agent draft
1. **Language:** changed from a Python standard-library chat function and plain HTML to **Java** for the chatbot function and **React/JavaScript** for the web app.
2. **Hosting:** kept **Vercel**. Because Vercel can't run Java natively, the Java service runs on Render behind a Vercel rewrite (see `spec.md`).
3. **Model:** confirmed **`openai/gpt-4o-mini`** as the model, with OpenRouter as the AI source.
4. **Projects:** the profile uses **only my three pinned GitHub projects**, not the CPSC 415 labs the draft suggested.
5. **"Not yet" inventory:** removed the local-models row (I have no experience with local models). Also removed React and Docker/Kubernetes, since my pinned projects show I've shipped both.

**Approved by:** Patrick Manswell, October 5, 2026
