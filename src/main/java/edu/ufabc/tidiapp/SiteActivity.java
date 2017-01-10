package edu.ufabc.tidiapp;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import edu.ufabc.tidiapp.tidia.TidiaContent;
import edu.ufabc.tidiapp.tidia.TidiaContentListFragment;
import edu.ufabc.tidiapp.sync.TidiaSyncAdapter;
import edu.ufabc.tidiapp.util.Constants;

public class SiteActivity extends AppCompatActivity {

    public static final String ARG_SITE_VALUES = "SITE_ITEM";

    private ContentValues site;

    private final FragmentPagerAdapter adapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
        @Override
        public Fragment getItem(int position) {
            TidiaContent.Item<TidiaContent.Site> item = new TidiaContent.Site(site.getAsString(TidiaContent.KEY_ACCOUNT)).item(site.getAsString(TidiaContent.KEY_ID));
            Bundle bundle = new Bundle();
            Fragment fragment;
            switch (position) {
                case 1:
                    bundle.putString(Constants.ARG_CONTENT_URL, new TidiaContent.Resource(item).url.toString());
                    fragment = new ResourceListFragment();
                    break;
                case 2:
                    bundle.putString(Constants.ARG_CONTENT_URL, new TidiaContent.Assignment(item).url.toString());
                    fragment = new AssignmentListFragment();
                    break;
                default:
                    bundle.putParcelable(ARG_SITE_VALUES, site);
                    fragment = new DescriptionFragment();
                    break;
            }
            fragment.setArguments(bundle);
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 1:
                    return getString(R.string.title_resources);
                case 2:
                    return getString(R.string.title_assignments);
                default:
                    return getString(R.string.title_description);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site);
        site = getIntent().getParcelableExtra(ARG_SITE_VALUES);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(site.getAsString(TidiaContent.KEY_NAME));
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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final ViewPager pager = (ViewPager) findViewById(R.id.container);
        pager.setAdapter(adapter);
        TabLayout layout = (TabLayout) findViewById(R.id.tabs);
        layout.setupWithViewPager(pager);
        layout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                .putExtra(Intent.EXTRA_EMAIL, new String[]{site.getAsString(TidiaContent.Site.KEY_AUTHOR_EMAIL)})
                                .putExtra(Intent.EXTRA_SUBJECT, site.getAsString(TidiaContent.KEY_NAME))
                                .setType("message/rfc822"),
                        getString(R.string.prompt_send_email)));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class DescriptionFragment extends Fragment {

        @SuppressWarnings("ConstantConditions")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            WebView view = (WebView) inflater.inflate(R.layout.fragment_item_site_description, container, false);
            view.setBackgroundColor(0);
            view.getSettings().setDefaultTextEncodingName("utf-8");
            ContentValues values = getArguments().getParcelable(ARG_SITE_VALUES);
            view.loadData(values.getAsString(TidiaContent.Site.KEY_DESCRIPTION), "text/html; charset=UTF-8", "UTF-8");
            return view;
        }
    }

    public static class AssignmentListFragment extends TidiaContentListFragment<TidiaContent.Assignment> {

        public AssignmentListFragment() {
            super(1, R.layout.fragment_item_site_assignment, String.format("%s DESC", TidiaContent.Assignment.KEY_EXPIRY_TIME));
        }

        @Override
        public void bindItemView(View view, TidiaContent.Item<TidiaContent.Assignment> item, ContentValues values) {
            super.bindItemView(view, item, values);
            if (values.getAsLong(TidiaContent.Assignment.KEY_EXPIRY_TIME) >= new Date().getTime()) {
                ((TextView) view.findViewById(R.id.label_deadline)).setText(getString(R.string.info_due, DateUtils.formatDateTime(getContext(), values.getAsLong(TidiaContent.Assignment.KEY_EXPIRY_TIME), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL)));
            } else {
                ((TextView) view.findViewById(R.id.label_name)).setTextColor(ContextCompat.getColor(getContext(), android.R.color.secondary_text_light_nodisable));
                ((TextView) view.findViewById(R.id.label_deadline)).setText(R.string.status_closed);
            }
        }

        @Override
        public void onItemViewClick(View view, final TidiaContent.Item<TidiaContent.Assignment> item, ContentValues values) {
            super.onItemViewClick(view, item, values);
            AccountManager.get(getContext()).getAuthToken(item.account, item.account.type, null, getActivity(), new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        String token = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                        AccountManager.get(getContext()).invalidateAuthToken(token, Constants.ACCOUNT_TYPE);
                        Uri url = TidiaContent.BASE_REMOTE_URL.buildUpon()
                                .appendPath("assignment")
                                .appendPath(item.id)
                                .appendQueryParameter("sakai.session", token)
                                .build();
                        Bundle headers = new Bundle();
                        headers.putString("Cookie", String.format("JSESSIONID=%s.Tidia4", token));
                        startActivity(new Intent(Intent.ACTION_VIEW, url)
                                .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                                .putExtra(Browser.EXTRA_HEADERS, headers));
                    } catch (AuthenticatorException | IOException | OperationCanceledException e1) {
                        e1.printStackTrace();
                        Toast.makeText(getContext(), e1.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }, null);
        }
    }

    public static class ResourceListFragment extends TidiaContentListFragment<TidiaContent.Resource> {

        public Runnable permissionCallback;

        public ResourceListFragment() {
            super(0, R.layout.fragment_item_site_resource, String.format("%s DESC", TidiaContent.Resource.KEY_MODIFY_TIME));
        }

        /**
         * Callback for the result from requesting permissions. This method
         * is invoked for every call on {@link #requestPermissions(String[], int)}.
         * <p>
         * <strong>Note:</strong> It is possible that the permissions request interaction
         * with the user is interrupted. In this case you will receive empty permissions
         * and results arrays which should be treated as a cancellation.
         * </p>
         *
         * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
         * @param permissions  The requested permissions. Never null.
         * @param grantResults The grant results for the corresponding permissions
         *                     which is either {@link PackageManager#PERMISSION_GRANTED}
         *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
         * @see #requestPermissions(String[], int)
         */
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            for (int i : grantResults)
                if (i != PackageManager.PERMISSION_GRANTED)
                    return;
            if (permissionCallback != null)
                permissionCallback.run();
        }

        private boolean requestPermissions() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                return true;
            List<String> permissions = new ArrayList<>();
            for (String p : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
                if (getContext().checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                    permissions.add(p);
            if (permissions.isEmpty())
                return true;
            String[] array = new String[permissions.size()];
            requestPermissions(permissions.toArray(array), 0);
            return false;
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private void download(final String dir, final String url, final long size, final Account account) {
            if (!requestPermissions()) {
                permissionCallback = new Runnable() {
                    @Override
                    public void run() {
                        download(dir, url, size, account);
                    }
                };
                return;
            }
            File directory = new File(ContextCompat.getExternalFilesDirs(getContext(), null)[0], dir);
            if (!directory.exists())
                directory.mkdirs();
            Uri uri = Uri.parse(url);
            File file = new File(directory, uri.getLastPathSegment());
            DownloadTask task = new DownloadTask(uri, file, size, account);
            if (file.exists() && file.length() == size)
                task.onPostExecute(null);
            else
                task.execute();
        }

        @Override
        public void bindItemView(View view, TidiaContent.Item<TidiaContent.Resource> item, ContentValues values) {
            super.bindItemView(view, item, values);
            ((TextView) view.findViewById(R.id.label_created_time)).setText(DateUtils.formatDateTime(getContext(), values.getAsLong(TidiaContent.KEY_MODIFY_TIME), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL));
            ((TextView) view.findViewById(R.id.label_size)).setText(Formatter.formatFileSize(getContext(), values.getAsLong(TidiaContent.Resource.KEY_SIZE)));
        }

        @Override
        public void onItemViewClick(View view, final TidiaContent.Item<TidiaContent.Resource> item, final ContentValues values) {
            super.onItemViewClick(view, item, values);
            download(values.getAsString(TidiaContent.Resource.KEY_SITE_ID), values.getAsString(TidiaContent.Resource.KEY_URL), values.getAsLong(TidiaContent.Resource.KEY_SIZE), item.account);
        }

        private class DownloadTask extends AsyncTask<Void, Long, Void> implements View.OnClickListener {

            private final Uri url;
            private final File file;
            private final long size;
            private final Account account;

            private Snackbar snackbar;
            private Exception exception;

            DownloadTask(Uri url, File file, long size, Account account) {
                this.url = url;
                this.file = file;
                this.size = size;
                this.account = account;
            }

            @Override
            protected void onPreExecute() {
                snackbar = Snackbar.make(getActivity().findViewById(R.id.fab), R.string.toast_downloading, Snackbar.LENGTH_INDEFINITE).setAction(R.string.action_cancel, this);
                snackbar.show();
            }

            @Override
            protected Void doInBackground(Void... args) {
                try {
                    String token = AccountManager.get(getContext()).blockingGetAuthToken(account, account.type, false);
                    HttpsURLConnection connection = (HttpsURLConnection) new URL(url.toString()).openConnection();
                    connection.setSSLSocketFactory(TidiaSyncAdapter.getSocketFactory(getContext()));
                    connection.setRequestProperty("Cookie", String.format("JSESSIONID=%s.Tidia4", token));
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream in = connection.getInputStream();
                    OutputStream out = new FileOutputStream(file);
                    int count;
                    long total = 0;
                    byte[] buffer = new byte[1024];
                    while (!isCancelled() && (count = in.read(buffer)) > 0) {
                        out.write(buffer, 0, count);
                        publishProgress(total += count, size);
                    }
                    in.close();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    exception = e;
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Long... values) {
                snackbar.setText(getString(R.string.toast_downloading_progress, values[0] * 100 / values[1]));
            }

            @Override
            public void onPostExecute(Void arg) {
                onCancelled();
                if (exception != null) {
                    Toast.makeText(getContext(), R.string.error_connection, Toast.LENGTH_LONG).show();
                    return;
                }
                Uri url = Uri.fromFile(file);
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        .setDataAndType(url, MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url.toString())));
                startActivity(Intent.createChooser(intent, getString(R.string.prompt_open_file, file.getName())));
            }

            @Override
            protected void onCancelled() {
                if (snackbar != null)
                    snackbar.dismiss();
            }

            @Override
            public void onClick(View view) {
                cancel(true);
            }
        }
    }
}
