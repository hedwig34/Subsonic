package com.hedwig34.dsub.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import com.hedwig34.dsub.R;
import com.hedwig34.dsub.domain.ChatMessage;
import com.hedwig34.dsub.service.MusicService;
import com.hedwig34.dsub.service.MusicServiceFactory;
import com.hedwig34.dsub.util.BackgroundTask;
import com.hedwig34.dsub.util.TabBackgroundTask;
import com.hedwig34.dsub.util.Util;
import com.hedwig34.dsub.view.ChatAdapter;
import com.hedwig34.dsub.util.Constants;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Joshua Bahnsen
 */
public class ChatFragment extends SubsonicFragment {
	private static final String TAG = ChatFragment.class.getSimpleName();
	private ListView chatListView;
	private EditText messageEditText;
	private ImageButton sendButton;
	private Long lastChatMessageTime = (long) 0;
	private ArrayList<ChatMessage> messageList = new ArrayList<ChatMessage>();
	private ScheduledExecutorService executorService;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		rootView = inflater.inflate(R.layout.chat, container, false);
		
		messageEditText = (EditText) rootView.findViewById(R.id.chat_edittext);
		sendButton = (ImageButton) rootView.findViewById(R.id.chat_send);

		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				sendMessage();
			}
		});

		chatListView = (ListView) rootView.findViewById(R.id.chat_entries);

		messageEditText.setImeActionLabel("Send", KeyEvent.KEYCODE_ENTER);
		messageEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			}

			@Override
			public void afterTextChanged(Editable editable) {
				sendButton.setEnabled(!Util.isNullOrWhiteSpace(editable.toString()));
			}
		});

		messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE || (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN)) {
					sendMessage();
					return true;
				}

				return false;
			}
		});

		invalidated = true;
		return rootView;
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
						if(primaryFragment) {
							load(false);
						} else {
							invalidated = true;
						}
					}
				});
			}
		};

		SharedPreferences prefs = Util.getPreferences(context);
		long refreshRate = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_CHAT_REFRESH, "30"));
		if(refreshRate > 0) {
			executorService = Executors.newSingleThreadScheduledExecutor();
			executorService.scheduleWithFixedDelay(runnable, refreshRate * 1000L, refreshRate * 1000L, TimeUnit.MILLISECONDS);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		if(executorService != null) {
			executorService.shutdown();
			executorService = null;
		}
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.chat, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(super.onOptionsItemSelected(item)) {
			return true;
		}

		return false;
	}
	
	@Override
	protected void refresh(boolean refresh) {
		load(refresh);
	}
	
	private synchronized void load(final boolean refresh) {
		Log.i(TAG, "Loading: " + refresh);
		setTitle(R.string.button_bar_chat);
		BackgroundTask<List<ChatMessage>> task = new TabBackgroundTask<List<ChatMessage>>(this) {
			@Override
			protected List<ChatMessage> doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				return musicService.getChatMessages(refresh ? 0L : lastChatMessageTime, context, this);
			}

			@Override
			protected void done(List<ChatMessage> result) {
				if (result != null && !result.isEmpty()) {
					if(refresh) {
						messageList.clear();
					}
					
					// Reset lastChatMessageTime if we have a newer message
					for (ChatMessage message : result) {
						if (message.getTime() > lastChatMessageTime) {
							lastChatMessageTime = message.getTime();
						}
					}

					// Reverse results to show them on the bottom
					Collections.reverse(result);
					messageList.addAll(result);

					ChatAdapter chatAdapter = new ChatAdapter(context, messageList);
					chatListView.setAdapter(chatAdapter);
				}
			}
		};

		task.execute();
	}

	private void sendMessage() {
		final String message = messageEditText.getText().toString();

		if (!Util.isNullOrWhiteSpace(message)) {
			messageEditText.setText("");
			InputMethodManager mgr = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
			mgr.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);

			BackgroundTask<Void> task = new TabBackgroundTask<Void>(this) {
				@Override
				protected Void doInBackground() throws Throwable {
					MusicService musicService = MusicServiceFactory.getMusicService(context);
					musicService.addChatMessage(message, context, this);
					return null;
				}

				@Override
				protected void done(Void result) {
					load(false);
				}
			};

			task.execute();
		}
	}
}
