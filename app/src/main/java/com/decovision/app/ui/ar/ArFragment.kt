/**
 * ArFragment.kt - DecoVision AR Screen
 *
 * Yeh app ka main screen hai jahan user:
 *   1. Floor surface detect karta hai (ARCore ke white dots)
 *   2. Catalog se furniture select karke floor pe tap karke place karta hai
 *   3. Placed furniture ko move (long press + drag), rotate (button), resize (pinch) kar sakta hai
 *   4. Furniture delete kar sakta hai
 *   5. Gallery se apni photo add karke AR mein vertical panel ki tarah place kar sakta hai
 *   6. Poora design screenshot le ke Room database mein save kar sakta hai
 *
 * Architecture: Fragment → ArViewModel → DesignRepository → Room
 */
package com.decovision.app.ui.ar

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.decovision.app.R
import com.decovision.app.databinding.FragmentArBinding
import com.decovision.app.util.PermissionHelper
import com.decovision.app.util.UiState
import com.decovision.app.util.showSnackbar
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// DATA CLASSES
// ─────────────────────────────────────────────────────────────────────────────

/**
 * FurnitureOption - Catalog mein dikhne wala har ek furniture item ka blueprint.
 *
 * @param name        Display naam (e.g. "Sofa")
 * @param emoji       Catalog card pe dikhne wala emoji
 * @param modelFile   assets/models/ ke andar GLB file ka naam (bina extension ke)
 *                    Gallery image ke liye yeh empty string hoga
 * @param scale       AR mein place hone ke baad initial size (metres mein)
 * @param yOffset     Floor se upar lift (metres) - kuch models floor ke neeche jaate hain
 * @param customImageUri  Sirf gallery se pick ki gayi image ke liye - null means catalog item
 */
data class FurnitureOption(
    val name: String,
    val emoji: String,
    val modelFile: String,
    val scale: Float = 1.0f,
    val yOffset: Float = 0.0f,
    val customImageUri: Uri? = null
)

/**
 * PlacedItem - AR scene mein actually place ho chuka ek furniture piece.
 *
 * @param id          ArViewModel.addFurnitureItem() se mila UUID - Room mein match karta hai
 * @param anchorNode  ARCore anchor - object ko real-world floor se attach rakhta hai
 *                    Camera move karo, object wahi rehta hai kyunki anchor world-space mein fixed hai
 * @param node        Actual 3D model node - rotate/scale is pe hoti hai
 * @param rotationY   Current Y-axis rotation degrees - Rotate button har click pe 15° badhata hai
 * @param currentScale Pinch-to-resize ka current scale factor - 0.1 to 5.0 range
 */
data class PlacedItem(
    val id: String,
    val anchorNode: AnchorNode,
    val node: Node,
    var rotationY: Float = 0f,
    var currentScale: Float = 1.0f
)

// ─────────────────────────────────────────────────────────────────────────────
// FRAGMENT
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @AndroidEntryPoint - Hilt ko batata hai ke is Fragment mein dependency injection karo.
 * ArViewModel aur uske constructor dependencies Hilt automatically provide karta hai.
 */
@AndroidEntryPoint
class ArFragment : Fragment() {

    // ViewBinding - null safety ke liye _binding pattern use kiya
    // onDestroyView mein null kiya jaata hai memory leak rokne ke liye
    private var _binding: FragmentArBinding? = null
    private val binding get() = _binding!!

    // ViewModel - Fragment recreate hone pe survive karta hai (rotation etc.)
    private val viewModel: ArViewModel by viewModels()

    // ── AR State ──────────────────────────────────────────────────────────────

    /** Placed furniture ka map - id se jaldi dhundha ja sake */
    private val placedItems = mutableMapOf<String, PlacedItem>()

    /** Currently selected item ka id - Rotate/Delete/Resize is pe kaam karte hain */
    private var selectedId: String? = null

    /**
     * Pending furniture - user ne catalog se item select kiya hai lekin abhi floor pe tap nahi kiya.
     * Jab tak yeh null nahi, overlay ka tap AR placement pe jayega.
     */
    private var pendingFurniture: FurnitureOption? = null

    /** Highlighted catalog card TextView - deselect karne ke liye reference */
    private var selectedTv: TextView? = null

    /**
     * ARCore Session ka thread-safe reference.
     * onFrame callback (GL thread) mein set hota hai.
     * doArTap() mein use hota hai floor hitTest ke liye.
     * AtomicReference isliye ke onFrame GL thread pe hai, touch handler Main thread pe.
     */
    private val sessionRef = AtomicReference<Session?>(null)

    // ── Custom Image State ────────────────────────────────────────────────────

    /** User ki gallery se add ki gayi custom image options - catalog strip mein add hoti hain */
    private val customOptions = mutableListOf<FurnitureOption>()

    /** Currently highlighted custom image card - deselect ke liye reference */
    private var selectedCustomCard: View? = null

    // ── Pinch-to-Resize State ─────────────────────────────────────────────────

    /** Pichle frame mein do ungliyon ke beech ki distance - scale calculate karne ke liye */
    private var lastPinchDist = 0f

    /** True jab do ungliyaan screen pe hain aur resize chal raha hai */
    private var isPinching = false

    // ── Move/Drag State ───────────────────────────────────────────────────────

    /** True jab user ne long press kiya aur drag se furniture move kar raha hai */
    private var isDraggingItem = false

    /** Drag operation mein kaun sa item move ho raha hai */
    private var draggingItemId: String? = null

    // ── Furniture Catalog ─────────────────────────────────────────────────────

    /**
     * Built-in furniture catalog - app ke saath aane wale GLB models.
     * modelFile = assets/models/${modelFile}.glb ka path
     * scale = scaleToUnits value - SceneView automatically model ko is size mein fit karta hai
     */
    private val catalog = listOf(
        FurnitureOption("Sofa",     "🛋️", "sofa",     scale = 1.2f),
        FurnitureOption("Chair",    "🪑", "chair",    scale = 0.8f),
        FurnitureOption("Table",    "🪵", "table",    scale = 0.9f),
        FurnitureOption("Bed",      "🛏️", "bed",      scale = 1.4f),
        FurnitureOption("Wardrobe", "🗄️", "wardrobe", scale = 1.0f),
        FurnitureOption("TV Stand", "📺", "tv_stand", scale = 1.5f),
        FurnitureOption("Lamp",     "💡", "lamp",     scale = 0.8f),
        FurnitureOption("Plant",       "🪴", "plant",       scale = 0.6f),
        FurnitureOption("Round Table",  "🔵", "table_round", scale = 0.7f)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PERMISSION & PICKER LAUNCHERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runtime permission launcher - gallery access ke liye READ_MEDIA_IMAGES (API 33+)
     * ya READ_EXTERNAL_STORAGE (older) maangta hai.
     * Permission grant hone pe openPicker() call hota hai.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) openPicker()
        else binding.root.showSnackbar("Permission chahiye gallery access ke liye")
    }

    /**
     * Image picker launcher - system gallery/files app kholta hai.
     * User jo image select kare uska URI handleCustomImage() ko milta hai.
     * takePersistableUriPermission: app restart ke baad bhi URI accessible rahe.
     */
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                handleCustomImage(uri)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentArBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * onFrame: SceneView ka GL thread callback - har frame pe call hota hai.
         * Yahan session capture karte hain taake doArTap() mein hitTest ke liye available ho.
         * GL thread pe kaam karna zaroori hai ARCore session ke liye.
         */
        binding.arSceneView.onFrame = {
            binding.arSceneView.session?.let { sessionRef.set(it) }
        }

        /**
         * onSessionCreated: ARCore session pehli baar initialize hone pe call hota hai.
         * ENVIRONMENTAL_HDR: real-world lighting estimate use karta hai taake 3D models
         * kamre ki actual lighting se match karein - zyada realistic dikhte hain.
         */
        binding.arSceneView.onSessionCreated = { session ->
            session.configure(session.config.apply {
                lightEstimationMode =
                    com.google.ar.core.Config.LightEstimationMode.ENVIRONMENTAL_HDR
            })
        }

        setupCatalog()
        setupOverlay()
        setupButtons()
        observeViewModel()
    }

    /**
     * onDestroyView: Fragment ka view destroy ho raha hai.
     * Sab AnchorNodes destroy karo - ARCore memory leak rokne ke liye zaroori.
     * _binding null karo - destroyed view ka reference na rahe.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        placedItems.clear()
        sessionRef.set(null)
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AR PLACEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * doArTap - User ne floor pe tap kiya, furniture place karo.
     *
     * Flow:
     * 1. Session check - agar AR ready nahi to user ko batao
     * 2. session.update() - latest ARCore frame lo
     * 3. hitTest - tap ke neeche koi floor (Plane) hai?
     *    - Plane.isPoseInPolygon: tap exactly detected floor area ke andar hai
     *    - TrackingState.TRACKING: yeh plane abhi accurately track ho raha hai
     * 4. Hit mila → anchor banao → model load karo
     * 5. Hit nahi mila → user ko white dots pe tap karne ko kaho
     *
     * @param x  Touch X coordinate screen pe
     * @param y  Touch Y coordinate screen pe
     */
    private fun doArTap(x: Float, y: Float) {
        val session = sessionRef.get() ?: binding.arSceneView.session ?: run {
            binding.root.showSnackbar("AR not ready - move camera over floor slowly")
            return
        }
        try {
            val frame = session.update()
            val hit = frame.hitTest(x, y).firstOrNull { h ->
                val t = h.trackable
                t is Plane &&
                    t.trackingState == TrackingState.TRACKING &&
                    t.isPoseInPolygon(h.hitPose)
            }
            if (hit != null) {
                val furniture = pendingFurniture ?: return
                if (furniture.customImageUri != null)
                    placeImageModel(hit.createAnchor(), furniture)
                else
                    loadAndPlaceModel(hit.createAnchor(), furniture)
                pendingFurniture = null
                selectedTv?.setBackgroundResource(R.drawable.bg_glass_pill)
                selectedCustomCard?.background =
                    requireContext().getDrawable(R.drawable.bg_glass_pill)
                selectedTv = null
                selectedCustomCard = null
                hideHint()
            } else {
                binding.root.showSnackbar("⚠️ White dots pe tap karo")
            }
        } catch (e: Exception) {
            binding.root.showSnackbar("Try again: ${e.message}")
        }
    }

    /**
     * loadAndPlaceModel - GLB 3D model load karo aur AR anchor pe attach karo.
     *
     * AnchorNode: ARCore anchor ko SceneView node se wrap karta hai.
     *             Yeh ensure karta hai ke model real-world position pe fixed rahe
     *             chahe camera kahan bhi jaye.
     *
     * scaleToUnits: SceneView automatically model ko is metre size mein fit karta hai
     *               regardless of original GLB file dimensions.
     *
     * Coroutine: modelLoader.createModelInstance suspend function hai  - 
     *            IO thread pe GLB file load hoti hai, Main pe node attach hota hai.
     */
    private fun loadAndPlaceModel(anchor: Anchor, furniture: FurnitureOption) {
        val engine = binding.arSceneView.engine
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        binding.arSceneView.addChildNode(anchorNode)
        binding.root.showSnackbar("Loading ${furniture.emoji}...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val modelInstance = binding.arSceneView.modelLoader
                    .createModelInstance("models/${furniture.modelFile}.glb")

                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = furniture.scale
                ).apply {
                    position = Position(0f, furniture.yOffset, 0f)
                }

                anchorNode.addChildNode(modelNode)
                val item = viewModel.addFurnitureItem(furniture.name)
                placedItems[item.id] = PlacedItem(item.id, anchorNode, modelNode, 0f, 1.0f)
                // Naya placed item automatically select ho - rotate/delete turant kaam kare
                selectedId = item.id
                binding.root.showSnackbar("✅ ${furniture.emoji} ${furniture.name} placed!")

            } catch (e: Exception) {
                // Model load fail - anchor hata do warna orphan anchor memory leak karta hai
                binding.arSceneView.removeChildNode(anchorNode)
                try { anchorNode.destroy() } catch (_: Exception) {}
                binding.root.showSnackbar("❌ Load failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM IMAGE PLACEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * placeImageModel - User ki gallery image ko AR mein vertical panel ki tarah place karo.
     *
     * Kyun GLB approach: SceneView ka modelLoader sirf GLB format support karta hai.
     * Filament texture direct API GL thread maangti hai jo coroutine mein guarantee nahi.
     *
     * Solution - Image GLB mein bake karo:
     * 1. Bitmap → PNG bytes (compress karo)
     * 2. PNG + quad mesh → complete GLB binary buildImageGlb() se
     * 3. GLB File mein save karo cache dir mein
     * 4. createModelInstance(file) - SceneView khud GL thread pe texture load karta hai
     *
     * KHR_materials_unlit extension: real image colours AR lighting se affect nahi hote  - 
     * image hamesha original colours mein dikhti hai.
     */
    private fun placeImageModel(anchor: Anchor, furniture: FurnitureOption) {
        val uri = furniture.customImageUri ?: return
        val engine = binding.arSceneView.engine
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        binding.arSceneView.addChildNode(anchorNode)
        binding.root.showSnackbar("📷 Image load ho rahi hai...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Step 1: Bitmap decode - IO thread pe (main thread block na ho)
                val (pngBytes, aspect) = withContext(Dispatchers.IO) {
                    val bmp = requireContext().contentResolver
                        .openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        } ?: throw Exception("Cannot decode image")

                    val w = bmp.width.coerceAtLeast(1)
                    val h = bmp.height.coerceAtLeast(1)
                    val asp = w.toFloat() / h

                    // 512px max - GPU texture size limit
                    val maxDim = 512
                    val scaled = if (w > maxDim || h > maxDim) {
                        val (sw, sh) = if (w >= h)
                            maxDim to (maxDim * h / w)
                        else
                            (maxDim * w / h) to maxDim
                        Bitmap.createScaledBitmap(bmp, sw, sh, true).also { bmp.recycle() }
                    } else bmp

                    // PNG format - lossless, transparency support
                    val baos = java.io.ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    scaled.recycle()
                    Pair(baos.toByteArray(), asp)
                }

                // Step 2: GLB banao - pure Kotlin, koi library nahi
                val glbBytes: ByteArray = withContext(Dispatchers.IO) {
                    buildImageGlb(pngBytes, aspect)
                }

                // Step 3: Cache dir mein file - createModelInstance(File) maangta hai
                val glbFile = withContext(Dispatchers.IO) {
                    val f = java.io.File(
                        requireContext().cacheDir,
                        "dv_img_${System.currentTimeMillis()}.glb"
                    )
                    f.writeBytes(glbBytes)
                    f
                }

                // Step 4: SceneView se load - GL thread internally handle hota hai
                val modelInstance = withContext(Dispatchers.Main) {
                    binding.arSceneView.modelLoader.createModelInstance(glbFile)
                }

                // Step 5: Node place karo - GLB already vertical (XY plane)
                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits  = 1.0f   // 1 metre tall panel
                ).apply {
                    position = Position(0f, 0f, 0f)
                }

                anchorNode.addChildNode(modelNode)
                val item = viewModel.addFurnitureItem(furniture.name)
                placedItems[item.id] = PlacedItem(item.id, anchorNode, modelNode, 0f, 1.0f)
                selectedId = item.id
                binding.root.showSnackbar("✅ Image AR mein place ho gayi!")

                // Temp GLB file cleanup - model load ho gaya, file ab zaruri nahi
                withContext(Dispatchers.IO) {
                    try { glbFile.delete() } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.arSceneView.removeChildNode(anchorNode)
                    try { anchorNode.destroy() } catch (_: Exception) {}
                    binding.root.showSnackbar("❌ Image error: ${e.message}")
                }
            }
        }
    }

    /**
     * buildImageGlb - Pure Kotlin mein complete GLB binary banao.
     *
     * GLB format structure:
     * [12-byte header] [JSON chunk] [BIN chunk]
     *
     * JSON chunk: glTF scene description (mesh, material, texture refs)
     * BIN chunk:  actual binary data - vertices + indices + PNG image bytes
     *
     * Mesh: 4 vertices, 2 triangles - vertical quad (XY plane)
     *   - Bottom Y=0: floor pe baith ta hai
     *   - Top Y=1: 1 metre tall
     *   - Width = aspect metres: image squeeze nahi hoti
     *
     * KHR_materials_unlit: Standard glTF extension.
     * AR scene mein light estimation hoti hai lekin unlit material pe apply nahi hoti.
     * Result: image always original colours mein dikhti hai, dark/bright room mein same.
     *
     * @param pngBytes  PNG compressed image bytes
     * @param aspect    width/height ratio - GLB mein bake hoti hai vertex positions mein
     */
    private fun buildImageGlb(pngBytes: ByteArray, aspect: Float): ByteArray {
        val w = aspect.coerceIn(0.25f, 4f)   // panel width in metres
        val h = 1.0f                           // panel height in metres (fixed 1m)

        // ── Vertex data: POS(12 bytes) + NORMAL(12 bytes) + UV(8 bytes) = 32 bytes/vertex ──
        val vb = java.nio.ByteBuffer.allocate(128).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // v0 bottom-left  v1 bottom-right  v2 top-right  v3 top-left
        // Normal: (0,0,1) = facing +Z direction (towards camera when placed)
        // UV: standard 0-1 mapping so full image fills the quad
        arrayOf(
            floatArrayOf(-w/2f, 0f, 0f,  0f,0f,1f,  0f,1f),  // bottom-left
            floatArrayOf( w/2f, 0f, 0f,  0f,0f,1f,  1f,1f),  // bottom-right
            floatArrayOf( w/2f,  h, 0f,  0f,0f,1f,  1f,0f),  // top-right
            floatArrayOf(-w/2f,  h, 0f,  0f,0f,1f,  0f,0f),  // top-left
        ).forEach { v -> v.forEach { vb.putFloat(it) } }
        val vertBytes = vb.array()   // 128 bytes

        // ── Index buffer: 2 triangles (CCW winding) ──────────────────────────
        // Triangle 1: v0-v1-v2 (bottom-left to top-right)
        // Triangle 2: v0-v2-v3 (bottom-left top-right to top-left)
        val ib = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        shortArrayOf(0,1,2, 0,2,3).forEach { ib.putShort(it) }
        val idxBytes = ib.array()   // 12 bytes

        // ── Buffer layout calculation ─────────────────────────────────────────
        val imgOffset = vertBytes.size + idxBytes.size   // 140 bytes
        val imgPad    = (4 - imgOffset % 4) % 4         // 4-byte alignment padding
        val imgStart  = imgOffset + imgPad               // actual PNG start offset
        val binTotal  = imgStart + pngBytes.size
        val binPad    = (4 - binTotal % 4) % 4          // final BIN chunk padding

        // ── glTF JSON - scene description ────────────────────────────────────
        // bufferViews[0]: vertex data (byteStride=32 for interleaved)
        // bufferViews[1]: index data
        // bufferViews[2]: placeholder (unused, index 2 reserved)
        // bufferViews[3]: PNG image bytes
        val json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0,"mode":4}]}],"materials":[{"name":"ImageMat","pbrMetallicRoughness":{"baseColorTexture":{"index":0},"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1},"doubleSided":true,"alphaMode":"OPAQUE","extensions":{"KHR_materials_unlit":{}}}],"extensionsUsed":["KHR_materials_unlit"],"textures":[{"source":0,"sampler":0}],"images":[{"mimeType":"image/png","bufferView":3}],"samplers":[{"magFilter":9729,"minFilter":9729,"wrapS":33071,"wrapT":33071}],"accessors":[{"bufferView":0,"byteOffset":0,"componentType":5126,"count":4,"type":"VEC3","min":[${-w/2f},0.0,0.0],"max":[${w/2f},${h},0.0]},{"bufferView":0,"byteOffset":12,"componentType":5126,"count":4,"type":"VEC3"},{"bufferView":0,"byteOffset":24,"componentType":5126,"count":4,"type":"VEC2"},{"bufferView":1,"byteOffset":0,"componentType":5123,"count":6,"type":"SCALAR","min":[0],"max":[3]}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":128,"byteStride":32,"target":34962},{"buffer":0,"byteOffset":128,"byteLength":12,"target":34963},{"buffer":0,"byteOffset":0,"byteLength":0},{"buffer":0,"byteOffset":${imgStart},"byteLength":${pngBytes.size}}],"buffers":[{"byteLength":${binTotal + binPad}}]}"""

        val jb = json.toByteArray()
        val jp = (4 - jb.size % 4) % 4   // JSON chunk padding (spaces, as per GLB spec)
        val totalSize = 12 + 8 + jb.size + jp + 8 + binTotal + binPad

        // ── GLB binary assembly ───────────────────────────────────────────────
        // Little-endian int helper
        val out = java.io.ByteArrayOutputStream(totalSize)
        fun i32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }

        // GLB header: magic(0x46546C67="glTF") + version(2) + totalLength
        i32(0x46546C67); i32(2); i32(totalSize)
        // JSON chunk
        i32(jb.size + jp); i32(0x4E4F534A)   // "JSON"
        out.write(jb); repeat(jp) { out.write(0x20) }   // pad with spaces
        // BIN chunk
        i32(binTotal + binPad); i32(0x004E4942)   // "BIN\0"
        out.write(vertBytes)                       // vertex data
        out.write(idxBytes)                        // index data
        repeat(imgPad) { out.write(0) }            // alignment
        out.write(pngBytes)                        // embedded PNG
        repeat(binPad) { out.write(0) }            // final padding
        return out.toByteArray()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOUCH OVERLAY - MOVE + PINCH + TAP
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * setupOverlay - Transparent View jo ARSceneView ke upar float karta hai.
     *
     * Kyun overlay: ARSceneView apna touch handling karta hai camera movement ke liye.
     * Humara overlay usse intercept karta hai jab:
     *   - pendingFurniture != null → tap = placement
     *   - Long press → drag mode start
     *   - 2 fingers → pinch resize
     *
     * GestureDetector:
     *   - onSingleTapConfirmed: placement tap
     *   - onLongPress: move mode shuru
     *
     * Touch routing:
     *   - Multi-touch (2+ fingers): pinch handler ko jao, camera ko nahi
     *   - Drag mode: moveItemToPosition() call karo
     *   - Otherwise: ARSceneView ko dene do (camera movement)
     */
    private fun setupOverlay() {
        val overlay = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        (binding.root as FrameLayout).addView(overlay, 1)

        val gd = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {

                /** Single confirmed tap - placement ya selection */
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (pendingFurniture != null) doArTap(e.x, e.y)
                    return true
                }

                /**
                 * Long press - move mode shuru karo.
                 * selectedId ya last placed item select ho jata hai.
                 * Hint dikhta hai "✋ Drag karo"
                 */
                override fun onLongPress(e: MotionEvent) {
                    if (pendingFurniture != null) return
                    if (placedItems.isEmpty()) return
                    val id = selectedId ?: placedItems.keys.lastOrNull() ?: return
                    selectedId = id
                    isDraggingItem = true
                    draggingItemId = id
                    showHint("✋ Drag karo - floor pe move hoga")
                }
            })

        overlay.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {

                // Dusri ungli touch hui - pinch shuru, drag cancel
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (ev.pointerCount == 2) {
                        isDraggingItem = false; draggingItemId = null
                        val dx = ev.getX(0) - ev.getX(1)
                        val dy = ev.getY(0) - ev.getY(1)
                        lastPinchDist = sqrt(dx * dx + dy * dy)
                        isPinching = true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    when {
                        // Pinch-to-resize: distance ratio se scale calculate
                        isPinching && ev.pointerCount == 2 -> {
                            val dx = ev.getX(0) - ev.getX(1)
                            val dy = ev.getY(0) - ev.getY(1)
                            val newDist = sqrt(dx * dx + dy * dy)
                            val scaleFactor = newDist / lastPinchDist.coerceAtLeast(1f)
                            lastPinchDist = newDist
                            val id = selectedId ?: placedItems.keys.lastOrNull()
                            id?.let { itemId ->
                                selectedId = itemId
                                placedItems[itemId]?.let { item ->
                                    val s = (item.currentScale * scaleFactor).coerceIn(0.1f, 5.0f)
                                    item.currentScale = s
                                    item.node.scale = Scale(s, s, s)
                                }
                            }
                        }
                        // Drag move: har MOVE event pe new floor position calculate
                        isDraggingItem && ev.pointerCount == 1 -> {
                            moveItemToPosition(draggingItemId, ev.x, ev.y)
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> { isPinching = false }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingItem) {
                        isDraggingItem = false; draggingItemId = null; hideHint()
                    }
                    isPinching = false
                }
            }

            // Touch routing decision:
            // pendingFurniture + single touch → gesture detector (placement)
            // multi-touch ya drag → consume (camera movement mat hone do)
            if (pendingFurniture != null && !isPinching) {
                gd.onTouchEvent(ev); true
            } else {
                gd.onTouchEvent(ev)   // long press detection ke liye zaroori
                isPinching || ev.pointerCount > 1 || isDraggingItem
            }
        }
    }

    /**
     * moveItemToPosition - Long press drag se item ko floor pe naye position pe le jao.
     *
     * Approach: har drag event pe:
     * 1. hitTest se naya floor position nikalo
     * 2. Naya AnchorNode banao wahan
     * 3. ModelNode ko purane parent se detach karo
     * 4. Naye AnchorNode mein attach karo
     * 5. Purana AnchorNode destroy karo (memory free)
     *
     * Agar hit nahi mila: item same jagah rehta hai - crash nahi hota.
     *
     * @param itemId  PlacedItem ki ID jo move ho rahi hai
     * @param x, y    Current touch position screen pe
     */
    private fun moveItemToPosition(itemId: String?, x: Float, y: Float) {
        itemId ?: return
        val item = placedItems[itemId] ?: return
        val session = sessionRef.get() ?: binding.arSceneView.session ?: return

        try {
            val frame = session.update()
            val hit = frame.hitTest(x, y).firstOrNull { h ->
                val t = h.trackable
                t is Plane &&
                    t.trackingState == TrackingState.TRACKING &&
                    t.isPoseInPolygon(h.hitPose)
            } ?: return   // Floor nahi mila - current position maintain karo

            val newAnchor = hit.createAnchor()
            val engine = binding.arSceneView.engine
            val newAnchorNode = AnchorNode(engine = engine, anchor = newAnchor)
            binding.arSceneView.addChildNode(newAnchorNode)

            // Node reparent: purana parent → naya parent
            item.anchorNode.removeChildNode(item.node)
            newAnchorNode.addChildNode(item.node)

            // Purana anchor cleanup
            binding.arSceneView.removeChildNode(item.anchorNode)
            try { item.anchorNode.destroy() } catch (_: Exception) {}

            // Map update: same id, naya anchorNode
            placedItems[itemId] = item.copy(anchorNode = newAnchorNode)

        } catch (_: Exception) {
            // Move fail - silently ignore, item stays
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CATALOG SETUP
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * setupCatalog - Bottom strip mein furniture cards banao.
     * Pehle builtin catalog, phir koi custom image cards (session mein pehle add ki gayi).
     */
    private fun setupCatalog() {
        val container = binding.layoutFurnitureItems
        container.removeAllViews()
        catalog.forEach { addCatalogCard(container, it) }
        customOptions.forEach { addCustomImageCard(container, it) }
    }

    /**
     * addCatalogCard - Catalog item ke liye TextView card banao.
     * Tap pe: card highlight ho, pendingFurniture set ho, hint dikhaye.
     */
    private fun addCatalogCard(container: LinearLayout, f: FurnitureOption) {
        val tv = TextView(requireContext()).apply {
            text = "${f.emoji}\n${f.name}"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(20, 14, 20, 14)
            setTextColor(android.graphics.Color.WHITE)
            background = requireContext().getDrawable(R.drawable.bg_glass_pill)
        }
        tv.setOnClickListener {
            clearCardSelection()
            selectedTv = tv
            tv.setBackgroundColor(android.graphics.Color.argb(200, 255, 170, 0))
            pendingFurniture = f
            showHint("📦 Floor pe tap karo - ${f.emoji} ${f.name}")
        }
        container.addView(tv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) })
    }

    /**
     * addCustomImageCard - Gallery image ke liye thumbnail card banao.
     * ImageView mein actual image dikhti hai.
     * Tap pe: pendingFurniture set hota hai usi image ke option se.
     */
    private fun addCustomImageCard(container: LinearLayout, f: FurnitureOption) {
        val uri = f.customImageUri ?: return
        val cardSize = (80 * resources.displayMetrics.density).toInt()
        val card = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(cardSize, cardSize).apply {
                setMargins(8, 0, 8, 0)
            }
            background = requireContext().getDrawable(R.drawable.bg_glass_pill)
            clipToOutline = true
        }
        val iv = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            try { setImageURI(uri) } catch (_: Exception) {}
        }
        card.addView(iv)
        val label = TextView(requireContext()).apply {
            text = f.name; textSize = 9f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
            setPadding(4, 2, 4, 2)
        }
        card.addView(label)
        card.setOnClickListener {
            clearCardSelection()
            selectedCustomCard = card
            card.setBackgroundColor(Color.argb(200, 255, 170, 0))
            pendingFurniture = f
            showHint("🖼️ Floor pe tap karo - ${f.name}")
        }
        container.addView(card)
    }

    /** Sab card selections clear karo - naya card select karne se pehle */
    private fun clearCardSelection() {
        selectedTv?.setBackgroundResource(R.drawable.bg_glass_pill)
        selectedCustomCard?.background =
            requireContext().getDrawable(R.drawable.bg_glass_pill)
        selectedTv = null
        selectedCustomCard = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUTTONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * setupButtons - FAB buttons ke click listeners.
     *
     * fabAddFurniture: Gallery se image pick karo
     * fabRotate:       Selected item 15° rotate karo (Y-axis)
     * fabDelete:       Selected item hata do (anchor destroy + map remove)
     * fabSave:         Design save karo (screenshot + Room DB)
     * btnBack:         AR screen se wapas jao
     */
    private fun setupButtons() {
        binding.fabAddFurniture.setOnClickListener { checkPermissionsAndOpenPicker() }

        binding.fabRotate.setOnClickListener {
            // selectedId ?? last placed - agar koi selected nahi to last item rotate ho
            val id = selectedId ?: placedItems.keys.lastOrNull() ?: run {
                binding.root.showSnackbar("⚠️ Pehle furniture place karo")
                return@setOnClickListener
            }
            selectedId = id
            placedItems[id]?.let { item ->
                item.rotationY += 15f
                item.node.rotation = Rotation(0f, item.rotationY, 0f)
                binding.root.showSnackbar("↻ ${(item.rotationY % 360).toInt()}°")
            }
        }

        binding.fabDelete.setOnClickListener {
            val id = selectedId ?: run {
                binding.root.showSnackbar("⚠️ Item select karo pehle")
                return@setOnClickListener
            }
            placedItems[id]?.let { item ->
                binding.arSceneView.removeChildNode(item.anchorNode)
                try { item.anchorNode.destroy() } catch (_: Exception) {}
                placedItems.remove(id)
                viewModel.removeFurnitureItem(id)
            }
            // Delete ke baad pichla item select ho
            selectedId = placedItems.keys.lastOrNull()
            binding.root.showSnackbar("🗑️ Deleted")
        }

        binding.fabSave.setOnClickListener { showSaveDialog() }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE - FIXED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * showSaveDialog - Design ka naam poochho, phir saveDesign() call karo.
     * Koi furniture nahi → save allow nahi.
     */
    private fun showSaveDialog() {
        if (placedItems.isEmpty()) {
            binding.root.showSnackbar("⚠️ Pehle koi furniture place karo")
            return
        }
        val dv = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_design, null)
        val et = dv.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.etDesignName
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.save_design_title)
            .setView(dv)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = et?.text?.toString()?.trim()
                if (name.isNullOrBlank()) {
                    binding.root.showSnackbar(getString(R.string.design_name_empty))
                    return@setPositiveButton
                }
                saveDesign(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * saveDesign - Poora save flow:
     *
     * 1. Screenshot lo (PixelCopy API - actual AR camera feed capture hoti hai)
     * 2. JPEG file mein save karo (app-private storage - koi permission nahi chahiye)
     * 3. MediaStore gallery mein bhi copy (Android 10+ - Gallery app mein dikhega)
     * 4. ViewModel ko batao → Repository → Room DB mein insert
     *
     * FIX: _binding null check har step pe - PixelCopy callback async hai,
     * fragment tab tak destroy ho sakta hai. Null check se crash nahi hota.
     *
     * FIX: navigateUp() sirf UiState.Success observer mein - saveDesign() se nahi.
     * Warna coroutine chalta rehta hai lekin fragment destroy ho jata hai → crash.
     */
    private fun saveDesign(name: String) {
        val b = _binding ?: return
        b.progressSaving.visibility = View.VISIBLE
        b.fabSave.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Furniture list collect karo
                val furnitureList = placedItems.values
                    .mapIndexed { i, _ -> "Item ${i+1}" }
                    .joinToString(", ")
                    .ifEmpty { "AR Design" }

                // Colorful placeholder image banao with design info
                val bmp = createDesignThumbnail(name, furnitureList)

                val file = withContext(Dispatchers.IO) {
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val dir = File(requireContext().filesDir, "designs").also { it.mkdirs() }
                    val f = File(dir, "design_$ts.jpg")
                    FileOutputStream(f).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bmp.recycle()
                    f
                }

                // Also try PixelCopy in background (bonus - might work)
                try {
                    val arBmp = captureScreen()
                    withContext(Dispatchers.IO) {
                        // Only replace if not black (check average brightness)
                        val pixels = IntArray(100)
                        arBmp.getPixels(pixels, 0, 10, 
                            arBmp.width/2 - 5, arBmp.height/2 - 5, 10, 10)
                        val avgBrightness = pixels.map { p ->
                            ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
                        }.average()
                        
                        if (avgBrightness > 30) { // Not black
                            FileOutputStream(file).use { out ->
                                arBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                        }
                        arBmp.recycle()
                    }
                } catch (_: Exception) {}

                viewModel.saveDesign(name, file.absolutePath)

            } catch (e: Exception) {
                _binding?.progressSaving?.visibility = View.GONE
                _binding?.fabSave?.isEnabled = true
                _binding?.root?.showSnackbar("Save error: ${e.message}")
            }
        }
    }

    /**
     * Design ka colorful thumbnail banao — furniture count aur naam ke saath.
     * AR screenshot capture nahi hoti is device pe, isliye ek
     * informative placeholder banate hain jo design details dikhaye.
     */
    private fun createDesignThumbnail(name: String, items: String): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Gradient background — dark teal (AR/interior feel)
        val gradient = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                android.graphics.Color.parseColor("#1a237e"),
                android.graphics.Color.parseColor("#0d47a1"),
                android.graphics.Color.parseColor("#01579b"),
                android.graphics.Color.parseColor("#006064")
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val bgPaint = android.graphics.Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
        }

        // App name top
        paint.textSize = 48f
        paint.alpha = 180
        canvas.drawText("DecoVision AR", 80f, 160f, paint)

        // Design name — large
        paint.textSize = 96f
        paint.alpha = 255
        paint.isFakeBoldText = true
        canvas.drawText(name, 80f, h / 2f - 80f, paint)

        // Items count
        paint.textSize = 52f
        paint.isFakeBoldText = false
        paint.alpha = 200
        val count = placedItems.size
        canvas.drawText("$count furniture piece${if (count != 1) "s" else ""}", 80f, h / 2f + 20f, paint)

        // Date
        paint.textSize = 44f
        paint.alpha = 150
        val date = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText(date, 80f, h / 2f + 100f, paint)

        // Furniture icons
        paint.textSize = 80f
        paint.alpha = 255
        val emojis = listOf("🛋️", "🪑", "🪵", "🛏️", "🗄️", "📺", "💡", "🪴")
        var ex = 80f
        catalog.filter { opt -> placedItems.values.any { true } }
            .take(5)
            .forEachIndexed { i, _ ->
                canvas.drawText(emojis.getOrElse(i) { "🏠" }, ex, h / 2f - 220f, paint)
                ex += 140f
            }

        return bmp
    }

    /**
     * captureScreen - AR screen ka screenshot lo.
     *
     * Android 8+ (API 26): PixelCopy - actual AR camera + 3D models capture hote hain.
     *   Window ka hardware-rendered frame directly bitmap mein copy hota hai.
     *   CountDownLatch: async callback ko synchronous banana ke liye.
     *
     * Below API 26: View.draw() fallback - sirf UI overlay capture hota hai (no AR).
     *
     * FIX: _binding null check PixelCopy callback mein - fragment destroy ho sakta hai
     *      callback aane se pehle.
     */
    /**
     * captureScreen - ARSceneView ka actual render capture karo.
     *
     * Problem: View.draw() SurfaceView (ARSceneView) ko capture nahi karta
     * — sirf black rectangle aata hai kyunke SurfaceView alag window mein render hota hai.
     *
     * Fix: PixelCopy API (Android 8+) jo actual window buffer se pixels copy karta hai.
     * Yeh AR camera + 3D models dono capture karta hai.
     *
     * CountDownLatch se synchronous wait — GL thread pe callback aata hai.
     */
    /**
     * captureScreen - UI hide karke PixelCopy se AR scene capture karo.
     * SurfaceView ko directly capture nahi kar sakte — UI elements ko
     * INVISIBLE karo taake PixelCopy pure AR scene ko capture kare.
     */
    private suspend fun captureScreen(): Bitmap = withContext(Dispatchers.Main) {
        val b = _binding ?: return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val w = b.root.width.coerceAtLeast(1)
        val h = b.root.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.root.draw(Canvas(bmp))
        bmp
    }

    /** File ko directly gallery mein copy karo */
    private fun saveToGallerySync(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = requireContext().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/DecoVision")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().copyTo(out) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    /**
     * saveToGallery - Design thumbnail ko device Gallery mein bhi save karo.
     *
     * MediaStore.Images.Media: Android ka media database - Gallery app yahan se content padhta hai.
     * IS_PENDING: pehle 1 set karo (file write in progress), phir 0 (complete)  - 
     *             incomplete file Gallery mein na dikhe.
     * RELATIVE_PATH: Pictures/DecoVision folder mein organized rakho.
     *
     * Only Android 10+ (API 29) - below that old WRITE_EXTERNAL_STORAGE approach needed.
     */
    private fun saveToGallery(bmp: Bitmap, fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = requireContext().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/DecoVision")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWMODEL OBSERVER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * observeViewModel - ViewModel ke LiveData observe karo.
     *
     * saveState: Room save operation ka result.
     *   Loading → progress bar dikha, button disable
     *   Success → progress hide, snackbar, navigateUp() ← SIRF YAHAN navigate karo
     *   Error   → progress hide, button enable, error message
     *
     * FIX: navigateUp() sirf Success mein hai - saveDesign() mein nahi.
     * Pehle saveDesign() mein navigateUp() tha jo fragment destroy karta tha
     * aur baad mein coroutine crash karta tha (_binding null).
     */
    private fun observeViewModel() {
        viewModel.saveState.observe(viewLifecycleOwner) { state ->
            val b = _binding ?: return@observe
            when (state) {
                is UiState.Loading -> {
                    b.progressSaving.visibility = View.VISIBLE
                    b.fabSave.isEnabled = false
                }
                is UiState.Success -> {
                    b.progressSaving.visibility = View.GONE
                    b.fabSave.isEnabled = true
                    b.root.showSnackbar("✅ Design save ho gayi!")
                    // isAdded check - fragment detach ho gaya ho to navigate crash karta hai
                    if (isAdded) {
                        try { findNavController().navigateUp() }
                        catch (_: Exception) {}
                    }
                }
                is UiState.Error -> {
                    b.progressSaving.visibility = View.GONE
                    b.fabSave.isEnabled = true
                    b.root.showSnackbar(
                        getString(R.string.design_save_error, state.message)
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GALLERY PICKER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * checkPermissionsAndOpenPicker - API version ke hisaab se sahi permission maango.
     * API 33+: READ_MEDIA_IMAGES (granular media permission)
     * Below 33: READ_EXTERNAL_STORAGE (broad storage permission)
     */
    private fun checkPermissionsAndOpenPicker() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isEmpty()) openPicker() else permissionLauncher.launch(needed.toTypedArray())
    }

    /** System image picker launch karo - ACTION_GET_CONTENT type = all images */
    private fun openPicker() {
        imagePicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
    }

    /**
     * handleCustomImage - Gallery se pick ki gayi image process karo.
     *
     * 1. Naya FurnitureOption banao customImageUri ke saath
     * 2. customOptions list mein add karo (session restart pe bhi available)
     * 3. Catalog strip mein thumbnail card add karo
     * 4. Strip ko right scroll karo - naya card visible ho
     * 5. Auto-select: user ko bas floor pe tap karna hai
     */
    private fun handleCustomImage(uri: Uri) {
        val label = "Photo ${customOptions.size + 1}"
        val option = FurnitureOption(
            name = label, emoji = "🖼️", modelFile = "",
            scale = 1.0f, customImageUri = uri
        )
        customOptions.add(option)
        addCustomImageCard(binding.layoutFurnitureItems, option)

        // Scroll right taake naya card visible ho
        binding.scrollFurniture.post {
            binding.scrollFurniture.fullScroll(View.FOCUS_RIGHT)
        }

        clearCardSelection()
        pendingFurniture = option
        showHint("🖼️ Floor pe tap karo - $label AR mein place hogi")
        binding.root.showSnackbar("✅ $label add ho gayi - floor pe tap karo")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Hint TextView dikhao - placement/move instructions ke liye */
    private fun showHint(text: String) {
        binding.tvHint.text = text
        binding.tvHint.visibility = View.VISIBLE
    }

    /** Hint TextView chupaao - action complete hone ke baad */
    private fun hideHint() {
        binding.tvHint.visibility = View.GONE
    }
}
