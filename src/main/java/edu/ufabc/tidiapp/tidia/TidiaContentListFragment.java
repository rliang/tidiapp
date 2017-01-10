package edu.ufabc.tidiapp.tidia;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Date;

import edu.ufabc.tidiapp.R;
import edu.ufabc.tidiapp.util.Constants;

public abstract class TidiaContentListFragment<T extends TidiaContent.Table<T>> extends Fragment implements TidiaContentListAdapter.Interface<T>, SwipeRefreshLayout.OnRefreshListener, SyncStatusObserver {

    private final int id;
    private final int layout;
    private final String order;

    private T table;
    private TidiaContentListAdapter<T> adapter;
    private RecyclerView list;
    private SwipeRefreshLayout refresher;
    private Object syncHandle;

    public TidiaContentListFragment(int id, int layout, String order) {
        this.id = id;
        this.layout = layout;
        this.order = order;
        setArguments(new Bundle());
    }

    @Override
    public void bindItemView(View view, TidiaContent.Item<T> item, ContentValues values) {
        final boolean viewed = values.getAsLong(TidiaContent.KEY_ACCESS_TIME) >= values.getAsLong(TidiaContent.KEY_MODIFY_TIME);
        TextView name = (TextView) view.findViewById(R.id.label_name);
        if (name != null) {
            name.setText(values.getAsString(TidiaContent.KEY_NAME));
            name.setTypeface(null, viewed ? Typeface.NORMAL : Typeface.BOLD);
        }
    }

    @Override
    public void onItemViewClick(View view, TidiaContent.Item<T> item, ContentValues values) {
        final boolean viewed = values.getAsLong(TidiaContent.KEY_ACCESS_TIME) >= values.getAsLong(TidiaContent.KEY_MODIFY_TIME);
        if (!viewed) {
            ContentValues update = new ContentValues();
            update.put(TidiaContent.KEY_ACCESS_TIME, new Date().getTime());
            getActivity().getContentResolver().update(item.url, update, null, null);
        }
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is optional, and non-graphical fragments can return null (which
     * is the default implementation).  This will be called between
     * {@link #onCreate(Bundle)} and {@link #onActivityCreated(Bundle)}.
     * <p>
     * <p>If you return a View from here, you will later be called in
     * {@link #onDestroyView} when the view is being released.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate
     *                           any views in the fragment,
     * @param container          If non-null, this is the parent view that the fragment's
     *                           UI should be attached to.  The fragment should not add the view itself,
     *                           but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     *                           from a previous saved state as given here.
     * @return Return the View for the fragment's UI, or null.
     */
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_content_list, container, false);
        refresher = (SwipeRefreshLayout) view.findViewById(R.id.refresher);
        list = (RecyclerView) view.findViewById(R.id.list_content);
        list.addItemDecoration(new DividerItemDecoration(getContext(), ((LinearLayoutManager) list.getLayoutManager()).getOrientation()));
        return view;
    }

    /**
     * Called when the fragment's activity has been created and this
     * fragment's view hierarchy instantiated.  It can be used to do final
     * initialization once these pieces are in place, such as retrieving
     * views or restoring state.  It is also useful for fragments that use
     * {@link #setRetainInstance(boolean)} to retain their instance,
     * as this callback tells the fragment when it is fully associated with
     * the new activity instance.  This is called after {@link #onCreateView}
     * and before {@link #onViewStateRestored(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        table = (T) TidiaContent.match(Uri.parse(getArguments().getString(Constants.ARG_CONTENT_URL)));
        adapter = new TidiaContentListAdapter<>(getActivity(), table, order, layout, id, this);
        list.setAdapter(adapter);
        refresher.setOnRefreshListener(this);
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     * This is generally
     * tied to {@link Activity#onResume() Activity.onResume} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onResume() {
        super.onResume();
        adapter.register();
        syncHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
        ContentValues update = new ContentValues();
        update.put(TidiaContent.KEY_VIEW_TIME, new Date().getTime());
        getContext().getContentResolver().update(table.url, update, null, null);
        onStatusChanged(0);
    }

    /**
     * Called when the Fragment is no longer resumed.  This is generally
     * tied to {@link Activity#onPause() Activity.onPause} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onPause() {
        super.onPause();
        ContentResolver.removeStatusChangeListener(syncHandle);
    }

    /**
     * Called when the fragment is no longer attached to its activity.  This
     * is called after {@link #onDestroy()}.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        if (adapter != null)
            adapter.unregister();
    }

    /**
     * Initialize the contents of the Fragment host's standard options menu.  You
     * should place your menu items in to <var>menu</var>.  For this method
     * to be called, you must have first called {@link #setHasOptionsMenu}.  See
     * {@link Activity#onCreateOptionsMenu(Menu) Activity.onCreateOptionsMenu}
     * for more information.
     *
     * @param menu     The options menu in which you place your items.
     * @param inflater TODO
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.content_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * This hook is called whenever an item in your options menu is selected.
     * The default implementation simply returns false to have the normal
     * processing happen (calling the item's Runnable or sending a message to
     * its Handler as appropriate).  You can use this method for any items
     * for which you would like to do processing without those other
     * facilities.
     * <p>
     * <p>Derived classes should call through to the base class for it to
     * perform the default menu handling.
     *
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to
     * proceed, true to consume it here.
     * @see #onCreateOptionsMenu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ContentValues values = new ContentValues();
        switch (item.getItemId()) {
            case R.id.menu_mark_all_as_read:
                values.put(TidiaContent.KEY_ACCESS_TIME, new Date().getTime());
                getContext().getContentResolver().update(table.url, values, null, null);
                return true;
            case R.id.menu_mark_all_as_unread:
                values.put(TidiaContent.KEY_ACCESS_TIME, 0);
                getContext().getContentResolver().update(table.url, values, null, null);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when a swipe gesture triggers a refresh.
     */
    @Override
    public void onRefresh() {
        ContentResolver.requestSync(table.account, Constants.CONTENT_AUTHORITY, new Intent()
                .putExtra(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                .putExtra(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                .putExtra(Constants.ARG_CONTENT_URL, table.url.toString())
                .getExtras());
    }

    @Override
    public void onStatusChanged(int i) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refresher.setRefreshing(ContentResolver.isSyncActive(table.account, Constants.CONTENT_AUTHORITY));
            }
        });
    }
}
