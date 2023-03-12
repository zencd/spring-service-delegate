package com.demo.lib;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Primary bean factory for an interface annotated with {@link DelegatedService}.
 * That bean will delegate work to a specific service, basing on the current region.
 */
@Component(DelegatedServiceBeanFactory.BEAN_NAME)
public class DelegatedServiceBeanFactory implements InvocationHandler {

    public static final String BEAN_NAME = "com.demo.lib.DelegatedServiceBeanFactory";
    public static final String FACTORY_METHOD_NAME = "createBean";

    public static final String currentRegion = "RU"; // FIXME resolve in runtime

    @Autowired
    private ApplicationContext applicationContext;

    @SuppressWarnings("unused")
    public Object createBean(Class<?> clazz) {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {clazz}, this);
    }

    @Override
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        Class<?> declaringInterface = method.getDeclaringClass();
        var beanByName = applicationContext.getBeansOfType(declaringInterface);
        var found = beanByName.values().stream()
                .filter(this::matchesCurrentRegion)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No service found 1) derived from " + declaringInterface.getName()
                        + " 2) having annotation " + IfRegion.class.getSimpleName()
                        + " 3) having region " + currentRegion));
        return method.invoke(found, args);
    }

    private boolean matchesCurrentRegion(Object service) {
        IfRegion anno = service.getClass().getAnnotation(IfRegion.class);
        if (anno == null) {
            return false;
        }
        return Arrays.stream(anno.value()).anyMatch(currentRegion::equalsIgnoreCase);
    }
}
