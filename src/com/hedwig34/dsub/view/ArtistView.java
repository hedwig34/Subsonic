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
import com.hedwig34.dsub.domain.Artist;
import com.hedwig34.dsub.util.FileUtil;
import com.hedwig34.dsub.util.Util;
import java.io.File;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class ArtistView extends UpdateView {
	private static final String TAG = ArtistView.class.getSimpleName();
	
	private Context context;
	private Artist artist;
	private File file;

    private TextView titleView;

    public ArtistView(Context context) {
        super(context);
		this.context = context;
        LayoutInflater.from(context).inflate(R.layout.artist_list_item, this, true);

        titleView = (TextView) findViewById(R.id.artist_name);
        starButton = (ImageButton) findViewById(R.id.artist_star);
		moreButton = (ImageView) findViewById(R.id.artist_more);
		moreButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.showContextMenu();
			}
		});
    }
    
    protected void setObjectImpl(Object obj) {
    	this.artist = (Artist) obj;
    	titleView.setText(artist.getName());
		file = FileUtil.getArtistDirectory(context, artist);
    }
    
    @Override
	protected void updateBackground() {
		exists = file.exists();
		isStarred = artist.isStarred();
	}
}
