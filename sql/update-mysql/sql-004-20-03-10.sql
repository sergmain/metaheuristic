update mh_function
set params = replace(PARAMS, 'sourcing: launchpad', 'sourcing: dispatcher');

update mh_function
set params = replace(PARAMS, 'sourcing: station', 'sourcing: processor');

