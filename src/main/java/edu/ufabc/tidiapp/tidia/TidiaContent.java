package edu.ufabc.tidiapp.tidia;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import edu.ufabc.tidiapp.util.Constants;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class TidiaContent {

    public static final Uri BASE_CONTENT_URL = new Uri.Builder().scheme("content").authority(Constants.CONTENT_AUTHORITY).build();
    public static final Uri BASE_REMOTE_URL = new Uri.Builder().scheme("https").authority("tidia4.ufabc.edu.br").appendPath("direct").build();

    public static final String KEY_ACCOUNT = "account";
    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_MODIFY_TIME = "modify_time";
    public static final String KEY_VIEW_TIME = "view_time";
    public static final String KEY_ACCESS_TIME = "access_time";

    private static final String BASE_TABLE = TextUtils.join(",", new String[]{
            String.format("%s TEXT NOT NULL", KEY_ACCOUNT),
            String.format("%s TEXT NOT NULL", KEY_ID),
            String.format("%s TEXT NOT NULL", KEY_NAME),
            String.format("%s INTEGER NOT NULL", KEY_MODIFY_TIME),
            String.format("%s INTEGER NOT NULL DEFAULT 0", KEY_VIEW_TIME),
            String.format("%s INTEGER NOT NULL DEFAULT 0", KEY_ACCESS_TIME),
    });

    public static TidiaContent match(Uri uri) {
        List<String> parts = new ArrayList<>(uri.getPathSegments());
        if (parts.isEmpty())
            return null;
        String account = parts.remove(0);
        if (parts.isEmpty())
            return null;
        String segment = parts.remove(0);
        if (segment.equals(Site.TABLE_NAME))
            return new Site(account).match(parts);
        return null;
    }

    public static ContentValues cursorToValues(Cursor cursor) {
        ContentValues values = new ContentValues();
        for (int i = 0; i < cursor.getColumnCount(); i++)
            if (!cursor.isNull(i))
                values.put(cursor.getColumnName(i), cursor.getString(i));
        return values;
    }

    public static <T> T fetch(RequestFuture<T> future) throws AuthFailureError, NetworkError {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof ServerError || e.getCause() instanceof ParseError)
                throw new AuthFailureError(e.getMessage(), e);
            throw new NetworkError(e);
        }
    }

    public static String fetchToken(RequestQueue queue, String username, String password) throws AuthFailureError, NetworkError {
        Uri url = TidiaContent.BASE_REMOTE_URL.buildUpon().appendPath("session").build();
        RequestFuture<String> future = RequestFuture.newFuture();
        final Map<String, String> params = new HashMap<>();
        params.put("_username", username);
        params.put("_password", password);
        queue.add(new StringRequest(Request.Method.POST, url.toString(), future, future) {
            @Override
            public Map<String, String> getParams() {
                return params;
            }
        });
        return fetch(future);
    }

    public static JSONObject fetchJSON(RequestQueue queue, final String token, Uri url) throws AuthFailureError, NetworkError {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        queue.add(new JsonObjectRequest(Request.Method.GET, url.toString(), null, future, future) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Cookie", String.format("JSESSIONID=%s.Tidia4", token));
                headers.putAll(super.getHeaders());
                return headers;
            }
        });
        return fetch(future);
    }

    public final Uri url;
    public final String providerMime;
    public final String providerTable;
    public final Account account;

    protected final List<String> providerSelection = new ArrayList<>();
    protected final List<String> providerSelectionValues = new ArrayList<>();

    protected TidiaContent(Uri url, String providerMime, String providerTable, String account) {
        this.url = url;
        this.providerMime = providerMime;
        this.providerTable = providerTable;
        this.account = new Account(account, Constants.ACCOUNT_TYPE);
        providerSelection.add(String.format("%s=?", KEY_ACCOUNT));
        providerSelectionValues.add(account);
    }

    public final String getProviderSelection(String additional) {
        List<String> selection = new ArrayList<>(providerSelection);
        if (additional != null)
            selection.add(additional);
        return TextUtils.join(" AND ", selection);
    }

    public final String[] getProviderSelectionValues(String[] additional) {
        List<String> selection = new ArrayList<>(providerSelectionValues);
        if (additional != null)
            selection.addAll(Arrays.asList(additional));
        String[] values = new String[selection.size()];
        return selection.toArray(values);
    }

    public abstract TidiaContent match(List<String> parts);

    public abstract int sync(ContentProviderClient provider, RequestQueue queue, String token) throws AuthFailureError, NetworkError, RemoteException;

    public abstract static class Table<I extends Table<I>> extends TidiaContent {

        protected Table(Uri url, String providerTable, String account) {
            super(url, ContentResolver.CURSOR_DIR_BASE_TYPE, providerTable, account);
        }

        protected abstract Map<String, ContentValues> fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError;

        public abstract Item<I> item(String id);

        @Override
        public TidiaContent match(List<String> parts) {
            if (parts.isEmpty())
                return this;
            return item(parts.remove(0)).match(parts);
        }

        @Override
        public int sync(ContentProviderClient provider, RequestQueue queue, String token) throws AuthFailureError, NetworkError, RemoteException {
            int count = 0;
            Map<String, ContentValues> instances = fetch(queue, token);
            Cursor cursor = provider.query(url, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(KEY_ID));
                    count += item(id).update(provider, queue, token, cursorToValues(cursor), instances.remove(id));
                }
                cursor.close();
            }
            count += instances.size();
            for (ContentValues values : instances.values()) {
                provider.insert(url, values);
                for (TidiaContent c : item(values.getAsString(KEY_ID)).contents)
                    count += c.sync(provider, queue, token);
            }
            return count;
        }
    }

    public abstract static class Item<T extends Table<?>> extends TidiaContent {

        public static ContentValues diff(ContentValues a, ContentValues b) {
            ContentValues values = new ContentValues();
            for (String key : a.keySet())
                if (!a.getAsString(key).equals(b.getAsString(key)))
                    values.put(key, a.getAsString(key));
            return values;
        }

        public final List<TidiaContent> contents = new ArrayList<>();

        public final T table;
        public final String id;

        protected Item(T table, String id) {
            super(Uri.withAppendedPath(table.url, id), ContentResolver.CURSOR_ITEM_BASE_TYPE, table.providerTable, table.account.name);
            providerSelection.addAll(table.providerSelection);
            providerSelectionValues.addAll(table.providerSelectionValues);
            providerSelection.add(String.format("%s=?", KEY_ID));
            providerSelectionValues.add(id);
            this.table = table;
            this.id = id;
        }

        protected abstract ContentValues fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError;

        protected int update(ContentProviderClient provider, RequestQueue queue, String token, ContentValues local, ContentValues remote) throws RemoteException, AuthFailureError, NetworkError {
            int count = 0;
            if (remote == null) {
                provider.delete(url, null, null);
                return count;
            }
            remote = diff(remote, local);
            if (remote.size() > 0)
                count += provider.update(url, remote, null, null);
            for (TidiaContent c : contents)
                count += c.sync(provider, queue, token);
            return count;
        }

        @Override
        public TidiaContent match(List<String> parts) {
            if (parts.isEmpty())
                return this;
            String part = parts.remove(0);
            for (TidiaContent c : contents)
                if (part.equals(c.providerTable))
                    return c.match(parts);
            return null;
        }

        @Override
        public int sync(ContentProviderClient provider, RequestQueue queue, String token) throws AuthFailureError, NetworkError, RemoteException {
            int count = 0;
            Cursor cursor = provider.query(url, null, null, null, null);
            if (cursor == null)
                return count;
            if (cursor.moveToNext())
                count += update(provider, queue, token, cursorToValues(cursor), fetch(queue, token));
            cursor.close();
            return count;
        }
    }

    public static class Site extends Table<Site> {

        public static final String TABLE_NAME = "site";
        public static final String KEY_DESCRIPTION = "description";
        public static final String KEY_AUTHOR_NAME = "author_name";
        public static final String KEY_AUTHOR_EMAIL = "author_email";
        public static final String KEY_CREATE_TIME = "create_time";
        public static final String KEY_IS_SYNCABLE = "is_syncable";
        public static final String TABLE = TextUtils.join(",", new String[]{BASE_TABLE,
                String.format("%s TEXT NOT NULL", KEY_DESCRIPTION),
                String.format("%s TEXT NOT NULL", KEY_AUTHOR_NAME),
                String.format("%s TEXT NOT NULL", KEY_AUTHOR_EMAIL),
                String.format("%s INTEGER NOT NULL", KEY_CREATE_TIME),
                String.format("%s INTEGER NOT NULL DEFAULT 0", KEY_IS_SYNCABLE),
                String.format("PRIMARY KEY(%s,%s)", KEY_ACCOUNT, KEY_ID),
        });

        public Site(String account) {
            super(BASE_CONTENT_URL.buildUpon().appendPath(account).appendPath(TABLE_NAME).build(), TABLE_NAME, account);
        }

        private ContentValues instance(JSONObject json) {
            ContentValues values = new ContentValues();
            values.put(KEY_ACCOUNT, account.name);
            values.put(KEY_ID, json.optString("id"));
            values.put(KEY_NAME, json.optString("title"));
            values.put(KEY_DESCRIPTION, json.optString("description"));
            values.put(KEY_CREATE_TIME, json.optLong("createdDate"));
            values.put(KEY_MODIFY_TIME, json.optLong("modifiedDate"));
            values.put(KEY_AUTHOR_NAME, json.optJSONObject("props").optString("contact-name"));
            values.put(KEY_AUTHOR_EMAIL, json.optJSONObject("props").optString("contact-email"));
            return values;
        }

        @Override
        protected Map<String, ContentValues> fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
            JSONArray json = fetchJSON(queue, token, BASE_REMOTE_URL.buildUpon().appendPath("site").build()).optJSONArray("site_collection");
            Map<String, ContentValues> instances = new HashMap<>();
            for (int i = 0; i < json.length(); i++) {
                ContentValues values = instance(json.optJSONObject(i));
                instances.put(values.getAsString(KEY_ID), values);
            }
            return instances;
        }

        @Override
        public Item<Site> item(String id) {
            Item<Site> item = new Item<Site>(this, id) {
                @Override
                protected ContentValues fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
                    return table.instance(fetchJSON(queue, token, BASE_REMOTE_URL.buildUpon().appendPath("site").appendPath(id).build()));
                }
            };
            item.contents.add(new Assignment(item));
            item.contents.add(new Resource(item));
            return item;
        }
    }

    public static class Assignment extends Table<Assignment> {

        public static final String TABLE_NAME = "assignment";
        public static final String KEY_SITE_ID = "site_id";
        public static final String KEY_DESCRIPTION = "description";
        public static final String KEY_CREATE_TIME = "create_time";
        public static final String KEY_EXPIRY_TIME = "expiry_time";
        public static final String TABLE = TextUtils.join(",", new String[]{BASE_TABLE,
                String.format("%s TEXT NOT NULL", KEY_DESCRIPTION),
                String.format("%s INTEGER NOT NULL", KEY_CREATE_TIME),
                String.format("%s INTEGER NOT NULL", KEY_EXPIRY_TIME),
                String.format("%s TEXT NOT NULL", KEY_SITE_ID),
                String.format("PRIMARY KEY(%s,%s,%s)", KEY_ACCOUNT, KEY_ID, KEY_SITE_ID),
                String.format("FOREIGN KEY(%s,%s) REFERENCES %s(%s,%s) ON DELETE CASCADE", KEY_ACCOUNT, KEY_SITE_ID, Site.TABLE_NAME, KEY_ACCOUNT, KEY_ID),
        });

        public final Item<Site> site;

        public Assignment(Item<Site> site) {
            super(site.url.buildUpon().appendPath(TABLE_NAME).build(), TABLE_NAME, site.account.name);
            providerSelection.add(String.format("%s=?", KEY_SITE_ID));
            providerSelectionValues.add(site.id);
            this.site = site;
        }

        private ContentValues instance(JSONObject json) {
            ContentValues values = new ContentValues();
            values.put(KEY_ACCOUNT, account.name);
            values.put(KEY_ID, json.optString("id"));
            values.put(KEY_NAME, json.optString("title"));
            values.put(KEY_DESCRIPTION, json.optString("instructions"));
            values.put(KEY_CREATE_TIME, json.optJSONObject("timeCreated").optLong("time"));
            values.put(KEY_MODIFY_TIME, json.optJSONObject("timeLastModified").optLong("time"));
            values.put(KEY_EXPIRY_TIME, json.optJSONObject("dropDeadTime").optLong("time"));
            values.put(KEY_SITE_ID, site.id);
            return values;
        }

        @Override
        protected Map<String, ContentValues> fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
            JSONArray json = fetchJSON(queue, token, BASE_REMOTE_URL.buildUpon().appendPath("assignment").appendPath("site").appendPath(site.id).build()).optJSONArray("assignment_collection");
            Map<String, ContentValues> instances = new HashMap<>();
            for (int i = 0; i < json.length(); i++) {
                ContentValues values = instance(json.optJSONObject(i));
                instances.put(values.getAsString(KEY_ID), values);
            }
            return instances;
        }

        @Override
        public Item<Assignment> item(String id) {
            return new Item<Assignment>(this, id) {
                @Override
                protected ContentValues fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
                    return table.instance(fetchJSON(queue, token, BASE_REMOTE_URL.buildUpon().appendPath("assignment").appendPath("item").appendPath(id).build()));
                }
            };
        }
    }

    public static class Resource extends Table<Resource> {

        public static final String TABLE_NAME = "resource";
        public static final String KEY_SITE_ID = "site_id";
        public static final String KEY_URL = "url";
        public static final String KEY_SIZE = "size";
        public static final String TABLE = TextUtils.join(",", new String[]{BASE_TABLE,
                String.format("%s TEXT NOT NULL", KEY_URL),
                String.format("%s INTEGER NOT NULL", KEY_SIZE),
                String.format("%s TEXT NOT NULL", KEY_SITE_ID),
                String.format("PRIMARY KEY(%s,%s,%s)", KEY_ACCOUNT, KEY_ID, KEY_SITE_ID),
                String.format("FOREIGN KEY(%s,%s) REFERENCES %s(%s,%s) ON DELETE CASCADE", KEY_ACCOUNT, KEY_SITE_ID, Site.TABLE_NAME, KEY_ACCOUNT, KEY_ID),
        });

        public final Item<Site> site;

        public Resource(Item<Site> site) {
            super(site.url.buildUpon().appendPath(TABLE_NAME).build(), TABLE_NAME, site.account.name);
            providerSelection.add(String.format("%s=?", KEY_SITE_ID));
            providerSelectionValues.add(site.id);
            this.site = site;
        }

        private Long parseTime(String format) {
            try {
                return new SimpleDateFormat("yyyyMMddhhmmssSSS").parse(format).getTime();
            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        }

        private ContentValues instance(JSONObject json) {
            ContentValues values = new ContentValues();
            values.put(KEY_ACCOUNT, account.name);
            values.put(KEY_ID, String.valueOf(json.optString("url").hashCode()));
            values.put(KEY_NAME, json.optString("title"));
            values.put(KEY_SIZE, json.optLong("size"));
            values.put(KEY_URL, json.optString("url"));
            values.put(KEY_MODIFY_TIME, parseTime(json.optString("modifiedDate")));
            values.put(KEY_SITE_ID, site.id);
            return values;
        }

        @Override
        protected Map<String, ContentValues> fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
            JSONArray json = fetchJSON(queue, token, BASE_REMOTE_URL.buildUpon().appendPath("content").appendPath("site").appendPath(site.id).build()).optJSONArray("content_collection");
            Map<String, ContentValues> instances = new HashMap<>();
            for (int i = 0; i < json.length(); i++) {
                ContentValues values = instance(json.optJSONObject(i));
                instances.put(values.getAsString(KEY_ID), values);
            }
            return instances;
        }

        @Override
        public Item<Resource> item(String id) {
            return new Item<Resource>(this, id) {
                @Override
                protected ContentValues fetch(RequestQueue queue, String token) throws AuthFailureError, NetworkError {
                    return table.fetch(queue, token).get(id);
                }
            };
        }
    }
}
