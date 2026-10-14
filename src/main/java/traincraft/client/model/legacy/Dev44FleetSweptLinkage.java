package traincraft.client.model.legacy;

import traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec;
import java.util.List;

/**
 * DEV44 fleet visual linkage candidate. Uses only exact, user-tagged mesh parts,
 * currently accepted wheel phases, and bounded rigid solutions. No geometry edits.
 * This does NOT imply mechanically verified joints or runtime acceptance.
 */
final class Dev44FleetSweptLinkage {
    static final int STRIDE = 8;
    private static final float MAX_THROW = 0.23f;
    private static final float MAX_STROKE = 0.40f;
    private Dev44FleetSweptLinkage() {}

    static final class Motion {
        final Dev44RigidLinkage.Pose pose;
        final float[] restA;
        Motion(Dev44RigidLinkage.Pose pose, float[] restA) {
            this.pose = pose; this.restA = restA;
        }
    }

    static final class Frame {
        final Motion[] moves;
        int rods, cranks, skipped;
        Frame(int chunks) { moves = new Motion[chunks]; }
        boolean has(int ch) { return ch >= 0 && ch < moves.length && moves[ch] != null; }
        Motion get(int ch) { return has(ch) ? moves[ch] : null; }
    }

    private static float[] center(float[] v, int ch) {
        int s=ch*24*STRIDE, e=Math.min(v.length,s+24*STRIDE);
        float[] c={0,0,0};int n=0;
        for(int i=s;i+2<e;i+=STRIDE){c[0]+=v[i];c[1]+=v[i+1];c[2]+=v[i+2];n++;}
        if(n==0)return null;
        for(int i=0;i<3;i++)c[i]/=n;
        return c;
    }
    private static float[][] bounds(float[] v,int ch) {
        int s=ch*24*STRIDE,e=Math.min(v.length,s+24*STRIDE);
        float[] lo={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] hi={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int i=s;i+2<e;i+=STRIDE){
            for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],v[i+a]);hi[a]=Math.max(hi[a],v[i+a]);}
        }
        return new float[][]{lo,hi};
    }
    private static float d2(float[] p, float[] q) {
        float dx=p[0]-q[0],dy=p[1]-q[1],dz=p[2]-q[2];return dx*dx+dy*dy+dz*dz;
    }
    private static float[] axle(TransformSpec w) { return new float[]{w.centerX(),w.centerY(),w.centerZ()}; }
    private static float radial2(float[] p,TransformSpec w) {
        float dx=p[0]-w.centerX(),dy=p[1]-w.centerY(),dz=p[2]-w.centerZ();
        if(w.axis()==0)return dy*dy+dz*dz;
        if(w.axis()==1)return dx*dx+dz*dz;
        return dx*dx+dy*dy;
    }
    private static float axial(float[] p,TransformSpec w) {
        return w.axis()==0?p[0]-w.centerX():w.axis()==1?p[1]-w.centerY():p[2]-w.centerZ();
    }
    private static TransformSpec wheel(float[] p,List<TransformSpec> ws,int axis) {
        TransformSpec best=null;float score=Float.POSITIVE_INFINITY;
        for(TransformSpec w:ws) {
            if(!w.rotates() || w.axis()!=axis)continue;
            float al=Math.abs(axial(p,w));
            float r=radial2(p,w);
            if(al>0.31f || r>0.45f*0.45f) continue;
            float s=al*al*9+r;
            if(s<score){score=s;best=w;}
        }
        return best;
    }
    private static Motion translation(float[] rest,float[] target,int axis){
        return new Motion(new Dev44RigidLinkage.Pose(target[0],target[1],target[2],1f,0f,axis),rest);
    }
    private static float[] wheelTarget(float[] p,TransformSpec w) {
        return Dev44RigidLinkage.wheelPoint(p,axle(w),w.cos(),w.sin(),w.axis());
    }
    private static float[] endpoint(float[][] b,float[] c,int along,boolean low) {
        float[] p=c.clone();p[along]=low?b[0][along]:b[1][along];return p;
    }
    private static Motion solveRod(float[] a,float[] b,TransformSpec wa,TransformSpec wb) {
        float[] da=wheelTarget(a,wa),db;
        if(wb!=null && wb!=wa){
            db=wheelTarget(b,wb);
            // Both endpoints must keep a constant rigid length throughout the motion.
            if(Math.abs((float)Math.sqrt(d2(da,db))-(float)Math.sqrt(d2(a,b)))>0.018f)return null;
        }else{
            // Virtual crosshead: solve a constant-length piston slide, not a guessed
            // full-orbit translation of the cylinder. No source vertex stretching.
            if(wa.axis()!=2 && wa.axis()!=0)return null;
            int along=wa.axis()==2?0:2;
            int rise=1;
            float L=(float)Math.hypot(b[along]-a[along],b[rise]-a[rise]);
            if(L<0.35f)return null;
            float dy=b[rise]-da[rise];
            float sq=L*L-dy*dy;
            if(sq<=0f)return null;
            db=b.clone();
            db[along]=da[along]+Math.copySign((float)Math.sqrt(sq),b[along]-a[along]);
            if(Math.abs(db[along]-b[along])>MAX_STROKE)return null;
        }
        Dev44RigidLinkage.Pose rigid=Dev44RigidLinkage.solve(a,b,da,db,wa.axis(),0.018f);
        return rigid==null?null:new Motion(rigid,a);
    }
    static Frame build(float[] v,int count,int[] roles,List<TransformSpec> wheels) {
        Frame f=new Frame((count+23)/24);
        if(wheels.isEmpty())return f;
        int n=Math.min(f.moves.length,roles.length);
        for(int ch=0;ch<n;ch++) {
            int role=roles[ch];
            // Selected "pistons" often include stationary cylinder shells.
            if(role!=1 && role!=3)continue;
            float[] c=center(v,ch);if(c==null)continue;
            float[][] b=bounds(v,ch);
            float dx=b[1][0]-b[0][0],dy=b[1][1]-b[0][1],dz=b[1][2]-b[0][2];
            if(role==3) {
                // Keep crank hardware on the actual accepted wheel's crank orbit;
                // never rotate a tagged box around its own center.
                TransformSpec w=wheel(c,wheels,2);
                if(w==null)w=wheel(c,wheels,0);
                if(w==null || radial2(c,w)<0.018f*0.018f || radial2(c,w)>MAX_THROW*MAX_THROW){f.skipped++;continue;}
                f.moves[ch]=translation(c,wheelTarget(c,w),w.axis());f.cranks++;
                continue;
            }
            int along=dx>=dz?0:2;
            int axis=along==0?2:0;
            float L=along==0?dx:dz;
            // Only authored long slender bars can safely admit a two-end rigid fit.
            if(L<0.38f || dy>0.30f || (along==0?dz:dx)>0.18f){f.skipped++;continue;}
            float[] low=endpoint(b,c,along,true),high=endpoint(b,c,along,false);
            TransformSpec wl=wheel(low,wheels,axis),wh=wheel(high,wheels,axis);
            float[] a;float[] end;TransformSpec wa,wb;
            if(wl==null && wh==null){f.skipped++;continue;}
            if(wh==null || (wl!=null && radial2(low,wl)<=radial2(high,wh))) {
                a=low;end=high;wa=wl!=null?wl:wh;wb=wh;
            } else {a=high;end=low;wa=wh;wb=wl;}
            if(wa==null || radial2(a,wa)>MAX_THROW*MAX_THROW || radial2(a,wa)<0.012f*0.012f){f.skipped++;continue;}
            if(wb==wa)wb=null;
            Motion m=solveRod(a,end,wa,wb);
            if(m!=null){f.moves[ch]=m;f.rods++;}else f.skipped++;
        }
        return f;
    }
}
