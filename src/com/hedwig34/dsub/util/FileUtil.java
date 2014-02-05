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
package com.hedwig34.dsub.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import com.hedwig34.dsub.domain.Artist;
import com.hedwig34.dsub.domain.Genre;
import com.hedwig34.dsub.domain.Indexes;
import com.hedwig34.dsub.domain.Playlist;
import com.hedwig34.dsub.domain.PodcastChannel;
import com.hedwig34.dsub.domain.MusicDirectory;
import com.hedwig34.dsub.domain.MusicFolder;
import com.hedwig34.dsub.domain.PodcastChannel;
import com.hedwig34.dsub.domain.PodcastEpisode;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * @author Sindre Mehus
 */
public class FileUtil {

    private static final String TAG = FileUtil.class.getSimpleName();
    private static final String[] FILE_SYSTEM_UNSAFE = {"/", "\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
    private static final String[] FILE_SYSTEM_UNSAFE_DIR = {"\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
    private static final List<String> MUSIC_FILE_EXTENSIONS = Arrays.asList("mp3", "ogg", "aac", "flac", "m4a", "wav", "wma");
	private static final List<String> VIDEO_FILE_EXTENSIONS = Arrays.asList("flv", "mp4", "m4v", "wmv", "avi", "mov", "mpg", "mkv");
	private static final List<String> PLAYLIST_FILE_EXTENSIONS = Arrays.asList("m3u");
    private static final File DEFAULT_MUSIC_DIR = createDirectory("music");
	private static final Kryo kryo = new Kryo();

	static {
		kryo.register(MusicDirectory.Entry.class);
		kryo.register(Indexes.class);
		kryo.register(Artist.class);
		kryo.register(MusicFolder.class);
		kryo.register(PodcastChannel.class);
		kryo.register(Playlist.class);
		kryo.register(Genre.class);
	}
	
	public static File getAnySong(Context context) {
		File dir = getMusicDirectory(context);
		return getAnySong(context, dir);
	}
	private static File getAnySong(Context context, File dir) {
		for(File file: dir.listFiles()) {
			if(file.isDirectory()) {
				return getAnySong(context, file);
			}
			
			String extension = getExtension(file.getName());
			if(MUSIC_FILE_EXTENSIONS.contains(extension)) {
				return file;
			}
		}
		
		return null;
	}

    public static File getSongFile(Context context, MusicDirectory.Entry song) {
        File dir = getAlbumDirectory(context, song);

        StringBuilder fileName = new StringBuilder();
        Integer track = song.getTrack();
        if (track != null) {
            if (track < 10) {
                fileName.append("0");
            }
            fileName.append(track).append("-");
        }

        fileName.append(fileSystemSafe(song.getTitle())).append(".");

        if (song.getTranscodedSuffix() != null) {
            fileName.append(song.getTranscodedSuffix());
        } else {
            fileName.append(song.getSuffix());
        }

        return new File(dir, fileName.toString());
    }
	
	public static File getPlaylistFile(String server, String name) {
		File playlistDir = getPlaylistDirectory(server);
		return new File(playlistDir, fileSystemSafe(name) + ".m3u");
	}
	public static File getPlaylistDirectory() {
		File playlistDir = new File(getSubsonicDirectory(), "playlists");
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}
	public static File getPlaylistDirectory(String server) {
		File playlistDir = new File(getPlaylistDirectory(), server);
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}

    public static File getAlbumArtFile(Context context, MusicDirectory.Entry entry) {
        File albumDir = getAlbumDirectory(context, entry);
		File artFile;
		File albumFile = getAlbumArtFile(albumDir);
		File hexFile = getHexAlbumArtFile(albumDir);
		if(albumDir.exists()) {
			if(hexFile.exists()) {
				hexFile.renameTo(albumFile);
			}
			artFile = albumFile;
		} else {
			artFile = hexFile;
		}
        return artFile;
    }

    public static File getAlbumArtFile(File albumDir) {
        return new File(albumDir, Constants.ALBUM_ART_FILE);
    }
	public static File getHexAlbumArtFile(File albumDir) {
		return new File(getAlbumArtDirectory(), Util.md5Hex(albumDir.getPath()) + ".jpeg");
	}

    public static Bitmap getAlbumArtBitmap(Context context, MusicDirectory.Entry entry, int size) {
        File albumArtFile = getAlbumArtFile(context, entry);
        if (albumArtFile.exists()) {
			final BitmapFactory.Options opt = new BitmapFactory.Options();
			opt.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(albumArtFile.getPath(), opt);
			opt.inPurgeable = true;
			opt.inSampleSize = Util.calculateInSampleSize(opt, size, Util.getScaledHeight(opt.outHeight, opt.outWidth, size));
			opt.inJustDecodeBounds = false;

			Bitmap bitmap = BitmapFactory.decodeFile(albumArtFile.getPath(), opt);
			return bitmap == null ? null : getScaledBitmap(bitmap, size);
        }
        return null;
    }
	public static Bitmap getSampledBitmap(byte[] bytes, int size) {
		final BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
		opt.inPurgeable = true;
		opt.inSampleSize = Util.calculateInSampleSize(opt, size, Util.getScaledHeight(opt.outHeight, opt.outWidth, size));
		opt.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
		return getScaledBitmap(bitmap, size);
	}
	public static Bitmap getScaledBitmap(Bitmap bitmap, int size) {
		return Bitmap.createScaledBitmap(bitmap, size, Util.getScaledHeight(bitmap, size), true);
	}

	public static File getAlbumArtDirectory() {
		File albumArtDir = new File(getSubsonicDirectory(), "artwork");
		ensureDirectoryExistsAndIsReadWritable(albumArtDir);
		ensureDirectoryExistsAndIsReadWritable(new File(albumArtDir, ".nomedia"));
		return albumArtDir;
	}
	
	public static File getArtistDirectory(Context context, Artist artist) {
		File dir = new File(getMusicDirectory(context).getPath() + "/" + fileSystemSafe(artist.getName()));
		return dir;
	}
	public static File getArtistDirectory(Context context, MusicDirectory.Entry artist) {
		File dir = new File(getMusicDirectory(context).getPath() + "/" + fileSystemSafe(artist.getTitle()));
		return dir;
	}

    public static File getAlbumDirectory(Context context, MusicDirectory.Entry entry) {
        File dir = null;
        if (entry.getPath() != null) {
            File f = new File(fileSystemSafeDir(entry.getPath()));
            dir = new File(getMusicDirectory(context).getPath() + "/" + (entry.isDirectory() ? f.getPath() : f.getParent()));
        } else {
			// Do a special lookup since 4.7+ doesn't match artist/album to entry.getPath
			String s = Util.getRestUrl(context, null, false) + entry.getId();
			String cacheName = (Util.isTagBrowsing(context) ? "album-" : "directory-") + s.hashCode() + ".ser";
			MusicDirectory entryDir = FileUtil.deserialize(context, cacheName, MusicDirectory.class);

			if(entryDir != null) {
				List<MusicDirectory.Entry> songs = entryDir.getChildren(false, true);
				if(songs.size() > 0) {
					MusicDirectory.Entry firstSong = songs.get(0);
					File songFile = FileUtil.getSongFile(context, firstSong);
					dir = songFile.getParentFile();
				}
			}

			if(dir == null) {
				String artist = fileSystemSafe(entry.getArtist());
				String album = fileSystemSafe(entry.getAlbum());
				if("unnamed".equals(album)) {
					album = fileSystemSafe(entry.getTitle());
				}
				dir = new File(getMusicDirectory(context).getPath() + "/" + artist + "/" + album);
			}
        }
        return dir;
    }
	
	public static String getPodcastPath(Context context, PodcastEpisode episode) {
		return fileSystemSafe(episode.getArtist()) + "/" + fileSystemSafe(episode.getTitle());
	}
	public static File getPodcastFile(Context context, String server) {
		File dir = getPodcastDirectory(context);
		return new File(dir.getPath() + "/" +  fileSystemSafe(server));
	}
	public static File getPodcastDirectory(Context context) {
		File dir = new File(getSubsonicDirectory(), "podcasts");
		ensureDirectoryExistsAndIsReadWritable(dir);
		return dir;
	}
	public static File getPodcastDirectory(Context context, PodcastChannel channel) {
		File dir = new File(getMusicDirectory(context).getPath() + "/" + fileSystemSafe(channel.getName()));
		return dir;
	}
	public static File getPodcastDirectory(Context context, String channel) {
		File dir = new File(getMusicDirectory(context).getPath() + "/" + fileSystemSafe(channel));
		return dir;
	}

    public static void createDirectoryForParent(File file) {
        File dir = file.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create directory " + dir);
            }
        }
    }

    private static File createDirectory(String name) {
        File dir = new File(getSubsonicDirectory(), name);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create " + name);
        }
        return dir;
    }

    public static File getSubsonicDirectory() {
        return new File(Environment.getExternalStorageDirectory(), "subsonic");
    }

    public static File getDefaultMusicDirectory() {
        return DEFAULT_MUSIC_DIR;
    }

    public static File getMusicDirectory(Context context) {
        String path = Util.getPreferences(context).getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, DEFAULT_MUSIC_DIR.getPath());
        File dir = new File(path);
        return ensureDirectoryExistsAndIsReadWritable(dir) ? dir : getDefaultMusicDirectory();
    }
	public static boolean deleteMusicDirectory(Context context) {
		File musicDirectory = FileUtil.getMusicDirectory(context);
		return Util.recursiveDelete(musicDirectory);
	}
	public static void deleteSerializedCache(Context context) {
		for(File file: context.getCacheDir().listFiles()) {
			if(file.getName().indexOf(".ser") != -1) {
				file.delete();
			}
		}
	}

	public static void unpinSong(File saveFile) {
		// Unpin file, rename to .complete
		File completeFile = new File(saveFile.getParent(), FileUtil.getBaseName(saveFile.getName()) +
				".complete." + FileUtil.getExtension(saveFile.getName()));

		if(!saveFile.renameTo(completeFile)) {
			Log.w(TAG, "Failed to rename " + saveFile + " to " + completeFile);
		}
	}

    public static boolean ensureDirectoryExistsAndIsReadWritable(File dir) {
        if (dir == null) {
            return false;
        }

        if (dir.exists()) {
            if (!dir.isDirectory()) {
                Log.w(TAG, dir + " exists but is not a directory.");
                return false;
            }
        } else {
            if (dir.mkdirs()) {
                Log.i(TAG, "Created directory " + dir);
            } else {
                Log.w(TAG, "Failed to create directory " + dir);
                return false;
            }
        }

        if (!dir.canRead()) {
            Log.w(TAG, "No read permission for directory " + dir);
            return false;
        }

        if (!dir.canWrite()) {
            Log.w(TAG, "No write permission for directory " + dir);
            return false;
        }
        return true;
    }

    /**
    * Makes a given filename safe by replacing special characters like slashes ("/" and "\")
    * with dashes ("-").
    *
    * @param filename The filename in question.
    * @return The filename with special characters replaced by hyphens.
    */
    private static String fileSystemSafe(String filename) {
        if (filename == null || filename.trim().length() == 0) {
            return "unnamed";
        }

        for (String s : FILE_SYSTEM_UNSAFE) {
            filename = filename.replace(s, "-");
        }
        return filename;
    }

    /**
     * Makes a given filename safe by replacing special characters like colons (":")
     * with dashes ("-").
     *
     * @param path The path of the directory in question.
     * @return The the directory name with special characters replaced by hyphens.
     */
    private static String fileSystemSafeDir(String path) {
        if (path == null || path.trim().length() == 0) {
            return "";
        }

        for (String s : FILE_SYSTEM_UNSAFE_DIR) {
            path = path.replace(s, "-");
        }
        return path;
    }

    /**
     * Similar to {@link File#listFiles()}, but returns a sorted set.
     * Never returns {@code null}, instead a warning is logged, and an empty set is returned.
     */
    public static SortedSet<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            Log.w(TAG, "Failed to list children for " + dir.getPath());
            return new TreeSet<File>();
        }

        return new TreeSet<File>(Arrays.asList(files));
    }

    public static SortedSet<File> listMediaFiles(File dir) {
        SortedSet<File> files = listFiles(dir);
        Iterator<File> iterator = files.iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (!file.isDirectory() && !isMediaFile(file)) {
                iterator.remove();
            }
        }
        return files;
    }

    private static boolean isMediaFile(File file) {
        String extension = getExtension(file.getName());
        return MUSIC_FILE_EXTENSIONS.contains(extension) || VIDEO_FILE_EXTENSIONS.contains(extension);
    }
	
	public static boolean isMusicFile(File file) {
		String extension = getExtension(file.getName());
        return MUSIC_FILE_EXTENSIONS.contains(extension);
	}
	public static boolean isVideoFile(File file) {
		String extension = getExtension(file.getName());
        return VIDEO_FILE_EXTENSIONS.contains(extension);
	}
	
	public static boolean isPlaylistFile(File file) {
		String extension = getExtension(file.getName());
		return PLAYLIST_FILE_EXTENSIONS.contains(extension);
	}

    /**
     * Returns the extension (the substring after the last dot) of the given file. The dot
     * is not included in the returned extension.
     *
     * @param name The filename in question.
     * @return The extension, or an empty string if no extension is found.
     */
    public static String getExtension(String name) {
        int index = name.lastIndexOf('.');
        return index == -1 ? "" : name.substring(index + 1).toLowerCase();
    }

    /**
     * Returns the base name (the substring before the last dot) of the given file. The dot
     * is not included in the returned basename.
     *
     * @param name The filename in question.
     * @return The base name, or an empty string if no basename is found.
     */
    public static String getBaseName(String name) {
        int index = name.lastIndexOf('.');
        return index == -1 ? name : name.substring(0, index);
    }
	
	public static long getUsedSize(Context context, File file) {
		long size = 0L;
		
		if(file.isFile()) {
			return file.length();
		} else {
			for (File child : FileUtil.listFiles(file)) {
				size += getUsedSize(context, child);
			}
			return size;
		}
	}

    public static <T extends Serializable> boolean serialize(Context context, T obj, String fileName) {
		synchronized (kryo) {
			Output out = null;
			try {
				RandomAccessFile file = new RandomAccessFile(context.getCacheDir() + "/" + fileName, "rw");
				out = new Output(new FileOutputStream(file.getFD()));
				kryo.writeObject(out, obj);
				Log.i(TAG, "Serialized object to " + fileName);
				return true;
			} catch (Throwable x) {
				Log.w(TAG, "Failed to serialize object to " + fileName);
				return false;
			} finally {
				Util.close(out);
			}
		}
    }

	public static <T extends Serializable> T deserialize(Context context, String fileName, Class<T> tClass) {
		return deserialize(context, fileName, tClass, 0);
	}

    public static <T extends Serializable> T deserialize(Context context, String fileName, Class<T> tClass, int hoursOld) {
		synchronized (kryo) {
			Input in = null;
			try {
				File file = new File(context.getCacheDir(), fileName);

				if(hoursOld != 0) {
					Date fileDate = new Date(file.lastModified());
					// Convert into hours
					long age = (new Date().getTime() - fileDate.getTime()) / 1000 / 3600;
					if(age > hoursOld) {
						return null;
					}
				}

				RandomAccessFile randomFile = new RandomAccessFile(file, "r");

				in = new Input(new FileInputStream(randomFile.getFD()));
				T result = (T) kryo.readObject(in, tClass);
				Log.i(TAG, "Deserialized object from " + fileName);
				return result;
			} catch(FileNotFoundException e) {
				// Different error message
				Log.w(TAG, "No serialization for object from " + fileName);
				return null;
			} catch (Throwable x) {
				Log.w(TAG, "Failed to deserialize object from " + fileName, x);
				return null;
			} finally {
				Util.close(in);
			}
		}
    }

	public static <T extends Serializable> boolean serializeCompressed(Context context, T obj, String fileName) {
		synchronized (kryo) {
			Output out = null;
			try {
				RandomAccessFile file = new RandomAccessFile(context.getCacheDir() + "/" + fileName, "rw");
				out = new Output(new DeflaterOutputStream(new FileOutputStream(file.getFD())));
				kryo.writeObject(out, obj);
				Log.i(TAG, "Serialized compressed object to " + fileName);
				return true;
			} catch (Throwable x) {
				Log.w(TAG, "Failed to serialize compressed object to " + fileName);
				return false;
			} finally {
				Util.close(out);
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static <T extends Serializable> T deserializeCompressed(Context context, String fileName, Class<T> tClass) {
		synchronized (kryo) {
			Input in = null;
			try {
				RandomAccessFile file = new RandomAccessFile(context.getCacheDir() + "/" + fileName, "r");

				in = new Input(new InflaterInputStream(new FileInputStream(file.getFD())));
				T result = (T) kryo.readObject(in, tClass);
				Log.i(TAG, "Deserialized compressed object from " + fileName);
				return result;
			} catch(FileNotFoundException e) {
				// Different error message
				Log.w(TAG, "No serialization compressed for object from " + fileName);
				return null;
			} catch (Throwable x) {
				Log.w(TAG, "Failed to deserialize compressed object from " + fileName, x);
				return null;
			} finally {
				Util.close(in);
			}
		}
	}
}
