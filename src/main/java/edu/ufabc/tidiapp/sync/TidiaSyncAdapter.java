package edu.ufabc.tidiapp.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Date;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import edu.ufabc.tidiapp.R;
import edu.ufabc.tidiapp.SiteActivity;
import edu.ufabc.tidiapp.tidia.TidiaContent;
import edu.ufabc.tidiapp.util.Constants;
import hugo.weaving.DebugLog;

@DebugLog
public class TidiaSyncAdapter extends AbstractThreadedSyncAdapter {

    public static SSLSocketFactory getSocketFactory(Context context) {
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            InputStream inputStream = context.getResources().openRawResource(R.raw.cert);
            Certificate certificate = certificateFactory.generateCertificate(inputStream);
            inputStream.close();
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", certificate);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            socketFactory = sslContext.getSocketFactory();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException e) {
            e.printStackTrace();
        }
        return socketFactory;
    }

    private final RequestQueue requestQueue;

    TidiaSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        requestQueue = Volley.newRequestQueue(context, new HurlStack(null, getSocketFactory(context)));
    }

    public String authenticate(String username, String password) throws AuthFailureError, NetworkError {
        return TidiaContent.fetchToken(requestQueue, username, password);
    }

    private void sync(ContentProviderClient provider, Account account, String token) throws RemoteException, AuthFailureError, NetworkError {
        TidiaContent.Site sites = new TidiaContent.Site(account.name);
        Cursor cursor = provider.query(sites.url, null, String.format("%s=1", TidiaContent.Site.KEY_IS_SYNCABLE), null, null);
        if (cursor == null)
            return;
        while (cursor.moveToNext()) {
            TidiaContent.Item<TidiaContent.Site> site = sites.item(cursor.getString(cursor.getColumnIndex(TidiaContent.KEY_ID)));
            if (site.sync(provider, requestQueue, token) > 0)
                notify(provider, site, TidiaContent.cursorToValues(cursor));
        }
        cursor.close();
    }

    private void notify(ContentProviderClient provider, TidiaContent.Item<TidiaContent.Site> site, ContentValues values) throws RemoteException {
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
                .setSummaryText(site.account.name);
        int number = 0;
        for (TidiaContent c : site.contents) {
            Cursor cursor = provider.query(c.url, null, String.format("%s<%s", TidiaContent.KEY_VIEW_TIME, TidiaContent.KEY_MODIFY_TIME), null, null);
            if (cursor == null)
                continue;
            number += cursor.getCount();
            while (cursor.moveToNext())
                style.addLine(cursor.getString(cursor.getColumnIndex(TidiaContent.KEY_NAME)));
            cursor.close();
        }
        if (number <= 0)
            return;
        Intent intent = new Intent(getContext(), SiteActivity.class)
                .setAction(Long.toString(new Date().getTime()))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SiteActivity.ARG_SITE_VALUES, values);
        Notification notification = new NotificationCompat.Builder(getContext())
                .setContentTitle(values.getAsString(TidiaContent.KEY_NAME))
                .setContentText(getContext().getString(R.string.notification_new_content))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setNumber(number)
                .setVibrate(new long[]{0,100,100,100,100})
                .setLights(Color.MAGENTA, 3000, 3000)
                .setContentIntent(PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT))
                .setAutoCancel(true)
                .setStyle(style)
                .build();
        ((NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(site.id.hashCode(), notification);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        try {
            AccountManager manager = AccountManager.get(getContext());
            String token = manager.blockingGetAuthToken(account, account.type, true);
            manager.invalidateAuthToken(account.type, token);
            TidiaContent content = null;
            if (extras.containsKey(Constants.ARG_CONTENT_URL))
                content = TidiaContent.match(Uri.parse(extras.getString(Constants.ARG_CONTENT_URL)));
            if (content != null)
                content.sync(provider, requestQueue, token);
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_EXPEDITED))
                sync(provider, account, token);
        } catch (AuthFailureError|AuthenticatorException e) {
            e.printStackTrace();
            syncResult.stats.numAuthExceptions++;
        } catch (IOException|NetworkError e) {
            e.printStackTrace();
            syncResult.stats.numIoExceptions++;
        } catch (RemoteException e) {
            e.printStackTrace();
            syncResult.databaseError = true;
        } catch (OperationCanceledException e) {
            e.printStackTrace();
        }
    }
}
