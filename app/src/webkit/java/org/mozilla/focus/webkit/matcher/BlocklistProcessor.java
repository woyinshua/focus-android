/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.webkit.matcher;

import android.util.ArrayMap;
import android.util.JsonReader;
import android.util.JsonToken;

import org.mozilla.focus.webkit.matcher.util.FocusString;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlocklistProcessor {

    final static String SOCIAL = "Social";
    final static String DISCONNECT = "Disconnect";

    private static final Set<String> IGNORED_CATEGORIES;

    static {
        final Set<String> ignored = new HashSet<>();

        ignored.add("Legacy Disconnect");
        ignored.add("Legacy Content");

        IGNORED_CATEGORIES = Collections.unmodifiableSet(ignored);
    }

    public static Map<String, Trie> loadCategoryMap(final JsonReader reader) throws IOException {
        final Map<String, Trie> categoryMap = new HashMap<>(5);

        reader.beginObject();

        while (reader.hasNext()) {
            JsonToken token = reader.peek();

            final String name = reader.nextName();

            if (name.equals("categories")) {
                extractCategories(reader, categoryMap);
            } else {
                reader.skipValue();
            }

        }

        reader.endObject();

        return categoryMap;
    }

    private interface UrlListCallback {
        void put(final String url);
    }

    private static class ListCallback implements UrlListCallback {
        final List<String> list;

        ListCallback(final List<String> list) {
            this.list = list;
        }

        @Override
        public void put(final String url) {
            list.add(url);
        }
    }

    private static class TrieCallback implements UrlListCallback {
        final Trie trie;

        TrieCallback(final Trie trie) {
            this.trie = trie;
        }

        @Override
        public void put(String url) {
            trie.put(FocusString.create(url).reverse());
        }
    }

    private static void extractCategories(final JsonReader reader, final Map<String, Trie> categoryMap) throws IOException {
        reader.beginObject();

        final List<String> socialOverrides = new LinkedList<String>();

        while (reader.hasNext()) {
            final String categoryName = reader.nextName();

            if (IGNORED_CATEGORIES.contains(categoryName)) {
                reader.skipValue();
            } else if (categoryName.equals(DISCONNECT)) {
                // We move these items into a different list, see below
                ListCallback callback = new ListCallback(socialOverrides);
                extractCategory(reader, callback);
            } else {
                final Trie categoryTrie = Trie.createRootNode();
                final TrieCallback callback = new TrieCallback(categoryTrie);

                extractCategory(reader, callback);

                categoryMap.put(categoryName, categoryTrie);
            }
        }

        final Trie socialTrie = categoryMap.get(SOCIAL);
        if (socialTrie == null) {
            throw new IllegalStateException("Expected social list to exist. Can't copy FB/Twitter into non-existing list");
        }

        for (final String url : socialOverrides) {
            socialTrie.put(FocusString.create(url).reverse());
        }

        reader.endObject();
    }

    private static void extractCategory(final JsonReader reader, final UrlListCallback callback) throws IOException {
        reader.beginArray();

        while (reader.hasNext()) {
            extractSite(reader, callback);
        }

        reader.endArray();
    }

    private static void extractSite(final JsonReader reader, final UrlListCallback callback) throws IOException {
        reader.beginObject();

        final String siteName = reader.nextName();
        {
            reader.beginObject();

            while (reader.hasNext()) {
                final String siteURL = reader.nextName();
                JsonToken nextToken = reader.peek();

                if (nextToken.name().equals("STRING")) {
                    // Sometimes there's a "dnt" entry, with unspecified purpose.
                    reader.skipValue();
                } else {
                    reader.beginArray();

                    while (reader.hasNext()) {
                        // TODO: iOS blocklist parsing removes www. prefix
                        final String blockURL = reader.nextString();
                        callback.put(blockURL);
                    }

                    reader.endArray();
                }
            }

            reader.endObject();
        }

        reader.endObject();
    }
}
