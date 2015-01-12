package com.littlewing.boxjump.app;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.littlewing.boxjump.app.R;


public class ArchAngle extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.music2);
        mp.start();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arch_angle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.arch_angle, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
