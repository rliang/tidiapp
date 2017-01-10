package edu.ufabc.tidiapp.account;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;

import edu.ufabc.tidiapp.LoginActivity;
import edu.ufabc.tidiapp.sync.TidiaSyncService;
import edu.ufabc.tidiapp.util.Constants;

public class TidiaAccountAuthenticator extends AbstractAccountAuthenticator {

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(AccountManager.KEY_ACCOUNTS, Context.MODE_PRIVATE);
    }

    public static Account login(Activity context) {
        AccountManager manager = AccountManager.get(context);
        Account account = null;
        String username = getSharedPreferences(context).getString(AccountManager.KEY_ACCOUNT_NAME, null);
        if (username != null)
            account = new Account(username, Constants.ACCOUNT_TYPE);
        if (account != null && manager.getPassword(account) != null)
            return account;
        return null;
    }

    public static Intent add(Activity context, String username, String password) {
        AccountManager manager = AccountManager.get(context);
        Account account = new Account(username, Constants.ACCOUNT_TYPE);
        if (manager.addAccountExplicitly(account, password, null)) {
            ContentResolver.setIsSyncable(account, Constants.CONTENT_AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, Constants.CONTENT_AUTHORITY, true);
            ContentResolver.addPeriodicSync(account, Constants.CONTENT_AUTHORITY, new Bundle(), Constants.SYNC_FREQUENCY);
        } else {
            manager.setPassword(account, password);
        }
        Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
        getSharedPreferences(context).edit().putString(AccountManager.KEY_ACCOUNT_NAME, account.name).apply();
        return intent;
    }

    @SuppressWarnings("deprecation")
    public static void remove(Activity context, String username) {
        AccountManager manager = AccountManager.get(context);
        Account account = new Account(username, Constants.ACCOUNT_TYPE);
        getSharedPreferences(context).edit().remove(AccountManager.KEY_ACCOUNT_NAME).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            manager.removeAccount(account, context, null, null);
        else
            manager.removeAccount(account, null, null);
    }

    private final Context context;

    TidiaAccountAuthenticator(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse,
                                 String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
                      String type, String authTokenType, String[] strings, Bundle bundle)
            throws NetworkErrorException {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, type);
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse,
                                     Account account, Bundle bundle)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response,
                               Account account, String authTokenType, Bundle extras)
            throws NetworkErrorException {
        Bundle bundle = new Bundle();
        try {
            String password = AccountManager.get(context).getPassword(account);
            String token = TidiaSyncService.getAdapter(context).authenticate(account.name, password);
            bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            bundle.putString(AccountManager.KEY_AUTHTOKEN, token);
            return bundle;
        } catch (AuthFailureError e) {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
            intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle;
        } catch (NetworkError e) {
            throw new NetworkErrorException(e);
        }
    }

    @Override
    public String getAuthTokenLabel(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse,
                                    Account account, String s, Bundle bundle)
            throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse accountAuthenticatorResponse,
                              Account account, String[] strings)
            throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
}
