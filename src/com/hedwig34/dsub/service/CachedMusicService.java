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
package com.hedwig34.dsub.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;

import android.content.Context;
import android.graphics.Bitmap;

import com.hedwig34.dsub.domain.Bookmark;
import com.hedwig34.dsub.domain.ChatMessage;
import com.hedwig34.dsub.domain.Genre;
import com.hedwig34.dsub.domain.Indexes;
import com.hedwig34.dsub.domain.RemoteStatus;
import com.hedwig34.dsub.domain.Lyrics;
import com.hedwig34.dsub.domain.MusicDirectory;
import com.hedwig34.dsub.domain.MusicFolder;
import com.hedwig34.dsub.domain.Playlist;
import com.hedwig34.dsub.domain.PodcastChannel;
import com.hedwig34.dsub.domain.SearchCritera;
import com.hedwig34.dsub.domain.SearchResult;
import com.hedwig34.dsub.domain.Share;
import com.hedwig34.dsub.domain.Version;
import com.hedwig34.dsub.util.CancellableTask;
import com.hedwig34.dsub.util.ProgressListener;
import com.hedwig34.dsub.util.TimeLimitedCache;
import com.hedwig34.dsub.util.FileUtil;
import com.hedwig34.dsub.util.Util;

/**
 * @author Sindre Mehus
 */
public class CachedMusicService implements MusicService {
	private static final String TAG = CachedMusicService.class.getSimpleName();

    private static final int MUSIC_DIR_CACHE_SIZE = 20;
    private static final int TTL_MUSIC_DIR = 5 * 60; // Five minutes

	private final RESTMusicService musicService;
    private final TimeLimitedCache<Boolean> cachedLicenseValid = new TimeLimitedCache<Boolean>(120, TimeUnit.SECONDS);
    private final TimeLimitedCache<Indexes> cachedIndexes = new TimeLimitedCache<Indexes>(60 * 60, TimeUnit.SECONDS);
    private final TimeLimitedCache<List<Playlist>> cachedPlaylists = new TimeLimitedCache<List<Playlist>>(3600, TimeUnit.SECONDS);
    private final TimeLimitedCache<List<MusicFolder>> cachedMusicFolders = new TimeLimitedCache<List<MusicFolder>>(10 * 3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<PodcastChannel>> cachedPodcastChannels = new TimeLimitedCache<List<PodcastChannel>>(10 * 3600, TimeUnit.SECONDS);
    private String restUrl;
	private boolean isTagBrowsing = false;

    public CachedMusicService(RESTMusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public void ping(Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        musicService.ping(context, progressListener);
    }

    @Override
    public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        Boolean result = cachedLicenseValid.get();
        if (result == null) {
			result = FileUtil.deserialize(context, getCacheName(context, "license"), Boolean.class);

			if(result == null) {
            	result = musicService.isLicenseValid(context, progressListener);

				// Only save a copy license is valid
				if(result == true) {
					FileUtil.serialize(context, (Boolean) result, getCacheName(context, "license"));
				}
			}
            cachedLicenseValid.set(result, result ? 30L * 60L : 2L * 60L, TimeUnit.SECONDS);
        }
        return result;
    }

    @Override
    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedMusicFolders.clear();
        }
        List<MusicFolder> result = cachedMusicFolders.get();
        if (result == null) {
        	if(!refresh) {
        		result = FileUtil.deserialize(context, getCacheName(context, "musicFolders"), ArrayList.class);
        	}
        	
        	if(result == null) {
            	result = musicService.getMusicFolders(refresh, context, progressListener);
            	FileUtil.serialize(context, new ArrayList<MusicFolder>(result), getCacheName(context, "musicFolders"));
        	}
            cachedMusicFolders.set(result);
        }
        return result;
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedIndexes.clear();
            cachedMusicFolders.clear();
        }
        Indexes result = cachedIndexes.get();
        if (result == null) {
            result = musicService.getIndexes(musicFolderId, refresh, context, progressListener);
            cachedIndexes.set(result);
        }
        return result;
    }

    @Override
    public MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;

		if(!refresh) {
			dir = FileUtil.deserialize(context, getCacheName(context, "directory", id), MusicDirectory.class);
		}

		if(dir == null) {
			dir = musicService.getMusicDirectory(id, name, refresh, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "directory", id));
		}

		return dir;
    }

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;

		if(!refresh) {
			dir = FileUtil.deserialize(context, getCacheName(context, "artist", id), MusicDirectory.class);
		}

		if(dir == null) {
			dir = musicService.getArtist(id, name, refresh, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "artist", id));
		}

		return dir;
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;

		if(!refresh) {
			dir = FileUtil.deserialize(context, getCacheName(context, "album", id), MusicDirectory.class);
		}

		if(dir == null) {
			dir = musicService.getAlbum(id, name, refresh, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "album", id));
		}

		return dir;
	}

	@Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
        return musicService.search(criteria, context, progressListener);
    }

    @Override
    public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;
		if(!refresh) {
			dir = FileUtil.deserialize(context, getCacheName(context, "playlist", id), MusicDirectory.class);
		}
		if(dir == null) {
			dir = musicService.getPlaylist(refresh, id, name, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "playlist", id));
		}
        return dir;
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        List<Playlist> result = refresh ? null : cachedPlaylists.get();
        if (result == null) {
        	if(!refresh) {
        		result = FileUtil.deserialize(context, getCacheName(context, "playlist"), ArrayList.class);
        	}
        	
        	if(result == null) {
	        	result = musicService.getPlaylists(refresh, context, progressListener);
	        	FileUtil.serialize(context, new ArrayList<Playlist>(result), getCacheName(context, "playlist"));
        	}
            cachedPlaylists.set(result);
        }
        return result;
    }

    @Override
    public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
		cachedPlaylists.clear();
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist")));
        musicService.createPlaylist(id, name, entries, context, progressListener);
    }
	
	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist")));
		musicService.deletePlaylist(id, context, progressListener);
	}
	
	@Override
	public void addToPlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist", id)));
		musicService.addToPlaylist(id, toAdd, context, progressListener);
	}
	
	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist", id)));
		musicService.removeFromPlaylist(id, toRemove, context, progressListener);
	}
	
	@Override
	public void overwritePlaylist(String id, String name, int toRemove, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist", id)));
		musicService.overwritePlaylist(id, name, toRemove, toAdd, context, progressListener);
	}
	
	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception {
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "playlist", id)));
		musicService.updatePlaylist(id, name, comment, pub, context, progressListener);
	}

    @Override
    public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getLyrics(artist, title, context, progressListener);
    }

    @Override
    public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception {
        musicService.scrobble(id, submission, context, progressListener);
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getAlbumList(type, size, offset, context, progressListener);
    }

	@Override
	public MusicDirectory getAlbumList(String type, String extra, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getAlbumList(type, extra, size, offset, context, progressListener);
	}

	@Override
    public MusicDirectory getStarredList(Context context, ProgressListener progressListener) throws Exception {
        return musicService.getStarredList(context, progressListener);
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getRandomSongs(size, folder, genre, startYear, endYear, context, progressListener);
    }

    @Override
    public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener) throws Exception {
        return musicService.getCoverArt(context, entry, size, progressListener);
    }

    @Override
    public HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception {
        return musicService.getDownloadInputStream(context, song, offset, maxBitrate, task);
    }

    @Override
    public Version getLocalVersion(Context context) throws Exception {
        return musicService.getLocalVersion(context);
    }

    @Override
    public Version getLatestVersion(Context context, ProgressListener progressListener) throws Exception {
        return musicService.getLatestVersion(context, progressListener);
    }

    @Override
    public String getVideoUrl(int maxBitrate, Context context, String id) {
        return musicService.getVideoUrl(maxBitrate, context, id);
    }
	
	@Override
    public String getVideoStreamUrl(String format, int maxBitrate, Context context, String id) throws Exception {
        return musicService.getVideoStreamUrl(format, maxBitrate, context, id);
    }
	
	@Override
	public String getHlsUrl(String id, int bitRate, Context context) throws Exception {
		return musicService.getHlsUrl(id, bitRate, context);
	}

    @Override
    public RemoteStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception {
        return musicService.updateJukeboxPlaylist(ids, context, progressListener);
    }

    @Override
    public RemoteStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception {
        return musicService.skipJukebox(index, offsetSeconds, context, progressListener);
    }

    @Override
    public RemoteStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception {
        return musicService.stopJukebox(context, progressListener);
    }

    @Override
    public RemoteStatus startJukebox(Context context, ProgressListener progressListener) throws Exception {
        return musicService.startJukebox(context, progressListener);
    }

    @Override
    public RemoteStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception {
        return musicService.getJukeboxStatus(context, progressListener);
    }

    @Override
    public RemoteStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception {
        return musicService.setJukeboxGain(gain, context, progressListener);
    }
    
	@Override
	public void setStarred(List<String> id, List<String> artistId, List<String> albumId, boolean starred, Context context, ProgressListener progressListener) throws Exception {
		musicService.setStarred(id, artistId, albumId, starred, context, progressListener);
	}
	
	@Override
	public List<Share> getShares(Context context, ProgressListener progressListener) throws Exception {
		return musicService.getShares(context, progressListener);	
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		return musicService.createShare(ids, description, expires, context, progressListener);
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception {
		musicService.deleteShare(id, context, progressListener);
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		musicService.updateShare(id, description, expires, context, progressListener);
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getChatMessages(since, context, progressListener);
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception {
		musicService.addChatMessage(message, context, progressListener);
	}
	
	@Override
	public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		List<Genre> result = null;

		if(!refresh) {
			result = FileUtil.deserialize(context, getCacheName(context, "genre"), ArrayList.class);
		}

		if(result == null) {
			result = musicService.getGenres(refresh, context, progressListener);
			FileUtil.serialize(context, new ArrayList<Genre>(result), getCacheName(context, "genre"));
		}

		return result;
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getSongsByGenre(genre, count, offset, context, progressListener);
	}
	
	@Override
	public List<PodcastChannel> getPodcastChannels(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		checkSettingsChanged(context);
		List<PodcastChannel> result = refresh ? null : cachedPodcastChannels.get();

		if (result == null) {
			if(!refresh) {
				result = FileUtil.deserialize(context, getCacheName(context, "podcast"), ArrayList.class);
			}
			
			if(result == null) {
				result = musicService.getPodcastChannels(refresh, context, progressListener);
				FileUtil.serialize(context, new ArrayList<PodcastChannel>(result), getCacheName(context, "podcast"));
			}
			cachedPodcastChannels.set(result);
		}

		return result;
	}
	
	@Override
	public MusicDirectory getPodcastEpisodes(boolean refresh, String id, Context context, ProgressListener progressListener) throws Exception {
		String altId = "p-" + id;
		MusicDirectory result = null;

		if(!refresh) {
			result = FileUtil.deserialize(context, getCacheName(context, "directory", altId), MusicDirectory.class, 10);
		}

		if(result == null) {
			result = musicService.getPodcastEpisodes(refresh, id, context, progressListener);
			FileUtil.serialize(context, result, getCacheName(context, "directory", altId));
		}

		return result;
	}
	
	@Override
	public void refreshPodcasts(Context context, ProgressListener progressListener) throws Exception {
		musicService.refreshPodcasts(context, progressListener);
	}
	
	@Override
	public void createPodcastChannel(String url, Context context, ProgressListener progressListener) throws Exception{
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "podcast")));
		cachedPodcastChannels.clear();
		musicService.createPodcastChannel(url, context, progressListener);
	}
	
	@Override
	public void deletePodcastChannel(String id, Context context, ProgressListener progressListener) throws Exception{
		Util.delete(new File(context.getCacheDir(), getCacheName(context, "podcast")));
		cachedPodcastChannels.clear();
		musicService.deletePodcastChannel(id, context, progressListener);
	}
	
	@Override
	public void downloadPodcastEpisode(String id, Context context, ProgressListener progressListener) throws Exception{
		musicService.downloadPodcastEpisode(id, context, progressListener);
	}
	
	@Override
	public void deletePodcastEpisode(String id, Context context, ProgressListener progressListener) throws Exception{
		musicService.deletePodcastEpisode(id, context, progressListener);
	}

	@Override
	public void setRating(String id, int rating, Context context, ProgressListener progressListener) throws Exception {
		musicService.setRating(id, rating, context, progressListener);
	}

	@Override
	public List<Bookmark> getBookmarks(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getBookmarks(refresh, context, progressListener);
	}

	@Override
	public void createBookmark(String id, int position, String comment, Context context, ProgressListener progressListener) throws Exception {
		musicService.createBookmark(id, position, comment, context, progressListener);
	}

	@Override
	public void deleteBookmark(String id, Context context, ProgressListener progressListener) throws Exception {
		musicService.deleteBookmark(id, context, progressListener);
	}

	@Override
	public int processOfflineSyncs(final Context context, final ProgressListener progressListener) throws Exception{
		return musicService.processOfflineSyncs(context, progressListener);
	}
	
	@Override
    public void setInstance(Integer instance) throws Exception {
    	musicService.setInstance(instance);
    }
  
  	private String getCacheName(Context context, String name, String id) {
  		String s = musicService.getRestUrl(context, null, false) + id;
  		return name + "-" + s.hashCode() + ".ser";
  	}
  	private String getCacheName(Context context, String name) {
  		String s = musicService.getRestUrl(context, null, false);
  		return name + "-" + s.hashCode() + ".ser";
  	}

    private void checkSettingsChanged(Context context) {
        String newUrl = musicService.getRestUrl(context, null, false);
		boolean newIsTagBrowsing = Util.isTagBrowsing(context);
        if (!Util.equals(newUrl, restUrl) || isTagBrowsing != newIsTagBrowsing) {
            cachedMusicFolders.clear();
            cachedLicenseValid.clear();
            cachedIndexes.clear();
            cachedPlaylists.clear();
			cachedPodcastChannels.clear();
            restUrl = newUrl;
			isTagBrowsing = newIsTagBrowsing;
        }
    }
}
