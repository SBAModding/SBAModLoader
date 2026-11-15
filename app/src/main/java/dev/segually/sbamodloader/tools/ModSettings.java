package dev.segually.sbamodloader.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings a mod declares for itself, to be shown and stored by the loader. Plain Java (org.json only), so it is
 * tested on a desktop JVM.
 *
 * <p>A mod declares them in its {@code sbapack.json} (or, for a mod that is not a pack, in a file next to it called
 * {@code <file>.sbasettings.json}):
 * <pre>
 * { "id": "mymod", "guid": "dev.me.mymod",
 *   "settings": [
 *     { "key": "enabled", "type": "bool", "label": "Turn it on", "default": true },
 *     { "key": "speed", "type": "float", "label": "Speed", "default": 1.0, "min": 0.5, "max": 3, "step": 0.1 },
 *     { "key": "mode", "type": "choice", "label": "Mode", "default": "fast", "choices": ["fast", "slow"] },
 *     { "key": "name", "type": "string", "label": "Name", "default": "" } ] }
 * </pre>
 * {@code id} names the mod's settings (native mods ask for them by it). {@code guid} is a BepInEx plugin's GUID: its
 * values are also written to that plugin's config file, so {@code Config.Bind} returns them. Everything is capped, since
 * the text comes from a file anyone can ship.
 */
public final class ModSettings {
    public enum Type { BOOL, INT, FLOAT, CHOICE, STRING }

    public static final int MAX_FIELDS = 32;
    public static final int MAX_CHOICES = 12;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,39}$");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$");

    public static final class Choice {
        public final String value;
        public final String label;

        Choice(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static final class Field {
        public final String key;
        public final Type type;
        public final String label;
        public final String summary;
        /** The default, as the text that is stored ("true", "1.5", "fast"). */
        public final String defaultValue;
        public final double min;
        public final double max;
        public final double step;
        public final List<Choice> choices;
        /** The BepInEx config section this goes in (only matters for plugins). */
        public final String section;
        public final int maxLength;

        Field(String key, Type type, String label, String summary, String defaultValue, double min, double max, double step,
              List<Choice> choices, String section, int maxLength) {
            this.key = key;
            this.type = type;
            this.label = label;
            this.summary = summary;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.step = step;
            this.choices = choices;
            this.section = section;
            this.maxLength = maxLength;
        }

        /** Whether {@code text} is a value this field can hold. */
        public boolean accepts(String text) {
            return coerce(text) != null;
        }

        /** The stored text for {@code text} made valid (a number clamped to its range, a choice that exists), or null when it cannot be. */
        public String coerce(String text) {
            if (text == null) return null;
            try {
                switch (type) {
                    case BOOL:
                        if (text.equals("true") || text.equals("false")) return text;
                        return null;
                    case INT: {
                        long v = Math.round(Double.parseDouble(text.trim()));
                        v = Math.max((long) min, Math.min((long) max, v));
                        return Long.toString(v);
                    }
                    case FLOAT: {
                        double v = Double.parseDouble(text.trim());
                        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
                        v = Math.max(min, Math.min(max, v));
                        return trimmed(v);
                    }
                    case CHOICE:
                        for (Choice c : choices) if (c.value.equals(text)) return text;
                        return null;
                    default:
                        return text.length() > maxLength ? text.substring(0, maxLength) : text;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public final String id;
    /** A BepInEx plugin's GUID, or null. */
    public final String guid;
    public final List<Field> fields;

    private ModSettings(String id, String guid, List<Field> fields) {
        this.id = id;
        this.guid = guid;
        this.fields = fields;
    }

    /** The field with this key, or null. */
    public Field field(String key) {
        for (Field f : fields) if (f.key.equals(key)) return f;
        return null;
    }

    /**
     * Reads the declaration in {@code o} (an {@code sbapack.json}, or a settings file). Returns null when it declares no
     * settings. A field that is malformed is skipped, so one mistake does not hide the rest.
     *
     * @param fallbackId the id to use when the file does not name one (the file name without its extension)
     */
    public static ModSettings parse(JSONObject o, String fallbackId) {
        JSONArray list = o.optJSONArray("settings");
        if (list == null || list.length() == 0) return null;
        String id = o.optString("id", "").trim();
        if (!ID.matcher(id).matches()) id = sanitize(fallbackId);
        String guid = o.optString("guid", "").trim();
        if (guid.isEmpty() || guid.length() > 100 || !guid.matches("^[A-Za-z0-9_.-]+$")) guid = null;
        List<Field> fields = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < list.length() && fields.size() < MAX_FIELDS; i++) {
            JSONObject f = list.optJSONObject(i);
            if (f == null) continue;
            try {
                Field field = field(f);
                if (seen.put(field.key, true) == null) fields.add(field);
            } catch (JSONException | IllegalArgumentException e) {
                // Skipped.
            }
        }
        return fields.isEmpty() ? null : new ModSettings(id, guid, fields);
    }

    private static Field field(JSONObject f) throws JSONException {
        String key = f.getString("key");
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("bad key");
        Type type = Type.valueOf(f.getString("type").toUpperCase(Locale.ROOT));
        String label = cap(f.optString("label", key), 60);
        if (label.isEmpty()) label = key;
        String summary = cap(f.optString("summary", ""), 140);
        String section = cap(f.optString("section", "General"), 40);
        if (section.isEmpty() || section.contains("[") || section.contains("]") || section.contains("\n")) section = "General";
        double min = 0, max = 100, step = 1;
        List<Choice> choices = new ArrayList<>();
        int maxLength = 60;
        String def;
        switch (type) {
            case BOOL:
                def = f.optBoolean("default", false) ? "true" : "false";
                break;
            case INT:
            case FLOAT: {
                min = f.optDouble("min", 0);
                max = f.optDouble("max", 100);
                if (!(min < max) || Double.isInfinite(min) || Double.isInfinite(max)) throw new IllegalArgumentException("bad range");
                step = f.optDouble("step", type == Type.INT ? 1 : (max - min) / 100);
                if (!(step > 0)) step = type == Type.INT ? 1 : (max - min) / 100;
                double d = Math.max(min, Math.min(max, f.optDouble("default", min)));
                def = type == Type.INT ? Long.toString(Math.round(d)) : trimmed(d);
                break;
            }
            case CHOICE: {
                JSONArray cs = f.getJSONArray("choices");
                for (int i = 0; i < cs.length() && choices.size() < MAX_CHOICES; i++) {
                    Object c = cs.get(i);
                    if (c instanceof JSONObject) {
                        String value = ((JSONObject) c).getString("value");
                        choices.add(new Choice(cap(value, 60), cap(((JSONObject) c).optString("label", value), 60)));
                    } else {
                        choices.add(new Choice(cap(String.valueOf(c), 60), cap(String.valueOf(c), 60)));
                    }
                }
                if (choices.isEmpty()) throw new IllegalArgumentException("no choices");
                String d = f.optString("default", choices.get(0).value);
                def = choices.get(0).value;
                for (Choice c : choices) if (c.value.equals(d)) def = d;
                break;
            }
            default:
                maxLength = Math.max(1, Math.min(200, f.optInt("maxLength", 60)));
                def = cap(f.optString("default", ""), maxLength);
        }
        return new Field(key, type, label, summary, def, min, max, step, choices, section, maxLength);
    }

    private static String cap(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** A number as short text: 1.0 is "1", 0.5 stays "0.5". */
    static String trimmed(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    /** Makes a file name usable as a settings id. */
    public static String sanitize(String name) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (s.isEmpty() || !Character.isLetterOrDigit(s.charAt(0))) s = "m" + s;
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    // ---- how values are written for other code ---------------------------------------------------

    /**
     * The values as the plain text native mods read: one {@code key=value} per line, a backslash and a newline written
     * as {@code \\\\} and {@code \\n}. Every field is written, with its default when the player has not changed it.
     */
    public String toNativeText(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        for (Field f : fields) {
            String v = values.get(f.key);
            if (v == null || !f.accepts(v)) v = f.defaultValue;
            sb.append(f.key).append('=').append(v.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")).append('\n');
        }
        return sb.toString();
    }

    /** The same values as a BepInEx config file, so {@code Config.Bind} returns them. Empty when there is no GUID. */
    public String toBepInExConfig(Map<String, String> values) {
        if (guid == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("## Settings file written by SBAModLoader from the mod's settings screen.\n");
        sb.append("## Plugin GUID: ").append(guid).append("\n\n");
        Map<String, List<Field>> sections = new LinkedHashMap<>();
        for (Field f : fields) sections.computeIfAbsent(f.section, k -> new ArrayList<>()).add(f);
        for (Map.Entry<String, List<Field>> s : sections.entrySet()) {
            sb.append('[').append(s.getKey()).append("]\n\n");
            for (Field f : s.getValue()) {
                String v = values.get(f.key);
                if (v == null || !f.accepts(v)) v = f.defaultValue;
                // A BepInEx value is one line.
                sb.append(f.key).append(" = ").append(v.replace("\r", " ").replace("\n", " ")).append("\n\n");
            }
        }
        return sb.toString();
    }
}
