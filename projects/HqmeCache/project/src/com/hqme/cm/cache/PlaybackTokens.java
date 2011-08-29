/** 
* This reference code is an implementation of the IEEE P2200 standard.  It is not
* a contribution to the IEEE P2200 standard.
* 
* Copyright (c) 2011 SanDisk Corporation.  All rights reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use
* this file except in compliance with the License.  You may obtain a copy of the
* License at
* 
*        http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software distributed
* under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied.
* 
* See the License for the specific language governing permissions and limitations
* under the License.
*/

package com.hqme.cm.cache;

import java.util.Date;
import java.util.HashMap;
import java.util.Random;

public class PlaybackTokens
{
    //==================================================================================================================================
    //static const char * uriFormatUtf = "http://localhost:%d/playback.jsp?token=%s";
    
    private static final long maxPlaybackTokenLifetime = 24 * 60 * 60 * 1000; // one hour in milliseconds

    /**
     * This class maintains the private meta-data for streaming media server
     * playback tokens contained within a playback Uri.
     * Playback tokens are used to protect the application session ID and file
     * path information from exposure via the Uri or playback channel. Tokens
     * have a maximum lifetime set by the static property
     * maxPlaybackTokenLifetime, and may be requested for playback no more than
     * maxPlaybackTokenRequests number of times.
     */
    static final class PlaybackToken
    {
        private static final HashMap<String, PlaybackToken> tokens = new HashMap<String, PlaybackToken>();
        private static final Random random = new Random(new Date().getTime());

        private long createdTime;
        private String tokenKey;
        UntenCacheService.UntenCacheObject object;
        
        public PlaybackToken(UntenCacheService.UntenCacheObject object) {
            synchronized (PlaybackToken.tokens) {
                this.object = object;
                
                do {
                    this.tokenKey = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
                } while (tokens.get(this.tokenKey) != null);

                this.createdTime = new Date().getTime();
            }
        }
    }

    private static boolean isPlaybackTokenValid(PlaybackToken token) {
        return token == null || new Date().getTime() - token.createdTime > maxPlaybackTokenLifetime ? false : true;
    }

    /**
     * Invoked from ContentObject function getStreamingUri() to
     * construct and track a temporal Token object for the object.
     * If a token was previously constructed, that token is returned, 
     * otherwise a new token is created.
     * 
     * @param object The IContentObject object.
     * @returns The playback token key for the now-tracked scPlaybackToken.
     */
    static String newPlaybackToken(UntenCacheService.UntenCacheObject object) {
        PlaybackToken token;
        synchronized (PlaybackToken.tokens) {
            for (String tokenKey : PlaybackToken.tokens.keySet()) {
                token = PlaybackToken.tokens.get(tokenKey);
                if (token != null && token.object.equals(object) && isPlaybackTokenValid(token)) {
                    return token.tokenKey;
                }
            }
            token = new PlaybackToken(object);
            PlaybackToken.tokens.put(token.tokenKey, token);
            return token.tokenKey;
        }
    }

    /**
     * Invoked from streaming server when processing a playback request.
     * 
     * @param tokenKey
     *            The playback token key previously obtained via
     *            scClientApiNative.scPathGetUri().
     * @return The playback token object for the specified key, or null if the
     *         key has expired or was requested too many times.
     */
    static PlaybackToken getPlaybackToken(String tokenKey)
    {
        synchronized (PlaybackToken.tokens) {
            PlaybackToken token = PlaybackToken.tokens.get(tokenKey);
            if (!isPlaybackTokenValid(token)) {
                token = null;
                PlaybackToken.tokens.remove(tokenKey);
            }
            return token;
        }
    }
}
