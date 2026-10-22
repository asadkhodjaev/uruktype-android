package uz.uruktype.android;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(34,60,34,20);
  TextView intro=new TextView(this);intro.setText("EasyType — тестовая версия\n\n1. Включите EasyType в списке клавиатур.\n2. Выберите EasyType как текущую клавиатуру.\n3. Для переключения языков проведите пальцем по пробелу.\n\nНе используйте для ввода секретных данных, пока приложение не прошло аудит.");intro.setTextSize(19);root.addView(intro);
  TextView enable=button("1. Включить клавиатуру");root.addView(enable);enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
  TextView choose=button("2. Выбрать клавиатуру");root.addView(choose);choose.setOnClickListener(v->{InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);m.showInputMethodPicker();});
  setContentView(root);
 }
 private TextView button(String label){TextView v=new TextView(this);v.setText(label);v.setTextSize(21);v.setPadding(12,34,12,34);v.setGravity(Gravity.CENTER);return v;}
}
