package com.github.exidcuter.dockerregistryexplorer.ui;

import org.tdfl.docker.model.LoginCredentials;

import java.util.concurrent.Executor;

final class RegistryConnectionTestRunner {
    interface Listener {
        void testingStarted();

        void testingSucceeded();

        void testingFailed(String message);
    }

    private final RegistryConnectionTester tester;
    private final Executor workerExecutor;
    private final Executor uiExecutor;
    private final Listener listener;

    private long generation;
    private boolean disposed;

    RegistryConnectionTestRunner(
            RegistryConnectionTester tester,
            Executor workerExecutor,
            Executor uiExecutor,
            Listener listener
    ) {
        this.tester = tester;
        this.workerExecutor = workerExecutor;
        this.uiExecutor = uiExecutor;
        this.listener = listener;
    }

    void testConnection(LoginCredentials credentials) {
        if (disposed) {
            return;
        }
        long requestGeneration = ++generation;
        listener.testingStarted();
        workerExecutor.execute(() -> {
            try {
                tester.testConnection(credentials);
                uiExecutor.execute(() -> complete(requestGeneration, listener::testingSucceeded));
            } catch (Exception exception) {
                uiExecutor.execute(() -> complete(
                        requestGeneration,
                        () -> listener.testingFailed(errorMessage(exception))
                ));
            }
        });
    }

    void invalidate() {
        generation++;
    }

    void dispose() {
        disposed = true;
        generation++;
    }

    private void complete(long requestGeneration, Runnable callback) {
        if (!disposed && generation == requestGeneration) {
            callback.run();
        }
    }

    private static String errorMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isEmpty()
                ? exception.toString()
                : exception.getMessage();
    }
}
