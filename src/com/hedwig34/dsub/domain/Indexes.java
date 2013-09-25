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
package com.hedwig34.dsub.domain;

import java.util.List;
import java.io.Serializable;

/**
 * @author Sindre Mehus
 */
public class Indexes implements Serializable {

    private long lastModified;
    private List<Artist> shortcuts;
    private List<Artist> artists;

	public Indexes() {

	}
    public Indexes(long lastModified, List<Artist> shortcuts, List<Artist> artists) {
        this.lastModified = lastModified;
        this.shortcuts = shortcuts;
        this.artists = artists;
    }

    public long getLastModified() {
        return lastModified;
    }

    public List<Artist> getShortcuts() {
        return shortcuts;
    }

    public List<Artist> getArtists() {
        return artists;
    }
}