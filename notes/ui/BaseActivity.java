package net.micode.notes.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import net.micode.notes.tool.LocaleHelper;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }
}
