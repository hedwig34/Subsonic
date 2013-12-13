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
package com.hedwig34.dsub.updates;

import android.content.Context;
import android.util.Log;
import com.hedwig34.dsub.updates.Updater;
import com.hedwig34.dsub.util.Constants;
import com.hedwig34.dsub.util.FileUtil;
import java.io.File;

/**
 *
 * @author Scott
 */
public class Updater403 extends Updater {	
	public Updater403() {
		super(403);
		TAG = Updater403.class.getSimpleName();
	}
	
	@Override
	public void update(Context context) {
		// Rename cover.jpeg to cover.jpg
		Log.i(TAG, "Running Updater403: updating cover.jpg to albumart.jpg");
		File dir = FileUtil.getMusicDirectory(context);
		if(dir != null) {
			moveArt(dir);
		}
	}
	
	private void moveArt(File dir) {
		for(File file: dir.listFiles()) {
			if(file.isDirectory()) {
				moveArt(file);
			} else if("cover.jpg".equals(file.getName()) || "cover.jpeg".equals(file.getName())) {
				File renamed = new File(dir, Constants.ALBUM_ART_FILE);
				file.renameTo(renamed);
			}
		}
	}
}
