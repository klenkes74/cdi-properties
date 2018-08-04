package org.vaadin.addon.cdiproperties;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.HtmlComponent;
import com.vaadin.flow.component.Text;


@SuppressWarnings("serial")
@SessionScoped
public class ComponentConfigurator implements Serializable {

    public final static String IGNORED_STRING = "CDI_PROPERTIES_IGNORE";

    @Inject
    private Instance<CustomProperty> customProperties;

    
    private static Annotation getPropertyAnnotation(InjectionPoint ip,
            Class annotationClass) {
        Annotation result = null;
        for (final Annotation annotation : ip.getQualifiers()) {
            if (annotationClass.isAssignableFrom(annotation.getClass())) {
                result = annotation;
                break;
            }
        }
        return result;
    }

    private static Object getPropertyValue(Object instance, String methodName) {
        Object result = null;
        try {
            result = instance.getClass().getMethod(methodName).invoke(instance);
        } catch (IllegalArgumentException | SecurityException | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static void applyProperties(Component component, Annotation propertyAnnotation) {
        try {
            final BeanInfo bi = Introspector.getBeanInfo(component.getClass());
            HashMap<String, Method> methods = new HashMap<>(bi.getPropertyDescriptors().length);
            for (PropertyDescriptor p : bi.getPropertyDescriptors()) {
                methods.put(p.getName(), p.getWriteMethod());
            }

            for (Method method : propertyAnnotation.getClass().getMethods()) {
                try {
                    Object value = method.invoke(propertyAnnotation);
                    if (!IGNORED_STRING.equals(value)) {
                        methods.get(method.getName()).invoke(component, value);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (IntrospectionException e) {
            // Ignore
        }

    }

    public <T extends Component> T getComponent(
            Class<? extends Annotation> annotationClass, InjectionPoint ip)
            throws InstantiationException, IllegalAccessException {
        Annotation propertyAnnotation = getPropertyAnnotation(ip,
                annotationClass);
        Class<T> componentClass = (Class<T>) getPropertyValue(propertyAnnotation, "implementation");
        T component = componentClass.newInstance();

        // Apply the setters
        applyProperties(component, propertyAnnotation);

        // Apply custom properties
        Iterator<CustomProperty> iterator = customProperties.iterator();
        while (iterator.hasNext()) {
            CustomProperty customProperty = iterator.next();
            if (customProperty.appliesTo(component)) {
                customProperty.apply(component, propertyAnnotation);
            }
        }

        return component;
    }

    public static abstract class CustomProperty {
        abstract void apply(Component component, Annotation propertyAnnotation);

        abstract boolean appliesTo(Component component);
    }

    private static class CustomPropertySize extends CustomProperty {
        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            Boolean sizeFull = (Boolean) getPropertyValue(propertyAnnotation, "sizeFull");
            Boolean sizeUndefined = (Boolean) getPropertyValue(propertyAnnotation, "sizeUndefined");

            if (component instanceof HasSize) {
                HasSize hc = (HasSize) component;

                if (sizeFull) {
                    hc.setSizeFull();
                } else if (sizeUndefined) {
                    hc.setSizeUndefined();
                } else {
                    String height = (String) getPropertyValue(propertyAnnotation, "height");
                    if (!IGNORED_STRING.equals(height)) {
                        hc.setHeight(height);
                    }

                    String width = (String) getPropertyValue(propertyAnnotation, "width");
                    if (!IGNORED_STRING.equals(width)) {
                        hc.setWidth(width);
                    }
                }
            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyCaptionKey extends CustomProperty {
        @Inject
        private Instance<TextBundle> textBundle;
        @Inject
        private Instance<Localizer> localizer;

        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String captionKey = (String) getPropertyValue(propertyAnnotation, "captionKey");
            final Boolean localized = (Boolean) getPropertyValue(propertyAnnotation, "localized");

            if (!IGNORED_STRING.equals(captionKey)) {
                try {
                    ((HtmlComponent) component).setTitle(textBundle.get().getText(captionKey));
                    if (localized) {
                        localizer.get().addLocalizedCaption((HtmlComponent)component, captionKey);

                    }
                } catch (final UnsatisfiedResolutionException e) {
                    ((HtmlComponent) component).setTitle("No TextBundle implementation found!");
                }

            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyStyleName extends CustomProperty {
        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String[] styleNames = (String[]) getPropertyValue(propertyAnnotation, "styleName");
            for (String styleName : styleNames) {
                ((HasStyle) component).setClassName(styleName);
                ;
            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyLabelValueKey extends CustomProperty {
        @Inject
        private Instance<TextBundle> textBundle;
        @Inject
        private Instance<Localizer> localizer;

        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String valueKey = (String) getPropertyValue(propertyAnnotation, "valueKey");
            if (!IGNORED_STRING.equals(valueKey)) {
                try {
                    ((Text) component).setText(textBundle.get().getText(valueKey));
                    final Boolean localized = (Boolean) getPropertyValue(propertyAnnotation, "localized");
                    if (localized) {
                        localizer.get().addLocalizedCaption((HtmlComponent) component, valueKey);
                    }
                } catch (final UnsatisfiedResolutionException e) {
                    ((Text) component).setText("No TextBundle implementation found!");
                }

            }
        }

        @Override
        boolean appliesTo(Component component) {
            return component instanceof Text;
        }
    }
}
