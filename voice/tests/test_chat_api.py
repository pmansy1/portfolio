"""No-key tests: the voice route answers through my Java chat service (POST /api/chat), not its own prompt."""
import io
import json
import urllib.error

import pytest

import server


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def test_answer_posts_the_question_to_the_chat_api(monkeypatch):
    seen = {}

    def fake_urlopen(req, timeout):
        seen["url"], seen["body"], seen["ctype"] = req.full_url, json.loads(req.data), req.headers["Content-type"]
        reply = {"ok": True, "answer": "Patrick has shipped Java.", "model": "openai/gpt-4o-mini",
                 "usage": {"promptTokens": 2900, "completionTokens": 10}}
        return FakeResponse(json.dumps(reply).encode())

    monkeypatch.setattr(server.urllib.request, "urlopen", fake_urlopen)
    text, cost = server.answer("Has Patrick shipped Java?")
    assert seen["url"] == server.CHAT_API_URL
    assert seen["body"] == {"question": "Has Patrick shipped Java?"}
    assert seen["ctype"] == "application/json"
    assert text == "Patrick has shipped Java."
    assert cost == pytest.approx(2900 * 0.15e-6 + 10 * 0.60e-6)


def test_chat_api_error_message_is_spoken_not_crashed(monkeypatch):
    def fake_urlopen(req, timeout):
        body = json.dumps({"ok": False, "error": "The chatbot is not configured right now."}).encode()
        raise urllib.error.HTTPError(req.full_url, 503, "Service Unavailable", {}, io.BytesIO(body))

    monkeypatch.setattr(server.urllib.request, "urlopen", fake_urlopen)
    text, cost = server.answer("hi")
    assert text == "The chatbot is not configured right now."
    assert cost == 0


def test_chat_service_down_is_a_clear_message(monkeypatch):
    def fake_urlopen(req, timeout):
        raise urllib.error.URLError("connection refused")

    monkeypatch.setattr(server.urllib.request, "urlopen", fake_urlopen)
    text, cost = server.answer("hi")
    assert "couldn't reach" in text.lower()
    assert cost == 0
