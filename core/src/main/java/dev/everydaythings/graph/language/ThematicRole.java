package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;

/**
 * Thematic roles (theta roles) — the semantic function of a participant in a frame.
 *
 * <p><b>Transitional:</b> This enum bridges to {@link Role} sememes. Each constant
 * maps to a {@link Role} seed instance via {@link #iid()}. New code should use
 * {@code Role.AGENT.iid()} directly; this enum exists for backwards compatibility
 * during the migration to frame-based relations.
 *
 * @see Role
 * @deprecated Use {@link Role} seed sememes directly (e.g., {@code Role.AGENT.iid()}).
 */
@Deprecated
public enum ThematicRole {
    AGENT(Role.AGENT),
    THEME(Role.THEME),
    PATIENT(Role.PATIENT),
    TARGET(Role.TARGET),
    SOURCE(Role.SOURCE),
    INSTRUMENT(Role.INSTRUMENT),
    LOCATION(Role.LOCATION),
    RECIPIENT(Role.RECIPIENT),
    CAUSE(Role.CAUSE),
    COMITATIVE(Role.COMITATIVE),
    NAME(Role.NAME);

    private final Role role;

    ThematicRole(Role role) {
        this.role = role;
    }

    /** The {@link Role} sememe this enum constant maps to. */
    public Role role() {
        return role;
    }

    /** The ItemID of the {@link Role} sememe. */
    public ItemID iid() {
        return role.iid();
    }

    /** Look up a ThematicRole from a Role sememe's IID. */
    public static ThematicRole fromIid(ItemID iid) {
        for (ThematicRole tr : values()) {
            if (tr.iid().equals(iid)) return tr;
        }
        return null;
    }
}
