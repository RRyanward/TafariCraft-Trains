package traincraft.client.renderer.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import com.mojang.blaze3d.platform.InputConstants;
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * DEV54H15P: same H15 keyboard workflow for native ModelPart locomotives and
 * verified legacy TCM locomotives. LegacyStaticMesh delegates only when a
 * source-qualified native adapter owns the selected nearby locomotive.
 * Each new native model requires a VERIFIED adapter; no inferred wheel positions.
 * H15P OFF leaves legacy H15M and H15N/H15O unchanged.
 */
public final class Dev54H15PNativeFleetEditor {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String SELECTOR="DEV54H15P-UNIFIED-NATIVE-ROD-EDITOR-ON.txt";
    private static final String VALUE="DEV54H15P-ON-V1";
    private static final String HEADER="DEV54H15P-NATIVE-RUNNING-GEAR-V1";
    private static final String MODEL="locomotive_steam_forney";
    private static final ResourceLocation STEEL=new ResourceLocation("minecraft","textures/block/iron_block.png");
    private static final float DRIVER_Y=4.5f,FRONT_Z=-5.5f,REAR_Z=5.5f;
    private static final float AXLE_SIDE=7.35f;
    private static final boolean ON=selector();
    // H15Q diagnostics: OFF is byte-equivalent to H15P live visuals and controls.
    private static final boolean Q_ON=qSelector();
    // H15R OFF preserves H15Q geometry byte-for-byte in beam().
    private static final boolean R_ON=rSelector();
    private static boolean rRenderLogged;
    private static int qInputRevision,qRenderedRevision=-1,qRenderedEntity=-1;
    private static boolean editing,preview,priorToggle;
    private static int entityId=-1,stage=0,wheelIndex=0;
    private static Config saved, draft;
    private static boolean loaded;
    private static final boolean[] last=new boolean[512];
    private static final float STEP=.25f,FINE=.0625f;

    // Default values in native ModelPart PIXELS, not TCM world units.
    private record Config(float pinY,float pinZ,float pistonY,float length,float pistonX,
                          float width,float inset,boolean hideOld,boolean hideGenerated,
                          boolean coupling,boolean caps,boolean mirrored,boolean reversed){
        static Config defaults(){return new Config(0f,1.65f,9.25f,15.5f,4.75f,
                .76f,0f,false,false,false,true,false,false);}
        boolean valid(){
            if(!Float.isFinite(pinY)||!Float.isFinite(pinZ)||!Float.isFinite(pistonY)||
                    !Float.isFinite(length)||!Float.isFinite(pistonX)||
                    !Float.isFinite(width)||!Float.isFinite(inset))return false;
            float radius=(float)Math.hypot(pinY,pinZ);
            if(radius<.85f||radius>2.25f||pistonY<5f||pistonY>12f||
                    length<12f||length>19f||pistonX<3.5f||pistonX>7.3f||
                    width<.4f||width>1.25f||inset<-.375f||inset>.5f)return false;
            for(int i=0;i<64;i++){
                float phase=(float)(i*Math.PI*2d/64d);
                float pinHeight=DRIVER_Y+pinY*(float)Math.cos(phase)+pinZ*(float)Math.sin(phase);
                float pinDepth=FRONT_Z-pinY*(float)Math.sin(phase)+pinZ*(float)Math.cos(phase);
                float dy=pistonY-pinHeight;
                float reach=length*length-dy*dy;
                if(reach<=0)return false;
                float end=pinDepth+(reversed?1f:-1f)*(float)Math.sqrt(reach);
                if(!Float.isFinite(end)||(!reversed && (end< -24.5f||end> -12.0f)))return false;
                if(reversed && (end< 12.0f||end> 26.0f))return false;
            }
            return true;
        }
    }
    private Dev54H15PNativeFleetEditor(){}
    public static boolean enabled(){return ON;}
    private static boolean rSelector(){try{
        Path p=FMLPaths.GAMEDIR.get().resolve("DEV54H15R-FORNEY-SOLID-RODS-ON.txt");
        return Files.isRegularFile(p)&&Files.readString(p,StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H15R-ON-V1");
    }catch(Exception ex){return false;}}
    private static boolean qSelector(){try{
        Path p=FMLPaths.GAMEDIR.get().resolve("DEV54H15Q-NATIVE-MIRROR-DEBUG-ON.txt");
        return Files.isRegularFile(p)&&Files.readString(p,StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H15Q-ON-V1");
    }catch(Exception ex){return false;}}
    public static boolean isEditing(){return ON&&editing;}
    private static boolean selector(){try{
        Path p=FMLPaths.GAMEDIR.get().resolve(SELECTOR);
        return Files.isRegularFile(p)&&Files.readString(p,StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals(VALUE);
    }catch(Exception ex){return false;}}
    private static Path file(){return FMLPaths.CONFIGDIR.get().resolve("tafaricraft-dev54h15p-native-running-gear.tsv");}
    private static void load(){if(loaded)return;loaded=true;saved=Config.defaults();
        try{
            if(Files.isRegularFile(file())){
                List<String> l=Files.readAllLines(file(),StandardCharsets.UTF_8);
                if(l.size()!=2||!HEADER.equals(l.get(0).trim()))throw new IllegalArgumentException("Header/row count");
                String[] c=l.get(1).split("\\t",-1);
                if(c.length!=14||!MODEL.equals(c[0]))throw new IllegalArgumentException("Fields/model");
                Config found=new Config(Float.parseFloat(c[1]),Float.parseFloat(c[2]),
                        Float.parseFloat(c[3]),Float.parseFloat(c[4]),Float.parseFloat(c[5]),
                        Float.parseFloat(c[6]),Float.parseFloat(c[7]),Boolean.parseBoolean(c[8]),
                        Boolean.parseBoolean(c[9]),Boolean.parseBoolean(c[10]),Boolean.parseBoolean(c[11]),
                        Boolean.parseBoolean(c[12]),Boolean.parseBoolean(c[13]));
                if(!found.valid())throw new IllegalArgumentException("Invalid native joint envelope");
                saved=found;
            }else{
                // Non-mutating migration preview from H15O, when installed.
                Path prior=FMLPaths.CONFIGDIR.get().resolve("tafaricraft-dev54h15o-forney-native-piston.tsv");
                if(Files.isRegularFile(prior)){
                    List<String> l=Files.readAllLines(prior,StandardCharsets.UTF_8);
                    if(l.size()==2&&"DEV54H15O-FORNEY-NATIVE-PISTON-V1".equals(l.get(0).trim())){
                        String[] c=l.get(1).split("\\t",-1);
                        if(c.length==5&&MODEL.equals(c[0])){
                            Config v=new Config(0f,1.65f,Float.parseFloat(c[1]),
                                    Float.parseFloat(c[2]),Float.parseFloat(c[3]),Float.parseFloat(c[4]),
                                    0f,false,false,false,true,false,false);
                            if(v.valid()){saved=v;LOG.info("[DEV54H15P-MIGRATION-PREVIEW] H15O file read only");}
                        }
                    }
                }
            }
            LOG.info("[DEV54H15P-LOADED] model={} saved={} file={}",MODEL,saved,file());
        }catch(Exception ex){LOG.warn("[DEV54H15P-LOAD-HOLD] default safe editor settings; bad file NOT modified",ex);saved=Config.defaults();}
        draft=saved;
    }
    private static void msg(String s){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)
        mc.player.displayClientMessage(Component.literal("H15 Rod Builder [NATIVE Forney]: "+s),true);}
    private static ForneySteamLocomotive nearest(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return null;
        ForneySteamLocomotive best=null;double d=12d*12d;
        for(ForneySteamLocomotive f:mc.level.getEntitiesOfClass(ForneySteamLocomotive.class,
                mc.player.getBoundingBox().inflate(12d))){double n=f.distanceToSqr(mc.player);
            if(n<d){best=f;d=n;}}
        return best;
    }
    private static boolean down(long w,int k){return InputConstants.isKeyDown(w,k);}
    private static boolean edge(long w,int k){boolean now=down(w,k);if(k<0||k>=last.length)return false;
        boolean e=now&&!last[k];last[k]=now;return e;}
    private static void stop(){editing=false;preview=false;stage=0;entityId=-1;draft=saved;}
    private static String wheelName(){return (wheelIndex/2==0?"FRONT":"REAR")+(wheelIndex%2==0?" LEFT":" RIGHT");}
    private static boolean write(){if(draft==null||!draft.valid())return false;
        Path p=file(),tmp=null;
        try{Files.createDirectories(p.getParent());
            if(Files.isRegularFile(p)){
                Path backups=p.getParent().resolve("dev54h15p-native-backups");Files.createDirectories(backups);
                String tag=Long.toString(System.currentTimeMillis());
                Files.copy(p,backups.resolve("forney-"+tag+".tsv"));
            }
            String row=String.join("\t",MODEL,Float.toString(draft.pinY),Float.toString(draft.pinZ),
                Float.toString(draft.pistonY),Float.toString(draft.length),Float.toString(draft.pistonX),
                Float.toString(draft.width),Float.toString(draft.inset),Boolean.toString(draft.hideOld),
                Boolean.toString(draft.hideGenerated),Boolean.toString(draft.coupling),
                Boolean.toString(draft.caps),Boolean.toString(draft.mirrored),Boolean.toString(draft.reversed));
            tmp=Files.createTempFile(p.getParent(),"h15p-native-",".tmp");
            Files.writeString(tmp,HEADER+"\n"+row+"\n",StandardCharsets.UTF_8);
            try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
            saved=draft;LOG.info("[DEV54H15P-SAVED] model={} file={} stage={} oldHidden={} newCoupling={}",
                    MODEL,p,stage,saved.hideOld,saved.coupling);return true;
        }catch(Exception e){LOG.error("[DEV54H15P-SAVE-HOLD] source and saved designs unchanged",e);return false;}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
    }
    /** Called before H15's legacy key handler. True means native owns this tick. */
    public static boolean tick(long window){
        if(!ON)return false;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null)return false;
        boolean ctrl=down(window,GLFW.GLFW_KEY_LEFT_CONTROL)||down(window,GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift=down(window,GLFW.GLFW_KEY_LEFT_SHIFT)||down(window,GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean toggling=ctrl&&shift&&down(window,GLFW.GLFW_KEY_R);
        ForneySteamLocomotive f=nearest();
        // Never claim unrelated native models. Future adapters join registry after verification.
        if(toggling&&!priorToggle&&((editing)||(f!=null))){
            load();
            if(editing){stop();msg("EDITOR OFF; unsaved changes discarded");}
            else{entityId=f.getId();editing=true;stage=0;draft=saved;preview=false;wheelIndex=0;
                if(Q_ON){qInputRevision++;LOG.info("[DEV54H15Q-EDITOR] entity={} native=Forney mirrorSides=true inputRev={} H15P_source=guarded",entityId,qInputRevision);}
                msg(Q_ON?"EDITOR ON | both sides MIRRORED | G twice, L load, arrows adjust, Enter save":
                        "EDITOR ON | PgUp/PgDn wheels, G pin, G piston, L saved");
                LOG.info("[DEV54H15P-EDITOR-ON] adapter={} wheels=4 nativeModelPart=true",MODEL);}
        }
        priorToggle=toggling;
        if(!editing)return toggling&&f!=null;
        if(f==null||f.getId()!=entityId){msg("Stay within 12 blocks of selected Forney");return true;}
        if(edge(window,GLFW.GLFW_KEY_PAGE_UP)||edge(window,GLFW.GLFW_KEY_PAGE_DOWN)){
            wheelIndex=Math.floorMod(wheelIndex+(down(window,GLFW.GLFW_KEY_PAGE_UP)?1:-1),4);
            msg("VERIFIED axle "+(wheelIndex+1)+"/4 "+wheelName());
        }
        if(edge(window,GLFW.GLFW_KEY_G)){
            if(wheelIndex>=2){msg("Rear truck wheels preserved; select FRONT driving wheel for piston");}
            else if(stage==0){stage=1;msg("G PIN on "+wheelName()+"; G again creates piston link");}
            else if(stage==1){stage=2;preview=true;
                if(Q_ON){qInputRevision++;LOG.info("[DEV54H15Q-PREVIEW] entity={} stage=2 BOTH sides mirror=true",entityId);}
                msg(Q_ON?"G PISTON ready | bright orange guides on BOTH sides; arrows/Shift+arrows adjust":
                    "G PISTON ready; arrows position, Shift+arrows pin, K/C new gear, Enter save");}
            else{stage=0;preview=false;msg("G reset point selection; saved file unchanged");}
        }
        if(edge(window,GLFW.GLFW_KEY_L)){
            if(stage!=0){msg("Backspace discard draft before L");}
            else{load();draft=saved;stage=2;preview=true;
                if(Q_ON){qInputRevision++;LOG.info("[DEV54H15Q-LOAD] entity={} mirrored geometry on both sides",entityId);}
                msg(Q_ON?"L loaded: BOTH sides mirrored; orange guides now visible":
                        "L loaded saved design; adjust then Enter save");}
        }
        if(edge(window,GLFW.GLFW_KEY_BACKSPACE)){
            stop();msg("Discarded draft; accepted rods unchanged");return true;
        }
        if(edge(window,GLFW.GLFW_KEY_ENTER)||edge(window,GLFW.GLFW_KEY_KP_ENTER)){
            if(stage!=2){msg("G crankpin, G piston required before saving");}
            else if(!draft.valid()){msg("Full-cycle piston geometry INVALID; no save");}
            else if(write()){stop();msg("SAVED native Forney running gear");}
            else msg("SAVE HOLD: check log; previous file preserved");
            return true;
        }
        if(edge(window,GLFW.GLFW_KEY_Z)){preview=!preview;msg("Z preview "+(preview?"ON":"OFF"));}
        if(edge(window,GLFW.GLFW_KEY_V))msg("V HOLD: Forney has verified FRONT cylinder only; no guessed rear piston");
        if(stage==2){
            int step=(shift?1:4);float px=step*FINE;
            if(edge(window,GLFW.GLFW_KEY_K)){Config a=draft;draft=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,!a.coupling,a.caps,a.mirrored,a.reversed);msg("K new coupling rods "+(draft.coupling?"ON":"OFF"));}
            if(edge(window,GLFW.GLFW_KEY_C)){Config a=draft;draft=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,!a.caps,a.mirrored,a.reversed);msg("C new crankpins "+(draft.caps?"ON":"OFF"));}
            if(edge(window,GLFW.GLFW_KEY_O)){Config a=draft;draft=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset,!a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);msg("O native original coupling rods "+(draft.hideOld?"HIDDEN":"VISIBLE"));}
            if(edge(window,GLFW.GLFW_KEY_I))msg("I no separate legacy generated rods in native Forney; H15N overlay replaced");
            if(edge(window,GLFW.GLFW_KEY_X))msg("X HOLD: native Forney wheel overlays share accepted axle phase; do not desync rods");
            Config a=draft,b=null;
            if(edge(window,GLFW.GLFW_KEY_UP))b=shift?change(a,a.pinY+px,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed):change(a,a.pinY,a.pinZ,a.pistonY+px,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_DOWN))b=shift?change(a,a.pinY-px,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed):change(a,a.pinY,a.pinZ,a.pistonY-px,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_LEFT))b=shift?change(a,a.pinY,a.pinZ-px,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed):change(a,a.pinY,a.pinZ,a.pistonY,a.length-px,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_RIGHT))b=shift?change(a,a.pinY,a.pinZ+px,a.pistonY,a.length,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed):change(a,a.pinY,a.pinZ,a.pistonY,a.length+px,a.pistonX,a.width,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_LEFT_BRACKET))b=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width-px,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_RIGHT_BRACKET))b=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width+px,a.inset,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_COMMA))b=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset+FINE,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(edge(window,GLFW.GLFW_KEY_PERIOD))b=change(a,a.pinY,a.pinZ,a.pistonY,a.length,a.pistonX,a.width,a.inset-FINE,a.hideOld,a.hideGenerated,a.coupling,a.caps,a.mirrored,a.reversed);
            if(b!=null){if(b.valid()){draft=b;
                if(Q_ON){qInputRevision++;LOG.info("[DEV54H15Q-INPUT] rev={} id={} mirroredSides=true pinY={} pinZ={} pistonY={} length={} pistonX={} inset={} width={}",
                    qInputRevision,entityId,b.pinY,b.pinZ,b.pistonY,b.length,b.pistonX,b.inset,b.width);}
                msg(String.format(Locale.ROOT,
                    Q_ON?"BOTH SIDES | pin=(%.2f,%.2f) pistonY=%.2f length=%.2f depth=%.3f width=%.2f":
                    "pin=(%.2f,%.2f) pistonY=%.2f length=%.2f depth=%.3f width=%.2f",
                    b.pinY,b.pinZ,b.pistonY,b.length,b.inset,b.width));}
                else msg("Adjustment outside verified full-cycle geometry; HOLD");}
        }
        // Alt+arrow never moves verified native wheel axles. Frozen original wheel geometry.
        if((down(window,GLFW.GLFW_KEY_LEFT_ALT)||down(window,GLFW.GLFW_KEY_RIGHT_ALT))&&
                (down(window,GLFW.GLFW_KEY_LEFT)||down(window,GLFW.GLFW_KEY_RIGHT)||
                        down(window,GLFW.GLFW_KEY_UP)||down(window,GLFW.GLFW_KEY_DOWN)))
            msg("Verified native axle centers are frozen; use Shift+arrows for crankpin");
        return true;
    }
    private static Config change(Config a,float py,float pz,float y,float len,float x,float wid,float depth,
                                  boolean old,boolean gen,boolean couple,boolean caps,boolean mirror,boolean rev){
        return new Config(py,pz,y,len,x,wid,depth,old,gen,couple,caps,mirror,rev);
    }
    /** Source-qualified two exact native Forney coupling bars, no other ModelParts hidden. */
    public static void renderNativeModel(ModelPart model,PoseStack pose,VertexConsumer v,int light,int overlay,
                                         ForneySteamLocomotive entity){
        if(!ON){model.render(pose,v,light,overlay);return;}
        load();Config c=(editing&&entity!=null&&entity.getId()==entityId)?draft:saved;
        if(c==null||!c.hideOld){model.render(pose,v,light,overlay);return;}
        ModelPart left=null,right=null;boolean lv=false,rv=false,masked=false;
        try{
            left=model.getChild("part_065");right=model.getChild("part_067");
            lv=left.visible;rv=right.visible;
            left.visible=false;right.visible=false;masked=true;
            model.render(pose,v,light,overlay);
        }catch(Exception ex){
            LOG.warn("[DEV54H15P-NATIVE-MASK-HOLD] qualified Forney part missing; original model retained",ex);
            if(masked){left.visible=lv;right.visible=rv;}
            model.render(pose,v,light,overlay);
        }finally{if(masked){left.visible=lv;right.visible=rv;}}
    }
    /** Called instead of H15O native piston overlay only when H15P is ON. */
    public static void render(ForneySteamLocomotive f,float partialTick,PoseStack pose,MultiBufferSource buffers,int light){
        if(!ON||f==null)return;load();
        Config c=(editing&&f.getId()==entityId)?draft:saved;
        if(c==null)return;
        if(R_ON&&!rRenderLogged){
            rRenderLogged=true;
            LOG.info("[DEV54H15R-SOLID-READY] Forney ModelPart wheels UNCHANGED; 3D octagonal bevels + 2-sided mirroring; editor stage={} Q_DEBUG={}",stage,Q_ON);
        }
        float nativePhase=(float)RollingStockWheelAnimation.sampleNativePhase(f,partialTick);
        if(!Float.isFinite(nativePhase))return;
        // Press Z to inspect the joint over a full cycle while parked.
        float phase=(editing&&preview&&f.getId()==entityId)?
                (f.tickCount+partialTick)*.09f:nativePhase;
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL));
        boolean qShow=Q_ON&&editing&&f.getId()==entityId&&stage==2;
        int qDrawn=0;
        float qLeftX=Float.NaN,qRightX=Float.NaN;
        float qLeftP=Float.NaN,qRightP=Float.NaN;
        // Same draft is reflected across the native X=0 model centerline.
        // Never add a new side-specific phase offset; accepted Forney wheels stay frozen.
        for(int side:new int[]{-1,1}){
            float a=phase+(c.mirrored&&side<0?(float)(Math.PI/2d):0f);
            float y=DRIVER_Y+c.pinY*(float)Math.cos(a)+c.pinZ*(float)Math.sin(a);
            float z=FRONT_Z-c.pinY*(float)Math.sin(a)+c.pinZ*(float)Math.cos(a);
            float dy=c.pistonY-y;
            float reach=c.length*c.length-dy*dy;
            if(reach<=0)continue;
            float end=z+(c.reversed?1f:-1f)*(float)Math.sqrt(reach);
            if(!Float.isFinite(end)||(!c.reversed&&(end < -24.5f||end> -12f)))continue;
            float wx=side*(AXLE_SIDE-c.inset)/16f;
            float px=side*(c.pistonX-c.inset)/16f;
            if(stage==2||!editing||entityId!=f.getId()){
                beam(pose,out,light,wx,y,z,px,c.pistonY,end,c.width,195,195,198,side);
                beam(pose,out,light,px,c.pistonY-.44f,end,px,c.pistonY+.44f,end,.7f,135,140,145,side);
                if(c.coupling){float backY=DRIVER_Y+c.pinY*(float)Math.cos(a)+c.pinZ*(float)Math.sin(a);
                    float backZ=REAR_Z-c.pinY*(float)Math.sin(a)+c.pinZ*(float)Math.cos(a);
                    beam(pose,out,light,wx,y,z,wx,backY,backZ,c.width,192,192,194,side);
                }
                if(c.caps)beam(pose,out,light,wx+side*.022f,y-.34f,z,wx+side*.022f,y+.34f,z,.62f,140,145,150,side);
            }
            if(editing&&f.getId()==entityId){
                float markerX=side*.025f;
                beam(pose,out,light,px+markerX,c.pistonY-.75f,end,px+markerX,c.pistonY+.75f,end,.36f,248,166,61,side);
            }
            if(qShow){
                // Full-bright outboard PREVIEW guides: don't move accepted wheels,
                // cylinders or real piston endpoints. Orange = debug only.
                float guideX=side*(AXLE_SIDE+1.5f-c.inset)/16f;
                if(!R_ON){
                    // Frozen Q guide, shown only when H15R is disabled.
                    beam(pose,out,0x00F000F0,guideX,y,z,guideX,c.pistonY,end,
                            Math.max(.90f,c.width),255,142,31,side);
                    beam(pose,out,0x00F000F0,guideX,y-.90f,z,guideX,y+.90f,z,
                            .64f,255,235,62,side);
                    beam(pose,out,0x00F000F0,guideX,c.pistonY-.95f,end,
                            guideX,c.pistonY+.95f,end,.64f,255,235,62,side);
                }else{
                    // H15R: no wide orange plane over the new solid steel rod.
                    // Only slim, outboard joint markers remain during editing.
                    beam(pose,out,0x00F000F0,guideX,y-.64f,z,guideX,y+.64f,z,
                            .24f,255,165,25,side);
                    beam(pose,out,0x00F000F0,guideX,c.pistonY-.64f,end,
                            guideX,c.pistonY+.64f,end,.24f,255,212,58,side);
                }
                if(side<0){qLeftX=wx;qLeftP=px;}else{qRightX=wx;qRightP=px;}
                qDrawn++;
            }
        }
        // One audit per input revision, not every rendered frame.
        if(Q_ON&&editing&&f.getId()==entityId&&
                (qRenderedRevision!=qInputRevision||qRenderedEntity!=entityId)){
            LOG.info("[DEV54H15Q-RENDER-PAIR] rev={} entity={} phase={} stage={} preview={} "+
                    "leftCrankX={} rightCrankX={} leftPistonX={} rightPistonX={} guidesDrawn={} "+
                    "mirrorX={} realRodVisible={}",qInputRevision,entityId,phase,stage,preview,
                    qLeftX,qRightX,qLeftP,qRightP,qDrawn,
                    qDrawn==2&&Math.abs(qLeftX+qRightX)<.0001f&&
                            Math.abs(qLeftP+qRightP)<.0001f,stage==2);
            qRenderedRevision=qInputRevision;qRenderedEntity=entityId;
        }
    }
    private static void beam(PoseStack stack,VertexConsumer v,int light,float xa,float ya,float za,
                             float xb,float yb,float zb,float width,int r,int g,int b,int side){
        if(R_ON){
            solidBeam(stack,v,light,xa,ya,za,xb,yb,zb,width,r,g,b);
            return;
        }
        // Original H15Q flat geometry (reversible when H15R is disabled).
        float dy=yb-ya,dz=zb-za;float len=(float)Math.hypot(dy,dz);
        if(len<.001f)return;
        float py=-dz/len*width*.5f,pz=dy/len*width*.5f,inv=1f/16f;
        Matrix4f m=stack.last().pose();Matrix3f n=stack.last().normal();
        vertex(v,m,n,xa,(ya+py)*inv,(za+pz)*inv,0,0,light,r,g,b,side);
        vertex(v,m,n,xa,(ya-py)*inv,(za-pz)*inv,0,1,light,r,g,b,side);
        vertex(v,m,n,xb,(yb-py)*inv,(zb-pz)*inv,1,1,light,r,g,b,side);
        vertex(v,m,n,xb,(yb+py)*inv,(zb+pz)*inv,1,0,light,r,g,b,side);
    }
    /**
     * H15R: a genuine solid 3D rod, not H15Q's one-sided quad.
     * Endpoints share the EXISTING verified native geometry in block units / model pixels.
     * Both ends have eight beveled perimeter vertices, eight four-vertex side faces
     * and capped ends. Nothing here changes wheel phase, axle or piston positions.
     */
    private static void solidBeam(PoseStack stack,VertexConsumer v,int light,
                                  float xa,float ya,float za,float xb,float yb,float zb,
                                  float width,int red,int green,int blue){
        final float pixel=1f/16f;
        float ax=xa, ay=ya*pixel, az=za*pixel;
        float bx=xb, by=yb*pixel, bz=zb*pixel;
        float tx=bx-ax,ty=by-ay,tz=bz-az;
        float length=(float)Math.sqrt(tx*tx+ty*ty+tz*tz);
        if(length<.0001f||!Float.isFinite(length))return;
        tx/=length;ty/=length;tz/=length;
        // In-plane normal: perpendicular to the rod in its Y/Z projection.
        float ux=0f,uy=-tz,uz=ty;
        float ulen=(float)Math.sqrt(uy*uy+uz*uz);
        if(ulen<.001f){ux=0f;uy=1f;uz=0f;ulen=1f;}
        ux/=ulen;uy/=ulen;uz/=ulen;
        // Second axis gives actual thickness out of the locomotive's side.
        float vx=ty*uz-tz*uy;
        float vy=tz*ux-tx*uz;
        float vz=tx*uy-ty*ux;
        float vlen=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);
        if(vlen<.0001f)return;
        vx/=vlen;vy/=vlen;vz/=vlen;
        float halfWidth=.5f*width*pixel;
        float halfThick=.375f*width*pixel;
        // Chamfered rectangle: 8-sided cross-section, 3D thickness & bevels.
        float[] u={-.32f,.32f,.5f,.5f,.32f,-.32f,-.5f,-.5f};
        float[] w={-.5f,-.5f,-.32f,.32f,.5f,.5f,.32f,-.32f};
        float[][] start=new float[8][3],end=new float[8][3];
        for(int i=0;i<8;i++){
            float a=u[i]*2f*halfWidth,b=w[i]*2f*halfThick;
            float dx=ux*a+vx*b,dy=uy*a+vy*b,dz=uz*a+vz*b;
            start[i][0]=ax+dx;start[i][1]=ay+dy;start[i][2]=az+dz;
            end[i][0]=bx+dx;end[i][1]=by+dy;end[i][2]=bz+dz;
        }
        Matrix4f m=stack.last().pose();Matrix3f nm=stack.last().normal();
        for(int i=0;i<8;i++){
            int j=(i+1)&7;
            float nu=(u[i]+u[j])*(halfWidth);
            float nw=(w[i]+w[j])*(halfThick);
            float nx=ux*nu+vx*nw,ny=uy*nu+vy*nw,nz=uz*nu+vz*nw;
            float nlen=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(nlen<.0001f)continue;
            nx/=nlen;ny/=nlen;nz/=nlen;
            // Subtle bevel shading, keeping user-selected width and solid steel visible.
            float shade=(i==2||i==3)?1.0f:(i==0||i==4?.88f:.76f);
            int rr=Math.min(255,Math.round(red*shade));
            int gg=Math.min(255,Math.round(green*shade));
            int bb=Math.min(255,Math.round(blue*shade));
            solidVertex(v,m,nm,start[i],0f,0f,light,rr,gg,bb,nx,ny,nz);
            solidVertex(v,m,nm,start[j],0f,1f,light,rr,gg,bb,nx,ny,nz);
            solidVertex(v,m,nm,end[j],1f,1f,light,rr,gg,bb,nx,ny,nz);
            solidVertex(v,m,nm,end[i],1f,0f,light,rr,gg,bb,nx,ny,nz);
        }
        // End caps: triangles as degenerate QUADS for the entity-cutout buffer.
        float[] ac={ax,ay,az},bc={bx,by,bz};
        for(int i=0;i<8;i++){
            int j=(i+1)&7;
            solidVertex(v,m,nm,ac,.5f,.5f,light,red,green,blue,-tx,-ty,-tz);
            solidVertex(v,m,nm,start[j],0f,1f,light,red,green,blue,-tx,-ty,-tz);
            solidVertex(v,m,nm,start[i],0f,0f,light,red,green,blue,-tx,-ty,-tz);
            solidVertex(v,m,nm,ac,.5f,.5f,light,red,green,blue,-tx,-ty,-tz);
            solidVertex(v,m,nm,bc,.5f,.5f,light,red,green,blue,tx,ty,tz);
            solidVertex(v,m,nm,end[i],0f,0f,light,red,green,blue,tx,ty,tz);
            solidVertex(v,m,nm,end[j],0f,1f,light,red,green,blue,tx,ty,tz);
            solidVertex(v,m,nm,bc,.5f,.5f,light,red,green,blue,tx,ty,tz);
        }
    }
    private static void solidVertex(VertexConsumer v,Matrix4f m,Matrix3f n,float[] pos,
                                    float u,float uv,int light,int r,int g,int b,
                                    float nx,float ny,float nz){
        v.vertex(m,pos[0],pos[1],pos[2]).color(r,g,b,255)
                .uv(u,uv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n,nx,ny,nz).endVertex();
    }
    private static void vertex(VertexConsumer v,Matrix4f m,Matrix3f n,float x,float y,float z,float u,
                               float uv,int light,int r,int g,int b,int side){
        v.vertex(m,x,y,z).color(r,g,b,255).uv(u,uv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n,side,0,0).endVertex();
    }
}
