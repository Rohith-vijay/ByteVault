package com.example.platform.user;

import java.util.Set;

public enum Role {
    USER(Set.of(
            Permission.READ_CONTENT
    )),
    ADMIN(Set.of(
            Permission.READ_CONTENT,
            Permission.VIEW_AUDIT_LOGS,
            Permission.MANAGE_MEDIA,
            Permission.MANAGE_USERS
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
