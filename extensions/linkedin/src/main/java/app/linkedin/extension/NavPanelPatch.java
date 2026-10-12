package app.linkedin.extension;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adds a "Gunshootr Patches" entry to the native "Me" panel, right below "Saved posts".
 * It reuses the panel's own section view data, and its click is redirected to SettingsActivity.
 */
@SuppressWarnings("unused")
public final class NavPanelPatch {
    private static final String PACKAGE = "com.linkedin.android.home.navpanel.";
    static final String TITLE = "Gunshootr Patches";

    /** Injected before every return of HomeNavPanelTransformer.transform(HomeNavPanelAggregateResponse). */
    public static AbstractList<Object> addSettingsItem(Object panel) {
        @SuppressWarnings("unchecked")
        AbstractList<Object> list = (AbstractList<Object>) panel;
        if (list == null) return null;
        try {
            Class<?> viewData = Class.forName(PACKAGE + "HomeNavPanelSectionV2ViewData");
            Class<?> sectionType = Class.forName(PACKAGE + "SectionType");
            Class<?> chevronType = Class.forName(PACKAGE + "HomeNavPanelSectionChevronType");
            Class<?> widget = Class.forName(PACKAGE + "HomeNavPanelAnalyticsWidgetViewData");
            Object savedPosts = sectionType.getField("SAVED_POSTS").get(null);
            Object chevronRight = chevronType.getField("RIGHT").get(null);

            for (Object item : list) {
                if (viewData.isInstance(item) && TITLE.equals(String.valueOf(Reflect.get(item, "title")))) {
                    return list;
                }
            }

            Object entry = createSectionEntry(viewData, savedPosts, chevronRight, sectionType, chevronType, widget);

            ArrayList<Object> result = new ArrayList<>(list);
            int index = result.size();
            for (int i = 0; i < result.size(); i++) {
                Object item = result.get(i);
                if (viewData.isInstance(item) && Reflect.get(item, "sectionType") == savedPosts) {
                    index = i + 1;
                    break;
                }
            }
            result.add(index, entry);
            return result;
        } catch (Throwable t) {
            Diagnostics.error("addSettingsItem failed", t);
            return list;
        }
    }

    private static Object createSectionEntry(
            Class<?> viewData, Object savedPosts, Object chevronRight,
            Class<?> sectionType, Class<?> chevronType, Class<?> widget) throws ReflectiveOperationException {
        // Try direct constructors with String or CharSequence first
        for (Class<?> titleType : new Class<?>[] { String.class, CharSequence.class }) {
            try {
                Constructor<?> c = viewData.getConstructor(titleType, sectionType, chevronType, List.class, widget);
                return c.newInstance(TITLE, savedPosts, chevronRight, Collections.emptyList(), null);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Constructor<?> c = viewData.getConstructor(titleType, sectionType, chevronType, List.class, int.class);
                return c.newInstance(TITLE, savedPosts, chevronRight, Collections.emptyList(), 24);
            } catch (NoSuchMethodException ignored) {
            }
        }

        // Generic constructor discovery across LinkedIn versions
        for (Constructor<?> c : viewData.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 4 && CharSequence.class.isAssignableFrom(p[0])
                    && (p[1] == sectionType || p[1].isAssignableFrom(sectionType))
                    && (p[2] == chevronType || p[2].isAssignableFrom(chevronType))
                    && List.class.isAssignableFrom(p[3])) {
                Object[] args = new Object[p.length];
                args[0] = TITLE;
                args[1] = savedPosts;
                args[2] = chevronRight;
                args[3] = Collections.emptyList();
                for (int i = 4; i < p.length; i++) {
                    if (p[i] == int.class) {
                        args[i] = 24;
                    } else if (p[i] == boolean.class) {
                        args[i] = false;
                    } else {
                        args[i] = null;
                    }
                }
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        throw new NoSuchMethodException("No matching HomeNavPanelSectionV2ViewData constructor found");
    }

    /** Injected before every return of HomeNavPanelSectionV2Presenter.onBind(ViewDataBinding). */
    public static void onSectionBind(Object presenter) {
        try {
            Object viewData = Reflect.get(presenter, "viewData");
            if (!TITLE.equals(String.valueOf(Reflect.get(viewData, "title")))) return;
            View.OnClickListener open = v -> SettingsActivity.open(activityOf(v.getContext()));
            Field field = Reflect.field(presenter.getClass(), "sectionHeaderClickListener");
            if (field != null) field.set(presenter, open);
        } catch (Throwable t) {
            Diagnostics.error("onSectionBind failed", t);
        }
    }

    private static Context activityOf(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return context;
    }
}
