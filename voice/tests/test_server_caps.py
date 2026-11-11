"""No-key tests for the voice route's guards (spec behaviors 17, 23). Model calls are faked."""
import pytest

import server


@pytest.fixture
def calls(monkeypatch):
    log = []
    monkeypatch.setattr(server, "transcribe", lambda _c, audio, fmt, model: log.append("stt") or
                        {"text": "", "cost": 0.00001, "seconds": 0.1})
    monkeypatch.setattr(server, "answer", lambda q: log.append("chat") or ("never", 0.0))
    monkeypatch.setattr(server, "speak", lambda *a, **k: log.append("tts"))
    return log


def test_unknown_audio_type_is_refused_before_any_call(calls):
    status, body = server.handle_voice(b"abc", "text/plain", client=object())
    assert status == 415 and "unsupported" in body["error"]
    assert calls == []


def test_oversized_upload_is_refused_before_any_call(calls):
    status, body = server.handle_voice(b"", "audio/webm", client=object(), length=server.MAX_BYTES + 1)
    assert status == 413
    assert calls == []


def test_empty_transcript_is_not_answered_or_spoken(calls):
    status, body = server.handle_voice(b"RIFF....", "audio/wav", client=object())
    assert status == 200
    assert body["answer"] == "I didn't catch that."
    assert calls == ["stt"]                              # no chat, no speech


def test_browser_content_type_parameters_are_ignored(calls):
    status, _ = server.handle_voice(b"x", "audio/webm;codecs=opus", client=object())
    assert status == 200
