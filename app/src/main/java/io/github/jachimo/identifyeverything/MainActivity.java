package io.github.jachimo.identifyeverything;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import io.github.jachimo.identifyeverything.data.ItemRepository;
import io.github.jachimo.identifyeverything.util.GuidGenerator;

public class MainActivity extends AppCompatActivity {

    private EditText guidField;
    private ItemRepository itemRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        itemRepository = new ViewModelProvider(this).get(ItemRepository.class);

        guidField = findViewById(R.id.guidInput);
        Button showDetailsButton = findViewById(R.id.showDetailsButton);

        showDetailsButton.setOnClickListener(v -> {
            String guid = guidField.getText().toString().trim();
            if (!GuidGenerator.isValidGuid(guid)) {
                Toast.makeText(this, R.string.invalid_guid_error, Toast.LENGTH_SHORT).show();
                return;
            }

            // Navigate to item details with GUID and URL
            String url = "https://mylabels.example.com/objects/v1/" + guid;
            Intent intent = new Intent(this, ItemDetailsActivity.class);
            intent.putExtra("GUID", guid);
            intent.putExtra("URL", url);
            startActivity(intent);
        });
    }
}
