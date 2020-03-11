update mh_function
set params = replace(PARAMS, 'sourcing: launchpad', 'sourcing: dispatcher');

update mh_function
set params = replace(PARAMS, 'sourcing: station', 'sourcing: processor');

update mh_function
set params = replace(PARAMS, 'metrics: false', '');

update mh_function
set params = replace(PARAMS, 'metrics: true', '');

update mh_function
set params = replace(PARAMS, 'version: 2', 'version: 1');