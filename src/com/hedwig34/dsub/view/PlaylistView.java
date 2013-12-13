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
package com.hedwig34.dsub.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.hedwig34.dsub.R;
import com.hedwig34.dsub.domain.Playlist;
import com.hedwig34.dsub.util.SyncUtil;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class PlaylistView extends UpdateView {
	private static final String TAG = PlaylistView.class.getSimpleName();

	private Context context;
	private Playlist playlist;

	private TextView titleView;

	public PlaylistView(Context context) {
		super(context);
		this.context = context;
		LayoutInflater.from(context).inflate(R.layout.basic_list_item, this, true);

		titleView = (TextView) findViewById(R.id.item_name);
		starButton = (ImageButton) findViewById(R.id.item_star);
		starButton.setFocusable(false);
		moreButton = (ImageView) findViewById(R.id.item_more);
		moreButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.showContextMenu();
			}
		});
	}

	protected void setObjectImpl(Object obj) {
		this.playlist = (Playlist) obj;
		titleView.setText(playlist.getName());
	}

	@Override
	protected void updateBackground() {
		pinned = SyncUtil.isSyncedPlaylist(context, playlist.getId());
	}
}
