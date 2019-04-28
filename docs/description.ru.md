---
layout: default
---

Общее описание

1. Сущности, общая концепция

1.1 Общая концепция
логически Aiai состоит из 2-ух верхнеуровневых модулей:
- launchpad - стартовая площадка
- station - станция

Стартовая площадка используется для управления заданиями, которые будут обрабатываться на станциях.
Станции могут получать задания от неограниченного количества стартовыз площадок.
Выбор стартовой площадки у которой необходимо запросить новое задание на выполнение осучествляется
на основе Round-robin алгоритма.

1.2 Flow (поток)
Поток - это описатель содержащий описания процессов, котрые будут выполняться для перобразования
данных из начальной формы в конечную. Поток, как описатель, не содержит состояния
выполнения процесса преобразования данных.

1.3 FlowInstance (реализация потока)
Реализация потока - это сущьность, которая включает в себя инфрмацию о выполнении преобразования данных.

1.4 Resource (ресурс)
Ресурс - это данные, которые используются или создаются в процессе преобразования данных потоком.
Ресурны бывают 2-ух типов - DATA (данные), SNIPPET(спипет)
Данные - это все что загружается в снипеты или или является продуктов их работы.
Снипет, как ресурс - это исполняемый код, который соответствует одному из описателей снипетов

1.5 Snippet (спипет)
Снипет - это описатель исполняемого кода, который будет вызываться при выполнении задачи.


1.6 Task (задача)
Задача - описание, что нужно выполнить, с какими параметрами, какой результат получить


2. Общая схема взаимодействия сушностей

2.1 для того , чтобы обработать данные необходимо выполнить следующие шаги:
- загрузить снипеты, которые будут использоваться при преобразовании данных
- создать и загрузить исходные данные, которые будут использоваться
как входные данные для всего преобразования.
- создать поток
- выполнить валидацию созданного потока. Если есть ошибки - исправить, повторить валидацию.
если все ок, то перейти к следующему шагу
-  после успешной валидации можно создать реализацию потока. Реализация включает в себя
код набора входных ресурсов, ссылку на Поток, который надо преобразовать.
- сгенерировать задачи для реализации потока
 стадии: PROCESSING - задачи генерятся, PROCESSED - задачи сгенерированы
- запустить задачи для данного потока
 стадии: STARTED - задачи выполняются, STOPPED - выполнение новых задач для данной реализвации потока
 остановлено, FINISHED - все задачи для данной реализации потока выполнены



3. Установка

Устанавливать стартовую площадку можно в любом окружении, где есть доступ к mysql или postgresql
установка станции на этом же сервере не рекомендуется для прода,
и имеет смысл только для целей разработки и тестирования

3.1. Директории
- для конфигурации стартовой площадки надо выбрать и создать рабочий директорий,
в котором стартовая площадка будет создавать свои артифакты
- выбрать директорий, в котором будет запускаться собственно Aiai. Рекомендуемая схема директориев:
\aiai - главный директорий для aiai
\aiai\config - директорий, в котором будет находиться application.properties конфиг файл
\aiai\git - в данный директорий будет делаться clone из git
\aiai\launchpad - директорий для стартовой площадки
\aiai\station - директорий для станции

3.2 git
перейти в \aiai  (или другой директорий, который выбран, как основной)
выполнить команду
git clone https://github.com/sergmain/aiai.git git

3.3 Сборка
для сборки проекта требуется java 11. 
Скачать текущий релиз JDK11 надо по ссылке https://jdk.java.net/11/

в директории \aiai\git запустить
mvn-all.bat

3.4 Database
создать нового пользователя и схему(базу данных).

используя соответствующий скрипт создать таблицы в БД в созданной ранее схеме
MySql       - sql/db-mysql.sql
Postgresql  - sql/db-postgresql.sql


3.5 application.properties
за основу можно взять файл, который доступен по url -
https://github.com/sergmain/aiai/blob/master/apps/aiai/src/main/resources/application.properties

при этом заменив значения, которые берутся из окружения, на конкретные значения

3.5.1 общие значения для стартовой площадки и станции
aiai.thread-number=3
определяет количество потоков, которые будет использоваться для паралельной работы aiai
делать количество потоков больше, чем количестов виртуальных ядер (ядер с учетом гипер-трединга) смысла нет.


3.6 Стартовая площадка
3.6.1 application.properties для стартовой площадки
конфигурация конфиг файла заключается в задании следующий параметров

spring.profiles.active=launchpad

для mysql
spring.datasource.url = jdbc:mysql://localhost:3306/aiai?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=CONVERT_TO_NULL&autoReconnect=true&failOverReadOnly=false&maxReconnects=10&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=America/Los_Angeles&sslMode=DISABLED
spring.datasource.username = aiai
spring.datasource.password = qwe321
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect = org.hibernate.dialect.MySQL57Dialect

временную зону в url для mysql ( serverTimezone=America/Los_Angeles ) выбрать согласно документации на mysql 
и изменить в spring.datasource.url
в приведеном выше примере временная зона - serverTimezone=America/Los_Angele

для postgresql
spring.datasource.url=jdbc:postgresql://host:5432/database?user=abc&password=xxx&sslmode=require
spring.datasource.username=abc
spring.datasource.password=xxx
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQL95Dialect


указать специфические для стартовой площадки параметры:

```
======================
aiai.launchpad.enabled=true
aiai.launchpad.dir=./launchpad
aiai.launchpad.is-replace-snapshot=true

aiai.launchpad.is-ssl-required=false
aiai.launchpad.secure-rest-url=true


# password - 123
aiai.launchpad.master-password=$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu
aiai.launchpad.master-username=q
aiai.launchpad.master-token=1

aiai.launchpad.rest-password=$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu
aiai.launchpad.rest-username=q1
aiai.launchpad.rest-token=11

aiai.launchpad.public-key= MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtS3jRjE1wlHcxiqn6fCRvTahRt6LBvhrqxzgo1FcpJ9uZvRUmf3KwszwQoL+Ypw7aM9oxmg15Q+pssKcrulS/ofDfbuusiYdny7wMlil1H11svQM3yGwMl9gjZ2FupaRwpyZkIMj1ILaDhylTudQCBoJgJ/BWyMCDn2kzh5EpV7hkhhfjZ/2/NRIcayQVmMKOikCXR8q1bb3QNQ2HiMyUsBUGzeO2DuvX4n375+SaFIDrse4eGNVbR/ImWw7TeD4wk0h5kJ2VTdgl2J7gVS7gCCMwBN9TVxPErRDxg/OtXreS8VRUd0hOZiadX12KjwI4mjhC4q+geXAq2sC1DOV8wIDAQAB

=========================
```

aiai.launchpad.is-replace-snapshot - можно ли перезаписывать snapshot-спипеты новыми версиями

aiai.launchpad.is-ssl-required - все взаимодействия со стартовой площадкой только через SSL
по умолчанию использование SSL для всех http запросов включено.
Для контороля SSL используется параметр aiai.is-ssl-required
Например:
aiai.launchpad.is-ssl-required=false

Отключать SSL рекомендуется только, если aiai  (как стартовая площадка, так и станция) запускается
на localhost или когда станции и стартовая площадка распологаются в одной DMZ


aiai.launchpad.secure-rest-url - нужен ли пароль/логин для защиты rest урл

aiai.launchpad.master-* - данные для аутенсификации для web интерфейса
aiai.launchpad.rest-* - данные для аутенсификации дл rest урл


для aiai.launchpad.public-key необходимо использовать приложение из apps/gen-keys

для
aiai.launchpad.rest-password
aiai.launchpad.master-password
необходимо использовать приложение из apps/gen-passwords

-username и -token выбираются самостоятельно, но не могут включать в себя символ '=' (символ равно)

3.7 Станция
3.7.1 application.properties для станции
spring.profiles.active=station

aiai.station.enabled=true
aiai.station.dir=./station


3.7.2 Конфигурация launchpad.yaml
Для конфигурации стартовых площадок, с которыми будет взаимодействовать конкретная станция используется
конфиг файл launchpad.yaml. Данный файл должен располагаться в директории который является главным для станции,
в данном руковосдстве это \aiai\station
формат файла:
launchpads:
  - signatureRequired: false
    isSecureRestUrl: true
    url: http://localhost:8080
    lookupType: direct
    authType: basic
    restPassword: 123
    restUsername: q1
    restToken: 11
    taskProcessingTime: |
      workingDay: 0:00-23:59
      weekend: 0:00-23:59
    publicKey: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtS3jRjE1wlHcxiqn6fCRvTahRt6LBvhrqxzgo1FcpJ9uZvRUmf3KwszwQoL+Ypw7aM9oxmg15Q+pssKcrulS/ofDfbuusiYdny7wMlil1H11svQM3yGwMl9gjZ2FupaRwpyZkIMj1ILaDhylTudQCBoJgJ/BWyMCDn2kzh5EpV7hkhhfjZ/2/NRIcayQVmMKOikCXR8q1bb3QNQ2HiMyUsBUGzeO2DuvX4n375+SaFIDrse4eGNVbR/ImWw7TeD4wk0h5kJ2VTdgl2J7gVS7gCCMwBN9TVxPErRDxg/OtXreS8VRUd0hOZiadX12KjwI4mjhC4q+geXAq2sC1DOV8wIDAQAB
    isAcceptOnlySignedSnippets: false


Описание параметров:
signatureRequired: пребуется ли подпись (в настоящее время не используется)
isSecureRestUrl: rest урл защищен логин/паролем
url: урл стартовой площадки / регистра,
    если lookupType==direct, то url это адрес стартовой площадки,
    если lookupType==registry, то url это адрес регистра стартовых площадок
lookupType: см параметр url
authType: тип аутенсификации, basic - стандартная BASIC аутенсификация, oauth - аутенсификация по OAuth2.0
restPassword: пароль для rest урл
restUsername: пароль для rest
restToken: токен для rest
taskProcessingTime: рассписание, когда задачи с данной стартовой площадки активны,
      workingDay:  время для запуска в рабочие дни
      weekend: время для запуска в выходные дни

    формат времени: 0:00 - 23:59 или 0:00 - 8:45, 19:00 - 23:59
publicKey: публичный ключ для данной стартовой площадки
isAcceptOnlySignedSnippets: принимать только подписанные снипеты


для publicKey необходимо использовать приложение из apps/gen-keys, подробнее см "Конфигурация стартовой плащадки"


3.8 Конфигурация spring.profiles.active
в настоящее время в aiai поддерживается 2 профиля - launchpad и station
эти профили можно использовать как по отдельности, так и комбинировать. В любом случаев,
в файле application.properties должна быть только ОДНА строка spring.profiles.active=
Например:
spring.profiles.active=launchpad
или
spring.profiles.active=launchpad, station
или
spring.profiles.active=station

!Внимание! данная инструкция написана для случая когда используютсф оба профиля.
поэтому параметр spring.profiles.active в apllication.properties должен быть следующим:

spring.profiles.active=launchpad, station
  


3.9 конфигурация Http сервера на стороне стартовой площадки
3.9.1 IP адрес
для точного указания на какой IP адрес биндить http используется параметр server.address
Например:
server.address=127.0.0.1


4. apps/gen-keys
для генерации ключей необходимо запустить

java -jar apps/gen-keys/target/gen-keys.jar

новые ключи (публичный и частный) будут напечатаны в консоле
как и где будут использоваться публичный и частный ключи будет написано далее

ВНИМАНИЕ! сообщения 'Public key in base64 format:' и 'Private key in base64 format:' 
не являются частью ключей и не должны копироваться в файлы с ключами

5. apps/gen-passwords

для преобразования паролей в формат aiai (bcrypt, 10 циклов) запустить

java -jar apps/gen-passwords/target/gen-passwords.jar <master password> <rest password>

<master password> - пароль для доступа к веб-консоле стартовой площадки
<rest password> - пароль для доступа к rest-api

результат работы поместить в соответствующие параметры:
master password --> aiai.launchpad.master-password
master token --> aiai.launchpad.master-token

rest password --> aiai.launchpad.rest-password
rest token --> aiai.launchpad.rest-token

токены могут быть изменены по желанию,
но не должны быть пустыми и включать в себя символ '=' (символ равно)

6. Запуск
для запуска aiai (как стартовой плошадки, так и станции) необходимо
из директория \aiai запустить команду

java -jar git/apps/aiai/target/aiai.jar

6.1 обновление кодовой базы
для того чтобы забрать последние изменения в проекте необходимо перейти в \aiai\git
и выполнить команды:

git pull origin master
mvn-all.bat

7. Управление стартовой площадкой
после того, как все параметры были прописаны, можно запустить стартовую площадку
по адресу на котором она была запущена
логин - aiai.master-username=aiai.master-token
пароль - aiai.launchpad.master-password

т.е если
aiai.master-username=yyy
aiai.master-token=xxx

то логин будет - yyy=xxx

если все запустилось успешно, то можно перейти к созданию сущеностей

8. Flow (поток)
создать простейший поток, состоящий из одного процесса.
Создать поток можно перейдя по адресу /launchpad/flow/flow-add

значения полей:
Code of flow -->  simple-flow

Parameters of flow -->

processes:
- code: simple-app
  collectResources: false
  name: Simple snippet
  type: FILE_PROCESSING
  parallelExec: false
  snippetCodes:
  - simple-app:1.1




сразу после создания данный поток не будет валиден
т.к. необходимо создать сниппет simple-app:1.1


9. Снипет
создание снипета, который будет загружен через стартовую площадку состоит из нескольких этапов
- создать приложение, которое будет обрабатывать данные
- создать конфиг описания снипета
- запаковать снипет приложением apps/package-snippet
- если окружение сконфигурировано на использование только подписанных снипетов,
то при паковке снипета так же подписать его используя приватный ключ


9.1 снипет без исполняемого кода
для простоты мы будем создавать снипет, который не содержит исполняемый код

9.2 конфиг снипета
отсутствие необходимости иметь исполняемый код помечается в
fileProvided=true

snippets:
    - name: simple-app
      version: 1.1
      type: simple
      env: simple-app
      fileProvided: true


сама ссылка на исполняемое приложение конфигурируется в env: simple-app

9.3 подписание снипета
создать временный директорий и в нем создать файл snippets.yaml
поместить в данный файл конфиг снипета
snippets:
    - name: simple-app
      version: 1.1
      type: simple
      env: simple-app
      fileProvided: true


используя приложние apps/package-snippet запаковать и подписать снипет. 
package-snippet описан в п.10


10. apps/package-snippet

для запаковывания снипета и его подписания необходимо:
- создать временный директорий
- в данном временном директории создать файл snippets.yaml и заполнить его настройками согласно п.9.3
- из временного директория запустить 

java -jar \aiai\git\apps/package-snippet/target/package-snippet.jar snippet.zip <path to private key file>

- для корректного запуска <path to private key file> должен указывать на созданный ранее частный ключ, например:
 \aiai\git\private-key.txt

первый параметр (в примере это snippet.zip) указывает название архива в который будет запакован снипет
если второй параметр определен, то снипет будет подписан.
второй параметр это путь до файла с частным ключом. Частный ключ генерится приложением gen-keys


11. загрузка снипетов
используя веб-интерфейс стартовой площадки загрузить снипет, перейдя по адресу

http://localhost:8080/launchpad/snippets

после успешной загрузки снипета проверка потока больше не должна выдавать сообщние, что снипет
не обнаружен

12. окружение станции
12.1 env.yaml
для того, чтобы снипеты запускались на стороне станции необходимо сконфигурировать
исполняемое окуржение. для этого необходимо создать файл \aiai\station\env.yaml

конфиг:
envs:
  simple-app: java -jar C:\aia\git\apps\simple-app\target\simple-app.jar


в нашем примере исполяемой командой будет запуск простого приложения

12.2 app/simple-app
данное приложение должно быть уже собрано после выполнения команды mvn-all.bat
для провеки необходимо из командной строки запустить:
java -jar C:\aia\git\apps\simple-app\target\simple-app.jar

если все приложение simple-app собрано успешкно то в консоле будет выведено:
Parameter file wasn't specified


13. Загрузка данных
для инициализации данных для обработки нашим снипетом загрузим любой текстовый файл
через интерфейс работы с ресурсами

http://localhost:8080/launchpad/resources

указать
Resource code - simple-resource
Resource pool code - simple-resource-pool


14. Создание реализации потока (Flow instance)
перейти по адресу
http://localhost:8080/launchpad/flow/flows

и выбрать поток, который мы ранее создали

и затем нажать кнопку 'Instances'

Создать новую реализацию потока:
Input pool code for this flow - simple-resource-pool


15. запуск реализации потока

после успешного создания реализации потока (Flow Instance) запустить генерацию задач
нажав кнопку "Produce"

дождаться когда статус будет изменен с PRODUCING на PRODUCED
примечание. в настоящее время автоматическое обновление старницы не реализовано.

Запустить поток на выполнение нажатием кнопки "Start"

в директории \aiai\station\task
должен появиться директорий 0 и в нем директорий с номером задачи.
в директории \aiai\station\task\0\xxx\artifacts после выполнения задачи
будет содан файл system-console.log в котором будет выведно содержимое файла,
который мы загрузили как ресурс.


