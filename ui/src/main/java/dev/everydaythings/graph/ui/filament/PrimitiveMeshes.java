package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.Box;
import dev.everydaythings.filament.Engine;
import dev.everydaythings.filament.IndexBuffer;
import dev.everydaythings.filament.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Generates VertexBuffer + IndexBuffer for primitive shapes.
 *
 * <p>Filament has no built-in primitive shapes — you provide raw vertex/index
 * buffers. This utility generates the mesh data for common primitives.
 *
 * <p>Two vertex formats:
 * <ul>
 *   <li><b>Colored</b>: POSITION (float3) + COLOR (ubyte4) — for 3D primitives</li>
 *   <li><b>Textured</b>: POSITION (float3) + UV0 (float2) — for texture-mapped quads</li>
 * </ul>
 */
public class PrimitiveMeshes {

    /**
     * A renderable mesh with vertex and index buffers.
     */
    public record Mesh(VertexBuffer vertexBuffer, IndexBuffer indexBuffer,
                       int indexCount, Box boundingBox) {
        public void destroy(Engine engine) {
            engine.destroyVertexBuffer(vertexBuffer);
            engine.destroyIndexBuffer(indexBuffer);
        }
    }

    /**
     * Create a textured quad in the XY plane, centered at origin.
     * Uses POSITION (float3) + UV0 (float2).
     *
     * @param engine Filament engine
     * @param w      quad width
     * @param h      quad height
     */
    public static Mesh createQuad(Engine engine, float w, float h) {
        float hw = w / 2f, hh = h / 2f;

        // 4 vertices: position (float3) + uv (float2)
        float[] positions = {
                -hw, -hh, 0,  // bottom-left
                 hw, -hh, 0,  // bottom-right
                 hw,  hh, 0,  // top-right
                -hw,  hh, 0,  // top-left
        };
        float[] uvs = {
                0, 0,  // bottom-left
                1, 0,  // bottom-right
                1, 1,  // top-right
                0, 1,  // top-left
        };
        short[] indices = {0, 1, 2, 0, 2, 3};

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer uvBuf = allocateFloat(uvs);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(4)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.UV0, 1,
                        VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, uvBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, 6, new Box(0, 0, 0, hw, hh, 0.01f));
    }

    /**
     * Create a colored box centered at origin.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * @param engine Filament engine
     * @param w      width (X)
     * @param h      height (Y)
     * @param d      depth (Z)
     * @param color  ABGR color (e.g. 0xFF0000FF = red)
     */
    public static Mesh createBox(Engine engine, float w, float h, float d, int color) {
        float hw = w / 2f, hh = h / 2f, hd = d / 2f;

        // 24 vertices (4 per face for flat normals)
        float[] positions = {
                // Front face (+Z)
                -hw, -hh,  hd,   hw, -hh,  hd,   hw,  hh,  hd,  -hw,  hh,  hd,
                // Back face (-Z)
                 hw, -hh, -hd,  -hw, -hh, -hd,  -hw,  hh, -hd,   hw,  hh, -hd,
                // Top face (+Y)
                -hw,  hh,  hd,   hw,  hh,  hd,   hw,  hh, -hd,  -hw,  hh, -hd,
                // Bottom face (-Y)
                -hw, -hh, -hd,   hw, -hh, -hd,   hw, -hh,  hd,  -hw, -hh,  hd,
                // Right face (+X)
                 hw, -hh,  hd,   hw, -hh, -hd,   hw,  hh, -hd,   hw,  hh,  hd,
                // Left face (-X)
                -hw, -hh, -hd,  -hw, -hh,  hd,  -hw,  hh,  hd,  -hw,  hh, -hd,
        };

        // 36 indices (6 per face)
        short[] indices = new short[36];
        for (int face = 0; face < 6; face++) {
            int vi = face * 4;
            int ii = face * 6;
            indices[ii    ] = (short) vi;
            indices[ii + 1] = (short) (vi + 1);
            indices[ii + 2] = (short) (vi + 2);
            indices[ii + 3] = (short) vi;
            indices[ii + 4] = (short) (vi + 2);
            indices[ii + 5] = (short) (vi + 3);
        }

        // All vertices same color
        int[] colors = new int[24];
        Arrays.fill(colors, color);

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(24)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(36)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, 36, new Box(0, 0, 0, hw, hh, hd));
    }

    /**
     * Create a colored UV sphere centered at origin.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * @param engine   Filament engine
     * @param radius   sphere radius
     * @param segments number of latitude/longitude segments
     * @param color    ABGR color
     */
    public static Mesh createSphere(Engine engine, float radius, int segments, int color) {
        int rings = segments;
        int sectors = segments;
        int vertexCount = (rings + 1) * (sectors + 1);
        int indexCount = rings * sectors * 6;

        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        short[] indices = new short[indexCount];

        // Generate vertices
        int vi = 0;
        for (int r = 0; r <= rings; r++) {
            float phi = (float) (Math.PI * r / rings);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            for (int s = 0; s <= sectors; s++) {
                float theta = (float) (2 * Math.PI * s / sectors);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                positions[vi * 3    ] = radius * cosTheta * sinPhi;
                positions[vi * 3 + 1] = radius * cosPhi;
                positions[vi * 3 + 2] = radius * sinTheta * sinPhi;
                colors[vi] = color;
                vi++;
            }
        }

        // Generate indices
        int ii = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                int cur = r * (sectors + 1) + s;
                int next = cur + sectors + 1;

                indices[ii++] = (short) cur;
                indices[ii++] = (short) next;
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) next;
                indices[ii++] = (short) (next + 1);
            }
        }

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, indexCount, new Box(0, 0, 0, radius, radius, radius));
    }

    /**
     * Create a colored cylinder aligned along the Y axis, centered at origin.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * <p>The cylinder barrel is a ring of quads. Top and bottom caps are
     * triangle fans from a center vertex.
     *
     * @param engine   Filament engine
     * @param radius   cylinder radius (XZ)
     * @param height   cylinder height (Y)
     * @param segments number of sectors around the Y axis
     * @param color    ABGR color
     */
    public static Mesh createCylinder(Engine engine, float radius, float height,
                                       int segments, int color) {
        float hh = height / 2f;

        // Vertex layout:
        //   Barrel: 2 rings of (segments+1) vertices = 2*(segments+1)
        //   Top cap: 1 center + (segments+1) rim = segments+2
        //   Bottom cap: 1 center + (segments+1) rim = segments+2
        int barrelVerts = 2 * (segments + 1);
        int capVerts = segments + 2;
        int vertexCount = barrelVerts + 2 * capVerts;

        // Index layout:
        //   Barrel: segments * 6
        //   Each cap: segments * 3
        int barrelIndices = segments * 6;
        int capIndices = segments * 3;
        int indexCount = barrelIndices + 2 * capIndices;

        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        short[] indices = new short[indexCount];

        Arrays.fill(colors, color);

        int vi = 0;
        int ii = 0;

        // ===== Barrel =====
        int barrelBase = vi;
        for (int ring = 0; ring < 2; ring++) {
            float y = (ring == 0) ? -hh : hh;
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                positions[vi * 3    ] = radius * (float) Math.cos(theta);
                positions[vi * 3 + 1] = y;
                positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
                vi++;
            }
        }

        // Barrel indices: quads between bottom ring and top ring
        for (int s = 0; s < segments; s++) {
            int bl = barrelBase + s;
            int br = barrelBase + s + 1;
            int tl = barrelBase + (segments + 1) + s;
            int tr = barrelBase + (segments + 1) + s + 1;

            indices[ii++] = (short) bl;
            indices[ii++] = (short) br;
            indices[ii++] = (short) tr;
            indices[ii++] = (short) bl;
            indices[ii++] = (short) tr;
            indices[ii++] = (short) tl;
        }

        // ===== Top Cap =====
        int topCenter = vi;
        positions[vi * 3    ] = 0;
        positions[vi * 3 + 1] = hh;
        positions[vi * 3 + 2] = 0;
        vi++;

        int topRimBase = vi;
        for (int s = 0; s <= segments; s++) {
            float theta = (float) (2 * Math.PI * s / segments);
            positions[vi * 3    ] = radius * (float) Math.cos(theta);
            positions[vi * 3 + 1] = hh;
            positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
            vi++;
        }

        for (int s = 0; s < segments; s++) {
            indices[ii++] = (short) topCenter;
            indices[ii++] = (short) (topRimBase + s);
            indices[ii++] = (short) (topRimBase + s + 1);
        }

        // ===== Bottom Cap =====
        int botCenter = vi;
        positions[vi * 3    ] = 0;
        positions[vi * 3 + 1] = -hh;
        positions[vi * 3 + 2] = 0;
        vi++;

        int botRimBase = vi;
        for (int s = 0; s <= segments; s++) {
            float theta = (float) (2 * Math.PI * s / segments);
            positions[vi * 3    ] = radius * (float) Math.cos(theta);
            positions[vi * 3 + 1] = -hh;
            positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
            vi++;
        }

        for (int s = 0; s < segments; s++) {
            indices[ii++] = (short) botCenter;
            indices[ii++] = (short) (botRimBase + s + 1);
            indices[ii++] = (short) (botRimBase + s);
        }

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, indexCount, new Box(0, 0, 0, radius, hh, radius));
    }

    /**
     * Create a colored flat quad in the XZ plane, centered at origin.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * @param engine Filament engine
     * @param w      width (X)
     * @param d      depth (Z)
     * @param color  ABGR color
     */
    public static Mesh createPlane(Engine engine, float w, float d, int color) {
        float hw = w / 2f, hd = d / 2f;

        float[] positions = {
                -hw, 0, -hd,
                 hw, 0, -hd,
                 hw, 0,  hd,
                -hw, 0,  hd,
        };
        short[] indices = {0, 2, 1, 0, 3, 2};

        int[] colors = new int[4];
        Arrays.fill(colors, color);

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(4)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, 6, new Box(0, 0, 0, hw, 0.01f, hd));
    }

    /**
     * Create a colored cone aligned along the Y axis, centered at origin.
     * Apex at +Y, base circle at -Y.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * @param engine   Filament engine
     * @param radius   base radius (XZ)
     * @param height   cone height (Y)
     * @param segments number of sectors around the Y axis
     * @param color    ABGR color
     */
    public static Mesh createCone(Engine engine, float radius, float height,
                                   int segments, int color) {
        float hh = height / 2f;

        // Vertices: apex + base rim (segments+1) + base center + base rim (segments+1)
        int vertexCount = 1 + (segments + 1) + 1 + (segments + 1);
        // Indices: side triangles (segments*3) + base triangles (segments*3)
        int indexCount = segments * 3 + segments * 3;

        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        short[] indices = new short[indexCount];
        Arrays.fill(colors, color);

        int vi = 0, ii = 0;

        // Apex
        int apex = vi;
        positions[vi * 3    ] = 0;
        positions[vi * 3 + 1] = hh;
        positions[vi * 3 + 2] = 0;
        vi++;

        // Side rim (at base)
        int sideRimBase = vi;
        for (int s = 0; s <= segments; s++) {
            float theta = (float) (2 * Math.PI * s / segments);
            positions[vi * 3    ] = radius * (float) Math.cos(theta);
            positions[vi * 3 + 1] = -hh;
            positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
            vi++;
        }

        // Side triangles
        for (int s = 0; s < segments; s++) {
            indices[ii++] = (short) apex;
            indices[ii++] = (short) (sideRimBase + s);
            indices[ii++] = (short) (sideRimBase + s + 1);
        }

        // Base center
        int baseCenter = vi;
        positions[vi * 3    ] = 0;
        positions[vi * 3 + 1] = -hh;
        positions[vi * 3 + 2] = 0;
        vi++;

        // Base rim
        int baseRimBase = vi;
        for (int s = 0; s <= segments; s++) {
            float theta = (float) (2 * Math.PI * s / segments);
            positions[vi * 3    ] = radius * (float) Math.cos(theta);
            positions[vi * 3 + 1] = -hh;
            positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
            vi++;
        }

        // Base triangles (wound opposite to look outward from below)
        for (int s = 0; s < segments; s++) {
            indices[ii++] = (short) baseCenter;
            indices[ii++] = (short) (baseRimBase + s + 1);
            indices[ii++] = (short) (baseRimBase + s);
        }

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        return new Mesh(vb, ib, indexCount, new Box(0, 0, 0, radius, hh, radius));
    }

    /**
     * Create a colored capsule (cylinder + hemisphere caps) aligned along Y, centered at origin.
     * Uses POSITION (float3) + COLOR (ubyte4).
     *
     * @param engine   Filament engine
     * @param radius   capsule radius
     * @param height   total capsule height (including caps)
     * @param segments number of sectors/rings
     * @param color    ABGR color
     */
    public static Mesh createCapsule(Engine engine, float radius, float height,
                                      int segments, int color) {
        // Capsule = cylinder body + two hemisphere caps
        // Cylinder height = total height - 2*radius
        float cylH = Math.max(0, height - 2 * radius);
        float hCylH = cylH / 2f;
        int rings = segments / 2; // hemisphere rings
        if (rings < 2) rings = 2;

        // Count vertices: top hemisphere + cylinder barrel + bottom hemisphere
        // Each hemisphere: (rings+1) * (segments+1)
        // Barrel: 2 * (segments+1)
        int hemiVerts = (rings + 1) * (segments + 1);
        int barrelVerts = 2 * (segments + 1);
        int vertexCount = 2 * hemiVerts + barrelVerts;

        // Count indices: hemispheres + barrel
        int hemiIndices = rings * segments * 6;
        int barrelIndices = segments * 6;
        int indexCount = 2 * hemiIndices + barrelIndices;

        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        short[] indices = new short[indexCount];
        Arrays.fill(colors, color);

        int vi = 0, ii = 0;

        // Top hemisphere (phi from 0 to PI/2)
        int topBase = vi;
        for (int r = 0; r <= rings; r++) {
            float phi = (float) (Math.PI / 2.0 * r / rings);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                positions[vi * 3    ] = radius * (float) Math.cos(theta) * sinPhi;
                positions[vi * 3 + 1] = hCylH + radius * cosPhi;
                positions[vi * 3 + 2] = radius * (float) Math.sin(theta) * sinPhi;
                vi++;
            }
        }
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int cur = topBase + r * (segments + 1) + s;
                int next = cur + segments + 1;
                indices[ii++] = (short) cur;
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) next;
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) (next + 1);
                indices[ii++] = (short) next;
            }
        }

        // Barrel
        int barrelBase = vi;
        for (int ring = 0; ring < 2; ring++) {
            float y = (ring == 0) ? hCylH : -hCylH;
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                positions[vi * 3    ] = radius * (float) Math.cos(theta);
                positions[vi * 3 + 1] = y;
                positions[vi * 3 + 2] = radius * (float) Math.sin(theta);
                vi++;
            }
        }
        for (int s = 0; s < segments; s++) {
            int bl = barrelBase + s;
            int br = barrelBase + s + 1;
            int tl = barrelBase + (segments + 1) + s;
            int tr = barrelBase + (segments + 1) + s + 1;
            indices[ii++] = (short) bl;
            indices[ii++] = (short) br;
            indices[ii++] = (short) tr;
            indices[ii++] = (short) bl;
            indices[ii++] = (short) tr;
            indices[ii++] = (short) tl;
        }

        // Bottom hemisphere (phi from PI/2 to PI)
        int botBase = vi;
        for (int r = 0; r <= rings; r++) {
            float phi = (float) (Math.PI / 2.0 + Math.PI / 2.0 * r / rings);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                positions[vi * 3    ] = radius * (float) Math.cos(theta) * sinPhi;
                positions[vi * 3 + 1] = -hCylH + radius * cosPhi;
                positions[vi * 3 + 2] = radius * (float) Math.sin(theta) * sinPhi;
                vi++;
            }
        }
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int cur = botBase + r * (segments + 1) + s;
                int next = cur + segments + 1;
                indices[ii++] = (short) cur;
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) next;
                indices[ii++] = (short) (cur + 1);
                indices[ii++] = (short) (next + 1);
                indices[ii++] = (short) next;
            }
        }

        ByteBuffer posBuf = allocateFloat(positions);
        ByteBuffer colorBuf = allocateInt(colors);
        ByteBuffer idxBuf = allocateShort(indices);

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        float totalHH = height / 2f;
        return new Mesh(vb, ib, indexCount, new Box(0, 0, 0, radius, totalHH, radius));
    }

    // ==================== Buffer Helpers ====================

    private static ByteBuffer allocateFloat(float[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder());
        for (float f : data) buf.putFloat(f);
        buf.flip();
        return buf;
    }

    private static ByteBuffer allocateInt(int[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder());
        for (int i : data) buf.putInt(i);
        buf.flip();
        return buf;
    }

    private static ByteBuffer allocateShort(short[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 2)
                .order(ByteOrder.nativeOrder());
        for (short s : data) buf.putShort(s);
        buf.flip();
        return buf;
    }
}
