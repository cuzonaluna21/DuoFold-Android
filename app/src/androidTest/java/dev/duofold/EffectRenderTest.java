package dev.duofold;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.test.ActivityInstrumentationTestCase2;
import android.view.Surface;
import android.widget.FrameLayout;
import dev.duofold.motion.EffectStyle;
import dev.duofold.render.FoldView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real external texture and GLSL shader with a synthetic screen; no Shizuku needed. */
public class EffectRenderTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public EffectRenderTest() {super(MainActivity.class);}

    public void testEffectSwitchingAndHoverConvergeOnGpu() throws Throwable {
        MainActivity activity=getActivity();
        CountDownLatch rendered=new CountDownLatch(1);
        AtomicReference<String> error=new AtomicReference<>();
        AtomicReference<Bitmap> sourceBitmap=new AtomicReference<>();
        FoldView[] holder=new FoldView[1];
        runTestOnUiThread(()-> {
            FoldView view=new FoldView(activity,new FoldView.Listener() {
                @Override public void onReady(Surface source,int width,int height) {
                    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                    sourceBitmap.set(bitmap);
                    Canvas canvas=new Canvas(bitmap);
                    Paint paint=new Paint();
                    try {
                        canvas.drawColor(Color.WHITE);
                        for(int y=0;y<height;y+=24)for(int x=0;x<width;x+=24) {
                            paint.setColor(((x/24+y/24)%2==0)?0xff287abf:0xffffbb40);
                            canvas.drawRect(x,y,x+24,y+24,paint);
                        }
                        paint.setColor(Color.BLACK);paint.setTextSize(30);
                        canvas.drawText("DUO / 0123456789",30,80,paint);
                        paint.setColor(Color.RED);canvas.drawRect(0,0,24,24,paint);
                        paint.setColor(Color.GREEN);canvas.drawRect(0,height-24,24,height,paint);
                    } finally {
                        Canvas output=source.lockCanvas(null);
                        output.drawBitmap(bitmap,0,0,null);source.unlockCanvasAndPost(output);
                    }
                }
                @Override public void onFirstFrame() {rendered.countDown();}
                @Override public void onError(String message) {error.set(message);rendered.countDown();}
            });
            holder[0]=view;
            activity.addContentView(view,new FrameLayout.LayoutParams(480,800));
            view.setFold(0,(float)Math.toRadians(28),1);
        });
        try {
            assertTrue("GPU did not render",rendered.await(15,TimeUnit.SECONDS));
            assertNull(error.get());
            FoldView view=holder[0];
            Bitmap original=snapshot(activity,view,"classic");
            double energy=0;
            for(int y=0;y<original.getHeight();y+=10)for(int x=0;x<original.getWidth();x+=10)
                energy+=Color.red(original.getPixel(x,y));
            assertTrue("Source texture is black",energy>10000);
            for(EffectStyle style:new EffectStyle[]{EffectStyle.EDGE,EffectStyle.SOFT,EffectStyle.CLEAR,EffectStyle.BOKEH}) {
                runTestOnUiThread(()->view.setEffectStyle(style));
                Bitmap current=snapshot(activity,view,style.id);
                assertTrue("Style identical to original: "+style.id,difference(original,current)>2);
                current.recycle();
            }
            for(float[] angles:new float[][]{{0,.49f},{0,-.49f},{.5f,0},{-.5f,0},{.35f,-.4f}}) {
                runTestOnUiThread(()-> {view.setEffectStyle(EffectStyle.BOKEH);view.setFold(angles[0],angles[1],1);});
                Bitmap actual=snapshot(activity,view,"bokeh-compare");
                CountDownLatch compared=new CountDownLatch(1);
                AtomicReference<Bitmap> reference=new AtomicReference<>();
                AtomicReference<Throwable> failure=new AtomicReference<>();
                view.queueEvent(()-> {
                    try {reference.set(BokehReference.render(getInstrumentation().getContext(),sourceBitmap.get(),view.getWidth(),view.getHeight(),angles[0],angles[1]));}
                    catch(Throwable e) {failure.set(e);}finally {compared.countDown();}
                });
                assertTrue("Reference render timeout",compared.await(10,TimeUnit.SECONDS));
                if(failure.get()!=null)throw failure.get();
                double delta=difference(actual,reference.get());
                android.util.Log.i("DuoReference","angles="+angles[0]+","+angles[1]+" mean RGB error="+delta);
                assertTrue("Source mismatch at "+angles[0]+","+angles[1]+": "+delta,delta<1.5);
                actual.recycle();reference.get().recycle();
            }
            runTestOnUiThread(()->view.setFold(0,(float)Math.toRadians(28),1));
            runTestOnUiThread(()->view.setEffectStyle(EffectStyle.SETTLE));
            // No further sensor events or source frames: the renderer must finish the fade itself.
            Thread.sleep(1500);
            Bitmap settled=snapshot(activity,view,"settled");
            runTestOnUiThread(()->view.setEffectStyle(EffectStyle.CLEAR));
            Bitmap clear=snapshot(activity,view,"clear-after-settle");
            assertTrue("Held frame did not sharpen",difference(settled,clear)<1);
            clear.recycle();settled.recycle();
            runTestOnUiThread(()->view.setEffectStyle(EffectStyle.CLASSIC));
            Bitmap restored=snapshot(activity,view,"classic-restored");
            assertEquals("Switching changed the original effect",0,difference(original,restored),.01);
            original.recycle();restored.recycle();
            assertNull(error.get());
        } finally {
            runTestOnUiThread(()-> {holder[0].releaseResources();holder[0].onPause();});
        }
    }

    private Bitmap snapshot(MainActivity activity,FoldView view,String name) throws Throwable {
        File frame=new File(activity.getFilesDir(),"debug-frame.png");
        if(frame.exists())assertTrue(frame.delete());
        runTestOnUiThread(view::saveDebugFrame);
        Bitmap bitmap=null;
        for(int i=0;i<100&&bitmap==null;i++) {
            Thread.sleep(50);
            if(frame.isFile())bitmap=BitmapFactory.decodeFile(frame.getAbsolutePath());
        }
        assertNotNull("No screenshot: "+name,bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"test-"+name+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
        return bitmap;
    }
    private double difference(Bitmap first,Bitmap second) {
        double total=0;int count=0;
        for(int y=0;y<first.getHeight();y+=4)for(int x=0;x<first.getWidth();x+=4) {
            int a=first.getPixel(x,y),b=second.getPixel(x,y);
            total+=Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));
            count+=3;
        }
        return total/count;
    }
}
