package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.runtime.options.SessionOptions;
import dev.everydaythings.graph.ui.input.*;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.text.*;
import lombok.extern.log4j.Log4j2;
import org.jline.keymap.BindingReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.everydaythings.graph.item.component.TickRegistry;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Text-based session for CLI and TUI modes.
 *
 * <p>Both CLI and TUI modes use the same raw-mode + {@link EvalInput} pipeline
 * for input handling (tab completion, token resolution, expression building).
 * The only difference is surface rendering:
 * <ul>
 *   <li><strong>TUI mode</strong> - Full-screen ANSI via {@link TuiSurfaceRenderer},
 *       with mouse events</li>
 *   <li><strong>CLI mode</strong> - Scrolling plain text via {@link CliSurfaceRenderer}</li>
 * </ul>
 *
 * <p>All business logic (navigation, commands, ItemModel) is in {@link Session}.
 */
@Log4j2
public class TextSession extends Session {

    private final UIMode mode;
    private final SessionOptions opts;

    // Terminal components (shared by CLI and TUI modes)
    private Terminal terminal;
    private JLineKeyAdapter keyAdapter;
    private BindingReader bindingReader;
    private Attributes savedAttributes;
    private boolean mouseEnabled = false;
    private TuiSurfaceRenderer lastRenderer;
    private int renderStartRow = 0;

    // Input handling
    private JLineInputRenderer inputRenderer;

    // Live tick support (clock, timers, etc.)
    private ScheduledExecutorService liveTimer;
    private final TickRegistry tickRegistry = new TickRegistry();

    // Message buffer — survives screen clears in TUI mode
    private String pendingMessage;


    /**
     * Create a text-based session.
     */
    public TextSession(LibrarianHandle librarian, Link context, UIMode mode, SessionOptions opts) {
        super(librarian, context);
        this.mode = mode;
        this.opts = opts;
    }

    @Override
    public int run() {
        // Eval mode - execute expression and exit
        if (opts != null && opts.isEvalMode()) {
            return runEval();
        }

        // Check for positional args that aren't item refs (commands)
        if (opts != null && opts.positionalArgs != null && !opts.positionalArgs.isEmpty()) {
            String first = opts.positionalArgs.get(0);
            if (!looksLikeLink(first)) {
                return runCommand();
            }
        }

        // Wire up ItemModel to trigger render on changes
        if (itemModel != null) {
            itemModel.onChange(this::render);
        }

        // Interactive mode - CLI or TUI
        if (mode == UIMode.TUI && isTuiSupported()) {
            return runTui();
        }
        return runCli();
    }

    // ==================================================================================
    // Eval Mode
    // ==================================================================================

    private int runEval() {
        logger.info("Evaluating expression: {}", opts.evalExpression);

        String[] parts = opts.evalExpression.trim().split("\\s+");
        if (parts.length == 0) {
            System.err.println("Error: Empty expression");
            return 1;
        }

        Item ctx = contextItem().orElse(null);
        return Eval.builder()
                .librarian(librarian)
                .context(ctx)
                .session(this)
                .interactive(false)
                .build()
                .run(Arrays.asList(parts));
    }

    private int runCommand() {
        int startIndex = looksLikeLink(opts.positionalArgs.get(0)) ? 1 : 0;
        List<String> commandArgs = opts.positionalArgs.subList(startIndex, opts.positionalArgs.size());

        if (commandArgs.isEmpty()) {
            Item ctx = contextItem().orElse(null);
            if (ctx != null) {
                showItemInfo(ctx);
                return 0;
            }
            System.err.println("No command specified");
            return 1;
        }

        Item ctx = contextItem().orElse(null);
        return Eval.builder()
                .librarian(librarian)
                .context(ctx)
                .session(this)
                .interactive(false)
                .build()
                .run(commandArgs);
    }

    private void showItemInfo(Item item) {
        System.out.println(item.displayToken());
        System.out.println("  IID: " + item.iid().encodeText());
        System.out.println("  Type: " + item.getClass().getSimpleName());
        var vocab = item.vocabulary();
        if (vocab != null && vocab.size() > 0) {
            System.out.println("  Verbs:");
            for (var entry : vocab) {
                String doc = entry.doc();
                if (doc != null && !doc.isBlank()) {
                    System.out.println("    " + entry.methodName() + " - " + doc);
                } else {
                    System.out.println("    " + entry.methodName());
                }
            }
        }
    }

    // ==================================================================================
    // TUI Mode - Platform-specific terminal handling
    // ==================================================================================

    /**
     * Check if TUI is supported on this terminal.
     */
    public static boolean isTuiSupported() {
        try {
            var terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .build();
            String type = terminal.getType();
            int width = terminal.getWidth();
            int height = terminal.getHeight();
            terminal.close();

            return type != null && !type.equals("dumb") && width > 0 && height > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run interactive TUI mode.
     */
    private int runTui() {
        logger.info("Starting TUI session");

        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .nativeSignals(true)
                    .jansi(true)
                    .build();
        } catch (IOException e) {
            logger.error("Failed to create terminal", e);
            System.err.println("TUI mode requires a terminal.");
            return 1;
        }

        running = true;
        AtomicReference<String> nextMode = new AtomicReference<>();
        onModeSwitch = nextMode::set;

        try {
            // Initialize TUI components
            keyAdapter = new JLineKeyAdapter(terminal);
            inputRenderer = new JLineInputRenderer(terminal);
            bindingReader = new BindingReader(terminal.reader());

            // Set up EvalInput for the prompt
            if (itemModel != null) {
                itemModel.setRenderInputInSurface(false);
            }
            initializeEvalInput();
            if (evalInput != null) {
                inputRenderer.focus();
                inputRenderer.render(evalInput.snapshot());
            }

            // Enter raw mode
            savedAttributes = terminal.enterRawMode();

            // Enable mouse tracking
            enableMouse();

            // Initial render
            render();

            // Start live timer for periodic widget updates (clock, etc.)
            startLiveTimer();

            // Main loop
            while (running) {
                String seq = readKeySequence();
                if (seq == null || seq.isEmpty()) {
                    continue;
                }

                // Check for mouse event
                if (seq.startsWith("\u001b[<") || seq.startsWith("\u001b[M")) {
                    String mouseSeq = seq.substring(2);
                    TerminalMouseEvent mouseEvent = TerminalMouseEvent.parse(mouseSeq);
                    if (mouseEvent != null) {
                        handleMouseEvent(mouseEvent);
                        continue;
                    }
                }

                KeyChord chord = keyAdapter.fromSequence(seq);
                if (chord == null) {
                    continue;
                }

                // Handle special TUI commands (Ctrl+D, Ctrl+L, Ctrl+G)
                if (handleSpecialChord(chord)) {
                    continue;
                }

                // Let Session handle navigation keys (routes to ItemModel)
                if (handleKey(chord)) {
                    continue;
                }

                // Feed to EvalInput via bindings
                if (evalInput != null) {
                    dispatchToEvalInput(chord);
                }
            }

        } finally {
            stopLiveTimer();
            resetTerminal();
        }

        // Handle mode switch request
        String requestedMode = nextMode.get();
        if (requestedMode != null) {
            if ("CLI".equalsIgnoreCase(requestedMode)) {
                return runCli();
            }
            if ("GUI".equalsIgnoreCase(requestedMode)) {
                System.err.println("GUI mode switch from TUI not supported. Restart with --ui=gui");
            }
        }

        return 0;
    }

    /**
     * Render the current state — dispatches to mode-specific renderer.
     */
    @Override
    protected void render() {
        if (itemModel == null) return;

        if (mode == UIMode.TUI) {
            renderTuiSurface();
        } else {
            renderCliSurface();
        }
    }

    /**
     * TUI surface render — full-screen ANSI with clear and redraw.
     */
    private void renderTuiSurface() {
        if (terminal == null) return;

        // Clear screen and move to top
        terminal.writer().print("\u001b[2J\u001b[H");
        renderStartRow = 0;

        // Create renderer with Librarian-backed unit resolution
        RenderContext ctx = buildRenderContext();
        TuiSurfaceRenderer output = new TuiSurfaceRenderer(null, ctx);
        SurfaceSchema surface = toSurface();
        if (surface != null) {
            surface.render(output);
        }

        terminal.writer().print(output.result());
        lastRenderer = output;

        // Show buffered message (e.g., move result, error) above the prompt
        if (pendingMessage != null) {
            terminal.writer().println();
            terminal.writer().print(pendingMessage);
            pendingMessage = null;
        }

        terminal.writer().println();
        terminal.writer().flush();

        // Re-render the input line (prompt is suppressed in surface when EvalInput active)
        if (inputRenderer != null && evalInput != null) {
            inputRenderer.focus();
            inputRenderer.render(evalInput.snapshot());
        }
    }

    /**
     * CLI surface render — scrolling output with input re-render.
     *
     * <p>Clears the input line, prints the surface as scrolling text,
     * then re-renders the input line at the new position.
     */
    private void renderCliSurface() {
        // Clear the input renderer's display before printing surface
        if (inputRenderer != null) {
            inputRenderer.dispose();
        }

        // Render surface as scrolling text
        CliSurfaceRenderer output = new CliSurfaceRenderer();
        SurfaceSchema surface = toSurface();
        if (surface != null) {
            surface.render(output);
        }

        String result = output.result();
        if (result != null && !result.isEmpty()) {
            if (terminal != null) {
                terminal.writer().print(result);
                terminal.writer().flush();
            } else {
                System.out.print(result);
            }
        }

        // Re-render the input line
        if (inputRenderer != null && evalInput != null) {
            inputRenderer.focus();
            inputRenderer.render(evalInput.snapshot());
        }
    }

    @Override
    protected void output(String message) {
        if (mode == UIMode.TUI) {
            // Buffer for next render (TUI clears screen, so direct writes vanish)
            pendingMessage = message;
        } else if (terminal != null) {
            terminal.writer().println(message);
            terminal.writer().flush();
        } else {
            System.out.println(message);
        }
    }

    /**
     * Read a key sequence from terminal.
     */
    private String readKeySequence() {
        try {
            int c = bindingReader.peekCharacter(100);
            if (c < 0) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            c = bindingReader.readCharacter();
            sb.append((char) c);

            // If ESC, might be start of escape sequence
            if (c == 27) {
                int next = bindingReader.peekCharacter(50);
                if (next >= 0) {
                    c = bindingReader.readCharacter();
                    sb.append((char) c);

                    // Check for CSI (ESC [)
                    if (c == '[') {
                        while ((next = bindingReader.peekCharacter(10)) >= 0) {
                            c = bindingReader.readCharacter();
                            sb.append((char) c);

                            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '~') {
                                if (c == 'M' && sb.length() == 3) {
                                    for (int i = 0; i < 3 && bindingReader.peekCharacter(10) >= 0; i++) {
                                        sb.append((char) bindingReader.readCharacter());
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        while ((next = bindingReader.peekCharacter(10)) >= 0) {
                            c = bindingReader.readCharacter();
                            sb.append((char) c);
                            if (Character.isLetter(c) || c == '~') {
                                break;
                            }
                        }
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Handle special key chords that bypass normal input.
     */
    private boolean handleSpecialChord(KeyChord chord) {
        // Ctrl+D - Quit
        if (chord.ctrl() && chord.isChar('d')) {
            running = false;
            return true;
        }

        // Ctrl+L - Switch to CLI mode
        if (chord.ctrl() && chord.isChar('l')) {
            requestModeSwitch("CLI");
            return true;
        }

        // Ctrl+G - Switch to GUI mode
        if (chord.ctrl() && chord.isChar('g')) {
            requestModeSwitch("GUI");
            return true;
        }

        return false;
    }

    @Override
    protected int maxCompletions() {
        return inputRenderer != null ? inputRenderer.maxCompletions() : 10;
    }

    @Override
    protected void onInputChanged(EvalInputSnapshot snapshot) {
        if (inputRenderer != null) {
            inputRenderer.render(snapshot);
        }
    }

    @Override
    protected void onInputDispatched(Eval.EvalResult result) {
        boolean modelWillChange = switch (result) {
            case Eval.EvalResult.ItemResult i -> true;
            case Eval.EvalResult.Created c -> true;
            case Eval.EvalResult.Value v -> v.value() != null
                    && v.value().getClass().isAnnotationPresent(Type.class);
            case Eval.EvalResult.ValueWithTarget vt -> vt.value() != null
                    && vt.value().getClass().isAnnotationPresent(Type.class);
            default -> false;
        };

        // Only render explicitly when the model didn't change —
        // model changes already triggered render via itemModel.onChange()
        if (!modelWillChange) {
            render();
        }
    }

    // ==================================================================================
    // Mouse Handling
    // ==================================================================================

    private void enableMouse() {
        terminal.writer().print(TerminalMouseEvent.enableTracking());
        terminal.writer().flush();
        mouseEnabled = true;
    }

    private void disableMouse() {
        if (mouseEnabled && terminal != null) {
            terminal.writer().print(TerminalMouseEvent.disableTracking());
            terminal.writer().flush();
            mouseEnabled = false;
        }
    }

    /**
     * Reset terminal to a sane state on exit.
     *
     * <p>Disables mouse tracking, restores saved attributes (exits raw mode),
     * cleans up the input renderer, flushes and closes the terminal.
     * As a belt-and-suspenders measure, writes mouse-disable sequences
     * directly to System.out to ensure they reach the terminal even if
     * JLine's writer has buffering issues.
     */
    private void resetTerminal() {
        // Disable mouse tracking via JLine
        disableMouse();

        // Restore terminal attributes (exit raw mode)
        if (savedAttributes != null && terminal != null) {
            terminal.setAttributes(savedAttributes);
            savedAttributes = null;
        }

        // Clean up input renderer
        if (inputRenderer != null) {
            inputRenderer.blur();
            inputRenderer.dispose();
        }

        // Flush and close JLine terminal
        if (terminal != null) {
            try {
                terminal.writer().flush();
            } catch (Exception e) {
                logger.debug("Error flushing terminal", e);
            }
            try {
                terminal.close();
            } catch (IOException e) {
                logger.debug("Error closing terminal", e);
            }
        }

        // Belt-and-suspenders: write mouse-disable directly to stdout
        // in case JLine's writer didn't flush properly
        System.out.print(TerminalMouseEvent.disableTracking());
        System.out.flush();

        terminal = null;
    }

    private void handleMouseEvent(TerminalMouseEvent event) {
        if (lastRenderer == null) {
            return;
        }

        int hitRow = event.row() - renderStartRow;
        int hitCol = event.column();

        String eventType = switch (event.type()) {
            case PRESS -> event.button() == TerminalMouseEvent.Button.RIGHT ? "rightClick" : "click";
            case SCROLL_UP -> "scrollUp";
            case SCROLL_DOWN -> "scrollDown";
            default -> null;
        };

        if (eventType == null) {
            return;
        }

        TuiSurfaceRenderer.HitRegion hit = lastRenderer.hitTest(hitRow, hitCol, eventType);
        if (hit != null) {
            handleEvent(hit.action(), hit.target());
        }
    }

    // ==================================================================================
    // CLI Mode - Raw mode with EvalInput (same input path as TUI)
    // ==================================================================================

    /**
     * Run interactive CLI mode.
     *
     * <p>Uses the same raw mode + EvalInput pipeline as TUI mode.
     * The only difference is surface rendering: CLI uses scrolling
     * {@link CliSurfaceRenderer} output instead of full-screen
     * {@link TuiSurfaceRenderer}.
     *
     * <p>Tab triggers completions, arrow keys navigate them, Enter dispatches.
     * This gives terminal tab-completion feel with expression-building feedback.
     */
    private int runCli() {
        logger.info("Starting CLI session");

        running = true;
        AtomicReference<String> nextMode = new AtomicReference<>();
        onModeSwitch = nextMode::set;

        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException e) {
            logger.error("Failed to create terminal", e);
            return 1;
        }

        try {
            // Initialize input components (same as TUI)
            keyAdapter = new JLineKeyAdapter(terminal);
            inputRenderer = new JLineInputRenderer(terminal);
            bindingReader = new BindingReader(terminal.reader());

            // Set up EvalInput (shared with TUI)
            if (itemModel != null) {
                itemModel.setRenderInputInSurface(false);
            }
            initializeEvalInput();
            if (evalInput != null) {
                inputRenderer.focus();
                inputRenderer.render(evalInput.snapshot());
            }

            // Enter raw mode for character-by-character input
            savedAttributes = terminal.enterRawMode();

            // Ensure mouse tracking is off
            terminal.writer().print(TerminalMouseEvent.disableTracking());
            terminal.writer().flush();

            // Initial surface render (scrolling, not full-screen)
            renderCliSurface();

            // Main loop — same key reading as TUI, no mouse handling
            while (running) {
                String seq = readKeySequence();
                if (seq == null || seq.isEmpty()) {
                    continue;
                }

                KeyChord chord = keyAdapter.fromSequence(seq);
                if (chord == null) {
                    continue;
                }

                // Handle special chords (Ctrl+D, Ctrl+L, Ctrl+G)
                if (handleSpecialChord(chord)) {
                    continue;
                }

                // Let Session handle navigation keys
                if (handleKey(chord)) {
                    continue;
                }

                // Feed to EvalInput via bindings
                if (evalInput != null) {
                    dispatchToEvalInput(chord);
                }
            }

        } finally {
            resetTerminal();
        }

        // Handle mode switch
        String requestedMode = nextMode.get();
        if (requestedMode != null) {
            if ("TUI".equalsIgnoreCase(requestedMode)) {
                if (isTuiSupported()) {
                    return runTui();
                }
                System.err.println("TUI mode not available");
            }
        }

        return 0;
    }

    /**
     * Format a value for text display — renders Views via CliSurfaceRenderer.
     */
    @Override
    protected String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof View view && view.root() != null) {
            CliSurfaceRenderer output = new CliSurfaceRenderer();
            view.root().render(output);
            return output.result();
        }
        if (value instanceof Item item) {
            String emoji = item.emoji();
            return (emoji != null ? emoji + " " : "") + item.displayToken();
        }
        return value.toString();
    }

    // ==================================================================================
    // Unit Resolution
    // ==================================================================================

    /**
     * Build a RenderContext with Librarian-backed unit resolution.
     *
     * <p>Units are resolved through the LibrarianHandle's token dictionary
     * (the core WORDS → ITEMS mechanism).
     */
    private RenderContext buildRenderContext() {
        return RenderContext.builder()
                .renderer(RenderContext.RENDERER_TUI)
                .breakpoint(RenderContext.BREAKPOINT_MD)
                .addCapability("color")
                .viewportWidth(terminal != null ? terminal.getWidth() : 80)
                .viewportHeight(terminal != null ? terminal.getHeight() : 24)
                .librarian(this.librarian)
                .renderMetrics(RenderMetrics.TUI_DEFAULT)
                .build();
    }

    // ==================================================================================
    // Live Tick Timer
    // ==================================================================================

    /**
     * Start a 1-second periodic timer that ticks live components and repaints.
     * Enables live widgets (clock, timers) to update in TUI mode.
     */
    private void startLiveTimer() {
        stopLiveTimer();

        contextItem().ifPresent(item -> tickRegistry.rebuild(item.content()));

        liveTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tui-live-timer");
            t.setDaemon(true);
            return t;
        });
        liveTimer.scheduleAtFixedRate(() -> {
            try {
                boolean ticked = tickRegistry.tickAll();
                if (ticked) {
                    render();
                }
            } catch (Exception e) {
                logger.trace("Live timer tick failed", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopLiveTimer() {
        if (liveTimer != null) {
            liveTimer.shutdown();
            liveTimer = null;
        }
        tickRegistry.clear();
    }

    @Override
    protected void onContextComponentsChanged(Item item) {
        tickRegistry.rebuild(item.content());
    }

    @Override
    public void close() {
        running = false;
        stopLiveTimer();
        super.close();
    }
}
