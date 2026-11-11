package edu.trincoll.manswell.portfolio.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Plain code looking for claims the tools did not support (behavior 31). It catches specific, checkable things:
 * a commit hash or a date that no successful result contained, and recent work described for a repository that
 * was never fetched successfully. It cannot judge meaning (who made a commit, what a commit "means"); that is
 * recorded in tools/CHECKS.md under "What the check cannot catch".
 */
public class ClaimChecker {

    private static final Pattern HASH = Pattern.compile("\\b(?=[0-9a-f]*\\d)[0-9a-f]{7,40}\\b");
    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?]+[.!?]?");
    private static final Set<String> RECENT_WORDS = Set.of("commit", "recent", "latest", "lately", "pushed", "last updated");

    private final List<String> repos;

    public ClaimChecker() {
        this(List.of());
    }

    /** @param repos the repository names the tool can fetch; mentioning one with recent-work words needs a fetch */
    public ClaimChecker(List<String> repos) {
        this.repos = List.copyOf(repos);
    }

    public ClaimCheck check(String answer, List<TraceStep> trace) {
        String text = answer == null ? "" : answer;
        String evidence = trace.stream().filter(TraceStep::succeeded)
                .map(s -> String.valueOf(s.result())).collect(Collectors.joining("\n"));
        List<String> flags = new ArrayList<>();
        flags.addAll(notIn(HASH, text.toLowerCase(Locale.ROOT), evidence, "commit"));
        flags.addAll(notIn(DATE, text, evidence, "date"));
        flags.addAll(unfetchedRepos(text, trace));
        return new ClaimCheck(flags.isEmpty(), flags);
    }

    private static List<String> notIn(Pattern p, String text, String evidence, String what) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (!evidence.contains(m.group())) {
                out.add(what + " " + m.group() + " appears in the answer but in no tool result");
            }
        }
        return out;
    }

    private List<String> unfetchedRepos(String answer, List<TraceStep> trace) {
        Set<String> fetched = trace.stream().filter(TraceStep::succeeded)
                .map(s -> String.valueOf(s.result().get("repo")).toLowerCase(Locale.ROOT))
                .map(r -> r.substring(r.indexOf('/') + 1))
                .collect(Collectors.toSet());
        List<String> out = new ArrayList<>();
        Matcher m = SENTENCE.matcher(answer.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String sentence = m.group();
            boolean aboutRecentWork = RECENT_WORDS.stream().anyMatch(sentence::contains);
            boolean negative = sentence.contains("couldn't") || sentence.contains("could not") || sentence.contains("can't")
                    || sentence.contains("unable") || sentence.contains("not able") || sentence.contains("doesn't exist");
            for (String repo : repos) {
                if (aboutRecentWork && !negative && sentence.contains(repo.toLowerCase(Locale.ROOT)) && !fetched.contains(repo.toLowerCase(Locale.ROOT))) {
                    out.add("recent work described for " + repo + " without a successful lookup");
                }
            }
        }
        return out;
    }
}
