package edu.ufabc.tidiapp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import edu.ufabc.tidiapp.account.TidiaAccountAuthenticator;
import edu.ufabc.tidiapp.tidia.TidiaContent;
import edu.ufabc.tidiapp.tidia.TidiaContentListFragment;
import edu.ufabc.tidiapp.util.Constants;

public class MainActivity extends AppCompatActivity {

    private Account account;

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_sites);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View view = toolbar.getChildAt(i);
                if (view instanceof TextView) {
                    view.setId(R.id.title);
                    view.setTransitionName(getString(R.string.transition_title));
                }
            }
        }
        setSupportActionBar(toolbar);
        account = new Account(getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_NAME), getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment);
        fragment.getArguments().putString(Constants.ARG_CONTENT_URL, new TidiaContent.Site(account.name).url.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_signout:
                TidiaAccountAuthenticator.remove(this, account.name);
                startActivity(new Intent(this, SplashActivity.class));
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SiteListFragment extends TidiaContentListFragment<TidiaContent.Site> {

        public SiteListFragment() {
            super(0, R.layout.fragment_item_site, String.format("%s DESC,%s DESC", TidiaContent.Site.KEY_IS_SYNCABLE, TidiaContent.Site.KEY_CREATE_TIME));
        }

        @Override
        public void bindItemView(View view, final TidiaContent.Item<TidiaContent.Site> item, final ContentValues values) {
            super.bindItemView(view, item, values);
            ((TextView) view.findViewById(R.id.label_owner)).setText(values.getAsString((TidiaContent.Site.KEY_AUTHOR_NAME)));
            ((Switch) view.findViewById(R.id.switch_syncable)).setOnCheckedChangeListener(null);
            ((Switch) view.findViewById(R.id.switch_syncable)).setChecked(values.containsKey(TidiaContent.Site.KEY_IS_SYNCABLE) && values.getAsLong((TidiaContent.Site.KEY_IS_SYNCABLE)) > 0);
            ((Switch) view.findViewById(R.id.switch_syncable)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean c) {
                    values.put(TidiaContent.Site.KEY_IS_SYNCABLE, c);
                    getContext().getContentResolver().update(item.url, values, null, null);
                    Toast.makeText(getContext(), getContext().getString(R.string.toast_sync_notifications, c ? getContext().getString(R.string.status_enabled_multiple) : getContext().getString(R.string.status_disabled_multiple), values.getAsString(TidiaContent.Site.KEY_NAME)), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onItemViewClick(View view, TidiaContent.Item<TidiaContent.Site> item, ContentValues values) {
            super.onItemViewClick(view, item, values);
            startActivity(new Intent(getContext(), SiteActivity.class)
                            .putExtra(SiteActivity.ARG_SITE_VALUES, values),
                    ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                            Pair.create(getActivity().findViewById(R.id.appbar), getString(R.string.transition_appbar)),
                            Pair.create(getActivity().findViewById(R.id.title), getString(R.string.transition_title))).toBundle());
        }
    }
}
