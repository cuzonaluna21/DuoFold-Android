package dev.duofold.render;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import dev.duofold.motion.FoldModel;

public final class FoldView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public interface Listener { void onReady(Surface input,int width,int height); void onError(String message); }
    private final Listener listener;
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] matrix=new float[16];
    private volatile FoldModel.State state=FoldModel.atAxes(0,0,1,400,800);
    private volatile FoldModel.State drawnState=FoldModel.atAxes(0,0,1,400,800);
    private SurfaceTexture texture;
    private Surface input;
    private int program,textureId,width,height;
    private volatile boolean newFrame,hasFrame;
    private volatile boolean stopped;
    private volatile boolean saveDebugFrame;
    public void saveDebugFrame() {if(dev.duofold.BuildConfig.DEBUG){saveDebugFrame=true;requestRender();}}
    public FoldView(Context context,Listener listener) {
        super(context); this.listener=listener;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,0,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setPreserveEGLContextOnPause(true);
        quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
        setRenderer(this); setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    public void setFold(float forwardRadians,float sideRadians,float intensity) {
        float density=getResources().getDisplayMetrics().density;
        float widthPoints=Math.max(1,getWidth())/density,heightPoints=Math.max(1,getHeight())/density;
        state=FoldModel.atAxes(forwardRadians,sideRadians,intensity,widthPoints,heightPoints);requestRender();
    }
    public FoldModel.State inputState() { return drawnState; }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        try {
            program=GLES20.glCreateProgram();
            GLES20.glAttachShader(program,compile(GLES20.GL_VERTEX_SHADER,asset("fold.vert")));
            GLES20.glAttachShader(program,compile(GLES20.GL_FRAGMENT_SHADER,asset("fold.frag")));
            GLES20.glLinkProgram(program);
            int[] status=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
            if(status[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            int[] ids=new int[1]; GLES20.glGenTextures(1,ids,0); textureId=ids[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            texture=new SurfaceTexture(textureId);
            texture.setOnFrameAvailableListener(t->{newFrame=true; requestRender();});
            input=new Surface(texture); hasFrame=false;
        } catch(Exception e) { post(()->listener.onError("GPU: "+e.getMessage())); }
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
        GLES20.glViewport(0,0,w,h); width=w; height=h;
        if(texture==null || stopped) return;
        int captureWidth=Math.min(w,1080), captureHeight=Math.round(h*(captureWidth/(float)w));
        texture.setDefaultBufferSize(captureWidth,captureHeight);
        post(()-> { if(!stopped) listener.onReady(input,captureWidth,captureHeight); });
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0,0,0,0); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if(texture==null || stopped || program==0) return;
        try {
            if(newFrame) { newFrame=false; texture.updateTexImage(); texture.getTransformMatrix(matrix); hasFrame=true; }
            if(!hasFrame) return;
            FoldModel.State s=state;
            GLES20.glUseProgram(program);
            int pos=GLES20.glGetAttribLocation(program,"aPosition");
            GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,quad);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glUniform1i(location("uScreen"),0);
            GLES20.glUniformMatrix4fv(location("uTextureMatrix"),1,false,matrix,0);
            GLES20.glUniform2f(location("uResolution"),width,height);
            GLES20.glUniform2f(location("uAngles"),s.angleX,s.angleY);
            GLES20.glUniform2f(location("uSizePoints"),s.widthPoints,s.heightPoints);
            scalar("uIntensity",s.intensity);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos);
            drawnState=s;
            if(saveDebugFrame && dev.duofold.BuildConfig.DEBUG) {
                saveDebugFrame=false;
                ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
                GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                int[] argb=new int[width*height];
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int offset=((height-1-y)*width+x)*4;
                    argb[y*width+x]=0xff000000|((pixels.get(offset)&255)<<16)|((pixels.get(offset+1)&255)<<8)|(pixels.get(offset+2)&255);
                }
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(argb,width,height,android.graphics.Bitmap.Config.ARGB_8888);
                new Thread(()-> {
                    try(java.io.FileOutputStream out=getContext().openFileOutput("debug-frame.png",0)) {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                        android.util.Log.i("DuoFold","Debug frame saved");
                    } catch(java.io.IOException e) {android.util.Log.e("DuoFold","Debug export failed",e);}
                    finally {bitmap.recycle();}
                },"DuoFrameExport").start();
            }
        } catch(Exception e) { post(()->listener.onError("GPU frame: "+e.getMessage())); }
    }
    public void releaseResources() {
        stopped=true;
        queueEvent(()-> {
            if(input!=null) {input.release();input=null;}
            if(texture!=null) {texture.release();texture=null;}
            if(program!=0) GLES20.glDeleteProgram(program);
            if(textureId!=0) GLES20.glDeleteTextures(1,new int[]{textureId},0);
        });
    }
    private int location(String name) { return GLES20.glGetUniformLocation(program,name); }
    private void scalar(String name,float value) { GLES20.glUniform1f(location(name),value); }
    private String asset(String name) throws Exception {
        try(InputStream in=getContext().getAssets().open(name)) { return new String(in.readAllBytes(),StandardCharsets.UTF_8); }
    }
    private int compile(int type,String source) {
        int shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] status=new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader)); return shader;
    }
}
