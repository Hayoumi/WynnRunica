package com.WynnRunica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatMessageCatalog {
    private static final Pattern LEADING_LAYOUT = Pattern.compile(
            "^((?:§(?:#[0-9a-fA-F]{6}|[0-9a-fA-FklmnorKLMNOR]))*)[ \\t]+");
    private static final Pattern SLOT = Pattern.compile("<(num|actor|territory)>");
    private static final Pattern PLURAL = Pattern.compile("<pl:([^|<>]*)\\|([^|<>]*)\\|([^|<>]*)>");
    private static final Pattern CODE = Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)");
    private static final Pattern NOT_TEXT = Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)|<em>|\\s+|<(?:num|actor|territory)>");
    private static volatile List<Rule> rules = List.of();

    private static final class Line {
        Pattern pattern;
        List<String> slots = new ArrayList<>();
        String ru;
        String lead = "";
        int baseSlot = -1;
        int baseOffset;
    }

    private static final class Rule {
        List<Line> lines = new ArrayList<>();
        int slots;
    }

    private ChatMessageCatalog() {}

    static void reload() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica/chat/messages.json");
        TranslationLoader.copyBundledJsonIfMissing("chat", file.getParent());
        if (!Files.isRegularFile(file)) throw new IllegalStateException("chat/messages.json is missing");
        loadFrom(file);
    }

    static void loadFrom(Path file) {
        List<Rule> loaded = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 2 || !"chat-messages".equals(root.get("domain").getAsString())) {
                throw new IllegalArgumentException("invalid chat catalog");
            }
            for (JsonElement item : root.getAsJsonArray("entries")) {
                Rule rule = new Rule();
                for (JsonElement element : item.getAsJsonObject().getAsJsonArray("lines")) {
                    JsonObject row = element.getAsJsonObject();
                    Line line = line(normalizeLayout(row.get("en").getAsString()), row.get("ru").getAsString());
                    rule.lines.add(line);
                    rule.slots += line.slots.size();
                }
                if (!rule.lines.isEmpty()) loaded.add(rule);
            }
        } catch (Exception error) {
            throw new IllegalStateException("invalid chat/messages.json: " + error.getMessage(), error);
        }
        rules = loaded;
    }

    private static Line line(String en, String ru) {
        Line line = new Line();
        line.ru = ru;
        StringBuilder regex = new StringBuilder();
        Matcher slot = SLOT.matcher(en);
        int at = 0;
        while (slot.find()) {
            regex.append(Pattern.quote(en.substring(at, slot.start())));
            if (slot.group(1).equals("num")) {
                regex.append("([+\\-]?\\d+(?:[.,/]\\d+)*)");
            } else {
                regex.append("(.{1,64}?)");
            }
            line.slots.add(slot.group(1));
            at = slot.end();
        }
        regex.append(Pattern.quote(en.substring(at)));
        line.pattern = Pattern.compile(regex.toString());
        findBase(line, en);
        return line;
    }

    private static void findBase(Line line, String en) {
        Matcher skip = NOT_TEXT.matcher(en);
        int at = 0;
        int slots = 0;
        int slotEnd = 0;
        int leadEnd = -1;
        while (at < en.length() && skip.region(at, en.length()).lookingAt()) {
            if (SLOT.matcher(skip.group()).matches()) {
                if (leadEnd < 0) leadEnd = at;
                slots++;
                slotEnd = skip.end();
            }
            at = skip.end();
        }
        if (leadEnd < 0) leadEnd = at;
        line.lead = en.substring(0, leadEnd).stripTrailing();
        if (at == en.length()) return;
        line.baseSlot = slots;
        line.baseOffset = at - slotEnd;
    }

    static String translate(String message, String[] codes) {
        String[] lines = message.split("\n", -1);
        String best = null;
        int bestSlots = Integer.MAX_VALUE;
        boolean conflict = false;
        for (Rule rule : rules) {
            if (rule.lines.size() != lines.length) continue;
            String candidate = apply(rule, message, lines, codes);
            if (candidate == null) continue;
            if (rule.slots < bestSlots) {
                bestSlots = rule.slots;
                best = candidate;
                conflict = false;
            } else if (rule.slots == bestSlots && !candidate.equals(best)) {
                conflict = true;
            }
        }
        if (conflict) return message;
        return best;
    }

    private static String apply(Rule rule, String message, String[] lines, String[] codes) {
        StringBuilder output = new StringBuilder();
        boolean translated = false;
        int lineStart = 0;
        for (int i = 0; i < lines.length; i++) {
            Line line = rule.lines.get(i);
            String normalized = normalizeLayout(lines[i]);
            Matcher found = line.pattern.matcher(normalized);
            if (!found.matches()) return null;
            if (i > 0) output.append("\n§r");
            int lineEnd = lineStart + lines[i].length();
            if (line.ru.isBlank()) {
                output.append(colored(message, lineStart, lineEnd, codes, ""));
            } else {
                translated = true;
                Matcher layout = LEADING_LAYOUT.matcher(lines[i]);
                if (layout.find()) output.append(lines[i], 0, layout.end());
                output.append(fill(line, found, message, codes, lineEnd - normalized.length()));
            }
            lineStart = lineEnd + 1;
        }
        if (!translated) return message;
        return output.toString();
    }

    private static String fill(Line line, Matcher found, String message, String[] codes, int shift) {
        String base = "";
        if (line.baseSlot == 0) base = codes[shift + line.baseOffset];
        if (line.baseSlot > 0) base = codes[shift + found.end(line.baseSlot) + line.baseOffset];

        StringBuilder out = new StringBuilder();
        int at = 0;
        if (line.ru.startsWith(line.lead)) at = line.lead.length();
        out.append(line.ru, 0, at).append(base);

        String active = base;
        String number = null;
        boolean[] used = new boolean[line.slots.size()];
        Matcher slot = SLOT.matcher(line.ru);
        while (slot.find()) {
            String piece = line.ru.substring(at, slot.start());
            out.append(plural(piece, number));
            active = activeCode(active, piece);
            String value = slot.group();
            for (int i = 0; i < used.length; i++) {
                if (used[i] || !line.slots.get(i).equals(slot.group(1))) continue;
                used[i] = true;
                value = colored(message, shift + found.start(i + 1), shift + found.end(i + 1), codes, active);
                if (slot.group(1).equals("num")) number = found.group(i + 1);
                break;
            }
            out.append(value);
            at = slot.end();
        }
        return out.append(plural(line.ru.substring(at), number)).toString();
    }

    // <pl:попытка|попытки|попыток> выбирает форму по числу, которое стоит в переводе перед ним.
    private static String plural(String piece, String number) {
        Matcher token = PLURAL.matcher(piece);
        StringBuilder out = new StringBuilder();
        while (token.find()) {
            String[] forms = {token.group(1), token.group(2), token.group(3)};
            token.appendReplacement(out, Matcher.quoteReplacement(TranslationManager.pluralForm(number, forms)));
        }
        return token.appendTail(out).toString();
    }

    private static String colored(String message, int start, int end, String[] codes, String active) {
        StringBuilder out = new StringBuilder();
        String last = active;
        for (int i = start; i < end; i++) {
            if (!codes[i].isEmpty() && !codes[i].equals(last)) {
                out.append(codes[i]);
                last = codes[i];
            }
            out.append(message.charAt(i));
        }
        if (!last.equals(active)) out.append(active);
        return out.toString();
    }

    private static String activeCode(String active, String piece) {
        Matcher code = CODE.matcher(piece);
        while (code.find()) {
            char kind = Character.toLowerCase(code.group().charAt(1));
            boolean color = kind == '#' || kind == 'r' || Character.digit(kind, 16) >= 0;
            if (color) active = code.group();
            if (!color) active += code.group();
        }
        return active;
    }

    static String normalizeLayout(String line) {
        if (line.isBlank()) return "";
        return LEADING_LAYOUT.matcher(line).replaceFirst("$1");
    }
}
