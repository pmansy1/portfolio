"""The billed eval: one transcription per clip, checked in code, and one speech call (behavior 24).
Skipped without a key, so the no-key run stays free. Record the output in CHECKS.md.

    python3 -m pytest tests/test_eval.py -v -s       # about $0.002 on the class models
"""
import json
import os
from pathlib import Path

import pytest

from check import compare

ROOT = Path(__file__).resolve().parents[1]
KEY = os.environ.get("OPENROUTER_API_KEY")
pytestmark = pytest.mark.skipif(not KEY, reason="OPENROUTER_API_KEY not set: the billed eval is skipped")
SETS = [ROOT / "clips" / "fixtures" / "expected.json", ROOT / "clips" / "expected.json"]
CASES = [(s, e) for s in SETS if s.exists() for e in json.loads(s.read_text())]


@pytest.fixture(scope="module")
def client():
    from openrouter import OpenRouter
    return OpenRouter(api_key=KEY)


@pytest.mark.parametrize(("expected_file", "entry"), CASES, ids=[e["file"] for _, e in CASES])
def test_each_clip_transcribes_within_tolerance(client, expected_file, entry):
    from transcribe import transcribe_file
    clip = expected_file.parent / entry["file"]
    if not clip.exists():
        pytest.skip(f"{clip.name} missing")
    r = transcribe_file(client, clip, language=entry.get("language"))
    verdict = compare(entry["text"], r["text"], entry.get("must_contain"), entry.get("max_wer", 0.15))
    print(f"\n{entry['file']}: wer={verdict['wer']} missing={verdict['missing']} ${r['cost']}  got={r['text']!r}")
    assert verdict["ok"], verdict


def test_speech_comes_back_as_audio(client, tmp_path):
    from speak import speak
    info = speak(client, "The eval ran, and this sentence cost a fraction of a cent.", tmp_path / "eval.mp3")
    print(f"\nspeech: {info['bytes']} bytes, ${info['cost']}")
    assert info["bytes"] > 2000                              # a JSON error body is a few hundred bytes
    head = (tmp_path / "eval.mp3").read_bytes()[:3]
    assert head.startswith(b"ID3") or head[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2")
