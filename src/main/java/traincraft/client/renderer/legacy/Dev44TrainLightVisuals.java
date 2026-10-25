package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DEV44 visual locomotive headlights; DOES NOT modify block light or spawn rules. */
public final class Dev44TrainLightVisuals {
    private Dev44TrainLightVisuals() {}
    private record Marker(double x,double y,double z) {}
    private record Entry(String sha, Marker[] markers) {}
    private static final Map<String, Entry> MAP = new HashMap<>();
    private static final Map<String, Boolean> HASH_OK = new HashMap<>();
    // Marked Shay 2 rear lamp: excluded from headlight beam and direction calculation.
    private static final Marker VB_SHAY_2_TAIL = new Marker(1.687813D,2.121738D,0.010381D);
    // DEV50d3X6: exact outward normal of Shay 2's HEADLAMP FRONT QUAD in
    // locomotive_steam_vb_shay_2.tcm (TCM1 vertex indices 2304..2307).
    // SHA256 E68F978CA29FA3AA46ECBB6E65BF0367A17602A9846881A5AE8635D706656B50
    // Measured face normal = (-0.59846008, 0, +0.80115253), 53.2403 degrees
    // outwards from local -X. Unlike X5's 27-degree assumption, the visual
    // beam and both collision probes use these SAME normalized components.
    // It remains a single Shay-2-only cone; do not change any marker XYZ.
    private static final float VB_SHAY_2_LENS_FORWARD = 0.59846008F;
    private static final float VB_SHAY_2_LENS_OUTWARD = 0.80115253F;
    // Fix 13a: protected RenderStateShard constants must be read by a subclass.
    // Isolate this access to the beam layer; do not alter raycasts or beam vertices.
    private static final class BeamStates extends RenderStateShard {
        private BeamStates() { super("traincraft_headlight_state_accessor", () -> {}, () -> {}); }
        static RenderType.CompositeState beamState() {
            return RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false);
        }
    }
    private static final RenderType HEADLIGHT_BEAM = RenderType.create(
            "traincraft_headlight_beam_visibility13a",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            BeamStates.beamState());
    // DEV55L25R2: share the existing soft-beam layer with two Creative-only
    // recovered tram previews. No legacy beam geometry or production path changes.
    public static RenderType creativeBeamType(){return HEADLIGHT_BEAM;}
    private static final Logger DEBUG_LOG = LoggerFactory.getLogger("TC-DEV44-LIGHT-DEBUG5");
    private static final Map<java.util.UUID, Long> DEBUG_LAST = new HashMap<>();
    private static final Set<String> VERIFY_LOGGED = new HashSet<>();
    private static boolean debugSample(Entity entity) {
        if (!entity.level().isClientSide) return false;
        long bucket = entity.level().getGameTime() / 60L;
        Long previous = DEBUG_LAST.put(entity.getUUID(), bucket);
        return previous == null || previous.longValue() != bucket;
    }
    static {
        MAP.put("locomotive_steam_262t", new Entry("9dc0f4d11d58cd187be62890c7e5b6565a157152d363d3441e4c3d307c60fb47", new Marker[]{new Marker(-3.375313D,-0.752790D,0.032171D)}));
        MAP.put("locomotive_steam_4_4_0", new Entry("2ed8c998e1659a31d78ef855046161fb95613d5a5b370781ef783673ab063a2d", new Marker[]{new Marker(-0.006162D,1.634150D,-1.687500D)}));
        MAP.put("locomotive_steam_adler", new Entry("3b3abbb3f2d9ad777977ab703d504166d521533a00b06cac5dd8af774ceccc32", new Marker[]{new Marker(-0.344213D,0.789464D,1.062500D), new Marker(0.352813D,0.782258D,1.062500D)}));
        MAP.put("locomotive_steam_berkshire_1225", new Entry("9ad06147c0290ff22c33a568ddf664cca3bab00704824bb390dc960e4b818865", new Marker[]{new Marker(3.328438D,-1.588487D,-0.059682D), new Marker(3.181563D,-1.995836D,0.348053D), new Marker(3.181563D,-1.998932D,-0.463596D)}));
        MAP.put("locomotive_steam_berkshire_765", new Entry("8a5588375bc8d284b29dcba73984bf4ea127232209de03fdb6aa744b7828a6c4", new Marker[]{new Marker(3.328438D,-1.718112D,-0.060288D), new Marker(3.328438D,-1.589294D,-0.058337D), new Marker(3.181563D,-1.998565D,0.313493D), new Marker(3.181563D,-2.000301D,-0.437512D)}));
        MAP.put("locomotive_steam_br80", new Entry("5396f886ea7ae2b05afb6254f0320906ec0d660b12b025206f1e45764a0000a9", new Marker[]{new Marker(-1.375312D,1.693500D,-0.010416D)}));
        MAP.put("locomotive_steam_br_britannia", new Entry("163a759bde575b7d4c6c170353905c8cb8f83788cfc56435247420b3494a0ed0", new Marker[]{new Marker(-2.137500D,-0.191765D,0.438153D), new Marker(-2.137500D,-0.198448D,-0.437820D)}));
        MAP.put("locomotive_steam_c11", new Entry("62d540ad3ad66d4ec7056a125660bdcc20bb3debbc8081d640c5e4a7b90b4959", new Marker[]{new Marker(-1.750312D,-1.025353D,-0.005798D), new Marker(-1.719062D,-0.994413D,0.402991D), new Marker(-1.719062D,-0.994782D,-0.412562D)}));
        MAP.put("locomotive_steam_c41", new Entry("37188413d060738ebdabb9abf802b9643820d12ef399194e3fb0a94ba141b0c0", new Marker[]{new Marker(-0.687813D,-1.802353D,-0.009147D)}));
        MAP.put("locomotive_steam_c41_080", new Entry("93ccae40ddadb884073d88a6cf79b53f6d2617b98d5a8737505af9f281d06941", new Marker[]{new Marker(-0.687813D,-1.799863D,-0.009849D)}));
        MAP.put("locomotive_steam_c41t", new Entry("ae488515e44dda3fb2bdf629e0252feecb14d563c50a7898370993a9c0d5ace1", new Marker[]{new Marker(-0.625313D,-1.789045D,-0.007530D)}));
        MAP.put("locomotive_steam_cherepanov", new Entry("9a9c904aa183a0d3a4b74756fc16a0bf96a82ca0d4172238e8bc01f967833713", new Marker[]{new Marker(1.062812D,0.447098D,0.005887D)}));
        MAP.put("locomotive_steam_climax", new Entry("121896af8c0e820e9bbf1f78a0cd22307604a7e3a1050d96fe92dc140d4b59ed", new Marker[]{new Marker(-2.812813D,-0.863312D,-0.012030D)}));
        MAP.put("locomotive_steam_climax_2", new Entry("3298f9a26416aba3277beae6e6848d14513f603ea6b6b5b5eef4b1b25b9c5a3f", new Marker[]{new Marker(-1.809577D,2.068704D,0.198205D)}));
        MAP.put("locomotive_steam_fowler", new Entry("cedceff96571a828e4b9c69d375ad69f63d105332bf48f7f4b3b11ee875f9a28", new Marker[]{new Marker(-0.656250D,-0.748443D,0.436333D)}));
        MAP.put("locomotive_steam_glyn_042t", new Entry("faf01d8ca5789e4697ede39bff66479a59ada87696cb203a08ff1821d858fb5e", new Marker[]{new Marker(-2.812813D,-0.865430D,0.000464D)}));
        MAP.put("locomotive_steam_gs4", new Entry("4b0ccc2822bef70fc3664f07c7091ff6193741bc59f57b7f6dbb892fe93006c3", new Marker[]{new Marker(-6.628438D,-1.404947D,-0.343689D), new Marker(-6.628438D,-1.405455D,0.340973D), new Marker(-6.847188D,-0.745653D,-0.003516D), new Marker(-6.847188D,-0.982341D,-0.001035D)}));
        MAP.put("locomotive_steam_gwr_101_class", new Entry("6c021e73387d5ae79a885f9ca5ddd5654fafcfa16dae52fc2b64f527059b5795", new Marker[]{new Marker(-1.750312D,-0.907554D,-0.002456D)}));
        MAP.put("locomotive_steam_gwr_42xx", new Entry("eefe1fae505dd708cfa8b47e391f831625ebeba44858ff45055efe215cde90d4", new Marker[]{new Marker(-2.844063D,-0.212417D,-0.003820D), new Marker(-2.844063D,-0.214745D,0.565957D), new Marker(-2.844063D,-0.216506D,-0.504685D)}));
        MAP.put("locomotive_steam_gwr_72xx", new Entry("6ef457f7b46cc0018b561904e3c984852712050c67ad05618eb34e9803c3ccc4", new Marker[]{new Marker(-3.156563D,-0.211671D,-0.006177D), new Marker(-3.156563D,-0.211258D,0.559345D), new Marker(-3.156563D,-0.228569D,-0.512319D)}));
        MAP.put("locomotive_steam_j50", new Entry("c0a8a051a1dccc9982b7b292773cda8ce1357076498aa6741302fe706da574ce", new Marker[]{new Marker(-2.062500D,-0.183760D,0.561418D)}));
        MAP.put("locomotive_steam_lssp7", new Entry("90d29ebf1278bb7eb3cc21d20c631a89056ed4a3503dd51bc3365acfdf48a401", new Marker[]{new Marker(-0.562813D,-1.093166D,0.810054D)}));
        MAP.put("locomotive_steam_milw_class_a", new Entry("cba9dd89c1d287367e9762525a4c16393c10c81f9d95f304c807038539b32961", new Marker[]{new Marker(-5.646875D,-1.124936D,0.000971D)}));
        MAP.put("locomotive_steam_pe", new Entry("cc3d8bf40a34e695b06cc617eeeee39fb1b76aaa510cefaf16bed62b30e3b4b5", new Marker[]{new Marker(-1.937500D,-0.719886D,-0.009850D)}));
        MAP.put("locomotive_steam_rw_type_2", new Entry("8201e113277cc21adc203976c04c792dbd090a19c67c5012df57dc8f9bfe85b6", new Marker[]{new Marker(-1.375312D,-0.998928D,-0.003624D)}));
        MAP.put("locomotive_steam_rw_type_3", new Entry("e1ac625f31296caa1ebfc7aabde6001a18f437411795ef6cb8bc29fdc8f58a96", new Marker[]{new Marker(-1.875312D,-0.126954D,0.000573D)}));
        MAP.put("locomotive_steam_s100_uk", new Entry("0361cee0f5bba4da0ea541cacd9ee0c4615cf88ecdae604ad9ea003687352fae", new Marker[]{new Marker(-0.950000D,-0.374098D,0.374609D), new Marker(-0.950000D,-0.374510D,1.248909D), new Marker(-0.903125D,-1.188308D,0.810644D)}));
        MAP.put("locomotive_steam_s100_us", new Entry("06bfad8cda5de0472dd77b532353bc9d98fec40dfa62637f6f56bc8d311b88c7", new Marker[]{new Marker(-0.937500D,-1.224456D,0.804171D)}));
        MAP.put("locomotive_steam_shay_3_truck", new Entry("37b23dec217825d10f5c8e1b368503ad858f8fe7b1a4bc8b10a6d09612d3f71e", new Marker[]{new Marker(-2.531250D,-0.707357D,-0.185779D)}));
        MAP.put("locomotive_steam_skookum", new Entry("999cecaaa803e4f8cf147544b148a2405c2457b9147294051df0859314445f0d", new Marker[]{new Marker(-1.718750D,-1.094421D,0.055946D)}));
        MAP.put("locomotive_steam_snow_plow", new Entry("c1748443660cfe0b81be6aff33826bb3c845b6f18fdcf172cb70b6b768d9d806", new Marker[]{new Marker(-2.937500D,-1.566356D,-0.004799D)}));
        MAP.put("locomotive_steam_southern1102", new Entry("ccd566fbe11b01c2f5784653a0cf405b9a7a7336f297c994c8302fc6a7259dfe", new Marker[]{new Marker(-0.625313D,-1.779615D,-0.004847D)}));
        MAP.put("locomotive_steam_star_class", new Entry("eefb360e8bef3a771c01a582e0f18329139888c4598b8e4d53b3fee273005363", new Marker[]{new Marker(-1.125312D,-0.530527D,-0.001355D)}));
        MAP.put("locomotive_steam_u57", new Entry("9553fe1e20c6a2965a8251efcd75a771e897ae1708be1922a00085ab5b2f091a", new Marker[]{new Marker(-0.007045D,1.942146D,4.375312D), new Marker(-0.373836D,1.005580D,4.750312D), new Marker(0.367022D,0.969763D,4.750312D)}));
        // DEV50d3X3: Both VB Shay placements from the reviewed DEV44 marker CSV, exact original mesh SHA gates.
        MAP.put("locomotive_steam_vb_shay", new Entry("1fc1209bc0288fb61671645ff3d7c2693e1c1a42115e63cf3c6db0e8da93a179", new Marker[]{new Marker(-2.625313D,-0.872865D,-0.007731D)}));
        MAP.put("locomotive_steam_vb_shay_2", new Entry("e68f978ca29fa3aa46ecbb6e65bf0367a17602a9846881a5ae8635d706656b50", new Marker[]{new Marker(-1.466338D,2.196581D,0.197824D)}));
        MAP.put("locomotive_steam_wwcp_062t", new Entry("04ce9253aed0850af62e5a4895ee73abb70ee066229b71ba251612db5d1773cf", new Marker[]{new Marker(-2.312813D,-0.088706D,-0.475094D), new Marker(-2.312813D,-0.090777D,0.462587D)}));
        MAP.put("minetrain", new Entry("416abd4b7aa4f0649ca7bcb62ea27fb820ea19dc3b82fe810454eb5da6738378", new Marker[]{new Marker(-0.625000D,1.188031D,-0.006744D)}));
        MAP.put("kof", new Entry("485257f00c3a82a95d60e1c375a69dea97aea32ac7e8a05dbc3fbf4f11cb13fe", new Marker[]{new Marker(-1.000000D,1.508360D,-0.007915D), new Marker(-1.250000D,0.820878D,-0.504152D), new Marker(-1.250000D,0.821595D,0.497946D)}));
        MAP.put("gp7", new Entry("4ec69b70405c9811e41f3aa158aef127d544f8e340ace2927ebed7227a60e3b5", new Marker[]{new Marker(-1.500000D,1.705470D,-0.006355D)}));
        MAP.put("br_e69", new Entry("d1cf68200d8093b14c1a3a1f0b9124ca28cc1c2a11107d5c71d32ccd5e127909", new Marker[]{new Marker(-1.437500D,0.536730D,-0.360201D), new Marker(-1.437500D,0.536993D,0.333103D)}));
        MAP.put("shunter", new Entry("7822bb80666998ac63430b329fa45732f4a26783eb107bc6ede28ca46f96f254", new Marker[]{new Marker(-1.750000D,1.843602D,-0.002574D)}));
    }
    private static boolean verify(ResourceLocation mesh, Entry entry) {
        String id = mesh.toString();
        Boolean prior = HASH_OK.get(id);
        if(prior != null) return prior;
        boolean pass = false;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(mesh);
            if(resource.isPresent()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = resource.get().open()) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) > 0) digest.update(b,0,n);
                }
                pass = HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(entry.sha());
            }
        } catch(Exception ignored) { }
        if (VERIFY_LOGGED.add(id)) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] resourceCheck mesh={} expected={} verified={}", id, entry.sha(), pass);
        HASH_OK.put(id,pass);
        return pass;
    }
    private static float sunsetStrength(long absoluteDayTime) {
        long time = Math.floorMod(absoluteDayTime, 24000L);
        if (time < 12000L) return 0.0F;
        if (time < 13000L) return (time-12000L)/1000.0F;
        if (time < 23000L) return 1.0F;
        return (24000L-time)/1000.0F;
    }
    /** Call after train mesh in the identical model-space PoseStack. */
    public static void draw(ResourceLocation meshId, Entity entity, PoseStack pose, MultiBufferSource buffers,
                            float meshMinX, float meshMaxX, float meshMinY) {
        boolean debug = debugSample(entity);
        if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] DRAW entry mesh={} entity={} pos={} yaw={} dayTime={} bbHeight={} alive={}",
                meshId, entity.getId(), entity.position(), entity.getYRot(), entity.level().getDayTime(), entity.getBbHeight(), entity.isAlive());
        if (!entity.isAlive()) {
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] SKIP dead-entity");
            return;
        }
        float brightness = traincraft.lighting.LocomotiveLightMode.brightness(entity);
        if (brightness <= 0.005F) {
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] SKIP daytime brightness={}", brightness);
            return;
        }
        // DEV44 Fix 10: state-driven lights, not velocity-driven lights.
        // Steam burn time and pressure are synchronized by SmallSteamLocomotive,
        // so this works on the client while a stationary engine is idling.
        // An unfired, depressurized engine has its headlights off.
        if (entity instanceof SmallSteamLocomotive steamEngine) {
            int burnTicks = steamEngine.getBurnTime();
            int steamUnits = steamEngine.getSteamAmount();
            boolean engineActive = burnTicks > 0 || steamUnits > 0;
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-ENGINE10] entity={} burnTicks={} steamUnits={} active={} speedIndependent=true",
                    entity.getId(), burnTicks, steamUnits, engineActive);
            // DEV55L3 review preview only: the synced, invisible entity name is
            // set exclusively by /tclightreview. Do not alter real boiler state.
            boolean tcDev55L3ReviewLamp = entity.getCustomName() != null
                    && "DEV55L3_TEST_LAMPS".equals(entity.getCustomName().getString());
            if (!engineActive && !tcDev55L3ReviewLamp &&
                    !traincraft.client.renderer.legacy.Dev58B10F3ViewerRuntime.active(entity,
                        meshId.getPath().replace("legacy_meshes/","").replace(".tcm",""))) return;
        }
        String filename = meshId.getPath();
        if (!filename.startsWith("legacy_meshes/locomotive_steam_") || !filename.endsWith(".tcm")) {
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] SKIP mesh-path filename={}", filename);
            return;
        }
        String id = filename.substring("legacy_meshes/".length(), filename.length()-4);
        Entry entry = MAP.get(id);
        if (entry == null) {
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] SKIP no-marker-record id={}", id);
            return;
        }
        boolean meshValid = verify(meshId,entry);
        if (!meshValid) {
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] SKIP marker-resource-hash id={}", id);
            return;
        }
        // Preserve sunsetStrength() unchanged. Source marker locations are never shifted.
        Matrix4f m = pose.last().pose();
        // DEV55L4: use true model Y orientation; Berkshire/Adler invert model Y.
        // Applies ONLY to no-fuel preview entities. 262T and VB Shays are locked.
        traincraft.debug.Dev55L4AimTuner.enter(entity,id,m.m11());
        Dev55L13SoftBeam.enter(entity,id);
        // DEV55L5: scoped to this viewer model, checked independently for each beam.
        traincraft.debug.Dev55L5Optics.enter(entity,id);
        VertexConsumer v = buffers.getBuffer(HEADLIGHT_BEAM);
        // DEV58B10F3: use the 108 user-marked positions, no old lens proxies,
        // preset angles, or duplicate legacy white beams during this test.
        if (Dev58B10F3ViewerRuntime.active(entity,id)) {
            if (Dev55L19FleetBeams.render(entity,id,v,m,new double[0][0],brightness)) return;
            // Incomplete world transform = hold, never overlay the old path.
            return;
        }
        Marker[] marks = entry.markers();
        if (marks.length == 0) return;
        double mid = 0.5D * (meshMinX + meshMaxX);
        // Determine forward from the actual marker group, not from each lamp (which
        // previously caused different lamps to cast in opposing directions).
        double avgX = 0D, avgZ = 0D;
        for (Marker mark : marks) { avgX += mark.x(); avgZ += mark.z(); }
        avgX /= marks.length; avgZ /= marks.length;
        float dir = avgX < mid ? -1F : 1F;
        // The engine's rendered model Y is inverted. Use the locomotive's own
        // physical height as a conservative reference for a low ground plane.
        // The surface is model-relative, not world terrain-following.
        float ground = meshMinY + Math.max(1.35F, entity.getBbHeight()) + 0.028F;
        // One main beam: lamp nearest model centerline and highest above ground.
        // The separate +/- Z lamps can receive outward auxiliary beams.
        int main = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int i=0;i<marks.length;i++) {
            Marker p=marks[i];
            double center = Math.abs(p.z()-avgZ);
            // Prefer central lamp; among similarly central lamps prefer higher position.
            double score = center*5.0D - p.y()*0.05D;
            if (score < best) {best=score;main=i;}
        }
        // The original lamp box is an actual square face, not crossed lightning
        // quads. Draw all recorded lamp faces, even when not used as beam sources.
        for (Marker mark : marks) {
            float x=(float)mark.x(),y=(float)mark.y(),z=(float)mark.z();
            // X6a: Shay 2's square lens is physically rotated; the legacy
            // constant-X glow quad made even an accurate beam look misaligned.
            if ("locomotive_steam_vb_shay_2".equals(id)) {
                shay2LensFace(v,m,x,y,z,brightness);
            } else if("locomotive_steam_climax_2".equals(id)
                    && Dev55L19FleetBeams.active(entity,id)) {
                // DEV55L25: yellow preview lens is drawn by L19 after the
                // center ray is resolved. The fixed-X glow face would otherwise
                // intersect the rotated lens and make a protruding lightbox.
            } else if("locomotive_steam_4_4_0".equals(id)) {
                // DEV55L27: the accepted 4-4-0 front lens lies on model -Z.
                // Rotate ONLY the emissive face; keep beam, reach, collision,
                // terrain lighting, mesh markers and soft-beam settings frozen.
                lampFaceZ(v,m,x,y,z,-1F,brightness);
            } else if("locomotive_steam_u57".equals(id)) {
                // DEV55L27: all three U57 headlamp housings face model +Z.
                // An X-normal glow square appeared edge-on in-game.
                lampFaceZ(v,m,x,y,z,1F,brightness);
            } else if("locomotive_steam_adler".equals(id)
                    && Dev55L19FleetBeams.active(entity,id)) {
                // Adler's two lamps face +Z in the verified mesh, not +X.
                // The L19 pilot aligns only the drawn yellow glow squares.
                // Physical mesh blocks and production lamps remain unchanged.
                lampFaceZ(v,m,x,y,z,1F,brightness);
            } else {
                lampFace(v,m,x,y,z,dir,brightness);
            }
        }
        // DEV55L18 review-only opposite-end red lamp candidates. No white beams,
        // no production locomotive changes, and no modification of existing marker records.
        if (Dev55L18RearLampReview.active(entity)) {
            for (double[] back : Dev55L18RearLampReview.positions(id)) {
                tailFace(v,m,new Marker(back[0],back[1],back[2]),brightness,Dev55L19FleetBeams.active(entity,id)?traincraft.debug.Dev55L19Settings.getTailDepth(id):0F);
            }
        }
        // DEV58B10R2: SOURCE-VERIFIED production beam direction, two engines only.
        // The U57 lenses have +Z face normals; WWCP front pair have -X normals.
        // Do not turn WWCP's opposite +X red REAR candidates into white beams.
        // The existing Creative L19 preview remains authoritative when active.
        if (Dev58B10BeamRepair.handles(id)
                && !Dev55L19FleetBeams.active(entity,id)) {
            double[][] exactLenses = new double[marks.length][3];
            for (int i=0; i<marks.length; i++) {
                exactLenses[i][0]=marks[i].x();
                exactLenses[i][1]=marks[i].y();
                exactLenses[i][2]=marks[i].z();
            }
            // DEV58B10R3: for these TWO verified-axis models, never expose the
            // known-wrong X-forward legacy beam if the pose safety guard fires.
            // Both success and a guarded HOLD terminate this branch; any HOLD
            // leaves the marked lens faces visible, but not an invalid beam.
            Dev58B10BeamRepair.draw(entity,id,v,m,exactLenses,brightness);
            return;
        }
        // DEV55L19: only opt-in Creative review entities use the common world-pose rays.
        // The accepted 4-4-0, Shay and Berkshire paths bypass the entire pilot.
        if (Dev55L19FleetBeams.active(entity,id)) {
            double[][] positions=new double[marks.length][4];
            for(int i=0;i<marks.length;i++){positions[i][0]=marks[i].x();positions[i][1]=marks[i].y();positions[i][2]=marks[i].z();positions[i][3]=1D;}
            boolean rendered=Dev55L19FleetBeams.render(entity,id,v,m,positions,brightness);
            if (rendered) {
                if (traincraft.debug.Dev55L19Settings.diagnostic() && entity.tickCount % 80 == 0)
                    DEBUG_LOG.info("[DEV55L22-NEW-PATH] model={} entity={} newOnly={} legacyVisible=SKIPPED",
                        id,entity.getId(),traincraft.debug.Dev55L19Settings.newOnly());
                return;
            }
            if("locomotive_steam_climax_2".equals(id)) {
                // DEV55L25 fallback: a failed review renderer still shows the
                // original fixed square, rather than a lamp that disappears.
                for(Marker mark:marks)lampFace(v,m,(float)mark.x(),(float)mark.y(),(float)mark.z(),dir,brightness);
            }
            // DEV55L22: explicit Creative-only switch. Never silently layer the
            // old static beam over a missing/broken new review ray. No effect on
            // 4-4-0, VB Shays, ordinary production engines or world-light blocks.
            if (traincraft.debug.Dev55L19Settings.newOnly()) {
                if (entity.tickCount % 80 == 0)
                    DEBUG_LOG.warn("[DEV55L22-NEW-PATH-MISSING] model={} entity={} newBeam=false legacyVisible=SUPPRESSED; inspect [DEV55L19-GUARD]",id,entity.getId());
                return;
            }
            if (traincraft.debug.Dev55L19Settings.diagnostic() && entity.tickCount % 80 == 0)
                DEBUG_LOG.warn("[DEV55L22-OLD-FALLBACK] model={} entity={} newBeam=false legacyVisible=ACTIVE",id,entity.getId());
        }
        // The red Shay 2 rear lamp is an independent outward-facing marker on +X.
        // No white beam is emitted from a tail light. Existing front lamp and
        // collision checks are preserved; only the Shay 2 branch enters here.
        if ("locomotive_steam_vb_shay_2".equals(id)) {
            tailFace(v,m,VB_SHAY_2_TAIL,brightness,0F); // Frozen accepted Shay 2
            if (debug) DEBUG_LOG.info("[TC-D3X3-SHAY] headlamp=-X tailLamp=+X front={} rear={}",
                    marks[main],VB_SHAY_2_TAIL);
        } else if ("locomotive_steam_vb_shay".equals(id) && debug) {
            DEBUG_LOG.info("[TC-D3X3-SHAY] added original Shay headlamp={} dir={}",marks[main],dir);
        }
        if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG5] ACTIVE entity={} mesh={} world={} yaw={} meshForwardX={} mainLamp={} groundModelY={} height={} cameraIndependent=true",
                entity.getId(), id, entity.position(), entity.getYRot(), dir, marks[main], ground, entity.getBbHeight());
        Marker primary=marks[main];
        // DEV50d3X6: the exact Shay 2 mesh lens faces ~53.24 degrees toward +Z,
        // not X5's 27-degree fan. Keep the X5 negative-model-Y groundward drop
        // and source/tail lamp positions; rotate only Shay 2's beam direction.
        // The visual projection, solid-wall ray and entity-hitbox probes use
        // the same normalized mesh-face components and full horizontal reach.
        if ("locomotive_steam_vb_shay".equals(id) ||
            "locomotive_steam_vb_shay_2".equals(id)) {
            boolean offsetShay = "locomotive_steam_vb_shay_2".equals(id);
            float fan = offsetShay ? 1F : 0F; // Shay 2 orientation is taken from its verified lens normal
            float max = offsetShay ? 10.8F : 12F;
            String label = offsetShay ? "VB_SHAY_2_LENS_53_24" : "VB_SHAY_DOWN";
            // X6a: Shay 2 uses the exact rendered PoseStack for world clipping.
            // The old yaw/bounding-height approximation cast its collision
            // rays away from the visible rotated lamp (walls/players missed).
            float reach = offsetShay
                    ? shay2BlockReach(entity,m,primary,max,debug)
                    : contactReach(entity,id,m,primary,dir,fan,ground,max,debug,label);
            reach = Math.min(reach,offsetShay
                    ? shay2EntityReach(entity,m,primary,max,debug)
                    : entityReach(entity,id,primary,dir,fan,max,debug,label));
            beam(v,m,primary,dir,fan,ground,brightness,!offsetShay,reach,true,offsetShay);
            if (debug) DEBUG_LOG.info("[TC-D3X6-SHAY-AIM] mesh={} source={} horizontalDirXZ=({}, {}) yawDegrees={} dropPerReach={} reach={} endModelY={}",
                id,primary,offsetShay? -VB_SHAY_2_LENS_FORWARD:dir,
                offsetShay? VB_SHAY_2_LENS_OUTWARD:0F,
                offsetShay? 53.2403F:0F,offsetShay?0.245F:0.145F,reach,
                offsetShay ? primary.y()-Math.min(2.65F,reach*0.245F)
                           : primary.y()+Math.min(1.75F,reach*0.145F));
            return;
        }
        float mainMax=traincraft.debug.Dev55L5Optics.reach(0F,12F);
        float mainReach = traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                ? traincraft.debug.Dev55L7PoseClip.blocks(entity,id,m,primary.x(),primary.y(),primary.z(),dir,0F,ground,mainMax)
                : contactReach(entity,id,m,primary,dir,0F,ground,mainMax,debug,"CENTER");
        // Fix 8: wall contact was detected on Berkshire but its broad light
        // ribbon still appeared ~1 block beyond the face. Keep the ray result
        // intact and apply a conservative visual-only safety margin.
        boolean berkshireWall = (id.equals("locomotive_steam_berkshire_765")
                || id.equals("locomotive_steam_berkshire_1225")) && mainReach < mainMax-0.15F;
        float visibleCenterReach = berkshireWall ? Math.max(0.28F,mainReach-0.85F) : mainReach;
        if (debug && berkshireWall) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG8] CENTER raw={} visual={} sharedWallCap={}",
                mainReach,visibleCenterReach,Math.max(0.28F,mainReach-1.05F));
        float centerEntityReach=traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                ? traincraft.debug.Dev55L7PoseClip.entities(entity,id,m,primary.x(),primary.y(),primary.z(),dir,0F,ground,mainMax)
                : entityReach(entity,id,primary,dir,0F,mainMax,debug,"CENTER");
        beam(v,m,primary,dir,0F,ground,brightness,true,Math.min(visibleCenterReach,centerEntityReach));
        // Only two actual lateral lamps (one on each side) can create a 45-degree
        // lateral beam. Never generate multiple beams from coincident center markers.
        int left=-1,right=-1;
        float leftFar=0.22F,rightFar=0.22F;
        for(int i=0;i<marks.length;i++) {
            if(i==main) continue;
            float dz=(float)(marks[i].z()-avgZ);
            if(dz < -leftFar) {left=i;leftFar=-dz;}
            if(dz > rightFar) {right=i;rightFar=dz;}
        }
        if(left>=0) {
            float leftMax=traincraft.debug.Dev55L5Optics.reach(-1F,10.8F);
            float raw=traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                    ? traincraft.debug.Dev55L7PoseClip.blocks(entity,id,m,marks[left].x(),marks[left].y(),marks[left].z(),dir,-1F,ground,leftMax)
                    : contactReach(entity,id,m,marks[left],dir,-1F,ground,leftMax,debug,"LEFT");
            // A lateral ray can miss a wall while its broad rendered wedge
            // overlaps it. When the center sees a wall, do not let either
            // Berkshire auxiliary wash reach past that wall's X plane.
            float reach=berkshireWall ? Math.min(raw,Math.max(0.28F,mainReach-1.05F)) : raw;
            if(debug && berkshireWall) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG8] LEFT raw={} visual={}",raw,reach);
            reach=Math.min(reach,traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                    ? traincraft.debug.Dev55L7PoseClip.entities(entity,id,m,marks[left].x(),marks[left].y(),marks[left].z(),dir,-1F,ground,leftMax)
                    : entityReach(entity,id,marks[left],dir,-1F,leftMax,debug,"LEFT"));
            beam(v,m,marks[left],dir,-1F,ground,brightness,false,reach);
            fillGaps(v,m,primary,marks[left],dir,-1F,ground,brightness,Math.min(Math.min(visibleCenterReach,centerEntityReach),reach));
        }
        if(right>=0) {
            float rightMax=traincraft.debug.Dev55L5Optics.reach(1F,10.8F);
            float raw=traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                    ? traincraft.debug.Dev55L7PoseClip.blocks(entity,id,m,marks[right].x(),marks[right].y(),marks[right].z(),dir,1F,ground,rightMax)
                    : contactReach(entity,id,m,marks[right],dir,1F,ground,rightMax,debug,"RIGHT");
            float reach=berkshireWall ? Math.min(raw,Math.max(0.28F,mainReach-1.05F)) : raw;
            if(debug && berkshireWall) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG8] RIGHT raw={} visual={}",raw,reach);
            reach=Math.min(reach,traincraft.debug.Dev55L7PoseClip.applies(entity,id)
                    ? traincraft.debug.Dev55L7PoseClip.entities(entity,id,m,marks[right].x(),marks[right].y(),marks[right].z(),dir,1F,ground,rightMax)
                    : entityReach(entity,id,marks[right],dir,1F,rightMax,debug,"RIGHT"));
            beam(v,m,marks[right],dir,1F,ground,brightness,false,reach);
            fillGaps(v,m,primary,marks[right],dir,1F,ground,brightness,Math.min(Math.min(visibleCenterReach,centerEntityReach),reach));
        }
    }
    /** DEV44 Fix 7: the traced volume starts at the actual marked lamp location,
     * not the vehicle's center. No camera/view PoseStack may enter this calculation.
     * Three narrow height samples are used because a single almost-horizontal ray
     * above a wall's top edge would incorrectly claim the route is unobstructed.
     */
    // DEV50d3X6a: camera-relative vertex positions in the live renderer's
    // model PoseStack are the *actual* beam coordinates on screen. Mapping
    // those positions to world space keeps solid-block and living-entity
    // interception in the same ray as the mesh without guessing entity yaw,
    // model origin offsets or bounding-box height. Shay 2 only.
    private static Vec3 shay2WorldPoint(Entity source, Matrix4f modelPose,
                                        double x,double y,double z) {
        Vector3f rendered = new Vector3f((float)x,(float)y,(float)z);
        modelPose.transformPosition(rendered);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 world = camera.add(rendered.x(),rendered.y(),rendered.z());
        // The vertex must lie near its owning entity; fail closed rather
        // than draw an unblocked beam if another renderer changes the pose.
        if (!Double.isFinite(world.x) || !Double.isFinite(world.y) || !Double.isFinite(world.z)
                || world.distanceToSqr(source.position()) > 225.0D) {
            throw new IllegalStateException("Shay 2 camera/model PoseStack origin mismatch: "+world
                    +" source="+source.position());
        }
        return world;
    }

    private static Vec3 shay2RayStart(Entity source, Matrix4f pose, Marker lamp) {
        // Small offset places the start just beyond the glowing lens surface.
        return shay2WorldPoint(source,pose,
                lamp.x()-VB_SHAY_2_LENS_FORWARD*0.035D,
                lamp.y(),lamp.z()+VB_SHAY_2_LENS_OUTWARD*0.035D);
    }

    private static Vec3 shay2RayEnd(Entity source, Matrix4f pose, Marker lamp,
                                   float maximum, double transverse) {
        // Tangent is perpendicular to the measured front-face normal.
        // Rays fan outward with the visible cone; its origin stays on the lens.
        return shay2WorldPoint(source,pose,
                lamp.x()-VB_SHAY_2_LENS_FORWARD*maximum
                        +VB_SHAY_2_LENS_OUTWARD*transverse,
                lamp.y()-Math.min(2.65F,maximum*0.245F),
                lamp.z()+VB_SHAY_2_LENS_OUTWARD*maximum
                        +VB_SHAY_2_LENS_FORWARD*transverse);
    }

    private static float shay2BlockReach(Entity source, Matrix4f pose, Marker lamp,
                                         float maximum, boolean debug) {
        try {
            Vec3 from=shay2RayStart(source,pose,lamp);
            float reach=maximum;
            BlockPos block=null;
            for(double side: new double[]{0.0D,-0.45D,0.45D}) {
                Vec3 to=shay2RayEnd(source,pose,lamp,maximum,side);
                BlockHitResult hit=source.level().clip(new ClipContext(from,to,
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
                if(hit.getType()==HitResult.Type.BLOCK) {
                    double fraction=from.distanceTo(hit.getLocation())/
                            Math.max(0.0001D,from.distanceTo(to));
                    float cut=(float)Math.max(0.28D,Math.min(maximum,
                            maximum*fraction-0.14D));
                    if(cut<reach){reach=cut;block=hit.getBlockPos();}
                }
                if(debug) DEBUG_LOG.info("[TC-D3X6A-SHAY2-BLOCK] from={} to={} side={} hitType={} block={} contact={} reach={}",
                        from,to,side,hit.getType(),hit.getBlockPos(),hit.getLocation(),reach);
            }
            if(debug) DEBUG_LOG.info("[TC-D3X6A-SHAY2-BLOCK] FINAL source={} hitBlock={} reach={} max={}",
                    source.getId(),block,reach,maximum);
            return reach;
        }catch(Exception e){
            DEBUG_LOG.warn("[TC-D3X6A-SHAY2-BLOCK] Fail-closed clipping {}",e.toString());
            return 0.28F;
        }
    }

    private static float shay2EntityReach(Entity source, Matrix4f pose, Marker lamp,
                                          float maximum, boolean debug) {
        try {
            Vec3 from=shay2RayStart(source,pose,lamp);
            float reach=maximum;
            int targetId=-1;
            for(double side: new double[]{0.0D,-0.45D,0.45D}) {
                Vec3 to=shay2RayEnd(source,pose,lamp,maximum,side);
                AABB volume=new AABB(from,to).inflate(0.9D);
                for(Entity target:source.level().getEntities(source,volume,e ->
                        e.isAlive() && !e.isSpectator() && e!=source &&
                        (e instanceof LivingEntity ||
                         (ForgeRegistries.ENTITY_TYPES.getKey(e.getType())!=null &&
                          "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()))))) {
                    if(target.isPassengerOfSameVehicle(source))continue;
                    java.util.Optional<Vec3> hit=target.getBoundingBox().inflate(0.10D).clip(from,to);
                    if(hit.isEmpty())continue;
                    double fraction=from.distanceTo(hit.get())/
                            Math.max(0.0001D,from.distanceTo(to));
                    float cut=(float)Math.max(0.28D,Math.min(maximum,
                            maximum*fraction-0.30D));
                    if(cut<reach){reach=cut;targetId=target.getId();}
                }
            }
            if(debug)DEBUG_LOG.info("[TC-D3X6A-SHAY2-ENTITY] source={} hitEntity={} reach={} max={}",
                    source.getId(),targetId,reach,maximum);
            return reach;
        }catch(Exception e){
            DEBUG_LOG.warn("[TC-D3X6A-SHAY2-ENTITY] Fail-closed clipping {}",e.toString());
            return 0.28F;
        }
    }

    private static void shay2LensFace(VertexConsumer v, Matrix4f m,
                                      float x,float y,float z,float brightness) {
        // Real 53.24-degree face orientation from TCM1 vertices 2304..2307.
        float nx=-VB_SHAY_2_LENS_FORWARD,nz=VB_SHAY_2_LENS_OUTWARD;
        float tx=nz,tz=-nx,h=0.072F;
        float px=x+nx*0.008F,pz=z+nz*0.008F;
        int alpha=(int)(236F*brightness);
        vertex(v,m,px-tx*h,y-h,pz-tz*h,255,234,177,alpha);
        vertex(v,m,px+tx*h,y-h,pz+tz*h,255,234,177,alpha);
        vertex(v,m,px+tx*h,y+h,pz+tz*h,255,234,177,alpha);
        vertex(v,m,px-tx*h,y+h,pz-tz*h,255,234,177,alpha);
        vertex(v,m,px-tx*h,y+h,pz-tz*h,255,234,177,alpha);
        vertex(v,m,px+tx*h,y+h,pz+tz*h,255,234,177,alpha);
        vertex(v,m,px+tx*h,y-h,pz+tz*h,255,234,177,alpha);
        vertex(v,m,px-tx*h,y-h,pz-tz*h,255,234,177,alpha);
    }

    private static float contactReach(Entity entity, String meshId, Matrix4f unusedRenderPose, Marker lamp,
                                      float dir, float lateral, float ground, float maximum, boolean debug, String label) {
        try {
            final double yaw = Math.toRadians(entity.getYRot());
            // Fix 7: the Berkshire model's +X lamp faces are opposite the
            // entity-yaw forward assumption used by Fix 6. Confirmed against
            // the measured oak wall at (26,-56,1): Fix 6 looked toward -X.
            // Change only Berkshire projection; do not disturb C11 or the
            // accepted visual/render PoseStack and its beam direction.
            final boolean berkshire = meshId.equals("locomotive_steam_berkshire_765")
                    || meshId.equals("locomotive_steam_berkshire_1225");
            final double collisionSign = berkshire ? -1D : 1D;
            final double fx = -Math.sin(yaw) * dir * collisionSign;
            final double fz =  Math.cos(yaw) * dir * collisionSign;
            // DEV55L2: beam MODEL-Z is independent of front/reverse direction.
            // 262T is runtime-accepted and must retain the exact old behavior.
            final double tcDev55L2SideBasis = "locomotive_steam_262t".equals(meshId) ? 1D : dir*collisionSign;
            final double rx = -fz*tcDev55L2SideBasis;
            final double rz = fx*tcDev55L2SideBasis;
            if (debug && lateral != 0F) DEBUG_LOG.info("[DEV55L2-SIDE-AXIS] mesh={} engine={} beam={} dir={} collisionSign={} sideBasis={} worldZ=({}, {})",
                    meshId, entity.getId(), label, dir, collisionSign, tcDev55L2SideBasis, rx, rz);
            // Local marker X and Z are the existing verified mesh coordinates.
            // The marker X is measured along the oriented locomotive axis.
            final double along = lamp.x() * dir + traincraft.debug.Dev55L5Optics.originForward(lateral);
            final double width = lamp.z() + traincraft.debug.Dev55L5Optics.originSide(lateral);
            final boolean shay2Lens = meshId.equals("locomotive_steam_vb_shay_2");
            final double alongSlope = shay2Lens ? VB_SHAY_2_LENS_FORWARD : traincraft.debug.Dev55L5Optics.forwardFactor(lateral);
            final double sideSlope = shay2Lens ? VB_SHAY_2_LENS_OUTWARD : traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral);
            final double forward = maximum;
            final double worldX = entity.getX() + fx*along + rx*width;
            final double worldZ = entity.getZ() + fz*along + rz*width;
            final double baseY = entity.getY();
            // Raycast across the projected beam's vertical profile. Keep all
            // samples above the rail top to avoid treating the floor as a wall.
            final boolean shayDown = meshId.equals("locomotive_steam_vb_shay") ||
                    meshId.equals("locomotive_steam_vb_shay_2");
            // The default DEV44 shallow ray samples remain byte-for-byte the
            // same for all accepted steam locomotives. Only the two Shays use
            // elevated samples following their newly downward-aimed ribbons.
            final double[] heights = shayDown
                    ? new double[]{Math.max(1.80D,entity.getBbHeight()*0.85D),
                                   Math.max(1.65D,entity.getBbHeight()*0.75D)}
                    : new double[]{Math.max(1.05D,entity.getBbHeight()*0.70D), 0.75D, 0.43D};
            final boolean shay2 = meshId.equals("locomotive_steam_vb_shay_2");
            final double verticalDrop=shay2 ? Math.min(2.65D,maximum*0.245D)
                    : shayDown ? Math.min(1.75D,maximum*0.145D) : 0.12D;
            float rendered = maximum;
            HitResult.Type closestType = HitResult.Type.MISS;
            BlockPos closestBlock = null;
            Vec3 closestPoint = null;
            Vec3 closestFrom = null;
            Vec3 closestTo = null;
            for (double height : heights) {
                Vec3 from = new Vec3(worldX,baseY+height,worldZ);
                // DEV55L4: use the same extra groundward slope as the preview beam.
                Vec3 to = from.add(fx*alongSlope*forward+rx*sideSlope*forward,
                                   -verticalDrop-maximum*traincraft.debug.Dev55L4AimTuner.deltaWorldDownPerBlock(),
                                    fz*alongSlope*forward+rz*sideSlope*forward);
                BlockHitResult hit = entity.level().clip(new ClipContext(from,to,
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity));
                if (hit.getType()==HitResult.Type.BLOCK) {
                    double fraction = from.distanceTo(hit.getLocation()) / Math.max(0.001D,from.distanceTo(to));
                    float distance = (float)Math.max(0.28D,Math.min(maximum,
                                                  fraction*maximum-0.12D));
                    if (distance < rendered) {
                        rendered=distance; closestType=hit.getType(); closestBlock=hit.getBlockPos();
                        closestPoint=hit.getLocation(); closestFrom=from; closestTo=to;
                    }
                }
                if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG7] BEAM {} height={} from={} to={} hitType={} block={} hitAt={} sampleReach={} max={}",
                        label,height,from,to,hit.getType(),hit.getBlockPos(),hit.getLocation(),
                        hit.getType()==HitResult.Type.BLOCK ? from.distanceTo(hit.getLocation()) : maximum,maximum);
            }
            if (debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-DEBUG7] RESULT {} mesh={} entity={} lamp={} modelForwardSign={} collisionSign={} worldForward=({}, {}) lateral={} chosenType={} block={} contact={} reach={} maximum={} cameraIndependent=true",
                    label,meshId,entity.getId(),lamp,dir,collisionSign,fx,fz,lateral,closestType,closestBlock,closestPoint,rendered,maximum);
            return rendered;
        } catch(Exception failure) {
            if(debug) DEBUG_LOG.warn("[TC-DEV44-LIGHT-DEBUG7] BEAM {} exception={}", label,failure.toString());
        }
        return maximum;
    }
    /** Fix 12: independent entity hitbox clipping of each visible beam.
     * Leaves Fix 8 block rays, shared WALL cap, geometry and vertex alpha intact.
     * The test uses nearby entity bounding boxes, never entity render geometry.
     */
    private static float entityReach(Entity source, String meshId, Marker lamp,
                                     float dir,float lateral,float maximum,boolean debug,String label) {
        try {
            double yaw=Math.toRadians(source.getYRot());
            boolean berkshire=meshId.equals("locomotive_steam_berkshire_765")
                    || meshId.equals("locomotive_steam_berkshire_1225");
            double sign=berkshire ? -1D : 1D;
            double fx=-Math.sin(yaw)*dir*sign, fz=Math.cos(yaw)*dir*sign;
            // DEV55L2: same MODEL-Z basis as the visible LEFT/RIGHT beam.
            // Frozen 262T keeps the prior basis.
            double tcDev55L2SideBasis="locomotive_steam_262t".equals(meshId) ? 1D : dir*sign;
            double rx=-fz*tcDev55L2SideBasis,rz=fx*tcDev55L2SideBasis;
            double opticsAlong=lamp.x()*dir+traincraft.debug.Dev55L5Optics.originForward(lateral);
            double opticsSide=lamp.z()+traincraft.debug.Dev55L5Optics.originSide(lateral);
            double x=source.getX()+fx*opticsAlong+rx*opticsSide;
            double z=source.getZ()+fz*opticsAlong+rz*opticsSide;
            boolean shay2Lens=meshId.equals("locomotive_steam_vb_shay_2");
            double along=shay2Lens ? VB_SHAY_2_LENS_FORWARD : traincraft.debug.Dev55L5Optics.forwardFactor(lateral);
            double side=shay2Lens ? VB_SHAY_2_LENS_OUTWARD : traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral);
            double ox=fx*along+rx*side,oz=fz*along+rz*side;
            float result=maximum;
            int hitId=-1;
            // DEV50d3X4: match the new Shay downward/outward ribbon; preserve
            // all legacy DEV44 non-Shay probes exactly as originally authored.
            boolean shayDown = meshId.equals("locomotive_steam_vb_shay") ||
                    meshId.equals("locomotive_steam_vb_shay_2");
            boolean shay2=meshId.equals("locomotive_steam_vb_shay_2");
            double verticalDrop=shay2 ? Math.min(2.65D,maximum*0.245D)
                    : shayDown ? Math.min(1.75D,maximum*0.145D) : 0.12D;
            double[] heights=shayDown
                    ? new double[]{Math.max(1.80D,source.getBbHeight()*0.85D),
                                   Math.max(1.65D,source.getBbHeight()*0.75D)}
                    : new double[]{Math.max(1.05D,source.getBbHeight()*0.70D),0.80D};
            // Probe centre and both horizontal edges, at two body heights.
            for(double vertical : heights) {
                for(double sideways : new double[]{-0.52D,0D,0.52D}) {
                    Vec3 from=new Vec3(x+rx*sideways,source.getY()+vertical,z+rz*sideways);
                    Vec3 to=from.add(ox*maximum,-verticalDrop-maximum*traincraft.debug.Dev55L4AimTuner.deltaWorldDownPerBlock(),oz*maximum);
                    AABB volume=new AABB(from,to).inflate(1.0D);
                    for(Entity target : source.level().getEntities(source,volume, e ->
                            e.isAlive() && !e.isSpectator() && e !=source &&
                            (e instanceof LivingEntity ||
                             (ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) != null &&
                              "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()))))) {
                        if(target.isPassengerOfSameVehicle(source)) continue;
                        AABB bb=target.getBoundingBox().inflate(0.08D);
                        java.util.Optional<Vec3> contact=bb.clip(from,to);
                        if(contact.isEmpty()) continue;
                        double distance=from.distanceTo(contact.get());
                        double fraction=distance/Math.max(0.001D,from.distanceTo(to));
                        float reach=(float)Math.max(0.28D,Math.min(maximum,fraction*maximum-0.30D));
                        if(reach<result) {result=reach;hitId=target.getId();}
                    }
                }
            }
            if(debug) DEBUG_LOG.info("[TC-DEV44-LIGHT-ENTITY12] beam={} engine={} hitEntity={} reach={} max={}",
                    label,source.getId(),hitId,result,maximum);
            return result;
        } catch(Exception e) {
            if(debug) DEBUG_LOG.warn("[TC-DEV44-LIGHT-ENTITY12] {} ray failed {}",label,e.toString());
            return maximum;
        }
    }
    /** DEV55L21R2: Adler test-preview yellow lens square normal to model +Z. */
    private static void lampFaceZ(VertexConsumer v,Matrix4f m,float x,float y,float z,
                                  float dir,float brightness) {
        float h=0.072F,fz=z+dir*0.008F;
        int alpha=(int)(236F*brightness);
        vertex(v,m,x-h,y-h,fz,255,232,161,alpha);
        vertex(v,m,x+h,y-h,fz,255,232,161,alpha);
        vertex(v,m,x+h,y+h,fz,255,232,161,alpha);
        vertex(v,m,x-h,y+h,fz,255,232,161,alpha);
        vertex(v,m,x-h,y+h,fz,255,232,161,alpha);
        vertex(v,m,x+h,y+h,fz,255,232,161,alpha);
        vertex(v,m,x+h,y-h,fz,255,232,161,alpha);
        vertex(v,m,x-h,y-h,fz,255,232,161,alpha);
    }
    private static void lampFace(VertexConsumer v, Matrix4f m, float x,float y,float z,
                                 float dir,float brightness) {
        // A visible square lamp face at the SELECTED mesh point; no floating proxy.
        float h=0.072F, fx=x+dir*0.008F;
        int alpha=(int)(236F*brightness);
        vertex(v,m,fx,y-h,z-h,255,232,161,alpha);
        vertex(v,m,fx,y-h,z+h,255,232,161,alpha);
        vertex(v,m,fx,y+h,z+h,255,232,161,alpha);
        vertex(v,m,fx,y+h,z-h,255,232,161,alpha);
        // Reverse winding for view from both locomotive sides.
        vertex(v,m,fx,y+h,z-h,255,232,161,alpha);
        vertex(v,m,fx,y+h,z+h,255,232,161,alpha);
        vertex(v,m,fx,y-h,z+h,255,232,161,alpha);
        vertex(v,m,fx,y-h,z-h,255,232,161,alpha);
    }
    /** Explicit rear red lamp on the SHAY 2 marked +X end; no beam or terrain lighting. */
    private static void tailFace(VertexConsumer v,Matrix4f matrix,Marker m,float brightness,float tailDepth) {
        // DEV55L26: opposite-end reviewed rear markers all face model +X.
        // Negative tailDepth sinks the red lens inward without moving its marker,
        // any white ray, collider, powered state or ground-light endpoint.
        float x=(float)m.x()+0.022F+tailDepth,y=(float)m.y(),z=(float)m.z(),h=0.075F;
        int alpha=(int)(234F*brightness);
        vertex(v,matrix,x,y-h,z-h,255,43,35,alpha);
        vertex(v,matrix,x,y-h,z+h,255,43,35,alpha);
        vertex(v,matrix,x,y+h,z+h,255,43,35,alpha);
        vertex(v,matrix,x,y+h,z-h,255,43,35,alpha);
        vertex(v,matrix,x,y+h,z-h,255,43,35,alpha);
        vertex(v,matrix,x,y+h,z+h,255,43,35,alpha);
        vertex(v,matrix,x,y-h,z+h,255,43,35,alpha);
        vertex(v,matrix,x,y-h,z-h,255,43,35,alpha);
    }
    private static void beam(VertexConsumer v,Matrix4f m,Marker mark,float dir,
                             float lateral,float ground,float brightness,boolean primary,float reach) {
        beam(v,m,mark,dir,lateral,ground,brightness,primary,reach,false,false);
    }
    private static void beam(VertexConsumer v,Matrix4f m,Marker mark,float dir,
                             float lateral,float ground,float brightness,boolean primary,
                              float reach,boolean shayDown,boolean shay2YUp) {
        float x=(float)mark.x(), y=(float)mark.y(), z=(float)mark.z();
        // DEV55L5: virtual beam origin only; square lens coordinates never move.
        x+=dir*traincraft.debug.Dev55L5Optics.originForward(lateral);
        z+=traincraft.debug.Dev55L5Optics.originSide(lateral);
        // Keep the main beam reach at 12 blocks, but narrow auxiliary lamps to
        // about 27 degrees. Each auxiliary uses a forward-projected footprint.
        float maximum = traincraft.debug.Dev55L5Optics.reach(lateral,primary ? 12.0F : 10.8F);
        float forward = Math.max(0.28F,Math.min(maximum,reach));
        // Shay 2 alone uses the NORMALIZED model lens direction; its maximum
        // 10.8-block reach still means 10.8 horizontal blocks, not an 18-block
        // oversized ray after the 53.24-degree rotation.
        float along = shay2YUp ? VB_SHAY_2_LENS_FORWARD : traincraft.debug.Dev55L5Optics.forwardFactor(lateral);
        float side = shay2YUp ? forward*VB_SHAY_2_LENS_OUTWARD
                : forward*traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral); // DEV55L6 quarter-turn, defaults unchanged
        float endX=x+dir*forward*along, endZ=z+side;
        // These legacy models invert Y. Project the FAR END onto the best
        // available model-space floor estimate instead of leaving it at lamp Y.
        // Shay 2 is Y-up even though most old steam meshes are Y-inverted.
        // Negative model Y is physically downward for this exact verified mesh.
        float endY=shay2YUp ? y-Math.min(2.65F,forward*0.245F)
                             : shayDown ? y+Math.min(1.75F,forward*0.145F)
                                        : Math.max(y+0.40F,ground);
        // DEV55L4: per-model vertical calibration on marked review previews ONLY.
        // Positive degrees lower the endpoint; normal engines use exactly 0.
        if (!shayDown && !shay2YUp)
            endY += forward * traincraft.debug.Dev55L4AimTuner.deltaModelYPerBlock();
        // Geometry-only: align the midpoint height with its 65% beam distance.
        // No alpha, collision ray, endpoint, or lamp changes.
        float midY=y+(endY-y)*0.65F;
        float xNear=x+dir*along*0.035F;
        float zNear=z+(shay2YUp ? VB_SHAY_2_LENS_OUTWARD*0.035F
                : traincraft.debug.Dev55L5Optics.turned(lateral) ? traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral)*0.035F : 0F);
        float xMid=x+dir*along*forward*0.65F;
        float zMid=z+side*0.65F;
        float width=(primary ? 1.15F : 1.05F)*Math.min(1F,0.3F+forward*0.10F);
        float nearWidth=0.065F;
        float sideNorm=(float)Math.sqrt(forward*forward*along*along+side*side);
        float nX= -side/sideNorm, nZ=dir*forward*along/sideNorm;
        int startA=(int)((primary?39F:30F)*brightness);
        // Broad double-sided horizontal wash. Edge alpha fades to zero in the
        // extended cone below; central strips cover the previously dark gaps.
        if (Dev55L13SoftBeam.active() && primary && !shayDown && !shay2YUp) {
            Dev55L13SoftBeam.ribbon(v,m,xNear,y,zNear,endX,endY,endZ,nX,nZ,width,brightness);
        } else {
        doubleSideQuad(v,m,
            xNear+nX*nearWidth,y,zNear+nZ*nearWidth,
            xNear-nX*nearWidth,y,zNear-nZ*nearWidth,
            xMid-nX*width*0.78F,midY,zMid-nZ*width*0.78F,
            xMid+nX*width*0.78F,midY,zMid+nZ*width*0.78F,startA);
        doubleSideQuad(v,m,
            xMid+nX*width*0.78F,midY,zMid+nZ*width*0.78F,
            xMid-nX*width*0.78F,midY,zMid-nZ*width*0.78F,
            endX-nX*width,endY,endZ-nZ*width,
            endX+nX*width,endY,endZ+nZ*width,(int)(startA*0.66F));
        }
        // Crossed vertical face keeps the beam visible from side angles.
        doubleSideQuad(v,m,
            xNear,y-0.06F,zNear,xNear,y+0.06F,zNear,
            endX,endY+0.18F,endZ,endX,endY-0.18F,endZ,(int)(startA*0.58F));
        // On collision, use a compact glint at the blocking surface. Never
        // extend the broad endpoint wash through the obstacle.
        if (forward < maximum-0.10F) {
            float h=0.12F;
            if (shay2YUp) {
                // Place the collision glint transverse to Shay 2's rotated ray.
                float gx=endX-dir*along*0.10F, gz=endZ-VB_SHAY_2_LENS_OUTWARD*0.10F;
                doubleSideQuad(v,m,gx+nX*h,endY-h,gz+nZ*h,
                    gx+nX*h,endY+h,gz+nZ*h,
                    gx-nX*h,endY+h,gz-nZ*h,
                    gx-nX*h,endY-h,gz-nZ*h,(int)(42F*brightness));
            } else if(traincraft.debug.Dev55L5Optics.turned(lateral)) {
                float gx=endX-dir*along*0.10F,gz=endZ-traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral)*0.10F;
                doubleSideQuad(v,m,gx+nX*h,endY-h,gz+nZ*h,
                    gx+nX*h,endY+h,gz+nZ*h,
                    gx-nX*h,endY+h,gz-nZ*h,
                    gx-nX*h,endY-h,gz-nZ*h,(int)(42F*brightness));
            } else {
                doubleSideQuad(v,m,endX-dir*0.10F,endY-h,endZ-h,
                    endX-dir*0.10F,endY+h,endZ-h,
                    endX-dir*0.10F,endY+h,endZ+h,
                    endX-dir*0.10F,endY-h,endZ+h,(int)(42F*brightness));
            }
        }
        if (forward >= maximum-0.10F && !(Dev55L13SoftBeam.active() && primary && !shayDown && !shay2YUp)) {
            if (shay2YUp) {
                // Groundward endpoint wash is perpendicular to the real lens axis.
                doubleSideQuad(v,m,
                    endX-dir*along*1.25F+nX*width*1.20F,endY,endZ-VB_SHAY_2_LENS_OUTWARD*1.25F+nZ*width*1.20F,
                    endX-dir*along*1.25F-nX*width*1.20F,endY,endZ-VB_SHAY_2_LENS_OUTWARD*1.25F-nZ*width*1.20F,
                    endX+dir*along*0.45F-nX*width*1.35F,endY,endZ+VB_SHAY_2_LENS_OUTWARD*0.45F-nZ*width*1.35F,
                    endX+dir*along*0.45F+nX*width*1.35F,endY,endZ+VB_SHAY_2_LENS_OUTWARD*0.45F+nZ*width*1.35F,(int)(23F*brightness));
            } else if(traincraft.debug.Dev55L5Optics.turned(lateral)) {
                float pathX=dir*along,pathZ=traincraft.debug.Dev55L5Optics.sidewaysFactor(lateral);
                doubleSideQuad(v,m,
                    endX-pathX*1.25F+nX*width*1.20F,endY,endZ-pathZ*1.25F+nZ*width*1.20F,
                    endX-pathX*1.25F-nX*width*1.20F,endY,endZ-pathZ*1.25F-nZ*width*1.20F,
                    endX+pathX*0.45F-nX*width*1.35F,endY,endZ+pathZ*0.45F-nZ*width*1.35F,
                    endX+pathX*0.45F+nX*width*1.35F,endY,endZ+pathZ*0.45F+nZ*width*1.35F,(int)(23F*brightness));
            } else {
                doubleSideQuad(v,m,
                    endX-dir*1.25F,endY,endZ-width*1.20F,
                    endX-dir*1.25F,endY,endZ+width*1.20F,
                    endX+dir*0.45F,endY,endZ+width*1.35F,
                    endX+dir*0.45F,endY,endZ-width*1.35F,(int)(23F*brightness));
            }
        }
    }
    /** Shared low-opacity coverage wedges fill the lateral gap between the
     * central beam and any real left/right auxiliary lamp sources. */
    private static void fillGaps(VertexConsumer v,Matrix4f m,Marker center,
                                 Marker auxiliary,float dir,float sign,float ground,float brightness,float reach) {
        // The old fan-gap wedge must NOT remain visible when the side beam is straight.
        if (traincraft.debug.Dev55L5Optics.turned(sign) ||
            Math.abs(traincraft.debug.Dev55L5Optics.sideSlope(sign))<0.001F) return;
        float x=(float)center.x()+dir*traincraft.debug.Dev55L5Optics.originForward(0F);
        float y=(float)center.y(), z=(float)center.z()+traincraft.debug.Dev55L5Optics.originSide(0F);
        float floor=Math.max(y+0.40F,ground);
        floor += reach * traincraft.debug.Dev55L4AimTuner.deltaModelYPerBlock();
        float startX=x+dir*0.11F;
        float farX=x+dir*reach;
        float innerZ=z+sign*0.40F;
        float gapFactor = Math.abs(traincraft.debug.Dev55L5Optics.sideSlope(sign)-sign*0.509525F)<0.0001F
                ? sign*0.338F : traincraft.debug.Dev55L5Optics.sideSlope(sign)*0.66336F;
        float outerZ=(float)auxiliary.z()+traincraft.debug.Dev55L5Optics.originSide(sign)+reach*gapFactor;
        // Double-sided trapezoid, modest alpha to blend beams without a solid bar.
        doubleSideQuad(v,m,
            startX,y,z,startX,y,(float)auxiliary.z(),
            farX,floor,outerZ,farX,floor,innerZ,(int)(20F*brightness));
    }
    private static void doubleSideQuad(VertexConsumer v,Matrix4f m,
                                      float ax,float ay,float az,float bx,float by,float bz,
                                      float cx,float cy,float cz,float dx,float dy,float dz,int alpha) {
        vertex(v,m,ax,ay,az,255,224,155,alpha);
        vertex(v,m,bx,by,bz,255,224,155,alpha);
        vertex(v,m,cx,cy,cz,255,212,137,0);
        vertex(v,m,dx,dy,dz,255,212,137,0);
        vertex(v,m,dx,dy,dz,255,212,137,0);
        vertex(v,m,cx,cy,cz,255,212,137,0);
        vertex(v,m,bx,by,bz,255,224,155,alpha);
        vertex(v,m,ax,ay,az,255,224,155,alpha);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,float x,float y,float z,int r,int g,int b,int a) {
        v.vertex(m,x,y,z).color(r,g,b,a).endVertex();
    }
    private static void quad(VertexConsumer v,Matrix4f m,float x0,float y0,float z0,
                             float x1,float y1,float z1,int r,int g,int b,int a,boolean transverse) {
        vertex(v,m,x0,y0,z0,r,g,b,a);
        vertex(v,m,transverse?x0:x1,y1,transverse?z1:z0,r,g,b,a);
        vertex(v,m,x1,y1,z1,r,g,b,a);
        vertex(v,m,transverse?x1:x0,y0,transverse?z0:z1,r,g,b,a);
    }
    private static void fan(VertexConsumer v,Matrix4f m,float x0,float x1,float y,float z,float dir,float brightness) {
        // Backface and depth can obscure the wash on some terrain; never forces glow through solids.
        float spread=0.9F;
        for(int i=0;i<7;i++) {
            float a=(i/7.0F)*6.2831853F, b=((i+1)/7.0F)*6.2831853F;
            float ex1=x1+0.24F*(float)Math.cos(a)*dir,ez1=z+spread*(float)Math.sin(a);
            float ex2=x1+0.24F*(float)Math.cos(b)*dir,ez2=z+spread*(float)Math.sin(b);
            vertex(v,m,x0,y,z,250,206,124,(int)(40*brightness));
            vertex(v,m,ex1,y,ez1,250,206,124,0);
            vertex(v,m,ex2,y,ez2,250,206,124,0);
            vertex(v,m,x0,y,z,250,206,124,(int)(40*brightness));
        }
    }
}
