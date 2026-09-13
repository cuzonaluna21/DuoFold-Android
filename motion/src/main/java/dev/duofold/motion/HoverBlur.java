package dev.duofold.motion;

/** Motion envelope independent of rendering frequency. All access is on the GL thread. */
public final class HoverBlur {
    private boolean initialized;
    private double previousTime,lastMovement;
    private float anchorX,anchorY;
    private static final float MOVEMENT_THRESHOLD=(float)Math.toRadians(.35);

    public void reset() {initialized=false;}
    public float sample(float x,float y,double seconds) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Double.isFinite(seconds))return 0;
        if(!initialized||seconds<previousTime) {
            initialized=true;anchorX=x;anchorY=y;lastMovement=seconds;previousTime=seconds;
            return 1;
        }
        previousTime=seconds;
        // Accumulate movement against an anchor. Slow deliberate tilts still count,
        // while sub-threshold noise around a held pose never resets the fade.
        if(Math.hypot(x-anchorX,y-anchorY)>=MOVEMENT_THRESHOLD) {
            anchorX=x;anchorY=y;lastMovement=seconds;
        }
        float t=(float)Math.max(0,Math.min(1,(seconds-lastMovement-.12)/.48));
        return 1-t*t*(3-2*t);
    }
}
