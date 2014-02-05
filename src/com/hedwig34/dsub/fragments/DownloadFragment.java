package com.hedwig34.dsub.fragments;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewFlipper;
import com.hedwig34.dsub.R;
import com.hedwig34.dsub.activity.SubsonicFragmentActivity;
import com.hedwig34.dsub.domain.MusicDirectory;
import com.hedwig34.dsub.domain.PlayerState;
import com.hedwig34.dsub.domain.RemoteControlState;
import com.hedwig34.dsub.domain.RepeatMode;
import com.hedwig34.dsub.service.DownloadFile;
import com.hedwig34.dsub.service.DownloadService;
import com.hedwig34.dsub.service.MusicService;
import com.hedwig34.dsub.service.MusicServiceFactory;
import com.hedwig34.dsub.util.Constants;
import com.hedwig34.dsub.util.SilentBackgroundTask;
import com.hedwig34.dsub.view.FadeOutAnimation;
import com.hedwig34.dsub.view.SongView;
import com.hedwig34.dsub.util.Util;
import com.hedwig34.dsub.view.VisualizerView;

import static com.hedwig34.dsub.domain.PlayerState.*;
import com.hedwig34.dsub.util.*;
import com.hedwig34.dsub.view.AutoRepeatButton;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import com.mobeta.android.dslv.*;
import com.hedwig34.dsub.activity.SubsonicActivity;

public class DownloadFragment extends SubsonicFragment implements OnGestureListener {
	private static final String TAG = DownloadFragment.class.getSimpleName();

	public static final int DIALOG_SAVE_PLAYLIST = 100;
	private static final int PERCENTAGE_OF_SCREEN_FOR_SWIPE = 10;
	private static final int COLOR_BUTTON_ENABLED = Color.rgb(51, 181, 229);
	private static final int COLOR_BUTTON_DISABLED = Color.rgb(206, 213, 211);
	private static final int INCREMENT_TIME = 5000;

	private ViewFlipper playlistFlipper;
	private TextView emptyTextView;
	private TextView songTitleTextView;
	private ImageView albumArtImageView;
	private DragSortListView playlistView;
	private TextView positionTextView;
	private TextView durationTextView;
	private TextView statusTextView;
	private SeekBar progressBar;
	private AutoRepeatButton previousButton;
	private AutoRepeatButton nextButton;
	private View pauseButton;
	private View stopButton;
	private View startButton;
	private ImageButton repeatButton;
	private Button equalizerButton;
	private Button visualizerButton;
	private Button jukeboxButton;
	private View toggleListButton;
	private ImageButton starButton;
	private ImageButton bookmarkButton;
	private View mainLayout;
	private ScheduledExecutorService executorService;
	private DownloadFile currentPlaying;
	private long currentRevision;
	private int swipeDistance;
	private int swipeVelocity;
	private VisualizerView visualizerView;
	private boolean nowPlaying = true;
	private ScheduledFuture<?> hideControlsFuture;
	private List<DownloadFile> songList;
	private SongListAdapter songListAdapter;
	private SilentBackgroundTask<Void> onProgressChangedTask;
	private SilentBackgroundTask<Void> onCurrentChangedTask;
	private SilentBackgroundTask<Void> onDownloadListChangedTask;
	private boolean seekInProgress = false;
	private boolean startFlipped = false;
	private boolean scrollWhenLoaded = false;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(savedInstanceState != null) {
			if(savedInstanceState.getInt(Constants.FRAGMENT_DOWNLOAD_FLIPPER) == 1) {
				startFlipped = true;
			}
		}
		Bundle args = getArguments();
		if(args != null) {
			if(args.getBoolean(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW)) {
				startFlipped = true;
				nowPlaying = false;
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(Constants.FRAGMENT_DOWNLOAD_FLIPPER, playlistFlipper.getDisplayedChild());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		rootView = inflater.inflate(R.layout.download, container, false);
		setTitle(nowPlaying ? "Now Playing" : "Downloading");
		
		mainLayout = rootView.findViewById(R.id.download_layout);
		if(!primaryFragment) {
			mainLayout.setVisibility(View.GONE);
		}

		WindowManager w = context.getWindowManager();
		Display d = w.getDefaultDisplay();
		swipeDistance = (d.getWidth() + d.getHeight()) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
		swipeVelocity = (d.getWidth() + d.getHeight()) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
		gestureScanner = new GestureDetector(this);

		playlistFlipper = (ViewFlipper)rootView.findViewById(R.id.download_playlist_flipper);
		emptyTextView = (TextView)rootView.findViewById(R.id.download_empty);
		songTitleTextView = (TextView)rootView.findViewById(R.id.download_song_title);
		albumArtImageView = (ImageView)rootView.findViewById(R.id.download_album_art_image);
		positionTextView = (TextView)rootView.findViewById(R.id.download_position);
		durationTextView = (TextView)rootView.findViewById(R.id.download_duration);
		statusTextView = (TextView)rootView.findViewById(R.id.download_status);
		progressBar = (SeekBar)rootView.findViewById(R.id.download_progress_bar);
		playlistView = (DragSortListView)rootView.findViewById(R.id.download_list);
		previousButton = (AutoRepeatButton)rootView.findViewById(R.id.download_previous);
		nextButton = (AutoRepeatButton)rootView.findViewById(R.id.download_next);
		pauseButton =rootView.findViewById(R.id.download_pause);
		stopButton =rootView.findViewById(R.id.download_stop);
		startButton =rootView.findViewById(R.id.download_start);
		repeatButton = (ImageButton)rootView.findViewById(R.id.download_repeat);
		equalizerButton = (Button)rootView.findViewById(R.id.download_equalizer);
		visualizerButton = (Button)rootView.findViewById(R.id.download_visualizer);
		jukeboxButton = (Button)rootView.findViewById(R.id.download_jukebox);
		bookmarkButton = (ImageButton) rootView.findViewById(R.id.download_bookmark);
		LinearLayout visualizerViewLayout = (LinearLayout)rootView.findViewById(R.id.download_visualizer_view_layout);
		toggleListButton =rootView.findViewById(R.id.download_toggle_list);

		starButton = (ImageButton)rootView.findViewById(R.id.download_star);
		if(Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_MENU_STAR, true)) {
			starButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					DownloadFile currentDownload = getDownloadService().getCurrentPlaying();
					if (currentDownload != null) {
						MusicDirectory.Entry currentSong = currentDownload.getSong();
						toggleStarred(currentSong);
						starButton.setImageResource(currentSong.isStarred() ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
					}
				}
			});
		} else {
			starButton.setVisibility(View.GONE);
		}

		View.OnTouchListener touchListener = new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent me) {
				return gestureScanner.onTouchEvent(me);
			}
		};
		pauseButton.setOnTouchListener(touchListener);
		stopButton.setOnTouchListener(touchListener);
		startButton.setOnTouchListener(touchListener);
		equalizerButton.setOnTouchListener(touchListener);
		visualizerButton.setOnTouchListener(touchListener);
		jukeboxButton.setOnTouchListener(touchListener);
		bookmarkButton.setOnTouchListener(touchListener);
		emptyTextView.setOnTouchListener(touchListener);
		albumArtImageView.setOnTouchListener(touchListener);

		previousButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				warnIfNetworkOrStorageUnavailable();
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().previous();
						return null;
					}

					@Override
					protected void done(Void result) {
						onCurrentChanged();
						onProgressChanged();
					}
				}.execute();
				setControlsVisible(true);
			}
		});
		previousButton.setOnRepeatListener(new Runnable() {
			public void run() {
				changeProgress(-INCREMENT_TIME);
			}
		});

		nextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				warnIfNetworkOrStorageUnavailable();
				new SilentBackgroundTask<Boolean>(context) {
					@Override
					protected Boolean doInBackground() throws Throwable {
						getDownloadService().next();
						return true;
					}

					@Override
					protected void done(Boolean result) {
						if(result) {
							onCurrentChanged();
							onProgressChanged();
						}
					}
				}.execute();
				setControlsVisible(true);
			}
		});
		nextButton.setOnRepeatListener(new Runnable() {
			public void run() {
				changeProgress(INCREMENT_TIME);
			}
		});

		pauseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().pause();
						return null;
					}

					@Override
					protected void done(Void result) {
						onCurrentChanged();
						onProgressChanged();
					}
				}.execute();
			}
		});

		stopButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().reset();
						return null;
					}

					@Override
					protected void done(Void result) {
						onCurrentChanged();
						onProgressChanged();
					}
				}.execute();
			}
		});

		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				warnIfNetworkOrStorageUnavailable();
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						start();
						return null;
					}

					@Override
					protected void done(Void result) {
						onCurrentChanged();
						onProgressChanged();
					}
				}.execute();
			}
		});

		repeatButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				RepeatMode repeatMode = getDownloadService().getRepeatMode().next();
				getDownloadService().setRepeatMode(repeatMode);
				onDownloadListChanged();
				switch (repeatMode) {
					case OFF:
						Util.toast(context, R.string.download_repeat_off);
						break;
					case ALL:
						Util.toast(context, R.string.download_repeat_all);
						break;
					case SINGLE:
						Util.toast(context, R.string.download_repeat_single);
						break;
					default:
						break;
				}
				setControlsVisible(true);
			}
		});

		equalizerButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				DownloadService downloadService = getDownloadService();
				if(downloadService != null && downloadService.getEqualizerController() != null
						&& downloadService.getEqualizerController().getEqualizer() != null) {
					SubsonicFragment fragment = new EqualizerFragment();
					replaceFragment(fragment, R.id.download_layout_container);
					setControlsVisible(true);
				} else {
					Util.toast(context, "Failed to start equalizer.  Try restarting.");
				}
			}
		});

		visualizerButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				boolean active = !visualizerView.isActive();
				visualizerView.setActive(active);
				boolean isActive = visualizerView.isActive();
				getDownloadService().setShowVisualization(isActive);
				updateButtons();
				if(active == isActive) {
					Util.toast(context, active ? R.string.download_visualizer_on : R.string.download_visualizer_off);
				} else {
					Util.toast(context, "Failed to start visualizer.  Try restarting.");
				}
				setControlsVisible(true);
			}
		});

		jukeboxButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				boolean jukeboxEnabled = !getDownloadService().isRemoteEnabled();
				getDownloadService().setRemoteEnabled(jukeboxEnabled ? RemoteControlState.JUKEBOX_SERVER : RemoteControlState.LOCAL);
				updateButtons();
				Util.toast(context, jukeboxEnabled ? R.string.download_jukebox_on : R.string.download_jukebox_off, false);
				setControlsVisible(true);
			}
		});

		bookmarkButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				createBookmark();
			}
		});

		toggleListButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleFullscreenAlbumArt();
				setControlsVisible(true);
			}
		});

		progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                new SilentBackgroundTask<Void>(context) {
                    @Override
                    protected Void doInBackground() throws Throwable {
                        getDownloadService().seekTo(progressBar.getProgress());
                        return null;
                    }

                    @Override
                    protected void done(Void result) {
                    	seekInProgress = false;
                        DownloadFragment.this.onProgressChanged();
                    }
                }.execute();
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
				seekInProgress = true;
            }

            @Override
            public void onProgressChanged(final SeekBar seekBar, final int position, final boolean fromUser) {
				if (fromUser) {
					Util.toast(context, Util.formatDuration(position / 1000), true);
					setControlsVisible(true);
				}
            }
		});
		playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
				if(nowPlaying) {
					warnIfNetworkOrStorageUnavailable();
					new SilentBackgroundTask<Void>(context) {
						@Override
						protected Void doInBackground() throws Throwable {
							getDownloadService().play(position);
							return null;
						}

						@Override
						protected void done(Void result) {
							onCurrentChanged();
							onProgressChanged();
						}
					}.execute();
				}
			}
		});
		playlistView.setDropListener(new DragSortListView.DropListener() {
			@Override
			public void drop(int from, int to) {
				getDownloadService().swap(nowPlaying, from, to);
				onDownloadListChanged();
			}
		});
		playlistView.setRemoveListener(new DragSortListView.RemoveListener() {
			@Override
			public void remove(int which) {
				getDownloadService().remove(which);
				onDownloadListChanged();
			}
		});

		registerForContextMenu(playlistView);

		DownloadService downloadService = getDownloadService();
		if (downloadService != null && context.getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, false)) {
			context.getIntent().removeExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE);
			warnIfNetworkOrStorageUnavailable();
			downloadService.setShufflePlayEnabled(true);
		}

		boolean visualizerAvailable = downloadService != null && downloadService.getVisualizerAvailable();
		boolean equalizerAvailable = downloadService != null && downloadService.getEqualizerAvailable();

		if (!equalizerAvailable) {
			equalizerButton.setVisibility(View.GONE);
		}
		if (!visualizerAvailable) {
			visualizerButton.setVisibility(View.GONE);
		} else {
			visualizerView = new VisualizerView(context);
			if(downloadService.getShowVisualization()) {
				visualizerView.setActive(true);
			}
			visualizerViewLayout.addView(visualizerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
		}

		return rootView;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		DownloadService downloadService = getDownloadService();
		if(Util.isOffline(context)) {
			menuInflater.inflate(R.menu.nowplaying_offline, menu);
		} else {
			if(nowPlaying) {
				menuInflater.inflate(R.menu.nowplaying, menu);
			}
			else {
				menuInflater.inflate(R.menu.nowplaying_downloading, menu);
			}

			if(downloadService != null && downloadService.getSleepTimer()) {
				menu.findItem(R.id.menu_toggle_timer).setTitle(R.string.download_stop_timer);
			}
		}
		if(downloadService != null && downloadService.getKeepScreenOn() && nowPlaying) {
			menu.findItem(R.id.menu_screen_on_off).setTitle(R.string.download_menu_screen_off);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		if(menuItemSelected(menuItem.getItemId(), null)) {
			return true;
		}
		
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	public void onCreateContextMenu(android.view.ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		if(!primaryFragment) {
			return;
		}

		if (view == playlistView) {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			DownloadFile downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);

			android.view.MenuInflater inflater = context.getMenuInflater();
			if(Util.isOffline(context)) {
				inflater.inflate(R.menu.nowplaying_context_offline, menu);
			} else {
				inflater.inflate(R.menu.nowplaying_context, menu);
				menu.findItem(R.id.menu_star).setTitle(downloadFile.getSong().isStarred() ? R.string.common_unstar : R.string.common_star);
			}

			if (downloadFile.getSong().getParent() == null) {
				menu.findItem(R.id.menu_show_album).setVisible(false);
			}
		}
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem menuItem) {
		if(!primaryFragment) {
			return false;
		}
		
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
		DownloadFile downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);
		return menuItemSelected(menuItem.getItemId(), downloadFile) || super.onContextItemSelected(menuItem);
	}

	private boolean menuItemSelected(int menuItemId, final DownloadFile song) {
		switch (menuItemId) {
			case R.id.menu_show_album:
				MusicDirectory.Entry entry = song.getSong();
				
				Intent intent = new Intent(context, SubsonicFragmentActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true);
				if(Util.isTagBrowsing(context)) {
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, entry.getAlbumId());
				} else {
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, entry.getParent());
				}
				intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.getAlbum());
				intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Artist");
				
				if(entry.getGrandParent() != null) {
					intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getGrandParent());
					intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_NAME, entry.getArtist());
				} else if(Util.isTagBrowsing(context)) {
					intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getArtistId());
					intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_NAME, entry.getArtist());
				}
				
				if(Util.isOffline(context)) {
					try {
						// This should only be succesful if this is a online song in offline mode
						Integer.parseInt(entry.getParent());
						String root = FileUtil.getMusicDirectory(context).getPath();
						String id = root + "/" + entry.getPath();
						id = id.substring(0, id.lastIndexOf("/"));
						intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, id);
						id = id.substring(0, id.lastIndexOf("/"));
						intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_ID, id);
						intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_NAME, entry.getArtist());
					} catch(Exception e) {
						// Do nothing, entry.getParent() is fine
					}
				}
				
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityWithoutTransition(context, intent);
				return true;
			case R.id.menu_lyrics:
				SubsonicFragment fragment = new LyricsFragment();
				Bundle args = new Bundle();
				args.putString(Constants.INTENT_EXTRA_NAME_ARTIST, song.getSong().getArtist());
				args.putString(Constants.INTENT_EXTRA_NAME_TITLE, song.getSong().getTitle());
				fragment.setArguments(args);
				
				replaceFragment(fragment, R.id.download_layout_container);
				return true;
			case R.id.menu_remove:
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().remove(song);
						return null;
					}

					@Override
					protected void done(Void result) {
						onDownloadListChanged();
					}
				}.execute();
				return true;
			case R.id.menu_delete:
				List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(1);
				songs.add(song.getSong());
				getDownloadService().delete(songs);
				return true;
			case R.id.menu_remove_all:
				Util.confirmDialog(context, R.string.download_menu_remove_all, "", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						new SilentBackgroundTask<Void>(context) {
							@Override
							protected Void doInBackground() throws Throwable {
								getDownloadService().setShufflePlayEnabled(false);
								if(nowPlaying) {
									getDownloadService().clear();
								}
								else {
									getDownloadService().clearBackground();
								}
								return null;
							}

							@Override
							protected void done(Void result) {
								onDownloadListChanged();
							}
						}.execute();
					}
				});
				return true;
			case R.id.menu_screen_on_off:
				if (getDownloadService().getKeepScreenOn()) {
					context.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					getDownloadService().setKeepScreenOn(false);
				} else {
					context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					getDownloadService().setKeepScreenOn(true);
				}
				context.supportInvalidateOptionsMenu();
				return true;
			case R.id.menu_shuffle:
				new SilentBackgroundTask<Void>(context) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().shuffle();
						return null;
					}

					@Override
					protected void done(Void result) {
						Util.toast(context, R.string.download_menu_shuffle_notification);
					}
				}.execute();
				return true;
			case R.id.menu_save_playlist:
				List<MusicDirectory.Entry> entries = new LinkedList<MusicDirectory.Entry>();
				for (DownloadFile downloadFile : getDownloadService().getSongs()) {
					entries.add(downloadFile.getSong());
				}
				createNewPlaylist(entries, true);
				return true;
			case R.id.menu_star:
				toggleStarred(song.getSong());
				return true;
			case R.id.menu_toggle_now_playing:
				toggleNowPlaying();
				context.supportInvalidateOptionsMenu();
				return true;
			case R.id.menu_toggle_timer:
				if(getDownloadService().getSleepTimer()) {
					getDownloadService().stopSleepTimer();
					context.supportInvalidateOptionsMenu();
				} else {
					startTimer();
				}
				return true;
			case R.id.menu_add_playlist:
				songs = new ArrayList<MusicDirectory.Entry>(1);
				songs.add(song.getSong());
				addToPlaylist(songs);
				return true;
			case R.id.menu_info:
				displaySongInfo(song.getSong());
				return true;
			default:
				return false;
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		final Handler handler = new Handler();
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					@Override
					public void run() {
						update();
					}
				});
			}
		};

		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleWithFixedDelay(runnable, 0L, 1000L, TimeUnit.MILLISECONDS);

		setControlsVisible(true);

		DownloadService downloadService = getDownloadService();
		if (downloadService == null || downloadService.getCurrentPlaying() == null || startFlipped) {
			playlistFlipper.setDisplayedChild(1);
		}
		if (downloadService != null && downloadService.getKeepScreenOn()) {
			context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			context.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		if (visualizerView != null && downloadService != null && downloadService.getShowVisualization()) {
			visualizerView.setActive(true);
		}

		updateButtons();

		if(currentPlaying == null && downloadService != null && currentPlaying == downloadService.getCurrentPlaying()) {
			getImageLoader().loadImage(albumArtImageView, null, true, false);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		executorService.shutdown();
		if (visualizerView != null && visualizerView.isActive()) {
			visualizerView.setActive(false);
		}
	}
	
	@Override
	public void setPrimaryFragment(boolean primary) {
		super.setPrimaryFragment(primary);
		if(rootView != null) {
			if(primary) {
				mainLayout.setVisibility(View.VISIBLE);
				updateButtons();
			} else {
				mainLayout.setVisibility(View.GONE);
			}
		}
	}

	private void scheduleHideControls() {
		if (hideControlsFuture != null) {
			hideControlsFuture.cancel(false);
		}

		final Handler handler = new Handler();
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					@Override
					public void run() {
						setControlsVisible(false);
					}
				});
			}
		};
		hideControlsFuture = executorService.schedule(runnable, 3000L, TimeUnit.MILLISECONDS);
	}

	private void setControlsVisible(boolean visible) {
		try {
			long duration = 1700L;
			FadeOutAnimation.createAndStart(rootView.findViewById(R.id.download_overlay_buttons), !visible, duration);

			if (visible) {
				scheduleHideControls();
			}
		} catch(Exception e) {

		}
	}

	private void updateButtons() {
		if(context == null) {
			return;
		}

		SharedPreferences prefs = Util.getPreferences(context);
		boolean equalizerOn = prefs.getBoolean(Constants.PREFERENCES_EQUALIZER_ON, false);
		if(equalizerOn && getDownloadService() != null && getDownloadService().getEqualizerController() != null &&
				getDownloadService().getEqualizerController().isEnabled()) {
			equalizerButton.setTextColor(COLOR_BUTTON_ENABLED);
		} else {
			equalizerButton.setTextColor(COLOR_BUTTON_DISABLED);
		}

		if (visualizerView != null) {
			visualizerButton.setTextColor(visualizerView.isActive() ? COLOR_BUTTON_ENABLED : COLOR_BUTTON_DISABLED);
		}

		boolean jukeboxEnabled = getDownloadService() != null && getDownloadService().isRemoteEnabled();
		jukeboxButton.setTextColor(jukeboxEnabled ? COLOR_BUTTON_ENABLED : COLOR_BUTTON_DISABLED);
		
		if(Util.isOffline(context)) {
			bookmarkButton.setVisibility(View.GONE);
		} else {
			bookmarkButton.setVisibility(View.VISIBLE);
		}
	}

	// Scroll to current playing/downloading.
	private void scrollToCurrent() {
		if (getDownloadService() == null || songListAdapter == null) {
			scrollWhenLoaded = true;
			return;
		}

		for (int i = 0; i < songListAdapter.getCount(); i++) {
			if (currentPlaying == playlistView.getItemAtPosition(i)) {
				playlistView.setSelectionFromTop(i, 40);
				return;
			}
		}
		DownloadFile currentDownloading = getDownloadService().getCurrentDownloading();
		for (int i = 0; i < songListAdapter.getCount(); i++) {
			if (currentDownloading == playlistView.getItemAtPosition(i)) {
				playlistView.setSelectionFromTop(i, 40);
				return;
			}
		}
	}

	private void update() {
		if (getDownloadService() == null) {
			return;
		}

		if (currentRevision != getDownloadService().getDownloadListUpdateRevision()) {
			onDownloadListChanged();
		}

		if (currentPlaying != getDownloadService().getCurrentPlaying()) {
			onCurrentChanged();
		}

		if(startFlipped) {
			startFlipped = false;
			scrollToCurrent();
		}

		onProgressChanged();
	}

	protected void startTimer() {
		View dialogView = context.getLayoutInflater().inflate(R.layout.start_timer, null);

		// Setup length label
		final TextView lengthBox = (TextView) dialogView.findViewById(R.id.timer_length_label);
		final SharedPreferences prefs = Util.getPreferences(context);
		String lengthString = prefs.getString(Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION, "5");
		int length = Integer.parseInt(lengthString);
		lengthBox.setText(Util.formatDuration(length));

		// Setup length slider
		final SeekBar lengthBar = (SeekBar) dialogView.findViewById(R.id.timer_length_bar);
		lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(fromUser) {
					int length = getMinutes(progress);
					lengthBox.setText(Util.formatDuration(length));
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.menu_set_timer)
			.setView(dialogView)
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					int length = getMinutes(lengthBar.getProgress());

					SharedPreferences.Editor editor = prefs.edit();
					editor.putString(Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION, Integer.toString(length));
					editor.commit();

					getDownloadService().setSleepTimerDuration(length);
					getDownloadService().startSleepTimer();
					context.supportInvalidateOptionsMenu();
				}
			})
			.setNegativeButton(R.string.common_cancel, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private int getMinutes(int progress) {
		if(progress < 30) {
			return progress + 1;
		} else if(progress < 61) {
			return (progress - 30) * 5 + getMinutes(29);
		} else {
			return (progress - 61) * 15 + getMinutes(60);
		}
	}

	private void toggleFullscreenAlbumArt() {
		if (playlistFlipper.getDisplayedChild() == 1) {
			playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.push_down_in));
			playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.push_down_out));
			playlistFlipper.setDisplayedChild(0);
		} else {
			scrollToCurrent();
			playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.push_up_in));
			playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.push_up_out));
			playlistFlipper.setDisplayedChild(1);
		}
	}

	private void start() {
		DownloadService service = getDownloadService();
		PlayerState state = service.getPlayerState();
		if (state == PAUSED || state == COMPLETED || state == STOPPED) {
			service.start();
		} else if (state == STOPPED || state == IDLE) {
			warnIfNetworkOrStorageUnavailable();
			int current = service.getCurrentPlayingIndex();
			// TODO: Use play() method.
			if (current == -1) {
				service.play(0);
			} else {
				service.play(current);
			}
		}
	}
	private void onDownloadListChanged() {
		onDownloadListChanged(false);
	}
	private void onDownloadListChanged(final boolean refresh) {
		final DownloadService downloadService = getDownloadService();
		if (downloadService == null || onDownloadListChangedTask != null) {
			return;
		}

		onDownloadListChangedTask = new SilentBackgroundTask<Void>(context) {
			int currentPlayingIndex;
			int size;

			@Override
			protected Void doInBackground() throws Throwable {
				currentPlayingIndex = downloadService.getCurrentPlayingIndex() + 1;
				size = downloadService.size();
				
				return null;
			}

			@Override
			protected void done(Void result) {
				List<DownloadFile> list;
				if(nowPlaying) {
					list = downloadService.getSongs();
				}
				else {
					list = downloadService.getBackgroundDownloads();
				}
				
				if(downloadService.isShufflePlayEnabled()) {
					emptyTextView.setText(R.string.download_shuffle_loading);
				}
				else {
					emptyTextView.setText(R.string.download_empty);
				}
				
				if(songListAdapter == null || refresh) {
					songList = new ArrayList<DownloadFile>();
					songList.addAll(list);
					playlistView.setAdapter(songListAdapter = new SongListAdapter(songList));
				} else {
					songList.clear();
					songList.addAll(list);
					songListAdapter.notifyDataSetChanged();
				}
				
				emptyTextView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
				currentRevision = downloadService.getDownloadListUpdateRevision();

				switch (downloadService.getRepeatMode()) {
					case OFF:
						if("light".equals(SubsonicActivity.getThemeName()) | "light_fullscreen".equals(SubsonicActivity.getThemeName())) {
							repeatButton.setImageResource(R.drawable.media_repeat_off_light);
						} else {
							repeatButton.setImageResource(R.drawable.media_repeat_off);
						}
						break;
					case ALL:
						repeatButton.setImageResource(R.drawable.media_repeat_all);
						break;
					case SINGLE:
						repeatButton.setImageResource(R.drawable.media_repeat_single);
						break;
					default:
						break;
				}
				
				if(scrollWhenLoaded) {
					scrollToCurrent();
					scrollWhenLoaded = false;
				}

				setSubtitle(context.getResources().getString(R.string.download_playing_out_of, currentPlayingIndex, size));
				onDownloadListChangedTask = null;
				if(onCurrentChangedTask != null) {
					onCurrentChangedTask.execute();
				} else if(onProgressChangedTask != null) {
					onProgressChangedTask.execute();
				}
			}
		};
		onDownloadListChangedTask.execute();
	}

	private void onCurrentChanged() {
		final DownloadService downloadService = getDownloadService();
		if (downloadService == null  || onCurrentChangedTask != null) {
			return;
		}
		
		onCurrentChangedTask = new SilentBackgroundTask<Void>(context) {
			int currentPlayingIndex;
			int currentPlayingSize;

			@Override
			protected Void doInBackground() throws Throwable {
				currentPlaying = downloadService.getCurrentPlaying();
				currentPlayingIndex = downloadService.getCurrentPlayingIndex() + 1;
				currentPlayingSize = downloadService.size();
				return null;
			}

			@Override
			protected void done(Void result) {
				if (currentPlaying != null) {
					MusicDirectory.Entry song = currentPlaying.getSong();
					songTitleTextView.setText(song.getTitle());
					getImageLoader().loadImage(albumArtImageView, song, true, true);
					starButton.setImageResource(song.isStarred() ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
					setSubtitle(context.getResources().getString(R.string.download_playing_out_of, currentPlayingIndex, currentPlayingSize));
				} else {
					songTitleTextView.setText(null);
					getImageLoader().loadImage(albumArtImageView, null, true, false);
					starButton.setImageResource(android.R.drawable.btn_star_big_off);
					setSubtitle(null);
				}
				onCurrentChangedTask = null;
				if(onProgressChangedTask != null) {
					onProgressChangedTask.execute();
				}
			}
		};
		
		if(onDownloadListChangedTask == null) {
			onCurrentChangedTask.execute();
		}
	}

	private void onProgressChanged() {
		// Make sure to only be trying to run one of these at a time
		if (getDownloadService() == null || onProgressChangedTask != null) {
			return;
		}

		onProgressChangedTask = new SilentBackgroundTask<Void>(context) {
			DownloadService downloadService;
			boolean isJukeboxEnabled;
			int millisPlayed;
			Integer duration;
			PlayerState playerState;

			@Override
			protected Void doInBackground() throws Throwable {
				downloadService = getDownloadService();
				isJukeboxEnabled = downloadService.isRemoteEnabled();
				millisPlayed = Math.max(0, downloadService.getPlayerPosition());
				duration = downloadService.getPlayerDuration();
				playerState = getDownloadService().getPlayerState();
				return null;
			}

			@Override
			protected void done(Void result) {
				if (currentPlaying != null) {
					int millisTotal = duration == null ? 0 : duration;

					positionTextView.setText(Util.formatDuration(millisPlayed / 1000));
					if(millisTotal > 0) {
						durationTextView.setText(Util.formatDuration(millisTotal / 1000));
					} else {
						durationTextView.setText("-:--");
					}
					progressBar.setMax(millisTotal == 0 ? 100 : millisTotal); // Work-around for apparent bug.
					if(!seekInProgress) {
						progressBar.setProgress(millisPlayed);
					}
					progressBar.setEnabled(currentPlaying.isWorkDone() || isJukeboxEnabled);
				} else {
					positionTextView.setText("0:00");
					durationTextView.setText("-:--");
					progressBar.setProgress(0);
					progressBar.setEnabled(false);
				}

				switch (playerState) {
					case DOWNLOADING:
						if(currentPlaying != null) {
							long bytes = currentPlaying.getPartialFile().length();
							statusTextView.setText(context.getResources().getString(R.string.download_playerstate_downloading, Util.formatLocalizedBytes(bytes, context)));
						}
						break;
					case PREPARING:
						statusTextView.setText(R.string.download_playerstate_buffering);
						break;
					default:
						if(currentPlaying != null) {
							String artist = "";
							if(currentPlaying.getSong().getArtist() != null) {
								artist = currentPlaying.getSong().getArtist() + " - ";
							}
							statusTextView.setText(artist + currentPlaying.getSong().getAlbum());
						} else {
							statusTextView.setText(null);
						}
						break;
				}

				switch (playerState) {
					case STARTED:
						pauseButton.setVisibility(View.VISIBLE);
						stopButton.setVisibility(View.INVISIBLE);
						startButton.setVisibility(View.INVISIBLE);
						break;
					case DOWNLOADING:
					case PREPARING:
						pauseButton.setVisibility(View.INVISIBLE);
						stopButton.setVisibility(View.VISIBLE);
						startButton.setVisibility(View.INVISIBLE);
						break;
					default:
						pauseButton.setVisibility(View.INVISIBLE);
						stopButton.setVisibility(View.INVISIBLE);
						startButton.setVisibility(View.VISIBLE);
						break;
				}

				jukeboxButton.setTextColor(isJukeboxEnabled ? COLOR_BUTTON_ENABLED : COLOR_BUTTON_DISABLED);
				onProgressChangedTask = null;
			}
		};
		if(onDownloadListChangedTask == null && onCurrentChangedTask == null) {
			onProgressChangedTask.execute();
		}
	}

	private void changeProgress(final int ms) {
		final DownloadService downloadService = getDownloadService();
		if(downloadService == null) {
			return;
		}

		new SilentBackgroundTask<Void>(context) {
			boolean isJukeboxEnabled;
			int msPlayed;
			Integer duration;
			PlayerState playerState;
			int seekTo;

			@Override
			protected Void doInBackground() throws Throwable {
				msPlayed = Math.max(0, downloadService.getPlayerPosition());
				duration = downloadService.getPlayerDuration();
				playerState = getDownloadService().getPlayerState();
				int msTotal = duration == null ? 0 : duration;
				if(msPlayed + ms > msTotal) {
					seekTo = msTotal;
				} else {
					seekTo = msPlayed + ms;
				}
				downloadService.seekTo(seekTo);
				return null;
			}

			@Override
			protected void done(Void result) {
				progressBar.setProgress(seekTo);
			}
		}.execute();
	}
	
	private void createBookmark() {
		DownloadService downloadService = getDownloadService();
		if(downloadService == null) {
			return;
		}
		
		final DownloadFile currentDownload = downloadService.getCurrentPlaying();
		if(currentDownload == null) {
			return;
		}
		
		View dialogView = context.getLayoutInflater().inflate(R.layout.create_bookmark, null);
		final EditText commentBox = (EditText)dialogView.findViewById(R.id.comment_text);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.download_save_bookmark_title)
			.setView(dialogView)
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					String comment = commentBox.getText().toString();

					createBookmark(currentDownload, comment);
				}
			})
			.setNegativeButton(R.string.common_cancel, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	private void createBookmark(final DownloadFile currentDownload, final String comment) {
		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicDirectory.Entry currentSong = currentDownload.getSong();
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.createBookmark(currentSong.getId(), getDownloadService().getPlayerPosition(), comment, context, null);

				return null;
			}

			@Override
			protected void done(Void result) {
				Util.toast(context, R.string.download_save_bookmark);
				setControlsVisible(true);
			}
		}.execute();
	}

	private class SongListAdapter extends ArrayAdapter<DownloadFile> {
		public SongListAdapter(List<DownloadFile> entries) {
			super(context, android.R.layout.simple_list_item_1, entries);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			SongView view;
			if (convertView != null && convertView instanceof SongView) {
				view = (SongView) convertView;
			} else {
				view = new SongView(context);
			}
			DownloadFile downloadFile = getItem(position);
			view.setObject(downloadFile.getSong(), false);
			return view;
		}
	}

	@Override
	public boolean onDown(MotionEvent me) {
		setControlsVisible(true);
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		DownloadService downloadService = getDownloadService();
		if (downloadService == null || e1 == null || e2 == null) {
			return false;
		}

		// Right to Left swipe
		if (e1.getX() - e2.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
			warnIfNetworkOrStorageUnavailable();
			downloadService.next();
			onCurrentChanged();
			onProgressChanged();
			return true;
		}

		// Left to Right swipe
		else if (e2.getX() - e1.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
			warnIfNetworkOrStorageUnavailable();
			downloadService.previous();
			onCurrentChanged();
			onProgressChanged();
			return true;
		}

		// Top to Bottom swipe
		 else if (e2.getY() - e1.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
			 warnIfNetworkOrStorageUnavailable();
			 downloadService.seekTo(downloadService.getPlayerPosition() + 30000); 
			 onProgressChanged();
			 return true;
		 }

		// Bottom to Top swipe
		else if (e1.getY() - e2.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
			warnIfNetworkOrStorageUnavailable();
			downloadService.seekTo(downloadService.getPlayerPosition() - 8000);
			onProgressChanged();
			return true;
		}

		return false;
	}

	private void toggleNowPlaying() {
		nowPlaying = !nowPlaying;
		setTitle(nowPlaying ? "Now Playing" : "Downloading");
		onDownloadListChanged(true);
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}
}
