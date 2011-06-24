package edu.mit.mobile.android.locast.ver2.itineraries;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;

public class ItineraryList extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = ItineraryList.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mUri;

	private ImageCache mImageCache;

	private final String[] ITINERARY_DISPLAY = new String[]{Itinerary._TITLE, Itinerary._THUMBNAIL, Itinerary._FAVORITES_COUNT, Itinerary._CASTS_COUNT};
	private final String[] ITINERARY_PROJECTION = ArrayUtils.concat(new String[]{Itinerary._ID}, ITINERARY_DISPLAY);

	private static String LOADER_DATA = "edu.mit.mobile.android.locast.LOADER_DATA";
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_list_activity);

		findViewById(R.id.refresh).setOnClickListener(this);
		findViewById(R.id.home).setOnClickListener(this);

		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.list_footer, null), null, false);
		mListView.setEmptyView(findViewById(android.R.id.empty));

		final Intent intent = getIntent();
		final String action = intent.getAction();

		mImageCache = ImageCache.getInstance(this);

		if (Intent.ACTION_VIEW.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
				mAdapter = new SimpleThumbnailCursorAdapter(this,
						R.layout.itinerary_item,
						null,
				ITINERARY_DISPLAY,
				new int[] {android.R.id.text1, R.id.media_thumbnail, R.id.favorites, R.id.casts},
				new int[]{R.id.media_thumbnail},
				0
				);
				mListView.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 48, 48, ImageLoaderAdapter.UNIT_DIP));

				final LoaderManager lm = getSupportLoaderManager();
				final Bundle loaderArgs = new Bundle();
				loaderArgs.putParcelable(LOADER_DATA, data);
				lm.initLoader(0, loaderArgs, this);
				setTitle(R.string.itineraries);
				mUri = data;
				startService(new Intent(Intent.ACTION_SYNC, data));
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
	}

	@Override
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	@Override
	public void setTitle(int title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	private void refresh(boolean explicitSync){
		startService(new Intent(Intent.ACTION_SYNC, mUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = args.getParcelable(LOADER_DATA);

		final CursorLoader cl = new CursorLoader(this, data, ITINERARY_PROJECTION, null, null, Itinerary.SORT_DEFAULT);
		cl.setUpdateThrottle(Constants.UPDATE_THROTTLE);
		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);

	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mUri, id)));
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.refresh:
			refresh(true);
			break;

		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
			break;
		}
	}
}
