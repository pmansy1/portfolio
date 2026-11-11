"""No-key tests for the transcript checker (spec behaviors 17, 19, 20).

    python3 -m pytest tests/test_check.py -v     # from voice/; well under a second
"""
from check import compare, wer, words

SAID = "My name is Patrick Manswell and I take CPSC 415 at Trinity College."   # 13 words


def test_words_ignore_case_and_punctuation():
    assert words("Hello, World!  It's 415.") == ["hello", "world", "it's", "415"]


def test_curly_apostrophe_is_the_same_word():
    assert words("I’m here") == words("I'm here")


def test_perfect_transcript_scores_zero():
    assert wer(SAID, SAID) == 0.0
    assert wer(SAID, "my name is patrick manswell, and i take cpsc 415 at trinity college") == 0.0


def test_one_substitution_in_thirteen_words():
    got = SAID.replace("Manswell", "Maxwell")
    assert abs(wer(SAID, got) - 1 / 13) < 1e-9


def test_insertion_and_deletion_count_as_edits():
    assert abs(wer("a b c d", "a b c d music") - 1 / 4) < 1e-9
    assert abs(wer("a b c d", "a b d") - 1 / 4) < 1e-9


def test_empty_transcript_is_a_failed_transcript_not_a_crash():
    assert wer(SAID, "") == 1.0
    assert wer(SAID, None) == 1.0
    result = compare(SAID, None)
    assert result == {"ok": False, "wer": 1.0, "missing": [], "empty": True}


def test_punctuation_only_reply_is_empty():
    assert compare(SAID, " ... ")["empty"]


def test_names_that_must_survive_are_checked_separately():
    got = SAID.replace("Manswell", "Maxwell")
    result = compare(SAID, got, must_contain=["Manswell", "415"])
    assert result["wer"] <= 0.15                      # the rate alone would have passed
    assert result["missing"] == ["Manswell"]
    assert not result["ok"]


def test_small_drift_within_tolerance_is_ok():
    got = "My name is Patrick Manswell and I take CPSC 415 at Trinity."   # one word dropped
    result = compare(SAID, got, must_contain=["Manswell"], max_wer=0.15)
    assert result["ok"] and result["wer"] == round(1 / 13, 3)


def test_drift_beyond_tolerance_fails():
    got = "My name is Patrick Manswell and I take a class."
    assert not compare(SAID, got, max_wer=0.15)["ok"]


def test_number_read_as_words_is_a_miss():
    got = SAID.replace("415", "four fifteen")
    assert compare(SAID, got, must_contain=["415"])["missing"] == ["415"]


def test_must_contain_ignores_spacing_and_case():
    got = "Compared with a boot camp graduate from cpsc415"
    assert compare("x", got, must_contain=["bootcamp", "CPSC 415"], max_wer=9)["missing"] == []
