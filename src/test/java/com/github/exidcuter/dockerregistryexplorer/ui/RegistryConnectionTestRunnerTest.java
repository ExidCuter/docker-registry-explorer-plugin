package com.github.exidcuter.dockerregistryexplorer.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tdfl.docker.model.LoginCredentials;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistryConnectionTestRunnerTest {
    private final ControlledExecutor worker = new ControlledExecutor();
    private final FakeTester tester = new FakeTester();
    private final RecordingListener listener = new RecordingListener();
    private RegistryConnectionTestRunner runner;

    @BeforeEach
    void setUp() {
        runner = new RegistryConnectionTestRunner(tester, worker, Runnable::run, listener);
    }

    @Test
    void testsCredentialsOffTheCallingThreadAndReportsSuccess() {
        runner.testConnection(credentials());

        assertEquals(1, listener.started);
        assertEquals(0, tester.calls);

        worker.runNext();

        assertEquals(1, tester.calls);
        assertEquals(1, listener.succeeded);
        assertEquals(List.of(), listener.failures);
    }

    @Test
    void reportsAConnectionFailure() {
        tester.failure = new IllegalStateException("Authentication failed");

        runner.testConnection(credentials());
        worker.runNext();

        assertEquals(List.of("Authentication failed"), listener.failures);
        assertEquals(0, listener.succeeded);
    }

    @Test
    void ignoresResultsForCredentialsThatHaveSinceChanged() {
        runner.testConnection(credentials());
        runner.invalidate();

        worker.runNext();

        assertEquals(0, listener.succeeded);
        assertEquals(List.of(), listener.failures);
    }

    @Test
    void ignoresResultsAfterTheDialogIsDisposed() {
        runner.testConnection(credentials());
        runner.dispose();

        worker.runNext();

        assertEquals(0, listener.succeeded);
        assertEquals(List.of(), listener.failures);
    }

    private static LoginCredentials credentials() {
        return LoginCredentials.builder()
                .registryURL("https://registry.example.com")
                .username("user")
                .password("password")
                .build();
    }

    private static final class ControlledExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.addLast(task);
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static final class FakeTester implements RegistryConnectionTester {
        private Exception failure;
        private int calls;

        @Override
        public void testConnection(LoginCredentials credentials) throws Exception {
            calls++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class RecordingListener implements RegistryConnectionTestRunner.Listener {
        private final List<String> failures = new ArrayList<>();
        private int started;
        private int succeeded;

        @Override
        public void testingStarted() {
            started++;
        }

        @Override
        public void testingSucceeded() {
            succeeded++;
        }

        @Override
        public void testingFailed(String message) {
            failures.add(message);
        }
    }
}
