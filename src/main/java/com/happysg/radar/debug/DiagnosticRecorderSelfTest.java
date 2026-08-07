package com.happysg.radar.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DiagnosticRecorderSelfTest {
    private DiagnosticRecorderSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = runChecks();
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Diagnostic recorder self-test failures: " + failures);
        }
        System.out.println("PASS diagnostic recorder checks");
    }

    public static List<String> runChecks() throws Exception {
        ArrayList<String> failures = new ArrayList<>();
        checkDeduplication(failures);
        checkCapacity(failures);
        checkRedaction(failures);
        checkPrivacyRedaction(failures);
        checkConcurrentRecording(failures);
        DiagnosticRecorder.clear();
        return List.copyOf(failures);
    }

    private static void checkDeduplication(List<String> failures) {
        DiagnosticRecorder.clear();
        RuntimeException failure = new RuntimeException("expected");
        DiagnosticRecorder.warn("test", "dedupe", "same", failure,
                null, null, "optional_mod");
        DiagnosticRecorder.warn("test", "dedupe", "same", failure,
                null, null, "optional_mod");
        List<DiagnosticEvent> snapshot = DiagnosticRecorder.snapshot();
        if (snapshot.size() != 1 || snapshot.getFirst().occurrences() != 2L) {
            failures.add("identical events were not deduplicated");
        }
    }

    private static void checkCapacity(List<String> failures) {
        DiagnosticRecorder.clear();
        for (int index = 0; index < DiagnosticRecorder.MAX_GROUPS + 25;
             index++) {
            DiagnosticRecorder.warn("capacity", "event_" + index,
                    "bounded", null, null, null);
        }
        if (DiagnosticRecorder.snapshot().size()
                != DiagnosticRecorder.MAX_GROUPS) {
            failures.add("event store did not enforce its capacity");
        }
    }

    private static void checkRedaction(List<String> failures) {
        DiagnosticEntry entry = new DiagnosticEntry("secret id",
                "must-not-escape", DiagnosticSeverity.INFO);
        if (!"[redacted]".equals(entry.value())) {
            failures.add("sensitive diagnostic key was not redacted");
        }
    }

    private static void checkPrivacyRedaction(List<String> failures) {
        DiagnosticRecorder.clear();
        DiagnosticRecorder.warn("privacy", "failure", "safe",
                new RuntimeException("C:\\Users\\Example\\instance "
                        + "123e4567-e89b-12d3-a456-426614174000 "
                        + "127.0.0.1:25565"), null, null);
        DiagnosticEvent event = DiagnosticRecorder.snapshot().getFirst();
        String combined = event.exceptionMessage() + event.stackTrace();
        if (combined.contains("Example")
                || combined.contains("123e4567")
                || combined.contains("127.0.0.1")) {
            failures.add("path, UUID, or address escaped privacy redaction");
        }
    }

    private static void checkConcurrentRecording(List<String> failures)
            throws Exception {
        DiagnosticRecorder.clear();
        int workers = 8;
        int writesPerWorker = 100;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(workers);
        for (int worker = 0; worker < workers; worker++) {
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int index = 0; index < writesPerWorker; index++) {
                        DiagnosticRecorder.warn("concurrency", "write",
                                "same_event", null, null, null);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean completed = finished.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        List<DiagnosticEvent> snapshot = DiagnosticRecorder.snapshot();
        if (!completed || snapshot.size() != 1
                || snapshot.getFirst().occurrences()
                != (long) workers * writesPerWorker) {
            failures.add("concurrent events were lost or split");
        }
    }
}
