# Metaheuristic

Distributed framework for hyper-parameter optimization and AutoML.  
[Explore Metaheuristic docs »](https://docs.metaheuristic.ai)


## Table of contents

- [Quick start](#quick-start)
- [Quick start for evaluation UI only](#quick-start-for-evaluation-ui-only)
- [Quick start with running the actual tasks](#quick-start-with-running-the-actual-tasks)
- [License and licensing](#license-and-licensing)
- [Copyright](#copyright)

## Prerequisites: Java Development Kit (JDK) 11

To run Metaheuristic you have to have jdk 11  
Right now there isn't any known bug which restricts to use certain JDK.

[Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)  
[AdoptOpenJDK (AKA OpenJDK) 11](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot)  
[Zulu JDK 11](https://www.azul.com/downloads/zulu-community/?&version=java-11-lts)  


## Quick start
>**Attention**. The 'Quick start' mode is working with embedded db which is hosting in memory. 
As a result after stopping Metaheuristic all data will be lost.

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
    java -Dspring.profiles.active=quickstart,launchpad,station -jar metaheuristic/apps/metaheuristic/target/metaheuristic.jar --mh.station.default-launchpad-yaml-file=metaheuristic/docs-dev/cfg/default-cfg/launchpad.yaml --mh.station.default-env-yaml-file=metaheuristic/docs-dev/cfg/default-cfg/env.yaml 
    ```


##### Quick start with running the actual tasks
>To run actual tasks in Metaheuristic you have to have python 3.x.  
Be sure to add the python bin dir to your **$PATH**  
Also PyYAML 5.1 package must [be installed](https://pyyaml.org/wiki/PyYAMLDocumentation) 

1. Change dir to /mh-root/metaheuristic-assets/examples/simple-metrics and run scripts:
    ```
    curl-add-snippet-as-one-file
    curl-add-resource-stub
    curl-add-experiment
    curl-add-plan
    curl-bind-experiment-to-plan-with-resource
    curl-add-workbook
    curl-produce-experiment-tasks
    ```

1. At this point Metaheuristic started to produce tasks 
and you have to have until status will 'PRODUCED'. You can check current status by running script
    ```
    curl-get-experiment-processing-status
    ```

1. After being changed to 'PRODUCED' run the command:
    ```
    curl-start-processing-of-experiment-tasks
    ```

1. All tasks will be completed in 10 minutes approximately. You can get the current status of processing by command:
    ```
    curl-get-experiment-processing-status
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
Metaheuristic has 2-license type.

All code in repository (https://github.com/sergmain/metaheuristic) is licensed under GPL-3.0  

For commercial use you must buy commercial license with annual subscription if needed:

| Type of customer (Org or personal)                     | Conditions of using |
|--------------------------------------------------------|---------------------|
| Personal use  (using in commercial project prohibited) | Free to use         |
| Scientific researches                                  | Free to use, citing | 
| Non-profit organizations                               | Free to use, citing | 
| Commercial use, less than 25 Stations\*                | Free to use         | 
| All other cases when there are 25 Stations\* or more   | Annual subscription, $50k for Launchpad\*\*, $500 per station | 

\* Station is a client part of metaheuristic which is processing tasks.   
\*\* Launchpad is a server part of metaheuristic which is serving all configurations 
and managing the process of assigning tasks to Stations.

## Copyright
Innovation platforms LLC, San Francisco, US 