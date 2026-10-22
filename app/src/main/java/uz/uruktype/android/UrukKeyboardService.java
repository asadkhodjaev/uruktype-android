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
  private final String[] names={"EN·UZ","РУ·ЎЗ"};
  private final String[][] layouts={
    {"qwertyuiop","asdfghjkl","zxcvbnm"},
    {"йцукенгшщзх","фывапролджэ","ячсмитьбю"}
  };
  private final String[] emojis={"😀","😂","❤️","👍","🙏","🎉","🙂","✅","🔥","🌹"};
  private int language=0;
  private boolean uppercase=false,symbols=false,emoji=false,dark=false;
  private LinearLayout root,body,bar;
  private final List<String> suggestions=new ArrayList<>();
  private TextView key(String title,float weight,int color){
    TextView v=new TextView(this);
    v.setText(title);v.setTextSize(title.length()>4?13:21);v.setGravity(Gravity.CENTER);v.setTextColor(dark?Color.WHITE:Color.rgb(35,39,46));
    GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(7));v.setBackground(bg);v.setElevation(dp(1));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,(int)(50*getResources().getDisplayMetrics().density),weight);
    p.setMargins(dp(2),dp(3),dp(2),dp(3));v.setLayoutParams(p);return v;
  }
  private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
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
  // Long presses on base letters: one shared Latin and one shared Cyrillic layout.
  private String alternative(String letter){
    if(language==0){
      switch(letter){
        case "o": return "ö";
        case "g": return "ğ";
        case "s": return "ş";
        case "c": return "ç";
        default: return null;
      }
    } else {
      switch(letter){
        case "у": return "ў";
        case "г": return "ғ";
        case "х": return "ҳ";
        case "к": return "қ";
        default: return null;
      }
    }
  }
  private void characterRow(String characters,boolean lastLetterRow){
    LinearLayout r=row();
    if(lastLetterRow){
      add(r,"⇧",1.4f,utility(),()->shift());
    }
    for(String original:characters.split("")){
      if(original.isEmpty())continue;
      final String primary=uppercase?original.toUpperCase(java.util.Locale.ROOT):original;
      String alternateBase=(!symbols&&!emoji)?alternative(original):null;
      final String secondary=alternateBase==null?null:(uppercase?alternateBase.toUpperCase(java.util.Locale.ROOT):alternateBase);
      android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
      GradientDrawable bg=new GradientDrawable();
      bg.setColor(standard());bg.setCornerRadius(dp(7));
      frame.setBackground(bg);
      frame.setElevation(dp(1));
      LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(51),1f);
      cell.setMargins(dp(2),dp(3),dp(2),dp(3));
      r.addView(frame,cell);
      TextView main=new TextView(this);
      main.setText(primary);
      main.setTextSize(22);
      main.setGravity(Gravity.CENTER);
      main.setTextColor(dark?Color.WHITE:0xff21242a);
      frame.addView(main,new android.widget.FrameLayout.LayoutParams(-1,-1));
      if(secondary!=null){
        TextView hint=new TextView(this);
        hint.setText(secondary);
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(dark?0xff9ca3ae:0xff9299a5);
        android.widget.FrameLayout.LayoutParams hp=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
        hp.setMargins(0,dp(1),dp(3),0);
        frame.addView(hint,hp);
        frame.setContentDescription(primary+"; удерживайте для "+secondary);
        frame.setOnLongClickListener(v->{
          send(secondary);
          if(uppercase){uppercase=false;render();}
          return true;
        });
      }
      frame.setOnClickListener(v->{
        send(primary);
        if(uppercase){uppercase=false;render();}
      });
    }
    if(lastLetterRow){
      add(r,"⌫",1.4f,utility(),()->erase());
    }
  }
  // A long press on the period opens an actual two-option picker, rather
  // than automatically inserting punctuation or requiring a sliding gesture.
  private void showPunctuationChoices(View anchor){
    LinearLayout options=new LinearLayout(this);
    options.setOrientation(LinearLayout.HORIZONTAL);
    options.setPadding(dp(3),dp(3),dp(3),dp(3));
    android.graphics.drawable.GradientDrawable panel=new android.graphics.drawable.GradientDrawable();
    panel.setColor(dark?0xff30343c:0xfff2f4f8);
    panel.setCornerRadius(dp(6));
    options.setBackground(panel);
    final android.widget.PopupWindow popup=new android.widget.PopupWindow(options,dp(124),dp(56),false);
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    popup.setOutsideTouchable(true);
    popup.setElevation(dp(5));
    popup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
    for(String value:new String[]{",","?"}){
      TextView button=new TextView(this);
      button.setText(value);
      button.setContentDescription(value.equals(",")?"Comma":"Question mark");
      button.setTextSize(23);
      button.setGravity(Gravity.CENTER);
      button.setTextColor(dark?Color.WHITE:Color.rgb(35,39,46));
      android.graphics.drawable.GradientDrawable keyBackground=new android.graphics.drawable.GradientDrawable();
      keyBackground.setColor(standard());
      keyBackground.setCornerRadius(dp(5));
      button.setBackground(keyBackground);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
      lp.setMargins(dp(2),dp(1),dp(2),dp(1));
      options.addView(button,lp);
      button.setOnClickListener(v->{popup.dismiss();send(value);});
    }
    popup.showAsDropDown(anchor,-dp(58),-dp(118));
  }
  private void render(){
    if(root==null)return;
    root.setBackgroundColor(dark?0xff262930:0xffe4e6eb);
    body.removeAllViews();
    // Thin number row, no additional toolbar.
    LinearLayout digits=row();
    for(int n=1;n<=10;n++){
      String digit=String.valueOf(n%10);
      add(digits,digit,1f,standard(),()->send(digit));
    }
    if(emoji){
      for(int line=0;line<2;line++){
        LinearLayout r=row();
        for(int col=0;col<5;col++){
          String value=emojis[line*5+col];
          add(r,value,1f,standard(),()->send(value));
        }
      }
      LinearLayout r=row();
      add(r,"⇧",1.4f,utility(),()->shift());
      add(r,"🙂",8f,standard(),()->send("🙂"));
      add(r,"⌫",1.4f,utility(),()->erase());
    } else if(symbols){
      characterRow("@#$%&-+()",false);
      characterRow("*/:;!?.,",false);
      characterRow("[]{}<>_=",true);
    } else {
      characterRow(layouts[language][0],false);
      characterRow(layouts[language][1],false);
      // Shift directly above the ?123 key, Backspace directly above Enter.
      characterRow(layouts[language][2],true);
    }
    LinearLayout bottom=row();
    TextView mode=key((symbols||emoji)?"ABC":"?123",1.4f,utility());
    bottom.addView(mode);
    mode.setOnClickListener(v->{
      if(symbols||emoji){symbols=false;emoji=false;}
      else{symbols=true;emoji=false;}
      render();
    });
    mode.setOnLongClickListener(v->{emoji=!emoji;symbols=false;render();return true;});
    add(bottom,"◎",1f,utility(),()->lang(1));
    TextView space=key(names[language],5f,standard());
    bottom.addView(space);
    space.setTextSize(15);
    space.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY;
      @Override public boolean onTouch(View v,MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
          float dx=e.getX()-startX,dy=e.getY()-startY;
          if(Math.abs(dx)>dp(50)&&Math.abs(dx)>Math.abs(dy)*1.25f)lang(dx<0?1:-1);
          else send(" ");
          return true;
        }
        return true;
      }
    });
    TextView dot=key(".",0.9f,standard());
    dot.setContentDescription("Точка; удерживайте для запятой или вопросительного знака");
    bottom.addView(dot);
    dot.setOnClickListener(v->send("."));
    dot.setOnLongClickListener(v->{showPunctuationChoices(v);return true;});
    TextView enter=key("OK",1.4f,utility());
    enter.setTextColor(0xff367ad7);
    bottom.addView(enter);
    enter.setOnClickListener(v->{
      InputConnection c=getCurrentInputConnection();
      if(c!=null)c.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    });
  }
  @Override public View onCreateInputView(){
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(10),dp(10),dp(11));
    body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
    render();return root;
  }
  @Override public void onFinishInputView(boolean finishingInput){
    super.onFinishInputView(finishingInput);
  }
}
