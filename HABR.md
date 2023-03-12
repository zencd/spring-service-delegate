# Spring Service Delegate

## Общая задача

    @Autowired
    UserService userService;

Есть некий спринг-сервис и несколько его реализаций (делегатов).
Хочется в динамике распределять вызовы между этими реализациями, в зависимости от запроса,
в данном случае от региона пользователя.

Ссылка не демо-проект в конце.

## Обычное, неинтересное, статичное решение

Вобщем-то можно всё это реализовать и в "статике", но тут появляется проблема:
придётся писать больше клиентского кода; конкретно, 
для каждого делегируемого сервиса нужно руками определять делегирующий бин.
Весь смысл данной заметки — как этой обязанности избежать.

    interface UserService { }

    @Primary
    @Service
    class DelegatingUserService implements UserService {
        Map<String,UserService> delegateByName;
        // делегируем вызовы на UserServiceRu или UserServiceWorld
    }

    @Service
    class UserServiceRu implements UserService { }

    @Service
    class UserServiceWorld implements UserService { }

## Желаемый дизайн

    @DelegatedService
    interface UserService { }

    @Service
    @IfRegion("RU") // условие, при котором будет вызываться именно он
    class UserServiceRu implements UserService { }

    @Service
    @IfRegion("WORLD") // другое условие
    class UserServiceWorld implements UserService { }

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

Шаг 2. Там найдём все сервисы (интерфейсы), аннотированных как наш `DelegatedService`:

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
    beanDef.setBeanClass(interfaceClass); // UserService.class
    beanDef.setFactoryBeanName("DelegatedServiceBeanFactory"); // имя фактори бина
    beanDef.setFactoryMethodName("createBean"); // имя метода в фактори бине, который создаёт новый инстанс
    beanDef.setPrimary(true); // назначаем его главным, во избежание конфликта
    registry.registerBeanDefinition(beanName, beanDef); // регистрируем factory bean

Шаг 4. Определим factory bean. Он создаст нам новый экземпляр бина `UserService`,
используя обычный динамический прокси `java.lang.reflect.Proxy`:

    class DelegatedServiceBeanFactory {
        Object createBean(Class serviceInterface) {
            return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {serviceInterface}, this);
        }
    }

Шаг 5. Реализация динамического прокси. В нём, во время исполнения, находим делегата,
сравнивая регион пользователя с регионом, прописаном на делегате,
и перенаправляем все действия на него.

    Object invoke(Object target, Method method, Object[] args) {
        Class delegatedService = method.getDeclaringClass();
        Map<String,Object> delegateBeanByName = applicationContext.getBeansOfType(delegatedService);
        String currentRegionFromRequest = ...
        Object delegateBean = ... // найти среди всех делагатов наиболее подходящего
        return method.invoke(delegateBean, args);
    }

Собственно, всё.

## Демонстрационный проект

https://github.com/zencd/spring-service-delegate
