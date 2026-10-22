package uz.uruktype.android;

import android.app.Activity;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UrukKeyboardService extends InputMethodService {
  private final String[] names={"EN·UZ","РУ","ЎЗ"};
  private final String[][] layouts={
    {"qwertyuiop","asdfghjkl","zxcvbnm"},
    {"йцукенгшщзх","фывапролджэ","ячсмитьбю"},
    {"йцукенгшўқғҳ","фывапролджэ","ячсмитьбю"}
  };
  private final String[] emojis={"😀","😂","❤️","👍","🙏","🎉","🙂","✅","🔥","🌹"};
  private int language=0;
  private boolean uppercase=false,symbols=false,emoji=false,dark=false;
  private LinearLayout root,body,bar;
  private final List<String> suggestions=new ArrayList<>();
  private TextView key(String title,float weight,int color){
    TextView v=new TextView(this);
    v.setText(title);v.setTextSize(title.length()>4?13:21);v.setGravity(Gravity.CENTER);v.setTextColor(dark?Color.WHITE:Color.rgb(35,39,46));
    GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(18*getResources().getDisplayMetrics().density);v.setBackground(bg);v.setElevation(1.5f*getResources().getDisplayMetrics().density);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,(int)(50*getResources().getDisplayMetrics().density),weight);
    p.setMargins(3,4,3,4);v.setLayoutParams(p);return v;
  }
  private int standard(){return dark?0xff454951:0xffffffff;}
  private int utility(){return dark?0xff555a63:0xffd5d9e0;}
  private LinearLayout row(){
    LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);r.setOrientation(LinearLayout.HORIZONTAL);
    body.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;
  }
  private void add(LinearLayout r,String label,float width,int color,Runnable onTap){
    TextView b=key(label,width,color);r.addView(b);b.setOnClickListener(v->onTap.run());
  }
  private void send(String s){
    InputConnection c=getCurrentInputConnection();
    if(c!=null)c.commitText(s,1);
  }
  private void shift(){uppercase=!uppercase;render();}
  private void erase(){
    InputConnection c=getCurrentInputConnection();if(c!=null)c.deleteSurroundingText(1,0);
  }
  private void lang(int delta){
    language=(language+names.length+delta)%names.length;render();
  }
  // New Uzbek Latin letters share QWERTY base keys with long presses.
  private String uzbekAlternative(String letter){
    switch(letter){
      case "o": return "ö";
      case "g": return "ğ";
      case "s": return "ş";
      case "c": return "ç";
      default: return null;
    }
  }
  private void characters(String chars){
    LinearLayout r=row();
    String[] arr=chars.split("");
    for(String s:arr)if(!s.isEmpty()){
      String printed=uppercase?s.toUpperCase(java.util.Locale.ROOT):s;
      TextView b=key(printed,1,standard());
      // Only English-Uzbek Latin layout has these extra long-press characters.
      String alternative=(language==0 && !symbols && !emoji)?uzbekAlternative(s):null;
      if(alternative!=null){
        String alternate=uppercase?alternative.toUpperCase(java.util.Locale.ROOT):alternative;
        String shown=printed+" "+alternate;
        android.text.SpannableString hint=new android.text.SpannableString(shown);
        int start=printed.length()+1;
        hint.setSpan(new android.text.style.RelativeSizeSpan(0.55f),start,shown.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        hint.setSpan(new android.text.style.SuperscriptSpan(),start,shown.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(hint);
        b.setContentDescription(printed+"; long press for "+alternate);
        b.setOnLongClickListener(v->{
          send(alternate);
          if(uppercase){uppercase=false;render();}
          return true;
        });
      }
      r.addView(b);
      b.setOnClickListener(v->{
        send(printed);
        if(uppercase){uppercase=false;render();}
      });
    }
  }
  private void render(){
    if(root==null)return;
    int background=dark?0xff24262c:0xffe9edf4;root.setBackgroundColor(background);
    body.removeAllViews();
    LinearLayout tools=row();
    add(tools,"☷",1,utility(),()->{dark=!dark;render();});
    add(tools,names[language],1,utility(),()->lang(1));
    add(tools,"🙂",1,utility(),()->{emoji=!emoji;symbols=false;render();});
    add(tools,"?123",1,utility(),()->{symbols=!symbols;emoji=false;render();});
    add(tools,"⌫",1,utility(),()->erase());
    if(emoji){
      LinearLayout r=row();
      for(int i=0;i<5;i++){String s=emojis[i];add(r,s,1,standard(),()->send(s));}
      r=row();
      for(int i=5;i<10;i++){String s=emojis[i];add(r,s,1,standard(),()->send(s));}
    } else if(symbols){
      characters("1234567890");
      characters("@#$%&-+()");
      characters("*/:;!?.,");
    } else {
      LinearLayout numbers=row();
      for(int i=1;i<=10;i++){String s=""+(i%10);add(numbers,s,1,utility(),()->send(s));}
      for(String s:layouts[language])characters(s);
    }
    LinearLayout last=row();
    add(last,"⇧",1.4f,utility(),()->shift());
    add(last,",",0.9f,standard(),()->send(","));
    TextView space=key(names[language],5,standard());last.addView(space);
    space.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY;
      @Override public boolean onTouch(View v,MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
          float dx=e.getX()-startX,dy=e.getY()-startY;
          if(Math.abs(dx)>50*getResources().getDisplayMetrics().density && Math.abs(dx)>Math.abs(dy)*1.25f)lang(dx<0?1:-1);
          else send(" ");
          return true;
        }
        return true;
      }
    });
    add(last,".",0.9f,standard(),()->send("."));
    add(last,"↵",1.4f,utility(),()->{
      InputConnection c=getCurrentInputConnection();if(c!=null)c.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    });
  }
  @Override public View onCreateInputView(){
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(4,7,4,12);
    body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
    render();return root;
  }
  @Override public void onFinishInputView(boolean finishingInput){
    super.onFinishInputView(finishingInput);
  }
}
