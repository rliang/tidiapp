package edu.ufabc.tidiapp;

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;

import edu.ufabc.tidiapp.account.TidiaAccountAuthenticator;
import edu.ufabc.tidiapp.sync.TidiaSyncService;

public class LoginActivity extends AppCompatActivity {

    private UserLoginTask userLoginTask;
    private EditText usernameField;
    private EditText passwordField;

    private AccountAuthenticatorResponse response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        response = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
        usernameField = (EditText) findViewById(R.id.login_field_username);
        passwordField = (EditText) findViewById(R.id.login_field_password);
        passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id != R.id.action_login && id != EditorInfo.IME_NULL)
                    return false;
                login();
                return true;
            }
        });
        Button button = (Button) findViewById(R.id.button_sign_in);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                login();
            }
        });
    }

    private void login() {
        if (userLoginTask != null)
            return;
        usernameField.setError(null);
        passwordField.setError(null);
        String username = usernameField.getText().toString();
        String password = passwordField.getText().toString();
        boolean cancel = false;
        View focusView = null;
        if (TextUtils.isEmpty(username)) {
            usernameField.setError(getString(R.string.error_field_required));
            focusView = usernameField;
            cancel = true;
        }
        if (cancel) {
            focusView.requestFocus();
            return;
        }
        showProgress(true);
        userLoginTask = new UserLoginTask(username, password);
        userLoginTask.execute((Void) null);
    }

    private void showProgress(final boolean show) {
        int time = getResources().getInteger(android.R.integer.config_shortAnimTime);
        findViewById(R.id.form_login).setVisibility(show ? View.GONE : View.VISIBLE);
        findViewById(R.id.form_login).animate().setDuration(time).alpha(show ? 0 : 1)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        findViewById(R.id.form_login).setVisibility(show ? View.GONE : View.VISIBLE);
                    }
                });
        findViewById(R.id.login_progress).setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.login_progress).animate().setDuration(time).alpha(show ? 1 : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        findViewById(R.id.login_progress).setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private class UserLoginTask extends AsyncTask<Void, Void, String> {

        private final String username;
        private final String password;
        private Integer error = null;

        UserLoginTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return TidiaSyncService.getAdapter(getApplicationContext()).authenticate(username, password);
            } catch (AuthFailureError e) {
                e.printStackTrace();
                error = R.string.error_login;
                return null;
            } catch (NetworkError e) {
                e.printStackTrace();
                error = R.string.error_connection;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String token) {
            onCancelled();
            if (token == null && error != null) {
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                passwordField.requestFocus();
                return;
            }
            Intent intent = TidiaAccountAuthenticator.add(LoginActivity.this, username, password);
            response.onResult(intent.getExtras());
            setResult(RESULT_OK, intent);
            finish();
        }

        @Override
        protected void onCancelled() {
            userLoginTask = null;
            showProgress(false);
        }
    }
}

