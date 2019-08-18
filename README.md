---
layout: default
---

<!---
<p align="center">
  <a href="https://getbootstrap.com/">
    <img src="https://docs.metaheuristic.ai/assets/brand/mh-logo.svg" alt="Metaheuristic logo" width="72" height="72">
  </a>
</p>
--->
 
<h3 align="center">Metaheuristic</h3>

<p align="center">
  Distributer framework for hyper-parameter optimization and AutoML.
  <br>
  <a href="https://docs.metaheuristic.ai">Explore Metaheuristic docs »</a>
</p>


## Table of contents

- [Quick start](#quick-start)
- [Quick start for evaluation UI only](#quick-start-for-evaluation-ui-only)
- [Quick start with running the actual tasks](#quick-start-with-running-the-actual-tasks)
- [License and licensing](#license-and-licensing)

## Prerequisites: Java Development Kit (JDK) 11

To run Metaheuristic you have to have jdk 11
Right now there isn't any known bug which restricts to use certain JDK.

[Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)  
[AdoptOpenJDK (AKA OpenJDK) 11](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot)  
[Zulu JDK 11](https://www.azul.com/downloads/zulu-community/?&version=java-11-lts)  


## Quick start

##### Quick start for evaluation UI only

1. Create temporary dir for Metaheuristic, i.e. /mh-root 
It'll be /mh-root in follow text. 

1. from /mh-root run git cloning command:
    ```
    git clone https://github.com/sergmain/metaheuristic.git
    git clone https://github.com/sergmain/metaheuristic-assets.git
    ```

1. Change dir to /mh-root/metaheuristis and run command:
    ```
    mvnw clean install -f pom.xml -Dmaven.test.skip=true
    ```
1. Change dir to /mh-root and run command:
    ```
    java  -Dspring.profiles.active=quickstart,launchpad,station -jar metaheuristic/apps/metaheuristic/target/metaheuristic.jar 
    ```


##### Quick start with running the actual tasks
>To run actual tasks in Metaheuristic you have to have python 3.x.  
Be sure to add the python bin dir to your **$PATH**

1. Change dir to /mh-root/metaheuristic-assets/examples/simple-metrics and run scripts:
    ```
    curl-snippet-as-one-file
    curl-resource-stub
    curl-experiment
    curl-plan
    curl-bind-experiment-to-plan-with-resource
    curl-add-workbook
    curl-experiment-produce-tasks
    ```

1. At this point Metaheuristic started to produce tasks 
and you have to have until status will 'PRODUCED'. You can check current status by running script
    ```
    curl-experiment-processing-status
    ```

1. After being changed to 'PRODUCED' run the command:
    ```
    curl-experiment-start-processing-of-tasks
    ```

1. All tasks will be completed in 10 minutes approximately. You can get the current status of processing by command:
    ```
    curl-experiment-processing-status
    ```

    there are 3 possible statuses at this point:  
    STARTED - processing of tasks was started  
    STOPPED - processing of tasks was stopped  
    FINISHED - processing of tasks was finished  

1. After status will change to FINISHED you can find our experiment at http://localhost:8080/launchpad/experiment/experiments  
login - q, password - 123

1. Press 'Info' button and on the next page 'Info' button at the bottom of page.

1. Select 2 axes, (i.e. RNN and batches) and press 'Draw plot' 


## License and licensing

| | [H2](https://h2database.com/) | [Derby](https://db.apache.org/derby) | [HSQLDB](http://hsqldb.org) | [MySQL](https://www.mysql.com/) | [PostgreSQL](https://www.postgresql.org) |
|--------------------------------|---------|---------|---------|-------|---------|
| Pure Java                      | Yes     | Yes     | Yes     | No    | No      |
| Memory Mode                    | Yes     | Yes     | Yes     | No    | No      |
| Encrypted Database             | Yes     | Yes     | Yes     | No    | No      |
| ODBC Driver                    | Yes     | No      | No      | Yes   | Yes     |
| Fulltext Search                | Yes     | No      | No      | Yes   | Yes     |
| Multi Version Concurrency      | Yes     | No      | Yes     | Yes   | Yes     |
| Footprint (embedded database)  | ~2 MB   | ~3 MB   | ~1.5 MB | —     | —       |
| Footprint (JDBC client driver) | ~500 KB | ~600 KB | ~1.5 MB | ~1 MB | ~700 KB |

