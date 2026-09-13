package dev.duofold;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import dev.duofold.bridge.BridgeClient;
import dev.duofold.motion.EffectStyle;

public final class MainActivity extends Activity {
    private static final int FG=0xfff0f2f7,MUTED=0xff9299a6,ACCENT=0xffbfd2ff,BG=0xff101216;
    private final Handler handler=new Handler(android.os.Looper.getMainLooper());
    private BridgeClient client;
    private SharedPreferences prefs;
    private TextView bridgeStatus,accessStatus,liveStatus,progressText;
    private Button start,trial;
    private boolean pendingStart,pendingTrial;
    private final Runnable update=new Runnable() {
        @Override public void run() { refresh();handler.postDelayed(this,400); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("duo",0);client=BridgeClient.get(this);
        int motionVersion=prefs.getInt("motionVersion",0);
        if(motionVersion<3) {
            SharedPreferences.Editor migration=prefs.edit().putInt("motionVersion",3).putBoolean("zAxis",false);
            if(motionVersion<2)migration.putFloat("range",80).putFloat("intensity",1);
            migration.apply();
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(18),dp(24),dp(28));
        scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout setup=card(body);
        setup.addView(text("开启步骤",22,FG));
        bridgeStatus=text("等待 Shizuku",13,MUTED);setup.addView(bridgeStatus);
        button(setup,"01   连接并授权 Shizuku",()->client.request(),false);
        accessStatus=text("等待辅助服务",13,MUTED);add(setup,accessStatus,12,0);
        button(setup,"02   开启屏幕效果辅助服务",this::openAccessibility,false);
        add(setup,text("辅助服务用于显示效果与修正触控。画面只在本机内存处理，不保存、不上传。",12,MUTED),10,0);
        liveStatus=text("准备就绪后，先体验 10 秒",14,FG);add(setup,liveStatus,16,0);
        progressText=text("",12,MUTED);add(setup,progressText,6,0);
        trial=button(setup,"03   体验 10 秒",()->requestStart(true),true);
        start=button(setup,"04   开启全局效果",()-> {
            FoldService s=FoldService.current();
            if(s!=null && s.isActive()) s.stopSession("已手动停止");else requestStart(false);
        },false);
        button(setup,"重新校准当前握姿",()-> {
            FoldService s=FoldService.current();if(s!=null && s.isActive()) {s.calibrate();Toast.makeText(this,"已校准",Toast.LENGTH_SHORT).show();}
        },false);
        add(setup,text(Build.VERSION.SDK_INT>=34
                ?"三指同时触屏或使用通知栏按钮，随时停止效果。"
                :"使用通知栏按钮，随时停止效果。",12,ACCENT),12,0);

        LinearLayout tuning=card(body);
        tuning.addView(text("调到舒服的手感",19,FG));
        slider(tuning,"最大倾斜角度",15,80,prefs.getFloat("range",80),value->prefs.edit().putFloat("range",value).apply(),"°");
        slider(tuning,"空间强度",20,100,prefs.getFloat("intensity",1)*100,value->prefs.edit().putFloat("intensity",value/100).apply(),"%");
        Switch zAxis=new Switch(this);zAxis.setText("Z 轴感应 · 前后倾斜");zAxis.setTextSize(14);zAxis.setTextColor(FG);
        zAxis.setChecked(prefs.getBoolean("zAxis",false));zAxis.setPadding(0,dp(8),0,dp(4));
        zAxis.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("zAxis",checked).apply());
        tuning.addView(zAxis,new LinearLayout.LayoutParams(-1,dp(52)));
        add(tuning,text("关闭：只响应左右翻转。开启：前后倾斜也会产生上/下方向的展开；斜着移动时两个方向连续合成。横竖屏会自动切换到当前界面的坐标轴，参数在下次开启时生效。",12,MUTED),8,0);

        LinearLayout effects=card(body);
        effects.addView(text("动画效果",19,FG));
        add(effects,text("选择后立即应用，自动记住你的选择。",12,MUTED),6,10);
        RadioGroup choices=new RadioGroup(this);
        EffectStyle selected=EffectStyle.fromId(prefs.getString("effectStyle","classic"));
        for(EffectStyle style:EffectStyle.values()) {
            RadioButton choice=new RadioButton(this);
            choice.setId(View.generateViewId());choice.setTag(style);
            choice.setText(style.title);choice.setTextSize(14);choice.setTextColor(FG);
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            choice.setMinHeight(dp(48));
            choices.addView(choice,new RadioGroup.LayoutParams(-1,-2));
            if(style==selected)choices.check(choice.getId());
        }
        effects.addView(choices);
        TextView effectDescription=text(selected.description,12,MUTED);
        add(effects,effectDescription,8,0);
        choices.setOnCheckedChangeListener((group,id)-> {
            RadioButton choice=group.findViewById(id);if(choice==null)return;
            EffectStyle style=(EffectStyle)choice.getTag();
            prefs.edit().putString("effectStyle",style.id).apply();
            effectDescription.setText(style.description);
            FoldService service=FoldService.current();
            if(service!=null)service.setEffectStyle(style);
        });

        TextView authorLink=text("作者 jcx  ·  GitHub ↗",11,ACCENT);
        authorLink.setGravity(Gravity.CENTER);
        authorLink.setContentDescription("打开作者 jcx 的 GitHub");
        authorLink.setPadding(0,dp(12),0,dp(8));
        authorLink.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/jcx396905-gif"))));
        add(body,authorLink,10,0);

        refresh();
    }
    @Override protected void onResume() {super.onResume();client.connect();handler.post(update);}
    @Override protected void onPause() {handler.removeCallbacks(update);super.onPause();}
    private void refresh() {
        if(bridgeStatus==null)return;
        bridgeStatus.setText(client.message());bridgeStatus.setTextColor(client.service()!=null?ACCENT:MUTED);
        FoldService s=FoldService.current();boolean active=s!=null&&s.isActive();
        accessStatus.setText(s!=null?"屏幕效果辅助服务已连接":"等待开启屏幕效果辅助服务");
        accessStatus.setTextColor(s!=null?ACCENT:MUTED);
        liveStatus.setText(s!=null?s.status():prefs.getString("lastStatus","准备就绪后，先体验 10 秒"));
        progressText.setText(active?"展开 "+Math.round(s.progress()*100)+"%  ·  已更新 "+s.frames()+" 帧":"");
        start.setText(active?"停止并恢复原始屏幕":"04   开启全局效果");
        trial.setEnabled(!active && s!=null && client.service()!=null);
        start.setEnabled(active || s!=null && client.service()!=null);
    }
    private void requestStart(boolean isTrial) {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            pendingStart=true;pendingTrial=isTrial;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},51);return;
        }
        startSession(isTrial);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==51 && pendingStart){pendingStart=false;startSession(pendingTrial);}
    }
    private void startSession(boolean isTrial) {
        FoldService s=FoldService.current();
        if(s==null){openAccessibility();return;}
        s.startSession(isTrial);
        if(s.isActive()) moveTaskToBack(true);
    }
    private void openAccessibility() {
        try {startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.intent.extra.COMPONENT_NAME",new ComponentName(this,FoldService.class)));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xff1b1e24);shape.setCornerRadius(dp(24));shape.setStroke(dp(1),0xff292e38);card.setBackground(shape);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);parent.addView(card,lp);return card;
    }
    private TextView text(String text,int size,int color) {
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;
    }
    private void add(LinearLayout p,View v,int top,int bottom) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);lp.bottomMargin=dp(bottom);p.addView(v,lp);
    }
    private Button button(LinearLayout p,String title,Runnable action,boolean filled) {
        Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(filled?BG:ACCENT);
        GradientDrawable bg=new GradientDrawable();bg.setColor(filled?ACCENT:0xff252b36);bg.setCornerRadius(dp(16));b.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(10);p.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    private interface ValueListener {void changed(float value);}
    private void slider(LinearLayout parent,String label,int min,int max,float initial,ValueListener listener,String unit) {
        TextView value=text(label+"  "+Math.round(initial)+unit,13,MUTED);add(parent,value,18,4);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(Math.round(initial)-min);parent.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int p,boolean user){value.setText(label+"  "+(min+p)+unit);if(user)listener.changed(min+p);}
            @Override public void onStartTrackingTouch(SeekBar b){} @Override public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
