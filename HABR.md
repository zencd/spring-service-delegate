# Spring Service Delegate

## Общая задача

    @Autowired
    FooService fooService;

Есть некий спринг-сервис и несколько его реализаций (делегатов).
Хочется в динамике распределять вызовы между этими реализациями, в зависимости от рантайм-обстоятельств.
В этом примере решение кого вызывать принимается, сравнивая регион клиента (выводится из http-запроса)
с регионом, прописанном на делегате.

Ссылка не демо-проект в конце.

## Обычное, неинтересное, статичное решение

Вобщем-то можно всё это реализовать и в статике, но есть проблема:
придётся писать больше клиентского кода — 
для каждого делегируемого сервиса нужно явно определять свой `DelegatingFooService`,
чего хотелось бы избежать.

    interface FooService { }

    @Primary
    @Service
    class DelegatingFooService implements FooService {
        Map<String,FooService> delegateByName;
        // делегируем вызовы на FooService1 или FooService2
    }

    @Service
    class FooService1 implements FooService { }

    @Service
    class FooService2 implements FooService { }

## Желаемый дизайн

    @DelegatedService
    interface FooService { }

    @Service
    @IfRegion("RU") // условие, при котором будет вызываться именно он
    class FooServiceRu implements FooService { }

    @Service
    @IfRegion("WORLD") // другое условие
    class FooServiceWorld implements FooService { }

## Новое, динамическое решение

Принцип был подсмотрен в
[FeignClientsRegistrar](https://github.com/spring-cloud/spring-cloud-openfeign/blob/main/spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignClientsRegistrar.java),
который делает примерно то же — динамически реализует интерфейсы (бины) feign-клиентов.

Шаг 1. Любые новые бины можно динамически определять в `ImportBeanDefinitionRegistrar`:

    class DelegatedServiceBeanRegistrar implements ImportBeanDefinitionRegistrar {
        void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            ...
        }
    }

Шаг 2. Там найдём все сервисы (джава интерфейсы), аннотированных как наш `DelegatedService`:

    Set<Class> whoAnnotated = new Reflections("com.demo").getTypesAnnotatedWith(DelegatedService.class);
    List<Class> interfaces = whoAnnotated.stream().filter(Class::isInterface).toList();
    for (var interfaceClass : interfaces) {
        ...
    }

Шаг 3. Для каждого `DelegatedService`
не создаём новый инстанс сервиса, но — лучше — указываем какой factory bean
умеет создавать новый инстанс такого сервиса.
Того, который будет распределять ответственность на конкретных исполнителей (делегатов).

    var beanDef = new GenericBeanDefinition();
    beanDef.setBeanClass(interfaceClass); // FooService.class
    beanDef.setFactoryBeanName("DelegatedServiceBeanFactory"); // имя фактори бина
    beanDef.setFactoryMethodName("createBean"); // имя метода в фактори бине, который создаёт новый инстанс
    beanDef.setPrimary(true); // назначаем его главным, во избежание конфликта
    registry.registerBeanDefinition(beanName, beanDef); // регистрируем factory bean

Шаг 4. Определим factory bean. Он создаст нам новый экземпляр бина `FooService`,
используя обычный динамический прокси `java.lang.reflect.Proxy`:

    class DelegatedServiceBeanFactory {
        Object createBean(Class<?> serviceInterface) {
            return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {serviceInterface}, this);
        }
    }

Шаг 5. Реализация динамического прокси. В нём, во время исполнения, находим делегата
(в данном случае, сравнивая текущий регион с регионом, прописаном на делегате),
и перенаправляем любое действие на него.

    Object invoke(Object target, Method method, Object[] args) {
        Class<?> delegatedService = method.getDeclaringClass();
        Map<String,Object> beanByName = applicationContext.getBeansOfType(delegatedService);
        String currentRegionFromRequest = ...
        Object delegateBean = ... // найти среди всех делагатов того, который сделает работу
        return method.invoke(delegateBean, args);
    }

Собственно, всё.

## Демонстрационный проект

https://github.com/zencd/spring-service-delegate
