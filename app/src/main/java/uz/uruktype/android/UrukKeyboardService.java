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
  private final String[] names={"eng","рус"};
  private final String[][] layouts={
    {"qwertyuiop","asdfghjkl","zxcvbnm"},
    {"йцукенгшщзх","фывапролджэ","ячсмитьбю"}
  };
  private final String[] emojis={"😀","😂","❤️","👍","🙏","🎉","🙂","✅","🔥","🌹"};
  private int language=0;
  private boolean uppercase=false,symbols=false,emoji=false,dark=false;
  private LinearLayout root,body,bar;
  private android.widget.PopupWindow punctuationPopup;
  private View letterKeySample;
  private final TextView[] punctuationOptions=new TextView[2];
  private int punctuationLeft,punctuationTop,punctuationWidth,punctuationHeight;
  private final List<String> suggestions=new ArrayList<>();
  private TextView key(String title,float weight,int color){
    TextView v=new TextView(this);
    v.setText(title);v.setTextSize(title.length()>1?16:22);v.setGravity(Gravity.CENTER);v.setTextColor(dark?Color.WHITE:Color.rgb(35,39,46));
    v.setIncludeFontPadding(false);
    GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(9));v.setBackground(bg);v.setElevation(dp(1));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),weight);
    p.setMargins(dp(2),dp(3),dp(2),dp(3));v.setLayoutParams(p);
    v.setOnTouchListener((view,event)->{pressFeedback(view,event);return false;});return v;
  }
  private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
  private int standard(){return dark?0xff454951:0xffffffff;}
  private int utility(){return dark?0xff555a63:0xffc9cacc;}
  private LinearLayout row(){
    LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);r.setOrientation(LinearLayout.HORIZONTAL);
    r.setClipChildren(false);r.setClipToPadding(false);
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
    language=(language+names.length+delta)%names.length;
    getSharedPreferences("keyboard",MODE_PRIVATE).edit().putInt("language",language).apply();
    render();
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
        case "е": return "ё";
        case "ь": return "ъ";
        default: return null;
      }
    }
  }
  private void characterRow(String characters,boolean lastLetterRow){
    LinearLayout r=row();
    if(lastLetterRow){
      add(r,"⇧",1.4f,utility(),()->shift());
    }
    LinearLayout letters=r;
    if(lastLetterRow){
      letters=new LinearLayout(this);
      letters.setOrientation(LinearLayout.HORIZONTAL);letters.setClipChildren(false);letters.setClipToPadding(false);
      r.addView(letters,new LinearLayout.LayoutParams(0,-2,9f));
    }
    for(String original:characters.split("")){
      if(original.isEmpty())continue;
      final String primary=uppercase?original.toUpperCase(java.util.Locale.ROOT):original;
      String alternateBase=(!symbols&&!emoji)?alternative(original):null;
      final String secondary=alternateBase==null?null:(uppercase?alternateBase.toUpperCase(java.util.Locale.ROOT):alternateBase);
      android.widget.FrameLayout frame=new android.widget.FrameLayout(this);
      GradientDrawable bg=new GradientDrawable();
      bg.setColor(standard());bg.setCornerRadius(dp(9));
      frame.setBackground(bg);
      frame.setElevation(dp(1));
      LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(45.9f),1f);
      cell.setMargins(dp(2),dp(3),dp(2),dp(3));
      letters.addView(frame,cell);
      frame.setOnTouchListener((view,event)->{pressFeedback(view,event);return false;});
      if(letterKeySample==null)letterKeySample=frame;
      TextView main=new TextView(this);
      main.setText(primary);
      main.setTextSize(22);
      main.setIncludeFontPadding(false);
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
          confirmFeedback(v);
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
  // Keep the original dot touch stream: hold, slide to an option, release.
  private void showPunctuationChoices(View anchor){
    dismissPunctuation();
    int keyWidth=letterKeySample!=null&&letterKeySample.getWidth()>0?letterKeySample.getWidth():dp(30);
    int keyHeight=letterKeySample!=null&&letterKeySample.getHeight()>0?letterKeySample.getHeight():dp(45.9f);
    int padding=dp(3),gap=dp(4);
    punctuationWidth=2*keyWidth+gap+2*padding;
    punctuationHeight=keyHeight+2*padding;
    LinearLayout options=new LinearLayout(this);
    options.setOrientation(LinearLayout.HORIZONTAL);
    options.setPadding(padding,padding,padding,padding);
    GradientDrawable panel=new GradientDrawable();
    panel.setColor(dark?0xff30343c:0xfff2f4f8);panel.setCornerRadius(dp(9));
    options.setBackground(panel);
    android.widget.PopupWindow popup=new android.widget.PopupWindow(options,punctuationWidth,punctuationHeight,false);
    punctuationPopup=popup;
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    popup.setTouchable(false); // Finger events continue to the dot, even over this panel.
    popup.setOutsideTouchable(false);
    popup.setElevation(dp(5));
    popup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
    String[] values={"?",","};
    for(int i=0;i<values.length;i++){
      TextView button=new TextView(this);
      button.setText(values[i]);button.setTextSize(22);button.setIncludeFontPadding(false);
      button.setGravity(Gravity.CENTER);
      button.setTextColor(dark?Color.WHITE:0xff21242a);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(keyWidth,keyHeight);
      if(i==1)lp.leftMargin=gap;
      options.addView(button,lp);punctuationOptions[i]=button;
    }
    highlightPunctuation(-1);
    int[] position=new int[2];anchor.getLocationOnScreen(position);
    punctuationLeft=Math.max(dp(4),Math.min(position[0]+anchor.getWidth()/2-punctuationWidth/2,
      getResources().getDisplayMetrics().widthPixels-punctuationWidth-dp(4)));
    popup.showAsDropDown(anchor,punctuationLeft-position[0],-anchor.getHeight()-punctuationHeight-dp(6));
    // Use the actual panel bounds after the window manager has positioned it.
    options.post(()->{
      if(punctuationPopup==popup){
        int[] actual=new int[2];options.getLocationOnScreen(actual);
        punctuationLeft=actual[0];punctuationTop=actual[1];
      }
    });
    punctuationTop=position[1]-punctuationHeight-dp(6);
  }
  private void highlightPunctuation(int selected){
    for(int i=0;i<punctuationOptions.length;i++){
      TextView option=punctuationOptions[i];if(option==null)continue;
      GradientDrawable background=new GradientDrawable();
      background.setColor(i==selected?0xffc4daf8:standard());background.setCornerRadius(dp(9));
      option.setBackground(background);
    }
  }
  private void bindPunctuationGesture(View dot){
    dot.setOnTouchListener(new View.OnTouchListener(){
      final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
      float downX,downY;
      boolean active,held;
      int selected=-1;
      final int slop=android.view.ViewConfiguration.get(UrukKeyboardService.this).getScaledTouchSlop();
      final Runnable hold=()->{
        if(active){held=true;showPunctuationChoices(dot);}
      };
      @Override public boolean onTouch(View v,MotionEvent event){
        pressFeedback(v,event);
        switch(event.getActionMasked()){
          case MotionEvent.ACTION_DOWN:
            downX=event.getRawX();downY=event.getRawY();active=true;held=false;selected=-1;
            v.setPressed(true);
            handler.postDelayed(hold,android.view.ViewConfiguration.getLongPressTimeout());
            return true;
          case MotionEvent.ACTION_MOVE:
            if(!active)return true;
            float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
            if(!held){
              if(Math.hypot(dx,dy)>slop){handler.removeCallbacks(hold);active=false;v.setPressed(false);pressedVisual(v,false);}
            } else {
              selected=PunctuationSwipe.choose(event.getRawX(),event.getRawY(),downX,downY,
                punctuationLeft,punctuationTop,punctuationWidth,punctuationHeight,v.getHeight(),slop);
              highlightPunctuation(selected);
            }
            return true;
          case MotionEvent.ACTION_UP:
            handler.removeCallbacks(hold);v.setPressed(false);
            if(active){
              if(held){
                selected=PunctuationSwipe.choose(event.getRawX(),event.getRawY(),downX,downY,
                  punctuationLeft,punctuationTop,punctuationWidth,punctuationHeight,v.getHeight(),slop);
                if(selected>=0){send(selected==0?"?":",");confirmFeedback(v);}
              } else {v.performClick();}
            }
            active=false;dismissPunctuation();return true;
          case MotionEvent.ACTION_CANCEL:
            handler.removeCallbacks(hold);active=false;v.setPressed(false);dismissPunctuation();return true;
          default:return true;
        }
      }
    });
  }
  private void dismissPunctuation(){
    if(punctuationPopup!=null){punctuationPopup.dismiss();punctuationPopup=null;}
    punctuationOptions[0]=null;punctuationOptions[1]=null;
  }
  private void pressedVisual(View view,boolean pressed){
    view.setScaleX(pressed?1.3f:1f);view.setScaleY(pressed?1.3f:1f);
    view.setTranslationZ(pressed?dp(8):0);
  }
  private void pressFeedback(View view,MotionEvent event){
    switch(event.getActionMasked()){
      case MotionEvent.ACTION_DOWN:
        pressedVisual(view,true);vibrateSoft(view,8,36);break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        pressedVisual(view,false);break;
    }
  }
  private void vibrateSoft(View view,int duration,int amplitude){
    android.os.Vibrator vibrator=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
    if(vibrator!=null&&vibrator.hasVibrator()){
      vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration,
        vibrator.hasAmplitudeControl()?amplitude:android.os.VibrationEffect.DEFAULT_AMPLITUDE));
    } else view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
  }
  private void confirmFeedback(View view){vibrateSoft(view,12,60);}
  private void render(){
    if(root==null)return;
    dismissPunctuation();
    root.setBackgroundColor(dark?0xff262930:0xffe3e3e7);
    body.removeAllViews();
    letterKeySample=null;
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
    TextView mode=key((symbols||emoji)?"abc":"123",1.4f,utility());
    bottom.addView(mode);
    mode.setOnClickListener(v->{
      if(symbols||emoji){symbols=false;emoji=false;}
      else{symbols=true;emoji=false;}
      render();
    });
    mode.setOnLongClickListener(v->{emoji=!emoji;symbols=false;render();return true;});
    TextView space=key(names[language],8.1f,standard());
    bottom.addView(space);
    space.setTextSize(15);
    space.setTextColor(dark?0xffa5a5aa:0xff85858b);
    space.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY;
      @Override public boolean onTouch(View v,MotionEvent e){
        pressFeedback(v,e);
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
    dot.setContentDescription("Точка; удерживайте и проведите к вопросительному знаку слева или запятой справа");
    bottom.addView(dot);
    dot.setOnClickListener(v->send("."));
    bindPunctuationGesture(dot);
    TextView enter=key("OK",1.4f,utility());
    enter.setTextColor(0xff367ad7);
    bottom.addView(enter);
    android.view.inputmethod.EditorInfo info=getCurrentInputEditorInfo();
    int action=info==null?0:info.imeOptions&android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
    boolean newline=info==null||(info.imeOptions&android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0
      ||action==android.view.inputmethod.EditorInfo.IME_ACTION_NONE||action==android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED;
    enter.setText(newline?"↵":action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH?"⌕":
      action==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT?"→":"OK");
    enter.setTextSize(newline||action==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT?26:
      action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH?24:16);
    enter.setContentDescription(newline?"Новая строка":"Подтвердить ввод");
    enter.setOnClickListener(v->{
      InputConnection c=getCurrentInputConnection();
      if(c!=null){if(newline)c.commitText("\n",1);else c.performEditorAction(action);}
    });
  }
  @Override public View onCreateInputView(){
    language=getSharedPreferences("keyboard",MODE_PRIVATE).getInt("language",0)%names.length;
    root=new LinearLayout(this);root.setClipChildren(false);root.setClipToPadding(false);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(10),dp(10),dp(11));
    body=new LinearLayout(this);body.setClipChildren(false);body.setClipToPadding(false);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
    render();return root;
  }
  @Override public void onFinishInputView(boolean finishingInput){
    dismissPunctuation();
    super.onFinishInputView(finishingInput);
  }
  @Override public void onStartInputView(android.view.inputmethod.EditorInfo info,boolean restarting){
    super.onStartInputView(info,restarting);uppercase=false;symbols=false;emoji=false;render();
  }
}

