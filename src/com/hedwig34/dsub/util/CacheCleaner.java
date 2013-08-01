package com.hedwig34.dsub.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.os.StatFs;
import com.hedwig34.dsub.domain.Playlist;
import com.hedwig34.dsub.service.DownloadFile;
import com.hedwig34.dsub.service.DownloadService;
import java.util.*;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class CacheCleaner {

    private static final String TAG = CacheCleaner.class.getSimpleName();
	private static final long MIN_FREE_SPACE = 500 * 1024L * 1024L;

    private final Context context;
    private final DownloadService downloadService;

    public CacheCleaner(Context context, DownloadService downloadService) {
        this.context = context;
        this.downloadService = downloadService;
    }

    public void clean() {
		new BackgroundCleanup().execute();
    }
	public void cleanSpace() {
		new BackgroundSpaceCleanup().execute();
	}
	public void cleanPlaylists(List<Playlist> playlists) {
		new BackgroundPlaylistsCleanup().execute(playlists);
	}

    private void deleteEmptyDirs(List<File> dirs, Set<File> undeletable) {
        for (File dir : dirs) {
            if (undeletable.contains(dir)) {
                continue;
            }

            File[] children = dir.listFiles();
			
			// No songs left in the folder
			if(children.length == 1 && children[0].getPath().equals(FileUtil.getAlbumArtFile(dir).getPath())) {
				Util.delete(children[0]);
				children = dir.listFiles();
			}

            // Delete empty directory
            if (children.length == 0) {
                Util.delete(dir);
            }
        }
    }
	
	private long getMinimumDelete(List<File> files) {
		if(files.size() == 0) {
			return 0L;
		}
		
		long cacheSizeBytes = Util.getCacheSizeMB(context) * 1024L * 1024L;
		
        long bytesUsedBySubsonic = 0L;
        for (File file : files) {
            bytesUsedBySubsonic += file.length();
        }
		
		// Ensure that file system is not more than 95% full.
        StatFs stat = new StatFs(files.get(0).getPath());
        long bytesTotalFs = (long) stat.getBlockCount() * (long) stat.getBlockSize();
        long bytesAvailableFs = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        long bytesUsedFs = bytesTotalFs - bytesAvailableFs;
        long minFsAvailability = bytesTotalFs - MIN_FREE_SPACE;

        long bytesToDeleteCacheLimit = Math.max(bytesUsedBySubsonic - cacheSizeBytes, 0L);
        long bytesToDeleteFsLimit = Math.max(bytesUsedFs - minFsAvailability, 0L);
        long bytesToDelete = Math.max(bytesToDeleteCacheLimit, bytesToDeleteFsLimit);

        Log.i(TAG, "File system       : " + Util.formatBytes(bytesAvailableFs) + " of " + Util.formatBytes(bytesTotalFs) + " available");
        Log.i(TAG, "Cache limit       : " + Util.formatBytes(cacheSizeBytes));
        Log.i(TAG, "Cache size before : " + Util.formatBytes(bytesUsedBySubsonic));
        Log.i(TAG, "Minimum to delete : " + Util.formatBytes(bytesToDelete));
		
		return bytesToDelete;
	}

    private void deleteFiles(List<File> files, Set<File> undeletable, long bytesToDelete, boolean deletePartials) {
        if (files.isEmpty()) {
            return;
        }

        long bytesDeleted = 0L;
        for (File file : files) {
			if(!deletePartials && bytesDeleted > bytesToDelete) break;

            if (bytesToDelete > bytesDeleted || (deletePartials && (file.getName().endsWith(".partial") || file.getName().contains(".partial.")))) {
                if (!undeletable.contains(file) && !file.getName().equals(Constants.ALBUM_ART_FILE)) {
                    long size = file.length();
                    if (Util.delete(file)) {
                        bytesDeleted += size;
                    }
                }
            }
        }

        Log.i(TAG, "Deleted           : " + Util.formatBytes(bytesDeleted));
    }

    private void findCandidatesForDeletion(File file, List<File> files, List<File> dirs) {
        if (file.isFile()) {
            String name = file.getName();
            boolean isCacheFile = name.endsWith(".partial") || name.contains(".partial.") || name.endsWith(".complete") || name.contains(".complete.");
            if (isCacheFile) {
                files.add(file);
            }
        } else {
            // Depth-first
            for (File child : FileUtil.listFiles(file)) {
                findCandidatesForDeletion(child, files, dirs);
            }
            dirs.add(file);
        }
    }

    private void sortByAscendingModificationTime(List<File> files) {
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.lastModified() < b.lastModified()) {
                    return -1;
                }
                if (a.lastModified() > b.lastModified()) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private Set<File> findUndeletableFiles() {
        Set<File> undeletable = new HashSet<File>(5);

        for (DownloadFile downloadFile : downloadService.getDownloads()) {
            undeletable.add(downloadFile.getPartialFile());
            undeletable.add(downloadFile.getCompleteFile());
        }

        undeletable.add(FileUtil.getMusicDirectory(context));
        return undeletable;
    }
	
	private class BackgroundCleanup extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			if (downloadService == null) {
				Log.e(TAG, "DownloadService not set. Aborting cache cleaning.");
				return null;
			}

			try {
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();

				findCandidatesForDeletion(FileUtil.getMusicDirectory(context), files, dirs);
				sortByAscendingModificationTime(files);

				Set<File> undeletable = findUndeletableFiles();

				deleteFiles(files, undeletable, getMinimumDelete(files), true);
				deleteEmptyDirs(dirs, undeletable);
			} catch (RuntimeException x) {
				Log.e(TAG, "Error in cache cleaning.", x);
			}
			
			return null;
		}
	}
	
	private class BackgroundSpaceCleanup extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			if (downloadService == null) {
				Log.e(TAG, "DownloadService not set. Aborting cache cleaning.");
				return null;
			}

			try {
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();
				findCandidatesForDeletion(FileUtil.getMusicDirectory(context), files, dirs);
				
				long bytesToDelete = getMinimumDelete(files);
				if(bytesToDelete > 0L) {
					sortByAscendingModificationTime(files);
					Set<File> undeletable = findUndeletableFiles();
					deleteFiles(files, undeletable, bytesToDelete, false);
				}
			} catch (RuntimeException x) {
				Log.e(TAG, "Error in cache cleaning.", x);
			}
			
			return null;
		}
	}
	
	private class BackgroundPlaylistsCleanup extends AsyncTask<List<Playlist>, Void, Void> {
		@Override
		protected Void doInBackground(List<Playlist>... params) {
			try {
				String server = Util.getServerName(context);
				SortedSet<File> playlistFiles = FileUtil.listFiles(FileUtil.getPlaylistDirectory(server));
				List<Playlist> playlists = params[0];
				for (Playlist playlist : playlists) {
					playlistFiles.remove(FileUtil.getPlaylistFile(server, playlist.getName()));
				}
				
				for(File playlist : playlistFiles) {
					playlist.delete();
				}
			} catch (RuntimeException x) {
				Log.e(TAG, "Error in playlist cache cleaning.", x);
			}
			
			return null;
		}
	}
}
