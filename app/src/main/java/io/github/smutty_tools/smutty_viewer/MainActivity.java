package io.github.smutty_tools.smutty_viewer;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String DEBUG_TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true; // display the menu
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_share:
                return true; // consume the event
            case R.id.action_refresh:
                refreshAction();
                return true; // consume the event
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true; // consume the event
            default:
                return super.onOptionsItemSelected(item);
        }
    }

//    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            Log.d(DEBUG_TAG, "No network available");
        } else {
            Log.d(DEBUG_TAG, networkInfo.toString());
        }
        return networkInfo;
    }

    public void showToastMessage(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void refreshAction() {

//        one of TYPE_MOBILE, TYPE_WIFI, TYPE_WIMAX, TYPE_ETHERNET, TYPE_BLUETOOTH
        NetworkInfo ni = getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            showToastMessage(getString(R.string.toast_network_unavailable));
        }


        // TODO: check for connectivity
        // TODO: fetch metadata
        // TODO: fetch latest indexes
        // TODO: fetch content
        // TODO: ...
    }
}
