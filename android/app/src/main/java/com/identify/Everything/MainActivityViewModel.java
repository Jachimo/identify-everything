package com.identify.Everything;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.identify.Everything.util.GuidGenerator;

public class MainActivityViewModel extends AndroidViewModel {

    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public MainActivityViewModel(Application application) {
        super(application);
    }

    public LiveData<String> getValidationError() {
        return validationError;
    }

    // Validation now handled by GuidGenerator Util directly in MainActivity
}
