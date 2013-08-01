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
package com.hedwig34.dsub.service.parser;

import android.content.Context;
import com.hedwig34.dsub.R;
import com.hedwig34.dsub.domain.ChatMessage;
import com.hedwig34.dsub.util.ProgressListener;
import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Joshua Bahnsen
 */
public class ChatMessageParser extends AbstractParser {

	public ChatMessageParser(Context context) {
        super(context);
    }

    public List<ChatMessage> parse(Reader reader, ProgressListener progressListener) throws Exception {
        updateProgress(progressListener, R.string.parser_reading);
        init(reader);
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        int eventType;
        
        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                if ("chatMessage".equals(name)) {
                	ChatMessage chatMessage = new ChatMessage();
                	chatMessage.setUsername(get("username"));
                    chatMessage.setTime(getLong("time"));
                    chatMessage.setMessage(get("message"));
                    result.add(chatMessage);
                } else if ("error".equals(name)) {
                    handleError();
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();
        updateProgress(progressListener, R.string.parser_reading_done);
        
        return result;
    }
}
