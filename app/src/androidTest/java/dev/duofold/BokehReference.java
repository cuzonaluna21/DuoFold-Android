package dev.duofold;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Independent renderer using the unmodified source shaders pinned at a2e7852.
 * Source: sosopop/duo_demo, MIT; see packaged THIRD_PARTY_NOTICES.txt.
 * Run on the view's GL thread; private FBO avoids touching the displayed test frame. */
final class BokehReference {
    static Bitmap render(Context assets,Bitmap source,int width,int height,float pitch,float roll) throws Exception {
        int[] previous=new int[1];GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,previous,0);
        int[] textures=new int[2],fbo=new int[1];int program=GLES20.glCreateProgram();
        int vs=shader(assets,GLES20.GL_VERTEX_SHADER,"dof_vertex.glsl");
        int fs=shader(assets,GLES20.GL_FRAGMENT_SHADER,"dof_fragment.glsl");
        GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new AssertionError(GLES20.glGetProgramInfoLog(program));
        try {
            GLES20.glGenTextures(2,textures,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[0]);
            params(GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,source,0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[1]);params(GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glGenFramebuffers(1,fbo,0);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[1],0);
            if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Reference FBO");
            GLES20.glViewport(0,0,width,height);GLES20.glUseProgram(program);
            float aspect=width/(float)height;
            float px=roll<=0?-aspect:aspect,py=pitch<=0?-1:1;
            float[] matrix=new float[16];Matrix.setIdentityM(matrix,0);
            Matrix.translateM(matrix,0,px,py,0);
            Matrix.rotateM(matrix,0,-(float)Math.toDegrees(pitch),1,0,0);
            Matrix.rotateM(matrix,0,(float)Math.toDegrees(roll),0,1,0);
            Matrix.translateM(matrix,0,-px,-py,0);
            GLES20.glUniformMatrix4fv(loc(program,"uModelMatrix"),1,false,matrix,0);
            GLES20.glUniform2f(loc(program,"uScreenHalfSize"),aspect,1);
            GLES20.glUniform2f(loc(program,"uPhotoHalfSize"),aspect,1);
            GLES20.glUniform2f(loc(program,"uPhotoOffset"),0,0);
            GLES20.glUniform1f(loc(program,"uCameraDistance"),1f/(float)Math.tan(Math.toRadians(15)));
            GLES20.glUniform1f(loc(program,"uAperture"),1);
            GLES20.glUniform1f(loc(program,"uMaxBlurPixels"),160);
            GLES20.glUniform2f(loc(program,"uTexelSize"),1f/source.getWidth(),1f/source.getHeight());
            GLES20.glUniform1i(loc(program,"uTexture"),2);
            FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-aspect,-1,aspect,-1,-aspect,1,aspect,1}).position(0);
            int pos=GLES20.glGetAttribLocation(program,"aPosition");GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(pos);
            ByteBuffer rgba=ByteBuffer.allocateDirect(width*height*4);
            GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,rgba);
            int error=GLES20.glGetError();if(error!=0)throw new AssertionError("Reference GL error "+error);
            int[] colors=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                int i=((height-1-y)*width+x)*4;
                colors[y*width+x]=0xff000000|((rgba.get(i)&255)<<16)|((rgba.get(i+1)&255)<<8)|(rgba.get(i+2)&255);
            }
            return Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888);
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,previous[0]);
            GLES20.glDeleteTextures(2,textures,0);GLES20.glDeleteFramebuffers(1,fbo,0);GLES20.glDeleteProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }
    private static int loc(int p,String name) {return GLES20.glGetUniformLocation(p,name);}
    private static void params(int min) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,min);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    }
    private static int shader(Context context,int type,String path) throws Exception {
        String code;
        try(InputStream in=context.getAssets().open("reference/"+path)) {code=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,code);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new AssertionError(GLES20.glGetShaderInfoLog(shader));return shader;
    }
}
