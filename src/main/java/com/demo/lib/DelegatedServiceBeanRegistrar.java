package com.demo.lib;

import lombok.SneakyThrows;
import org.reflections.Reflections;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;
import java.util.Set;

/**
 * Registers the primary bean factory for each interface annotated with {@link DelegatedService}.
 */
public class DelegatedServiceBeanRegistrar implements ImportBeanDefinitionRegistrar {

    @SneakyThrows
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Set<Class<?>> whoAnnotated = new Reflections(LibConfig.BASE_PACKAGE).getTypesAnnotatedWith(DelegatedService.class);
        List<Class<?>> interfaces = whoAnnotated.stream().filter(Class::isInterface).toList();
        for (var interfaceClass : interfaces) {
            String beanName = interfaceClass.getName() + ".PrimaryBean";

            var methodArgs = new ConstructorArgumentValues();
            methodArgs.addIndexedArgumentValue(0, interfaceClass);

            var beanDef = new GenericBeanDefinition();
            beanDef.setBeanClass(interfaceClass);
            beanDef.setFactoryBeanName(DelegatedServiceBeanFactory.BEAN_NAME);
            beanDef.setFactoryMethodName(DelegatedServiceBeanFactory.FACTORY_METHOD_NAME);
            beanDef.setConstructorArgumentValues(methodArgs);
            beanDef.setPrimary(true); // otherwise error "expected single matching bean but found 3"

            registry.registerBeanDefinition(beanName, beanDef);
        }
    }
}
