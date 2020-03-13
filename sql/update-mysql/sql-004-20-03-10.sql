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

update mh_processor
set status = replace(status, 'snippetCode: ', 'functionCode: ');

update mh_processor
set status = replace(status, 'snippetState: ', 'functionState: ');

update mh_processor
set status = replace(status, ': snippet_config_error', ': function_config_error');

