# Spring Service Delegate

## Общая задача

Есть некий спринг-сервис:

    @Autowired
    FooService fooService;

и несколько его реализаций (делегатов).
Хочется в динамике распределять вызовы между этими реализациями в зависимости от рантайм-обстоятельств.
В этом примере решение принимается, сравнивая текущий биллинг клиента (выводится из http-запроса)
с биллингом, прописанном на делегате.
Технически, биллинг - это константа: "BILLING1" или "BILLING2".

## Желаемые дизайн

    @DelegatedService
    interface FooService { }

    @Service
    @IfBilling("BILLING1") // условие, при котором будет вызываться именно он
    class FooService1 implements FooService { }

    @Service
    @IfBilling("BILLING2") // другое условие
    class FooService2 implements FooService { }

## Обычное, неинтересное, статичное решение

Вобщем-то можно всё это реализовать и в статике, но есть проблема:
нужно писать больше клиентского кода -
для каждого делегируемого сервиса придётся явно определять свой `DelegatingFooService`,
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

## Новое, динамическое решение

Решение было подсмотрено в
[FeignClientsRegistrar](https://github.com/spring-cloud/spring-cloud-openfeign/blob/main/spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignClientsRegistrar.java)
который делает примерно то же - динамически реализует интерфейсы (бины) feign-клиентов.

Любые новые бины можно динамически определять в ImportBeanDefinitionRegistrar:

    public class DelegatedServiceBeanRegistrar implements ImportBeanDefinitionRegistrar {
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            ...
        }
    }

Там найдём все сервисы (джава интерфейсы), аннотированных как наш `DelegatedService`:

    Set<Class<?>> whoAnnotated = new Reflections("com.demo").getTypesAnnotatedWith(DelegatedService.class);
    var interfaces = whoAnnotated.stream().filter(Class::isInterface).toList();
    for (var interfaceClass : interfaces) {
        ...
    }

Для каждого `DelegatedService`
не создаём новый инстанс сервиса, но - лучше - укажем какой factory bean
умеет создавать новый инстанс сервиса.
Инстанс, который будет распределять ответственность на конкретных исполнителей (делегатов).

    var beanDef = new GenericBeanDefinition();
    beanDef.setBeanClass(interfaceClass); // FooService.class
    beanDef.setFactoryBeanName("DelegatedServiceBeanFactory"); // имя фактори бина
    beanDef.setFactoryMethodName("createBean"); // имя метода в фактори бине, который создаёт новый инстанс
    beanDef.setPrimary(true); // назначаем его главным, во избежание конфликта
    registry.registerBeanDefinition(beanName, beanDef); // регистрируем factory bean

Factory bean; он создаёт новый экземпляр, наследующий `FooService`, на лету через обычный динамический прокси `java.lang.reflect.Proxy`:

    class DelegatedServiceBeanFactory {
        public Object createBean(Class<?> serviceInterface) {
            return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {serviceInterface}, this);
        }
    }

Реализация динамического прокси. В нём в динамике находим делегата
(в данном случае - сравнивая текущий биллинг с биллингом, прописаном на делегате),
и перенаправляем все действия на него.

    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        Class<?> serviceInterface = method.getDeclaringClass();
        Map<String,Object> beanByName = applicationContext.getBeansOfType(serviceInterface);
        String currentBilling = ...
        Object delegateBean = ... // найти среди всех делагатов того, который сделает работу
        return method.invoke(delegateBean, args);
    }

Собственно, всё.

## Демонстрационный проект

https://github.com/zencd/spring-service-delegate
