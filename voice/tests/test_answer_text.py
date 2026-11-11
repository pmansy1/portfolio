"""No-key tests for the spoken-answer shaping (spec behavior 21)."""
from answer_text import for_speech


def test_markdown_is_stripped():
    assert for_speech("**Patrick** has shipped `Java` in _two_ projects.") == "Patrick has shipped Java in two projects."


def test_list_markers_and_headings_become_plain_sentences():
    text = "# Skills\n- Java\n- React"
    out = for_speech(text)
    assert "#" not in out and "-" not in out.split()[0]
    assert "Java" in out and "React" in out


def test_at_most_three_sentences():
    text = "One. Two! Three? Four. Five."
    assert for_speech(text) == "One. Two! Three?"


def test_links_keep_their_text_not_the_url():
    assert for_speech("See [the demo](https://example.com).") == "See the demo."


def test_empty_stays_empty():
    assert for_speech("") == ""
    assert for_speech(None) == ""
