package traincraft.client.model.legacy;
/** DEV44 R13t: rigid XY/XZ/YZ linkage, with no scaling and no positional guessing.
 * Returns null if endpoints cannot be reached by a rigid rod within tolerance.
 */
public final class Dev44RigidLinkage {
  private Dev44RigidLinkage() {}
  public static final class Pose {
    public final float ax,ay,az,cos,sin;
    public final int axis;
    public Pose(float x,float y,float z,float c,float s,int a){ax=x;ay=y;az=z;cos=c;sin=s;axis=a;}
    public float[] point(float x,float y,float z){
      if(axis==0)return new float[]{ax+x,ay+y*cos-z*sin,az+y*sin+z*cos};
      if(axis==1)return new float[]{ax+x*cos-z*sin,ay+y,az+x*sin+z*cos};
      return new float[]{ax+x*cos-y*sin,ay+x*sin+y*cos,az+z};
    }
    public float[] normal(float x,float y,float z){
      if(axis==0)return new float[]{x,y*cos-z*sin,y*sin+z*cos};
      if(axis==1)return new float[]{x*cos-z*sin,y,x*sin+z*cos};
      return new float[]{x*cos-y*sin,x*sin+y*cos,z};
    }
  }
  /** Source A/B and destination A/B are model-space *joint* points.
   * Pose operates on source point coordinates relative to source A.
   */
  public static Pose solve(float[] a,float[] b,float[] da,float[] db,int axis,float tolerance){
    if(axis<0||axis>2||a.length!=3||b.length!=3||da.length!=3||db.length!=3)return null;
    int u=axis==0?1:0, v=axis==2?1:2;
    float x=b[u]-a[u],y=b[v]-a[v],X=db[u]-da[u],Y=db[v]-da[v];
    double l=Math.hypot(x,y),L=Math.hypot(X,Y);
    if(l<1e-5||Math.abs(L-l)>tolerance || Math.abs((db[axis]-da[axis])-(b[axis]-a[axis]))>tolerance)return null;
    float c=(float)((x*X+y*Y)/(l*L)), si=(float)((x*Y-y*X)/(l*L));
    return new Pose(da[0],da[1],da[2],c,si,axis);
  }
  public static float[] wheelPoint(float[] p,float[] center,float cos,float sin,int axis){
    float[] d=new float[]{p[0]-center[0],p[1]-center[1],p[2]-center[2]};
    if(axis==0)return new float[]{p[0],center[1]+d[1]*cos-d[2]*sin,center[2]+d[1]*sin+d[2]*cos};
    if(axis==1)return new float[]{center[0]+d[0]*cos-d[2]*sin,p[1],center[2]+d[0]*sin+d[2]*cos};
    return new float[]{center[0]+d[0]*cos-d[1]*sin,center[1]+d[0]*sin+d[1]*cos,p[2]};
  }
}
