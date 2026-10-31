package pt.tvatalhos;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Ecrã "A desligar…": volta a acender o ecrã por um instante quando se carrega no botão de
 * desligar, para o vigia poder escolher "Desligar" no menu da TV (em vez de ficar em standby).
 */
public class ShutdownActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView t = new TextView(this);
        t.setText("A desligar…");
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.BLACK);
        setContentView(t);
    }
}
