package com.github.exidcuter.dockerregistryexplorer.ui;

import org.tdfl.docker.DockerRegistry;

import javax.swing.tree.DefaultMutableTreeNode;

final class RegistryTreeNode extends DefaultMutableTreeNode {
    enum Kind {
        ROOT,
        REGISTRY,
        IMAGE,
        TAG,
        LOADING,
        PLACEHOLDER,
        STATUS,
        ERROR
    }

    private final Kind kind;
    private String value;
    private DockerRegistry registry;
    private long requestId;
    private boolean loaded;
    private boolean loading;
    private boolean deleting;

    private RegistryTreeNode(Kind kind, String value, DockerRegistry registry, boolean allowsChildren) {
        super(value, allowsChildren);
        this.kind = kind;
        this.value = value;
        this.registry = registry;
    }

    static RegistryTreeNode root() {
        return new RegistryTreeNode(Kind.ROOT, "registries", null, true);
    }

    static RegistryTreeNode registry(DockerRegistry registry) {
        RegistryTreeNode node = new RegistryTreeNode(
                Kind.REGISTRY,
                registry.getLoginCredentials().getRegistryURL(),
                registry,
                true
        );
        node.add(placeholder("Expand to load repositories"));
        return node;
    }

    static RegistryTreeNode image(String name) {
        RegistryTreeNode node = new RegistryTreeNode(Kind.IMAGE, name, null, true);
        node.add(placeholder("Expand to load tags"));
        return node;
    }

    static RegistryTreeNode tag(String tag) {
        return new RegistryTreeNode(Kind.TAG, tag, null, false);
    }

    static RegistryTreeNode loading() {
        return new RegistryTreeNode(Kind.LOADING, "Loading...", null, false);
    }

    static RegistryTreeNode placeholder(String text) {
        return new RegistryTreeNode(Kind.PLACEHOLDER, text, null, false);
    }

    static RegistryTreeNode status(String text) {
        return new RegistryTreeNode(Kind.STATUS, text, null, false);
    }

    static RegistryTreeNode error(String text) {
        return new RegistryTreeNode(Kind.ERROR, text, null, false);
    }

    Kind getKind() {
        return kind;
    }

    String getValue() {
        return value;
    }

    DockerRegistry getRegistry() {
        return registry;
    }

    void setRegistry(DockerRegistry registry) {
        this.registry = registry;
        this.value = registry.getLoginCredentials().getRegistryURL();
        setUserObject(value);
    }

    long getRequestId() {
        return requestId;
    }

    void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    boolean isLoaded() {
        return loaded;
    }

    void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    boolean isLoading() {
        return loading;
    }

    void setLoading(boolean loading) {
        this.loading = loading;
    }

    boolean isDeleting() {
        return deleting;
    }

    void setDeleting(boolean deleting) {
        this.deleting = deleting;
    }

    @Override
    public String toString() {
        if (kind == Kind.TAG && deleting) {
            return value + " (Deleting...)";
        }
        return value;
    }
}
