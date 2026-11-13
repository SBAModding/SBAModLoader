package dev.segually.sbamodloader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import androidx.fragment.app.Fragment;

import dev.segually.sbamodloader.tools.CrashDetector;
import dev.segually.sbamodloader.ui.GlassBar;
import dev.segually.sbamodloader.ui.pages.DashboardFragment;
import dev.segually.sbamodloader.ui.pages.ModsFragment;
import dev.segually.sbamodloader.ui.pages.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_PAGE = "page";
    private int page = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.nav_bar);
        GlassBar.apply(nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showPage(id == R.id.nav_mods ? 1 : id == R.id.nav_settings ? 2 : 0);
            return true;
        });

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            var bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            var lp = (android.widget.FrameLayout.LayoutParams) nav.getLayoutParams();
            lp.bottomMargin = bars.bottom + Math.round(16 * getResources().getDisplayMetrics().density);
            nav.setLayoutParams(lp);
            var fade = findViewById(R.id.edge_fade).getLayoutParams();
            fade.height = bars.bottom + Math.round(140 * getResources().getDisplayMetrics().density);
            findViewById(R.id.edge_fade).setLayoutParams(fade);
            return insets;
        });

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt(KEY_PAGE, 0);
        } else {
            showPage(0);
        }

        CrashDetector.init(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PAGE, page);
    }

    private void showPage(int index) {
        page = index;
        Fragment f = switch (index) {
            case 1 -> new ModsFragment();
            case 2 -> new SettingsFragment();
            default -> new DashboardFragment();
        };
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.page_container, f)
                .commit();
    }
}
