# Metaheuristic

[![Join the chat at https://gitter.im/metaheuristic-ai/community](https://badges.gitter.im/metaheuristic-ai/community.svg)](https://gitter.im/metaheuristic-ai/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Hetaheuristic is application for organazing a distributed computations.  
[Explore Metaheuristic docs »](https://docs.metaheuristic.ai)


## Table of contents

- [Quick start from docker](#quick-start-from-docker)
- [Quick start](#quick-start)
- [License and licensing](#license-and-licensing)
- [Copyright](#copyright)

>**Attention**. The 'Quick start' mode is working with embedded db which is being hosted in memory.
As a result after stopping Metaheuristic all data will be lost.

## Quick start from docker

Docker tag: sergmain/metaheuristic:metaheuristic-quickstart-embedded  
bind ports 8083:3083

Access a simple UI of Metaheuristic via [http://localhost:8083](http://localhost:8083)

or access an Angular-based UI of Metaheuristic via
[https://adocker8083.metaheuristic.ai/#/](https://adocker8083.metaheuristic.ai/#/)

continue reading at [start running the actual tasks](https://docs.metaheuristic.ai/p/quick-start#quick-start-for-evaluating-ui-only)


## Quick start

The main article about [quick start](https://docs.metaheuristic.ai/p/quick-start)


## License and licensing
Metaheuristic has a dual licensing.

All code in a repository (https://github.com/sergmain/metaheuristic) is licensed under GPL-3.0

For commercial use you must buy commercial annual subscription if needed:

| Type of customer (Org or personal)                                  | Conditions of using                                                    |
|---------------------------------------------------------------------|------------------------------------------------------------------------|
| Personal use                                                        | Free to use                                                            |  
| Commercial usage in company owned by 1-3 person\*                   | Free to use                                                            |  
| Scientific researches                                               | Free to use                                                            |  
| Non-profit organizations                                            | Free to use\*\*\*\*                                                    |  
| Commercial use, less than 25 Processors\*\*                         | Free to use                                                            | 
| All other cases when there are 25 Processors\*\* or more.\*\*\*\*\* | Annual subscription, $25k for Dispatcher\*\*\*, $500 per Processor\*\* | 

\* Ownership of company must be directed, ownership via any kind of proxy company is prohibited.  
\*\* Processors are client parts of metaheuristic which are processing tasks.
One instance of Metaheuristic can handle multiply Processors.   
\*\*\* Dispatcher is a server part of metaheuristic which is serving all configurations
and managing the process of assigning tasks to Processors.   
\*\*\*\* Citing isn't required but we will be glad is you cite Metaheuristic in your paper.   
\*\*\*\*\* If your installation has lesser that 25 Processors but you need commercial support,
you can buy it at the same price.

## Copyright
Innovation platforms LLC, San Francisco, US, 2022 


## How to cite Metaheuristic

Please cite this repository if it was useful for your research:

The bibtex entry for this is:
```text
@misc{metaheuristic,
  author = {Lissner, Sergio},
  title = {Metaheuristic},
  year = {2022},
  publisher = {GitHub},
  journal = {GitHub repository},
  howpublished = {\url{https://github.com/sergmain/metaheuristic}},
}
```

