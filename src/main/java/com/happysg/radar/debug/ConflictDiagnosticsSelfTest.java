package com.happysg.radar.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ConflictDiagnosticsSelfTest {
    private ConflictDiagnosticsSelfTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path temporary = Files.createTempDirectory(
                "create-radar-conflict-selftest-").toAbsolutePath().normalize();
        String previousUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", temporary.toString());
            testConfidence();
            testEarlyJournal();
            testMixinParser();
            testAppliedMixinDiscovery();
            testMixinAuditConsistency();
            testTraceBounds();
            testLogFiltering(temporary);
            testComparator(temporary);
            System.out.println("PASS conflict diagnostics checks");
        } finally {
            if (previousUserDirectory != null) {
                System.setProperty("user.dir", previousUserDirectory);
            }
            deleteTree(temporary);
        }
    }

    private static void testConfidence() {
        require(ConflictConfidence.fromScore(20) == ConflictConfidence.LOW,
                "low confidence threshold");
        require(ConflictConfidence.fromScore(45) == ConflictConfidence.MEDIUM,
                "medium confidence threshold");
        require(ConflictConfidence.fromScore(80) == ConflictConfidence.HIGH,
                "high confidence threshold");
    }

    private static void testEarlyJournal() throws IOException {
        EarlyDiagnosticJournal.beginBoot("selftest");
        EarlyDiagnosticJournal.recordFailure("MIXIN_APPLY",
                "example.Target", "foreign.Mixin", "foreign.mixins.json",
                new IllegalStateException("token=journal-secret"));
        Path journal = EarlyDiagnosticJournal.diagnosticDirectory()
                .resolve("boot-current.jsonl");
        require(Files.isRegularFile(journal), "early journal created");
        String text = Files.readString(journal, StandardCharsets.UTF_8);
        require(text.contains("MIXIN_APPLY"),
                "early Mixin failure retained");
        require(!text.contains("journal-secret"),
                "early journal secret redacted");
    }

    private static void testMixinParser() {
        ClassNode node = new ClassNode();
        MethodNode inject = new MethodNode(Opcodes.ACC_PRIVATE, "handler",
                "()V", null, null);
        AnnotationNode injectAnnotation = new AnnotationNode(
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        injectAnnotation.values = new ArrayList<>(List.of(
                "method", List.of("fireShot(Lnet/minecraft/Foo;)V"),
                "cancellable", true));
        inject.visibleAnnotations = new ArrayList<>(List.of(injectAnnotation));
        node.methods.add(inject);

        MethodNode redirect = new MethodNode(Opcodes.ACC_PRIVATE, "redirect",
                "()V", null, null);
        AnnotationNode redirectAnnotation = new AnnotationNode(
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        redirectAnnotation.values = new ArrayList<>(List.of(
                "method", List.of("fireShot")));
        redirect.visibleAnnotations = new ArrayList<>(
                List.of(redirectAnnotation));
        node.methods.add(redirect);

        List<MixinAuditEntry.Injection> parsed =
                ConflictAnalyzer.inspectClassNodeForTest(node);
        require(parsed.size() == 2, "two injection annotations parsed");
        require(parsed.getFirst().cancellable(), "cancellable flag parsed");
        require(parsed.stream().anyMatch(value ->
                        value.kind().equals("Redirect")),
                "redirect kind parsed");
    }

    private static void testAppliedMixinDiscovery() {
        String target = "example.Target";
        String secondTarget = "example.SecondTarget";
        String ownClass = "com.happysg.radar.mixin.TestOwnMixin";
        String foreignClass = "foreign.mixin.TestForeignMixin";
        IMixinInfo own = fakeMixin(ownClass, "create_radar.mixins.json",
                List.of(target, secondTarget), injectionNode(
                        "createRadar$handler", "fireShot"));
        IMixinInfo foreign = fakeMixin(foreignClass,
                "foreign.mixins.json", List.of(target), injectionNode(
                        "foreign$handler", "fireShot"));
        Map<String, List<String>> owners = Map.of(
                ownClass, List.of("create_radar"),
                foreignClass, List.of("foreign_mod"));
        ArrayList<String> warnings = new ArrayList<>();

        ConflictAnalyzer.MixinResult result = ConflictAnalyzer.inspectMixins(
                Set.of(target, secondTarget),
                List.of(ownClass, "duplicate.discovery.Key", foreignClass),
                className -> owners.getOrDefault(className, List.of()),
                className -> switch (className) {
                    case "com.happysg.radar.mixin.TestOwnMixin",
                         "duplicate.discovery.Key" -> Set.of(own);
                    case "foreign.mixin.TestForeignMixin" -> Set.of(foreign);
                    default -> Set.of();
                }, warnings);

        require(warnings.isEmpty(), "mixin discovery has no warnings");
        require(result.audit().size() == 3,
                "mixin discovery deduplicates and expands real targets");
        require(result.audit().stream().noneMatch(entry ->
                        entry.targetClass().equals(entry.mixinClass())),
                "mixin discovery does not label lookup key as target");
        require(result.audit().stream().anyMatch(entry ->
                        entry.targetClass().equals(target)
                                && entry.mixinClass().equals(foreignClass)
                                && entry.ownerMods().contains("foreign_mod")),
                "foreign mixin retained on real target");
        require(result.overlaps().size() == 1,
                "shared target and method produce one overlap");
        MixinOverlap overlap = result.overlaps().getFirst();
        require(overlap.targetClass().equals(target)
                        && overlap.createRadarMixin().equals(ownClass)
                        && overlap.foreignMixin().equals(foreignClass)
                        && overlap.sharedMethods().equals(List.of("fireShot")),
                "overlap uses mapped target and method");
    }

    private static void testMixinAuditConsistency() {
        String target = "example.JournalTarget";
        String mixin = "com.happysg.radar.mixin.JournalMixin";
        EarlyDiagnosticJournal.record("MIXIN_APPLY", "POST_APPLY", Map.of(
                "target", target, "mixin", mixin));
        ArrayList<String> warnings = new ArrayList<>();
        ConflictAnalyzer.validateMixinAudit(List.of(), warnings);
        require(warnings.stream().anyMatch(value ->
                        value.contains("Mixin audit omitted 1 of 1")),
                "missing successful mixin application marks partial audit");

        warnings.clear();
        ConflictAnalyzer.validateMixinAudit(List.of(new MixinAuditEntry(
                target, mixin, "create_radar.mixins.json",
                List.of("create_radar"), 1000, true, false, List.of())),
                warnings);
        require(warnings.isEmpty(),
                "matching successful mixin application passes audit");
    }

    private static ClassNode injectionNode(String handler, String target) {
        ClassNode node = new ClassNode();
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, handler,
                "()V", null, null);
        AnnotationNode annotation = new AnnotationNode(
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        annotation.values = new ArrayList<>(List.of(
                "method", List.of(target)));
        method.visibleAnnotations = new ArrayList<>(List.of(annotation));
        node.methods.add(method);
        return node;
    }

    private static IMixinInfo fakeMixin(String className, String configName,
                                        List<String> targets,
                                        ClassNode classNode) {
        IMixinConfig config = (IMixinConfig) Proxy.newProxyInstance(
                ConflictDiagnosticsSelfTest.class.getClassLoader(),
                new Class<?>[]{IMixinConfig.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> configName;
                    case "getTargets" -> Set.copyOf(targets);
                    case "getPriority" -> 1000;
                    case "isRequired" -> false;
                    case "toString" -> configName;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        return (IMixinInfo) Proxy.newProxyInstance(
                ConflictDiagnosticsSelfTest.class.getClassLoader(),
                new Class<?>[]{IMixinInfo.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getConfig" -> config;
                    case "getName", "getClassName" -> className;
                    case "getClassRef" -> className.replace('.', '/');
                    case "getClassNode" -> classNode;
                    case "getTargetClasses" -> targets;
                    case "getPriority" -> 1000;
                    case "isDetachedSuper" -> false;
                    case "toString" -> className;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }

    private static void testTraceBounds() {
        ConflictTraceRecorder.setEnabled(true, null, "selftest");
        for (int index = 0; index < ConflictTraceRecorder.MAX_EVENTS + 50;
             index++) {
            try (ConflictTraceRecorder.Scope scope =
                         ConflictTraceRecorder.begin("test", "operation", null,
                                 null, Map.of("index", Integer.toString(index)))) {
                scope.result("complete");
            }
        }
        ConflictTraceRecorder.Snapshot snapshot =
                ConflictTraceRecorder.snapshot();
        require(snapshot.events().size() == ConflictTraceRecorder.MAX_EVENTS,
                "trace ring bounded");
        require(snapshot.state().droppedEvents() > 0,
                "trace eviction reported");
        require(snapshot.heartbeats().stream().anyMatch(value ->
                        value.operation().endsWith("test/operation")
                                && value.count() >= 1),
                "heartbeat aggregated");
        ConflictTraceRecorder.setEnabled(false, null);
    }

    private static void testLogFiltering(Path temporary) throws IOException {
        Path logs = temporary.resolve("logs");
        Files.createDirectories(logs);
        String secret = "token=super-secret";
        Files.writeString(logs.resolve("latest.log"), String.join("\n",
                "ordinary line",
                "[CHAT] com.happysg.radar should not include player words",
                "MixinApplyError for com.happysg.radar.mixin.Test " + secret,
                "    at foreign.mod.Test.run(Test.java:10)",
                "after context"), StandardCharsets.UTF_8);
        FilteredLogEvidence.Evidence evidence =
                FilteredLogEvidence.capture();
        require(evidence.text().contains("MixinApplyError"),
                "relevant log marker retained");
        require(!evidence.text().contains("player words"),
                "chat excluded");
        require(!evidence.text().contains("super-secret"),
                "log secret redacted");
    }

    private static void testComparator(Path temporary) throws Exception {
        Path baseline = temporary.resolve("baseline.zip");
        Path problem = temporary.resolve("problem.zip");
        writeFixture(baseline, false);
        writeFixture(problem, true);
        DiagnosticReportComparator.Comparison comparison =
                DiagnosticReportComparator.compare(baseline, problem);
        JsonArray candidates = comparison.json()
                .getAsJsonArray("ranked_candidates");
        require(!candidates.isEmpty(), "comparison candidate produced");
        require(candidates.get(0).getAsJsonObject().get("modId")
                        .getAsString().equals("unknown_conflict"),
                "new evidence ranks unknown mod");
        require(comparison.text().contains("unknown_conflict"),
                "text comparison includes candidate");
    }

    private static void writeFixture(Path path, boolean broken)
            throws IOException {
        JsonObject state = new JsonObject();
        state.addProperty("schema", 2);
        state.addProperty("minecraft", "1.21.1");
        state.addProperty("neoforge", "21.1.227");
        JsonArray mods = new JsonArray();
        mods.add(mod("create_radar", "test"));
        if (broken) mods.add(mod("unknown_conflict", "1.0"));
        state.add("loaded_mods", mods);

        JsonObject conflicts = new JsonObject();
        JsonArray candidates = new JsonArray();
        if (broken) {
            JsonObject candidate = new JsonObject();
            candidate.addProperty("modId", "unknown_conflict");
            candidate.addProperty("score", 60);
            candidates.add(candidate);
        }
        conflicts.add("candidates", candidates);

        JsonObject mixins = new JsonObject();
        JsonArray overlaps = new JsonArray();
        if (broken) {
            JsonObject overlap = new JsonObject();
            overlap.addProperty("risk", "HIGH");
            overlap.addProperty("targetClass", "example.Target");
            overlap.addProperty("foreignMixin", "unknown.Mixin");
            JsonArray owners = new JsonArray();
            owners.add("unknown_conflict");
            overlap.add("foreignMods", owners);
            overlaps.add(overlap);
        }
        mixins.add("overlaps", overlaps);

        JsonObject events = new JsonObject();
        events.add("events", new JsonArray());
        JsonObject trace = new JsonObject();
        trace.add("events", new JsonArray());
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            entry(zip, "state.json", state.toString());
            entry(zip, "conflict_analysis.json", conflicts.toString());
            entry(zip, "mixin_audit.json", mixins.toString());
            entry(zip, "events.json", events.toString());
            entry(zip, "trace.json", trace.toString());
        }
    }

    private static JsonObject mod(String id, String version) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("version", version);
        return value;
    }

    private static void entry(ZipOutputStream zip, String name, String value)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("FAIL " + message);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
