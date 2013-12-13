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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;

import com.hedwig34.dsub.fragments.SubsonicFragment;
import com.hedwig34.dsub.util.Constants;
import com.hedwig34.dsub.util.Util;
import com.hedwig34.dsub.provider.DSubSearchProvider;

/**
 * Receives search queries and forwards to the SearchFragment.
 *
 * @author Sindre Mehus
 */
public class QueryReceiverActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			doSearch();
		} else if(Intent.ACTION_VIEW.equals(intent.getAction())) {
			showResult(intent.getDataString(), intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
		}
        finish();
        Util.disablePendingTransition(this);
    }

	private void doSearch() {
		String query = getIntent().getStringExtra(SearchManager.QUERY);
		if (query != null) {
			Intent intent = new Intent(QueryReceiverActivity.this, SubsonicFragmentActivity.class);
			intent.putExtra(Constants.INTENT_EXTRA_NAME_QUERY, query);
			intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			Util.startActivityWithoutTransition(QueryReceiverActivity.this, intent);
		}
	}
	private void showResult(String albumId, String name) {
		if (albumId != null) {
			Intent intent = new Intent(this, SubsonicFragmentActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true);
			intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, albumId);
			if (name != null) {
				intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, name);
			}
			Util.startActivityWithoutTransition(this, intent);
		}
	}
}