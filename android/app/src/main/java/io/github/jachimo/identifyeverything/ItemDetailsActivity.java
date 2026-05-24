package io.github.jachimo.identifyeverything;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.jachimo.identifyeverything.data.Item;
import io.github.jachimo.identifyeverything.data.ItemRepository;

/**
 * Displays and allows editing of item details.
 * <p>
 * Loads item data (title, description, location) from the repository
 * and provides a save action via the FAB.
 */
public class ItemDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ItemDetailsActivity";

    private TextView urlText;
    private TextView versionText;
    private TextInputLayout titleContainer;
    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private TextView locationText;
    private FloatingActionButton saveButton;
    private ProgressBar progressBar;

    private ItemRepository repository;
    private String guid;
    private String itemUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        guid = getIntent().getStringExtra("GUID");
        itemUrl = "https://mylabels.example.com/objects/v1/" + guid;

        urlText = findViewById(R.id.urlText);
        versionText = findViewById(R.id.versionText);
        titleContainer = findViewById(R.id.titleContainer);
        titleInput = findViewById(R.id.titleInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        locationText = findViewById(R.id.locationText);
        saveButton = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);

        repository = new ViewModelProvider(this).get(ItemRepository.class);
        repository.initialize();

        loadItem();
    }

    private void loadItem() {
        urlText.setText(itemUrl);
        showProgress(true);

        repository.getItem(guid).observe(this, item -> {
            showProgress(false);
            if (item != null) {
                populateFromItem(item);
            } else {
                // Item not in local DB, present empty form to create
                versionText.setText("New Item - will be created on save");
            }
        });
    }

    private void populateFromItem(Item item) {
        if (item.getTitle() != null && !item.getTitle().isEmpty()) {
            titleInput.setText(item.getTitle());
        }
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            descriptionInput.setText(item.getDescription());
        }
        if (item.getUrl() != null) {
            urlText.setText(item.getUrl());
        }

        // Try to extract title/description from version data
        // This is a future enhancement when structured data is available
        versionText.setText("Latest Version: v1");
    }

    public void onSaveClick(View view) {
        saveChanges();
    }

    private void saveChanges() {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String description = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";

        if (title.isEmpty()) {
            titleContainer.setError("Title is required");
            return;
        }
        titleContainer.setError(null);

        showProgress(true);

        // Build item data JSON
        JsonObject data = new JsonObject();
        data.addProperty("title", title);
        data.addProperty("description", description);

        // Check if item exists locally or create new
        repository.getItem(guid).observe(this, item -> {
            if (item == null) {
                // Create new item
                String domain = "mylabels.example.com";
                repository.createItem(guid, itemUrl, domain, result -> {
                    if (result instanceof Item) {
                        saveItemData((Item) result, data.toString());
                    } else {
                        showProgress(false);
                        Toast.makeText(this, "Error creating item", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                saveItemData(item, data.toString());
            }
        });
    }

    private void saveItemData(Item item, String dataJson) {
        repository.saveItem(item, dataJson, "Updated via mobile app", result -> {
            runOnUiThread(() -> {
                showProgress(false);
                if (result instanceof Item) {
                    Toast.makeText(ItemDetailsActivity.this,
                            "Saved successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ItemDetailsActivity.this,
                            String.valueOf(result), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (saveButton != null) {
            saveButton.setEnabled(!show);
        }
    }
}