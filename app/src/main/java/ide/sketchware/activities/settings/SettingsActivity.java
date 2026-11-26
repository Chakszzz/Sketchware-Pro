package ide.sketchware.activities.settings;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import ide.sketchware.databinding.ActivitySettingsBinding;
import ide.sketchware.fragments.settings.appearance.SettingsAppearanceFragment;

public class SettingsActivity extends BaseAppCompatActivity {

    public static final String FRAGMENT_TAG_EXTRA = "fragment_tag";
    public static final String SETTINGS_APPEARANCE_FRAGMENT = "settings_appearance";
    private ActivitySettingsBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String fragmentTag = getIntent().getStringExtra(FRAGMENT_TAG_EXTRA);
        Fragment fragment = switch (fragmentTag != null ? fragmentTag : "") {
            case SETTINGS_APPEARANCE_FRAGMENT -> new SettingsAppearanceFragment();
            default -> throw new IllegalArgumentException("Unknown fragment tag: " + fragmentTag);
        };

        openFragment(fragment);
    }

    private void openFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(binding.settingsFragmentContainer.getId(), fragment)
                .commit();
    }
}