import gc
import os
import sys
from random import randint

import yaml
from datetime import datetime
from keras import backend as K


class Logger(object):
    def __init__(self, artifact_path):
        self.terminal = sys.stdout
        self.log = open(os.path.join(artifact_path, "logfile-predict.log"), "w")

    def write(self, message):
        self.terminal.write(message)
        self.log.write(message)
        self.log.flush()

    def flush(self):
        # this flush method is needed for python 3 compatibility.
        # this handles the flush command by doing nothing.
        # you might want to specify some extra behavior here.
        pass


print(sys.argv)
cwd = os.getcwd()

artifact_path = os.path.join(cwd, 'artifacts')
sys.stdout = Logger(artifact_path)

yaml_file = os.path.join(artifact_path, 'params.yaml')

with open(yaml_file, 'r') as stream:
    params = (yaml.load(stream))

print(params['hyperParams'])

output_file_path = params['outputResourceAbsolutePath']
with open(output_file_path, 'w') as output_file:
    output_file.write("Ok")
    output_file.close()

print(str(datetime.now()))


metrics = {}
metricValues = {}
metrics['values'] = metricValues

metricValues['sum'] = randint(100, 200)
metrics_yaml_file = os.path.join(artifact_path, 'metrics.yaml')

with open(metrics_yaml_file, 'w') as outfile:
    yaml.dump(metrics, outfile, default_plan_style=False)


K.clear_session()
gc.collect()
