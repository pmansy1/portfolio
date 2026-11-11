"""Shape the chatbot's answer for the speech model (spec behavior 21). No model, no key.

The answer is read aloud, so markdown characters would be spoken or mangled, and a long
answer costs per character. Keep the words, drop the markup, stop after three sentences.
"""
import re

MAX_SENTENCES = 3
_LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")               # [text](url) -> text
_LINE_MARK = re.compile(r"^\s*(?:#+|[-*+]|\d+[.)])\s+", re.M)  # headings, bullets, numbered items
_INLINE = re.compile(r"[*_`]+")                             # bold, italics, code ticks
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


def for_speech(text: str | None, max_sentences: int = MAX_SENTENCES) -> str:
    """Plain spoken text: links reduced to their words, markdown removed, list lines joined
    as sentences, at most `max_sentences` sentences."""
    if not text:
        return ""
    plain = _INLINE.sub("", _LINE_MARK.sub("", _LINK.sub(r"\1", text)))
    lines = [ln.strip() for ln in plain.splitlines() if ln.strip()]
    joined = " ".join(ln if ln[-1] in ".!?" else ln + "." for ln in lines)
    sentences = _SENTENCE_END.split(joined)
    return " ".join(sentences[:max_sentences]).strip()
