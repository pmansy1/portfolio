"""No-key tests for the speaker's refusal to save a non-audio reply (spec behavior 18)."""
import pytest

from speak import speak


class FakeReply:
    def __init__(self, body: bytes, ctype: str):
        self._body, self.headers = body, {"content-type": ctype}

    def read(self) -> bytes:
        return self._body


class FakeClient:
    def __init__(self, reply):
        self.tts = type("T", (), {"create_speech": lambda _s, **_k: reply})()


def test_json_error_body_is_an_error_and_no_file_is_written(tmp_path):
    out = tmp_path / "answer.mp3"
    client = FakeClient(FakeReply(b'{"error":{"message":"voice not found"}}', "application/json"))
    with pytest.raises(RuntimeError, match="expected audio"):
        speak(client, "Hello.", out)
    assert not out.exists()


def test_audio_reply_is_saved_with_its_measurements(tmp_path, monkeypatch):
    monkeypatch.setattr("speak.estimate", lambda model, chars: 0.0001)
    out = tmp_path / "answer.mp3"
    body = b"ID3" + b"\x00" * 5000
    info = speak(FakeClient(FakeReply(body, "audio/mpeg")), "Hello there.", out)
    assert out.read_bytes() == body
    assert info["bytes"] == len(body) and info["chars"] == 12
    assert info["cost"] == 0.0001 and info["cost_is_estimate"]


def test_pcm_reply_is_framed_as_wav(tmp_path, monkeypatch):
    import wave
    monkeypatch.setattr("speak.estimate", lambda model, chars: None)
    out = tmp_path / "answer.wav"
    speak(FakeClient(FakeReply(b"\x00\x00" * 2400, "audio/pcm;rate=24000;channels=1")), "Hi.", out)
    with wave.open(str(out)) as w:
        assert w.getframerate() == 24000 and w.getnframes() == 2400
