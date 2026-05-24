package io.github.jachimo.identifyeverything;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import io.github.jachimo.identifyeverything.data.ItemRepository;
import io.github.jachimo.identifyeverything.util.GuidGenerator;

public class ItemDetailsActivity extends AppCompatActivity {

    private EditText titleInput;
    private TextInputLayout titleContainer;
    private EditText descriptionInput;
    private TextView locationText;
    private TextView metadataText;
    private TextView urlText;
    private TextView versionText;

    private ItemRepository itemRepository;
    private String currentGuid;
    private String itemUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        currentGuid = getIntent().getStringExtra("GUID");
        itemUrl = getIntent().getStringExtra("URL");

        itemRepository = new ViewModelProvider(this).get(ItemRepository.class);

        setupUI();
        loadData();
    }

    private void setupUI() {
        titleInput = findViewById(R.id.titleInput);
        titleContainer = findViewById(R.id.titleContainer);
        descriptionInput = findViewById(R.id.descriptionInput);
        locationText = findViewById(R.id.locationText);
        metadataText = findViewById(R.id.metadataText);
        urlText = findViewById(R.id.urlText);
        versionText = findViewById(R.id.versionText);

        FloatingActionButton saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> saveChanges());
    }

    private void loadData() {
        if (itemUrl != null) {
            urlText.setText(itemUrl);
        }

        // TODO: Load actual item data from backend
        // For now, show placeholder data
        titleContainer.setHint("Item Title");
        descriptionInput.setText("Item description goes here");
        locationText.setText("GPS: -, - (GPS pending)");
        versionText.setText("Versions: 1");
    }

    private void saveChanges() {
        String title = titleInput.getText().toString().trim();

        if (title.isEmpty()) {
            titleContainer.setError("Title is required");
            return;
        }

        titleContainer.setError(null);

        // TODO: Send update to backend
        String note = descriptionInput.getText().toString().trim();
        Toast.makeText(this, "Item updated: " + title, Toast.LENGTH_SHORT).show();

        // Return to previous screen
        finish();
    }
}
