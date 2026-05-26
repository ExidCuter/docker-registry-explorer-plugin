package com.github.exidcuter.dockerregistryexplorer.ui;

import com.github.exidcuter.dockerregistryexplorer.data.RegistryExplorerState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.tdfl.docker.DockerRegistry;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.Executor;

final class RegistryExplorerPanel extends JPanel implements Disposable {
    private final Tree tree;
    private final RegistryTreeController controller;

    RegistryExplorerPanel(
            Project project,
            RegistryExplorerState state,
            RegistryOperations operations,
            Executor workerExecutor
    ) {
        super(new BorderLayout());
        setBackground(JBUI.CurrentTheme.ToolWindow.background());
        this.controller = new RegistryTreeController(
                state.getDockerRepositories(),
                operations,
                workerExecutor,
                SwingUtilities::invokeLater,
                new RegistryTreeController.Listener() {
                    @Override
                    public void repositoriesChanged(List<DockerRegistry> repositories) {
                        state.setDockerRepositories(repositories);
                    }

                    @Override
                    public void networkError(String message) {
                        RegistryErrorNotifier.notifyError(project, message);
                    }

                    @Override
                    public void operationSucceeded(String message) {
                        RegistryErrorNotifier.notifySuccess(project, message);
                    }

                    @Override
                    public boolean confirmDeletion(String imageReference) {
                        return Messages.showYesNoDialog(
                                project,
                                "Are you sure you want to delete image \"" + imageReference + "\"?",
                                "Delete Docker Image",
                                "Delete",
                                "Cancel",
                                Messages.getWarningIcon()
                        ) == Messages.YES;
                    }

                    @Override
                    public void showManifest(String imageReference, String details) {
                        new ManifestInfoDialogWrapper(project, imageReference, details).show();
                    }

                    @Override
                    public void stateChanged() {
                        tree.repaint();
                    }
                }
        );
        this.tree = new Tree(controller.getModel());
        configureTreeAppearance(this.tree);
        this.tree.setRootVisible(false);
        this.tree.setShowsRootHandles(true);
        this.tree.setCellRenderer(createRenderer());
        this.tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                Object value = event.getPath().getLastPathComponent();
                if (value instanceof RegistryTreeNode) {
                    controller.nodeExpanded((RegistryTreeNode) value);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                // Nothing to do: a failed branch retries when expanded again.
            }
        });
        this.tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showContextMenuIfRequested(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showContextMenuIfRequested(event);
            }
        });
        controller.attachTree(tree);

        JPanel decoratedTree = ToolbarDecorator.createDecorator(tree)
                .disableUpDownActions()
                .setAddAction(this::addButtonPressed)
                .setEditAction(this::editButtonPressed)
                .setEditActionUpdater(event -> controller.canEditSelection())
                .setRemoveAction(this::removeButtonPressed)
                .setRemoveActionUpdater(event -> controller.canRemoveSelection())
                .addExtraAction(new DumbAwareAction("Refresh Registry Listing", null, AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        controller.refresh();
                    }
                })
                .setPanelBorder(BorderFactory.createEmptyBorder())
                .setScrollPaneBorder(BorderFactory.createEmptyBorder())
                .createPanel();
        configureDecoratedTreeAppearance(decoratedTree);
        add(decoratedTree, BorderLayout.CENTER);

        controller.initialize();
        tree.expandPath(new TreePath(controller.getRoot().getPath()));
    }

    private void showContextMenuIfRequested(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null || !(path.getLastPathComponent() instanceof RegistryTreeNode)) {
            return;
        }
        RegistryTreeNode node = (RegistryTreeNode) path.getLastPathComponent();
        if (node.getKind() != RegistryTreeNode.Kind.TAG) {
            return;
        }

        tree.setSelectionPath(path);
        JPopupMenu menu = createTagContextMenu(
                node,
                () -> controller.showManifest(node),
                () -> controller.deleteTag(node)
        );
        menu.show(tree, event.getX(), event.getY());
    }

    static JPopupMenu createTagContextMenu(RegistryTreeNode node, Runnable showInfoAction, Runnable deleteAction) {
        if (node.getKind() != RegistryTreeNode.Kind.TAG) {
            return null;
        }

        JMenuItem showInfo = new JMenuItem("Show Info");
        showInfo.addActionListener(ignored -> showInfoAction.run());
        JMenuItem delete = new JMenuItem("Delete");
        delete.setEnabled(!node.isDeleting());
        delete.addActionListener(ignored -> deleteAction.run());
        JPopupMenu menu = new JPopupMenu();
        menu.add(showInfo);
        menu.addSeparator();
        menu.add(delete);
        return menu;
    }

    static void configureTreeAppearance(Tree tree) {
        tree.setOpaque(true);
        tree.setBackground(JBUI.CurrentTheme.ToolWindow.background());
    }

    static void configureDecoratedTreeAppearance(JPanel decoratedTree) {
        decoratedTree.setOpaque(true);
        decoratedTree.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        JScrollPane scrollPane = UIUtil.findComponentOfType(decoratedTree, JScrollPane.class);
        if (scrollPane != null) {
            scrollPane.setOpaque(true);
            scrollPane.setBackground(JBUI.CurrentTheme.ToolWindow.background());
            scrollPane.getViewport().setOpaque(true);
            scrollPane.getViewport().setBackground(JBUI.CurrentTheme.ToolWindow.background());
        }
    }

    @Override
    public void dispose() {
        if (SwingUtilities.isEventDispatchThread()) {
            controller.dispose();
        } else {
            SwingUtilities.invokeLater(controller::dispose);
        }
    }

    private void addButtonPressed(AnActionButton button) {
        AddEditDialogWrapper dialog = new AddEditDialogWrapper();
        if (dialog.showAndGet()) {
            controller.addRegistry(new DockerRegistry(dialog.getLoginCredentials(), true));
        }
    }

    private void editButtonPressed(AnActionButton button) {
        RegistryTreeNode selected = controller.selectedNode();
        if (selected == null || selected.getKind() != RegistryTreeNode.Kind.REGISTRY) {
            return;
        }
        AddEditDialogWrapper dialog = new AddEditDialogWrapper(selected.getRegistry().getLoginCredentials());
        if (dialog.showAndGet()) {
            controller.editRegistry(selected, new DockerRegistry(dialog.getLoginCredentials(), true));
        }
    }

    private void removeButtonPressed(AnActionButton button) {
        RegistryTreeNode selected = controller.selectedNode();
        if (selected == null) {
            return;
        }
        if (selected.getKind() == RegistryTreeNode.Kind.REGISTRY) {
            controller.removeRegistry(selected);
        } else if (selected.getKind() == RegistryTreeNode.Kind.TAG) {
            controller.deleteTag(selected);
        }
    }

    private DefaultTreeCellRenderer createRenderer() {
        Icon registryIcon = IconLoader.getIcon("/icons/registry.svg", RegistryExplorerPanel.class);
        Icon imageIcon = IconLoader.getIcon("/icons/image.svg", RegistryExplorerPanel.class);
        Icon tagIcon = IconLoader.getIcon("/icons/tag.svg", RegistryExplorerPanel.class);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree sourceTree,
                    Object value,
                    boolean selected,
                    boolean expanded,
                    boolean leaf,
                    int row,
                    boolean hasFocus
            ) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(
                        sourceTree, value, selected, expanded, leaf, row, hasFocus
                );
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                label.setEnabled(true);
                if (!(value instanceof RegistryTreeNode)) {
                    return label;
                }
                RegistryTreeNode node = (RegistryTreeNode) value;
                if (node.getKind() == RegistryTreeNode.Kind.REGISTRY) {
                    label.setIcon(registryIcon);
                } else if (node.getKind() == RegistryTreeNode.Kind.IMAGE) {
                    label.setIcon(imageIcon);
                } else if (node.getKind() == RegistryTreeNode.Kind.TAG) {
                    label.setIcon(tagIcon);
                    label.setEnabled(!node.isDeleting());
                } else {
                    label.setIcon(null);
                }
                return label;
            }
        };
        configureRendererAppearance(renderer);
        return renderer;
    }

    static void configureRendererAppearance(DefaultTreeCellRenderer renderer) {
        renderer.setBackgroundNonSelectionColor(JBUI.CurrentTheme.ToolWindow.background());
    }
}
