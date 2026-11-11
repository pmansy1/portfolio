"""Browser check of the voice page (behavior 22). Start server.py first: it needs the key; the browser must not.

    pip install playwright pytest && playwright install chromium     # once
    python3 server.py &
    python3 -m pytest test_browser.py -v -s                           # three billed calls, about $0.005

A microphone can't be scripted reliably, so it uploads a fixture clip through the page's
file picker. The last three assertions are the point: what the browser was allowed to see.
Whether the answer is honest is a person's judgment, recorded in CHECKS.md.
"""
import os
from pathlib import Path

from playwright.sync_api import sync_playwright

URL = os.environ.get("VOICE_URL", "http://localhost:8000")
CLIP = Path(__file__).parent / "clips" / "fixtures" / "clip-02.wav"   # "Have you ever deployed anything with Kubernetes? ..."


def test_voice_page_answers_and_the_key_never_reaches_the_browser():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not os.environ.get("HEADED"))
        page = browser.new_page()
        requests = []
        page.on("request", lambda r: requests.append((r.url, r.headers.get("authorization"))))

        page.goto(URL)
        assert "sk-or" not in page.content()
        page.set_input_files("#file", str(CLIP))
        page.click("#send")
        page.wait_for_function("document.querySelector('#out').textContent.includes('Answer:')", timeout=120_000)
        out = page.inner_text("#out")
        has_audio = page.evaluate("!!document.querySelector('#player').src")
        browser.close()

    print("\n" + out)
    transcript, answer = out.split("Answer:")[0], out.split("Answer:")[1]
    assert "kubernetes" in transcript.lower(), out
    assert len(answer.split("[")[0].strip()) > 20, out
    assert "speech failed" not in out, out
    assert has_audio
    assert all(auth is None for _, auth in requests)                   # no Authorization header left the browser
    assert all("openrouter" not in url for url, _ in requests)          # the browser never called the provider
    assert any(url.endswith("/api/voice") for url, _ in requests)       # it called our server
