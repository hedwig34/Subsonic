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

 Copyright 2010 (C) Sindre Mehus
 */
package com.hedwig34.dsub.view;

import android.widget.ArrayAdapter;
import android.widget.SectionIndexer;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.hedwig34.dsub.R;
import com.hedwig34.dsub.domain.Genre;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;

/**
 * @author Sindre Mehus
*/
public class GenreAdapter extends ArrayAdapter<Genre>{
	private Context activity;
	private List<Genre> genres;

    public GenreAdapter(Context context, List<Genre> genres) {
        super(context, android.R.layout.simple_list_item_1, genres);
		this.activity = context;
		this.genres = genres;
    }
	
	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Genre genre = genres.get(position);
		GenreView view;
		if (convertView != null && convertView instanceof GenreView) {
			view = (GenreView) convertView;
		} else {
			view = new GenreView(activity);
		}
		view.setObject(genre);
		return view;
    }
}
