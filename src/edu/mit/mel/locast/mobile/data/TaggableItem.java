package edu.mit.mel.locast.mobile.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

/**
 * DB entry for an item that can be tagged.
 * 
 * @author stevep
 *
 */
public abstract class TaggableItem extends JsonSyncableItem {

	public static final String PRIVACY = "privacy",
								AUTHOR = "author";
	
	public static final String PRIVACY_PUBLIC = "public",
								PRIVACY_PROTECTED = "protected",
								PRIVACY_PRIVATE = "private";

	// the ordering of this must match the arrays.xml
	public static final String[] PRIVACY_LIST = {PRIVACY_PUBLIC, PRIVACY_PROTECTED, PRIVACY_PRIVATE};
	
	// key for ContentValues to temporarily store tags as a delimited list
	public static final String TEMP_TAGS = "_tags";
	
	
	@Override
	public Map<String, SyncItem> getSyncMap() {
		final Map<String, SyncItem>syncMap = new HashMap<String, SyncItem>();
		
		//syncMap.put(Tag.PATH, new SyncMap("tags", SyncMap.LIST_STRING));
		syncMap.put(Tag.PATH, new SyncCustomArray("tags") {
			
			@Override
			public JSONArray toJSON(Context context, Uri localItem, Cursor c) throws JSONException {
				if (localItem == null || context.getContentResolver().getType(localItem).startsWith("vnd.android.cursor.dir")){
					return null;
				}
				JSONArray jo = null;
				if (localItem != null){
					jo = new JSONArray(getTags(context.getContentResolver(), localItem));
				}
				return jo;
			}
			
			@Override
			public ContentValues fromJSON(Context context, Uri localItem, JSONArray item)
					throws JSONException {
				final ContentValues cv = new ContentValues();
				final List<String> tags = new ArrayList<String>(item.length());
				for (int i = 0; i < item.length(); i++){
					tags.add(item.getString(i));
				}
				return putList(Tag.PATH, cv, tags);	
			}
		});
		return syncMap;
	}

	/**
	 * @param c a cursor pointing at an item's row
	 * @return true if the item is editable by the logged-in user.
	 */
	public static boolean canEdit(Cursor c){
		return c.getString(c.getColumnIndex(PRIVACY)).equals(PRIVACY_PUBLIC) ||
		AndroidNetworkClient.getInstance(null).getUsername().equals(c.getString(c.getColumnIndex(AUTHOR)));
	}
	
	/**
	 * @param c
	 * @return true if the authenticated user can change the item's privacy level.
	 */
	public static boolean canChangePrivacyLevel(Cursor c){
		return AndroidNetworkClient.getInstance(null).getUsername().equals(c.getString(c.getColumnIndex(AUTHOR)));
	}
	
	
	@Override
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c)
			throws SyncException {
		
	}
	
	@Override
	public void onUpdateItem(Context context, Uri uri) throws SyncException,
			IOException {

	}
	
	/**
	 * @param c
	 * @return a list of all the tags attached to a given item
	 */
	public static Set<String> getTags(ContentResolver cr, Uri item) {
		final Cursor tags = cr.query(Uri.withAppendedPath(item, Tag.PATH), Tag.DEFAULT_PROJECTION, null, null, null);
		final Set<String> tagList = new HashSet<String>(tags.getCount());
		final int tagColumn = tags.getColumnIndex(Tag._NAME);
		for (tags.moveToFirst(); !tags.isAfterLast(); tags.moveToNext()){
			tagList.add(tags.getString(tagColumn));
		}
		tags.close();
		return tagList;
	}
	
	/**
	 * Set the tags of the given item
	 * @param cv
	 * @param tags
	 */
	public static void putTags(ContentResolver cr, Uri item, Collection<String> tags) {
		final ContentValues cv = new ContentValues();
		cv.put(Tag.PATH, TaggableItem.toListString(tags));
		cr.update(Uri.withAppendedPath(item, Tag.PATH), cv, null, null);
	}
	
	public static int MAX_POPULAR_TAGS = 10; 
	
	/**
	 * TODO make this pick the set of tags of a set of content.
	 * 
	 * @param cr a content resolver
	 * @return the top MAX_POPULAR_TAGS most popular tags in the set.
	 */
	public static List<String> getPopularTags(ContentResolver cr){
		
		final Map<String, Integer> tagPop = new HashMap<String, Integer>();
		final List<String> popTags;
		
		final Cursor c = cr.query(Tag.CONTENT_URI, Tag.DEFAULT_PROJECTION, null, null, null);
		final int tagColumn = c.getColumnIndex(Tag._NAME);
		
		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
			final String tag = c.getString(tagColumn);
			
			final Integer count = tagPop.get(tag);
			if (count == null){
				tagPop.put(tag, 1);
			}else{
				tagPop.put(tag, count + 1);
			}
		}
		c.close();
		
		popTags = new ArrayList<String>(tagPop.keySet());
		
		Collections.sort(popTags, new Comparator<String>() {
			public int compare(String object1, String object2) {
				return tagPop.get(object2).compareTo(tagPop.get(object1));
			}
			
		});
		int limit;
		if (popTags.size() < MAX_POPULAR_TAGS){
			limit = popTags.size();
		}else{
			limit = MAX_POPULAR_TAGS;
		}
		return popTags.subList(0, limit);
	}
	
	/**
	 * A hack to work around duplicate column names when selecting with tags.
	 * @param projection The TaggableItem's projection
	 * @return a projection with appropriate namespacing to avoid conflicts.
	 */
	public static String[] getTagProjection(String[] projection){
		final List<String> l = new ArrayList<String>(projection.length);
		// horrible hack to get around duplicate column names.
		for (final String col: projection){
			if (TaggableItem._ID.equals(col)){
				l.add("c."+col +" AS "+TaggableItem._ID);
			}else{
				l.add(col);
			}
		}
		return l.toArray(new String[]{});
	}
	
	public static Uri getTagUri(Uri baseUri, Collection<String> tags){
		if (tags.isEmpty()){
			return baseUri;
		}
		
		return Uri.withAppendedPath(Uri.withAppendedPath(baseUri, Tag.PATH), Tag.toTagString(tags));
	}
}