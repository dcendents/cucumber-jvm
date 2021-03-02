package io.cucumber.cdi2;

import io.cucumber.core.backend.ObjectFactory;
import org.apiguardian.api.API;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Unmanaged;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@API(status = API.Status.STABLE)
public final class Cdi2Factory implements ObjectFactory {

    static final String BEANS_XML_FILE = "META-INF/beans.xml";

    private final Map<Class<?>, Unmanaged.UnmanagedInstance<?>> standaloneInstances = new HashMap<>();
    private final Set<Class<?>> classes = new HashSet<>();
    private Set<File> managedBeanLocations;
    private SeContainer container;

    @Override
    public void start() {
        if (container == null) {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance();
            initializer.addBeanClasses(classes.toArray(new Class[classes.size()]));
            container = initializer.initialize();
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.close();
            container = null;
        }
        for (Unmanaged.UnmanagedInstance<?> unmanaged : standaloneInstances.values()) {
            unmanaged.preDestroy();
            unmanaged.dispose();
        }
        standaloneInstances.clear();
    }

    @Override
    public boolean addClass(final Class<?> clazz) {
        File classLocation = new File(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
        if (!getManagedBeanLocations().contains(classLocation)) {
            classes.add(clazz);
        }
        return true;
    }

    private Set<File> getManagedBeanLocations() {
        if (managedBeanLocations == null) {
            managedBeanLocations = new HashSet<>();
            try {
                for (Enumeration<URL> beans = Thread.currentThread().getContextClassLoader().getResources(BEANS_XML_FILE); beans
                        .hasMoreElements();) {
                    URL url = beans.nextElement();
                    URLConnection connection = url.openConnection();
                    File location = connection instanceof JarURLConnection
                            ? location = new File(((JarURLConnection) connection).getJarFileURL().getPath())
                            : new File(url.getPath()).getParentFile().getParentFile();
                    managedBeanLocations.add(location);
                }
            } catch (IOException e) {
            }
        }
        return managedBeanLocations;
    }

    @Override
    public <T> T getInstance(final Class<T> type) {
        Unmanaged.UnmanagedInstance<?> instance = standaloneInstances.get(type);
        if (instance != null) {
            return type.cast(instance.get());
        }
        Instance<T> selected = container.select(type);
        if (selected.isUnsatisfied()) {
            BeanManager beanManager = container.getBeanManager();
            Unmanaged<T> unmanaged = new Unmanaged<>(beanManager, type);
            Unmanaged.UnmanagedInstance<T> value = unmanaged.newInstance();
            value.produce();
            value.inject();
            value.postConstruct();
            standaloneInstances.put(type, value);
            return value.get();
        }
        return selected.get();
    }

}
