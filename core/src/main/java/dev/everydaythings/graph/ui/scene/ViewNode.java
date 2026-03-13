package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * A compiled structural node — the serializable unit of a scene template tree.
 *
 * <p>ViewNodes are compiled from {@code @Scene} annotations by {@link SceneCompiler}
 * and can be serialized to CBOR for storage as
 * {@link dev.everydaythings.graph.frame.SurfaceTemplateComponent}
 * on type items.
 *
 * <p>Uses MAP encoding so that only non-default fields are serialized, keeping
 * the CBOR compact for simple nodes (e.g., a text node only stores type + textContent).
 * 3D fields (canon order 200+) are omitted entirely for 2D-only templates.
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
public class ViewNode implements Canonical {

    // ==================================================================================
    // Node Types
    // ==================================================================================

    /** Type of structural node in a scene tree. */
    public enum NodeType {
        // 2D primitives
        CONTAINER, TEXT, IMAGE, SHAPE, EMBED,
        // 3D scene elements
        BODY, FACE, TRANSFORM, LIGHT, AUDIO_3D, ENVIRONMENT, CAMERA
    }

    // ==================================================================================
    // Conditional Style Mapping
    // ==================================================================================

    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class StateStyle implements Canonical {
        @Canonical.Canon(order = 0) public String condition;
        @Canonical.Canon(order = 1) public List<String> styles;

        public StateStyle() {}

        public StateStyle(String condition, List<String> styles) {
            this.condition = condition;
            this.styles = styles;
        }
    }

    // ==================================================================================
    // Event Handler
    // ==================================================================================

    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class EventHandler implements Canonical {
        @Canonical.Canon(order = 0) public String eventType;
        @Canonical.Canon(order = 1) public String action;
        @Canonical.Canon(order = 2) public String target;
        @Canonical.Canon(order = 3) public String condition;

        public EventHandler() {}

        public EventHandler(String eventType, String action, String target, String condition) {
            this.eventType = eventType;
            this.action = action;
            this.target = target;
            this.condition = condition;
        }
    }

    // ==================================================================================
    // Core Fields (canon order 0-9)
    // ==================================================================================

    @Canonical.Canon(order = 0) public NodeType type;
    @Canonical.Canon(order = 1) public String visibilityCondition;

    // ==================================================================================
    // Container Properties (canon order 10-19)
    // ==================================================================================

    @Canonical.Canon(order = 10) public Scene.Direction direction = Scene.Direction.HORIZONTAL;
    @Canonical.Canon(order = 11) public String cornerRadius = "";
    @Canonical.Canon(order = 12) public String background = "";
    @Canonical.Canon(order = 13) public String padding = "";
    @Canonical.Canon(order = 14) public String gap = "";
    @Canonical.Canon(order = 15) public String size = "";

    // ==================================================================================
    // Shape Properties (canon order 20-29)
    // ==================================================================================

    @Canonical.Canon(order = 20) public String shapeType = "";
    @Canonical.Canon(order = 21) public String shapeFill = "";
    @Canonical.Canon(order = 22) public String shapeStroke = "";
    @Canonical.Canon(order = 23) public String shapeStrokeWidth = "";
    @Canonical.Canon(order = 24) public String shapePath = "";
    @Canonical.Canon(order = 25) public String shapeWidth = "";
    @Canonical.Canon(order = 26) public String shapeHeight = "";

    // ==================================================================================
    // Text Properties (canon order 30-34)
    // ==================================================================================

    @Canonical.Canon(order = 30) public String textContent = "";
    @Canonical.Canon(order = 31) public String textBind = "";
    @Canonical.Canon(order = 32) public String textFormat = "plain";
    @Canonical.Canon(order = 33) public String textFontSize = "";
    @Canonical.Canon(order = 34) public String fontFamily = "";

    // ==================================================================================
    // Image Properties (canon order 35-39)
    // ==================================================================================

    @Canonical.Canon(order = 35) public String imageAlt = "";
    @Canonical.Canon(order = 36) public String imageBind = "";
    @Canonical.Canon(order = 37) public String imageSize = "medium";
    @Canonical.Canon(order = 38) public String imageFit = "contain";

    // ==================================================================================
    // Binding (canon order 40-49)
    // ==================================================================================

    @Canonical.Canon(order = 40) public String bindPath = "";
    @Canonical.Canon(order = 41) public String embedBind = "";
    @Canonical.Canon(order = 42) public String repeatBind = "";
    @Canonical.Canon(order = 43) public String repeatItemVar = "item";
    @Canonical.Canon(order = 44) public String repeatIndexVar = "index";
    @Canonical.Canon(order = 45) public String repeatAsKey;
    @Canonical.Canon(order = 46) public String repeatColumns = "";

    // Runtime-only: resolved Class for repeatAs (not serialized)
    public transient Class<? extends SurfaceSchema> repeatAs = null;

    // ==================================================================================
    // Border Properties (canon order 50-59)
    // ==================================================================================

    @Canonical.Canon(order = 50) public String border = "";
    @Canonical.Canon(order = 51) public String borderTop = "";
    @Canonical.Canon(order = 52) public String borderRight = "";
    @Canonical.Canon(order = 53) public String borderBottom = "";
    @Canonical.Canon(order = 54) public String borderLeft = "";
    @Canonical.Canon(order = 55) public String borderWidth = "";
    @Canonical.Canon(order = 56) public String borderStyle = "";
    @Canonical.Canon(order = 57) public String borderColor = "";
    @Canonical.Canon(order = 58) public String borderRadius = "";

    // ==================================================================================
    // Sizing & Alignment (canon order 60-69)
    // ==================================================================================

    @Canonical.Canon(order = 60) public String width = "";
    @Canonical.Canon(order = 61) public String height = "";
    @Canonical.Canon(order = 62) public String align = "";

    // Elevation (3D hint) — stored as String to avoid IEEE 754 floats
    @Canonical.Canon(order = 63) public String elevation = "";
    @Canonical.Canon(order = 64) public boolean elevationSolid = true;

    // Rotation (from @Scene.Container(rotation = ...))
    @Canonical.Canon(order = 65) public String rotation = "";
    // Transform origin (from @Scene.Container(transformOrigin = ...))
    @Canonical.Canon(order = 66) public String transformOrigin = "";
    // Node ID (from @Scene.Container(id = ...))
    @Canonical.Canon(order = 67) public String nodeId = "";
    // Container query condition (from @Scene.Query)
    @Canonical.Canon(order = 68) public String queryCondition = "";
    // Aspect ratio (from @Scene.Container(aspectRatio = ...))
    @Canonical.Canon(order = 69) public String aspectRatio = "";

    // Unified placement metadata (from @Scene.Place)
    @Canonical.Canon(order = 73) public String placeIn = "";
    @Canonical.Canon(order = 74) public String placeAnchor = "";

    // Overflow behavior (from @Scene.Container(overflow = ...))
    @Canonical.Canon(order = 75) public String overflow = "visible";

    // Constraint-style placement (from @Scene.Place)
    @Canonical.Canon(order = 76) public String placeTop = "";
    @Canonical.Canon(order = 77) public String placeBottom = "";
    @Canonical.Canon(order = 78) public String placeLeft = "";
    @Canonical.Canon(order = 79) public String placeRight = "";
    @Canonical.Canon(order = 80) public String placeTopTo = "";
    @Canonical.Canon(order = 81) public String placeBottomTo = "";
    @Canonical.Canon(order = 82) public String placeLeftTo = "";
    @Canonical.Canon(order = 83) public String placeRightTo = "";
    @Canonical.Canon(order = 84) public String placeWidth = "";
    @Canonical.Canon(order = 85) public String placeHeight = "";
    @Canonical.Canon(order = 86) public String placeMinWidth = "";
    @Canonical.Canon(order = 87) public String placeMinHeight = "";
    @Canonical.Canon(order = 88) public String placeMaxWidth = "";
    @Canonical.Canon(order = 89) public String placeMaxHeight = "";
    @Canonical.Canon(order = 90) public String placeAlignX = "";
    @Canonical.Canon(order = 91) public String placeAlignY = "";
    @Canonical.Canon(order = 92) public int placeZIndex = 0;
    @Canonical.Canon(order = 93) public String placeOverflow = "visible";

    // ==================================================================================
    // Styles, Events, Children (canon order 70+, 100)
    // ==================================================================================

    @Canonical.Canon(order = 70) public List<String> styles = new ArrayList<>();
    @Canonical.Canon(order = 71) public List<StateStyle> stateStyles = new ArrayList<>();
    @Canonical.Canon(order = 72) public List<EventHandler> events = new ArrayList<>();

    // Transitions are runtime-only (Easing is not Canonical); not serialized to CBOR
    public transient List<TransitionSpec> transitions = List.of();
    public transient String elementName;

    @Canonical.Canon(order = 100) public List<ViewNode> children = new ArrayList<>();

    // ==================================================================================
    // 3D: Image Model (canon order 110-111)
    // ==================================================================================

    @Canonical.Canon(order = 110) public String imageModelResource = "";
    @Canonical.Canon(order = 111) public int imageModelColor = -1;

    // ==================================================================================
    // 3D: Context Menu (canon order 120)
    // ==================================================================================

    @Canonical.Canon(order = 120) public List<ContextMenuItem> contextMenu = new ArrayList<>();

    // ==================================================================================
    // 3D: Depth (canon order 130) — replaces elevation for SceneCompiler path
    // ==================================================================================

    @Canonical.Canon(order = 130) public String depth = "";

    // ==================================================================================
    // 3D: Body (canon order 200-209)
    // ==================================================================================

    @Canonical.Canon(order = 200) public String bodyShape = "";
    @Canonical.Canon(order = 201) public String bodyWidth = "1m";
    @Canonical.Canon(order = 202) public String bodyHeight = "1m";
    @Canonical.Canon(order = 203) public String bodyDepth = "1m";
    @Canonical.Canon(order = 204) public String bodyRadius = "0.5m";
    @Canonical.Canon(order = 205) public String bodyMesh = "";
    @Canonical.Canon(order = 206) public int bodyColor = 0x808080;
    // Runtime-only: double fields are not CBOR-serializable (CG-CBOR forbids IEEE 754)
    public double bodyOpacity = 1.0;
    @Canonical.Canon(order = 208) public String bodyShading = "lit";

    // ==================================================================================
    // 3D: Face (canon order 210-211)
    // ==================================================================================

    @Canonical.Canon(order = 210) public String faceName = "top";
    @Canonical.Canon(order = 211) public int facePpm = 512;

    // ==================================================================================
    // 3D: Transform (canon order 220-232)
    // ==================================================================================

    @Canonical.Canon(order = 220) public String transformX = "0";
    @Canonical.Canon(order = 221) public String transformY = "0";
    @Canonical.Canon(order = 222) public String transformZ = "0";
    // Runtime-only: double fields are not CBOR-serializable (CG-CBOR forbids IEEE 754)
    public double transformYaw = 0;
    public double transformPitch = 0;
    public double transformRoll = 0;
    public double transformAxisX = 0;
    public double transformAxisY = 1;
    public double transformAxisZ = 0;
    public double transformAngle = 0;
    public double transformScaleX = 1;
    public double transformScaleY = 1;
    public double transformScaleZ = 1;

    // ==================================================================================
    // 3D: Light (canon order 240-248)
    // ==================================================================================

    @Canonical.Canon(order = 240) public String lightType = "directional";
    @Canonical.Canon(order = 241) public int lightColor = 0xFFFFFF;
    public double lightIntensity = 1.0;
    @Canonical.Canon(order = 243) public String lightX = "0";
    @Canonical.Canon(order = 244) public String lightY = "0";
    @Canonical.Canon(order = 245) public String lightZ = "5m";
    public double lightDirX = 0;
    public double lightDirY = 0;
    public double lightDirZ = -1;

    // ==================================================================================
    // 3D: Audio (canon order 250-260)
    // ==================================================================================

    @Canonical.Canon(order = 250) public String audioSrc = "";
    @Canonical.Canon(order = 251) public String audioX = "0";
    @Canonical.Canon(order = 252) public String audioY = "0";
    @Canonical.Canon(order = 253) public String audioZ = "0";
    public double audioVolume = 1.0;
    public double audioPitch = 1.0;
    @Canonical.Canon(order = 256) public boolean audioLoop = false;
    @Canonical.Canon(order = 257) public boolean audioSpatial = true;
    @Canonical.Canon(order = 258) public String audioRefDistance = "1m";
    @Canonical.Canon(order = 259) public String audioMaxDistance = "50m";
    @Canonical.Canon(order = 260) public boolean audioAutoplay = false;

    // ==================================================================================
    // 3D: Environment (canon order 270-274)
    // ==================================================================================

    @Canonical.Canon(order = 270) public int envBackground = 0x1A1A2E;
    @Canonical.Canon(order = 271) public int envAmbient = 0x404040;
    @Canonical.Canon(order = 272) public String envFogNear = "";
    @Canonical.Canon(order = 273) public String envFogFar = "";
    @Canonical.Canon(order = 274) public int envFogColor = 0x808080;

    // ==================================================================================
    // 3D: Camera (canon order 280-289)
    // ==================================================================================

    @Canonical.Canon(order = 280) public String cameraProjection = "perspective";
    public double cameraFov = 60;
    @Canonical.Canon(order = 282) public String cameraNear = "0.1m";
    @Canonical.Canon(order = 283) public String cameraFar = "1000m";
    @Canonical.Canon(order = 284) public String cameraX = "0";
    @Canonical.Canon(order = 285) public String cameraY = "5m";
    @Canonical.Canon(order = 286) public String cameraZ = "1.5m";
    @Canonical.Canon(order = 287) public String cameraTargetX = "0";
    @Canonical.Canon(order = 288) public String cameraTargetY = "0";
    @Canonical.Canon(order = 289) public String cameraTargetZ = "0";

    // ==================================================================================
    // Utility Methods
    // ==================================================================================

    public boolean hasShape() {
        return shapeType != null && !shapeType.isEmpty();
    }

    public boolean isRepeat() {
        return repeatBind != null && !repeatBind.isEmpty();
    }
}
