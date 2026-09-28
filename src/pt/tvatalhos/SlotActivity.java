package pt.tvatalhos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Aberta pelos ícones "Atalho N": lança a app atribuída a esse atalho. */
public class SlotActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String cls = getIntent().getComponent() != null ? getIntent().getComponent().getClassName() : "";
        String pkg = null;
        for (int s = 1; s <= Apps.SLOTS; s++) {
            if (cls.endsWith(".Atalho" + s)) pkg = Apps.prefs(this).getString(Apps.slotKey(s), null);
        }
        if (pkg == null || !Apps.open(this, pkg)) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
