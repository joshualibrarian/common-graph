# Chess 3D Models

## Sources & Licenses

**Geometry**: [clarkerubber/Staunton-Pieces](https://github.com/clarkerubber/Staunton-Pieces) — MIT License
Classic Staunton chess piece STL models.

**Textures**: [ambientCG](https://ambientcg.com) — CC0 (Public Domain)
- Light wood: [Wood048](https://ambientcg.com/view?id=Wood048) (white pieces)
- Dark wood: [Wood039](https://ambientcg.com/view?id=Wood039) (black pieces)

## Generating GLB Files

The STL source files and PBR textures are included. Run the Blender script
to generate the 12 GLB files needed by the chess game.

### Prerequisites

- Blender 3.0+ (`apt install blender` on Debian)

### Steps

1. Open Blender
2. Switch to the **Scripting** tab
3. Click **Open** and select `convert_stl_to_glb.py`
4. Click **Run Script** (or press Alt+P)

The script will import each STL, UV-unwrap it, apply PBR wood materials,
and export 12 GLB files:

```
w_king.glb      b_king.glb
w_queen.glb     b_queen.glb
w_rook.glb      b_rook.glb
w_bishop.glb    b_bishop.glb
w_knight.glb    b_knight.glb
w_pawn.glb      b_pawn.glb
```

## File Layout

```
chess/models/
├── README.md                    ← this file
├── convert_stl_to_glb.py       ← Blender conversion script
├── King.STL                     ← source geometry (MIT)
├── Queen.STL
├── Rook.STL
├── Bishop.STL
├── Knight.STL
├── Pawn.STL
├── textures/
│   ├── light_wood/              ← Wood048 PBR maps (CC0)
│   │   ├── Wood048_1K-JPG_Color.jpg
│   │   ├── Wood048_1K-JPG_NormalGL.jpg
│   │   └── Wood048_1K-JPG_Roughness.jpg
│   └── dark_wood/               ← Wood039 PBR maps (CC0)
│       ├── Wood039_1K-JPG_Color.jpg
│       ├── Wood039_1K-JPG_NormalGL.jpg
│       └── Wood039_1K-JPG_Roughness.jpg
└── *.glb                        ← generated output (not checked in)
```
