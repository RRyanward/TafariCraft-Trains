package traincraft.client.renderer.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DEV54H15O: native ModelPart Forney piston-only in-game designer.
 * Existing driver and rear-truck wheel animation remain completely unchanged.
 * H15O selector OFF = byte-for-byte equivalent H15N geometry path.
 * H15O selector ON = editable crosshead height / connecting length / inset / width.
 * The projecting cylinder-front piston shaft is deliberately not rendered.
 * A tapered side-view connecting rod terminates inside the native piston housing.
 * This is Forney-specific, not a generalized all-ModelPart wheel selector.
 */
public final class ForneyPistonLinkPilotRenderer {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String MARKER="DEV54H15N-FORNEY-PISTON-LINK-ON.txt";
    private static final String DESIGNER_MARKER="DEV54H15O-FORNEY-NATIVE-EDITOR-ON.txt";
    private static final String DESIGN_FILE="tafaricraft-dev54h15o-forney-native-piston.tsv";
    private static final String DESIGN_HEADER="DEV54H15O-FORNEY-NATIVE-PISTON-V1";
    private static final AtomicBoolean REPORTED=new AtomicBoolean();
    private static final AtomicBoolean DESIGN_REPORTED=new AtomicBoolean();
    private static final float DRIVER_Y=4.5F;
    private static final float DRIVER_Z=-5.5F;
    private static final float CRANK_RADIUS=1.65F;
    private static final float CROSSHEAD_Y=7.5F;
    private static final float CONNECTING_LENGTH=13.5F;
    private static final float CYLINDER_FRONT_Z=-24.0F;
    private static final float OUTER_X=7.35F;
    private static final ResourceLocation STEEL=new ResourceLocation(
            "minecraft","textures/block/iron_block.png");
    private static final boolean ENABLED=markerEnabled(MARKER,"DEV54H15N-ON-V1");
    private static final boolean DESIGNER_ENABLED=markerEnabled(DESIGNER_MARKER,"DEV54H15O-ON-V1");

    // All coordinates are native ModelPart pixels. Saved in run/config independently
    // of the H15 legacy-TSV files, so accepted meshes remain frozen.
    private record Settings(float crossheadY,float rodLength,float crossheadX,float rodWidth) {
        static Settings defaults(){return new Settings(9.25F,15.50F,4.75F,0.76F);}
        boolean valid(){return Float.isFinite(crossheadY) && crossheadY>=5.0F && crossheadY<=12.0F
                && Float.isFinite(rodLength) && rodLength>=12.0F && rodLength<=17.0F
                && Float.isFinite(crossheadX) && crossheadX>=3.50F && crossheadX<=7.30F
                && Float.isFinite(rodWidth) && rodWidth>=0.40F && rodWidth<=1.25F;}
    }
    private static Settings saved=load();
    private static Settings working=saved;
    private static boolean editing;
    private static int selectedId=-1;

    static {
        if(DESIGNER_ENABLED && !Dev54H15PNativeFleetEditor.enabled())
            MinecraftForge.EVENT_BUS.addListener(ForneyPistonLinkPilotRenderer::onKey);
    }

    private ForneyPistonLinkPilotRenderer(){}

    private static boolean markerEnabled(String marker,String expected){
        try{
            Path f=FMLPaths.GAMEDIR.get().resolve(marker);
            return Files.isRegularFile(f)&&Files.readString(f,StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equals(expected);
        }catch(Exception e){return false;}
    }

    private static Path designPath(){return FMLPaths.CONFIGDIR.get().resolve(DESIGN_FILE);}

    private static Settings load(){
        if(!DESIGNER_ENABLED)return Settings.defaults();
        try{
            Path p=designPath();
            if(!Files.isRegularFile(p))return Settings.defaults();
            List<String> rows=Files.readAllLines(p,StandardCharsets.UTF_8);
            if(rows.size()!=2 || !DESIGN_HEADER.equals(rows.get(0).strip()))
                throw new IllegalArgumentException("Invalid H15O header or row count");
            String[] s=rows.get(1).split("\\t",-1);
            if(s.length!=5 || !"locomotive_steam_forney".equals(s[0]))
                throw new IllegalArgumentException("Invalid Forney config fields");
            Settings v=new Settings(Float.parseFloat(s[1]),Float.parseFloat(s[2]),
                    Float.parseFloat(s[3]),Float.parseFloat(s[4]));
            if(!v.valid())throw new IllegalArgumentException("Out-of-bounds Forney config");
            LOG.info("[DEV54H15O-LOADED] yPx={} lengthPx={} endXPx={} widthPx={}",
                    v.crossheadY,v.rodLength,v.crossheadX,v.rodWidth);
            return v;
        }catch(Exception e){
            LOG.warn("[DEV54H15O-CONFIG-HOLD] Invalid saved Forney settings: {}; safe defaults used",e.toString());
            return Settings.defaults();
        }
    }

    private static ForneySteamLocomotive nearForney(Minecraft mc){
        if(mc.player==null || mc.level==null)return null;
        ForneySteamLocomotive nearest=null;
        double best=12.0D*12.0D;
        for(ForneySteamLocomotive e:mc.level.getEntitiesOfClass(ForneySteamLocomotive.class,
                mc.player.getBoundingBox().inflate(12.0D))){
            double ds=e.distanceToSqr(mc.player);
            if(ds<best){nearest=e;best=ds;}
        }
        return nearest;
    }

    private static void message(LocalPlayer player,String msg){
        if(player!=null)player.displayClientMessage(Component.literal("H15O Forney: "+msg),true);
    }

    private static void onKey(InputEvent.Key event){
        if(event.getAction()!=GLFW.GLFW_PRESS)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null || mc.player==null || mc.level==null)return;
        int key=event.getKey(), mods=event.getModifiers();
        boolean ctrl=(mods & GLFW.GLFW_MOD_CONTROL)!=0;
        boolean alt=(mods & GLFW.GLFW_MOD_ALT)!=0;
        if(key==GLFW.GLFW_KEY_F && ctrl && alt){
            if(editing){editing=false;working=saved;selectedId=-1;
                message(mc.player,"Editor closed; unsaved changes discarded");
            }else{
                ForneySteamLocomotive forney=nearForney(mc);
                if(forney==null){message(mc.player,"No Forney within 12 blocks");return;}
                selectedId=forney.getId();editing=true;working=saved;
                message(mc.player,"NATIVE EDIT ON | arrows: height/length | PgUp/PgDn: inset | [ ]: width | Enter save");
                LOG.info("[DEV54H15O-EDIT-ON] entityId={} current={}",selectedId,working);
            }
            return;
        }
        if(!editing)return;
        ForneySteamLocomotive forney=nearForney(mc);
        if(forney==null || forney.getId()!=selectedId){
            message(mc.player,"Return within 12 blocks of selected Forney");return;
        }
        float step=((mods & GLFW.GLFW_MOD_SHIFT)!=0)?0.0625F:0.25F;
        Settings a=working,b=null;
        switch(key){
            case GLFW.GLFW_KEY_UP -> b=new Settings(a.crossheadY+step,a.rodLength,a.crossheadX,a.rodWidth);
            case GLFW.GLFW_KEY_DOWN -> b=new Settings(a.crossheadY-step,a.rodLength,a.crossheadX,a.rodWidth);
            // Right pushes angled rod farther forward into piston housing.
            case GLFW.GLFW_KEY_RIGHT -> b=new Settings(a.crossheadY,a.rodLength+step,a.crossheadX,a.rodWidth);
            case GLFW.GLFW_KEY_LEFT -> b=new Settings(a.crossheadY,a.rodLength-step,a.crossheadX,a.rodWidth);
            // Smaller absolute native X tucks the end inward under cylinder wall.
            case GLFW.GLFW_KEY_PAGE_UP -> b=new Settings(a.crossheadY,a.rodLength,a.crossheadX-step,a.rodWidth);
            case GLFW.GLFW_KEY_PAGE_DOWN -> b=new Settings(a.crossheadY,a.rodLength,a.crossheadX+step,a.rodWidth);
            case GLFW.GLFW_KEY_LEFT_BRACKET -> b=new Settings(a.crossheadY,a.rodLength,a.crossheadX,a.rodWidth-step);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> b=new Settings(a.crossheadY,a.rodLength,a.crossheadX,a.rodWidth+step);
            case GLFW.GLFW_KEY_R -> {working=Settings.defaults();message(mc.player,"Reset preview to clean piston default; press Enter to save");return;}
            case GLFW.GLFW_KEY_BACKSPACE -> {
                working=saved;editing=false;selectedId=-1;
                message(mc.player,"Discarded unsaved edits");return;
            }
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER -> {
                if(writeSettings(working)){
                    saved=working;editing=false;selectedId=-1;
                    message(mc.player,"Saved native Forney piston design; full reload will persist");
                }else message(mc.player,"SAVE FAILED - see log; editing preserved");
                return;
            }
            default -> {return;}
        }
        if(b==null)return;
        if(!b.valid()){message(mc.player,"Mechanical adjustment limit reached; unchanged");return;}
        working=b;
        message(mc.player,String.format(Locale.ROOT,
                "y=%.2f length=%.2f tuckX=%.2f width=%.2f px | Enter saves",
                b.crossheadY,b.rodLength,b.crossheadX,b.rodWidth));
    }

    private static boolean writeSettings(Settings s){
        if(!s.valid())return false;
        Path target=designPath();
        Path temp=null;
        try{
            Files.createDirectories(target.getParent());
            temp=Files.createTempFile(target.getParent(),"h15o-forney-",".tmp");
            String value=DESIGN_HEADER+"\nlocomotive_steam_forney\t"+
                    s.crossheadY+"\t"+s.rodLength+"\t"+s.crossheadX+"\t"+s.rodWidth+"\n";
            Files.writeString(temp,value,StandardCharsets.UTF_8);
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("[DEV54H15O-SAVED] {}",target);
            return true;
        }catch(Exception e){LOG.error("[DEV54H15O-SAVE-FAILED] {}",target,e);return false;}
        finally{if(temp!=null){try{Files.deleteIfExists(temp);}catch(Exception ignored){}}}
    }

    public static void render(ForneySteamLocomotive entity,float partialTick,
                              PoseStack pose,MultiBufferSource buffers,int light){
        if(!ENABLED||entity==null)return;
        if(Dev54H15PNativeFleetEditor.enabled()){
            Dev54H15PNativeFleetEditor.render(entity,partialTick,pose,buffers,light);
            return;
        }
        final double nativeAngle=RollingStockWheelAnimation.sampleNativePhase(entity,partialTick);
        if(!Double.isFinite(nativeAngle))return;
        final float phase=(float)nativeAngle;
        if(!DESIGNER_ENABLED){renderH15NPrevious(pose,buffers,light,phase);return;}
        Settings cfg=(editing && selectedId==entity.getId())?working:saved;
        renderEditable(pose,buffers,light,phase,cfg,editing && selectedId==entity.getId());
        if(DESIGN_REPORTED.compareAndSet(false,true))
            LOG.info("[DEV54H15O-FORNEY-NATIVE-DESIGNER-ACTIVE] pistonShaft=OFF " +
                    "nativeWheelPhase=unchanged editableModelPart=true yPx={} rodLengthPx={} endXPx={} " +
                    "rodWidthPx={} originalRearOverlay=untouched",
                    cfg.crossheadY,cfg.rodLength,cfg.crossheadX,cfg.rodWidth);
    }

    /** New H15O path: the angled connecting rod goes upward and INWARD into
     * the native piston housing. No long shaft is drawn ahead of the crosshead.
     */
    private static void renderEditable(PoseStack pose,MultiBufferSource buffers,int light,
                                       float phase,Settings c,boolean highlight){
        float crankY=DRIVER_Y+(float)Math.sin(phase)*CRANK_RADIUS;
        float crankZ=DRIVER_Z+(float)Math.cos(phase)*CRANK_RADIUS;
        float dy=c.crossheadY-crankY;
        float reach2=c.rodLength*c.rodLength-dy*dy;
        if(!(reach2>0.0F))return;
        float slideZ=crankZ-(float)Math.sqrt(reach2);
        // If calibration exits the native piston housing, leave geometry
        // hidden rather than draw a physically impossible protrusion.
        if(!Float.isFinite(slideZ) || slideZ< -24.5F || slideZ> -12.0F)return;
        VertexConsumer v=buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL));
        for(int side:new int[]{-1,1}){
            // Retain accepted wheel-pin position; only crosshead end goes inboard.
            float pinX=side*OUTER_X/16.0F;
            float endX=side*c.crossheadX/16.0F;
            taperedBeam(pose,v,light,pinX,crankY,crankZ,endX,c.crossheadY,slideZ,
                    c.rodWidth,190,190,190,side);
            // Compact crosshead slides inside the cylinder; never render a
            // shaft from the crosshead to the forward cylinder mouth.
            taperedBeam(pose,v,light,endX,c.crossheadY-.44F,slideZ,
                    endX,c.crossheadY+.44F,slideZ,.70F,145,145,150,side);
            // Already-present H15N pin cap at original native wheel crank.
            taperedBeam(pose,v,light,pinX+side*.022F,crankY-.35F,crankZ,
                    pinX+side*.022F,crankY+.35F,crankZ,.65F,145,145,150,side);
            if(highlight){
                taperedBeam(pose,v,light,endX+side*.035F,c.crossheadY-.62F,slideZ,
                        endX+side*.035F,c.crossheadY+.62F,slideZ,.34F,245,171,65,side);
            }
        }
    }

    // Frozen H15N geometry when the H15O selector is OFF. This keeps the
    // existing accepted/diagnostic behavior reversible with only a marker.
    private static void renderH15NPrevious(PoseStack pose,MultiBufferSource buffers,int light,float phase){
        final float crankY=DRIVER_Y+(float)Math.sin(phase)*CRANK_RADIUS;
        final float crankZ=DRIVER_Z+(float)Math.cos(phase)*CRANK_RADIUS;
        final float dy=CROSSHEAD_Y-crankY;
        final float reach2=CONNECTING_LENGTH*CONNECTING_LENGTH-dy*dy;
        if(!(reach2>0.0F))return;
        final float slideZ=crankZ-(float)Math.sqrt(reach2);
        if(!Float.isFinite(slideZ)||slideZ<=CYLINDER_FRONT_Z)return;
        final VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL));
        for(int side:new int[]{-1,1}){
            float x=side*OUTER_X/16.0F;
            beam(pose,out,light,x,crankY,crankZ,CROSSHEAD_Y,slideZ,
                    .78F,190,190,190,side);
            beam(pose,out,light,x+side*.013F,CROSSHEAD_Y,slideZ,
                    CROSSHEAD_Y,CYLINDER_FRONT_Z,.44F,170,170,175,side);
            beam(pose,out,light,x+side*.02F,CROSSHEAD_Y-1.0F,slideZ,
                    CROSSHEAD_Y+1.0F,slideZ,1.0F,155,155,160,side);
            beam(pose,out,light,x+side*.025F,crankY-.35F,crankZ,
                    crankY+.35F,crankZ,.65F,145,145,150,side);
        }
        if(REPORTED.compareAndSet(false,true))
            LOG.info("[DEV54H15N-FORNEY-LINK-ACTIVE] nativeModelPart=true " +
                    "wheelPhase=existing pistonLinkSides=2 crankRadiusPx={} " +
                    "driverZPx={} cylinderFrontZPx={} unchangedWheelRenderer=true",
                    CRANK_RADIUS,DRIVER_Z,CYLINDER_FRONT_Z);
    }

    private static void taperedBeam(PoseStack stack,VertexConsumer v,int light,
                                    float xa,float ya,float za,float xb,float yb,float zb,float width,
                                    int r,int g,int b,int side){
        float dy=yb-ya,dz=zb-za;
        float len=(float)Math.sqrt(dy*dy+dz*dz);
        if(!(len>0.001F))return;
        float py=-dz/len*width*.5F,pz=dy/len*width*.5F;
        final Matrix4f mat=stack.last().pose();
        final Matrix3f normal=stack.last().normal();
        float inv=1.0F/16.0F;
        vertex(v,mat,normal,xa,(ya+py)*inv,(za+pz)*inv,0,0,light,r,g,b,side);
        vertex(v,mat,normal,xa,(ya-py)*inv,(za-pz)*inv,0,1,light,r,g,b,side);
        vertex(v,mat,normal,xb,(yb-py)*inv,(zb-pz)*inv,1,1,light,r,g,b,side);
        vertex(v,mat,normal,xb,(yb+py)*inv,(zb+pz)*inv,1,0,light,r,g,b,side);
    }

    /** Frozen H15N uniform-X beam renderer, used only when H15O is disabled. */
    private static void beam(PoseStack stack,VertexConsumer v,int light,float x,
                             float ya,float za,float yb,float zb,float width,
                             int r,int g,int b,int side){
        float dy=yb-ya,dz=zb-za;
        float len=(float)Math.sqrt(dy*dy+dz*dz);
        if(!(len>0.001F))return;
        float py=-dz/len*width*.5F,pz=dy/len*width*.5F;
        final Matrix4f mat=stack.last().pose();
        final Matrix3f normal=stack.last().normal();
        final float inv=1.0F/16.0F;
        vertex(v,mat,normal,x,(ya+py)*inv,(za+pz)*inv,0,0,light,r,g,b,side);
        vertex(v,mat,normal,x,(ya-py)*inv,(za-pz)*inv,0,1,light,r,g,b,side);
        vertex(v,mat,normal,x,(yb-py)*inv,(zb-pz)*inv,1,1,light,r,g,b,side);
        vertex(v,mat,normal,x,(yb+py)*inv,(zb+pz)*inv,1,0,light,r,g,b,side);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,Matrix3f n,float x,
                               float y,float z,float u,float uv,int light,
                               int r,int g,int b,int side){
        v.vertex(m,x,y,z).color(r,g,b,255).uv(u,uv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n,side,0.0F,0.0F).endVertex();
    }
}
