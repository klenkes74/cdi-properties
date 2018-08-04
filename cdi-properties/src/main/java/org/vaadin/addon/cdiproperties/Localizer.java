package org.vaadin.addon.cdiproperties;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.inject.Inject;
import javax.inject.Qualifier;

import com.vaadin.flow.component.HtmlComponent;
import com.wcs.vaadin.flow.cdi.UIScoped;

@SuppressWarnings("serial")
@UIScoped
public class Localizer implements Serializable {

    @Inject
    private Instance<TextBundle> textBundle;

    private final Map<HtmlComponent, String> localizedCaptions = new HashMap<>();

    void updateCaption(@Observes @TextBundleUpdated final Object parameters) {
        for (final Entry<HtmlComponent, String> entry : localizedCaptions
                .entrySet()) {
            try {
                entry.getKey().setTitle(textBundle.get().getText(entry.getValue()));
            } catch (final UnsatisfiedResolutionException e) {
                entry.getKey().setTitle("No TextBundle implementation found!");
            }
        }
    }

    void addLocalizedCaption(final HtmlComponent component, final String captionKey) {
        localizedCaptions.put(component, captionKey);
    }

    @Qualifier
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TextBundleUpdated {
    }
}
