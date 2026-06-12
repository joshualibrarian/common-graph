package dev.everydaythings.graph.runtime.eval;

import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.cryptography.vault.JwksFileVault;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.network.Endpoint;
import dev.everydaythings.graph.network.UnixEndpoint;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.transport.TransportRegistry;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import dev.everydaythings.graph.runtime.librarian.RemoteLibrarian;
import dev.everydaythings.graph.runtime.session.SessionResolver;
import dev.everydaythings.graph.runtime.session.SessionStore;
import dev.everydaythings.graph.runtime.session.SystemPaths;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code cg-eval} — one-shot CLI entry that submits an INPUT frame to a
 * librarian on behalf of a session.
 *
 * <p>The procedural eval flow per [[project_eval_and_remote_session_design_2026_06_01]]
 * (revised by 2026-06-09 design pass — no EvalSession class; eval is just a
 * procedure):
 *
 * <ol>
 *   <li>Resolve the session location via {@link SessionResolver}.</li>
 *   <li>Load or mint the session vault at that location via
 *       {@link SessionStore}.</li>
 *   <li>Load the user vault (required when minting a fresh session — DELEGATION
 *       is signed by the User).</li>
 *   <li>Construct a {@link RemoteLibrarian} with the session and user vaults.</li>
 *   <li>Open the connection to the librarian's parley socket.</li>
 *   <li>Build an INPUT frame carrying the text and the context-item iid
 *       (defaults to the session iid per the implicit-context convention).</li>
 *   <li>Submit (fire-and-forget) and close.</li>
 * </ol>
 *
 * <p>Real parse / response observation lands incrementally — the wire path is
 * here, the parse handler on the librarian (Slice B parser-fill) is its own
 * piece of work.  This entry point exists to drive that integration end-to-end.
 *
 * <h2>CLI shape</h2>
 *
 * <pre>{@code
 * cg-eval "create user named bob"
 * cg-eval --in @<context-iid> "do something"
 * cg-eval --session ./my-session/ "..."
 * cg-eval --session-name work "..."
 * cg-eval --ephemeral "..."                          # one-shot, no persistence
 * cg-eval --librarian /run/librarian/parley.sock "..."
 * }</pre>
 */
@Log4j2
@Command(
        name = "cg-eval",
        description = "Submit a one-shot text input to a librarian via the session.",
        mixinStandardHelpOptions = true)
public final class CgEval implements Callable<Integer> {

    @Parameters(arity = "1..*", description = "Text to deliver as INPUT to the context item.")
    List<String> textParts;

    @Option(names = {"--session"}, description = "Explicit session materialization path.")
    Path sessionPath;

    @Option(names = {"--session-name"}, description = "Workspace name when no explicit path (default: ${DEFAULT-VALUE}).",
            defaultValue = SessionResolver.DEFAULT_NAME)
    String sessionName;

    @Option(names = {"--ephemeral"}, description = "Place a one-shot session in XDG_RUNTIME_DIR; no persistence.")
    boolean ephemeral;

    @Option(names = {"--in", "--context"},
            description = "Context item iid to deliver input to.  Defaults to the session itself.")
    String contextRefString;

    @Option(names = {"--librarian"},
            description = "Librarian endpoint (Unix socket path).  Defaults to ${DEFAULT-VALUE}.",
            defaultValue = "${env:XDG_RUNTIME_DIR}/librarian/parley.sock")
    String librarianEndpoint;

    @Option(names = {"--user-vault"},
            description = "Path to the User vault directory (needed when minting a fresh session).")
    Path userVaultPath;

    public static void main(String[] args) {
        int code = new CommandLine(new CgEval()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        String text = String.join(" ", textParts);
        if (text.isBlank()) {
            System.err.println("cg-eval: empty input text");
            return 2;
        }

        SessionResolver.Resolved sessionLocation = resolveSession();
        logger.info("Session: {}", sessionLocation);

        Vault userVault = loadUserVaultIfNeeded(sessionLocation);
        Vault sessionVault = SessionStore.loadOrMintSessionVault(sessionLocation);
        logger.info("Session iid: {}", sessionVault.identity());

        ItemRef contextIid = resolveContext(sessionVault);
        logger.info("Context: {}", contextIid);

        Endpoint endpoint = resolveLibrarianEndpoint();
        logger.info("Librarian endpoint: {}", endpoint);

        Transport transport;
        try {
            transport = TransportRegistry.require(endpoint);
        } catch (Exception e) {
            System.err.println("cg-eval: no transport registered for endpoint " + endpoint);
            return 3;
        }

        try (RemoteLibrarian remote = new RemoteLibrarian(sessionVault, userVault, null)) {
            try {
                remote.connect(transport, endpoint).get();
            } catch (Exception e) {
                System.err.println("cg-eval: failed to connect to librarian at " + endpoint
                        + ": " + e.getMessage());
                return 4;
            }

            Frame inputFrame = buildInputFrame(contextIid, text, sessionVault.identity());
            try {
                remote.submit(inputFrame);
            } catch (Exception e) {
                System.err.println("cg-eval: submit failed: " + e.getMessage());
                return 5;
            }
            // Fire-and-forget for now.  Once response-correlation lands for
            // INPUT/parse-result, this is where we wait for the result frame
            // and print it.  For v1 we just print the submitted frame's iid.
            System.out.println("submitted: " + inputFrame.body().headRef());
        }
        return 0;
    }

    /**
     * Resolve the session location from CLI flags.  Per
     * {@link SessionResolver}'s order: explicit path → walk-up → XDG default.
     */
    private SessionResolver.Resolved resolveSession() {
        return SessionResolver.resolve(
                sessionPath, sessionName, ephemeral,
                Paths.get("").toAbsolutePath(),
                SystemPaths.system());
    }

    /**
     * Load the user vault when needed for minting a fresh session.  If the
     * session already has a vault on disk, the user vault is not needed
     * (the bootstrap dance only signs DELEGATION on first contact).
     *
     * <p>Resolution:
     * <ul>
     *   <li>{@code --user-vault &lt;path&gt;} → load from there.</li>
     *   <li>Otherwise: {@code ~/.vault/} per the convention in
     *       [[project_materialization_pattern_2026_06_05]].</li>
     * </ul>
     *
     * <p>Returns {@code null} if no user vault is found and the session
     * already exists (load-only case, no delegation needed).
     */
    private Vault loadUserVaultIfNeeded(SessionResolver.Resolved location) {
        if (SessionStore.hasVault(location) && userVaultPath == null) {
            return null;
        }
        Path vaultDir = (userVaultPath != null)
                ? userVaultPath
                : SystemPaths.system().home().resolve(".vault");
        try {
            return JwksFileVault.load(vaultDir);
        } catch (Exception e) {
            if (SessionStore.hasVault(location)) {
                // Session already exists; user vault not strictly required.
                logger.debug("User vault not loaded ({}); proceeding with existing session.", e.toString());
                return null;
            }
            throw new IllegalStateException(
                    "Cannot mint a fresh session without a User vault at " + vaultDir
                            + " (pass --user-vault or run 'cg init')", e);
        }
    }

    /**
     * Determine the context-item iid.  --in / --context overrides; default is
     * the session itself per the implicit-context convention
     * ([[project_intrinsic_vs_ui_surface_2026_06_01]]).
     */
    private ItemRef resolveContext(Vault sessionVault) {
        if (contextRefString != null && !contextRefString.isBlank()) {
            return ItemRef.parseText(contextRefString);
        }
        return sessionVault.identity();
    }

    private Endpoint resolveLibrarianEndpoint() {
        return UnixEndpoint.of(librarianEndpoint);
    }

    /**
     * Build the INPUT frame: predicate INPUT, THEME = context iid, VALUE = text,
     * AGENT = session iid.  No record at this stage — RemoteLibrarian.submit
     * adds the session-signed envelope record carrying SEQUENCE.
     */
    private static Frame buildInputFrame(ItemRef contextIid, String text, ItemRef sessionIid) {
        return Frame.compose(ItemRef.iid(LibrarianVocabulary.Input.KEY))
                .with(ItemRef.iid(ThematicRole.Theme.KEY), contextIid)
                .with(ItemRef.iid(ThematicRole.Value.KEY), text)
                .with(ItemRef.iid(ThematicRole.Agent.KEY), sessionIid)
                .build();
    }
}
