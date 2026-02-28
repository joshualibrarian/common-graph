package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.Camera;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Camera controller with orbit and fly modes for the 3D scene.
 *
 * <p>Two content-aware behaviors controlled by {@link ContentHint}:
 *
 * <p><b>OBJECT</b> — inspecting a 3D body (default):
 * <ul>
 *   <li>Starts in ORBIT mode: camera orbits around the object's center</li>
 *   <li>Left-drag orbits (yaw/pitch)</li>
 *   <li>Right-drag pans the target point</li>
 *   <li>Scroll zooms</li>
 *   <li>Tab switches to FLY mode</li>
 * </ul>
 *
 * <p><b>SPACE</b> — exploring an environment:
 * <ul>
 *   <li>Starts in FLY mode: camera moves through the space</li>
 *   <li>Right-drag looks around (yaw/pitch)</li>
 *   <li>WASD moves forward/back/left/right relative to look direction</li>
 *   <li>Q/E moves down/up</li>
 *   <li>Shift sprints (3x speed)</li>
 *   <li>Scroll adjusts movement speed</li>
 *   <li>Tab switches to ORBIT mode</li>
 * </ul>
 */
public class CameraController {

    public enum Mode { ORBIT, FLY, LOCKED }

    /**
     * Hint about what content the camera is viewing,
     * which determines the default mode and control scheme.
     */
    public enum ContentHint { OBJECT, SPACE }

    private Mode mode = Mode.ORBIT;
    private ContentHint contentHint = ContentHint.OBJECT;
    private boolean orthographic = false;
    private double orthographicHalfHeight = 1.0;

    // Orbit state
    private double orbitYaw = 0;       // degrees, horizontal angle
    private double orbitPitch = 20;    // degrees, vertical angle (positive = above target)
    private double orbitDistance = 3.5;
    private double targetX = 0, targetY = 1.0, targetZ = 0;

    // Fly state
    private double eyeX, eyeY = 1.6, eyeZ = 3.5;
    private double flyYaw = 180, flyPitch = 0; // degrees — facing -Z

    // Camera projection
    private double fov = 60;
    private double nearPlane = 0.1;
    private double farPlane = 1000;

    // Mouse tracking
    private boolean dragging = false;
    private int dragButton = -1;
    private double lastMouseX, lastMouseY;
    private static final double MOUSE_SENSITIVITY = 0.3;

    // Raw key state for continuous movement
    private final boolean[] keysDown = new boolean[512];
    private static final double BASE_MOVE_SPEED = 3.0;  // meters per second
    private static final double SPRINT_MULTIPLIER = 3.0;
    private static final double ZOOM_SPEED = 0.3;       // meters per scroll tick
    private static final double MIN_ORBIT_DISTANCE = 0.3;

    // Adjustable fly speed (scroll wheel in FLY mode)
    private double flySpeedMultiplier = 1.0;
    private static final double MIN_FLY_SPEED = 0.1;
    private static final double MAX_FLY_SPEED = 20.0;
    private static final double SPEED_SCROLL_FACTOR = 1.25; // multiplicative per scroll tick

    // ==================== Camera Defaults ====================

    /**
     * Set camera defaults from a space's @Space.Camera annotation values.
     */
    public void setDefaults(double fov, double near, double far,
                            double eyeX, double eyeY, double eyeZ,
                            double targetX, double targetY, double targetZ) {
        this.fov = fov;
        this.nearPlane = near;
        this.farPlane = far;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;

        // Derive orbit parameters from eye position and target
        double dx = eyeX - targetX;
        double dy = eyeY - targetY;
        double dz = eyeZ - targetZ;
        this.orbitDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.orbitYaw = Math.toDegrees(Math.atan2(dx, dz));
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        this.orbitPitch = Math.toDegrees(Math.atan2(dy, horizontalDist));

        // Also initialize fly state
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.flyYaw = orbitYaw + 180; // facing toward target
        this.flyPitch = -orbitPitch;

        // Reset fly speed on new scene
        this.flySpeedMultiplier = 1.0;

        // Set initial mode based on content hint
        if (!orthographic) {
            mode = (contentHint == ContentHint.SPACE) ? Mode.FLY : Mode.ORBIT;
        }
    }

    // ==================== Content Hint ====================

    /**
     * Set the content hint, which determines the default camera mode.
     * Call before {@link #setDefaults} so it takes effect.
     */
    public void setContentHint(ContentHint hint) {
        this.contentHint = hint;
    }

    public ContentHint contentHint() {
        return contentHint;
    }

    // ==================== Orthographic Mode ====================

    /**
     * Enable/disable orthographic (FLAT) mode.
     * In orthographic mode, all mouse/keyboard interaction is disabled
     * and the camera uses an orthographic projection filling the viewport.
     */
    public void setOrthographic(boolean ortho) {
        this.orthographic = ortho;
        if (ortho) {
            mode = Mode.LOCKED;
        } else if (mode == Mode.LOCKED) {
            mode = (contentHint == ContentHint.SPACE) ? Mode.FLY : Mode.ORBIT;
        }
    }

    public boolean isOrthographic() {
        return orthographic;
    }

    /**
     * Set orthographic vertical half-span in world units.
     * The horizontal span is derived from viewport aspect.
     */
    public void setOrthographicHalfHeight(double halfHeight) {
        this.orthographicHalfHeight = Math.max(0.05, halfHeight);
    }

    // ==================== Input Handlers ====================

    public void onMouseButton(int button, int action, int mods) {
        if (mode == Mode.LOCKED) return;
        if (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT
                || button == GLFW_MOUSE_BUTTON_MIDDLE) {
            if (action == GLFW_PRESS) {
                dragging = true;
                dragButton = button;
            } else if (action == GLFW_RELEASE) {
                dragging = false;
                dragButton = -1;
            }
        }
    }

    public void onCursorPos(double x, double y) {
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;

        if (mode == Mode.LOCKED || !dragging) return;

        if (mode == Mode.ORBIT) {
            if (dragButton == GLFW_MOUSE_BUTTON_LEFT) {
                // Left-drag: orbit (rotate around target)
                orbitYaw += dx * MOUSE_SENSITIVITY;
                orbitPitch += dy * MOUSE_SENSITIVITY;
                orbitPitch = clamp(orbitPitch, -89, 89);
            } else if (dragButton == GLFW_MOUSE_BUTTON_RIGHT
                    || dragButton == GLFW_MOUSE_BUTTON_MIDDLE) {
                // Right/middle-drag: pan target point in camera-local XY plane
                panTarget(dx, dy);
            }
        } else if (mode == Mode.FLY) {
            if (dragButton == GLFW_MOUSE_BUTTON_RIGHT
                    || dragButton == GLFW_MOUSE_BUTTON_LEFT) {
                // Right or left drag: look around
                flyYaw += dx * MOUSE_SENSITIVITY;
                flyPitch -= dy * MOUSE_SENSITIVITY;
                flyPitch = clamp(flyPitch, -89, 89);
            } else if (dragButton == GLFW_MOUSE_BUTTON_MIDDLE) {
                // Middle-drag in FLY: strafe (pan without rotation)
                panFly(dx, dy);
            }
        }
    }

    public void onScroll(double xOffset, double yOffset) {
        if (mode == Mode.LOCKED) return;
        if (mode == Mode.ORBIT) {
            // Scroll zooms orbit distance
            orbitDistance -= yOffset * ZOOM_SPEED;
            orbitDistance = Math.max(MIN_ORBIT_DISTANCE, orbitDistance);
        } else if (mode == Mode.FLY) {
            // Scroll adjusts movement speed
            if (yOffset > 0) {
                flySpeedMultiplier = Math.min(MAX_FLY_SPEED, flySpeedMultiplier * SPEED_SCROLL_FACTOR);
            } else if (yOffset < 0) {
                flySpeedMultiplier = Math.max(MIN_FLY_SPEED, flySpeedMultiplier / SPEED_SCROLL_FACTOR);
            }
        }
    }

    /**
     * Track raw GLFW key press/release for continuous WASD movement.
     * Call this from the GLFW key callback (before SkiaKeyAdapter filtering).
     *
     * @return true if the key was consumed (mode toggle)
     */
    public boolean onKeyRaw(int key, int action) {
        if (mode == Mode.LOCKED) return false;
        if (key >= 0 && key < keysDown.length) {
            keysDown[key] = (action != GLFW_RELEASE);
        }

        // Tab toggles between ORBIT and FLY
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
            if (mode == Mode.ORBIT) {
                transitionToFly();
            } else if (mode == Mode.FLY) {
                transitionToOrbit();
            }
            return true;
        }

        // Escape in FLY mode returns to ORBIT
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS && mode == Mode.FLY) {
            transitionToOrbit();
            return true;
        }

        return false;
    }

    // ==================== Per-Frame Update ====================

    /**
     * Update camera state. Called every frame from onBeforeRender.
     *
     * @param deltaTime seconds since last frame
     */
    public void update(double deltaTime) {
        if (mode == Mode.LOCKED) return;
        if (mode == Mode.FLY) {
            double speed = BASE_MOVE_SPEED * flySpeedMultiplier * deltaTime;

            // Sprint with Shift
            if (keysDown[GLFW_KEY_LEFT_SHIFT] || keysDown[GLFW_KEY_RIGHT_SHIFT]) {
                speed *= SPRINT_MULTIPLIER;
            }

            // Forward direction from yaw (XZ plane)
            double radYaw = Math.toRadians(flyYaw);
            double forwardX = Math.sin(radYaw);
            double forwardZ = Math.cos(radYaw);
            double rightX = Math.cos(radYaw);
            double rightZ = -Math.sin(radYaw);

            // WASD movement
            if (keysDown[GLFW_KEY_W]) { eyeX += forwardX * speed; eyeZ += forwardZ * speed; }
            if (keysDown[GLFW_KEY_S]) { eyeX -= forwardX * speed; eyeZ -= forwardZ * speed; }
            if (keysDown[GLFW_KEY_A]) { eyeX -= rightX * speed; eyeZ -= rightZ * speed; }
            if (keysDown[GLFW_KEY_D]) { eyeX += rightX * speed; eyeZ += rightZ * speed; }
            if (keysDown[GLFW_KEY_E]) { eyeY += speed; }
            if (keysDown[GLFW_KEY_Q]) { eyeY -= speed; }
        }
    }

    /**
     * Apply current camera state to the Filament Camera.
     */
    public void applyToCamera(Camera cam, double aspect) {
        if (orthographic) {
            // Keep a stable world-space span (no perspective skew/jitter).
            double halfH = orthographicHalfHeight;
            double halfW = halfH * Math.max(0.1, aspect);
            cam.setProjection(Camera.Projection.ORTHO,
                    -halfW, halfW, -halfH, halfH, nearPlane, farPlane);

            // Reuse scene defaults for view direction/target.
            double[] eye = eyePosition();
            cam.lookAt(eye[0], eye[1], eye[2], targetX, targetY, targetZ, 0, 1, 0);
            return;
        }

        cam.setProjection(fov, aspect, nearPlane, farPlane, Camera.Fov.VERTICAL);

        if (mode == Mode.ORBIT) {
            // Compute eye position from orbit angles and distance
            double radYaw = Math.toRadians(orbitYaw);
            double radPitch = Math.toRadians(orbitPitch);
            double cosPitch = Math.cos(radPitch);

            double ex = targetX + orbitDistance * Math.sin(radYaw) * cosPitch;
            double ey = targetY + orbitDistance * Math.sin(radPitch);
            double ez = targetZ + orbitDistance * Math.cos(radYaw) * cosPitch;

            cam.lookAt(ex, ey, ez, targetX, targetY, targetZ, 0, 1, 0);
        } else {
            // Fly mode — eye position + look direction from yaw/pitch
            double radYaw = Math.toRadians(flyYaw);
            double radPitch = Math.toRadians(flyPitch);
            double cosPitch = Math.cos(radPitch);

            double lookX = eyeX + Math.sin(radYaw) * cosPitch;
            double lookY = eyeY + Math.sin(radPitch);
            double lookZ = eyeZ + Math.cos(radYaw) * cosPitch;

            cam.lookAt(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, 0, 1, 0);
        }
    }

    // ==================== Mode Transitions ====================

    private void transitionToFly() {
        // Compute current eye position from orbit state
        double radYaw = Math.toRadians(orbitYaw);
        double radPitch = Math.toRadians(orbitPitch);
        double cosPitch = Math.cos(radPitch);

        eyeX = targetX + orbitDistance * Math.sin(radYaw) * cosPitch;
        eyeY = targetY + orbitDistance * Math.sin(radPitch);
        eyeZ = targetZ + orbitDistance * Math.cos(radYaw) * cosPitch;

        // Face toward the target
        flyYaw = orbitYaw + 180;
        flyPitch = -orbitPitch;

        mode = Mode.FLY;
    }

    private void transitionToOrbit() {
        // Reconstruct orbit distance from current fly position
        double dx = eyeX - targetX;
        double dy = eyeY - targetY;
        double dz = eyeZ - targetZ;
        orbitDistance = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        orbitYaw = Math.toDegrees(Math.atan2(dx, dz));
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        orbitPitch = Math.toDegrees(Math.atan2(dy, horizontalDist));

        mode = Mode.ORBIT;
    }

    // ==================== Pan Helpers ====================

    /**
     * Pan the orbit target in the camera-local XY plane.
     */
    private void panTarget(double dx, double dy) {
        // Scale pan speed by distance (farther away = larger movements)
        double panScale = orbitDistance * 0.002;

        // Camera's right and up vectors in world space
        double radYaw = Math.toRadians(orbitYaw);
        double radPitch = Math.toRadians(orbitPitch);

        // Right vector (perpendicular to orbit yaw in XZ plane)
        double rightX = Math.cos(radYaw);
        double rightZ = -Math.sin(radYaw);

        // Up vector (perpendicular to both forward and right, in camera plane)
        double cosPitch = Math.cos(radPitch);
        double sinPitch = Math.sin(radPitch);
        double upX = -Math.sin(radYaw) * sinPitch;
        double upY = cosPitch;
        double upZ = -Math.cos(radYaw) * sinPitch;

        targetX -= (dx * rightX + dy * upX) * panScale;
        targetY -= (dy * upY) * panScale;
        targetZ -= (dx * rightZ + dy * upZ) * panScale;
    }

    /**
     * Pan the fly camera position (strafe) without changing look direction.
     */
    private void panFly(double dx, double dy) {
        double panScale = 0.01 * flySpeedMultiplier;

        double radYaw = Math.toRadians(flyYaw);
        double rightX = Math.cos(radYaw);
        double rightZ = -Math.sin(radYaw);

        eyeX -= dx * rightX * panScale;
        eyeZ -= dx * rightZ * panScale;
        eyeY += dy * panScale;
    }

    // ==================== Accessors ====================

    public Mode mode() { return mode; }

    /**
     * Current fly speed multiplier (adjusted by scroll in FLY mode).
     */
    public double flySpeedMultiplier() { return flySpeedMultiplier; }

    /**
     * Get the current eye position as {x, y, z}.
     *
     * <p>In orbit mode, derives position from orbit angles and distance.
     * In fly mode, returns the fly eye position directly.
     */
    public double[] eyePosition() {
        if (mode == Mode.ORBIT) {
            double radYaw = Math.toRadians(orbitYaw);
            double radPitch = Math.toRadians(orbitPitch);
            double cosPitch = Math.cos(radPitch);
            return new double[]{
                    targetX + orbitDistance * Math.sin(radYaw) * cosPitch,
                    targetY + orbitDistance * Math.sin(radPitch),
                    targetZ + orbitDistance * Math.cos(radYaw) * cosPitch
            };
        } else {
            return new double[]{eyeX, eyeY, eyeZ};
        }
    }

    /**
     * Get the look-at direction as a normalized {x, y, z} vector.
     *
     * <p>In orbit mode, the direction points from eye toward the target.
     * In fly mode, derived from flyYaw and flyPitch.
     */
    public double[] lookDirection() {
        if (mode == Mode.ORBIT) {
            double[] eye = eyePosition();
            double dx = targetX - eye[0];
            double dy = targetY - eye[1];
            double dz = targetZ - eye[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-9) return new double[]{0, 0, -1};
            return new double[]{dx / len, dy / len, dz / len};
        } else {
            double radYaw = Math.toRadians(flyYaw);
            double radPitch = Math.toRadians(flyPitch);
            double cosPitch = Math.cos(radPitch);
            return new double[]{
                    Math.sin(radYaw) * cosPitch,
                    Math.sin(radPitch),
                    Math.cos(radYaw) * cosPitch
            };
        }
    }

    /**
     * Check if a WASD/Q/E movement key is currently pressed.
     * Used by GraphicalSession to decide whether to consume key events.
     */
    public boolean isMovementKeyDown() {
        return keysDown[GLFW_KEY_W] || keysDown[GLFW_KEY_A] ||
               keysDown[GLFW_KEY_S] || keysDown[GLFW_KEY_D] ||
               keysDown[GLFW_KEY_Q] || keysDown[GLFW_KEY_E];
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
