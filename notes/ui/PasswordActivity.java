package net.micode.notes.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.databinding.ActivityPasswordBinding;
import net.micode.notes.tool.SecurityManager;

import java.util.List;

public class PasswordActivity extends Activity {

    public static final String ACTION_SETUP_PASSWORD = "net.micode.notes.action.SETUP_PASSWORD";
    public static final String ACTION_CHECK_PASSWORD = "net.micode.notes.action.CHECK_PASSWORD";
    public static final String EXTRA_PASSWORD_TYPE = "extra_password_type";

    private int mMode; // 0: Check, 1: Setup
    private int mPasswordType;

    private ActivityPasswordBinding binding;

    private String mFirstInput = null; // For setup confirmation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String action = getIntent().getAction();
        if (ACTION_SETUP_PASSWORD.equals(action)) {
            mMode = 1;
            mPasswordType = getIntent().getIntExtra(EXTRA_PASSWORD_TYPE, SecurityManager.TYPE_PIN);
        } else {
            mMode = 0;
            // Check mode: get type from SecurityManager
            mPasswordType = SecurityManager.getInstance(this).getPasswordType();
        }

        initViews();
        setupViews();
    }

    private void initViews() {
        binding.btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void setupViews() {
        if (mMode == 1) { // Setup
            binding.tvPrompt.setText("请设置密码");
        } else { // Check
            binding.tvPrompt.setText("请输入密码");
        }

        if (mPasswordType == SecurityManager.TYPE_PIN) {
            binding.etPin.setVisibility(View.VISIBLE);
            binding.lockPatternView.setVisibility(View.GONE);
            binding.etPin.requestFocus(); // Auto focus
            setupPinLogic();
        } else if (mPasswordType == SecurityManager.TYPE_PATTERN) {
            binding.etPin.setVisibility(View.GONE);
            binding.lockPatternView.setVisibility(View.VISIBLE);
            setupPatternLogic();
        } else {
            // Should not happen
            finish();
        }
    }

    private void setupPinLogic() {
        binding.etPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                handleInput(binding.etPin.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void setupPatternLogic() {
        binding.lockPatternView.setOnPatternListener(new LockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                binding.tvError.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onPatternCleared() {}

            @Override
            public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {}

            @Override
            public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                if (pattern.size() < 3) {
                    binding.tvError.setText("连接至少3个点");
                    binding.tvError.setVisibility(View.VISIBLE);
                    binding.lockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                    return;
                }
                handleInput(LockPatternView.patternToString(pattern));
            }
        });
    }

    private void handleInput(String input) {
        if (TextUtils.isEmpty(input)) return;
        binding.tvError.setVisibility(View.INVISIBLE);

        if (mMode == 0) { // Check
            if (SecurityManager.getInstance(this).checkPassword(input)) {
                setResult(RESULT_OK);
                finish();
            } else {
                binding.tvError.setText("密码错误");
                binding.tvError.setVisibility(View.VISIBLE);
                if (mPasswordType == SecurityManager.TYPE_PATTERN) {
                    binding.lockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                } else {
                    binding.etPin.setText("");
                }
            }
        } else { // Setup
            if (mFirstInput == null) {
                // First entry
                mFirstInput = input;
                binding.tvPrompt.setText("请再次输入以确认");
                if (mPasswordType == SecurityManager.TYPE_PATTERN) {
                    binding.lockPatternView.clearPattern();
                } else {
                    binding.etPin.setText("");
                }
            } else {
                // Second entry
                if (mFirstInput.equals(input)) {
                    SecurityManager.getInstance(this).setPassword(input, mPasswordType);
                    Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    binding.tvError.setText("两次输入不一致，请重试");
                    binding.tvError.setVisibility(View.VISIBLE);
                    // Reset to start
                    mFirstInput = null;
                    binding.tvPrompt.setText("请设置密码");
                    if (mPasswordType == SecurityManager.TYPE_PATTERN) {
                        binding.lockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                        binding.lockPatternView.postDelayed(() -> binding.lockPatternView.clearPattern(), 1000);
                    } else {
                        binding.etPin.setText("");
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
