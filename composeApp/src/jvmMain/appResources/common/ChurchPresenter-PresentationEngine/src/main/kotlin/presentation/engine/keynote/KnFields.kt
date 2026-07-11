package presentation.engine.keynote

/**
 * Vendored Keynote/iWork archive type ids and protobuf field numbers, extracted from
 * psobot/keynote-parser (protos/versions/14.4 + versions/v14_4/mapping.py). Each constant cites
 * its proto source. The dynamic reader ignores unknown fields, so version drift degrades
 * gracefully instead of crashing (per-slide fidelity gate in KeynoteDeckParser).
 */
internal object KnFields {

    // ── Type registry (mapping.py) ────────────────────────────────────────────
    const val TYPE_KN_DOCUMENT = 1              // KN.DocumentArchive
    const val TYPE_KN_SHOW = 2                  // KN.ShowArchive
    const val TYPE_KN_SLIDE_NODE = 4            // KN.SlideNodeArchive
    const val TYPE_KN_SLIDE = 5                 // KN.SlideArchive
    const val TYPE_KN_SLIDE_ALT = 6             // KN.SlideArchive (second registration)
    const val TYPE_KN_PLACEHOLDER = 7           // KN.PlaceholderArchive
    const val TYPE_KN_BUILD = 8                 // KN.BuildArchive
    const val TYPE_KN_SLIDE_STYLE = 9           // KN.SlideStyleArchive
    const val TYPE_KN_PLACEHOLDER_ALT = 12      // KN.PlaceholderArchive (second registration)
    const val TYPE_KN_NOTE = 15                 // KN.NoteArchive
    const val TYPE_KN_BUILD_CHUNK = 153         // KN.BuildChunkArchive
    const val TYPE_TSWP_STORAGE = 2001          // TSWP.StorageArchive
    const val TYPE_TSWP_STORAGE_ALT = 2005      // TSWP.StorageArchive (second registration)
    const val TYPE_TSWP_SHAPE_INFO = 2011       // TSWP.ShapeInfoArchive
    const val TYPE_TSWP_CHARACTER_STYLE = 2021  // TSWP.CharacterStyleArchive
    const val TYPE_TSWP_PARAGRAPH_STYLE = 2022  // TSWP.ParagraphStyleArchive
    const val TYPE_TSD_SHAPE = 3004             // TSD.ShapeArchive
    const val TYPE_TSD_IMAGE = 3005             // TSD.ImageArchive
    const val TYPE_TSD_MOVIE = 3007             // TSD.MovieArchive
    const val TYPE_TSD_GROUP = 3008             // TSD.GroupArchive
    const val TYPE_TSD_SHAPE_STYLE = 3015       // TSD.ShapeStyleArchive
    const val TYPE_TSP_PACKAGE_METADATA = 11006 // TSP.PackageMetadata

    // ── TSP basics (TSPMessages.proto) ────────────────────────────────────────
    const val REFERENCE_IDENTIFIER = 1          // TSP.Reference.identifier
    const val POINT_X = 1                       // TSP.Point.x (float)
    const val POINT_Y = 2                       // TSP.Point.y (float)
    const val SIZE_WIDTH = 1                    // TSP.Size.width (float)
    const val SIZE_HEIGHT = 2                   // TSP.Size.height (float)
    const val COLOR_MODEL = 1                   // TSP.Color.model (1=rgb)
    const val COLOR_R = 3                       // TSP.Color.r (float)
    const val COLOR_G = 4
    const val COLOR_B = 5
    const val COLOR_A = 6

    // ── TSP.PackageMetadata (TSPArchiveMessages.proto) ────────────────────────
    const val PACKAGE_METADATA_DATAS = 4        // repeated TSP.DataInfo
    const val DATA_INFO_IDENTIFIER = 1
    const val DATA_INFO_PREFERRED_FILE_NAME = 3
    const val DATA_INFO_FILE_NAME = 4

    // ── KN.DocumentArchive / ShowArchive / SlideTree (KNArchives.proto) ───────
    const val DOCUMENT_SHOW = 2                 // TSP.Reference show
    const val SHOW_SLIDE_TREE = 3               // KN.SlideTreeArchive (embedded)
    const val SHOW_SIZE = 4                     // TSP.Size
    const val SLIDE_TREE_SLIDES = 2             // repeated TSP.Reference (slide nodes, in order)

    // ── KN.SlideNodeArchive ───────────────────────────────────────────────────
    const val SLIDE_NODE_CHILDREN = 1           // repeated TSP.Reference
    const val SLIDE_NODE_SLIDE = 2              // TSP.Reference → KN.SlideArchive
    const val SLIDE_NODE_IS_SKIPPED = 4         // bool

    // ── KN.SlideArchive ───────────────────────────────────────────────────────
    const val SLIDE_STYLE = 1                   // TSP.Reference → KN.SlideStyleArchive
    const val SLIDE_BUILDS = 2                  // repeated TSP.Reference → KN.BuildArchive
    const val SLIDE_TRANSITION = 4              // KN.TransitionArchive (embedded)
    const val SLIDE_TITLE_PLACEHOLDER = 5       // TSP.Reference
    const val SLIDE_BODY_PLACEHOLDER = 6        // TSP.Reference
    const val SLIDE_OWNED_DRAWABLES = 7         // repeated TSP.Reference
    const val SLIDE_TEMPLATE_SLIDE = 17         // TSP.Reference → KN.SlideArchive (master chain)
    const val SLIDE_SLIDE_NUMBER_PLACEHOLDER = 20
    const val SLIDE_NOTE = 27                   // TSP.Reference → KN.NoteArchive
    const val SLIDE_OBJECT_PLACEHOLDER = 30
    const val SLIDE_DRAWABLES_Z_ORDER = 42      // repeated TSP.Reference (authoritative z-order)
    const val SLIDE_BUILD_CHUNKS = 43           // repeated TSP.Reference → KN.BuildChunkArchive

    // ── KN.SlideStyleArchive → SlideStylePropertiesArchive ────────────────────
    const val SLIDE_STYLE_PROPERTIES = 11       // KN.SlideStylePropertiesArchive
    const val SLIDE_STYLE_PROPS_FILL = 1        // TSD.FillArchive

    // ── KN.NoteArchive ────────────────────────────────────────────────────────
    const val NOTE_CONTAINED_STORAGE = 1        // TSP.Reference → TSWP.StorageArchive

    // ── KN.BuildArchive / BuildChunkArchive / attributes ──────────────────────
    const val BUILD_DRAWABLE = 1                // TSP.Reference
    const val BUILD_DELIVERY = 2                // string
    const val BUILD_ATTRIBUTES = 4              // KN.BuildAttributesArchive (embedded)
    const val BUILD_ATTRS_ANIMATION = 18        // KN.AnimationAttributesArchive
    const val ANIM_ATTRS_TYPE = 1               // string animation_type
    const val ANIM_ATTRS_EFFECT = 2             // string effect ("apple:build-effect:…")
    const val ANIM_ATTRS_DURATION = 3           // double
    const val ANIM_ATTRS_DIRECTION = 4          // uint32
    const val ANIM_ATTRS_DELAY = 5              // double
    const val ANIM_ATTRS_IS_AUTOMATIC = 6       // bool
    const val BUILD_CHUNK_BUILD = 1             // TSP.Reference
    const val BUILD_CHUNK_DELAY = 3             // double
    const val BUILD_CHUNK_DURATION = 4          // double
    const val BUILD_CHUNK_AUTOMATIC = 5         // bool

    // ── KN.TransitionArchive ──────────────────────────────────────────────────
    const val TRANSITION_ATTRIBUTES = 2         // KN.TransitionAttributesArchive
    const val TRANSITION_ATTRS_ANIMATION = 8    // KN.AnimationAttributesArchive

    // ── TSD drawables (TSDArchives.proto) ─────────────────────────────────────
    const val DRAWABLE_GEOMETRY = 1             // TSD.GeometryArchive
    const val GEOMETRY_POSITION = 1             // TSP.Point
    const val GEOMETRY_SIZE = 2                 // TSP.Size
    const val GEOMETRY_FLAGS = 3                // uint32 (bit0 hflip, bit1 vflip)
    const val GEOMETRY_ANGLE = 4                // float

    const val SHAPE_SUPER = 1                   // TSD.DrawableArchive
    const val SHAPE_STYLE = 2                   // TSP.Reference → TSD.ShapeStyleArchive
    const val SHAPE_PATHSOURCE = 3              // TSD.PathSourceArchive
    const val PATHSOURCE_POINT = 3              // TSD.PointPathSourceArchive
    const val PATHSOURCE_SCALAR = 4             // TSD.ScalarPathSourceArchive
    const val PATHSOURCE_BEZIER = 5             // TSD.BezierPathSourceArchive
    const val PATHSOURCE_EDITABLE_BEZIER = 8    // TSD.EditableBezierPathSourceArchive
    const val SCALAR_PATH_TYPE = 1              // 0 = rounded rectangle
    const val SCALAR_PATH_SCALAR = 2            // float (corner radius factor)
    const val SCALAR_PATH_NATURAL_SIZE = 3      // TSP.Size

    const val IMAGE_SUPER = 1                   // TSD.DrawableArchive
    const val IMAGE_DATA = 11                   // TSP.DataReference
    const val IMAGE_MASK = 5                    // TSP.Reference (set → gate)
    const val DATA_REFERENCE_IDENTIFIER = 1     // TSP.DataReference.identifier

    // ── TSD.MovieArchive — validated via dumpKeynote against RandomPresentation.key: field 1's
    // submessage yields the correct drawable geometry (position/size matched the dumped drawable
    // line), field 14's reference resolved to data id 9137 (= Data/IMG_3840-9137.mov, the deck's
    // actual movie asset), field 15's reference resolved to data id 9138 (= Data/posterImage-9138
    // .png, the deck's actual poster). Several other scalar fields are present (autoplay/loop/
    // volume-shaped booleans and floats at 3/4/5/7/9/13/18/23/24/28) but with no corroborating
    // evidence for which is which — left unmapped rather than guessed; playback start is instead
    // driven by the layer's build-derived visibility (see KeynoteBuildMapper/PresentationPlayer).
    const val MOVIE_SUPER = 1                   // TSD.DrawableArchive
    const val MOVIE_DATA = 14                   // TSP.DataReference → the .mov asset
    const val MOVIE_POSTER = 15                 // TSP.DataReference → poster image

    const val GROUP_SUPER = 1                   // TSD.DrawableArchive
    const val GROUP_CHILDREN = 2                // repeated TSP.Reference

    // ── TSD.ShapeStyleArchive ─────────────────────────────────────────────────
    const val SHAPE_STYLE_PROPERTIES = 11       // TSD.ShapeStylePropertiesArchive
    const val SHAPE_PROPS_FILL = 1              // TSD.FillArchive
    const val SHAPE_PROPS_STROKE = 2            // TSD.StrokeArchive
    const val SHAPE_PROPS_OPACITY = 3           // float
    const val FILL_COLOR = 1                    // TSP.Color
    const val FILL_GRADIENT = 2                 // TSD.GradientArchive
    const val FILL_IMAGE = 3                    // TSD.ImageFillArchive
    const val GRADIENT_STOPS = 1                // repeated TSD.GradientArchive.GradientStop
    const val GRADIENT_STOP_COLOR = 1           // TSP.Color
    const val IMAGE_FILL_DATA = 6               // TSP.DataReference imagedata
    const val STROKE_COLOR = 1                  // TSP.Color
    const val STROKE_WIDTH = 2                  // float

    // ── TSWP (TSWPArchives.proto) ─────────────────────────────────────────────
    const val SHAPE_INFO_SUPER = 1              // TSD.ShapeArchive
    const val SHAPE_INFO_OWNED_STORAGE = 4      // TSP.Reference → TSWP.StorageArchive
    const val PLACEHOLDER_SUPER = 1             // TSWP.ShapeInfoArchive
    const val PLACEHOLDER_KIND = 2              // enum (2=title, 3=body)
    const val STORAGE_TEXT = 3                  // repeated string
    const val STORAGE_TABLE_PARA_STYLE = 5      // TSWP.ObjectAttributeTable
    const val STORAGE_TABLE_CHAR_STYLE = 8      // TSWP.ObjectAttributeTable
    const val ATTR_TABLE_ENTRIES = 1            // repeated ObjectAttribute
    const val ATTR_ENTRY_CHAR_INDEX = 1         // uint32
    const val ATTR_ENTRY_OBJECT = 2             // TSP.Reference
    const val CHARACTER_STYLE_PROPERTIES = 11   // TSWP.CharacterStylePropertiesArchive
    const val PARAGRAPH_STYLE_CHAR_PROPERTIES = 11
    const val PARAGRAPH_STYLE_PARA_PROPERTIES = 12
    const val CHAR_PROPS_BOLD = 1
    const val CHAR_PROPS_ITALIC = 2
    const val CHAR_PROPS_FONT_SIZE = 3          // float
    const val CHAR_PROPS_FONT_NAME = 5          // string
    const val CHAR_PROPS_FONT_COLOR = 7         // TSP.Color
    const val PARA_PROPS_ALIGNMENT = 1          // 0=left,1=right,2=center,3=justify
    const val STYLE_SUPER = 1                   // TSS.StyleArchive (in all style archives)
    const val TSS_STYLE_PARENT = 3              // TSS.StyleArchive.parent (TSP.Reference)

    // ── TSP.Path (TSPMessages.proto) ──────────────────────────────────────────
    const val PATH_ELEMENTS = 1                 // repeated TSP.Path.Element
    const val PATH_ELEMENT_TYPE = 1             // 1 moveTo, 2 lineTo, 3 quadCurveTo, 4 curveTo, 5 close
    const val PATH_ELEMENT_POINTS = 2           // repeated TSP.Point
    const val BEZIER_PATH_NATURAL_SIZE = 2      // TSD.BezierPathSourceArchive.naturalSize
    const val BEZIER_PATH_PATH = 3              // TSD.BezierPathSourceArchive.path
}
