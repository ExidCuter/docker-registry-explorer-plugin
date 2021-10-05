package com.github.exidcuter.dockerregistryexplorer.ui;

import com.github.exidcuter.dockerregistryexplorer.data.RegistryExplorerState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.tdfl.docker.DockerRegistry;
import org.tdfl.docker.model.Tags;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExplorerToolWindowFactory implements ToolWindowFactory {
    private Project project;
    private Tree tree;
    private List<DockerRegistry> dockerRepositories;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.dockerRepositories = RegistryExplorerState.getInstance().getDockerRepositories();

        if (this.dockerRepositories == null) {
            this.dockerRepositories = new ArrayList<>();
        }

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("repository");

        fetchData().forEach(root::add);

        DefaultTreeModel model = new DefaultTreeModel(root);
        this.tree = new Tree(model);
        this.tree.setCellRenderer(createRenderer());
        this.tree.setRootVisible(false);

        JPanel toolbarDecorator = ToolbarDecorator.createDecorator(tree)
                .setAddAction(this::addButtonPressed)
                .setEditAction(this::editButtonPressed)
                .setRemoveAction(this::deleteButtonPressed)
                .addExtraAction(new AnActionButton("Refresh Registry Listing", AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        refreshTree(tree);
                    }
                })
                .createPanel();

        toolbarDecorator.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JBScrollPane jScrollPane = new JBScrollPane(tree);
        jScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel repositoriesPanel = new JPanel(new BorderLayout(0, 0));

        repositoriesPanel.add(toolbarDecorator, BorderLayout.PAGE_START);
        repositoriesPanel.add(jScrollPane, BorderLayout.CENTER);

        toolWindow.getComponent().add(repositoriesPanel);
    }

    public void refreshTree(Tree tree) {
        tree.setPaintBusy(true);

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        root.removeAllChildren();

        fetchData().forEach(root::add);

        model.reload(root);

        tree.setPaintBusy(false);
    }

    private List<DefaultMutableTreeNode> fetchData() {
        List<DefaultMutableTreeNode> repositoryNodes = new ArrayList<>();

        dockerRepositories.forEach(repository -> {

            DefaultMutableTreeNode repositoryNode = new DefaultMutableTreeNode(repository.getLoginCredentials().getRegistryURL());
            try {
                repository.getCatalog().getRepositories().forEach(imageName -> {
                    DefaultMutableTreeNode imageNode = new DefaultMutableTreeNode(imageName);
                    Tags tags = repository.getTags(imageName);

                    if (tags.getTags() != null) {
                        tags.getTags().forEach(tag -> {
                            imageNode.add(new DefaultMutableTreeNode(tag, false));
                        });
                    }

                    repositoryNode.add(imageNode);
                });
            } catch (Exception e) {
                RegistryErrorNotifier.notifyError(project, e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage());
            }

            repositoryNodes.add(repositoryNode);
        });

        RegistryExplorerState.getInstance().setDockerRepositories(this.dockerRepositories);

        return repositoryNodes;
    }

    private void addButtonPressed(AnActionButton button) {
        AddEditDialogWrapper dialog = new AddEditDialogWrapper();
        if (dialog.showAndGet()) {
            dockerRepositories.add(new DockerRegistry(dialog.getLoginCredentials(), true));
            refreshTree(tree);
        }
    }

    private void editButtonPressed(AnActionButton button) {
        DefaultMutableTreeNode[] selectedNodes = tree.getSelectedNodes(DefaultMutableTreeNode.class, null);

        if (selectedNodes.length > 0 && selectedNodes[0].getParent().getParent() == null) {
            DockerRegistry repo = dockerRepositories.stream()
                    .filter(dockerRepository -> dockerRepository.getLoginCredentials().getRegistryURL().equals(selectedNodes[0].getUserObject()))
                    .findFirst()
                    .get();

            AddEditDialogWrapper dialog = new AddEditDialogWrapper(repo.getLoginCredentials());

            if (dialog.showAndGet()) {
                repo.setLoginCredentials(dialog.getLoginCredentials());
                refreshTree(tree);
            }
        }
    }

    private void deleteButtonPressed(AnActionButton button) {
        DefaultMutableTreeNode[] selectedNodes = tree.getSelectedNodes(DefaultMutableTreeNode.class, null);

        if (selectedNodes.length > 0 && selectedNodes[0].getParent().getParent() == null) {
            DockerRegistry repo = dockerRepositories.stream()
                    .filter(dockerRepository -> dockerRepository.getLoginCredentials().getRegistryURL().equals(selectedNodes[0].getUserObject()))
                    .findFirst()
                    .get();

            dockerRepositories.remove(repo);
            refreshTree(tree);
        } else if (selectedNodes.length > 0 && !selectedNodes[0].getAllowsChildren()) {
            String tag = (String) selectedNodes[0].getUserObject();
            String image = (String) ((DefaultMutableTreeNode) selectedNodes[0].getParent()).getUserObject();
            String registry = (String) ((DefaultMutableTreeNode) selectedNodes[0].getParent().getParent()).getUserObject();

            DockerRegistry dockerRegistry = dockerRepositories.stream().filter(dr -> dr.getLoginCredentials().getRegistryURL().equals(registry)).findFirst().orElse(null);

            if (dockerRegistry != null) {
                try {
                    dockerRegistry.deleteImage(image, tag);
                    refreshTree(tree);
                    RegistryErrorNotifier.notifySuccess(project, registry + "/" + image + ":" + tag + " was successfully deleted!");
                } catch (Exception e) {
                    RegistryErrorNotifier.notifyError(project, e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage());
                }
            }
        }
    }

    private DefaultTreeCellRenderer createRenderer() {
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            private Border border = BorderFactory.createEmptyBorder ( 2, 4, 2, 4 );

            public Component getTreeCellRendererComponent ( JTree tree, Object value, boolean sel,
                                                            boolean expanded, boolean leaf, int row,
                                                            boolean hasFocus ) {
                JLabel label = ( JLabel ) super.getTreeCellRendererComponent ( tree, value, sel, expanded, leaf, row, hasFocus );
                label.setBorder ( border );

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

                if (node.getParent() != null && node.getParent().getParent() != null && node.getParent().getParent().getParent() == null) {
                    label.setIcon(IconLoader.getIcon("/icons/image.svg"));
                }

                return label;
            }
        };

        Icon listIcon = IconLoader.getIcon("/icons/registry.svg");
        Icon leafIcon = IconLoader.getIcon("/icons/tag.svg");

        renderer.setClosedIcon(listIcon);
        renderer.setOpenIcon(listIcon);
        renderer.setLeafIcon(leafIcon);
        renderer.setIconTextGap(5);
        renderer.setBounds(0, 50,0,0);
        renderer.contains(0,50);

        return renderer;
    }
}
