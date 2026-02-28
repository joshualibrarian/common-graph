"""
Blender Python script to convert Staunton STL chess pieces into GLB files
with PBR wood materials (light wood for white, dark wood for black).

Source geometry: clarkerubber/Staunton-Pieces (MIT license)
Textures: ambientCG Wood048 (light) + Wood039 (dark) (CC0)

Usage:
  1. Open Blender (3.0+)
  2. Switch to the Scripting tab
  3. Open this file (or paste it)
  4. Click "Run Script" (or Alt+P)

The script will:
  - Import each STL piece
  - UV unwrap it
  - Create PBR wood materials from texture maps
  - Export white and black versions as separate GLB files
  - Clean up between pieces

Output: 12 GLB files in OUTPUT_DIR (w_king.glb, b_king.glb, etc.)
"""

import bpy
import os
import math

# ============================================================
# CONFIGURATION
# ============================================================
BASE_DIR = os.path.expanduser(
    "~/projects/common-graph/games/src/main/resources/chess/models"
)
OUTPUT_DIR = BASE_DIR
TEXTURE_DIR = os.path.join(BASE_DIR, "textures")

# STL files from clarkerubber/Staunton-Pieces
PIECES = ["King", "Queen", "Rook", "Bishop", "Knight", "Pawn"]

    # Models are exported at 1:1 STL scale (millimeters).
# No normalization — pieces retain their real Staunton proportions.
# The renderer auto-scales from the model's bounding box at paint time.

# Texture paths (ambientCG CC0)
LIGHT_WOOD = {
    "color":        os.path.join(TEXTURE_DIR, "light_wood", "Wood048_1K-JPG_Color.jpg"),
    "normal":       os.path.join(TEXTURE_DIR, "light_wood", "Wood048_1K-JPG_NormalGL.jpg"),
    "roughness":    os.path.join(TEXTURE_DIR, "light_wood", "Wood048_1K-JPG_Roughness.jpg"),
    "displacement": os.path.join(TEXTURE_DIR, "light_wood", "Wood048_1K-JPG_Displacement.jpg"),
}

DARK_WOOD = {
    "color":        os.path.join(TEXTURE_DIR, "dark_wood", "Wood039_1K-JPG_Color.jpg"),
    "normal":       os.path.join(TEXTURE_DIR, "dark_wood", "Wood039_1K-JPG_NormalGL.jpg"),
    "roughness":    os.path.join(TEXTURE_DIR, "dark_wood", "Wood039_1K-JPG_Roughness.jpg"),
    "displacement": os.path.join(TEXTURE_DIR, "dark_wood", "Wood039_1K-JPG_Displacement.jpg"),
}

# Fallback solid colors if textures are missing
LIGHT_COLOR = (0.76, 0.60, 0.42, 1.0)   # warm maple
DARK_COLOR  = (0.20, 0.12, 0.07, 1.0)   # dark walnut


def clear_scene():
    """Remove all objects from the scene."""
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    # Clean orphan data
    for block in bpy.data.meshes:
        if block.users == 0:
            bpy.data.meshes.remove(block)
    for block in bpy.data.materials:
        if block.users == 0:
            bpy.data.materials.remove(block)
    for block in bpy.data.images:
        if block.users == 0:
            bpy.data.images.remove(block)


def load_texture(filepath):
    """Load an image texture, return the image datablock or None."""
    if os.path.exists(filepath):
        return bpy.data.images.load(filepath, check_existing=True)
    print(f"  Warning: texture not found: {filepath}")
    return None


def create_wood_material(name, textures, fallback_color):
    """Create a PBR wood material with texture maps or solid color fallback."""
    mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links

    # Clear default nodes
    for node in nodes:
        nodes.remove(node)

    # Output node
    output = nodes.new('ShaderNodeOutputMaterial')
    output.location = (600, 0)

    # Principled BSDF
    bsdf = nodes.new('ShaderNodeBsdfPrincipled')
    bsdf.location = (200, 0)
    links.new(bsdf.outputs['BSDF'], output.inputs['Surface'])

    # Try to load textures
    color_img = load_texture(textures["color"])
    normal_img = load_texture(textures["normal"])
    roughness_img = load_texture(textures["roughness"])

    if color_img:
        # Base Color texture
        color_tex = nodes.new('ShaderNodeTexImage')
        color_tex.image = color_img
        color_tex.location = (-400, 200)
        links.new(color_tex.outputs['Color'], bsdf.inputs['Base Color'])

        # Shared texture coordinate + mapping
        tex_coord = nodes.new('ShaderNodeTexCoord')
        tex_coord.location = (-800, 0)
        mapping = nodes.new('ShaderNodeMapping')
        mapping.location = (-600, 0)
        links.new(tex_coord.outputs['UV'], mapping.inputs['Vector'])
        links.new(mapping.outputs['Vector'], color_tex.inputs['Vector'])

        if normal_img:
            normal_tex = nodes.new('ShaderNodeTexImage')
            normal_tex.image = normal_img
            normal_tex.image.colorspace_settings.name = 'Non-Color'
            normal_tex.location = (-400, -100)
            links.new(mapping.outputs['Vector'], normal_tex.inputs['Vector'])

            normal_map = nodes.new('ShaderNodeNormalMap')
            normal_map.location = (0, -100)
            normal_map.inputs['Strength'].default_value = 1.0
            links.new(normal_tex.outputs['Color'], normal_map.inputs['Color'])
            links.new(normal_map.outputs['Normal'], bsdf.inputs['Normal'])

        if roughness_img:
            rough_tex = nodes.new('ShaderNodeTexImage')
            rough_tex.image = roughness_img
            rough_tex.image.colorspace_settings.name = 'Non-Color'
            rough_tex.location = (-400, -400)
            links.new(mapping.outputs['Vector'], rough_tex.inputs['Vector'])
            links.new(rough_tex.outputs['Color'], bsdf.inputs['Roughness'])
        else:
            bsdf.inputs['Roughness'].default_value = 0.4

        print(f"  Created textured material: {name}")
    else:
        # Solid color fallback
        bsdf.inputs['Base Color'].default_value = fallback_color
        bsdf.inputs['Roughness'].default_value = 0.35
        bsdf.inputs['Specular IOR Level'].default_value = 0.5
        print(f"  Created solid color material: {name} (textures not found)")

    return mat


def import_and_export_piece(piece_name, material, prefix, output_dir):
    """Import an STL, apply material, UV unwrap, and export as GLB."""
    stl_path = os.path.join(BASE_DIR, f"{piece_name}.STL")
    if not os.path.exists(stl_path):
        print(f"  ERROR: STL not found: {stl_path}")
        return False

    # Import STL
    bpy.ops.wm.stl_import(filepath=stl_path)

    # Get the imported object (should be the only selected object)
    obj = bpy.context.selected_objects[0] if bpy.context.selected_objects else None
    if obj is None:
        print(f"  ERROR: No object imported from {stl_path}")
        return False

    # Set as active
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)

    # Center origin at geometry bounds, then move to world origin
    bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
    obj.location = (0, 0, 0)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    # The STL pieces have their height along Blender Y. Rotate -90° around X
    # so height aligns with Blender +Z (up), base at low Z. export_yup=True
    # then maps Blender Z → glTF Y, producing Y-up models with base at Y=0.
    bpy.ops.transform.rotate(value=math.radians(-90), orient_axis='X')
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    # Move origin to base of piece (minimum Z) so placement means "base sits here".
    # No scaling — models stay in their original STL dimensions (millimeters).
    min_z = min(v.co.z for v in obj.data.vertices)
    for v in obj.data.vertices:
        v.co.z -= min_z

    # UV unwrap (Smart UV Project works well for organic shapes)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.02)
    bpy.ops.object.mode_set(mode='OBJECT')

    # Shade smooth
    bpy.ops.object.shade_smooth()

    # Apply material
    if obj.data.materials:
        obj.data.materials[0] = material
    else:
        obj.data.materials.append(material)

    # Export as GLB
    output_name = f"{prefix}_{piece_name.lower()}.glb"
    output_path = os.path.join(output_dir, output_name)

    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    bpy.ops.export_scene.gltf(
        filepath=output_path,
        export_format='GLB',
        use_selection=True,
        export_apply=True,
        export_yup=True,
    )
    print(f"  Exported: {output_name}")

    # Clean up for next piece
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)

    return True


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("\n" + "=" * 60)
    print("  Staunton Chess Pieces — STL → GLB Converter")
    print("=" * 60)

    # Verify STL files exist
    missing_stl = [p for p in PIECES if not os.path.exists(os.path.join(BASE_DIR, f"{p}.STL"))]
    if missing_stl:
        print(f"\n  ERROR: Missing STL files: {missing_stl}")
        print(f"  Expected in: {BASE_DIR}")
        return

    print(f"\n  STL source:  {BASE_DIR}")
    print(f"  Textures:    {TEXTURE_DIR}")
    print(f"  Output:      {OUTPUT_DIR}")

    # Create materials
    print("\nCreating materials...")
    white_mat = create_wood_material("WhiteWood", LIGHT_WOOD, LIGHT_COLOR)
    black_mat = create_wood_material("BlackWood", DARK_WOOD, DARK_COLOR)

    # Process each piece
    print("\nConverting pieces...")
    success_count = 0
    for piece in PIECES:
        print(f"\n--- {piece} ---")
        clear_scene()

        # Re-create materials (they get cleaned up with orphan data)
        white_mat = create_wood_material("WhiteWood", LIGHT_WOOD, LIGHT_COLOR)
        black_mat = create_wood_material("BlackWood", DARK_WOOD, DARK_COLOR)

        # White version
        if import_and_export_piece(piece, white_mat, "w", OUTPUT_DIR):
            success_count += 1

        # Black version
        if import_and_export_piece(piece, black_mat, "b", OUTPUT_DIR):
            success_count += 1

    # Summary
    print("\n" + "=" * 60)
    print(f"  Done! Exported {success_count}/12 GLB files")
    print("=" * 60)

    # Verify outputs
    expected_files = [f"{c}_{p.lower()}.glb" for c in ("w", "b") for p in PIECES]
    missing = [f for f in expected_files if not os.path.exists(os.path.join(OUTPUT_DIR, f))]
    if missing:
        print(f"\n  WARNING — missing outputs: {missing}")
    else:
        print("\n  All 12 GLB files created successfully!")
        print(f"  Location: {OUTPUT_DIR}")

    # List output files
    print("\n  Files:")
    for f in sorted(expected_files):
        path = os.path.join(OUTPUT_DIR, f)
        if os.path.exists(path):
            size = os.path.getsize(path)
            print(f"    {f:20s}  ({size:,} bytes)")


if __name__ == "__main__":
    main()
