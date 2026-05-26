package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.runtime.stage.ItemStage;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import picocli.CommandLine;

/**
 * Apache Commons Daemon adapter for {@link Librarian}.
 *
 * <p>Use this as the daemon class with jsvc / Procrun / launchd-style service
 * managers. The service manager instantiates this class via its no-arg
 * constructor, then invokes {@link #init init}, {@link #start start},
 * {@link #stop stop}, and {@link #destroy destroy} across the daemon's
 * lifetime.
 *
 * <p>The flow inside mirrors {@link Librarian#main(String[])}: parse
 * {@link LibrarianOptions} from the daemon's arguments, bring up an
 * {@link ItemStage} (probing GraalVM, degrading gracefully if absent),
 * instantiate a Librarian on it via the existing factories, and start the
 * Parley listener. Same construction path; the only difference is that the
 * lifecycle is driven externally by the service manager rather than by a
 * blocking call to {@code Thread.join()}.
 *
 * <p>For direct command-line invocation (no service manager), use
 * {@link Librarian#main(String[]) Librarian.main} instead.
 */
@Log4j2
public class LibrarianDaemon implements Daemon {

    private ItemStage stage;
    private Librarian librarian;
    private LibrarianOptions opts;

    @Override
    public void init(DaemonContext ctx) {
        logger.info("Daemon init: {}", String.join(" ", ctx.getArguments()));
        opts = new LibrarianOptions();
        new CommandLine(opts).parseArgs(ctx.getArguments());
    }

    @Override
    public void start() {
        stage = new ItemStage();
        logger.info("ItemStage up. Polyglot: {}",
                stage.polyglotAvailable()
                        ? "GraalVM " + stage.polyglotLanguages()
                        : "Java-only");

        librarian = opts.path != null
                ? Librarian.fresh(stage, opts.effectivePath())
                : Librarian.ephemeral(stage);

        // TODO: hook this up once Parley.listen(port) is wired.
        // librarian.parley().listen(opts.port);

        logger.info("Librarian daemon started. IID: {}", librarian.iid().encodeText());
    }

    @Override
    public void stop() {
        logger.info("Daemon stop");
        // Clean shutdown of listeners happens in destroy().
    }

    @Override
    public void destroy() {
        logger.info("Daemon destroy");
        if (librarian != null) {
            try { librarian.close(); } catch (RuntimeException ignored) {}
        }
        librarian = null;
        stage = null;
    }
}
