package edu.trincoll.manswell.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Behavior 1: profile/profile.json obeys the schema rules in spec.md component 1. No network. */
class ProfileSchemaTest {

    static final Path PROFILE = Path.of("..", "profile", "profile.json");
    static final Set<String> PINNED = Set.of("fhir-lite-patient-explorer", "ehr-reconciliation", "Cloud-Native-Dev-Project");
    static final List<String> ANNOTATION_FIELDS = List.of("what_is_this", "why_this_choice", "what_breaks", "what_did_i_learn");

    static String raw;
    static Map<String, Object> profile;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws Exception {
        raw = Files.readString(PROFILE);
        profile = new ObjectMapper().readValue(raw, Map.class);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(String key) {
        return (List<Map<String, Object>>) profile.getOrDefault(key, List.of());
    }

    @Test
    void rule1_everyProjectIsShipped() {
        assertThat(list("projects")).allSatisfy(p -> assertThat(p.get("status")).isEqualTo("shipped"));
    }

    @Test
    void rule2_everySkillIsBackedByARealProject() {
        Set<String> ids = list("projects").stream().map(p -> (String) p.get("id")).collect(Collectors.toSet());
        assertThat(list("skills")).allSatisfy(s -> {
            @SuppressWarnings("unchecked")
            List<String> evidence = (List<String>) s.get("evidence");
            assertThat(evidence).as("evidence for %s", s.get("name")).isNotEmpty();
            assertThat(ids).as("evidence for %s", s.get("name")).containsAll(evidence);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void rule3_allFourAnnotationsAreFilledIn() {
        assertThat(list("projects")).allSatisfy(p -> {
            Map<String, Object> a = (Map<String, Object>) p.get("annotation");
            assertThat(a).as("annotation of %s", p.get("id")).isNotNull();
            ANNOTATION_FIELDS.forEach(f ->
                    assertThat((String) a.get(f)).as("%s.%s", p.get("id"), f).isNotBlank());
        });
    }

    @Test
    void rule4_aNameAppearsInOnlyOneOfSkillsLearningNotYet() {
        List<String> all = new ArrayList<>();
        for (String section : List.of("skills", "learning", "not_yet")) {
            list(section).forEach(e -> all.add(((String) e.get("name")).trim().toLowerCase()));
        }
        Set<String> seen = new HashSet<>();
        List<String> dupes = all.stream().filter(n -> !seen.add(n)).toList();
        assertThat(dupes).isEmpty();
    }

    @Test
    void rule5_exactlyThePinnedProjects() {
        Set<String> ids = list("projects").stream().map(p -> (String) p.get("id")).collect(Collectors.toSet());
        assertThat(ids).isEqualTo(PINNED);
    }

    @Test
    void notYetEntriesHaveAReason() {
        assertThat(list("not_yet")).isNotEmpty().allSatisfy(n ->
                assertThat((String) n.get("why_not_yet")).as("why_not_yet for %s", n.get("name")).isNotBlank());
    }

    @Test
    void noPlaceholderIsLeftForPatrickToWrite() {
        List<String> todo = raw.lines().filter(l -> l.contains("TODO(")).map(String::strip).toList();
        assertThat(todo).as("profile lines still marked TODO(Patrick)").isEmpty();
    }
}
