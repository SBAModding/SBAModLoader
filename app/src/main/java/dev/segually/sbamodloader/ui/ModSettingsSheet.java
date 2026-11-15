package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.tools.ModSettings;
import dev.segually.sbamodloader.tools.ModSettingsStore;

/**
 * The screen that is made from a mod's declared settings: a switch for a bool, a slider for a number, buttons or a menu
 * for a choice, a text field for text. Every change is saved at once; the game picks them up the next time it starts.
 */
public final class ModSettingsSheet {
    private final Context context;
    private final ModSettings settings;
    private final LinearLayout column;
    private Map<String, String> values;

    private ModSettingsSheet(Context context, ModSettings settings) {
        this.context = context;
        this.settings = settings;
        this.column = new LinearLayout(context);
        this.values = ModSettingsStore.load(settings);
    }

    public static void show(Context context, String modName, ModSettings settings) {
        ModSettingsSheet sheet = new ModSettingsSheet(context, settings);
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(sheet.build(modName, dialog));
        dialog.show();
        // A tall list opens fully instead of as a short peek.
        View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottom != null) com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottom)
                .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }

    private View build(String modName, BottomSheetDialog dialog) {
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        column.setPadding(pad, pad, pad, pad);

        TextView title = text(R.style.TextAppearance_SBA_HeadlineSmallEmphasized, modName, 0);
        column.addView(title);
        TextView note = text(R.style.TextAppearance_SBA_BodyMedium, context.getString(R.string.mod_settings_note), 0);
        column.addView(note, margin(0, 4, 0, 8));

        for (ModSettings.Field f : settings.fields) column.addView(row(f), margin(0, 12, 0, 0));

        MaterialButton reset = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        reset.setText(R.string.mod_settings_reset);
        reset.setOnClickListener(v -> {
            try {
                ModSettingsStore.reset(settings);
            } catch (IOException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
            show(context, modName, settings);
        });
        column.addView(reset, margin(0, 16, 0, 0));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);
        return scroll;
    }

    private String current(ModSettings.Field f) {
        String v = values.get(f.key);
        return v != null ? v : f.defaultValue;
    }

    private void save(ModSettings.Field f, String value) {
        try {
            String saved = ModSettingsStore.set(settings, f.key, value);
            values.put(f.key, saved);
        } catch (IOException e) {
            Toast.makeText(context, e.getMessage() != null ? e.getMessage() : e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private View row(ModSettings.Field f) {
        switch (f.type) {
            case BOOL:
                return boolRow(f);
            case INT:
            case FLOAT:
                return numberRow(f);
            case CHOICE:
                return choiceRow(f);
            default:
                return stringRow(f);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        c.setPadding(p, p, p, p);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        c.setBackground(bg);
        return c;
    }

    private View titles(ModSettings.Field f) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(R.style.TextAppearance_SBA_RowTitle, f.label, 0));
        if (!f.summary.isEmpty()) col.addView(text(R.style.TextAppearance_SBA_RowBody, f.summary, 0));
        return col;
    }

    private View boolRow(ModSettings.Field f) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(titles(f), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked("true".equals(current(f)));
        sw.setOnCheckedChangeListener((b, on) -> save(f, on ? "true" : "false"));
        c.addView(sw);
        c.setOnClickListener(v -> sw.toggle());
        return c;
    }

    private View numberRow(ModSettings.Field f) {
        LinearLayout c = card();
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titles(f), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueText = text(R.style.TextAppearance_SBA_TitleMediumEmphasized, "", 0);
        header.addView(valueText);
        c.addView(header);

        final boolean isInt = f.type == ModSettings.Type.INT;
        // A slider needs a whole number of steps between its ends.
        int steps = (int) Math.max(1, Math.min(1000, Math.round((f.max - f.min) / f.step)));
        final float stepSize = (float) ((f.max - f.min) / steps);
        Slider slider = new Slider(context);
        slider.setValueFrom((float) f.min);
        slider.setValueTo((float) f.max);
        slider.setStepSize(stepSize);
        double start = parse(current(f), f.min);
        slider.setValue(snap(start, f.min, stepSize, steps));
        valueText.setText(format(slider.getValue(), isInt));
        slider.addOnChangeListener((s, value, fromUser) -> valueText.setText(format(value, isInt)));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {
            }

            @Override
            public void onStopTrackingTouch(Slider s) {
                save(f, isInt ? Long.toString(Math.round(s.getValue())) : Double.toString(s.getValue()));
            }
        });
        c.addView(slider);
        return c;
    }

    private static float snap(double value, double min, float stepSize, int steps) {
        long n = Math.round((value - min) / stepSize);
        n = Math.max(0, Math.min(steps, n));
        return (float) (min + n * stepSize);
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String format(float v, boolean isInt) {
        return isInt ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private View choiceRow(ModSettings.Field f) {
        LinearLayout c = card();
        c.addView(titles(f));
        if (f.choices.size() <= 4) {
            MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            int selected = View.NO_ID;
            for (ModSettings.Choice choice : f.choices) {
                MaterialButton b = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                b.setText(choice.label);
                b.setId(View.generateViewId());
                b.setCheckable(true);
                b.setTag(choice.value);
                b.setBackgroundTintList(context.getColorStateList(R.color.toggle_bg));
                b.setTextColor(context.getColorStateList(R.color.toggle_text));
                group.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1f));
                if (choice.value.equals(current(f))) selected = b.getId();
            }
            if (selected != View.NO_ID) group.check(selected);
            group.addOnButtonCheckedListener((g, id, checked) -> {
                if (checked) save(f, String.valueOf(g.findViewById(id).getTag()));
            });
            c.addView(group, margin(0, 12, 0, 0));
        } else {
            MaterialButton pick = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            pick.setText(labelOf(f, current(f)) + " ▾");
            pick.setOnClickListener(v -> {
                androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(context, v);
                for (int i = 0; i < f.choices.size(); i++) menu.getMenu().add(0, i, i, f.choices.get(i).label);
                menu.setOnMenuItemClickListener(item -> {
                    ModSettings.Choice choice = f.choices.get(item.getItemId());
                    save(f, choice.value);
                    pick.setText(choice.label + " ▾");
                    return true;
                });
                menu.show();
            });
            c.addView(pick, margin(0, 12, 0, 0));
        }
        return c;
    }

    private static String labelOf(ModSettings.Field f, String value) {
        for (ModSettings.Choice c : f.choices) if (c.value.equals(value)) return c.label;
        return value;
    }

    private View stringRow(ModSettings.Field f) {
        LinearLayout c = card();
        c.addView(titles(f));
        TextInputLayout layout = new TextInputLayout(context);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setText(current(f));
        layout.addView(input);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                save(f, s.toString());
            }
        });
        c.addView(layout, margin(0, 8, 0, 0));
        return c;
    }

    private TextView text(int style, String value, int unused) {
        TextView t = new TextView(context);
        t.setTextAppearance(style);
        t.setText(value);
        t.setTextColor(MaterialColors.getColor(t, style == R.style.TextAppearance_SBA_RowBody || style == R.style.TextAppearance_SBA_BodyMedium
                ? com.google.android.material.R.attr.colorOnSurfaceVariant : com.google.android.material.R.attr.colorOnSurface));
        return t;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
