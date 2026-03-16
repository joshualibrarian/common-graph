package dev.everydaythings.graph.web;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.session.SessionMessage;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.network.session.RenderInstructionRecorder;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic test: verifies what the server sends for Views
 * and that the JS client can decode it.
 */
@DisplayName("View Serialization")
class ViewSerializationTest {

    private Librarian librarian;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
    }

    @AfterEach
    void tearDown() {
        if (librarian != null) librarian.close();
    }

    @Test
    @DisplayName("simple text view produces instruction stream")
    void simpleTextViewProducesInstructions() {
        View view = View.of(TextSurface.of("Hello, World!"));

        RenderInstructionRecorder recorder = new RenderInstructionRecorder();
        view.root().render(recorder);
        CBORObject instructions = recorder.toCbor();

        System.out.println("Instructions: " + instructions.ToJSONString());

        assertThat(instructions.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("dispatch response CBOR has expected structure")
    void dispatchResponseCborStructure() {
        View view = View.of(TextSurface.of("Hello from CG"));
        SessionMessage.DispatchResponse response =
                SessionMessage.DispatchResponse.success(1, view);

        // Encode to bytes (what gets sent over the wire)
        byte[] encoded = response.encode();
        System.out.println("Encoded bytes: " + encoded.length);

        // Decode the raw CBOR to inspect structure
        CBORObject tagged = CBORObject.DecodeFromBytes(encoded);
        System.out.println("Tag: " + tagged.getMostOuterTag());
        CBORObject map = tagged.UntagOne();
        System.out.println("Keys: " + map.getKeys());
        System.out.println("Full CBOR: " + map.ToJSONString());

        if (map.ContainsKey("view")) {
            CBORObject viewObj = map.get("view");
            System.out.println("View CBOR: " + viewObj.ToJSONString());

            if (viewObj.ContainsKey("instructions")) {
                CBORObject instrs = viewObj.get("instructions");
                System.out.println("Instructions count: " + instrs.size());
                for (int i = 0; i < instrs.size(); i++) {
                    System.out.println("  [" + i + "]: " + instrs.get(i).ToJSONString());
                }
            } else {
                System.out.println("NO instructions key in view!");
            }
        } else {
            System.out.println("NO view key in response!");
        }

        // Verify round-trip decode
        ProtocolMessage decoded = ProtocolMessage.decode(encoded);
        assertThat(decoded).isInstanceOf(SessionMessage.DispatchResponse.class);
        SessionMessage.DispatchResponse dr = (SessionMessage.DispatchResponse) decoded;
        assertThat(dr.success()).isTrue();
        assertThat(dr.requestId()).isEqualTo(1);
    }

    @Test
    @DisplayName("librarian dispatch produces renderable view")
    void librarianDispatchProducesView() {
        // Simulate what happens when the web client dispatches "list"
        ActionResult result = librarian.dispatch("list", List.of());
        System.out.println("Dispatch success: " + result.success());
        System.out.println("Result value type: " +
                (result.value() != null ? result.value().getClass().getSimpleName() : "null"));

        if (result.value() instanceof View v) {
            System.out.println("View root type: " +
                    (v.root() != null ? v.root().getClass().getSimpleName() : "null"));

            if (v.root() != null) {
                RenderInstructionRecorder recorder = new RenderInstructionRecorder();
                v.root().render(recorder);
                CBORObject instructions = recorder.toCbor();
                System.out.println("Instructions count: " + instructions.size());
                for (int i = 0; i < Math.min(5, instructions.size()); i++) {
                    System.out.println("  [" + i + "]: " + instructions.get(i).ToJSONString());
                }
            }
        } else if (result.value() != null) {
            System.out.println("Value: " + result.value());
        }

        if (result.error() != null) {
            System.out.println("Error: " + result.error().getMessage());
        }
    }

    @Test
    @DisplayName("librarian 'show' dispatch and full CBOR output")
    void librarianShowDispatch() {
        ActionResult result = librarian.dispatch("show", List.of());
        System.out.println("show dispatch success: " + result.success());
        System.out.println("show result value type: " +
                (result.value() != null ? result.value().getClass().getName() : "null"));

        if (result.error() != null) {
            System.out.println("show error: " + result.error().getMessage());
            result.error().printStackTrace();
        }

        // Simulate what WebSocketSessionHandler does
        Object value = result.value();
        View view = value instanceof View v ? v :
                value != null ? View.of(TextSurface.of(value.toString())) : View.empty();

        System.out.println("View root type: " +
                (view.root() != null ? view.root().getClass().getSimpleName() : "null"));

        RenderInstructionRecorder recorder = new RenderInstructionRecorder();
        view.root().render(recorder);
        CBORObject instructions = recorder.toCbor();
        System.out.println("Total instructions: " + instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            System.out.println("  [" + i + "]: " + instructions.get(i).ToJSONString());
        }

        // Now simulate the full wire format
        SessionMessage.DispatchResponse response =
                SessionMessage.DispatchResponse.success(1, view);
        byte[] encoded = response.encode();
        CBORObject tagged = CBORObject.DecodeFromBytes(encoded);
        CBORObject map = tagged.UntagOne();
        System.out.println("\nFull wire CBOR: " + map.ToJSONString());
    }
}
