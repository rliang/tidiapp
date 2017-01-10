package edu.ufabc.tidiapp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.io.IOException;

import edu.ufabc.tidiapp.account.TidiaAccountAuthenticator;
import edu.ufabc.tidiapp.tidia.TidiaContent;
import edu.ufabc.tidiapp.util.Constants;

public class SplashActivity extends AppCompatActivity implements AccountManagerCallback<Bundle> {

    private void startMainActivity(Account account) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name)
                .putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Account account = TidiaAccountAuthenticator.login(this);
        if (account == null) {
            AccountManager.get(this).addAccount(Constants.ACCOUNT_TYPE, null, null, null, this, this, null);
        } else {
            startMainActivity(account);
            finish();
        }
    }

    @Override
    public void run(AccountManagerFuture<Bundle> accountManagerFuture) {
        try {
            Bundle bundle = accountManagerFuture.getResult();
            Account account = new Account(bundle.getString(AccountManager.KEY_ACCOUNT_NAME), bundle.getString(AccountManager.KEY_ACCOUNT_TYPE));
            ContentResolver.requestSync(account, Constants.CONTENT_AUTHORITY, new Intent()
                    .putExtra(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    .putExtra(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    .putExtra(Constants.ARG_CONTENT_URL, new TidiaContent.Site(account.name).url.toString())
                    .getExtras());
            startMainActivity(account);
        } catch (OperationCanceledException | IOException | AuthenticatorException e) {
            e.printStackTrace();
        } finally {
            finish();
        }
    }
}
