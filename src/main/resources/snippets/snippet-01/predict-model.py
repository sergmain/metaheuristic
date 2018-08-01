from datetime import datetime

import gc
import numpy
import pandas
import sys
from keras import backend as K
from sklearn.preprocessing import MinMaxScaler


class Logger(object):
    def __init__(self):
        self.terminal = sys.stdout
        self.log = open("logfile-predict.log", "w")

    def write(self, message):
        self.terminal.write(message)
        self.log.write(message)
        self.log.flush()

    def flush(self):
        # this flush method is needed for python 3 compatibility.
        # this handles the flush command by doing nothing.
        # you might want to specify some extra behavior here.
        pass


sys.stdout = Logger()

time_steps = None
batch_size = None
dataset_file_name = None
is_scaler = None
model_file_name = None
seed = None

print("time_steps: {}, batch_size: {}".format(time_steps, batch_size))
print('model name: {}'.format(model_file_name))

# fix random seed for reproducibility
numpy.random.seed(seed)

print(str(datetime.now()))


#####

######

scaler = MinMaxScaler(feature_range=(0, 1))

dataframe = pandas.read_csv(dataset_file_name, engine='python')
dataset_origin = dataframe.values

dataset_len = len(dataset_origin)
effective_count = dataset_len - time_steps
test_size = batch_size
train_size = effective_count - test_size
shift = dataset_len - effective_count

print("dataset length: {}, effective_count: {}, shift: {}, train_size: {}, test_size: {}".format(dataset_len, effective_count, shift, train_size, test_size))

### Load model

### Predict results

### Main prediction result


K.clear_session()
gc.collect()
