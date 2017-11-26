package io.github.smutty_tools.smutty_viewer.Tools;

import android.content.Context;
import android.widget.Toast;

public class Toaster {

    Context context;

    public Toaster(Context context) {
        this.context = context;
    }

    public void display(String message) {
        CharSequence text = message;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
