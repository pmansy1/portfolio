package edu.trincoll.manswell.portfolio.tools;

import java.util.List;

/** The claim check's verdict (behavior 31). */
public record ClaimCheck(boolean supported, List<String> unsupported) {

    public ClaimCheck {
        unsupported = List.copyOf(unsupported);
    }
}
