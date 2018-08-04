package org.vaadin.addon.cdiproperties;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HtmlComponent;
import org.vaadin.addon.cdiproperties.Generator.ComponentModel.ComponentProperty;

class Sets {
    static <T> Set<T> newHashSet(T ... classes) {
        HashSet<T> result = new HashSet<T>(classes.length);
        Collections.addAll(result, classes);

        return result;
    }
}

class Lists {
    static <T> List<T> newArrayList(Collection<T> elements) {
        ArrayList<T> result = new ArrayList<T>(elements.size());
        result.addAll(elements);
        return result;
    }
}

class Generator {

    private static Set<Class<? extends HtmlComponent>> excludedClasses = Sets.newHashSet();
    private static Set<String> excludedProperties = Sets.newHashSet("UI",
            "componentError", "connectorEnabled", "connectorId", "width",
            "height", "stateType", "type", "styleName", "timeFormat");
    private static Set primitiveWrapperClasses = Sets.newHashSet(Boolean.class,
            Byte.class, Character.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class);



    public static void main(String[] args) throws IntrospectionException {

        Set<ComponentModel> componentModels = Sets.newHashSet();

        for (PojoClass pojoClass : PojoClassFactory
                .enumerateClassesByExtendingType("com.vaadin.flow",
                        Component.class, null)) {
            if ((pojoClass.isConcrete())
                    && !excludedClasses.contains(pojoClass.getClazz())) {
                Object implementation = getPojoInstance(pojoClass);

                if (implementation != null) {
                    ComponentModel componentModel = new ComponentModel(pojoClass.getClazz());

                    // Add bean properties
                    BeanInfo bi = Introspector.getBeanInfo(implementation.getClass());
                    PropertyDescriptor[] propertyDescriptor = bi.getPropertyDescriptors();

                    for (PropertyDescriptor pid : bi.getPropertyDescriptors()) {
                        boolean setterFound = pid.getWriteMethod() != null;

                        if (setterFound && !excludedProperties.contains(pid.getName())) {
                            Class type = pid.getPropertyType();

                            if (primitiveWrapperClasses.contains(type)
                                    || type.isEnum() || type == String.class
                                    || type == Class.class) {


                                String defaultValue = formatDefaultValue(getDefaultValue(pid));
                                if (type == String.class) {
                                    defaultValue = "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING";
                                }

                                ComponentProperty cp = new ComponentProperty(
                                        formatType(type),
                                        pid.getName(),
                                        defaultValue);
                                componentModel.getProperties().add(cp);
                            }
                        }
                    }

                    // Add custom properties
                    componentModel.getProperties().addAll(
                            getCustomProperties(pojoClass, implementation));

                    try {
                        writeFile(
                                args[0]
                                        + "/"
                                        + componentModel.formatAnnotationClassName()
                                        + ".java",
                                componentModel.toAnnotation());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    componentModels.add(componentModel);
                }
            }
        }

        try {
            writeFile(args[1] + "/ComponentProducers.java",
                    toProducer(componentModels));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static Object getDefaultValue(PropertyDescriptor pid) {
        Object result = pid.getReadMethod().getDefaultValue();

        if (result == null) {
            if (pid.getPropertyType().isEnum()) {
                result = pid.getPropertyType().getEnumConstants()[0];
            } else if (Boolean.class.isAssignableFrom(pid.getPropertyType())) {
                result = false;
            } else if (Double.class.isAssignableFrom(pid.getPropertyType())) {
                result = 0.0d;
            }
        }

        return result;
    }

    private static Collection<? extends ComponentProperty> getCustomProperties(
            PojoClass pojoClass, Object implementation) {
        Collection<ComponentProperty> result = Sets.newHashSet();

        Component component = (Component) implementation;

        if (component instanceof HtmlComponent) {
            HtmlComponent c = (HtmlComponent) component;
            result.add(new ComponentProperty("float", "widthValue", formatDefaultValue(c.getWidth())));
            result.add(new ComponentProperty("float", "heightValue", formatDefaultValue(c.getHeight())));
        }

        result.add(new ComponentProperty("String", "width",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));
        result.add(new ComponentProperty("String", "height",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));

        result.add(new ComponentProperty("Class", "implementation",
                formatDefaultValue(implementation.getClass())));

        result.add(new ComponentProperty("String", "captionKey",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));

        result.add(new ComponentProperty("boolean", "sizeFull", "false"));
        result.add(new ComponentProperty("boolean", "sizeUndefined", "false"));

        result.add(new ComponentProperty("boolean", "localized", "true"));

        result.add(new ComponentProperty("String[]", "styleName", "{}"));

        if (implementation instanceof com.vaadin.flow.component.Text) {
            result.add(new ComponentProperty("String", "text",
                    "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));
        }

        return result;
    }

    static String formatType(Class type) {
        String result = type.getCanonicalName();
        if (type == Boolean.class) {
            result = "boolean";
        } else if (type == Integer.class) {
            result = "int";
        } else if (type == Float.class) {
            result = "float";
        } else if (type == Double.class) {
            result = "double";
        }

        return result;
    }

    static String formatDefaultValue(Object defaultValue) {
        String result = String.valueOf(defaultValue);
        if (defaultValue instanceof String) {
            result = "\"" + defaultValue + "\"";
        } else if (defaultValue instanceof Float) {
            result = result.concat("f");
        } else if (defaultValue != null && defaultValue.getClass().isEnum()) {
            Enum e = (Enum) defaultValue;
            result = e.getClass().getCanonicalName() + "." + e.name();
        } else if (defaultValue instanceof Class) {
            result = ((Class) defaultValue).getCanonicalName() + ".class";
        }
        return result;
    }

    private static String toProducer(Set<ComponentModel> componentModels) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.vaadin.addon.cdiproperties.producer;\n");
        sb.append("import javax.enterprise.inject.*;\n");
        sb.append("import javax.inject.*;\n");
        sb.append("import org.vaadin.addon.cdiproperties.ComponentConfigurator;\n");
        sb.append("import javax.enterprise.inject.spi.*;\n");
        sb.append("import javax.enterprise.context.SessionScoped;\n");
        sb.append("import org.vaadin.addon.cdiproperties.annotation.*;\n");
        sb.append("\n\n@SessionScoped\n");
        // sb.append("@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD })\n");
        // sb.append("@Retention(RetentionPolicy.RUNTIME)\n");
        sb.append("public class ComponentProducers implements java.io.Serializable {\n\n");
        sb.append("@Inject\n");
        sb.append("private ComponentConfigurator cc;\n\n");

        List<ComponentModel> ordered = Lists.newArrayList(componentModels);
        Collections.sort(ordered, new Comparator<ComponentModel>() {
            @Override
            public int compare(ComponentModel o1, ComponentModel o2) {
                return o1.getComponentClass().getSimpleName()
                        .compareTo(o2.getComponentClass().getSimpleName());
            }
        });
        for (ComponentModel componentModel : ordered) {
            sb.append(componentModel.toProducerMethod());
        }

        sb.append("\n\n}");
        return sb.toString();
    }

    private static Object getPojoInstance(PojoClass pojoClass) {
        Object instance = null;
        try {
            instance = pojoClass.getClazz().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
        }
        return instance;
    }

    public static void writeFile(String filename, String output)
            throws IOException {
        File file = new File(filename);
        FileWriter writer = new FileWriter(file);
        writer.write(output);
        writer.close();
    }

    static class ComponentModel {
        private final Class componentClass;
        private final Set<ComponentProperty> properties = Sets.newHashSet();

        public ComponentModel(Class componentClass) {
            super();
            this.componentClass = componentClass;
        }

        public Set<ComponentProperty> getProperties() {
            return properties;
        }

        public Class getComponentClass() {
            return componentClass;
        }

        public String toAnnotation() {
            StringBuilder sb = new StringBuilder();
            sb.append("package org.vaadin.addon.cdiproperties.annotation;\n");
            sb.append("import javax.inject.*;\n");
            sb.append("import java.lang.annotation.*;\n");
            sb.append("import javax.enterprise.util.*;\n");
            sb.append("\n\n@Qualifier\n");
            sb.append("@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD })\n");
            sb.append("@Retention(RetentionPolicy.RUNTIME)\n");
            sb.append("public @interface ").append(formatAnnotationClassName())
                    .append(" {");

            List<ComponentProperty> ordered = Lists.newArrayList(properties);
            Collections.sort(ordered, new Comparator<ComponentProperty>() {
                @Override
                public int compare(ComponentProperty o1, ComponentProperty o2) {
                    return o1.name.compareTo(o2.name);
                }

            });
            for (ComponentProperty cp : ordered) {
                sb.append("\n");
                sb.append(cp.toAnnotationMethod());
            }

            sb.append("\n\n}");
            return sb.toString();
        }

        public String formatAnnotationClassName() {
            return (componentClass == HtmlComponent.class ? ""
                    : componentClass.getSimpleName()) + "Properties";
        }

        public String toProducerMethod() {
            StringBuilder sb = new StringBuilder();

            sb.append("@Produces\n");
            sb.append("@").append(formatAnnotationClassName()).append("\n");
            sb.append("public ").append(componentClass.getName())
                    .append(" create").append(componentClass.getSimpleName())
                    .append("With").append(formatAnnotationClassName())
                    .append("(final InjectionPoint ip) throws Exception {\n");
            sb.append("\treturn cc.getComponent(")
                    .append(formatAnnotationClassName())
                    .append(".class, ip);\n");
            sb.append("}\n\n");
            return sb.toString();
        }

        static class ComponentProperty {
            private final String type;
            private final String name;
            private final String defaultValue;

            public ComponentProperty(String type, String name,
                    String defaultValue) {
                super();
                this.type = type;
                this.name = name;
                this.defaultValue = defaultValue;
            }

            String toAnnotationMethod() {
                StringBuilder sb = new StringBuilder();
                sb.append("\n@Nonbinding\n");
                sb.append(type).append(" ").append(name).append("() default ")
                        .append(defaultValue).append(";");
                return sb.toString();
            }

        }
    }

}
