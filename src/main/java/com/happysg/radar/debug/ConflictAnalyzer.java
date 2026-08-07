package com.happysg.radar.debug;

import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Produces evidence-based candidates without loading foreign classes. */
public final class ConflictAnalyzer {
    private static final String CREATE_RADAR_PREFIX =
            "com.happysg.radar.";
    private static final Pattern STACK_FRAME = Pattern.compile(
            "(?m)^\\s*at\\s+([A-Za-z0-9_.$/]+)\\.([A-Za-z0-9_$<>]+)\\(");
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "Inject", "Redirect", "ModifyArg", "ModifyArgs",
            "ModifyConstant", "ModifyVariable", "ModifyExpressionValue",
            "WrapOperation", "WrapWithCondition", "ModifyReceiver",
            "WrapMethod", "Overwrite", "Accessor", "Invoker");
    private static final Set<String> HIGH_RISK_KINDS = Set.of(
            "Redirect", "Overwrite", "WrapOperation", "WrapMethod",
            "ModifyConstant", "ModifyVariable", "ModifyArgs", "ModifyArg",
            "ModifyExpressionValue", "ModifyReceiver");

    private ConflictAnalyzer() {
    }

    public static ConflictAnalysisSnapshot capture(
            List<DiagnosticEvent> events) {
        ArrayList<String> warnings = new ArrayList<>();
        try {
            ModClassOwnershipIndex ownership =
                    ModClassOwnershipIndex.capture();
            LinkedHashSet<String> relevant = relevantClasses(events,
                    ownership);
            MixinResult mixins = inspectMixins(relevant, ownership, warnings);
            relevant.addAll(mixins.targets());
            ArrayList<ClassOwnership> classOwnership = new ArrayList<>();
            for (String className : relevant) {
                ClassOwnership description = ownership.describe(className);
                if (!description.owners().isEmpty()) {
                    classOwnership.add(description);
                }
            }
            classOwnership.sort(Comparator.comparing(
                    ClassOwnership::className));
            List<ConflictCandidate> candidates = rank(events,
                    mixins.overlaps(), classOwnership, ownership);
            return new ConflictAnalysisSnapshot(
                    warnings.isEmpty() ? "complete" : "partial",
                    candidates, mixins.audit(), mixins.overlaps(),
                    classOwnership, warnings);
        } catch (RuntimeException | LinkageError failure) {
            DiagnosticRecorder.error("conflict_analysis", "capture",
                    "conflict_analysis_failed", failure, null, null);
            return ConflictAnalysisSnapshot.unavailable(
                    failure.getClass().getSimpleName() + ": "
                            + DiagnosticRecorder.sanitize(
                            failure.getMessage(), 160));
        }
    }

    private static LinkedHashSet<String> relevantClasses(
            List<DiagnosticEvent> events,
            ModClassOwnershipIndex ownership) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(ownership.createRadarClasses());
        for (DiagnosticEvent event : events) {
            result.addAll(stackClasses(event.stackTrace()));
        }
        for (EarlyDiagnosticJournal.EarlyEvent event
                : EarlyDiagnosticJournal.snapshot()) {
            String target = event.details().get("target");
            if (target != null && !target.isBlank()) result.add(target);
        }
        return result;
    }

    private static MixinResult inspectMixins(Set<String> targets,
                                             ModClassOwnershipIndex ownership,
                                             List<String> warnings) {
        ArrayList<MixinAuditEntry> audit = new ArrayList<>();
        LinkedHashMap<String, List<MixinAuditEntry>> byTarget =
                new LinkedHashMap<>();
        LinkedHashSet<String> allTargets = new LinkedHashSet<>(targets);
        for (String target : List.copyOf(allTargets)) {
            Set<IMixinInfo> applied;
            try {
                applied = Mixins.getMixinsForClass(target);
            } catch (RuntimeException | LinkageError failure) {
                warnings.add("Unable to inspect applied mixins for " + target
                        + ": " + failure.getClass().getSimpleName());
                continue;
            }
            for (IMixinInfo info : applied) {
                MixinAuditEntry entry = auditEntry(target, info, ownership,
                        warnings);
                audit.add(entry);
                byTarget.computeIfAbsent(target,
                        ignored -> new ArrayList<>()).add(entry);
            }
        }

        ArrayList<MixinOverlap> overlaps = new ArrayList<>();
        for (Map.Entry<String, List<MixinAuditEntry>> targetEntry
                : byTarget.entrySet()) {
            String target = targetEntry.getKey();
            List<MixinAuditEntry> entries = targetEntry.getValue();
            List<MixinAuditEntry> ours = entries.stream()
                    .filter(MixinAuditEntry::createRadarMixin).toList();
            List<MixinAuditEntry> foreign = entries.stream()
                    .filter(entry -> !entry.createRadarMixin()).toList();
            for (MixinAuditEntry other : foreign) {
                if (ours.isEmpty() && ownership.modIds(target)
                        .contains("create_radar")) {
                    overlaps.add(targetOwnedOverlap(target, other));
                }
                for (MixinAuditEntry own : ours) {
                    overlaps.add(compare(own, other));
                }
            }
        }
        audit.sort(Comparator.comparing(MixinAuditEntry::targetClass)
                .thenComparing(MixinAuditEntry::mixinClass));
        overlaps.sort(Comparator
                .comparing((MixinOverlap value) -> value.risk().ordinal())
                .reversed().thenComparing(MixinOverlap::targetClass)
                .thenComparing(MixinOverlap::foreignMixin));
        return new MixinResult(List.copyOf(audit), List.copyOf(overlaps),
                Set.copyOf(allTargets));
    }

    private static MixinAuditEntry auditEntry(String target, IMixinInfo info,
                                              ModClassOwnershipIndex ownership,
                                              List<String> warnings) {
        ArrayList<MixinAuditEntry.Injection> injections = new ArrayList<>();
        try {
            ClassNode node = info.getClassNode(
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                collectAnnotations(method, method.visibleAnnotations,
                        injections);
                collectAnnotations(method, method.invisibleAnnotations,
                        injections);
            }
        } catch (RuntimeException | LinkageError failure) {
            warnings.add("Unable to inspect mixin " + info.getClassName()
                    + ": " + failure.getClass().getSimpleName());
        }
        boolean ours = info.getClassName().startsWith(CREATE_RADAR_PREFIX);
        return new MixinAuditEntry(target, info.getClassName(),
                info.getConfig().getName(), ownership.modIds(
                info.getClassName()), info.getPriority(), ours,
                ownership.modIds(target).contains("create_radar"), injections);
    }

    private static void collectAnnotations(MethodNode method,
                                           List<AnnotationNode> annotations,
                                           List<MixinAuditEntry.Injection> out) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            String kind = annotationKind(annotation.desc);
            if (!INJECTION_ANNOTATIONS.contains(kind)) continue;
            Map<String, Object> values = annotationValues(annotation);
            List<String> methods = strings(values.get("method"));
            if (kind.equals("Overwrite") && methods.isEmpty()) {
                methods = List.of(method.name + method.desc);
            }
            if ((kind.equals("Accessor") || kind.equals("Invoker"))
                    && methods.isEmpty()) {
                Object value = values.get("value");
                methods = value instanceof String string && !string.isBlank()
                        ? List.of(string) : List.of(method.name);
            }
            ArrayList<String> at = new ArrayList<>();
            collectAt(values.get("at"), at);
            boolean cancellable = Boolean.TRUE.equals(values.get("cancellable"));
            out.add(new MixinAuditEntry.Injection(method.name, kind, methods,
                    at, cancellable));
        }
    }

    static List<MixinAuditEntry.Injection> inspectClassNodeForTest(
            ClassNode node) {
        ArrayList<MixinAuditEntry.Injection> result = new ArrayList<>();
        for (MethodNode method : node.methods) {
            collectAnnotations(method, method.visibleAnnotations, result);
            collectAnnotations(method, method.invisibleAnnotations, result);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> annotationValues(
            AnnotationNode annotation) {
        if (annotation.values == null) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            result.put(String.valueOf(annotation.values.get(index)),
                    annotation.values.get(index + 1));
        }
        return result;
    }

    private static List<String> strings(Object value) {
        if (value instanceof String string) return List.of(string);
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().filter(String.class::isInstance)
                .map(String.class::cast).toList();
    }

    private static void collectAt(Object value, List<String> output) {
        if (value instanceof AnnotationNode annotation) {
            Map<String, Object> values = annotationValues(annotation);
            String atValue = String.valueOf(values.getOrDefault("value", ""));
            String target = String.valueOf(values.getOrDefault("target", ""));
            output.add(target.isBlank() ? atValue : atValue + ":" + target);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectAt(item, output));
        }
    }

    private static String annotationKind(String descriptor) {
        try {
            String name = Type.getType(descriptor).getClassName();
            return name.substring(name.lastIndexOf('.') + 1);
        } catch (RuntimeException ignored) {
            return descriptor;
        }
    }

    private static MixinOverlap compare(MixinAuditEntry own,
                                        MixinAuditEntry foreign) {
        LinkedHashSet<String> ownMethods = selectors(own.injections());
        LinkedHashSet<String> foreignMethods = selectors(foreign.injections());
        LinkedHashSet<String> shared = new LinkedHashSet<>();
        for (String ours : ownMethods) {
            for (String theirs : foreignMethods) {
                if (sameMethod(ours, theirs)) shared.add(methodName(ours));
            }
        }
        Set<String> foreignKinds = foreign.injections().stream()
                .map(MixinAuditEntry.Injection::kind)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
        boolean highRisk = foreignKinds.stream().anyMatch(HIGH_RISK_KINDS::contains)
                || own.injections().stream().map(
                MixinAuditEntry.Injection::kind).anyMatch(HIGH_RISK_KINDS::contains);
        boolean cancellable = foreign.injections().stream()
                .anyMatch(MixinAuditEntry.Injection::cancellable);
        MixinOverlap.Risk risk = shared.isEmpty() ? MixinOverlap.Risk.LOW
                : highRisk ? MixinOverlap.Risk.HIGH
                : cancellable ? MixinOverlap.Risk.MEDIUM
                : MixinOverlap.Risk.MEDIUM;
        return new MixinOverlap(own.targetClass(), own.mixinClass(),
                foreign.mixinClass(), foreign.ownerMods(), risk,
                List.copyOf(shared), List.copyOf(foreignKinds),
                own.priority(), foreign.priority());
    }

    private static MixinOverlap targetOwnedOverlap(String target,
                                                   MixinAuditEntry foreign) {
        List<String> kinds = foreign.injections().stream()
                .map(MixinAuditEntry.Injection::kind).distinct().toList();
        MixinOverlap.Risk risk = kinds.stream().anyMatch(HIGH_RISK_KINDS::contains)
                ? MixinOverlap.Risk.MEDIUM : MixinOverlap.Risk.LOW;
        return new MixinOverlap(target, "<create_radar_target_class>",
                foreign.mixinClass(), foreign.ownerMods(), risk, List.of(),
                kinds, 0, foreign.priority());
    }

    private static LinkedHashSet<String> selectors(
            List<MixinAuditEntry.Injection> injections) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        injections.forEach(injection -> result.addAll(
                injection.methodSelectors()));
        return result;
    }

    private static boolean sameMethod(String first, String second) {
        if (first.equals(second)) return true;
        return methodName(first).equals(methodName(second));
    }

    private static String methodName(String selector) {
        String value = selector == null ? "" : selector;
        int ownerSeparator = value.lastIndexOf(';');
        if (ownerSeparator >= 0) value = value.substring(ownerSeparator + 1);
        int descriptor = value.indexOf('(');
        if (descriptor >= 0) value = value.substring(0, descriptor);
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(0, colon);
        return value.trim();
    }

    private static List<ConflictCandidate> rank(
            List<DiagnosticEvent> events, List<MixinOverlap> overlaps,
            List<ClassOwnership> classes,
            ModClassOwnershipIndex ownership) {
        HashMap<String, CandidateBuilder> candidates = new HashMap<>();
        Set<String> duplicateClasses = classes.stream()
                .filter(ClassOwnership::duplicate)
                .map(ClassOwnership::className).collect(java.util.stream.Collectors.toSet());

        for (DiagnosticEvent event : events) {
            for (String explicit : event.implicatedMods()) {
                add(candidates, explicit, ownership, new ConflictEvidence(
                        "explicit_boundary", 60,
                        "Create Radar recorded this mod at the failing boundary",
                        event.stackFingerprint(), "", event.operation()));
            }
            int foreignDepth = 0;
            for (StackFrame frame : stackFrames(event.stackTrace())) {
                for (ClassOwnership.Owner owner : ownership.owners(
                        frame.className())) {
                    if (owner.modId().equals("create_radar")) continue;
                    int weight = foreignDepth == 0 ? 80 : 45;
                    add(candidates, owner.modId(), ownership,
                            new ConflictEvidence("stack_owner", weight,
                                    "Owns stack frame " + frame.className()
                                            + "." + frame.methodName(),
                                    event.stackFingerprint(), frame.className(),
                                    frame.methodName()));
                    if (duplicateClasses.contains(frame.className())) {
                        add(candidates, owner.modId(), ownership,
                                new ConflictEvidence("duplicate_class", 70,
                                        "Shares ownership of relevant class "
                                                + frame.className(),
                                        event.stackFingerprint(),
                                        frame.className(), frame.methodName()));
                    }
                    foreignDepth++;
                }
            }
        }

        for (MixinOverlap overlap : overlaps) {
            int weight = switch (overlap.risk()) {
                case HIGH -> 60;
                case MEDIUM -> 45;
                case LOW -> 20;
            };
            for (String modId : overlap.foreignMods()) {
                add(candidates, modId, ownership, new ConflictEvidence(
                        "mixin_overlap", weight,
                        overlap.risk().name().toLowerCase(Locale.ROOT)
                                + "-risk applied mixin overlap with "
                                + overlap.createRadarMixin(), "",
                        overlap.targetClass(),
                        String.join(",", overlap.sharedMethods())));
            }
        }

        for (EarlyDiagnosticJournal.EarlyEvent event
                : EarlyDiagnosticJournal.snapshot()) {
            if (!event.status().equals("ERROR")) continue;
            String mixinClass = event.details().getOrDefault("mixin", "");
            for (String modId : ownership.modIds(mixinClass)) {
                if (modId.equals("create_radar")) continue;
                add(candidates, modId, ownership, new ConflictEvidence(
                        "mixin_apply_error", 100,
                        "Mixin prepare/apply error named " + mixinClass,
                        event.details().getOrDefault("fingerprint", ""),
                        event.details().getOrDefault("target", ""), ""));
            }
        }

        return candidates.values().stream().map(CandidateBuilder::build)
                .sorted(Comparator.comparingInt(ConflictCandidate::score)
                        .reversed().thenComparing(ConflictCandidate::modId))
                .limit(64).toList();
    }

    private static void add(Map<String, CandidateBuilder> candidates,
                            String modId,
                            ModClassOwnershipIndex ownership,
                            ConflictEvidence evidence) {
        if (modId == null || modId.isBlank()
                || modId.equals("create_radar")) return;
        candidates.computeIfAbsent(modId, id -> new CandidateBuilder(id,
                ownership.version(id))).add(evidence);
    }

    private static List<String> stackClasses(String stack) {
        return stackFrames(stack).stream().map(StackFrame::className)
                .distinct().toList();
    }

    private static List<StackFrame> stackFrames(String stack) {
        if (stack == null || stack.isBlank()) return List.of();
        ArrayList<StackFrame> result = new ArrayList<>();
        Matcher matcher = STACK_FRAME.matcher(stack);
        while (matcher.find() && result.size() < 64) {
            result.add(new StackFrame(matcher.group(1).replace('/', '.'),
                    matcher.group(2)));
        }
        return result;
    }

    private record StackFrame(String className, String methodName) {
    }

    private record MixinResult(List<MixinAuditEntry> audit,
                               List<MixinOverlap> overlaps,
                               Set<String> targets) {
    }

    private static final class CandidateBuilder {
        private final String modId;
        private final String version;
        private final LinkedHashMap<String, ConflictEvidence> evidence =
                new LinkedHashMap<>();

        private CandidateBuilder(String modId, String version) {
            this.modId = modId;
            this.version = version;
        }

        private void add(ConflictEvidence value) {
            String key = value.type() + '|' + value.summary() + '|'
                    + value.eventFingerprint();
            evidence.putIfAbsent(key, value);
        }

        private ConflictCandidate build() {
            Map<String, Integer> strongestByType = new HashMap<>();
            for (ConflictEvidence value : evidence.values()) {
                strongestByType.merge(value.type(), value.weight(), Math::max);
            }
            int score = Math.min(100, strongestByType.values().stream()
                    .mapToInt(Integer::intValue).sum());
            boolean hasRuntime = evidence.values().stream().anyMatch(value ->
                    value.type().equals("stack_owner")
                            || value.type().equals("explicit_boundary")
                            || value.type().equals("mixin_apply_error"));
            List<String> counter = hasRuntime ? List.of() : List.of(
                    "No recorded runtime failure directly names this mod");
            return new ConflictCandidate(modId, version, score,
                    ConflictConfidence.fromScore(score),
                    List.copyOf(evidence.values()), counter);
        }
    }
}
