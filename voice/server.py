"""The voice loop: the browser records, the server transcribes, answers from the profile, and speaks.

    export OPENROUTER_API_KEY=...
    python3 server.py                 # then open http://localhost:8000

GET  /            the page: hold-to-talk, a file picker fallback, a typed fallback.
POST /api/voice   body = audio bytes, Content-Type names the format. Three billed calls on the server:
                  transcription, the chat answer, speech. Returns JSON with the transcript, the answer,
                  the MP3 as base64, and the three costs. The browser never sees the key (behavior 22).
POST /api/ask     body = the question as text; the same answer() without transcription.

Week 7 note: my Java chat service does not exist yet, so answer() uses the course example's
profile (example-profile.json, a fictional student). It is swapped for mine before Nov 30.
"""
import base64
import json
import os
import pathlib
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from openrouter import OpenRouter

from answer_text import for_speech
from speak import DEFAULT_MODEL as TTS_MODEL
from speak import DEFAULT_VOICE as TTS_VOICE
from speak import speak
from transcribe import DEFAULT_MODEL as STT_MODEL
from transcribe import transcribe

HERE = pathlib.Path(__file__).parent
CHAT_MODEL = os.environ.get("CHAT_MODEL", "minimax/minimax-m3")
MAX_BYTES = 10_000_000                                   # behavior 23: what a visitor may upload
MAX_QUESTION_CHARS = 500
PORT = int(os.environ.get("PORT", "8000"))
PROFILE = json.loads((HERE / "example-profile.json").read_text())
FORMATS = {"audio/webm": "webm", "audio/mpeg": "mp3", "audio/mp3": "mp3", "audio/wav": "wav", "audio/ogg": "ogg",
           "audio/x-wav": "wav", "audio/wave": "wav", "audio/mp4": "m4a", "audio/x-m4a": "m4a", "audio/flac": "flac"}
NOT_HEARD = "I didn't catch that."

SYSTEM = """You answer a visitor's questions about one person, from the profile below and nothing else.
Rules: shipped work is in "projects" and "skills_shipped"; anything in "not_yet" is something the person has
deliberately not done, and you say so plainly. If the profile does not cover the question, say that you don't
know rather than guessing. Never claim experience the profile does not record. Answer in two or three spoken
sentences: this answer will be read aloud, so no lists, no markdown, no URLs.

PROFILE:
""" + json.dumps(PROFILE, indent=1)

_client: OpenRouter | None = None


def get_client() -> OpenRouter:
    """The key is read on the server, once, and never leaves it."""
    global _client
    if _client is None:
        key = os.environ.get("OPENROUTER_API_KEY")
        if not key:
            raise RuntimeError("OPENROUTER_API_KEY is not set on the server")
        _client = OpenRouter(api_key=key)
    return _client


def answer(question: str) -> tuple[str, float | None]:
    """The chatbot's answer function; the typed path and the voice path both call it.
    max_tokens covers minimax-m3's hidden reasoning; at 400 it sometimes returned nothing."""
    c = get_client().chat.send(model=CHAT_MODEL, max_tokens=1500,
                               messages=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": question}])
    text = (c.choices[0].message.content or "").strip()
    if not text:                                          # an empty answer is a failed answer, said so
        text = f"I couldn't put an answer together (the model stopped: {c.choices[0].finish_reason}). Please ask again."
    return text, getattr(c.usage, "cost", None)


def voice_turn(client, audio: bytes, fmt: str) -> dict:
    """Hear, think, speak, once. A failed speech call still returns the transcript and the answer."""
    t0 = time.time()
    heard = transcribe(client, audio, fmt, STT_MODEL)
    question = (heard["text"] or "").strip()[:MAX_QUESTION_CHARS]
    if not any(ch.isalnum() for ch in question):          # behavior 17: nothing heard, nothing answered
        return {"transcript": "", "answer": NOT_HEARD, "audio_b64": None,
                "cost": {"stt": heard["cost"], "chat": 0, "tts": 0}, "seconds": {"stt": heard["seconds"]}}
    t1 = time.time()
    reply, chat_cost = answer(question)
    reply = for_speech(reply) or reply                    # behavior 21
    t2 = time.time()
    audio_b64, tts_cost, tts_error = None, 0, None
    out = HERE / "last-answer.mp3"
    try:
        spoken = speak(client, reply, out, TTS_MODEL, TTS_VOICE)
        audio_b64, tts_cost = base64.b64encode(out.read_bytes()).decode(), spoken["cost"]
    except Exception as e:  # noqa: BLE001  the provider's message, never the key
        tts_error = str(e)[:300]
    seconds = {"stt": round(t1 - t0, 1), "chat": round(t2 - t1, 1), "tts": round(time.time() - t2, 1)}
    print(f"turn: stt {seconds['stt']}s chat {seconds['chat']}s tts {seconds['tts']}s"
          + (f"  TTS FAILED: {tts_error}" if tts_error else ""), flush=True)
    return {"transcript": question, "answer": reply, "audio_b64": audio_b64, "tts_error": tts_error,
            "cost": {"stt": heard["cost"], "chat": chat_cost, "tts": tts_cost}, "seconds": seconds,
            "models": {"stt": STT_MODEL, "chat": CHAT_MODEL, "tts": f"{TTS_MODEL} / {TTS_VOICE}"}}


def handle_voice(body: bytes, content_type: str, client=None, length: int | None = None) -> tuple[int, dict]:
    """Validate before billing (behavior 23), then run one turn."""
    if (length if length is not None else len(body)) > MAX_BYTES:
        return 413, {"error": f"recording too large; the limit is {MAX_BYTES // 1_000_000} MB"}
    ctype = (content_type or "").split(";")[0].strip().lower()
    fmt = FORMATS.get(ctype)
    if not fmt:
        return 415, {"error": f"unsupported audio type {ctype!r}; send one of {sorted(FORMATS)}"}
    return 200, voice_turn(client or get_client(), body, fmt)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/api/"):
            return self.reply(405, {"error": "POST only"})
        page = (HERE / "index.html").read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(page)))
        self.end_headers()
        self.wfile.write(page)

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
        except ValueError:
            return self.reply(400, {"error": "bad Content-Length"})
        if length > MAX_BYTES:                            # refuse without reading the body
            return self.reply(413, {"error": f"recording too large; the limit is {MAX_BYTES // 1_000_000} MB"})
        body = self.rfile.read(length)
        try:
            if self.path == "/api/voice":
                return self.reply(*handle_voice(body, self.headers.get("Content-Type", "")))
            if self.path == "/api/ask":
                question = body.decode("utf-8", "replace").strip()[:MAX_QUESTION_CHARS]
                if not question:
                    return self.reply(400, {"error": "ask a question"})
                reply, cost = answer(question)
                return self.reply(200, {"transcript": question, "answer": for_speech(reply) or reply,
                                        "audio_b64": None, "cost": {"stt": 0, "chat": cost, "tts": 0}})
            self.reply(404, {"error": "not found"})
        except Exception as e:  # noqa: BLE001  logged in full here; the visitor gets the provider's message
            traceback.print_exc()
            self.reply(502, {"error": f"the voice service failed: {str(e)[:300]}"})

    def reply(self, status: int, data: dict):
        payload = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    get_client()                                          # fail at startup, not on the first visitor
    print(f"http://localhost:{PORT}  (stt {STT_MODEL}, chat {CHAT_MODEL}, tts {TTS_MODEL}/{TTS_VOICE})")
    try:
        ThreadingHTTPServer(("localhost", PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")
