# Metaheuristic


Metaheuristic is an application for organizing distributed computations.  
[Explore Metaheuristic docs »](https://github.com/sergmain/metaheuristic/wiki)

- [to Index of articles](https://github.com/sergmain/metaheuristic/wiki/)


## Table of contents
- [Immediate start](#Immediate-start)
- [Quick start](#quick-start)
- [License and licensing](#license-and-licensing)
- [Copyright](#copyright)
- [How to cite Metaheuristic](#How-to-cite-Metaheuristic)

## Immediate start
Prerequisites: Java 17, token OPENAI_API_KEY
 - create dir /mh-home
 - run command:
```commandline
java -Xms1g -Xmx1g -Dfile.encoding=UTF-8 -Dspring.profiles.active=dispatcher,h2 -DMH_HOME=/mhbp_home -jar distrib/metaheuristic.jar
```
 - access http://localhost:8080/ 
    - login: qqq
    - password: 123

 - The article about details for [Immediate start](https://github.com/sergmain/metaheuristic/wiki/Immediate-start)


## Quick start

The main article about [quick start](https://github.com/sergmain/metaheuristic/wiki/quick-start)


## License and licensing
Metaheuristic has a dual licensing.

All code in a repository (https://github.com/sergmain/metaheuristic) is licensed under GPL-3.0
Exception is a module 'apps/commons' which is licensed under Apache 2 type license.

For commercial use you must buy a commercial annual subscription if needed:

| Type of customer (Org or personal)                                         | Conditions of using   |
|----------------------------------------------------------------------------|-----------------------|
| Personal use                                                               | Free to use           |  
| Commercial usage in company owned up to 2 persons\*                        | Free to use           |  
| Scientific researches                                                      | Free to use\*\*\*\*   |  
| Non-profit organizations                                                   | Free to use           |  
| Commercial usage without MHBP, less than 25 Processors\*\*                 | Free to use           | 
| Commercial usage with MHBP\*\*\*\*\*, one instance of DB per account\*\*\* | Free to use           | 

\* Ownership of company must be directed, ownership via any kind of proxy company is prohibited.  
\*\* Processors are client parts of metaheuristic which are processing tasks.
One instance of Metaheuristic can handle multiply Processors.   
\*\*\* instance of DB is an installation of database in OS, not a scheme or database in term of DLL.
I.e. - if you want to use multi-tenant option and host all your companies and users in one installation of DB, you have to buy subscription.
Accounts in Main company (ID #1) are excluded from counting.  
\*\*\*\* Citing isn't required but we will be glad if you cite Metaheuristic in your paper.
\*\*\*\*\* usage of MHBP is an usage of Evaluation or/and Scenario 

Commercial usage
All other cases (i.e. there are 25 Processors\*\* or more, multi-tenant/multi user installation,
commercial support, usage of MHBP's Evaluation or/and Scenario, other cases):
- Annual subscription
   - $25k for Dispatcher\*\*\*
   - $500 per Processor
   - $1200 per User\*


\* User is person with distinctive account in db. If person has 2 accounts this is calculated as 2 Users.
Accounts in Main company (ID #1) are excluded from counting.
\*\* Processors are client parts of metaheuristic which are processing tasks.
One instance of Metaheuristic can handle multiply Processors. For using MHBP only, Processors aren't needed.   
\*\*\* Dispatcher is a server part of metaheuristic which is serving all configurations
and managing the process of assigning tasks to Processors.

If you need a commercial support, it can be bought at the same price as for Commercial usage.

The main article about licensing - https://github.com/sergmain/metaheuristic/wiki/license-licensing-copyright#license-and-licensing

## Copyright
Innovation platforms LLC, San Francisco, US, 2023


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

