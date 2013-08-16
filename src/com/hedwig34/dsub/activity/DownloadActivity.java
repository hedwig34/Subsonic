/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package com.hedwig34.dsub.activity;

import com.hedwig34.dsub.R;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import com.hedwig34.dsub.fragments.DownloadFragment;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.widget.EditText;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import com.hedwig34.dsub.domain.MusicDirectory;
import com.hedwig34.dsub.service.DownloadFile;
import com.hedwig34.dsub.service.MusicService;
import com.hedwig34.dsub.service.MusicServiceFactory;
import com.hedwig34.dsub.util.SilentBackgroundTask;
import com.hedwig34.dsub.util.Util;
import java.util.LinkedList;
import java.util.List;

public class DownloadActivity extends SubsonicActivity {
	private static final String TAG = DownloadActivity.class.getSimpleName();
	private EditText playlistNameView;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download_activity);

		if (findViewById(R.id.download_container) != null && savedInstanceState == null) {
			currentFragment = new DownloadFragment();
			currentFragment.setPrimaryFragment(true);
			getSupportFragmentManager().beginTransaction().add(R.id.download_container, currentFragment, currentFragment.getSupportTag() + "").commit();
		}

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == android.R.id.home) {
			Intent i = new Intent();
			i.setClass(this, MainActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		if(currentFragment != null) {
			return ((DownloadFragment)currentFragment).getGestureDetector().onTouchEvent(me);
		} else {
			return false;
		}
	}
	
	@Override
	public void onBackPressed() {
		if(onBackPressedSupport()) {
			super.onBackPressed();
		}
	}
}
