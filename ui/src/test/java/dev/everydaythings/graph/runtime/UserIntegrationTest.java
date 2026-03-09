package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.item.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: User creation and principal management through Eval.
 *
 * <p>Tests the end-to-end path:
 * create user alice → verify identity → serve alice → verify principal
 */
class UserIntegrationTest {

    private Librarian librarian;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
    }

    @Test
    void librarian_autoCreatesPrincipalOnBoot() {
        assertThat(librarian.principal()).isPresent();
        Signer principal = librarian.principal().get();
        assertThat(principal).isInstanceOf(User.class);
        assertThat(principal.name()).isNotNull();
    }

    @Test
    void eval_createUser_returnsUserWithName() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "user", "alice"));

        assertThat(result).isInstanceOf(Eval.EvalResult.Created.class);
        Item created = ((Eval.EvalResult.Created) result).item();
        assertThat(created).isInstanceOf(User.class);

        User user = (User) created;
        assertThat(user.name()).isEqualTo("alice");
        assertThat(user.displayToken()).isEqualTo("alice");
    }

    @Test
    void user_canSign_afterCreation() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "user", "alice"));
        User user = (User) ((Eval.EvalResult.Created) result).item();

        assertThat(user.canSign()).isTrue();
        assertThat(user.publicKey()).isNotNull();
    }

    @Test
    void createUser_doesNotChangePrincipal() {
        // Librarian auto-creates a principal on boot
        assertThat(librarian.principal()).isPresent();
        Signer originalPrincipal = librarian.principal().get();

        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        eval.evaluateCommand(List.of("create", "user", "alice"));

        // Creating a new user should NOT change the principal
        assertThat(librarian.principal()).isPresent();
        assertThat(librarian.principal().get().iid()).isEqualTo(originalPrincipal.iid());
    }

    @Test
    void serve_setsPrincipal() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        // First create a user
        Eval.EvalResult createResult = eval.evaluateCommand(List.of("create", "user", "alice"));
        assertThat(createResult).isInstanceOf(Eval.EvalResult.Created.class);
        User alice = (User) ((Eval.EvalResult.Created) createResult).item();

        // Verify user's name is indexed in token dictionary
        var tokenDict = librarian.tokenIndex();
        assertThat(tokenDict).as("Token dictionary should exist").isNotNull();
        var postings = tokenDict.lookup("alice").toList();
        assertThat(postings).as("Token 'alice' should be indexed after user creation")
                .isNotEmpty();
        assertThat(postings.get(0).target()).isEqualTo(alice.iid());

        // Verify "serve" resolves as a verb token
        var servePostings = tokenDict.lookup("serve").toList();
        assertThat(servePostings).as("Token 'serve' should be indexed as a verb").isNotEmpty();

        // Then serve (sets principal)
        Eval.EvalResult serveResult = eval.evaluateCommand(List.of("serve", "alice"));

        assertThat(serveResult).as("serve alice result: " + serveResult).satisfies(r ->
                assertThat(r.isSuccess()).as("serve should succeed").isTrue());
        assertThat(librarian.principal()).isPresent();
        assertThat(librarian.principal().get()).isInstanceOf(User.class);
        assertThat(librarian.principal().get().displayToken()).isEqualTo("alice");
    }

    @Test
    void prompt_showsPrincipal_afterServe() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        // Create and serve
        eval.evaluateCommand(List.of("create", "user", "alice"));
        eval.evaluateCommand(List.of("serve", "alice"));

        // Build a session and check the prompt
        Session session = Session.create(
                new LocalLibrarian(librarian),
                (Item) librarian,
                new dev.everydaythings.graph.runtime.options.SessionOptions()
        );

        String prompt = session.buildPrompt();
        assertThat(prompt).contains("alice@");
    }

    @Test
    void user_hasUniqueIID() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult r1 = eval.evaluateCommand(List.of("create", "user", "alice"));
        Eval.EvalResult r2 = eval.evaluateCommand(List.of("create", "user", "bob"));

        User alice = (User) ((Eval.EvalResult.Created) r1).item();
        User bob = (User) ((Eval.EvalResult.Created) r2).item();

        assertThat(alice.iid()).isNotEqualTo(bob.iid());
        assertThat(alice.name()).isEqualTo("alice");
        assertThat(bob.name()).isEqualTo("bob");
    }

    @Test
    void user_tokenResolvesToType() {
        // "user" alone should navigate to the User type seed
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("user"));

        assertThat(result).isInstanceOf(Eval.EvalResult.ItemResult.class);
        Item item = ((Eval.EvalResult.ItemResult) result).item();
        assertThat(item.iid()).isEqualTo(ItemID.fromString("cg:type/user"));
    }

    @Test
    void fullFlow_createUser_serve_createChess_play() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        // Step 1: Create user
        Eval.EvalResult userResult = eval.evaluateCommand(List.of("create", "user", "alice"));
        assertThat(userResult).isInstanceOf(Eval.EvalResult.Created.class);
        User alice = (User) ((Eval.EvalResult.Created) userResult).item();

        // Step 2: Serve user (set as principal)
        eval.evaluateCommand(List.of("serve", "alice"));
        assertThat(librarian.principal()).isPresent();

        // Step 3: Create an item
        Eval.EvalResult itemResult = eval.evaluateCommand(List.of("create", "item"));
        assertThat(itemResult).isInstanceOf(Eval.EvalResult.Created.class);
        Item item = ((Eval.EvalResult.Created) itemResult).item();

        // Step 4: Create chess on the item context
        Eval evalOnItem = Eval.builder()
                .librarian(librarian)
                .context(item)
                .interactive(false)
                .build();

        Eval.EvalResult chessResult = evalOnItem.evaluateCommand(List.of("create", "chess"));
        assertThat(chessResult.isSuccess()).isTrue();
    }
}
