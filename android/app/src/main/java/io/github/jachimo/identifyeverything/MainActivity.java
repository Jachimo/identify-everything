package io.github.jachimo.identifyeverything;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jachimo.identifyeverything.util.GuidGenerator;

/**
 * Main entry point for the Identify Everything app.
 * <p>
 * Allows the user to enter or scan a GUID and navigate to
 * the item details view.
 */
public class MainActivity extends AppCompatActivity {

    private EditText guidInput;
    private Button showDetailsButton;
    private Button scanQrButton;
    private TextView validationIndicator;

    private static final int REQUEST_CODE_SCAN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        guidInput = findViewById(R.id.guidInput);
        showDetailsButton = findViewById(R.id.showDetailsButton);
        scanQrButton = findViewById(R.id.scanQrButton);
        validationIndicator = findViewById(R.id.validationIndicator);

        showDetailsButton.setEnabled(false);

        guidInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateInput(s.toString());
            }
        });

        showDetailsButton.setOnClickListener(v -> openItemDetails());

        scanQrButton.setOnClickListener(v -> scanQrCode());
    }

    private void validateInput(String input) {
        if (input.isEmpty()) {
            validationIndicator.setVisibility(View.GONE);
            showDetailsButton.setEnabled(false);
        } else if (GuidGenerator.isValid(input)) {
            validationIndicator.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            validationIndicator.setText("Valid GUID");
            validationIndicator.setVisibility(View.VISIBLE);
            showDetailsButton.setEnabled(true);
        } else {
            validationIndicator.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            validationIndicator.setText("Invalid GUID format");
            validationIndicator.setVisibility(View.VISIBLE);
            showDetailsButton.setEnabled(false);
        }
    }

    private void openItemDetails() {
        String rawInput = guidInput.getText().toString().trim();
        String normalized = GuidGenerator.normalize(rawInput);
        if (normalized == null || !GuidGenerator.isValid(normalized)) {
            Toast.makeText(this, "Invalid GUID", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ItemDetailsActivity.class);
        intent.putExtra("GUID", normalized);
        startActivity(intent);
    }

    private void scanQrCode() {
        // ZXing QR scanner integration
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        try {
            startActivityForResult(intent, REQUEST_CODE_SCAN);
        } catch (Exception e) {
            Toast.makeText(this, "QR Scanner not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK) {
            String contents = data.getStringExtra("SCAN_RESULT");
            if (contents != null) {
                guidInput.setText(contents);
            }
        }
    }
}