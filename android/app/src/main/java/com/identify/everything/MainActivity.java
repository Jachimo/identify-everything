package com.identify.everything;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText guidInput;
    private Button openButton;
    private Button generateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        guidInput = findViewById(R.id.guidInput);
        openButton = findViewById(R.id.openButton);
        generateButton = findViewById(R.id.generateButton);

        openButton.setOnClickListener(v -> {
            String guid = guidInput.getText().toString().trim();
            if (guid.isEmpty()) {
                Toast.makeText(this, "Please enter a GUID", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!GuidGenerator.isValid(guid)) {
                Toast.makeText(this, "Invalid GUID format", Toast.LENGTH_SHORT).show();
                return;
            }
            openItemDetails(guid);
        });

        generateButton.setOnClickListener(v -> {
            String newGuid = GuidGenerator.generate();
            guidInput.setText(newGuid);
        });
    }

    private void openItemDetails(String guid) {
        Intent intent = new Intent(this, ItemDetailsActivity.class);
        intent.putExtra("guid", guid);
        startActivity(intent);
    }
}
