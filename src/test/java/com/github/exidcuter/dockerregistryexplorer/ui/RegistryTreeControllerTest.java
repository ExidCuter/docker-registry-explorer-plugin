package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tdfl.docker.DockerRegistry;
import org.tdfl.docker.model.LoginCredentials;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreePath;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTreeControllerTest {
    private final DockerRegistry registry = registry("https://registry.example.com");
    private final FakeOperations operations = new FakeOperations();
    private final ControlledExecutor worker = new ControlledExecutor();
    private final RecordingListener listener = new RecordingListener();

    private RegistryTreeController controller;
    private JTree tree;

    @BeforeEach
    void setUp() throws Exception {
        runOnEdt(() -> {
            controller = new RegistryTreeController(
                    List.of(registry),
                    operations,
                    worker,
                    Runnable::run,
                    listener
            );
            tree = new JTree(controller.getModel());
            controller.attachTree(tree);
            controller.initialize();
            tree.expandPath(path(controller.getRoot()));
        });
    }

    @Test
    void loadsRegistryAndTagsOnlyWhenTheirNodesAreExpanded() throws Exception {
        RegistryTreeNode registryNode = registryNode();

        assertEquals(0, operations.catalogCalls);
        assertEquals(RegistryTreeNode.Kind.PLACEHOLDER, child(registryNode).getKind());

        expandAndLoad(registryNode);

        RegistryTreeNode imageNode = child(registryNode);
        assertEquals(1, operations.catalogCalls);
        assertEquals(RegistryTreeNode.Kind.IMAGE, imageNode.getKind());
        assertEquals(0, operations.tagCalls);

        expandAndLoad(imageNode);

        assertEquals(1, operations.tagCalls);
        assertEquals(RegistryTreeNode.Kind.TAG, child(imageNode).getKind());
        assertEquals("latest", child(imageNode).getValue());
    }

    @Test
    void discardsARefreshResponseFromThePreviousRequest() throws Exception {
        operations.catalogResults.add(List.of("stale-image"));
        operations.catalogResults.add(List.of("current-image"));
        RegistryTreeNode registryNode = registryNode();

        runOnEdt(() -> {
            tree.expandPath(path(registryNode));
            controller.nodeExpanded(registryNode);
            controller.refresh();
        });

        assertEquals(2, worker.pendingCount());
        runOnEdt(worker::runNext);
        assertEquals(RegistryTreeNode.Kind.LOADING, child(registryNode).getKind());

        runOnEdt(worker::runNext);
        assertEquals("current-image", child(registryNode).getValue());
    }

    @Test
    void editDiscardsThePreviousRegistryLoad() throws Exception {
        operations.catalogResults.add(List.of("stale-image"));
        operations.catalogResults.add(List.of("edited-image"));
        RegistryTreeNode registryNode = registryNode();

        runOnEdt(() -> {
            controller.nodeExpanded(registryNode);
            controller.editRegistry(registryNode, registry("https://edited.example.com"));
        });

        assertEquals(2, worker.pendingCount());
        runOnEdt(worker::runNext);
        assertEquals(RegistryTreeNode.Kind.LOADING, child(registryNode).getKind());

        runOnEdt(worker::runNext);
        assertEquals("edited-image", child(registryNode).getValue());
        assertEquals("https://edited.example.com", registryNode.getValue());
    }

    @Test
    void programmaticExpansionDoesNotStartDuplicateLoads() throws Exception {
        runOnEdt(() -> tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                controller.nodeExpanded((RegistryTreeNode) event.getPath().getLastPathComponent());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        }));

        runOnEdt(() -> controller.addRegistry(registry("https://added.example.com")));

        assertEquals(1, worker.pendingCount());
    }

    @Test
    void discardsACompletionAfterRegistryRemoval() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        runOnEdt(() -> controller.nodeExpanded(registryNode));
        runOnEdt(() -> controller.removeRegistry(registryNode));
        runOnEdt(worker::runNext);

        assertEquals(RegistryTreeNode.Kind.STATUS, child(controller.getRoot()).getKind());
        assertEquals(1, listener.savedRepositories.size());
        assertTrue(listener.savedRepositories.get(0).isEmpty());
        assertEquals(0, listener.errors.size());
    }

    @Test
    void discardsACompletionAfterDisposal() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        runOnEdt(() -> controller.nodeExpanded(registryNode));
        runOnEdt(controller::dispose);
        runOnEdt(worker::runNext);

        assertEquals(RegistryTreeNode.Kind.LOADING, child(registryNode).getKind());
        assertEquals(0, listener.errors.size());
    }

    @Test
    void deletesTagsInTheWorkerAndReloadsAnExpandedImage() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        expandAndLoad(imageNode);
        RegistryTreeNode tagNode = child(imageNode);

        operations.tags = List.of("replacement");
        runOnEdt(() -> {
            tree.setSelectionPath(path(tagNode));
            controller.deleteTag(tagNode);
        });

        assertEquals(0, operations.deleteCalls);
        assertEquals(List.of("image:latest"), listener.confirmedImageReferences);
        assertFalse(callOnEdt(controller::canRemoveSelection));

        runOnEdt(worker::runNext);
        assertEquals(1, operations.deleteCalls);
        assertEquals(1, listener.successes.size());
        assertEquals(1, worker.pendingCount());

        runOnEdt(worker::runNext);
        RegistryTreeNode replacementTag = child(imageNode);
        assertEquals("replacement", replacementTag.getValue());
        runOnEdt(() -> tree.setSelectionPath(path(replacementTag)));
        assertTrue(callOnEdt(controller::canRemoveSelection));
    }

    @Test
    void failedDeletionKeepsTheTagAvailableAndReportsTheError() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        expandAndLoad(imageNode);
        RegistryTreeNode tagNode = child(imageNode);
        operations.deleteFailure = new IllegalStateException("Delete rejected");

        runOnEdt(() -> {
            tree.setSelectionPath(path(tagNode));
            controller.deleteTag(tagNode);
        });
        runOnEdt(worker::runNext);

        assertEquals("latest", child(imageNode).getValue());
        assertTrue(callOnEdt(controller::canRemoveSelection));
        assertEquals(List.of("Delete rejected"), listener.errors);
    }

    @Test
    void cancelledDeletionDoesNotScheduleARegistryRequest() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        expandAndLoad(imageNode);
        RegistryTreeNode tagNode = child(imageNode);
        listener.allowDeletion = false;

        runOnEdt(() -> {
            tree.setSelectionPath(path(tagNode));
            controller.deleteTag(tagNode);
        });

        assertEquals(List.of("image:latest"), listener.confirmedImageReferences);
        assertEquals(0, worker.pendingCount());
        assertEquals(0, operations.deleteCalls);
        assertTrue(callOnEdt(controller::canRemoveSelection));
    }

    @Test
    void exposesRetryableInlineFailureAndNotifiesTheUser() throws Exception {
        operations.catalogFailure = new IllegalStateException("Registry offline");
        RegistryTreeNode registryNode = registryNode();

        expandAndLoad(registryNode);

        assertEquals(RegistryTreeNode.Kind.ERROR, child(registryNode).getKind());
        assertTrue(child(registryNode).getValue().contains("Collapse and expand to retry"));
        assertEquals(List.of("Registry offline"), listener.errors);
    }

    @Test
    void enablesActionsOnlyForSupportedSelectionKinds() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        runOnEdt(() -> tree.setSelectionPath(path(registryNode)));
        assertTrue(callOnEdt(controller::canEditSelection));
        assertTrue(callOnEdt(controller::canRemoveSelection));

        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        runOnEdt(() -> tree.setSelectionPath(path(imageNode)));
        assertFalse(callOnEdt(controller::canEditSelection));
        assertFalse(callOnEdt(controller::canRemoveSelection));

        expandAndLoad(imageNode);
        runOnEdt(() -> tree.setSelectionPath(path(child(imageNode))));
        assertFalse(callOnEdt(controller::canEditSelection));
        assertTrue(callOnEdt(controller::canRemoveSelection));
    }

    @Test
    void loadsManifestForTheSelectedTagWithoutBlockingAndShowsProperties() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        expandAndLoad(imageNode);
        RegistryTreeNode tagNode = child(imageNode);

        runOnEdt(() -> {
            tree.setSelectionPath(path(tagNode));
            controller.showManifest(tagNode);
        });

        assertEquals(0, operations.manifestCalls);
        runOnEdt(worker::runNext);

        assertEquals(1, operations.manifestCalls);
        assertEquals(List.of("image:latest"), listener.manifestReferences);
        assertTrue(listener.manifestDetails.get(0).contains("Schema version: 2"));
        assertTrue(listener.manifestDetails.get(0).contains("Architecture: amd64"));
        assertTrue(listener.manifestDetails.get(0).contains("sha256:layer"));
    }

    @Test
    void doesNotShowManifestForATagThatIsNoLongerSelected() throws Exception {
        RegistryTreeNode registryNode = registryNode();
        expandAndLoad(registryNode);
        RegistryTreeNode imageNode = child(registryNode);
        expandAndLoad(imageNode);
        RegistryTreeNode tagNode = child(imageNode);

        runOnEdt(() -> {
            tree.setSelectionPath(path(tagNode));
            controller.showManifest(tagNode);
            tree.setSelectionPath(path(imageNode));
        });
        runOnEdt(worker::runNext);

        assertEquals(1, operations.manifestCalls);
        assertEquals(List.of(), listener.manifestReferences);
    }

    private void expandAndLoad(RegistryTreeNode node) throws Exception {
        runOnEdt(() -> {
            tree.expandPath(path(node));
            controller.nodeExpanded(node);
        });
        assertEquals(1, worker.pendingCount());
        runOnEdt(worker::runNext);
    }

    private RegistryTreeNode registryNode() throws Exception {
        return callOnEdt(() -> child(controller.getRoot()));
    }

    private static RegistryTreeNode child(RegistryTreeNode node) {
        return (RegistryTreeNode) node.getChildAt(0);
    }

    private static TreePath path(RegistryTreeNode node) {
        return new TreePath(node.getPath());
    }

    private static DockerRegistry registry(String url) {
        return new DockerRegistry(LoginCredentials.builder()
                .username("user")
                .password("password")
                .registryURL(url)
                .build());
    }

    private static void runOnEdt(ThrowingRunnable task) throws Exception {
        callOnEdt(() -> {
            task.run();
            return null;
        });
    }

    private static <T> T callOnEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.call();
        }
        List<T> result = new ArrayList<>(1);
        List<Exception> exception = new ArrayList<>(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.add(task.call());
                } catch (Exception caught) {
                    exception.add(caught);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (InvocationTargetException invocation) {
            throw new RuntimeException(invocation.getCause());
        }
        if (!exception.isEmpty()) {
            throw exception.get(0);
        }
        return result.get(0);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ControlledExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.addLast(task);
        }

        int pendingCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static final class FakeOperations implements RegistryOperations {
        private final Deque<List<String>> catalogResults = new ArrayDeque<>();
        private List<String> tags = List.of("latest");
        private Exception catalogFailure;
        private Exception deleteFailure;
        private int manifestCalls;
        private int catalogCalls;
        private int tagCalls;
        private int deleteCalls;

        @Override
        public List<String> loadRepositories(DockerRegistry requestedRegistry) throws Exception {
            catalogCalls++;
            if (catalogFailure != null) {
                throw catalogFailure;
            }
            return catalogResults.isEmpty() ? List.of("image") : catalogResults.removeFirst();
        }

        @Override
        public List<String> loadTags(DockerRegistry requestedRegistry, String repositoryName) {
            tagCalls++;
            return tags;
        }

        @Override
        public JsonNode loadManifest(DockerRegistry requestedRegistry, String repositoryName, String tag) throws Exception {
            manifestCalls++;
            return new ObjectMapper().readTree("""
                    {
                      "schemaVersion": 2,
                      "architecture": "amd64",
                      "layers": [{"digest": "sha256:layer"}]
                    }
                    """);
        }

        @Override
        public void deleteTag(DockerRegistry requestedRegistry, String repositoryName, String tag) throws Exception {
            deleteCalls++;
            if (deleteFailure != null) {
                throw deleteFailure;
            }
        }
    }

    private static final class RecordingListener implements RegistryTreeController.Listener {
        private final List<List<DockerRegistry>> savedRepositories = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> confirmedImageReferences = new ArrayList<>();
        private final List<String> manifestReferences = new ArrayList<>();
        private final List<String> manifestDetails = new ArrayList<>();
        private boolean allowDeletion = true;

        @Override
        public void repositoriesChanged(List<DockerRegistry> repositories) {
            savedRepositories.add(repositories);
        }

        @Override
        public void networkError(String message) {
            errors.add(message);
        }

        @Override
        public void operationSucceeded(String message) {
            successes.add(message);
        }

        @Override
        public boolean confirmDeletion(String imageReference) {
            confirmedImageReferences.add(imageReference);
            return allowDeletion;
        }

        @Override
        public void showManifest(String imageReference, String details) {
            manifestReferences.add(imageReference);
            manifestDetails.add(details);
        }

        @Override
        public void stateChanged() {
        }
    }
}
