# Intent: voice

## Goal
A visitor to my portfolio site can ask the chatbot a question out loud and hear the answer. The server transcribes the recording, the chatbot answers from my profile exactly as it does for a typed question, and the answer is spoken back. The transcript is model output and may be wrong, so the program checks it where it can (against text a person wrote first) and shows the visitor what it heard so the rest is visible.

## Who it is for
- **Recruiters and collaborators** who would rather ask than type, for example on a phone. Today they can only type into the chat box.
- **Me and Prof. Kousen**, who need evidence that a misheard name or number is caught by code, and what one spoken turn costs before the site is deployed.

## Constraints
- **Same rule as typed answers:** the spoken answer comes from the same answer function and the same profile. It must refuse to fabricate exactly as the typed path does.
- **Models (class defaults, through OpenRouter):** transcription `openai/whisper-large-v3-turbo`, compared with `elevenlabs/scribe-v2`. Speech `microsoft/mai-voice-2.1-flash`, compared with `elevenlabs/eleven-v4-turbo`.
- **Language:** Python with the `openrouter` SDK for the Week 7 code in `voice/`. My chat service in `spec.md` is Java, but it does not exist yet, so the loop runs on the course example's server and profile until it does.
- **Key on the server only.** The browser sends audio and nothing else. No key in the repository.
- **Cost:** about half a cent per voice turn on the class models. It counts against the $5 semester ceiling in `intent/portfolio.md`. The server caps uploads at 10 MB.
- **Deadline:** Week 7 lab, Monday, November 9, 2026, 1:30 PM, tag `week07-submitted` (submitted late).

## Not in scope
- Streaming or real-time conversation. One recording in, one answer out.
- Speaker identification or reading tone. The transcriber is asked only for the words.
- Deployment. The loop runs on localhost until the November 30 hosting milestone.
- A Java port of `/api/voice`. It moves next to `/api/chat` once that route exists.

## Success looks like
1. Two clips of my own voice, with `expected.json` written before any model heard them, are transcribed by both models, and `voice/CHECKS.md` records the word error rate and which model got my name ("Manswell") and "415" right.
2. A question asked through the page's recorder comes back as a transcript, a short answer, and audio, with the three costs and their total recorded as one turn.
3. An empty or garbled transcript gives "I didn't catch that." It is not a crash, and the chatbot never answers a question nobody asked.

## Open questions
- Will the deployed `/api/voice` be Java (Spring calling the two endpoints with `RestClient`) or a small Python function next to the Java service? To decide with the hosting choice, before November 30.

**Approved by:** Patrick Manswell, November 11, 2026
