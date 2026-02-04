/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import net.micode.notes.R;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.viewmodel.LoginViewModel;

/**
 * 登录界面
 *
 * <p>
 * 提供用户登录和注册功能。
 * 登录成功后，用户的笔记会自动同步到云端。
 * </p>
 * <p>
 * 遵循 MVVM 架构，所有业务逻辑委托给 {@link LoginViewModel}。
 * </p>
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    private TextInputEditText mEtUsername;
    private TextInputEditText mEtPassword;
    private MaterialButton mBtnLogin;
    private MaterialButton mBtnRegister;
    private View mTvSkip;
    private ProgressBar mProgressBar;

    private LoginViewModel mViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查是否已经登录
        UserAuthManager authManager = UserAuthManager.getInstance(this);
        if (authManager.isLoggedIn()) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_login);

        mViewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        initViews();
        setupListeners();
        observeViewModel();
    }

    private void initViews() {
        mEtUsername = findViewById(R.id.et_username);
        mEtPassword = findViewById(R.id.et_password);
        mBtnLogin = findViewById(R.id.btn_login);
        mBtnRegister = findViewById(R.id.btn_register);
        mTvSkip = findViewById(R.id.tv_skip);
        mProgressBar = findViewById(R.id.progress_bar);
    }

    private void setupListeners() {
        mBtnLogin.setOnClickListener(v -> attemptLogin());
        mBtnRegister.setOnClickListener(v -> attemptRegister());
        mTvSkip.setOnClickListener(v -> skipLogin());
    }

    private void observeViewModel() {
        mViewModel.getIsLoading().observe(this, isLoading -> showLoading(isLoading));

        mViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                mViewModel.clearError();
            }
        });

        mViewModel.getLoginSuccess().observe(this, success -> {
            if (success) {
                Integer migratedCount = mViewModel.getMigratedNotesCount().getValue();
                String message = (migratedCount != null && migratedCount > 0)
                        ? "登录成功！已迁移 " + migratedCount + " 条本地笔记"
                        : "登录成功！";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                startMainActivity();
            }
        });
    }

    private void attemptLogin() {
        String username = mEtUsername.getText().toString().trim();
        String password = mEtPassword.getText().toString().trim();
        mViewModel.login(username, password);
    }

    private void attemptRegister() {
        String username = mEtUsername.getText().toString().trim();
        String password = mEtPassword.getText().toString().trim();
        mViewModel.register(username, password);
    }

    private void skipLogin() {
        Toast.makeText(this, "使用本地模式（不同步）", Toast.LENGTH_SHORT).show();
        startMainActivity();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, NotesListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mBtnLogin.setEnabled(!show);
        mBtnRegister.setEnabled(!show);
    }
}
