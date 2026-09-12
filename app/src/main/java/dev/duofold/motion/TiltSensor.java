package dev.duofold.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

/** Relative pose model. Android matrices map device coordinates to world coordinates. */
public final class TiltSensor implements SensorEventListener {
    public interface Listener {void onTilt(float progress,float forwardDegrees,float sideDegrees);}
    private final SensorManager manager;
    private final WindowManager windows;
    private final Listener listener;
    private final float range;
    private final boolean includeForward;
    private final float[] device=new float[9],screen=new float[9],reference=new float[9];
    private Sensor attitude,gyro;
    private boolean calibrated;
    private float filteredForward,filteredSide,forwardRate,sideRate;
    private long previous,rateTime;
    private int lastRotation=-1;
    public TiltSensor(Context context,float range,boolean includeForward,Listener listener) {
        this.range=range;this.includeForward=includeForward;this.listener=listener;
        manager=context.getSystemService(SensorManager.class);
        windows=context.getSystemService(WindowManager.class);
    }
    public boolean start() {
        attitude=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(attitude==null)attitude=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(attitude==null)return false;
        boolean ok=manager.registerListener(this,attitude,8333);
        gyro=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(ok&&gyro!=null)manager.registerListener(this,gyro,8333);
        return ok;
    }
    public void calibrate() {calibrated=false;filteredForward=0;filteredSide=0;previous=0;}
    public void stop() {manager.unregisterListener(this);}
    @Override public void onSensorChanged(SensorEvent event) {
        int orientation=windows.getDefaultDisplay().getRotation();
        if(event.sensor.getType()==Sensor.TYPE_GYROSCOPE) {
            sideRate=switch(orientation) {
                case Surface.ROTATION_90 -> -event.values[0];
                case Surface.ROTATION_180 -> -event.values[1];
                case Surface.ROTATION_270 -> event.values[0];
                default -> event.values[1];
            };
            forwardRate=switch(orientation) {
                case Surface.ROTATION_90 -> event.values[1];
                case Surface.ROTATION_180 -> -event.values[0];
                case Surface.ROTATION_270 -> -event.values[1];
                default -> event.values[0];
            };
            rateTime=event.timestamp;return;
        }
        SensorManager.getRotationMatrixFromVector(device,event.values);
        int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        switch(orientation) {
            case Surface.ROTATION_90: x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;break;
            case Surface.ROTATION_180:x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;break;
            case Surface.ROTATION_270:x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;break;
        }
        SensorManager.remapCoordinateSystem(device,x,y,screen);
        if(!calibrated||orientation!=lastRotation) {
            System.arraycopy(screen,0,reference,0,9);calibrated=true;filteredForward=0;filteredSide=0;previous=event.timestamp;
            lastRotation=orientation;listener.onTilt(1,0,0);return;
        }
        float nx=reference[0]*screen[2]+reference[3]*screen[5]+reference[6]*screen[8];
        float nz=reference[2]*screen[2]+reference[5]*screen[5]+reference[8]*screen[8];
        float ny=reference[1]*screen[2]+reference[4]*screen[5]+reference[7]*screen[8];
        float side=(float)Math.atan2(nx,nz);
        float forward=-(float)Math.atan2(ny,nz);
        float[] measured=MotionAxes.resolve(side,forward,includeForward);
        float[] prediction=event.timestamp-rateTime<100_000_000L
                ? MotionAxes.resolve(sideRate*.04f,forwardRate*.04f,includeForward) : new float[]{0,0};
        float dt=Math.max(0,Math.min(.1f,(event.timestamp-previous)/1e9f));previous=event.timestamp;
        float response=1-(float)Math.exp(-dt/.006922f);
        filteredForward+=response*wrap(measured[0]+prediction[0]-filteredForward);
        filteredSide+=response*wrap(measured[1]+prediction[1]-filteredSide);
        float limit=Math.max(15,Math.min(80,range));
        float forwardDegrees=(float)Math.toDegrees(filteredForward);
        float sideDegrees=(float)Math.toDegrees(filteredSide);
        float magnitude=(float)Math.hypot(forwardDegrees,sideDegrees);
        if(magnitude>limit) {float scale=limit/magnitude;forwardDegrees*=scale;sideDegrees*=scale;magnitude=limit;}
        listener.onTilt(1-magnitude/limit,forwardDegrees,sideDegrees);
    }
    private static float wrap(float angle) {
        while(angle>Math.PI)angle-=2*(float)Math.PI;
        while(angle<-Math.PI)angle+=2*(float)Math.PI;
        return angle;
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
}
