package dev.everydaythings.graph.work;

import dev.everydaythings.graph.item.id.ItemID;

import java.util.*;

public final class WorkingSession {

    public enum Section { COMPONENTS, MOUNTS, ACTIONS }

    private final ItemID iid;

    /** Optional: what we're based on in this session (VID or channel). */
    private BaseSelector base;

    private final Map<String, ComponentDef> componentsByHid = new HashMap<>();
    private final Map<String, MountDef> mountsByMid = new HashMap<>();
    private final Map<String, MountDef> mountsByPath = new HashMap<>();
    private final Map<String, ActionDef> actionsByAid = new HashMap<>();
    private final Map<String, ActionDef> actionsByHandle = new HashMap<>();

    private final EnumSet<Section> dirtySections = EnumSet.noneOf(Section.class);
    private final Map<Section, Set<String>> dirtyIds = new EnumMap<>(Section.class);

    public WorkingSession(ItemID iid) {
        this.iid = Objects.requireNonNull(iid, "iid");
        for (Section s : Section.values()) dirtyIds.put(s, new LinkedHashSet<>());
    }

    public ItemID iid() { return iid; }

    public Optional<BaseSelector> base() { return Optional.ofNullable(base); }
    public void setBase(BaseSelector base) { this.base = base; }

    // --- queries (read-only views) ---
    public Collection<ComponentDef> components() { return Collections.unmodifiableCollection(componentsByHid.values()); }
    public Collection<MountDef> mounts() { return Collections.unmodifiableCollection(mountsByMid.values()); }
    public Collection<ActionDef> actions() { return Collections.unmodifiableCollection(actionsByAid.values()); }

    public Optional<ComponentDef> component(String hid) { return Optional.ofNullable(componentsByHid.get(hid)); }
    public Optional<MountDef> mountByPath(String path) { return Optional.ofNullable(mountsByPath.get(path)); }
    public Optional<ActionDef> actionByHandle(String handle) { return Optional.ofNullable(actionsByHandle.get(handle)); }

    public boolean isDirty() { return !dirtySections.isEmpty(); }
    public EnumSet<Section> dirtySections() { return dirtySections.clone(); }
    public Set<String> dirtyIds(Section section) { return Collections.unmodifiableSet(dirtyIds.get(section)); }

    // --- mutations (package-private, used by ingesters / GUI) ---

    void putComponent(ComponentDef def) {
        Objects.requireNonNull(def, "def");
        componentsByHid.put(def.hid(), def);
        markDirty(Section.COMPONENTS, def.hid());
    }

    void removeComponent(String hid) {
        ComponentDef removed = componentsByHid.remove(hid);
        if (removed != null) markDirty(Section.COMPONENTS, hid);

        // Note: you may also want to remove mounts/actions targeting this hid (or just warn).
    }

    void putMount(MountDef def) {
        Objects.requireNonNull(def, "def");

        // maintain path index
        MountDef old = mountsByMid.put(def.mid(), def);
        if (old != null) {
            mountsByPath.remove(old.path());
        }
        mountsByPath.put(def.path(), def);

        markDirty(Section.MOUNTS, def.mid());
    }

    void removeMount(String mid) {
        MountDef old = mountsByMid.remove(mid);
        if (old != null) {
            mountsByPath.remove(old.path());
            markDirty(Section.MOUNTS, mid);
        }
    }

    void putAction(ActionDef def) {
        Objects.requireNonNull(def, "def");

        // enforce handle uniqueness (within item actions)
        ActionDef existingForHandle = actionsByHandle.get(def.handle());
        if (existingForHandle != null && !existingForHandle.aid().equals(def.aid())) {
            throw new IllegalArgumentException("Action handle already used: " + def.handle());
        }

        ActionDef old = actionsByAid.put(def.aid(), def);
        if (old != null) actionsByHandle.remove(old.handle());
        actionsByHandle.put(def.handle(), def);

        markDirty(Section.ACTIONS, def.aid());
    }

    void removeAction(String aid) {
        ActionDef old = actionsByAid.remove(aid);
        if (old != null) {
            actionsByHandle.remove(old.handle());
            markDirty(Section.ACTIONS, aid);
        }
    }

    private void markDirty(Section section, String id) {
        dirtySections.add(section);
        dirtyIds.get(section).add(id);
    }

    /** Call after successful commit or after regenerating from base. */
    public void clearDirty() {
        dirtySections.clear();
        for (Set<String> s : dirtyIds.values()) s.clear();
    }

    // --- base selector model ---
    public sealed interface BaseSelector permits BaseSelector.Vid, BaseSelector.Channel {
        record Vid(String vid) implements BaseSelector {}
        record Channel(String name) implements BaseSelector {}
    }
}
