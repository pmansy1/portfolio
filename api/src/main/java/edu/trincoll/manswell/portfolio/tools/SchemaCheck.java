package edu.trincoll.manswell.portfolio.tools;

import java.util.List;
import java.util.Map;

/** The subset of JSON Schema our tools use: required, known properties, string/integer, enum, minimum/maximum. */
final class SchemaCheck {

    private SchemaCheck() {
    }

    @SuppressWarnings("unchecked")
    static String problem(Map<String, Object> schema, Map<String, Object> args) {
        Map<String, Object> props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Map<String, Object> spec = (Map<String, Object>) props.get(e.getKey());
            if (spec == null) {
                return "unexpected argument '" + e.getKey() + "'; expected " + String.join(", ", props.keySet());
            }
            String problem = valueProblem(e.getKey(), e.getValue(), spec);
            if (problem != null) {
                return problem;
            }
        }
        for (String r : required) {                          // after unknown names: "txt" says more than "missing text"
            if (!args.containsKey(r)) {
                return "missing required argument '" + r + "'";
            }
        }
        return null;
    }

    private static String valueProblem(String key, Object value, Map<String, Object> spec) {
        String type = (String) spec.get("type");
        if ("string".equals(type) && !(value instanceof String)) {
            return "argument '" + key + "' must be a string";
        }
        if ("integer".equals(type) && !(value instanceof Integer || value instanceof Long)) {
            return "argument '" + key + "' must be an integer";
        }
        if (spec.get("enum") instanceof List<?> allowed && !allowed.contains(value)) {
            return "argument '" + key + "' must be one of " + allowed;
        }
        if ("integer".equals(type)) {
            long n = ((Number) value).longValue();
            Number min = (Number) spec.get("minimum");
            Number max = (Number) spec.get("maximum");
            if ((min != null && n < min.longValue()) || (max != null && n > max.longValue())) {
                return "argument '" + key + "' must be between " + min + " and " + max;
            }
        }
        return null;
    }
}
