package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.JsonNode;
import org.tdfl.docker.DockerRegistry;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

final class RegistryTreeController {
    interface Listener {
        void repositoriesChanged(List<DockerRegistry> repositories);

        void networkError(String message);

        void operationSucceeded(String message);

        boolean confirmDeletion(String imageReference);

        void showManifest(String imageReference, String details);

        void stateChanged();
    }

    private final List<DockerRegistry> repositories;
    private final RegistryOperations operations;
    private final Executor workerExecutor;
    private final Executor uiExecutor;
    private final Listener listener;
    private final RegistryTreeNode root = RegistryTreeNode.root();
    private final DefaultTreeModel model = new DefaultTreeModel(root);

    private JTree tree;
    private long requestSequence;
    private long manifestRequestSequence;
    private boolean disposed;

    RegistryTreeController(
            List<DockerRegistry> configuredRepositories,
            RegistryOperations operations,
            Executor workerExecutor,
            Executor uiExecutor,
            Listener listener
    ) {
        this.repositories = configuredRepositories == null
                ? new ArrayList<>()
                : new ArrayList<>(configuredRepositories);
        this.operations = operations;
        this.workerExecutor = workerExecutor;
        this.uiExecutor = uiExecutor;
        this.listener = listener;
    }

    DefaultTreeModel getModel() {
        return model;
    }

    RegistryTreeNode getRoot() {
        return root;
    }

    void attachTree(JTree tree) {
        requireEdt();
        this.tree = tree;
    }

    void initialize() {
        requireEdt();
        root.removeAllChildren();
        if (repositories.isEmpty()) {
            root.add(RegistryTreeNode.status("No registries configured. Use Add to configure one."));
        } else {
            repositories.forEach(repository -> root.add(RegistryTreeNode.registry(repository)));
        }
        model.reload(root);
    }

    void nodeExpanded(RegistryTreeNode node) {
        requireEdt();
        if (node.getKind() == RegistryTreeNode.Kind.REGISTRY && !node.isLoaded() && !node.isLoading()) {
            loadRepositories(node);
        } else if (node.getKind() == RegistryTreeNode.Kind.IMAGE && !node.isLoaded() && !node.isLoading()) {
            loadTags(node);
        }
    }

    void refresh() {
        requireEdt();
        for (int i = 0; i < root.getChildCount(); i++) {
            RegistryTreeNode node = (RegistryTreeNode) root.getChildAt(i);
            if (node.getKind() != RegistryTreeNode.Kind.REGISTRY) {
                continue;
            }
            boolean expanded = isExpanded(node);
            resetRegistry(node);
            if (expanded) {
                tree.expandPath(path(node));
                nodeExpanded(node);
            }
        }
        listener.stateChanged();
    }

    void addRegistry(DockerRegistry repository) {
        requireEdt();
        removeRootStatus();
        repositories.add(repository);
        RegistryTreeNode node = RegistryTreeNode.registry(repository);
        model.insertNodeInto(node, root, root.getChildCount());
        persistRepositories();
        tree.setSelectionPath(path(node));
        tree.expandPath(path(node));
        nodeExpanded(node);
    }

    void editRegistry(RegistryTreeNode node, DockerRegistry replacement) {
        requireEdt();
        if (node.getKind() != RegistryTreeNode.Kind.REGISTRY || !isAttached(node)) {
            return;
        }
        int index = repositories.indexOf(node.getRegistry());
        if (index < 0) {
            return;
        }
        repositories.set(index, replacement);
        node.setRegistry(replacement);
        model.nodeChanged(node);
        resetRegistry(node);
        persistRepositories();
        tree.expandPath(path(node));
        nodeExpanded(node);
    }

    void removeRegistry(RegistryTreeNode node) {
        requireEdt();
        if (node.getKind() != RegistryTreeNode.Kind.REGISTRY || !isAttached(node)) {
            return;
        }
        repositories.remove(node.getRegistry());
        model.removeNodeFromParent(node);
        if (repositories.isEmpty()) {
            model.insertNodeInto(
                    RegistryTreeNode.status("No registries configured. Use Add to configure one."),
                    root,
                    0
            );
        }
        persistRepositories();
        listener.stateChanged();
    }

    void deleteTag(RegistryTreeNode tagNode) {
        requireEdt();
        if (tagNode.getKind() != RegistryTreeNode.Kind.TAG || tagNode.isDeleting() || !isAttached(tagNode)) {
            return;
        }
        RegistryTreeNode imageNode = (RegistryTreeNode) tagNode.getParent();
        RegistryTreeNode registryNode = (RegistryTreeNode) imageNode.getParent();
        DockerRegistry registry = registryNode.getRegistry();
        String imageName = imageNode.getValue();
        String tag = tagNode.getValue();
        if (!listener.confirmDeletion(imageName + ":" + tag)) {
            return;
        }

        tagNode.setDeleting(true);
        model.nodeChanged(tagNode);
        listener.stateChanged();

        submit(() -> {
            try {
                operations.deleteTag(registry, imageName, tag);
                runOnUi(() -> applyDeleteSuccess(registryNode, registry, imageName, tag));
            } catch (Exception exception) {
                runOnUi(() -> applyDeleteFailure(tagNode, exception));
            }
        });
    }

    void showManifest(RegistryTreeNode tagNode) {
        requireEdt();
        if (tagNode.getKind() != RegistryTreeNode.Kind.TAG || !isAttached(tagNode)) {
            return;
        }
        RegistryTreeNode imageNode = (RegistryTreeNode) tagNode.getParent();
        RegistryTreeNode registryNode = (RegistryTreeNode) imageNode.getParent();
        DockerRegistry registry = registryNode.getRegistry();
        String imageName = imageNode.getValue();
        String tag = tagNode.getValue();
        long requestId = ++manifestRequestSequence;

        submit(() -> {
            try {
                JsonNode manifest = operations.loadManifest(registry, imageName, tag);
                String details = ManifestDetailsFormatter.format(
                        manifest,
                        registry.getLoginCredentials().getRegistryURL(),
                        imageName,
                        tag
                );
                runOnUi(() -> applyManifest(tagNode, requestId, imageName, tag, details));
            } catch (Exception exception) {
                runOnUi(() -> applyManifestFailure(tagNode, requestId, exception));
            }
        });
    }

    boolean canEditSelection() {
        RegistryTreeNode selected = selectedNode();
        return selected != null && selected.getKind() == RegistryTreeNode.Kind.REGISTRY;
    }

    boolean canRemoveSelection() {
        RegistryTreeNode selected = selectedNode();
        return selected != null
                && (selected.getKind() == RegistryTreeNode.Kind.REGISTRY
                || (selected.getKind() == RegistryTreeNode.Kind.TAG && !selected.isDeleting()));
    }

    RegistryTreeNode selectedNode() {
        Object selected = tree.getLastSelectedPathComponent();
        return selected instanceof RegistryTreeNode ? (RegistryTreeNode) selected : null;
    }

    void dispose() {
        requireEdt();
        disposed = true;
        requestSequence++;
        manifestRequestSequence++;
    }

    private void loadRepositories(RegistryTreeNode node) {
        DockerRegistry registry = node.getRegistry();
        long requestId = beginLoad(node);
        submit(() -> {
            try {
                List<String> names = immutableCopy(operations.loadRepositories(registry));
                runOnUi(() -> applyRepositories(node, registry, requestId, names));
            } catch (Exception exception) {
                runOnUi(() -> applyLoadFailure(node, registry, requestId, "repositories", exception));
            }
        });
    }

    private void loadTags(RegistryTreeNode node) {
        RegistryTreeNode registryNode = (RegistryTreeNode) node.getParent();
        DockerRegistry registry = registryNode.getRegistry();
        String repositoryName = node.getValue();
        long requestId = beginLoad(node);
        submit(() -> {
            try {
                List<String> tags = immutableCopy(operations.loadTags(registry, repositoryName));
                runOnUi(() -> applyTags(node, registryNode, registry, requestId, tags));
            } catch (Exception exception) {
                runOnUi(() -> applyLoadFailure(node, registry, requestId, "tags", exception));
            }
        });
    }

    private long beginLoad(RegistryTreeNode node) {
        long requestId = ++requestSequence;
        node.setRequestId(requestId);
        node.setLoading(true);
        node.setLoaded(false);
        replaceChildren(node, RegistryTreeNode.loading());
        listener.stateChanged();
        return requestId;
    }

    private void applyRepositories(
            RegistryTreeNode node,
            DockerRegistry registry,
            long requestId,
            List<String> names
    ) {
        requireEdt();
        if (!isCurrent(node, registry, requestId)) {
            return;
        }
        node.setLoading(false);
        node.setLoaded(true);
        node.removeAllChildren();
        if (names.isEmpty()) {
            node.add(RegistryTreeNode.status("No repositories found."));
        } else {
            names.forEach(name -> node.add(RegistryTreeNode.image(name)));
        }
        model.reload(node);
        listener.stateChanged();
    }

    private void applyTags(
            RegistryTreeNode node,
            RegistryTreeNode registryNode,
            DockerRegistry registry,
            long requestId,
            List<String> tags
    ) {
        requireEdt();
        if (!isCurrent(node, requestId)
                || !isAttached(registryNode)
                || registryNode.getRegistry() != registry) {
            return;
        }
        node.setLoading(false);
        node.setLoaded(true);
        node.removeAllChildren();
        if (tags.isEmpty()) {
            node.add(RegistryTreeNode.status("No tags found."));
        } else {
            tags.forEach(tag -> node.add(RegistryTreeNode.tag(tag)));
        }
        model.reload(node);
        listener.stateChanged();
    }

    private void applyLoadFailure(
            RegistryTreeNode node,
            DockerRegistry registry,
            long requestId,
            String itemName,
            Exception exception
    ) {
        requireEdt();
        if (!isCurrent(node, requestId)
                || (node.getKind() == RegistryTreeNode.Kind.REGISTRY && node.getRegistry() != registry)) {
            return;
        }
        node.setLoading(false);
        node.setLoaded(false);
        replaceChildren(node, RegistryTreeNode.error(
                "Failed to load " + itemName + ". Collapse and expand to retry."
        ));
        listener.networkError(errorMessage(exception));
        listener.stateChanged();
    }

    private void applyDeleteSuccess(
            RegistryTreeNode registryNode,
            DockerRegistry registry,
            String imageName,
            String tag
    ) {
        requireEdt();
        if (disposed) {
            return;
        }
        listener.operationSucceeded(
                registry.getLoginCredentials().getRegistryURL() + "/" + imageName + ":" + tag
                        + " was successfully deleted!"
        );
        if (!isAttached(registryNode) || registryNode.getRegistry() != registry) {
            listener.stateChanged();
            return;
        }
        RegistryTreeNode currentImage = findImage(registryNode, imageName);
        if (currentImage != null) {
            boolean expanded = isExpanded(currentImage);
            resetImage(currentImage);
            if (expanded) {
                tree.expandPath(path(currentImage));
                nodeExpanded(currentImage);
            }
        }
        listener.stateChanged();
    }

    private void applyDeleteFailure(RegistryTreeNode tagNode, Exception exception) {
        requireEdt();
        if (disposed) {
            return;
        }
        if (isAttached(tagNode)) {
            tagNode.setDeleting(false);
            model.nodeChanged(tagNode);
        }
        listener.networkError(errorMessage(exception));
        listener.stateChanged();
    }

    private void applyManifest(
            RegistryTreeNode tagNode,
            long requestId,
            String imageName,
            String tag,
            String details
    ) {
        requireEdt();
        if (!isCurrentManifest(tagNode, requestId)) {
            return;
        }
        listener.showManifest(imageName + ":" + tag, details);
    }

    private void applyManifestFailure(RegistryTreeNode tagNode, long requestId, Exception exception) {
        requireEdt();
        if (!isCurrentManifest(tagNode, requestId)) {
            return;
        }
        listener.networkError(errorMessage(exception));
    }

    private void resetRegistry(RegistryTreeNode node) {
        node.setRequestId(++requestSequence);
        node.setLoading(false);
        node.setLoaded(false);
        replaceChildren(node, RegistryTreeNode.placeholder("Expand to load repositories"));
    }

    private void resetImage(RegistryTreeNode node) {
        node.setRequestId(++requestSequence);
        node.setLoading(false);
        node.setLoaded(false);
        replaceChildren(node, RegistryTreeNode.placeholder("Expand to load tags"));
    }

    private RegistryTreeNode findImage(RegistryTreeNode registryNode, String imageName) {
        for (int i = 0; i < registryNode.getChildCount(); i++) {
            RegistryTreeNode node = (RegistryTreeNode) registryNode.getChildAt(i);
            if (node.getKind() == RegistryTreeNode.Kind.IMAGE && imageName.equals(node.getValue())) {
                return node;
            }
        }
        return null;
    }

    private void removeRootStatus() {
        if (root.getChildCount() == 1
                && ((RegistryTreeNode) root.getChildAt(0)).getKind() == RegistryTreeNode.Kind.STATUS) {
            model.removeNodeFromParent((RegistryTreeNode) root.getChildAt(0));
        }
    }

    private void persistRepositories() {
        listener.repositoriesChanged(new ArrayList<>(repositories));
        listener.stateChanged();
    }

    private boolean isCurrent(RegistryTreeNode node, DockerRegistry registry, long requestId) {
        return isCurrent(node, requestId) && node.getRegistry() == registry;
    }

    private boolean isCurrent(RegistryTreeNode node, long requestId) {
        return !disposed && isAttached(node) && node.getRequestId() == requestId;
    }

    private boolean isCurrentManifest(RegistryTreeNode node, long requestId) {
        return !disposed
                && isAttached(node)
                && selectedNode() == node
                && manifestRequestSequence == requestId;
    }

    private boolean isAttached(RegistryTreeNode node) {
        RegistryTreeNode cursor = node;
        while (cursor.getParent() instanceof RegistryTreeNode) {
            cursor = (RegistryTreeNode) cursor.getParent();
        }
        return cursor == root;
    }

    private boolean isExpanded(RegistryTreeNode node) {
        return tree.isExpanded(path(node));
    }

    private TreePath path(RegistryTreeNode node) {
        return new TreePath(node.getPath());
    }

    private void replaceChildren(RegistryTreeNode node, RegistryTreeNode child) {
        node.removeAllChildren();
        node.add(child);
        model.reload(node);
    }

    private void submit(Runnable task) {
        if (disposed) {
            return;
        }
        workerExecutor.execute(task);
    }

    private void runOnUi(Runnable task) {
        uiExecutor.execute(() -> {
            if (!disposed) {
                task.run();
            }
        });
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? Collections.emptyList() : new ArrayList<>(values);
    }

    private static String errorMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isEmpty()
                ? exception.toString()
                : exception.getMessage();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Registry tree updates must run on the Swing EDT");
        }
    }
}
