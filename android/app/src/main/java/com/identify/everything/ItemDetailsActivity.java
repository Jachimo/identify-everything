package com.identify.everything;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

public class ItemDetailsActivity extends AppCompatActivity {

    private String guid;
    private TextView guidDisplay;
    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText locationText;
    private FloatingActionButton saveFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        guid = getIntent().getStringExtra("guid");

        guidDisplay = findViewById(R.id.guidDisplay);
        titleInput = findViewById(R.id.titleInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        locationText = findViewById(R.id.locationText);
        saveFab = findViewById(R.id.saveFab);

        if (guid != null) {
            guidDisplay.setText(guid);
        }

        saveFab.setOnClickListener(v -> saveChanges());
    }

    private void saveChanges() {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String description = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";

        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Saved (offline)", Toast.LENGTH_SHORT).show();
    }
}
