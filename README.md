# Metaheuristic

[![Join the chat at https://gitter.im/metaheuristic-ai/community](https://badges.gitter.im/metaheuristic-ai/community.svg)](https://gitter.im/metaheuristic-ai/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Distributed framework for hyper-parameter optimization and AutoML.  
[Explore Metaheuristic docs »](https://docs.metaheuristic.ai)


## Table of contents

- [Quick start](#quick-start)
- [Quick start for evaluating UI only](#quick-start-for-evaluating-ui-only)
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

##### Quick start for evaluating UI only

1. Create temporary dir for Metaheuristic, i.e. /mh-root 
It'll be /mh-root in follow text. 

1. from /mh-root run git cloning commands:
    ```
    git clone https://github.com/sergmain/metaheuristic.git
    git clone https://github.com/sergmain/metaheuristic-assets.git
    ```

1. Change dir to /mh-root/metaheuristis and run command:
    ```
    mvnw clean install -f pom.xml -Dmaven.test.skip=true
    ```

1. Change dir to /mh-root/metaheuristic-assets/examples/simple-metrics and run scripts:
    ```
    curl-upload-simple-metrics-to-experiment-result
    ```

1. Change dir to /mh-root and run command:
    ```
    java -Dspring.profiles.active=quickstart,dispatcher,processor -jar metaheuristic/apps/metaheuristic/target/metaheuristic.jar --mh.processor.default-dispatcher-yaml-file=metaheuristic/docs-dev/cfg/default-cfg/dispatcher.yaml --mh.processor.default-env-yaml-file=metaheuristic/docs-dev/cfg/default-cfg/env.yaml 
    ```

1. Now you can find our experiment data at http://localhost:8080/dispatcher/experiment-result/experiment-results
login - q, password - 123

1. Press 'Details' for experiment info (there should be only one record in ExperimentResult)

1. Press 'Info' button and on the next page 'Info' button at the bottom of page.

1. Select 2 axes, (i.e. RNN and batches) and press 'Draw plot' 


##### Quick start with running the actual tasks
>To run actual tasks in Metaheuristic you have to have python 3.x.  
Be sure to add the python bin dir to your **$PATH**  
Also PyYAML 5.1 package must [be installed](https://pyyaml.org/wiki/PyYAMLDocumentation) 

1. Change dir to /mh-root/metaheuristic-assets/examples/simple-metrics and run scripts:
    ```
    curl-upload-function-as-one-file
    curl-add-resource-stub
    curl-add-experiment
    curl-add-plan
    curl-bind-experiment-to-plan
    curl-create-execContext
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

1. After status will change to FINISHED you can find our experiment at http://localhost:8080/dispatcher/experiment/experiments  
login - q, password - 123

1. Press 'Info' button and on the next page 'Info' button at the bottom of page.

1. Select 2 axes, (i.e. RNN and batches) and press 'Draw plot' 


## License and licensing
Metaheuristic has dual licensing.

All code in repository (https://github.com/sergmain/metaheuristic) is licensed under GPL-3.0  

For commercial use you must buy commercial license with annual subscription if needed:

| Type of customer (Org or personal)                     | Conditions of using |
|--------------------------------------------------------|---------------------|
| Personal use  (using in commercial project prohibited) | Free to use         |
| Scientific researches                                  | Free to use, citing | 
| Non-profit organizations                               | Free to use, citing | 
| Commercial use, less than 50 active concurrent Processors\*                | Free to use         | 
| All other cases when there are 50 active concurrent Processors\* or more   | Annual subscription, $50k for Dispatcher\*\*, $200 per Processor | 

\* Processor is a client part of metaheuristic which is processing tasks.   
\*\* Dispatcher is a server part of metaheuristic which is serving all configurations 
and managing the process of assigning tasks to Processors.

## Copyright
Innovation platforms LLC, San Francisco, US 